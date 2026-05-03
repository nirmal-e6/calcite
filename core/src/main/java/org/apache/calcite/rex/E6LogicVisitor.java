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
package org.apache.calcite.rex;

import org.apache.calcite.config.CalciteForkSettings;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelOptUtil.Logic;

import com.google.common.collect.Iterables;

import org.apache.calcite.rel.RelHomogeneousShuttle;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.metadata.RelColumnOrigin;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Visitor pattern for traversing a tree of {@link RexNode} objects.
 */
public class E6LogicVisitor extends RexUnaryBiVisitor<@Nullable Logic> {
private final RexNode seek;
private final Collection<Logic> logicCollection;
private final LogicalFilter m_logicalFilter;
private static final Logger s_logger = LoggerFactory.getLogger(E6LogicVisitor.class);

/** Creates a LogicVisitor. */
private E6LogicVisitor(RexNode seek, Collection<Logic> logicCollection, LogicalFilter filter) {
    super(true);
    this.seek = seek;
    this.logicCollection = logicCollection;
    this.m_logicalFilter = filter;
}

/** Finds a suitable logic for evaluating {@code seek} within a list of
 * expressions.
 *
 * <p>Chooses a logic that is safe (that is, gives the right
 * answer) with the fewest possibilities (that is, we prefer one that
 * returns [true as true, false as false, unknown as false] over one that
 * distinguishes false from unknown).
 */
public static Logic find(Logic logic, List<RexNode> nodes,
    RexNode seek, LogicalFilter filter) {
    s_logger.debug("[E6LogicVisitor] inside find method of E6LogicVisitor");
    final Set<Logic> set = EnumSet.noneOf(Logic.class);
    final E6LogicVisitor visitor = new E6LogicVisitor(seek, set, filter);
    for (RexNode node : nodes) {
        node.accept(visitor, logic);
    }
    // Convert FALSE (which can only exist within LogicVisitor) to
    // UNKNOWN_AS_TRUE.
    if (set.remove(Logic.FALSE)) {
        set.add(Logic.UNKNOWN_AS_TRUE);
    }
    s_logger.debug("set size : {}", set.size());
    switch (set.size()) {
        case 0:
            throw new IllegalArgumentException("not found: " + seek);
        case 1:
            return Iterables.getOnlyElement(set);
        default:
            return Logic.TRUE_FALSE_UNKNOWN;
    }
}

public static void collect(RexNode node, RexNode seek, Logic logic,
    List<Logic> logicList, LogicalFilter filter) {
    s_logger.debug("[E6LogicVisitor] inside collect");
    node.accept(new E6LogicVisitor(seek, logicList, filter), logic);
    // Convert FALSE (which can only exist within LogicVisitor) to
    // UNKNOWN_AS_TRUE.
    Collections.replaceAll(logicList, Logic.FALSE, Logic.UNKNOWN_AS_TRUE);
}

@Override public @Nullable Logic visitCall(RexCall call, @Nullable Logic logic) {
    s_logger.debug("[E6LogicVisitor] inside visitCall");
    s_logger.debug("call Kind is : {}", call.getKind());
    final Logic arg0 = logic;
    switch (call.getKind()) {
        case IS_NOT_NULL:
        case IS_NULL:
            logic = Logic.TRUE_FALSE_UNKNOWN;
            break;
        case IS_TRUE:
        case IS_NOT_TRUE:
            logic = Logic.UNKNOWN_AS_FALSE;
            break;
        case IS_FALSE:
        case IS_NOT_FALSE:
            logic = Logic.UNKNOWN_AS_TRUE;
            break;
        case NOT:
            logic = requireNonNull(logic, "logic").negate2();
            break;
        case CASE:
            logic = Logic.TRUE_FALSE_UNKNOWN;
            break;
        default:
            break;
    }
    switch (requireNonNull(logic, "logic")) {
        case TRUE:
            switch (call.getKind()) {
                case AND:
                    break;
                default:
                    logic = Logic.TRUE_FALSE_UNKNOWN;
            }
            break;
        default:
            break;
    }
    for (RexNode operand : call.operands) {
        operand.accept(this, logic);
    }
    return end(call, arg0);
}

@Override protected @Nullable Logic end(RexNode node, @Nullable Logic arg) {
    s_logger.debug("[E6LogicVisitor] inside end");
    if (node.equals(seek)) {
        logicCollection.add(requireNonNull(arg, "arg"));
    }
    return arg;
}

@Override public @Nullable Logic visitOver(RexOver over, @Nullable Logic arg) {
    s_logger.debug("[E6LogicVisitor] inside visitOver");
    return end(over, arg);
}

@Override public @Nullable Logic visitFieldAccess(RexFieldAccess fieldAccess,
    @Nullable Logic arg) {
    s_logger.debug("[E6LogicVisitor] inside visitFieldAccess");
    return end(fieldAccess, arg);
}

@Override public @Nullable Logic visitSubQuery(RexSubQuery subQuery, @Nullable Logic arg) {
    boolean disabledNullability = false;
    boolean haveNullValuesInColumn = false;

    s_logger.debug("[E6LogicVisitor] inside  visitSubQuery");
    if(subQuery.rel.getRowType().getFieldList().size() == 1 && subQuery.operands.size() == 1)
    {
        RelMetadataQuery mq = subQuery.rel.getCluster().getMetadataQuery();
        Set<RexNode> expressionLineage = mq.getExpressionLineage(m_logicalFilter, subQuery.operands.get(0));
        if(expressionLineage != null)
        {
            s_logger.debug("expression lineage is not null... proceeding further");
            List<RexNode> subqueryOperandColumnNode =
                expressionLineage.stream().collect(Collectors.toList());

            // if operand column is present and is RexTableInputRef
            if (subqueryOperandColumnNode.size() == 1 && subqueryOperandColumnNode.get(
                0) instanceof RexTableInputRef)
            {
                RexTableInputRef inputRef = (RexTableInputRef) subqueryOperandColumnNode.get(0);
                s_logger.debug("subquery has 1 operand column and is a RexTableInputRef");
                RelColumnOrigin columnOrigin = mq.getColumnOrigin(subQuery.rel, 0);
                if (columnOrigin != null)
                {
                    s_logger.debug("column origin is not null... proceeding further");
                    // get fully qualified column name of subquery's projection
                    int originColumnOrdinal = columnOrigin.getOriginColumnOrdinal();
                    RelOptTable originTable = columnOrigin.getOriginTable();
                    List<String> fullyQualifiedSubqueryColumnName = new ArrayList<>(originTable.getQualifiedName());
                    String subqueryColumnName = originTable.getRowType()
                        .getFieldList()
                        .get(originColumnOrdinal)
                        .getName();
                    fullyQualifiedSubqueryColumnName.add(subqueryColumnName);
                    s_logger.debug("fully qualified subquery column name : {}",fullyQualifiedSubqueryColumnName);

                    // get fully qualified column name of operand column
                    List<String> fullyQualifiedOperandColumnName = new ArrayList<>(
                        inputRef.getTableRef().getQualifiedName());
                    int operandColumnOrdinal = inputRef.getIndex();
                    String operandColumnName = (inputRef.getTableRef()).getTable()
                        .getRowType()
                        .getFieldList()
                        .get(operandColumnOrdinal)
                        .getName();
                    fullyQualifiedOperandColumnName.add(operandColumnName);
                    s_logger.debug("fully qualified operand column name {}", fullyQualifiedOperandColumnName);

                    // if both column is same then only check for nullability
                    if (fullyQualifiedSubqueryColumnName.equals(fullyQualifiedOperandColumnName))
                    {
                        s_logger.debug("both columns as same, chacking for nulls...");
                        Long columnNumNulls = CalciteForkSettings.columnNumNulls(originTable, subqueryColumnName);
                        if(columnNumNulls != null)
                        {
                            if (columnNumNulls > 0)
                            {
                                haveNullValuesInColumn = true;
                                s_logger.debug("subquery column has null value : {}",
                                    columnNumNulls);
                            }
                            else
                            {
                                s_logger.debug("subquery column is present in map but value is : {}", columnNumNulls);
                            }
                        }
                        else
                        {
                            s_logger.debug("column null map is NULL");
                        }
                    }
                    else
                    {
                        s_logger.debug("both columns are different checking null for both columns");
                        Long columnNumNullsForSubquery =
                            CalciteForkSettings.columnNumNulls(originTable, subqueryColumnName);
                        Long columnNumNullsForOperand = CalciteForkSettings.columnNumNulls(
                            inputRef.getTableRef().getTable(), operandColumnName);

                        if(columnNumNullsForSubquery != null)
                        {
                            if (columnNumNullsForSubquery > 0)
                            {
                                haveNullValuesInColumn = true;
                                s_logger.debug("subquery column has null value : {}",
                                    columnNumNullsForSubquery);
                            }
                            else
                            {
                                s_logger.debug("subquery column is present in map but value is : {}", columnNumNullsForSubquery);
                            }
                        }
                        else
                        {
                            s_logger.debug("column null map is NULL for subquery");
                        }

                        if(columnNumNullsForOperand != null)
                        {
                            if (columnNumNullsForOperand > 0)
                            {
                                haveNullValuesInColumn = true;
                                s_logger.debug("operand column has null value : {}",
                                    columnNumNullsForOperand);
                            }
                            else
                            {
                                s_logger.debug("operand column is present in map but value is : {}", columnNumNullsForOperand);
                            }
                        }
                        else
                        {
                            s_logger.debug("column null map is NULL for operad");
                        }
                    }
                }
                else
                {
                    s_logger.debug("column origin is null... couldn't proceed further");
                }
            }
            else
            {
                s_logger.debug("subquery has {} operand column and is a {}", subqueryOperandColumnNode.size(), subqueryOperandColumnNode);
            }
        }
        else
        {
            s_logger.debug("expression lineage is null... couldn't proceed further");
        }
    }
    else
    {
        s_logger.debug("either subquery or operands list is greater than one");
        s_logger.debug("subquery columns size : {} operand columns size : {}", subQuery.rel.getRowType().getFieldList().size(),
            subQuery.operands.size());
    }

    E6SubqueryOptVisitor subqueryOptVisitor = new E6SubqueryOptVisitor(subQuery.rel);
    subQuery.rel.accept(subqueryOptVisitor);
    boolean canOptimizeNonNullableSubquery = subqueryOptVisitor.canOptimizeNonNullableSubquery();
    s_logger.debug("value of haveNullValuesInColumn : {}", haveNullValuesInColumn);
    s_logger.debug("value of canOptimizeNonNullableSubquery : {}", canOptimizeNonNullableSubquery);
    s_logger.debug("value of logic : {}",arg);
    s_logger.debug("value of ENABLE_NON_NULLABLE_SUBQUERY_OPT : {}", CalciteForkSettings.enableNonNullableSubqueryOpt());

    if(CalciteForkSettings.enableNonNullableSubqueryOpt() && arg == Logic.FALSE && !haveNullValuesInColumn
        && canOptimizeNonNullableSubquery)
    {
        arg = Logic.TRUE_FALSE;
        s_logger.debug("updating logic to TRUE_FALSE");
        disabledNullability = true;
    }
    if (!subQuery.getType().isNullable() && !disabledNullability)
    {
        if (arg == Logic.TRUE_FALSE_UNKNOWN) {
            s_logger.debug("updating logic to TRUE_FALSE");
            arg = Logic.TRUE_FALSE;
        }
    }
    s_logger.debug("value of logic after visitor : {}", arg);
    return end(subQuery, arg);
}

static class E6SubqueryOptVisitor extends RelHomogeneousShuttle
{
    private int totalJoins = 0;
    private int otherJoinCounter = 0;
    private boolean canOptimizeNonNullableSubquery = false;

    private final RelNode subquery;

    E6SubqueryOptVisitor(RelNode subquery)
    {
        this.subquery = subquery;
    }

    @Override
    public RelNode visit(LogicalJoin join)
    {
        s_logger.debug("[E6SubqueryOptVisitor] inside visit join");
        if(join.getJoinType() == JoinRelType.INNER)
        {
            s_logger.debug("[E6SubqueryOptVisitor] inside inner join condition");
            if(totalJoins == 0)
            {
                s_logger.debug("[E6SubqueryOptVisitor] updating canOptimizeNonNullableSubquery to true for INNER join");
                canOptimizeNonNullableSubquery = true;
                s_logger.debug("[E6SubqueryOptVisitor] canOptimizeNonNullableSubquery after update : {}", canOptimizeNonNullableSubquery);
            }
            else
            {
                s_logger.debug("[E6SubqueryOptVisitor] total joins are more than zero at this moment : {}", totalJoins);
            }
            s_logger.debug("[E6SubqueryOptVisitor] incrementing totalJoins count");
            totalJoins++;
            s_logger.debug("[E6SubqueryOptVisitor] totalJoins count after update : {}",totalJoins);
        }
        else if(join.getJoinType() == JoinRelType.LEFT)
        {
            s_logger.debug("[E6SubqueryOptVisitor] inside left join condition");
            if(totalJoins == 0 && subquery.getRowType().getFieldList().size() == 1)
            {
                s_logger.debug("[E6SubqueryOptVisitor] inside left join's second condition");
                RelDataTypeField subqueryOutputColumn = subquery.getRowType().getFieldList().get(0);
                RelNode rightChild = join.getRight();
                RelNode leftChild = join.getLeft();
                List<String> leftChildFieldNames = leftChild.getRowType().getFieldNames();
                List<String> leftChildFieldNamesLowerCase = new ArrayList<>();

                List<String> rightChildFieldNames = rightChild.getRowType().getFieldNames();
                List<String> rightChildFieldNamesLowerCase = new ArrayList<>();
                String subqueryColumnName = subqueryOutputColumn.getName().toLowerCase();

                for(String field : leftChildFieldNames)
                {
                    leftChildFieldNamesLowerCase.add(field.toLowerCase());
                }

                for(String field : rightChildFieldNames)
                {
                    rightChildFieldNamesLowerCase.add(field.toLowerCase());
                }

                s_logger.debug("[E6SubqueryOptVisitor] left child row type : {}", leftChildFieldNamesLowerCase);
                s_logger.debug("[E6SubqueryOptVisitor] right child row type : {}", rightChildFieldNamesLowerCase);
                s_logger.debug("[E6SubqueryOptVisitor] subqueryOutputColumn : {}", subqueryColumnName);

                boolean leftChildContainsSubqueryOutputColumn = leftChildFieldNamesLowerCase.contains(subqueryColumnName);
                boolean rightChildContainsSubqueryOutputColumn = rightChildFieldNamesLowerCase.contains(subqueryColumnName);

                s_logger.debug("[E6SubqueryOptVisitor] leftChildContainsSubqueryOutputColumn {} | rightChildContainsSubqueryOutputColumn {}",
                    leftChildContainsSubqueryOutputColumn, rightChildContainsSubqueryOutputColumn);

                if(leftChildContainsSubqueryOutputColumn &&
                    !rightChildContainsSubqueryOutputColumn)
                {
                    s_logger.debug("[E6SubqueryOptVisitor] updating canOptimizeNonNullableSubquery to true for LEFT join");
                    canOptimizeNonNullableSubquery = true;
                    s_logger.debug("[E6SubqueryOptVisitor] canOptimizeNonNullableSubquery after update : {}", canOptimizeNonNullableSubquery);
                }
                s_logger.debug("[E6SubqueryOptVisitor] outside left join's canOptimizeNonNullableSubquery changing condition");
            }
            else
            {
                s_logger.debug("[E6SubqueryOptVisitor] couldn't go inside left join's second condition");
                s_logger.debug("[E6SubqueryOptVisitor] totalJoins {} subquery field list size : {}", totalJoins,
                    subquery.getRowType().getFieldList().size());
            }
            s_logger.debug("[E6SubqueryOptVisitor] incrementing totalJoins count");
            totalJoins++;
            s_logger.debug("[E6SubqueryOptVisitor] totalJoins count after update : {}",totalJoins);
        }
        else
        {
            s_logger.debug("[E6SubqueryOptVisitor] Join is not left or right, it went into else condition");
            s_logger.debug("[E6SubqueryOptVisitor] current status of otherJoinCounter : {} | totalJoins : {}", otherJoinCounter, totalJoins);
            otherJoinCounter++;
            totalJoins++;
            s_logger.debug("[E6SubqueryOptVisitor] current status after updating of otherJoinCounter : {} | totalJoins : {}", otherJoinCounter, totalJoins);
        }
        s_logger.debug("[E6SubqueryOptVisitor] else condition exit line");
        return visitChildren(join);
    }

    public boolean canOptimizeNonNullableSubquery()
    {
        s_logger.debug("[E6SubqueryOptVisitor] canOptimizeNonNullableSubquery called");
        s_logger.debug("total joins : {} | can optimize non nullable subquery : {} | other joins : {}", totalJoins,
            canOptimizeNonNullableSubquery, otherJoinCounter);
        return totalJoins == 0 || (canOptimizeNonNullableSubquery && otherJoinCounter == 0) ;
    }
}

}
