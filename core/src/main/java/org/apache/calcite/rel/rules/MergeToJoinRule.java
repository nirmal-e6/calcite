/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.rel.rules;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.plan.ViewExpanders;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.core.MergeSpec;
import org.apache.calcite.rel.core.TableModify;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.logical.LogicalTableModify;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.tools.RelBuilder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.immutables.value.Value;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Planner rule that lowers a semantic {@code MERGE} into a
 * {@code Join}-based {@link TableModify} tree that executors can consume.
 *
 * <p>The semantic form has the {@code USING} source as its only input and
 * carries {@link MergeSpec}. The rewritten form is
 * {@code TableModify(Project(Join(source, target)))}, where the Project
 * packs insert expressions, the old target row and update expressions into
 * a single row in the layout the {@link TableModify} executors already
 * understand.
 *
 * <p>The rule's operand is {@link LogicalTableModify} because the semantic
 * form is only produced by {@link org.apache.calcite.sql2rel.SqlToRelConverter}
 * as a {@link LogicalTableModify}. No other {@link TableModify} subclass
 * currently carries a {@link MergeSpec}; lowering must happen before any
 * convention conversion.
 *
 * @see CoreRules#MERGE_TO_JOIN
 */
@Value.Enclosing
public class MergeToJoinRule
    extends RelRule<MergeToJoinRule.Config>
    implements TransformationRule {

  /** Creates a MergeToJoinRule. */
  protected MergeToJoinRule(Config config) {
    super(config);
  }

  private static boolean isSemanticMerge(LogicalTableModify modify) {
    return modify.getOperation() == TableModify.Operation.MERGE
        && modify.getMergeSpec() != null;
  }

  @Override public void onMatch(RelOptRuleCall call) {
    final LogicalTableModify modify = call.rel(0);
    call.transformTo(apply(modify, call.builder()));
  }

  /** Applies the transformation: converts a semantic {@code MERGE} into the
   * {@code TableModify(Project(Join(source, target)))} form expected by
   * executors. Exposed so callers (for example, direct {@code rel2sql}
   * paths) can perform the lowering without running the rule through a
   * planner. */
  public static LogicalTableModify apply(LogicalTableModify modify,
      RelBuilder relBuilder) {
    final MergeSpec mergeSpec =
        requireNonNull(modify.getMergeSpec(), "mergeSpec");
    final RelNode source = modify.getInput();
    final RelNode target =
        modify.getTable().toRel(
            ViewExpanders.simpleContext(
            source.getCluster()));
    // A MERGE with a WHEN NOT MATCHED INSERT clause must retain unmatched
    // source rows (those not joining any target row) so the INSERT fires
    // for them -- hence LEFT. When only WHEN MATCHED clauses are present,
    // unmatched source rows are discarded, so INNER suffices.
    final RelNode join =
        LogicalJoin.create(source, target, ImmutableList.of(),
            mergeSpec.getOnCondition(), ImmutableSet.of(),
            mergeSpec.hasInsertClause() ? JoinRelType.LEFT : JoinRelType.INNER);

    final List<RexNode> projects = new ArrayList<>();
    final MergeSpec.MergeClause insertClause = mergeSpec.getInsertClause();
    if (insertClause != null) {
      projects.addAll(insertClause.getSourceExpressionList());
    }

    final MergeSpec.MergeClause updateClause = mergeSpec.getUpdateClause();
    final List<String> updateColumnList = new ArrayList<>();
    if (updateClause != null) {
      final RexBuilder rexBuilder = source.getCluster().getRexBuilder();
      final int sourceFieldCount = mergeSpec.getSourceFieldCount();
      for (int i = 0; i < target.getRowType().getFieldCount(); i++) {
        projects.add(rexBuilder.makeInputRef(join, sourceFieldCount + i));
      }
      for (int ordinal : updateClause.getTargetColumnOrdinals()) {
        updateColumnList.add(target.getRowType().getFieldNames().get(ordinal));
      }
      projects.addAll(updateClause.getSourceExpressionList());
    }

    final RelNode packedInput =
        relBuilder.push(join)
            .project(projects)
            .build();
    return LogicalTableModify.create(modify.getTable(),
        modify.getCatalogReader(), packedInput, modify.getOperation(),
        updateColumnList, null, modify.isFlattened());
  }

  /** Rule configuration. */
  @Value.Immutable
  public interface Config extends RelRule.Config {
    Config DEFAULT = ImmutableMergeToJoinRule.Config.of()
        .withOperandSupplier(b ->
            b.operand(LogicalTableModify.class)
                .predicate(MergeToJoinRule::isSemanticMerge)
                .anyInputs());

    @Override default MergeToJoinRule toRule() {
      return new MergeToJoinRule(this);
    }
  }
}
