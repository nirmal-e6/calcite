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
package org.apache.calcite.rel.core;

import org.apache.calcite.rex.RexNode;
import org.apache.calcite.util.ImmutableIntList;

import com.google.common.collect.ImmutableList;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

import static java.util.Objects.requireNonNull;

/**
 * Semantic description of a {@code MERGE} operation.
 *
 * <p>Expressions in this object reference a virtual input row whose fields
 * are ordered as
 * {@code [source fields (0..sourceFieldCount-1),
 * target table fields (sourceFieldCount..N-1)]}.
 * {@link #getSourceFieldCount()} records the boundary between the two
 * ranges. After {@link org.apache.calcite.rel.rules.MergeToJoinRule}
 * lowers a semantic {@code MERGE}, the virtual row maps 1:1 to the physical
 * row produced by the rebuilt {@code Join(source, targetScan)} of the legacy
 * form.
 */
public class MergeSpec {
  private final RexNode onCondition;
  private final int sourceFieldCount;
  private final ImmutableList<MergeClause> clauses;

  private MergeSpec(RexNode onCondition, int sourceFieldCount,
      List<MergeClause> clauses) {
    this.onCondition = requireNonNull(onCondition, "onCondition");
    checkArgument(sourceFieldCount >= 0,
        "sourceFieldCount must be non-negative");
    this.sourceFieldCount = sourceFieldCount;
    this.clauses = ImmutableList.copyOf(clauses);
    checkArgument(!this.clauses.isEmpty(), "clauses must not be empty");
  }

  /** Creates a {@code MergeSpec}. */
  public static MergeSpec create(RexNode onCondition, int sourceFieldCount,
      List<MergeClause> clauses) {
    return new MergeSpec(onCondition, sourceFieldCount, clauses);
  }

  /** Returns the {@code ON} condition. Expressed over the virtual
   * {@code [source, target]} row described in the class Javadoc. */
  public RexNode getOnCondition() {
    return onCondition;
  }

  public int getSourceFieldCount() {
    return sourceFieldCount;
  }

  public ImmutableList<MergeClause> getClauses() {
    return clauses;
  }

  public boolean hasInsertClause() {
    return clauses.stream().anyMatch(c -> c.actionType == ActionType.INSERT);
  }

  public @Nullable MergeClause getUpdateClause() {
    return clauses.stream()
        .filter(c -> c.actionType == ActionType.UPDATE)
        .findFirst()
        .orElse(null);
  }

  public @Nullable MergeClause getInsertClause() {
    return clauses.stream()
        .filter(c -> c.actionType == ActionType.INSERT)
        .findFirst()
        .orElse(null);
  }

  @Override public String toString() {
    return "MergeSpec(onCondition=[" + onCondition + "]"
        + ", sourceFieldCount=" + sourceFieldCount
        + ", clauses=" + clauses + ")";
  }

  @Override public boolean equals(@Nullable Object obj) {
    return obj == this
        || obj instanceof MergeSpec
        && onCondition.equals(((MergeSpec) obj).onCondition)
        && sourceFieldCount == ((MergeSpec) obj).sourceFieldCount
        && clauses.equals(((MergeSpec) obj).clauses);
  }

  @Override public int hashCode() {
    return Objects.hash(onCondition, sourceFieldCount, clauses);
  }

  /** Match category for a {@code MERGE} clause. */
  public enum MatchType {
    MATCHED,
    NOT_MATCHED
  }

  /** Action performed by a {@code MERGE} clause. */
  public enum ActionType {
    UPDATE,
    INSERT
  }

  /** A single ordered {@code MERGE} clause. */
  public static class MergeClause {
    private final MatchType matchType;
    private final ActionType actionType;
    private final ImmutableIntList targetColumnOrdinals;
    private final ImmutableList<RexNode> sourceExpressionList;

    private MergeClause(MatchType matchType, ActionType actionType,
        ImmutableIntList targetColumnOrdinals,
        List<RexNode> sourceExpressionList) {
      this.matchType = requireNonNull(matchType, "matchType");
      this.actionType = requireNonNull(actionType, "actionType");
      this.targetColumnOrdinals =
          requireNonNull(targetColumnOrdinals, "targetColumnOrdinals");
      this.sourceExpressionList = ImmutableList.copyOf(sourceExpressionList);
      checkArgument(
          targetColumnOrdinals.size() == this.sourceExpressionList.size(),
          "targetColumnOrdinals and sourceExpressionList must have the same size");
    }

    /** Creates a {@code MergeClause}. */
    public static MergeClause create(MatchType matchType, ActionType actionType,
        ImmutableIntList targetColumnOrdinals,
        List<RexNode> sourceExpressionList) {
      return new MergeClause(matchType, actionType, targetColumnOrdinals,
          sourceExpressionList);
    }

    public MatchType getMatchType() {
      return matchType;
    }

    public ActionType getActionType() {
      return actionType;
    }

    /** Returns the ordinals into the target table's row type that this
     * clause writes. For {@code INSERT} clauses produced by
     * {@link org.apache.calcite.sql2rel.SqlToRelConverter}, this normally
     * covers every target column (defaults such as {@code NULL} or the
     * configured initializer fill columns the user did not list), so the
     * original column subset written by the user is not preserved here. */
    public ImmutableIntList getTargetColumnOrdinals() {
      return targetColumnOrdinals;
    }

    /** Returns the values written to {@link #getTargetColumnOrdinals()},
     * positionally aligned. Expressions reference the virtual
     * {@code [source, target]} row described on {@link MergeSpec}. */
    public ImmutableList<RexNode> getSourceExpressionList() {
      return sourceExpressionList;
    }

    @Override public String toString() {
      return "MergeClause(matchType=" + matchType
          + ", actionType=" + actionType
          + ", targetColumnOrdinals=" + targetColumnOrdinals
          + ", sourceExpressionList=" + sourceExpressionList + ")";
    }

    @Override public boolean equals(@Nullable Object obj) {
      return obj == this
          || obj instanceof MergeClause
          && matchType == ((MergeClause) obj).matchType
          && actionType == ((MergeClause) obj).actionType
          && targetColumnOrdinals.equals(
              ((MergeClause) obj).targetColumnOrdinals)
          && sourceExpressionList.equals(
              ((MergeClause) obj).sourceExpressionList);
    }

    @Override public int hashCode() {
      return Objects.hash(matchType, actionType, targetColumnOrdinals,
          sourceExpressionList);
    }
  }
}
