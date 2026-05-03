/*
 * Copyright (c) 2025 Uniphi Inc
 * All rights reserved.
 *
 * File Name: E6EnforceJoinOrderLoptRule.java
 *
 * Created On: 2025-10-20
 */

package org.apache.calcite.rel.rules;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Join;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.core.RelFactories;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.tools.RelBuilderFactory;
import org.apache.calcite.util.*;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.immutables.value.Value;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import static java.util.Objects.requireNonNull;

@Value.Enclosing
public class E6EnforceJoinOrderLoptRule extends RelRule<E6EnforceJoinOrderLoptRule.Config>
    implements TransformationRule
{

public interface JoinOrderContext
{
    Map<?, List<Integer>> getJoinFactorMap();

    Object getLiveMultiJoinKey(MultiJoin multiJoinRel);
}

protected E6EnforceJoinOrderLoptRule(Config config) {
    super(config);
}

@Deprecated // to be removed before 2.0
public E6EnforceJoinOrderLoptRule(RelBuilderFactory relBuilderFactory) {
    this(Config.DEFAULT.withRelBuilderFactory(relBuilderFactory)
        .as(Config.class));
}

@Deprecated // to be removed before 2.0
public E6EnforceJoinOrderLoptRule(RelFactories.JoinFactory joinFactory,
    RelFactories.ProjectFactory projectFactory,
    RelFactories.FilterFactory filterFactory)
{
    this(RelBuilder.proto(joinFactory, projectFactory, filterFactory));
}

//~ Methods ----------------------------------------------------------------

@Override public void onMatch(RelOptRuleCall call)
{
    final MultiJoin multiJoinRel = call.rel(0);
    JoinOrderContext context = call.getPlanner().getContext().unwrap(JoinOrderContext.class);
    if (context == null)
    {
        return;
    }

    Object liveMJSpec = context.getLiveMultiJoinKey(multiJoinRel);

    final LoptMultiJoin multiJoin = new LoptMultiJoin(multiJoinRel);
    multiJoin.setFactorWeights();
    extractJoinOrdering(call, multiJoin, liveMJSpec, context);
}

/**
 * Generates N optimal join orderings. Each ordering contains each factor as the first factor in the ordering.
 *
 * @param multiJoin join factors being optimized
 * @param call RelOptRuleCall associated with this rule
 */
private void extractJoinOrdering(
    RelOptRuleCall call,
    LoptMultiJoin multiJoin,
    Object liveMJSpec,
    JoinOrderContext context)
{
    final List<String> fieldNames = multiJoin.getMultiJoinRel().getRowType().getFieldNames();
    if (context.getJoinFactorMap().isEmpty())
    {
        return;
    }

    LoptJoinTree joinTree = enforceOrdering(call, multiJoin, context.getJoinFactorMap().get(liveMJSpec));
    RelNode newProject = createTopProject(call.builder(), multiJoin, joinTree, fieldNames);
    call.transformTo(newProject);
}

/**
 * Creates the topmost projection that will sit on top of the selected join
 * ordering. The projection needs to match the original join ordering. Also,
 * places any post-join filters on top of the project.
 *
 * @param multiJoin join factors being optimized
 * @param joinTree selected join ordering
 * @param fieldNames field names corresponding to the projection expressions
 *
 * @return created projection
 */
@SuppressWarnings("deprecation")
private static RelNode createTopProject(
    RelBuilder relBuilder,
    LoptMultiJoin multiJoin,
    LoptJoinTree joinTree,
    List<String> fieldNames)
{
    List<RexNode> newProjExprs = new ArrayList<>();
    RexBuilder rexBuilder = multiJoin.getMultiJoinRel().getCluster().getRexBuilder();

    // create a projection on top of the joins, matching the original
    // join order
    final List<Integer> newJoinOrder = joinTree.getTreeOrder();
    int nJoinFactors = multiJoin.getNumJoinFactors();
    List<RelDataTypeField> fields = multiJoin.getMultiJoinFields();

    // create a mapping from each factor to its field offset in the join
    // ordering
    final Map<Integer, Integer> factorToOffsetMap = new HashMap<>();
    for (int pos = 0, fieldStart = 0; pos < nJoinFactors; pos++) {
        factorToOffsetMap.put(newJoinOrder.get(pos), fieldStart);
        fieldStart +=
            multiJoin.getNumFieldsInJoinFactor(newJoinOrder.get(pos));
    }

    for (int currFactor = 0; currFactor < nJoinFactors; currFactor++) {
        // if the factor is the right factor in a removable self-join,
        // then where possible, remap references to the right factor to
        // the corresponding reference in the left factor
        Integer leftFactor = null;
        if (multiJoin.isRightFactorInRemovableSelfJoin(currFactor)) {
            leftFactor = multiJoin.getOtherSelfJoinFactor(currFactor);
        }
        for (int fieldPos = 0;
            fieldPos < multiJoin.getNumFieldsInJoinFactor(currFactor);
            fieldPos++) {
            int newOffset = requireNonNull(factorToOffsetMap.get(currFactor),
                () -> "factorToOffsetMap.get(currFactor)") + fieldPos;
            if (leftFactor != null) {
                Integer leftOffset =
                    multiJoin.getRightColumnMapping(currFactor, fieldPos);
                if (leftOffset != null) {
                    newOffset =
                        requireNonNull(factorToOffsetMap.get(leftFactor),
                            "factorToOffsetMap.get(leftFactor)") + leftOffset;
                }
            }
            //            newProjExprs.add(
            //                rexBuilder.makeInputRef(
            //                    fields.get(newProjExprs.size()).getType(),
            //                    newOffset));
            final RelDataType inputType = joinTree.getJoinTree().getRowType().getFieldList().get(newOffset).getType();
            final RelDataType targetType = fields.get(newProjExprs.size()).getType();

            RexNode ref = rexBuilder.makeInputRef(inputType, newOffset);
            if (!RelOptUtil.eq("target", targetType, "source" , inputType, Litmus.IGNORE))
            {
                ref = rexBuilder.makeCast(targetType, ref, true);
            }
            newProjExprs.add(ref);
        }
    }

    relBuilder.push(joinTree.getJoinTree());
    relBuilder.project(newProjExprs, fieldNames);

    // Place the post-join filter (if it exists) on top of the final
    // projection.
    RexNode postJoinFilter = multiJoin.getMultiJoinRel().getPostJoinFilter();
    if (postJoinFilter != null)
    {
        relBuilder.filter(postJoinFilter);
    }
    return relBuilder.build();
}

/**
 * Locates from a list of filters those that correspond to a particular join
 * tree. Then, for each of those filters, extracts the fields corresponding
 * to a particular factor, setting them in a bitmap.
 *
 * @param multiJoin join factors being optimized
 * @param filters list of join filters
 * @param joinFactors bitmap containing the factors in a particular join
 * tree
 * @param factorStart the initial offset of the factor whose join keys will
 * be extracted
 * @param nFields the number of fields in the factor whose join keys will be
 * extracted
 * @param joinKeys the bitmap that will be set with the join keys
 */
private static void setFactorJoinKeys(
    LoptMultiJoin multiJoin,
    List<RexNode> filters,
    ImmutableBitSet joinFactors,
    int factorStart,
    int nFields,
    ImmutableBitSet.Builder joinKeys)
{
    for (RexNode joinFilter : filters)
    {
        ImmutableBitSet filterFactors =
            multiJoin.getFactorsRefByJoinFilter(joinFilter);

        // if all factors in the join filter are in the bitmap containing
        // the factors in a join tree, then from that filter, add the
        // fields corresponding to the specified factor to the join key
        // bitmap; in doing so, adjust the join keys so they start at
        // offset 0
        if (joinFactors.contains(filterFactors))
        {
            ImmutableBitSet joinFields =
                multiJoin.getFieldsRefByJoinFilter(joinFilter);
            for (int field = joinFields.nextSetBit(factorStart); (field >= 0) && (field < (factorStart + nFields));
                field = joinFields.nextSetBit(field + 1))
            {
                joinKeys.set(field - factorStart);
            }
        }
    }
}

/**
 * Generates a join tree with a specific factor as the first factor in the
 * join tree.
 */
private @Nullable LoptJoinTree enforceOrdering(RelOptRuleCall call, LoptMultiJoin multiJoin, List<Integer> joinFactorList)
{
    LoptJoinTree joinTree = null;
    final int nJoinFactors = multiJoin.getNumJoinFactors();
    final BitSet factorsToAdd = BitSets.range(0, nJoinFactors);
    final BitSet factorsAdded = new BitSet(nJoinFactors);
    final List<RexNode> filtersToAdd = new ArrayList<>(multiJoin.getJoinFilters());

    BitSet rightPendingJoins = new BitSet();
    for (int i = 0; i < multiJoin.getMultiJoinRel().getJoinTypes().size(); i++)
    {
        JoinRelType joinType = multiJoin.getMultiJoinRel().getJoinTypes().get(i);
        if (joinType == JoinRelType.RIGHT)
        {
            rightPendingJoins.set(i);
        }
    }

    int cursor = joinFactorList.get(0).intValue();
    int index = 0;

    while (!factorsToAdd.isEmpty())
    {
        // find the next remaining factor in input order
        int nextFactor = -1;
        for (int k = 0; k < nJoinFactors; k++)
        {
            int idx = cursor + k;
            if (factorsToAdd.get(idx))
            {
                nextFactor = idx;
                break;
            }
        }
        if (nextFactor < 0)
        {
            break;
        }

        // self-join hint: place the pair right after the first half
        boolean selfJoin = false;
        Integer mate = multiJoin.getOtherSelfJoinFactor(nextFactor);
        if (mate != null && !factorsAdded.get(mate))
        {
            selfJoin = true;
        }

        // add the factor; pass in a bitmap representing the factors
        // this factor joins with that have already been added to
        // the tree
        BitSet factorsNeeded = multiJoin.getFactorsRefByFactor(nextFactor).toBitSet();
        factorsNeeded.and(factorsAdded);

        joinTree =
            addFactorToTree(
                call,
                multiJoin,
                joinTree,
                nextFactor,
                factorsNeeded,
                filtersToAdd,
                selfJoin,
                rightPendingJoins);
        if (joinTree == null)
        {
            return null;
        }
        factorsToAdd.clear(nextFactor);
        factorsAdded.set(nextFactor);
        index = index + 1;

        if (index >= joinFactorList.size())
        {
            break;
        }
        cursor = joinFactorList.get(index);
    }

    //assert filtersToAdd.isEmpty();
    return joinTree;
}

/**
 * Returns whether a RelNode corresponds to a Join that wasn't one of the
 * original MultiJoin input factors.
 */
private static boolean isJoinTree(RelNode rel)
{
    // full outer joins were already optimized in a prior instantiation
    // of this rule; therefore we should never see a join input that's
    // a full outer join
    if (rel instanceof Join)
    {
        assert ((Join) rel).getJoinType() != JoinRelType.FULL;
        return true;
    }
    else
    {
        return false;
    }
}

/**
 * Adds a new factor into the current join tree. The factor is either pushed
 * down into one of the subtrees of the join recursively, or it is added to
 * the top of the current tree, whichever yields a better ordering.
 *
 * @param multiJoin join factors being optimized
 * @param joinTree current join tree
 * @param factorToAdd new factor to be added
 * @param factorsNeeded factors that must precede the factor to be added
 * @param filtersToAdd filters remaining to be added; filters added to the
 * new join tree are removed from the list
 * @param selfJoin true if the join being created is a self-join that's
 * removable
 *
 * @return optimal join tree with the new factor added if it is possible to
 * add the factor; otherwise, null is returned
 */
private @Nullable LoptJoinTree addFactorToTree(
    RelOptRuleCall call,
    LoptMultiJoin multiJoin,
    @Nullable LoptJoinTree joinTree,
    int factorToAdd,
    BitSet factorsNeeded,
    List<RexNode> filtersToAdd,
    boolean selfJoin,
    BitSet rightPendingJoins)
{
    final RelMetadataQuery mq = call.getMetadataQuery();
    final RelBuilder relBuilder = call.builder();

    // if this is the first factor in the tree, create a join tree with
    // the single factor
    if (joinTree == null)
    {
        return new LoptJoinTree(multiJoin.getJoinFactor(factorToAdd), factorToAdd);
    }

    // Create a temporary copy of the filter list as we may need the
    // original list to pass into addToTop().  However, if no tree was
    // created by addToTop() because the factor being added is part of
    // a self-join, then pass the original filter list so the added
    // filters will still be removed from the list.
    LoptJoinTree topTree =
        addToTop(
            mq,
            relBuilder,
            multiJoin,
            joinTree,
            factorToAdd,
            filtersToAdd,
            selfJoin,
            rightPendingJoins);

    LoptJoinTree pushDownTree =
        pushDownFactor(
            call,
            multiJoin,
            joinTree,
            factorToAdd,
            factorsNeeded,
            new ArrayList<>(filtersToAdd),
            selfJoin,
            rightPendingJoins);

    //    RelOptCost costOfTopTree = (topTree == null) ? null : config.costFunction().getCost(call, topTree.getJoinTree());
    //    RelOptCost costOfPushDownTree  = (pushDownTree  == null) ? null : config.costFunction().getCost(call, pushDownTree.getJoinTree());

    LoptJoinTree bestTree = null;
    if (topTree == null)
    {
        bestTree = pushDownTree;
    }
    else if (pushDownTree == null)
    {
        bestTree = topTree;
    }
    //    else if (costOfPushDownTree != null && costOfTopTree != null && costOfPushDownTree.isLt(costOfTopTree))
    //    {
    //        bestTree = pushDownTree;
    //    }
    //    else if (costOfPushDownTree != null && costOfTopTree != null && costOfPushDownTree.isEqWithEpsilon(costOfTopTree)
    //        && rowWidthCost(pushDownTree.getJoinTree()) < rowWidthCost(topTree.getJoinTree()))
    //    {
    //        bestTree = pushDownTree;
    //    }
    else
    {
        bestTree = topTree;
    }

    return bestTree;
}

private @Nullable LoptJoinTree pushDownFactor(
    RelOptRuleCall call,
    LoptMultiJoin multiJoin,
    LoptJoinTree joinTree,
    int factorToAdd,
    BitSet factorsNeeded,
    List<RexNode> filtersToAdd,
    boolean selfJoin,
    BitSet rightPendingJoins)
{
    // We can only push into a *join*; otherwise, new factor must go on top.
    if (!isJoinTree(joinTree.getJoinTree()))
    {
        return null;
    }

    // Never cross a removable self-join boundary.
    if (joinTree.isRemovableSelfJoin())
    {
        return null;
    }

    // We *only* descend the RIGHT spine to preserve leaf order.
    final LoptJoinTree left  = requireNonNull(joinTree.getLeft(),  "left");
    final LoptJoinTree right = requireNonNull(joinTree.getRight(), "right");
    final Join topJoin       = (Join) joinTree.getJoinTree();
    final JoinRelType jt     = topJoin.getJoinType();

    // If the current top join null-generates on the right, we cannot push
    // a new factor into that side (outer-join semantics).
    if (jt.generatesNullsOnRight())
    {
        return null;
    }

    // Only push if the RIGHT subtree contains *all* partners the new factor needs.
    if (!multiJoin.hasAllFactors(right, factorsNeeded))
    {
        return null;
    }

    // Remember the original join order to adjust any existing ON conditions later.
    final List<Integer> origJoinOrder = joinTree.getTreeOrder();

    // Recurse: insert 'factorToAdd' *inside the right subtree*.
    LoptJoinTree newRight =
        addFactorToTree(
            call,
            multiJoin,
            right,
            factorToAdd,
            factorsNeeded,
            filtersToAdd,
            selfJoin,
            rightPendingJoins);

    if (newRight == null)
    {
        // Could not place the factor deeper while preserving constraints.
        return null;
    }

    // Left side stays as-is; right side is the updated subtree.
    LoptJoinTree newLeft = left;

    // Adjust the original top join condition to reflect the new right subtree
    // (no swapping is performed; we preserve input order and sides).
    RexNode newTopCond = topJoin.getCondition();
    newTopCond =
        adjustFilter(
            multiJoin,
            requireNonNull(newLeft,  "newLeft"),
            requireNonNull(newRight, "newRight"),
            newTopCond,
            factorToAdd,
            origJoinOrder,
            joinTree.getJoinTree().getRowType().getFieldList());

    // For INNER, we can attach earliest-legal additional conjuncts now.
    // For LEFT/RIGHT outers, extra conjuncts get added on top in createJoinSubtree.
    if (jt != JoinRelType.LEFT && jt != JoinRelType.RIGHT)
    {
        final RexBuilder rb = multiJoin.getMultiJoinRel().getCluster().getRexBuilder();
        RexNode extra =
            addFilters(
                multiJoin,
                newLeft,
                -1,
                newRight,
                filtersToAdd,
                /*adjust*/ true);
        newTopCond = RelOptUtil.andJoinFilters(rb, newTopCond, extra);
    }

    // Rebuild the top join with the *same join type* and *no input swap*.
    return createJoinSubtree(
        call.getMetadataQuery(),
        call.builder(),
        multiJoin,
        newLeft,
        newRight,
        newTopCond,
        jt,               // keep original LEFT/RIGHT/INNER/FULL of the top
        filtersToAdd,
        /*fullAdjust*/ false,
        /*selfJoin*/   false);
}

private static JoinRelType getJoinTypeInStep(LoptMultiJoin multiJoin, BitSet leftInputs, int newFactorOnRightSide)
{
    if (multiJoin.getMultiJoinRel().isFullOuterJoin())
    {
        return JoinRelType.FULL;
    }

    final List<JoinRelType> joinTypes = multiJoin.getMultiJoinRel().getJoinTypes();
    if (joinTypes.get(newFactorOnRightSide) == JoinRelType.LEFT)
    {
        return JoinRelType.LEFT;
    }
    for (int leftFactor = leftInputs.nextSetBit(0); leftFactor >= 0; leftFactor = leftInputs.nextSetBit(leftFactor + 1))
    {
        if (joinTypes.get(leftFactor) == JoinRelType.RIGHT)
        {
            // some left factor null-generating
            return JoinRelType.RIGHT;
        }
    }
    return JoinRelType.INNER;
}

private static JoinRelType getJoinType(LoptMultiJoin multiJoin, BitSet leftInputs, int rightBit, BitSet rightPendingJoins)
{
    if (multiJoin.getMultiJoinRel().isFullOuterJoin())
    {
        return JoinRelType.FULL;
    }

    List<JoinRelType> joinTypes = multiJoin.getMultiJoinRel().getJoinTypes();

    // when a right-join-marked input already on the left meets its partner (the new right).
    for (int j = rightPendingJoins.nextSetBit(0); j >= 0; j = rightPendingJoins.nextSetBit(j + 1))
    {
        if (leftInputs.get(j) && multiJoin.getOuterJoinCond(j) != null)
        {
            RexNode joinFilter = multiJoin.getOuterJoinCond(j);

            List<RexNode> joinFilterExprs = RelOptUtil.conjunctions(joinFilter);
            ImmutableBitSet partnersOfLeft = ImmutableBitSet.of();
            for (RexNode joinPredicate: joinFilterExprs)
            {
                partnersOfLeft = multiJoin.getFactorsRefByJoinFilter(joinPredicate);
                partnersOfLeft = partnersOfLeft.clear(j);
            }

            if (partnersOfLeft != null && partnersOfLeft.get(rightBit))
            {
                rightPendingJoins.clear(j);

                // left side is null generating; hence this join is left join
                return JoinRelType.RIGHT;
            }
        }
    }

    if (multiJoin.getOuterJoinCond(rightBit) != null)
    {
        RexNode joinFilter = multiJoin.getOuterJoinCond(rightBit);

        List<RexNode> joinFilterExprs = RelOptUtil.conjunctions(joinFilter);
        ImmutableBitSet partnersOfRight = ImmutableBitSet.of();
        for (RexNode joinPredicate: joinFilterExprs)
        {
            partnersOfRight = multiJoin.getFactorsRefByJoinFilter(joinPredicate);
            partnersOfRight = partnersOfRight.clear(rightBit);
        }

        // when the new right input is LEFT-marked and meets a partner on the left.
        if (joinTypes.get(rightBit) == JoinRelType.LEFT)
        {
            if (partnersOfRight != null && intersects(leftInputs, partnersOfRight))
            {
                // right side is null generating; hence this join is left join
                return JoinRelType.LEFT;
            }
        }

        // right side is RIGHT-marked, but we're attaching it on the right;
        // that’s semantically a LEFT step with the roles flipped.
        if (joinTypes.get(rightBit) == JoinRelType.RIGHT)
        {
            if (partnersOfRight != null && intersects(leftInputs, partnersOfRight))
            {
                return JoinRelType.LEFT;
            }
        }
    }

    // No outer edge is being realized at this step → INNER.
    return JoinRelType.INNER;
}

private static boolean intersects(BitSet leftInputs, ImmutableBitSet partners)
{
    for (int p = partners.nextSetBit(0); p >= 0; p = partners.nextSetBit(p+1))
    {
        if (leftInputs.get(p))
        {
            return true;
        }
    }
    return false;
}

private static int rightOwner(LoptMultiJoin multiJoin, BitSet leftInputs, int rightJoinIdx)
{
    for (int leftJoinIdx = leftInputs.nextSetBit(0); leftJoinIdx >= 0; leftJoinIdx = leftInputs.nextSetBit(leftJoinIdx+1))
    {
        if (multiJoin.getMultiJoinRel().getJoinTypes().get(leftJoinIdx) == JoinRelType.RIGHT)
        {
            final RexNode outerJoinCond = multiJoin.getOuterJoinCond(leftJoinIdx);
            if (outerJoinCond == null)
            {
                continue;
            }

            RexNode joinFilter = multiJoin.getOuterJoinCond(leftJoinIdx);
            List<RexNode> joinFilterExprs = RelOptUtil.conjunctions(joinFilter);
            for (RexNode joinPredicate: joinFilterExprs)
            {
                if (multiJoin.getFactorsRefByJoinFilter(joinPredicate).get(rightJoinIdx))
                {
                    return leftJoinIdx;
                }
            }
        }
    }
    return -1;
}


/**
 * Computes a cost for a join tree based on the row widths of the inputs
 * into the join. Joins where the inputs have the fewest number of columns
 * lower in the tree are better than equivalent joins where the inputs with
 * the larger number of columns are lower in the tree.
 *
 * @param tree a tree of RelNodes
 *
 * @return the cost associated with the width of the tree
 */
private static int rowWidthCost(RelNode tree)
{
    // The width cost is the width of the tree itself plus the widths
    // of its children.  Hence, skinnier rows are better when they're
    // lower in the tree since the width of a RelNode contributes to
    // the cost of each LogicalJoin that appears above that RelNode.
    int width = tree.getRowType().getFieldCount();
    if (isJoinTree(tree))
    {
        Join joinRel = (Join) tree;
        width += rowWidthCost(joinRel.getLeft()) + rowWidthCost(joinRel.getRight());
    }
    return width;
}


/**
 * Creates a join tree with the new factor added to the top of the tree.
 *
 * @param multiJoin join factors being optimized
 * @param joinTree current join tree
 * @param factorToAdd new factor to be added
 * @param filtersToAdd filters remaining to be added; modifies the list to
 * remove filters that can be added to the join tree
 * @param selfJoin true if the join being created is a self-join that's
 * removable
 *
 * @return new join tree
 */
private static @Nullable LoptJoinTree addToTop(
    RelMetadataQuery mq,
    RelBuilder relBuilder,
    LoptMultiJoin multiJoin,
    LoptJoinTree joinTree,
    int factorToAdd,
    List<RexNode> filtersToAdd,
    boolean selfJoin,
    BitSet rightPendingJoins)
{
    // if the factor being added is null-generating, create the join
    // as a left outer join since it's being added to the RHS side of
    // the join; createJoinSubTree may swap the inputs and therefore
    // convert the left outer join to a right outer join; if the original
    // MultiJoin was a full outer join, these should be the only
    // factors in the join, so create the join as a full outer join
    final BitSet leftInputs = new BitSet(multiJoin.getNumJoinFactors());
    for (int f : joinTree.getTreeOrder())
    {
        leftInputs.set(f);
    }

    // final JoinRelType joinType = getJoinType(multiJoin, leftInputs, factorToAdd, rightPendingJoins);
    final JoinRelType joinType = getJoinTypeInStep(multiJoin, leftInputs, factorToAdd);

    LoptJoinTree rightTree =
        new LoptJoinTree(
            multiJoin.getJoinFactor(factorToAdd),
            factorToAdd);

    // in the case of a left or right outer join, use the specific
    // outer join condition
    RexNode condition;
    if (joinType == JoinRelType.LEFT)
    {
        condition =
            requireNonNull(multiJoin.getOuterJoinCond(factorToAdd),
                "multiJoin.getOuterJoinCond(factorToAdd)");
    }
    else if (joinType == JoinRelType.RIGHT)
    {
        int rightJoinFactor = rightOwner(multiJoin, leftInputs, factorToAdd);
        condition =
            requireNonNull(multiJoin.getOuterJoinCond(rightJoinFactor),
                "multiJoin.getOuterJoinCond(factorToAdd)");
    }
    else
    {
        condition =
            addFilters(
                multiJoin,
                joinTree,
                -1,
                rightTree,
                filtersToAdd,
                false);
    }

    return createJoinSubtree(
        mq,
        relBuilder,
        multiJoin,
        joinTree,
        rightTree,
        condition,
        joinType,
        filtersToAdd,
        true,
        selfJoin);
}

/**
 * Determines which join filters can be added to the current join tree. Note
 * that the join filter still reflects the original join ordering. It will
 * only be adjusted to reflect the new join ordering if the "adjust"
 * parameter is set to true.
 *
 * @param multiJoin join factors being optimized
 * @param leftTree left subtree of the join tree
 * @param leftIdx if &ge; 0, only consider filters that reference leftIdx in
 * leftTree; otherwise, consider all filters that reference any factor in
 * leftTree
 * @param rightTree right subtree of the join tree
 * @param filtersToAdd remaining join filters that need to be added; those
 * that are added are removed from the list
 * @param adjust if true, adjust filter to reflect new join ordering
 *
 * @return AND'd expression of the join filters that can be added to the
 * current join tree
 */
private static RexNode addFilters(
    LoptMultiJoin multiJoin,
    LoptJoinTree leftTree,
    int leftIdx,
    LoptJoinTree rightTree,
    List<RexNode> filtersToAdd,
    boolean adjust)
{
    // loop through the remaining filters to be added and pick out the
    // ones that reference only the factors in the new join tree
    final RexBuilder rexBuilder = multiJoin.getMultiJoinRel().getCluster().getRexBuilder();
    final ImmutableBitSet.Builder childFactorBuilder = ImmutableBitSet.builder();
    childFactorBuilder.addAll(rightTree.getTreeOrder());
    if (leftIdx >= 0)
    {
        childFactorBuilder.set(leftIdx);
    }
    else
    {
        childFactorBuilder.addAll(leftTree.getTreeOrder());
    }
    for (int child : rightTree.getTreeOrder())
    {
        childFactorBuilder.set(child);
    }

    final ImmutableBitSet childFactor = childFactorBuilder.build();
    RexNode condition = null;
    final ListIterator<RexNode> filterIter = filtersToAdd.listIterator();
    while (filterIter.hasNext())
    {
        RexNode joinFilter = filterIter.next();
        ImmutableBitSet filterBitmap =
            multiJoin.getFactorsRefByJoinFilter(joinFilter);

        // if all factors in the join filter are in the join tree,
        // AND the filter to the current join condition
        if (childFactor.contains(filterBitmap))
        {
            if (condition == null)
            {
                condition = joinFilter;
            }
            else
            {
                condition =
                    rexBuilder.makeCall(
                        SqlStdOperatorTable.AND,
                        condition,
                        joinFilter);
            }
            filterIter.remove();
        }
    }

    if (adjust && (condition != null))
    {
        int [] adjustments = new int[multiJoin.getNumTotalFields()];
        if (needsAdjustment(
            multiJoin,
            adjustments,
            leftTree,
            rightTree,
            false))
        {
            condition =
                condition.accept(
                    new RelOptUtil.RexInputConverter(
                        rexBuilder,
                        multiJoin.getMultiJoinFields(),
                        leftTree.getJoinTree().getRowType().getFieldList(),
                        rightTree.getJoinTree().getRowType().getFieldList(),
                        adjustments));
        }
    }

    if (condition == null)
    {
        condition = rexBuilder.makeLiteral(true);
    }

    return condition;
}

/**
 * Adjusts a filter to reflect a newly added factor in the middle of an
 * existing join tree.
 *
 * @param multiJoin join factors being optimized
 * @param left left subtree of the join
 * @param right right subtree of the join
 * @param condition current join condition
 * @param factorAdded index corresponding to the newly added factor
 * @param origJoinOrder original join order, before factor was pushed into
 * the tree
 * @param origFields fields from the original join before the factor was
 * added
 *
 * @return modified join condition reflecting addition of the new factor
 */
private static RexNode adjustFilter(
    LoptMultiJoin multiJoin,
    LoptJoinTree left,
    LoptJoinTree right,
    RexNode condition,
    int factorAdded,
    List<Integer> origJoinOrder,
    List<RelDataTypeField> origFields)
{
    final List<Integer> newJoinOrder = new ArrayList<>();
    left.getTreeOrder(newJoinOrder);
    right.getTreeOrder(newJoinOrder);

    int totalFields =
        left.getJoinTree().getRowType().getFieldCount()
            + right.getJoinTree().getRowType().getFieldCount()
            - multiJoin.getNumFieldsInJoinFactor(factorAdded);
    int [] adjustments = new int[totalFields];

    // go through each factor and adjust relative to the original
    // join order
    boolean needAdjust = false;
    int nFieldsNew = 0;
    for (int newPos = 0; newPos < newJoinOrder.size(); newPos++)
    {
        int nFieldsOld = 0;

        // no need to make any adjustments on the newly added factor
        int factor = newJoinOrder.get(newPos);
        if (factor != factorAdded)
        {
            // locate the position of the factor in the original join
            // ordering
            for (int pos : origJoinOrder)
            {
                if (factor == pos)
                {
                    break;
                }
                nFieldsOld += multiJoin.getNumFieldsInJoinFactor(pos);
            }

            // fill in the adjustment array for this factor
            if (remapJoinReferences(
                multiJoin,
                factor,
                newJoinOrder,
                newPos,
                adjustments,
                nFieldsOld,
                nFieldsNew,
                false)) {
                needAdjust = true;
            }
        }
        nFieldsNew += multiJoin.getNumFieldsInJoinFactor(factor);
    }

    if (needAdjust)
    {
        RexBuilder rexBuilder =
            multiJoin.getMultiJoinRel().getCluster().getRexBuilder();
        condition =
            condition.accept(
                new RelOptUtil.RexInputConverter(
                    rexBuilder,
                    origFields,
                    left.getJoinTree().getRowType().getFieldList(),
                    right.getJoinTree().getRowType().getFieldList(),
                    adjustments));
    }

    return condition;
}

/**
 * Sets an adjustment array based on where column references for a
 * particular factor end up as a result of a new join ordering.
 *
 * <p>If the factor is not the right factor in a removable self-join, then
 * it needs to be adjusted as follows:
 *
 * <ul>
 * <li>First subtract, based on where the factor was in the original join
 * ordering.
 * <li>Then add on the number of fields in the factors that now precede this
 * factor in the new join ordering.
 * </ul>
 *
 * <p>If the factor is the right factor in a removable self-join and its
 * column reference can be mapped to the left factor in the self-join, then:
 *
 * <ul>
 * <li>First subtract, based on where the column reference is in the new
 * join ordering.
 * <li>Then, add on the number of fields up to the start of the left factor
 * in the self-join in the new join ordering.
 * <li>Then, finally add on the offset of the corresponding column from the
 * left factor.
 * </ul>
 *
 * <p>Note that this only applies if both factors in the self-join are in the
 * join ordering. If they are, then the left factor always precedes the
 * right factor in the join ordering.
 *
 * @param multiJoin join factors being optimized
 * @param factor the factor whose references are being adjusted
 * @param newJoinOrder the new join ordering containing the factor
 * @param newPos the position of the factor in the new join ordering
 * @param adjustments the adjustments array that will be set
 * @param offset the starting offset within the original join ordering for
 * the columns of the factor being adjusted
 * @param newOffset the new starting offset in the new join ordering for the
 * columns of the factor being adjusted
 * @param alwaysUseDefault always use the default adjustment value
 * regardless of whether the factor is the right factor in a removable
 * self-join
 *
 * @return true if at least one column from the factor requires adjustment
 */
private static boolean remapJoinReferences(
    LoptMultiJoin multiJoin,
    int factor,
    List<Integer> newJoinOrder,
    int newPos,
    int[] adjustments,
    int offset,
    int newOffset,
    boolean alwaysUseDefault)
{
    boolean needAdjust = false;
    int defaultAdjustment = -offset + newOffset;
    if (!alwaysUseDefault
        && multiJoin.isRightFactorInRemovableSelfJoin(factor)
        && (newPos != 0)
        && newJoinOrder.get(newPos - 1).equals(
        multiJoin.getOtherSelfJoinFactor(factor)))
    {
        int nLeftFields =
            multiJoin.getNumFieldsInJoinFactor(
                newJoinOrder.get(
                    newPos - 1));
        for (int i = 0; i < multiJoin.getNumFieldsInJoinFactor(factor); i++)
        {
            Integer leftOffset = multiJoin.getRightColumnMapping(factor, i);

            // if the left factor doesn't reference the column, then
            // use the default adjustment value
            if (leftOffset == null)
            {
                adjustments[i + offset] = defaultAdjustment;
            }
            else
            {
                adjustments[i + offset] =
                    -(offset + i) + (newOffset - nLeftFields)
                        + leftOffset;
            }
            if (adjustments[i + offset] != 0)
            {
                needAdjust = true;
            }
        }
    }
    else
    {
        if (defaultAdjustment != 0)
        {
            needAdjust = true;
            for (int i = 0; i < multiJoin.getNumFieldsInJoinFactor(newJoinOrder.get(newPos)); i++)
            {
                adjustments[i + offset] = defaultAdjustment;
            }
        }
    }

    return needAdjust;
}



/**
 * Creates a LogicalJoin given left and right operands and a join condition.
 * Swaps the operands if beneficial.
 *
 * @param multiJoin join factors being optimized
 * @param left left operand
 * @param right right operand
 * @param condition join condition
 * @param joinType the join type
 * @param fullAdjust true if the join condition reflects the original join
 * ordering and therefore has not gone through any type of adjustment yet;
 * otherwise, the condition has already been partially adjusted and only
 * needs to be further adjusted if swapping is done
 * @param filtersToAdd additional filters that may be added on top of the
 * resulting LogicalJoin, if the join is a left or right outer join
 * @param selfJoin true if the join being created is a self-join that's
 * removable
 *
 * @return created LogicalJoin
 */
private static LoptJoinTree createJoinSubtree(
    RelMetadataQuery mq,
    RelBuilder relBuilder,
    LoptMultiJoin multiJoin,
    LoptJoinTree left,
    LoptJoinTree right,
    RexNode condition,
    JoinRelType joinType,
    List<RexNode> filtersToAdd,
    boolean fullAdjust,
    boolean selfJoin)
{
    RexBuilder rexBuilder =
        multiJoin.getMultiJoinRel().getCluster().getRexBuilder();

    if (fullAdjust)
    {
        int [] adjustments = new int[multiJoin.getNumTotalFields()];
        if (needsAdjustment(
            multiJoin,
            adjustments,
            left,
            right,
            selfJoin))
        {
            condition =
                condition.accept(
                    new RelOptUtil.RexInputConverter(
                        rexBuilder,
                        multiJoin.getMultiJoinFields(),
                        left.getJoinTree().getRowType().getFieldList(),
                        right.getJoinTree().getRowType().getFieldList(),
                        adjustments));
        }
    }

    relBuilder.push(left.getJoinTree())
        .push(right.getJoinTree())
        .join(joinType, condition);

    // if this is a left or right outer join, and additional filters can
    // be applied to the resulting join, then they need to be applied
    // as a filter on top of the outer join result
    if ((joinType == JoinRelType.LEFT) || (joinType == JoinRelType.RIGHT))
    {
        assert !selfJoin;
        addAdditionalFilters(
            relBuilder,
            multiJoin,
            left,
            right,
            filtersToAdd);
    }

    return new LoptJoinTree(
        relBuilder.build(),
        left.getFactorTree(),
        right.getFactorTree(),
        selfJoin);
}

/**
 * Determines whether any additional filters are applicable to a join tree.
 * If there are any, creates a filter node on top of the join tree with the
 * additional filters.
 *
 * @param relBuilder Builder holding current join tree
 * @param multiJoin join factors being optimized
 * @param left left side of join tree
 * @param right right side of join tree
 * @param filtersToAdd remaining filters
 */
private static void addAdditionalFilters(
    RelBuilder relBuilder,
    LoptMultiJoin multiJoin,
    LoptJoinTree left,
    LoptJoinTree right,
    List<RexNode> filtersToAdd)
{
    RexNode filterCond =
        addFilters(multiJoin, left, -1, right, filtersToAdd, false);
    if (!filterCond.isAlwaysTrue())
    {
        // adjust the filter to reflect the outer join output
        int [] adjustments = new int[multiJoin.getNumTotalFields()];
        if (needsAdjustment(multiJoin, adjustments, left, right, false))
        {
            RexBuilder rexBuilder =
                multiJoin.getMultiJoinRel().getCluster().getRexBuilder();
            filterCond =
                filterCond.accept(
                    new RelOptUtil.RexInputConverter(
                        rexBuilder,
                        multiJoin.getMultiJoinFields(),
                        relBuilder.peek().getRowType().getFieldList(),
                        adjustments));
            relBuilder.filter(filterCond);
        }
    }
}


/**
 * Sets an array indicating how much each factor in a join tree needs to be
 * adjusted to reflect the tree's join ordering.
 *
 * @param multiJoin join factors being optimized
 * @param adjustments array to be filled out
 * @param joinTree join tree
 * @param otherTree null unless joinTree only represents the left side of
 * the join tree
 * @param selfJoin true if no adjustments need to be made for self-joins
 *
 * @return true if some adjustment is required; false otherwise
 */
private static boolean needsAdjustment(
    LoptMultiJoin multiJoin,
    int[] adjustments,
    LoptJoinTree joinTree,
    LoptJoinTree otherTree,
    boolean selfJoin) {
    boolean needAdjustment = false;

    final List<Integer> joinOrder = new ArrayList<>();
    joinTree.getTreeOrder(joinOrder);
    if (otherTree != null) {
        otherTree.getTreeOrder(joinOrder);
    }

    int nFields = 0;
    for (int newPos = 0; newPos < joinOrder.size(); newPos++) {
        int origPos = joinOrder.get(newPos);
        int joinStart = multiJoin.getJoinStart(origPos);

        // Determine the adjustments needed for join references.  Note
        // that if the adjustment is being done for a self-join filter,
        // we always use the default adjustment value rather than
        // remapping the right factor to reference the left factor.
        // Otherwise, we have no way of later identifying that the join is
        // self-join.
        if (remapJoinReferences(
            multiJoin,
            origPos,
            joinOrder,
            newPos,
            adjustments,
            joinStart,
            nFields,
            selfJoin)) {
            needAdjustment = true;
        }
        nFields += multiJoin.getNumFieldsInJoinFactor(origPos);
    }

    return needAdjustment;
}


/** Rule configuration. */
@Value.Immutable
public interface Config extends RelRule.Config
{
    Config DEFAULT = ImmutableE6EnforceJoinOrderLoptRule.Config.of()
        .withOperandSupplier(b -> b.operand(MultiJoin.class).anyInputs());


    @Override default E6EnforceJoinOrderLoptRule toRule()
    {
        return new E6EnforceJoinOrderLoptRule(this);
    }
}
} /// ////// End of class
