package org.apache.calcite.rel.rules;

import com.google.common.collect.ImmutableList;
import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.rel.core.Aggregate;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.core.Values;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.Util;
import org.immutables.value.Value;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Rule that applies {@link Aggregate} to a {@link Values} (currently just an empty {@code Value}s).
 *
 * <p>This is still useful because {@link PruneEmptyRules#AGGREGATE_INSTANCE}
 * doesn't handle {@code Aggregate}, which is in turn because {@code Aggregate} of empty relations need some special
 * handling: a single row will be generated, where each column's value depends on the specific aggregate calls (e.g.
 * COUNT is 0, SUM is NULL).
 *
 * <p>Sample query where this matters:
 *
 * <blockquote><code>SELECT COUNT(*) FROM s.foo WHERE 1 = 0</code></blockquote>
 *
 * <p>This rule only applies to "grand totals", that is, {@code GROUP BY ()}.
 * Any non-empty {@code GROUP BY} clause will return one row per group key value, and each group will consist of at
 * least one row.
 *
 * @see CoreRules#AGGREGATE_VALUES
 */
@Value.Enclosing
public class E6AggregateValuesRule extends RelRule<E6AggregateValuesRule.Config> implements SubstitutionRule
{

/**
 * Creates an AggregateValuesRule.
 */
protected E6AggregateValuesRule(E6AggregateValuesRule.Config config)
{
    super(config);
}

@Override
public void onMatch(RelOptRuleCall call)
{
    final Aggregate aggregate = call.rel(0);
    final Values values = call.rel(1);
    Util.discard(values);
    final RelBuilder relBuilder = call.builder();
    final RexBuilder rexBuilder = relBuilder.getRexBuilder();

    final List<RexLiteral> literals = new ArrayList<>();
    for (final AggregateCall aggregateCall : aggregate.getAggCallList())
    {
        SqlKind kind = aggregateCall.getAggregation().getKind();
        switch (kind)
        {
            case COUNT:
            case SUM0:
                literals.add(rexBuilder.makeLiteral(BigDecimal.ZERO, aggregateCall.getType()));
                break;

            case MIN:
            case MAX:
            case SUM:
            case AVG:
            case PERCENTILE_CONT:
                literals.add(rexBuilder.makeNullLiteral(aggregateCall.getType()));
                break;

            default:
                if ("APPROX_PERCENTILE".equals(kind.name()))
                {
                    literals.add(rexBuilder.makeNullLiteral(aggregateCall.getType()));
                    break;
                }
                // Unknown what this aggregate call should do on empty Values. Bail out to be safe.
                return;
        }
    }

    call.transformTo(relBuilder.values(ImmutableList.of(literals), aggregate.getRowType()).build());

    // New plan is absolutely better than old plan.
    call.getPlanner().prune(aggregate);
}

/**
 * Rule configuration.
 */
@Value.Immutable
public interface Config extends RelRule.Config
{

    E6AggregateValuesRule.Config DEFAULT = ImmutableE6AggregateValuesRule.Config.of()
        .withOperandFor(Aggregate.class, Values.class);

    @Override
    default E6AggregateValuesRule toRule()
    {
        return new E6AggregateValuesRule(this);
    }

    /**
     * Defines an operand tree for the given classes.
     */
    default E6AggregateValuesRule.Config withOperandFor(Class<? extends Aggregate> aggregateClass,
        Class<? extends Values> valuesClass)
    {
        return withOperandSupplier(b0 -> b0.operand(aggregateClass)
            .predicate(aggregate -> aggregate.getGroupCount() == 0)
            .oneInput(b1 -> b1.operand(valuesClass).predicate(values -> values.getTuples().isEmpty()).noInputs())).as(
            E6AggregateValuesRule.Config.class);
    }

}

}
