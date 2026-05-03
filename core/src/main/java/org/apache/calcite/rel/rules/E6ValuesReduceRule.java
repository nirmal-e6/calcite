/*
 * Copyright (c) 2026 Uniphi Inc
 * All rights reserved.
 *
 * File Name: E6ValuesReduceRule.java
 *
 * Created On: 2026-03-02
 */

package org.apache.calcite.rel.rules;

import org.apache.calcite.plan.RelOptPredicateList;
import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rel.logical.LogicalValues;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexShuttle;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * E6 override of {@link ValuesReduceRule} that fixes incorrect tuple elimination.
 *
 * <p>Replaces {@code CoreRules.FILTER_VALUES_MERGE},
 * {@code CoreRules.PROJECT_VALUES_MERGE} and
 * {@code CoreRules.PROJECT_FILTER_VALUES_MERGE} in
 * planner constant-reduction rules.
 *
 * @see ValuesReduceRule
 */
public class E6ValuesReduceRule extends ValuesReduceRule
{

/** Replaces {@code CoreRules.FILTER_VALUES_MERGE}. */
public static final E6ValuesReduceRule FILTER_VALUES_MERGE = new E6ValuesReduceRule(ValuesReduceRule.Config.FILTER);

/** Replaces {@code CoreRules.PROJECT_VALUES_MERGE}. */
public static final E6ValuesReduceRule PROJECT_VALUES_MERGE = new E6ValuesReduceRule(ValuesReduceRule.Config.PROJECT);

/** Replaces {@code CoreRules.PROJECT_FILTER_VALUES_MERGE}. */
public static final E6ValuesReduceRule PROJECT_FILTER_VALUES_MERGE = new E6ValuesReduceRule(
    ValuesReduceRule.Config.PROJECT_FILTER);

protected E6ValuesReduceRule(Config config)
{
    super(config);
}

/**
 * Bug Fix: When filter codition is not reduced, it remains
 * as RexCall and Calcite does not handle it correctly.
 */
@Override
protected void apply(RelOptRuleCall call,
    @Nullable LogicalProject project,
    @Nullable LogicalFilter filter,
    LogicalValues values)
{
    if (filter != null)
    {
        final RexNode conditionExpr = filter.getCondition();
        final LiteralSubstShuttle shuttle = new LiteralSubstShuttle();

        for (final List<RexLiteral> literalList : values.getTuples())
        {
            shuttle.m_literalList = literalList;
            final RexNode substituted = conditionExpr.accept(shuttle);

            final List<RexNode> lstToReduce = new ArrayList<>();
            lstToReduce.add(substituted);

            ReduceExpressionsRule.reduceExpressions(
                values, lstToReduce, RelOptPredicateList.EMPTY,
                false, true, false);

            // If the expression is still a RexCall after full reduction (including simplification),
            // the executor could not evaluate it at plan time. Abort the optimization
            if (lstToReduce.get(0) instanceof RexCall)
            {
                return;
            }
        }
    }

    super.apply(call, project, filter, values);
}

/**
 * Substitutes {@link RexInputRef} nodes with literal values from a tuple row.
 * Mirrors the private {@code MyRexShuttle} inside {@link ValuesReduceRule}.
 */
private static class LiteralSubstShuttle
    extends RexShuttle
{

@Nullable List<RexLiteral> m_literalList;

@Override
public RexNode visitInputRef(RexInputRef inputRef)
{
    return requireNonNull(m_literalList, "m_literalList").get(inputRef.getIndex());
}

} ///////// End of class LiteralSubstShuttle

} ///////// End of class
