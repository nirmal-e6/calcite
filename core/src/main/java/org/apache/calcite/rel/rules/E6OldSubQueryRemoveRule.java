/*
 * Copyright (c) 2024 Uniphi Inc
 * All rights reserved.
 *
 * File Name: SubQueryRemoveRule.java
 *
 * Created On: 2024-01-04
 */


// This class is copy of calcite's SubQueryRemoveRule [CALCITE VERSION 1.39] to fix some bugs related to IN clause and scalar query


package org.apache.calcite.rel.rules;

import com.google.common.collect.Iterables;
import org.apache.calcite.config.CalciteForkSettings;
import org.apache.calcite.rel.RelHomogeneousShuttle;
import org.apache.calcite.rex.E6LogicVisitor;
import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Collect;
import org.apache.calcite.rel.core.Correlate;
import org.apache.calcite.rel.core.CorrelationId;
import org.apache.calcite.rel.core.Filter;
import org.apache.calcite.rel.core.Join;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.core.Project;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.metadata.RelMdUtil;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rex.*;
import org.apache.calcite.sql.SqlAggFunction;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.fun.SqlQuantifyOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql2rel.RelDecorrelator;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.calcite.util.Pair;

import com.google.common.collect.ImmutableList;

import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.calcite.util.Util.last;

/**
 * Transform that converts IN, EXISTS and scalar sub-queries into joins.
 *
 * <p>Sub-queries are represented by {@link RexSubQuery} expressions.
 *
 * <p>A sub-query may or may not be correlated. If a sub-query is correlated,
 * the wrapped {@link RelNode} will contain a {@link RexCorrelVariable} before
 * the rewrite, and the product of the rewrite will be a {@link Correlate}.
 * The Correlate can be removed using {@link RelDecorrelator}.
 *
 * @see CoreRules#FILTER_SUB_QUERY_TO_CORRELATE
 * @see CoreRules#PROJECT_SUB_QUERY_TO_CORRELATE
 * @see CoreRules#JOIN_SUB_QUERY_TO_CORRELATE
 *
 * This class was shaded to disable SINGLE_VALUE function and create a new property,
 * ruleEnabledForCorrelatedQuery. This property selectively disables scalar query
 * and IN rewrites, if the subquery is not a correlated subquery.
 * For Filter and Join, setting ruleEnabledForCorrelatedQuery disables scalar query
 * and IN rewrite. For Project, only IN rewrite is disabled for Project.
 * This query was failing with RC issue when IN rewrite is enabled for Project.
 * SELECT
 *   1 IN (1, 2, 3),
 *   4 IN (1, 2, 3),
 *   1, 2, 3 IN (1, 2, 3, 4),
 *   1, 2, 4 IN (1, 2, 3),   => This returns true instead of false
 *   1 IN (SELECT 1 UNION SELECT 2 UNION SELECT 3),
 *   4 IN (SELECT 1 UNION SELECT 2 UNION SELECT 3),
 *   NULL IN (1, 2, 3),
 *   1 IN (1, 2, 2, 3),
 *   NULL IN (SELECT 1);
 */

@Value.Enclosing
public class E6OldSubQueryRemoveRule
    extends RelRule<E6OldSubQueryRemoveRule.Config>
    implements TransformationRule
{

private static final Logger s_logger = LoggerFactory.getLogger(E6OldSubQueryRemoveRule.class);
private boolean isRuleEnabledOnlyForCorrelation = false;
/** Creates a SubQueryRemoveRule. */
protected E6OldSubQueryRemoveRule(Config config) {
  super(config);
  Objects.requireNonNull(config.matchHandler());
  isRuleEnabledOnlyForCorrelation = config.isRuleEnabledOnlyForCorrelatedQuery();
}

@Override public void onMatch(RelOptRuleCall call) {
  config.matchHandler().accept(this, call);
}

protected RexNode apply(RexSubQuery e, Set<CorrelationId> variablesSet,
    RelOptUtil.Logic logic,
    RelBuilder builder, int inputCount, int offset, int subQueryIndex,boolean ruleEnabledOnlyForCorrelatedQuery) {
  // E6Data change: keep the old compatibility gates centralized so callers can
  // ask whether a sub-query is rewritable before choosing it for this rule.
  if (shouldSkipSubQuery(e, variablesSet, ruleEnabledOnlyForCorrelatedQuery)) {
    return null;
  }

  switch (e.getKind()) {
    case SCALAR_QUERY:
      return rewriteScalarQuery(e, variablesSet, builder, inputCount, offset);
    case ARRAY_QUERY_CONSTRUCTOR:
    case MAP_QUERY_CONSTRUCTOR:
    case MULTISET_QUERY_CONSTRUCTOR:
      return rewriteCollection(e, variablesSet, builder,
          inputCount, offset);
    case SOME:
      return rewriteSome(e, variablesSet, builder);
    case IN:
      return rewriteIn(e, variablesSet, logic, builder, offset, subQueryIndex);
    case EXISTS:
      return rewriteExists(e, variablesSet, logic, builder);
    case UNIQUE:
      return rewriteUnique(e, builder);
    default:
      throw new AssertionError(e.getKind());
  }
}

// E6Data change: JOIN conditions may contain multiple sub-queries; skip the
// ones this old rule intentionally leaves unchanged and rewrite the next valid one.
private RexSubQuery findSubQueryForRule(RexNode node) {
  for (RexSubQuery subQuery : collectSubQueries(node)) {
    Set<CorrelationId> variablesSet = RelOptUtil.getVariablesUsed(subQuery.rel);
    if (!shouldSkipSubQuery(subQuery, variablesSet)) {
      return subQuery;
    }
  }
  return null;
}

private boolean shouldSkipSubQuery(RexSubQuery e, Set<CorrelationId> variablesSet) {
  return shouldSkipSubQuery(e, variablesSet, isRuleEnabledOnlyForCorrelation);
}

private boolean shouldSkipSubQuery(RexSubQuery e, Set<CorrelationId> variablesSet,
    boolean ruleEnabledOnlyForCorrelatedQuery) {
  switch (e.getKind()) {
    case SCALAR_QUERY:
    case ARRAY_QUERY_CONSTRUCTOR:
    case MAP_QUERY_CONSTRUCTOR:
    case MULTISET_QUERY_CONSTRUCTOR:
      return ruleEnabledOnlyForCorrelatedQuery
          && variablesSet.isEmpty()
          && !this.description.equals("SubQueryRemoveRule:Project");

    case IN:
      if (isInWithLiteralOperand(e)) {
        return true;
      }
      // Keep E6's compatibility gates for uncorrelated IN rewrites, but make the
      // decision local to this sub-query so later correlated sub-queries can run.
      if (ruleEnabledOnlyForCorrelatedQuery && variablesSet.isEmpty()
          && !this.description.equals("SubQueryRemoveRule:Filter")) {
        return (this.description.equals("SubQueryRemoveRule:Project")
            && !CalciteForkSettings.decorrelateInClauseForProjection())
            || (this.description.equals("SubQueryRemoveRule:Join")
            && !CalciteForkSettings.decorrelateInClauseForJoin());
      }
      return false;

    default:
      return false;
  }
}

private static boolean isInWithLiteralOperand(RexSubQuery e) {
  if (e.getOperands().isEmpty()) {
    return false;
  }

  RexNode operand = e.getOperands().get(0);
  if (operand instanceof RexLiteral) {
    return true;
  }
  if (operand instanceof RexCall) {
    RexCall call = (RexCall) operand;
    return call.getOperator().getKind() == SqlKind.CAST
        && call.getOperands().get(0) instanceof RexLiteral;
  }
  return false;
}

private static List<RexSubQuery> collectSubQueries(RexNode node) {
  List<RexSubQuery> subQueries = new ArrayList<>();
  node.accept(new RexVisitorImpl<Void>(true) {
    @Override public Void visitSubQuery(RexSubQuery subQuery) {
      subQueries.add(subQuery);
      return null;
    }
  });
  return subQueries;
}

/**
 * Rewrites a scalar sub-query into an
 * {@link org.apache.calcite.rel.core.Aggregate}.
 *
 * @param e            Scalar sub-query to rewrite
 * @param variablesSet A set of variables used by a relational
 *                     expression of the specified RexSubQuery
 * @param builder      Builder
 * @param offset       Offset to shift {@link RexInputRef}
 *
 * @return Expression that may be used to replace the RexSubQuery
 */
private static RexNode rewriteScalarQuery(RexSubQuery e, Set<CorrelationId> variablesSet,
    RelBuilder builder, int inputCount, int offset) {
  builder.push(e.rel);
  final RelMetadataQuery mq = e.rel.getCluster().getMetadataQuery();
  //final Boolean unique = mq.areColumnsUnique(builder.peek(),
  //    ImmutableBitSet.of());

  // Commenting SINGLE_VALUE agg node for now, as executor needs to be modified
  // to implement this functionality. Also, see E6ReplaceSingleFuncWithCase
  // if (unique == null || !unique) {
  //    builder.aggregate(builder.groupKey(),
  //        builder.aggregateCall(SqlStdOperatorTable.SINGLE_VALUE,
  //            builder.field(0)));
  //}
  builder.join(JoinRelType.LEFT, builder.literal(true), variablesSet);
  return field(builder, inputCount, offset);
}

/**
 * Rewrites a sub-query into a
 * {@link org.apache.calcite.rel.core.Collect}.
 *
 * @param e            Sub-query to rewrite
 * @param variablesSet A set of variables used by a relational
 *                     expression of the specified RexSubQuery
 * @param builder      Builder
 * @param offset       Offset to shift {@link RexInputRef}
 * @return Expression that may be used to replace the RexSubQuery
 */
private static RexNode rewriteCollection(RexSubQuery e,
    Set<CorrelationId> variablesSet, RelBuilder builder,
    int inputCount, int offset) {
  builder.push(e.rel);
  builder.push(
      Collect.create(builder.build(), e.getKind(), "x"));
  builder.join(JoinRelType.INNER, builder.literal(true), variablesSet);
  return field(builder, inputCount, offset);
}

/**
 * Rewrites a SOME sub-query into a {@link Join}.
 *
 * @param e            SOME sub-query to rewrite
 * @param builder      Builder
 *
 * @return Expression that may be used to replace the RexSubQuery
 */
private static RexNode rewriteSome(RexSubQuery e, Set<CorrelationId> variablesSet,
    RelBuilder builder) {
  // Most general case, where the left and right keys might have nulls, and
  // caller requires 3-valued logic return.
  //
  // select e.deptno, e.deptno < some (select deptno from emp) as v
  // from emp as e
  //
  // becomes
  //
  // select e.deptno,
  //   case
  //   when q.c = 0 then false // sub-query is empty
  //   when (e.deptno < q.m) is true then true
  //   when q.c > q.d then unknown // sub-query has at least one null
  //   else e.deptno < q.m
  //   end as v
  // from emp as e
  // cross join (
  //   select max(deptno) as m, count(*) as c, count(deptno) as d
  //   from emp) as q
  //
  final SqlQuantifyOperator op = (SqlQuantifyOperator) e.op;
  switch (op.comparisonKind) {
    case GREATER_THAN_OR_EQUAL:
    case LESS_THAN_OR_EQUAL:
    case LESS_THAN:
    case GREATER_THAN:
    case NOT_EQUALS:
      break;

    default:
      // "SOME =" should have been rewritten into IN.
      throw new AssertionError("unexpected " + op);
  }

  final RexNode caseRexNode;
  final RexNode literalFalse = builder.literal(false);
  final RexNode literalTrue = builder.literal(true);
  final RexLiteral literalUnknown =
      builder.getRexBuilder().makeNullLiteral(literalFalse.getType());

  final SqlAggFunction minMax = op.comparisonKind == SqlKind.GREATER_THAN
                                    || op.comparisonKind == SqlKind.GREATER_THAN_OR_EQUAL
                                ? SqlStdOperatorTable.MIN
                                : SqlStdOperatorTable.MAX;

  if (variablesSet.isEmpty()) {
    switch (op.comparisonKind) {
      case GREATER_THAN_OR_EQUAL:
      case LESS_THAN_OR_EQUAL:
      case LESS_THAN:
      case GREATER_THAN:
        // for non-correlated case queries such as
        // select e.deptno, e.deptno < some (select deptno from emp) as v
        // from emp as e
        //
        // becomes
        //
        // select e.deptno,
        //   case
        //   when q.c = 0 then false // sub-query is empty
        //   when (e.deptno < q.m) is true then true
        //   when q.c > q.d then unknown // sub-query has at least one null
        //   else e.deptno < q.m
        //   end as v
        // from emp as e
        // cross join (
        //   select max(deptno) as m, count(*) as c, count(deptno) as d
        //   from emp) as q
        builder.push(e.rel)
            .aggregate(builder.groupKey(),
                builder.aggregateCall(minMax, builder.field(0)).as("m"),
                builder.count(false, "c"),
                builder.count(false, "d", builder.field(0)))
            .as("q")
            .join(JoinRelType.INNER);
        caseRexNode = builder.call(SqlStdOperatorTable.CASE,
            builder.equals(builder.field("q", "c"), builder.literal(0)),
            literalFalse,
            builder.call(SqlStdOperatorTable.IS_TRUE,
                builder.call(RexUtil.op(op.comparisonKind),
                    e.operands.get(0), builder.field("q", "m"))),
            literalTrue,
            builder.greaterThan(builder.field("q", "c"),
                builder.field("q", "d")),
            literalUnknown,
            builder.call(RexUtil.op(op.comparisonKind),
                e.operands.get(0), builder.field("q", "m")));
        break;

      case NOT_EQUALS:
        // for non-correlated case queries such as
        // select e.deptno, e.deptno <> some (select deptno from emp) as v
        // from emp as e
        //
        // becomes
        //
        // select e.deptno,
        //   case
        //   when q.c = 0 then false // sub-query is empty
        //   when e.deptno is null then unknown
        //   when q.c <> q.d && q.d <= 1 then e.deptno != m || unknown
        //   when q.d = 1
        //     then e.deptno != m // sub-query has the distinct result
        //   else true
        //   end as v
        // from emp as e
        // cross join (
        //   select count(*) as c, count(deptno) as d, max(deptno) as m
        //   from (select distinct deptno from emp)) as q
        builder.push(e.rel);
        builder.distinct()
            .aggregate(builder.groupKey(),
                builder.count(false, "c"),
                builder.count(false, "d", builder.field(0)),
                builder.max(builder.field(0)).as("m"))
            .as("q")
            .join(JoinRelType.INNER);
        caseRexNode = builder.call(SqlStdOperatorTable.CASE,
            builder.equals(builder.field("c"), builder.literal(0)),
            literalFalse,
            builder.isNull(e.getOperands().get(0)),
            literalUnknown,
            builder.and(
                builder.notEquals(builder.field("d"), builder.field("c")),
                builder.lessThanOrEqual(builder.field("d"),
                    builder.literal(1))),
            builder.or(
                builder.notEquals(e.operands.get(0), builder.field("q", "m")),
                literalUnknown),
            builder.equals(builder.field("d"), builder.literal(1)),
            builder.notEquals(e.operands.get(0), builder.field("q", "m")),
            literalTrue);
        break;

      default:
        throw new AssertionError("not possible - per above check");
    }
  } else {
    final String indicator = "trueLiteral";
    final List<RexNode> parentQueryFields = new ArrayList<>();
    switch (op.comparisonKind) {
      case GREATER_THAN_OR_EQUAL:
      case LESS_THAN_OR_EQUAL:
      case LESS_THAN:
      case GREATER_THAN:
        // for correlated case queries such as
        //
        // select e.deptno, e.deptno < some (
        //   select deptno from emp where emp.name = e.name) as v
        // from emp as e
        //
        // becomes
        //
        // select e.deptno,
        //   case
        //   when indicator is null then false // sub-query is empty for corresponding corr value
        //   when q.c = 0 then false // sub-query is empty
        //   when (e.deptno < q.m) is true then true
        //   when q.c > q.d then unknown // sub-query has at least one null
        //   else e.deptno < q.m
        //   end as v
        // from emp as e
        // left outer join (
        //   select name, max(deptno) as m, count(*) as c, count(deptno) as d,
        //       "alwaysTrue" as indicator
        //   from emp group by name) as q on e.name = q.name
        builder.push(e.rel)
            .aggregate(builder.groupKey(),
                builder.aggregateCall(minMax, builder.field(0)).as("m"),
                builder.count(false, "c"),
                builder.count(false, "d", builder.field(0)));

        parentQueryFields.addAll(builder.fields());
        parentQueryFields.add(builder.alias(literalTrue, indicator));
        builder.project(parentQueryFields).as("q");
        builder.join(JoinRelType.LEFT, literalTrue, variablesSet);
        caseRexNode = builder.call(SqlStdOperatorTable.CASE,
            builder.isNull(builder.field("q", indicator)),
            literalFalse,
            builder.equals(builder.field("q", "c"), builder.literal(0)),
            literalFalse,
            builder.call(SqlStdOperatorTable.IS_TRUE,
                builder.call(RexUtil.op(op.comparisonKind),
                    e.operands.get(0), builder.field("q", "m"))),
            literalTrue,
            builder.greaterThan(builder.field("q", "c"),
                builder.field("q", "d")),
            literalUnknown,
            builder.call(RexUtil.op(op.comparisonKind),
                e.operands.get(0), builder.field("q", "m")));
        break;

      case NOT_EQUALS:
        // for correlated case queries such as
        //
        // select e.deptno, e.deptno <> some (
        //   select deptno from emp where emp.name = e.name) as v
        // from emp as e
        //
        // becomes
        //
        // select e.deptno,
        //   case
        //   when indicator is null
        //     then false // sub-query is empty for corresponding corr value
        //   when q.c = 0 then false // sub-query is empty
        //   when e.deptno is null then unknown
        //   when q.c <> q.d && q.d <= 1
        //     then e.deptno != m || unknown
        //   when q.d = 1
        //     then e.deptno != m // sub-query has the distinct result
        //   else true
        //   end as v
        // from emp as e
        // left outer join (
        //   select name, count(distinct *) as c, count(distinct deptno) as d,
        //       max(deptno) as m, "alwaysTrue" as indicator
        //   from emp group by name) as q on e.name = q.name
        builder.push(e.rel)
            .aggregate(builder.groupKey(),
                builder.count(true, "c"),
                builder.count(true, "d", builder.field(0)),
                builder.max(builder.field(0)).as("m"));

        parentQueryFields.addAll(builder.fields());
        parentQueryFields.add(builder.alias(literalTrue, indicator));
        builder.project(parentQueryFields).as("q"); // TODO use projectPlus
        builder.join(JoinRelType.LEFT, literalTrue, variablesSet);
        caseRexNode = builder.call(SqlStdOperatorTable.CASE,
            builder.isNull(builder.field("q", indicator)),
            literalFalse,
            builder.equals(builder.field("c"), builder.literal(0)),
            literalFalse,
            builder.isNull(e.getOperands().get(0)),
            literalUnknown,
            builder.and(
                builder.notEquals(builder.field("d"), builder.field("c")),
                builder.lessThanOrEqual(builder.field("d"),
                    builder.literal(1))),
            builder.or(
                builder.notEquals(e.operands.get(0), builder.field("q", "m")),
                literalUnknown),
            builder.equals(builder.field("d"), builder.literal(1)),
            builder.notEquals(e.operands.get(0), builder.field("q", "m")),
            literalTrue);
        break;

      default:
        throw new AssertionError("not possible - per above check");
    }
  }

  // CASE statement above is created with nullable boolean type, but it might
  // not be correct.  If the original sub-query node's type is not nullable it
  // is guaranteed for case statement to not produce NULLs. Therefore to avoid
  // planner complaining we need to add cast.  Note that nullable type is
  // created due to the MIN aggregate call, since there is no GROUP BY.
  if (!e.getType().isNullable()) {
    return builder.cast(caseRexNode, e.getType().getSqlTypeName());
  }
  return caseRexNode;
}

/**
 * Rewrites an EXISTS RexSubQuery into a {@link Join}.
 *
 * @param e            EXISTS sub-query to rewrite
 * @param variablesSet A set of variables used by a relational
 *                     expression of the specified RexSubQuery
 * @param logic        Logic for evaluating
 * @param builder      Builder
 *
 * @return Expression that may be used to replace the RexSubQuery
 */
private static RexNode rewriteExists(RexSubQuery e, Set<CorrelationId> variablesSet,
    RelOptUtil.Logic logic, RelBuilder builder) {
  // If the sub-query is guaranteed to produce at least one row, just return
  // TRUE.
  final RelMetadataQuery mq = e.rel.getCluster().getMetadataQuery();
  if (RelMdUtil.isRelDefinitelyNotEmpty(mq, e.rel)) {
    return builder.literal(true);
  }
  if (RelMdUtil.isRelDefinitelyEmpty(mq, e.rel)) {
    return builder.literal(false);
  }
  builder.push(e.rel);
  builder.project(builder.alias(builder.literal(true), "i"));
  switch (logic) {
    case TRUE:
      // Handles queries with single EXISTS in filter condition:
      // select e.deptno from emp as e
      // where exists (select deptno from emp)
      builder.aggregate(builder.groupKey(0));
      builder.as("dt");
      builder.join(JoinRelType.INNER, builder.literal(true), variablesSet);
      return builder.literal(true);
    default:
      builder.distinct();
  }

  builder.as("dt");

  builder.join(JoinRelType.LEFT, builder.literal(true), variablesSet);

  return builder.isNotNull(last(builder.fields()));
}

/**
 * Rewrites a UNIQUE RexSubQuery into an EXISTS RexSubQuery.
 *
 * <p>For example, rewrites the UNIQUE sub-query:
 *
 * <pre>{@code
 * UNIQUE (SELECT PUBLISHED_IN
 * FROM BOOK
 * WHERE AUTHOR_ID = 3)
 * }</pre>
 *
 * <p>to the following EXISTS sub-query:
 *
 * <pre>{@code
 * NOT EXISTS (
 *   SELECT * FROM (
 *     SELECT PUBLISHED_IN
 *     FROM BOOK
 *     WHERE AUTHOR_ID = 3
 *   ) T
 *   WHERE (T.PUBLISHED_IN) IS NOT NULL
 *   GROUP BY T.PUBLISHED_IN
 *   HAVING COUNT(*) > 1
 * )
 * }</pre>
 *
 * @param e            UNIQUE sub-query to rewrite
 * @param builder      Builder
 *
 * @return Expression that may be used to replace the RexSubQuery
 */
private static RexNode rewriteUnique(RexSubQuery e, RelBuilder builder) {
  // if sub-query always return unique value.
  final RelMetadataQuery mq = e.rel.getCluster().getMetadataQuery();
  Boolean isUnique = mq.areRowsUnique(e.rel, true);
  if (isUnique != null && isUnique) {
    return builder.getRexBuilder().makeLiteral(true);
  }
  builder.push(e.rel);
  List<RexNode> notNullCondition =
      builder.fields().stream()
          .map(builder::isNotNull)
          .collect(Collectors.toList());
  builder
      .filter(notNullCondition)
      .aggregate(builder.groupKey(builder.fields()), builder.countStar("c"))
      .filter(
          builder.greaterThan(last(builder.fields()), builder.literal(1)));
  RelNode relNode = builder.build();
  return builder.call(SqlStdOperatorTable.NOT, RexSubQuery.exists(relNode));
}

/**
 * Rewrites an IN RexSubQuery into a {@link Join}.
 *
 * @param e            IN sub-query to rewrite
 * @param variablesSet A set of variables used by a relational
 *                     expression of the specified RexSubQuery
 * @param logic        Logic for evaluating
 * @param builder      Builder
 * @param offset       Offset to shift {@link RexInputRef}
 *
 * @return Expression that may be used to replace the RexSubQuery
 */
private static RexNode rewriteIn(RexSubQuery e, Set<CorrelationId> variablesSet,
    RelOptUtil.Logic logic, RelBuilder builder, int offset, int subQueryIndex) {
  // Most general case, where the left and right keys might have nulls, and
  // caller requires 3-valued logic return.
  //
  // select e.deptno, e.deptno in (select deptno from emp)
  // from emp as e
  //
  // becomes
  //
  // select e.deptno,
  //   case
  //   when ct.c = 0 then false
  //   when e.deptno is null then null
  //   when dt.i is not null then true
  //   when ct.ck < ct.c then null
  //   else false
  //   end
  // from emp as e
  // left join (
  //   (select count(*) as c, count(deptno) as ck from emp) as ct
  //   cross join (select distinct deptno, true as i from emp)) as dt
  //   on e.deptno = dt.deptno
  //
  // If keys are not null we can remove "ct" and simplify to
  //
  // select e.deptno,
  //   case
  //   when dt.i is not null then true
  //   else false
  //   end
  // from emp as e
  // left join (select distinct deptno, true as i from emp) as dt
  //   on e.deptno = dt.deptno
  //
  // We could further simplify to
  //
  // select e.deptno,
  //   dt.i is not null
  // from emp as e
  // left join (select distinct deptno, true as i from emp) as dt
  //   on e.deptno = dt.deptno
  //
  // but have not yet.
  //
  // If the logic is TRUE we can just kill the record if the condition
  // evaluates to FALSE or UNKNOWN. Thus the query simplifies to an inner
  // join:
  //
  // select e.deptno,
  //   true
  // from emp as e
  // inner join (select distinct deptno from emp) as dt
  //   on e.deptno = dt.deptno
  //

  builder.push(e.rel);
  final List<RexNode> fields = new ArrayList<>(builder.fields());

  // for the case when IN has only literal operands, it may be handled
  // in the simpler way:
  //
  // select e.deptno, 123456 in (select deptno from emp)
  // from emp as e
  //
  // becomes
  //
  // select e.deptno,
  //   case
  //   when dt.c IS NULL THEN FALSE
  //   when e.deptno IS NULL THEN NULL
  //   when dt.cs IS FALSE THEN NULL
  //   when dt.cs IS NOT NULL THEN TRUE
  //   else false
  //   end
  // from emp AS e
  // cross join (
  //   select distinct deptno is not null as cs, count(*) as c
  //   from emp
  //   where deptno = 123456 or deptno is null or e.deptno is null
  //   order by cs desc limit 1) as dt
  //

  String ctAlias = "ct";
  if (subQueryIndex != 0)
  {
    ctAlias = "ct" + subQueryIndex;
  }

  boolean allLiterals = RexUtil.allLiterals(e.getOperands());
  final List<RexNode> expressionOperands = new ArrayList<>(e.getOperands());

  final List<RexNode> keyIsNulls = e.getOperands().stream()
      .filter(operand -> operand.getType().isNullable())
      .map(builder::isNull)
      .collect(Collectors.toList());

  final RexLiteral trueLiteral = builder.literal(true);
  final RexLiteral falseLiteral = builder.literal(false);
  final RexLiteral unknownLiteral =
      builder.getRexBuilder().makeNullLiteral(trueLiteral.getType());
  if (allLiterals) {
    final List<RexNode> conditions =
        Pair.zip(expressionOperands, fields).stream()
            .map(pair -> builder.equals(pair.left, pair.right))
            .collect(Collectors.toList());
    switch (logic) {
      case TRUE:
      case TRUE_FALSE:
        builder.filter(conditions);
        builder.project(builder.alias(trueLiteral, "cs"));
        builder.distinct();
        break;
      default:
        List<RexNode> isNullOperands = fields.stream()
            .map(builder::isNull)
            .collect(Collectors.toList());
        // uses keyIsNulls conditions in the filter to avoid empty results
        isNullOperands.addAll(keyIsNulls);
        builder.filter(
            builder.or(
                builder.and(conditions),
                builder.or(isNullOperands)));
        RexNode project = builder.and(
            fields.stream()
                .map(builder::isNotNull)
                .collect(Collectors.toList()));
        builder.project(builder.alias(project, "cs"));

        if (variablesSet.isEmpty()) {
          builder.aggregate(builder.groupKey(builder.field("cs")),
              builder.count(false, "c"));

          // sorts input with desc order since we are interested
          // only in the case when one of the values is true.
          // When true value is absent then we are interested
          // only in false value.
          builder.sortLimit(0, 1,
              ImmutableList.of(builder.desc(builder.field("cs"))));
        } else {
          builder.distinct();
        }
    }
    // clears expressionOperands and fields lists since
    // all expressions were used in the filter
    expressionOperands.clear();
    fields.clear();
  } else {
    switch (logic) {
      case TRUE:
        builder.aggregate(builder.groupKey(fields));
        break;
      case TRUE_FALSE_UNKNOWN:
      case UNKNOWN_AS_TRUE:
        // Builds the cross join
        builder.aggregate(builder.groupKey(),
            builder.count(false, "c"),
            builder.count(builder.fields()).as("ck"));
        builder.as(ctAlias);
        if (!variablesSet.isEmpty()) {
          builder.join(JoinRelType.LEFT, trueLiteral, variablesSet);
        } else {
          builder.join(JoinRelType.INNER, trueLiteral, variablesSet);
        }
        offset += 2;
        builder.push(e.rel);
        // fall through
      default:
        fields.add(builder.alias(trueLiteral, "i"));
        builder.project(fields);
        builder.distinct();
    }
  }

  String dtAlias = "dt";
  if (subQueryIndex != 0) {
    dtAlias = "dt" + subQueryIndex;
  }
  builder.as(dtAlias);
  int refOffset = offset;
  final List<RexNode> conditions =
      Pair.zip(expressionOperands, builder.fields()).stream()
          .map(pair -> builder.equals(pair.left, RexUtil.shift(pair.right, refOffset)))
          .collect(Collectors.toList());
  switch (logic) {
    case TRUE:
      builder.join(JoinRelType.INNER, builder.and(conditions), variablesSet);
      return trueLiteral;
    default:
      break;
  }
  // Now the left join
  builder.join(JoinRelType.LEFT, builder.and(conditions), variablesSet);

  final ImmutableList.Builder<RexNode> operands = ImmutableList.builder();
  RexLiteral b = trueLiteral;
  switch (logic) {
    case TRUE_FALSE_UNKNOWN:
      b = unknownLiteral;
      // fall through
    case UNKNOWN_AS_TRUE:
      if (allLiterals) {
        // Considers case when right side of IN is empty
        // for the case of non-correlated sub-queries
        if (variablesSet.isEmpty()) {
          operands.add(
              builder.isNull(builder.field("c")),
              falseLiteral);
        }
        operands.add(
            builder.equals(builder.field("cs"), falseLiteral),
            b);
      } else {
        operands.add(
            builder.equals(builder.field(ctAlias, "c"), builder.literal(0)),
            falseLiteral);
      }
      break;
    default:
      break;
  }

  if (!keyIsNulls.isEmpty()) {
    operands.add(builder.or(keyIsNulls), unknownLiteral);
  }

  if (allLiterals) {
    operands.add(builder.isNotNull(builder.field("cs")),
        trueLiteral);
  } else {
    operands.add(builder.isNotNull(last(builder.fields())),
        trueLiteral);
  }

  if (!allLiterals) {
    switch (logic) {
      case TRUE_FALSE_UNKNOWN:
      case UNKNOWN_AS_TRUE:
        operands.add(
            builder.lessThan(builder.field(ctAlias, "ck"),
                builder.field(ctAlias, "c")),
            b);
        break;
      default:
        break;
    }
  }
  operands.add(falseLiteral);
  return builder.call(SqlStdOperatorTable.CASE, operands.build());
}

/** Returns a reference to a particular field, by offset, across several
 * inputs on a {@link RelBuilder}'s stack. */
private static RexInputRef field(RelBuilder builder, int inputCount, int offset) {
  for (int inputOrdinal = 0;;) {
    final RelNode r = builder.peek(inputCount, inputOrdinal);
    if (offset < r.getRowType().getFieldCount()) {
      return builder.field(inputCount, inputOrdinal, offset);
    }
    ++inputOrdinal;
    offset -= r.getRowType().getFieldCount();
  }
}

/** Returns a list of expressions that project the first {@code fieldCount}
 * fields of the top input on a {@link RelBuilder}'s stack. */
private static List<RexNode> fields(RelBuilder builder, int fieldCount) {
  final List<RexNode> projects = new ArrayList<>();
  for (int i = 0; i < fieldCount; i++) {
    projects.add(builder.field(i));
  }
  return projects;
}

private static void matchProject(E6OldSubQueryRemoveRule rule,
    RelOptRuleCall call) {

  final Project project = call.rel(0);
  final RelBuilder builder = call.builder();
  final RexSubQuery e =
      RexUtil.SubQueryFinder.find(project.getProjects());
  assert e != null;
  final RelOptUtil.Logic logic =
      LogicVisitor.find(RelOptUtil.Logic.TRUE_FALSE_UNKNOWN,
          project.getProjects(), e);
  builder.push(project.getInput());
  final int fieldCount = builder.peek().getRowType().getFieldCount();
  final Set<CorrelationId>  variablesSet =
      RelOptUtil.getVariablesUsed(e.rel);
  final RexNode target = rule.apply(e, variablesSet,
      logic, builder, 1, fieldCount, 0,rule.config.isRuleEnabledOnlyForCorrelatedQuery() );
  if (target == null)
  {
    return;
  }
    CalciteForkSettings.incrementSubqueriesInProject(call.getPlanner().getContext());
  final RexShuttle shuttle = new ReplaceSubQueryShuttle(e, target);
  builder.project(shuttle.apply(project.getProjects()),
      project.getRowType().getFieldNames());
  call.transformTo(builder.build());
}

private static void matchFilter(E6OldSubQueryRemoveRule rule,
    RelOptRuleCall call) {

  final Filter filter = call.rel(0);
  final Set<CorrelationId> filterVariablesSet = filter.getVariablesSet();
  final RelBuilder builder = call.builder();
  builder.push(filter.getInput());
  int count = 0;
  RexNode c = filter.getCondition();
  while (true) {
    final RexSubQuery e = RexUtil.SubQueryFinder.find(c);
    if (e == null) {
      assert count > 0;
      break;
    }
    ++count;
    final RelOptUtil.Logic logic;
      // s_logger.debug("ENABLE_NON_NULLABLE_SUBQUERY_OPT is : {}", CalciteForkSettings.enableNonNullableSubqueryOpt());
      if(rule.config.forceNonNullableSubqueryOpt())
      {
          logic = RelOptUtil.Logic.TRUE_FALSE;
      }
      else if(CalciteForkSettings.enableNonNullableSubqueryOpt())
      {
          // s_logger.debug("logic visitor : E6LogicVisitor");
          logic = E6LogicVisitor.find(RelOptUtil.Logic.TRUE, ImmutableList.of(c), e, (LogicalFilter) filter);
      }
      else
      {
          // s_logger.debug("logic visitor : Calcite's LogicVisitor");
          logic = LogicVisitor.find(RelOptUtil.Logic.TRUE, ImmutableList.of(c), e);
      }
    final Set<CorrelationId>  variablesSet =
        RelOptUtil.getVariablesUsed(e.rel);
    // Filter without variables could be handled before this change, we do not want
    // to break it yet for compatibility reason.
    if (!filterVariablesSet.isEmpty()) {
      // Only consider the correlated variables which originated from this sub-query level.
      variablesSet.retainAll(filterVariablesSet);
    }
    final RexNode target = rule.apply(e, variablesSet, logic,
        builder, 1, builder.peek().getRowType().getFieldCount(),
        count ,rule.config.isRuleEnabledOnlyForCorrelatedQuery());
    if (target == null)
    {
      return;
    }
      CalciteForkSettings.incrementSubqueryInFilter(call.getPlanner().getContext(), count);
    final RexShuttle shuttle = new ReplaceSubQueryShuttle(e, target);
    c = c.accept(shuttle);
  }
  builder.filter(c);
  builder.project(fields(builder, filter.getRowType().getFieldCount()));
  call.transformTo(builder.build());
}

// backport from latest calcite
private static void matchJoin(E6OldSubQueryRemoveRule rule, RelOptRuleCall call) {
  final Join join = call.rel(0);
  final RelBuilder builder = call.builder();
  // E6Data change: do not let an unsupported leading sub-query block a later
  // correlated sub-query in the same join condition.
  final RexSubQuery e = rule.findSubQueryForRule(join.getCondition());
  if (e == null) {
    return;
  }

  ImmutableBitSet inputSet = RelOptUtil.InputFinder.bits(e.getOperands(), null);
  int nFieldsLeft = join.getLeft().getRowType().getFieldCount();
  int nFieldsRight = join.getRight().getRowType().getFieldCount();

  // Correlation columns should also be considered.
  // For example:
  //                                   LogicalJoin
  //              left                                          right
  //                |                                             |
  // LogicalProject.NONE.[0, 1]                            LogicalValues.NONE.[0]
  // RecordType(INTEGER DEPTNO, CHAR(11) DNAME)            RecordType(INTEGER DEPTNO)
  //
  // and subquery: $SCALAR_QUERY with correlate
  // LogicalProject(DEPTNO=[$1])
  //   LogicalFilter(condition=[=(CAST($0):CHAR(11) NOT NULL, $cor0.DNAME)])
  //
  // In such a case $cor0.DNAME need to be accounted as input form left side.
  final Set<CorrelationId> variablesSet = RelOptUtil.getVariablesUsed(e.rel);
  for (CorrelationId id : variablesSet) {
    ImmutableBitSet requiredColumns = RelOptUtil.correlationColumns(id, e.rel);
    inputSet = ImmutableBitSet.union(ImmutableList.of(requiredColumns, inputSet));
  }

  boolean inputIntersectsLeftSide = inputSet.intersects(ImmutableBitSet.range(0, nFieldsLeft));
  boolean inputIntersectsRightSide =
      inputSet.intersects(ImmutableBitSet.range(nFieldsLeft, nFieldsLeft + nFieldsRight));
  if (inputIntersectsLeftSide && inputIntersectsRightSide) {
    // The current existential rewrite needs to make join with one side of the origin join and
    // generate a new condition to replace the on clause. But for RexNode whose operands are
    // on either side of the join, we can't push them into join. So this rewriting is not
    // supported.
    return;
  }

  if (inputIntersectsLeftSide) {
    builder.push(join.getLeft());
    final RelOptUtil.Logic logic =
        LogicVisitor.find(join.getJoinType().generatesNullsOnRight()
                          ? RelOptUtil.Logic.TRUE_FALSE_UNKNOWN : RelOptUtil.Logic.TRUE,
            ImmutableList.of(join.getCondition()), e);

    final RexNode target =
        rule.apply(e, variablesSet, logic, builder, 1, nFieldsLeft, 0, rule.isRuleEnabledOnlyForCorrelation);
    if (target == null)
    {
      return;
    }
    final RexShuttle shuttle = new ReplaceSubQueryShuttle(e, target);

    final RexNode newCond =
        shuttle.apply(
            RexUtil.shift(join.getCondition(), nFieldsLeft,
                builder.fields().size() - nFieldsLeft));
    builder.push(join.getRight());
    builder.join(join.getJoinType(), newCond);

    final int nFields = builder.fields().size();
    ImmutableList<RexNode> fields =
        builder.fields(ImmutableBitSet.range(0, nFieldsLeft)
            .union(ImmutableBitSet.range(nFields - nFieldsRight, nFields)));
    builder.project(fields);
  } else {
    builder.push(join.getRight());

    final RelOptUtil.Logic logic =
        LogicVisitor.find(join.getJoinType().generatesNullsOnLeft()
                          ? RelOptUtil.Logic.TRUE_FALSE_UNKNOWN : RelOptUtil.Logic.TRUE,
            ImmutableList.of(join.getCondition()), e);

    RexSubQuery subQuery = e;
    if (!variablesSet.isEmpty()) {
      // Original correlates reference joint row type, but we are about to create
      // new join of original right side and correlated sub-query. Therefore we have
      // to adjust correlated variables in following way:
      //   1) new correlation variable must reference row type of right side only
      //   2) field index must be shifted on the size of the left side
      // Example:
      // SELECT e1.*
      // FROM emp e1
      // JOIN dept d
      //   ON e1.deptno = d.deptno
      //   AND d.deptno IN (
      //     SELECT e3.empno
      //     FROM emp e3
      //     WHERE d.deptno > e3.comm
      //   )
      // ORDER BY e1.empno, e1.deptno;
      //
      // LogicalJoin(condition=[AND(=($7, $8), IN(CAST($8):SMALLINT NOT NULL, {
      // LogicalProject(EMPNO=[$0])
      //   LogicalFilter(condition=[>(CAST($cor0.DEPTNO0):DECIMAL(7, 2) NOT NULL, $6)])
      //     LogicalTableScan(table=[[scott, EMP]])
      // }))], joinType=[inner])
      //   LogicalTableScan(table=[[scott, EMP]])
      //   LogicalProject(DEPTNO=[$0])
      //     LogicalTableScan(table=[[scott, DEPT]])
      //
      // Rewrite to:
      //
      // LogicalProject(EMPNO=[$0], ENAME=[$1], ..., COMM=[$6], DEPTNO=[$7], DEPTNO0=[$8])
      //   LogicalJoin(condition=[=($7, $8)], joinType=[inner])
      //     LogicalTableScan(table=[[scott, EMP]])
      //     LogicalFilter(condition=[=(CAST($0):SMALLINT NOT NULL, $1)])
      //       LogicalCorrelate(correlation=[$cor0], joinType=[inner], requiredColumns=[{0}])
      //         LogicalProject(DEPTNO=[$0])
      //           LogicalTableScan(table=[[scott, DEPT]])
      //         LogicalProject(EMPNO=[$0])
      //           LogicalFilter(condition=[>(CAST($cor0.DEPTNO):DECIMAL(7, 2) NOT NULL, $6)])
      //             LogicalTableScan(table=[[scott, EMP]])
      CorrelationId id = Iterables.getOnlyElement(variablesSet);
      RexBuilder rexBuilder = builder.getRexBuilder();

      RelNode newSubQueryRel = e.rel.accept(new RelHomogeneousShuttle() {
        @Override public RelNode visit(RelNode other) {
          RelNode node =
              RexUtil.shiftFieldAccess(rexBuilder, other, id, join.getRight(), -nFieldsLeft);
          return super.visit(node);
        }
      });
      subQuery = e.clone(newSubQueryRel);
    }
    subQuery =
        subQuery.clone(subQuery.getType(), RexUtil.shift(subQuery.getOperands(), -nFieldsLeft));

    final int nFields = join.getRowType().getFieldCount();
    final RexNode target =
        rule.apply(subQuery, variablesSet, logic, builder, 1, nFieldsRight, 0, rule.isRuleEnabledOnlyForCorrelation);

    if (target == null)
    {
      return;
    }
    final RexShuttle shuttle = new ReplaceSubQueryShuttle(e, RexUtil.shift(target, nFieldsLeft));

    RelNode newRight = builder.build();
    builder.push(join.getLeft());
    builder.push(newRight);

    builder.join(join.getJoinType(), shuttle.apply(join.getCondition()));
    builder.project(fields(builder, nFields));
  }

  CalciteForkSettings.recordSubqueryInJoin(call.getPlanner().getContext());

  call.transformTo(builder.build());
}

/** Shuttle that replaces occurrences of a given
 * {@link org.apache.calcite.rex.RexSubQuery} with a replacement
 * expression. */
private static class ReplaceSubQueryShuttle extends RexShuttle {
  private final RexSubQuery subQuery;
  private final RexNode replacement;

  ReplaceSubQueryShuttle(RexSubQuery subQuery, RexNode replacement) {
    this.subQuery = subQuery;
    this.replacement = replacement;
  }

  @Override public RexNode visitSubQuery(RexSubQuery subQuery) {
    return subQuery.equals(this.subQuery) ? replacement : subQuery;
  }
}
/** Rule configuration. */
@Value.Immutable(singleton = false)
public interface Config extends RelRule.Config {
  Config PROJECT = ImmutableE6OldSubQueryRemoveRule.Config.builder()
      .withMatchHandler(E6OldSubQueryRemoveRule::matchProject)
      .build()
      .withOperandSupplier(b ->
          b.operand(Project.class)
              .predicate(RexUtil.SubQueryFinder::containsSubQuery).anyInputs())
      .withDescription("SubQueryRemoveRule:Project");

  Config FILTER = ImmutableE6OldSubQueryRemoveRule.Config.builder()
      .withMatchHandler(E6OldSubQueryRemoveRule::matchFilter)
      .build()
      .withOperandSupplier(b ->
          b.operand(Filter.class)
              .predicate(RexUtil.SubQueryFinder::containsSubQuery).anyInputs())
      .withDescription("SubQueryRemoveRule:Filter");

  Config JOIN = ImmutableE6OldSubQueryRemoveRule.Config.builder()
      .withMatchHandler(E6OldSubQueryRemoveRule::matchJoin)
      .build()
      .withOperandSupplier(b ->
          b.operand(Join.class)
              .predicate(RexUtil.SubQueryFinder::containsSubQuery)
              .anyInputs())
      .withDescription("SubQueryRemoveRule:Join");

  @Override default E6OldSubQueryRemoveRule toRule() {
    return new E6OldSubQueryRemoveRule(this);
  }

  /** Forwards a call to {@link #onMatch(RelOptRuleCall)}. */
  MatchHandler<E6OldSubQueryRemoveRule> matchHandler();

  /** Sets {@link #matchHandler()}. */
  Config withMatchHandler(MatchHandler<E6OldSubQueryRemoveRule> matchHandler);

  @Value.Default default boolean isRuleEnabledOnlyForCorrelatedQuery() {
    return false;
  }

    Config withRuleEnabledOnlyForCorrelatedQuery(boolean ruleEnabledForCorrelatedQuery);

    @Value.Default default boolean forceNonNullableSubqueryOpt() {
        return false;
    }
    Config withForceNonNullableSubqueryOpt(boolean forceNonNullableSubqueryOpt);
}
}
