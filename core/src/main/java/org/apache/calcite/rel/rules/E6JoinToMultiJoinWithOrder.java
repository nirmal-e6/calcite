/*
 * Copyright (c) 2025 Uniphi Inc
 * All rights reserved.
 *
 * File Name: E6JoinToMultiJoinWithOrder.java
 *
 * Created On: 2025-09-29
 */

package org.apache.calcite.rel.rules;

import com.google.common.collect.ImmutableMap;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.SingleRel;
import org.apache.calcite.rel.core.Filter;
import org.apache.calcite.rel.core.Join;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.core.Project;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.*;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.calcite.util.ImmutableIntList;
import org.immutables.value.Value;

import java.util.*;

import static java.util.Objects.requireNonNull;

@Value.Enclosing
public class E6JoinToMultiJoinWithOrder extends RelRule<E6JoinToMultiJoinWithOrder.Config> implements TransformationRule
{

protected E6JoinToMultiJoinWithOrder(Config config)
{
    super(config);
}

public void onMatch(RelOptRuleCall call)
{
    final Join binaryJoin = call.rel(0);

    // Extract winner leaf order
    List<RelNode> leaves = leafOrder(binaryJoin);

    // Construct the (ordered) factor list to build the MultiJoin
    // Also construct newIndex map: factorRel -> newSlot
    Map<RelNode,Integer> newIndex = new HashMap<>();
    List<RelNode> orderedInputs = new ArrayList<>(leaves.size());

    for (int order = 0; order < leaves.size(); order++)
    {
        RelNode nextLeaf = leaves.get(order);
        orderedInputs.add(nextLeaf);
        newIndex.put(nextLeaf, order);
    }

    call.transformTo(buildMultiJoinInOrder(binaryJoin.getCluster(), orderedInputs, binaryJoin));
}

// Collect the binary-winner’s leaf list in left-to-right order
static List<RelNode> leafOrder(RelNode joinRoot)
{
    List<RelNode> out = new ArrayList<>();
    collectLeaves(unwrapRelNode(joinRoot), out);
    return out;
}

static void collectLeaves(RelNode n, List<RelNode> out)
{
    n = unwrapRelNode(n);
    if (n instanceof Join)
    {
        Join join = (Join) n;
        collectLeaves(join.getLeft(),  out);
        collectLeaves(join.getRight(), out);
        return;
    }
    out.add(n);
}

static RelNode unwrapRelNode(RelNode n)
{
    RelNode cur = n;
    while (cur instanceof Project || cur instanceof Filter)
    {
        cur = cur.getInput(0);
    }
    return cur;
}


// build a MultiJoin whose inputs are exactly 'orderedInputs', aligned with the winner’s leaf order
static MultiJoin buildMultiJoinInOrder(RelOptCluster cluster, List<RelNode> orderedInputs, RelNode rootOfBinaryTree)
{
    final RexBuilder rb = cluster.getRexBuilder();
    final int n = orderedInputs.size();
    final int[] startOfFactor = new int[n];
    final int[] fieldCountOfFactor = new int[n];

    int totalFields = 0;

    for (int i = 0; i < n; i++)
    {
        int fieldCount = orderedInputs.get(i).getRowType().getFieldCount();
        fieldCountOfFactor[i] = fieldCount;
        startOfFactor[i] = totalFields;
        totalFields += fieldCount;
    }

    // Map each leaf RelNode identity to its factor index (new coordinate system)
    final HashMap<RelNode, Integer> leafToFactor = new HashMap<>();
    for (int i = 0; i < n; i++)
    {
        leafToFactor.put(orderedInputs.get(i), i);
    }

    // For every binary node, build a local->(factor,localIdx) map
    HashMap<RelNode, LocalLayout> localLayouts = new HashMap<>();
    buildLocalLayouts(rootOfBinaryTree, leafToFactor, localLayouts);

    List<RexNode> innerConjuncts = new ArrayList<>();
    List<RexNode> singleSideConjuncts = new ArrayList<>();
    List<JoinRelType> joinTypes = new ArrayList<>(Collections.nCopies(n, JoinRelType.INNER));
    List<RexNode> outerJoinConds = new ArrayList<>(Collections.nCopies(n, (RexNode) null));
    boolean isFull = false;

    boolean topIsOuter = (rootOfBinaryTree instanceof Join) && ((Join) rootOfBinaryTree).getJoinType() != JoinRelType.INNER;

    if (topIsOuter)
    {
        final Join top = (Join) rootOfBinaryTree;
        final JoinRelType joinType = top.getJoinType();
        if (n != 2)
        {
            throw new IllegalStateException("Top outer-join island must map to exactly 2 inputs; got " + n);
        }

        final RexNode remapped = top.getCondition().accept(createRemapperFor(top, localLayouts, startOfFactor));
        switch (joinType)
        {
            case LEFT:
                joinTypes.set(1, JoinRelType.LEFT);
                outerJoinConds.set(1, remapped);
                break;
            case RIGHT:
                joinTypes.set(0, JoinRelType.RIGHT);
                outerJoinConds.set(0, remapped);
                break;
            case FULL:
                isFull = true;
                break;
            case INNER:
                break;
        }
    }
    else
    {
        collectInnerConjuncts(rootOfBinaryTree, innerConjuncts, singleSideConjuncts, localLayouts, startOfFactor);
    }


    final RexNode joinFilter = innerConjuncts.isEmpty() ? rb.makeLiteral(true)
                                                        : RexUtil.composeConjunction(rb, innerConjuncts, false);

    final RexNode postJoinFilter = singleSideConjuncts.isEmpty() ? null :
                                   RexUtil.composeConjunction(rb, singleSideConjuncts, false);

    // projFields  & joinFieldRefCountsMap
    final List<ImmutableBitSet> projFields = new ArrayList<>(n);
    for (int i = 0; i < n; i++)
    {
        projFields.add(ImmutableBitSet.range(fieldCountOfFactor[i]));
    }

    com.google.common.collect.ImmutableMap<Integer, ImmutableIntList> refCounts = computeRefCounts(n, fieldCountOfFactor, startOfFactor, innerConjuncts, postJoinFilter);
    RelDataType rowType = concatRowType(cluster.getTypeFactory(), orderedInputs);

    return new MultiJoin(cluster, orderedInputs, joinFilter, rowType, isFull, outerJoinConds, joinTypes, projFields, refCounts, postJoinFilter);
}

// Creates a RexShuttle that remaps field references from a local join's coordinate system to the global MultiJoin's coordinate system.
private static LocalToGlobalRemapper createRemapperFor(RelNode joinNode, HashMap<RelNode, LocalLayout> localLayouts, int[] startOfFactor)
{
    LocalLayout layout = localLayouts.get(joinNode);
    return new LocalToGlobalRemapper(layout, startOfFactor);
}

// for any node inside the island, map each local field index -> (factorIdx, localIdxWithinFactor)
static void buildLocalLayouts(RelNode node, HashMap<RelNode, Integer> leafToFactor, HashMap<RelNode, LocalLayout> out)
{
    if (node instanceof Project || node instanceof Filter)
    {
        buildLocalLayouts(((SingleRel) node).getInput(), leafToFactor, out);
        out.put(node, out.get(((SingleRel) node).getInput()));
        return;
    }

    if (node instanceof Join)
    {
        Join join = (Join) node;
        buildLocalLayouts(join.getLeft(), leafToFactor, out);
        buildLocalLayouts(join.getRight(), leafToFactor, out);
        LocalLayout leftChildLayout = out.get(join.getLeft());
        LocalLayout rightChildLayout = out.get(join.getRight());
        out.put(join, LocalLayout.concat(leftChildLayout, rightChildLayout));
        return;
    }

    // Leaf (table or barrier)
    Integer factor = leafToFactor.get(node);
    if (factor == null)
    {
        throw new IllegalStateException("Leaf not in orderedInputs: " + node.getRelTypeName());
    }

    final int fieldCount = node.getRowType().getFieldCount();
    final int[] factorIdx = new int[fieldCount];
    final int[] localIdx = new int[fieldCount];

    for (int i = 0; i < fieldCount; i++)
    {
        factorIdx[i] = factor;
        localIdx[i] = i;
    }

    out.put(node, new LocalLayout(factorIdx, localIdx));
}

// Local layout: for a node’s concatenated rowtype, where does each field come from.
static final class LocalLayout implements java.io.Serializable
{
    final int factorIdx[]; // size of array = no. of fields at this node
    final int localIdx[]; // local index within that factor

    LocalLayout(int[] f, int[] l)
    {
        this.factorIdx = f;
        this.localIdx = l;
    }

    // combine 2 join factors - join their factorIndex and fieldIndex arrays
    static LocalLayout concat(LocalLayout leftLayout, LocalLayout rightLayout)
    {
        int sizeOfLeftLayout = leftLayout.factorIdx.length;
        int sizeOfRightLayout = rightLayout.factorIdx.length;

        int[] f = Arrays.copyOf(leftLayout.factorIdx, sizeOfLeftLayout + sizeOfRightLayout);
        int[] l = Arrays.copyOf(leftLayout.localIdx, sizeOfLeftLayout + sizeOfRightLayout);
        System.arraycopy(rightLayout.factorIdx, 0, f,sizeOfLeftLayout, sizeOfRightLayout);
        System.arraycopy(rightLayout.localIdx, 0, l, sizeOfLeftLayout, sizeOfRightLayout);
        return new LocalLayout(f, l);
    }
}

// Remap join-local RexInputRef(i) -> RexInputRef(startOfFactor[factor[i]] + localIdx[i])
static final class LocalToGlobalRemapper extends RexShuttle
{
    final LocalLayout layout;
    final int startOfFactor[];

    LocalToGlobalRemapper(LocalLayout layout, int startOfFactor[])
    {
        this.layout = requireNonNull(layout, "layout");
        this.startOfFactor = startOfFactor;
    }

    @Override public RexNode visitInputRef(RexInputRef ref)
    {
        int i = ref.getIndex();
        int f = layout.factorIdx[i];
        int k = layout.localIdx[i];

        // shift local index by factor start index
        int newIdx = startOfFactor[f] + k;
        return new RexInputRef(newIdx, ref.getType());
    }

}

// Collect inner-join conjuncts; keep single-side conjuncts separately as postFilter
static void collectInnerConjuncts(RelNode node, List<RexNode> innerConjuncts, List<RexNode> singleSideConjuncts,
    HashMap<RelNode, LocalLayout> localLayouts, int[] startOfFactor)
{
    if (node instanceof Project || node instanceof Filter)
    {
        // Recurse with the same dependencies
        collectInnerConjuncts(
            ((SingleRel) node).getInput(),
            innerConjuncts,
            singleSideConjuncts,
            localLayouts,
            startOfFactor);
        return;
    }

    if (node instanceof Join)
    {
        Join j = (Join) node;
        if (j.getJoinType() == JoinRelType.INNER)
        {
            RexShuttle remapper = new LocalToGlobalRemapper(localLayouts.get(j), startOfFactor);
            RexNode cond = j.getCondition().accept(remapper);

            for (RexNode pred : RelOptUtil.conjunctions(cond))
            {
                final ImmutableBitSet used = RelOptUtil.InputFinder.bits(pred);
                int distinctFactors = countDistinctFactors(used, startOfFactor);
                if (distinctFactors == 1)
                {
                    singleSideConjuncts.add(pred);
                }
                else
                {
                    innerConjuncts.add(pred);
                }
            }

            // Recurse for child nodes
            collectInnerConjuncts(j.getLeft(), innerConjuncts, singleSideConjuncts, localLayouts, startOfFactor);
            collectInnerConjuncts(j.getRight(), innerConjuncts, singleSideConjuncts, localLayouts, startOfFactor);
        }
    }
}

static int countDistinctFactors(ImmutableBitSet usedGlobals, int[] startOfFactor)
{
    if (usedGlobals.isEmpty())
    {
        return 0; // constant
    }
    final BitSet factors = new BitSet(startOfFactor.length);
    for (int g = usedGlobals.nextSetBit(0); g >= 0; g = usedGlobals.nextSetBit(g + 1))
    {
        factors.set(factorOfGlobalIndex(g, startOfFactor));
    }
    return factors.cardinality();
}


// Compute reference counts per factor used by joinFilter/postFilter
static com.google.common.collect.ImmutableMap<Integer, ImmutableIntList> computeRefCounts(int n, int fieldCountOfFactor[],
    int[] startOfFactor, List<RexNode> innerConjuncts, RexNode postJoinFilter)
{
    // outer dim is factor, inner dim is count of field usage for that factor
    int counts[][] = new int[n][];
    for (int i = 0; i < n; i++)
    {
        counts[i] = new int[fieldCountOfFactor[i]];
    }

    final List<RexNode> nodes =
        new ArrayList<>(innerConjuncts != null ? innerConjuncts : Collections.<RexNode>emptyList());
    if (postJoinFilter != null)
    {
        nodes.add(postJoinFilter);
    }

    for (RexNode expr : nodes)
    {
        if (expr == null)
        {
            continue;
        }
        ImmutableBitSet used = RelOptUtil.InputFinder.bits(expr);
        for (int g = used.nextSetBit(0); g >= 0; g = used.nextSetBit(g + 1))
        {
            int f = factorOfGlobalIndex(g, startOfFactor);
            int local = g - startOfFactor[f];
            if (local >= 0 && local < counts[f].length)
            {
                counts[f][local]++;
            }
        }
    }

    ImmutableMap.Builder<Integer, ImmutableIntList> builder = ImmutableMap.builder();
    for (int f = 0; f < n; f++)
    {
        builder.put(f, ImmutableIntList.of(counts[f]));
    }
    return builder.build();

}


static int factorOfGlobalIndex(int g, int[] startOfFactor)
{
    // ranges are [start[f], start[f+1]) for all but last
    for (int factor = 0; factor < startOfFactor.length; factor++)
    {
        int start = startOfFactor[factor];
        int end   = (factor + 1 < startOfFactor.length) ? startOfFactor[factor + 1] : Integer.MAX_VALUE;
        if (g >= start && g < end)
        {
            return factor;
        }
    }
    throw new IndexOutOfBoundsException("Global field index " + g + " not in any factor range");
}



// Concatenate input row types
static RelDataType concatRowType(RelDataTypeFactory tf, List<RelNode> inputs)
{
    final RelDataTypeFactory.Builder builder = tf.builder();
    final Set<String> seen = new HashSet<>();
    for (RelNode in : inputs)
    {
        for (RelDataTypeField f : in.getRowType().getFieldList())
        {
            String name = f.getName();
            if (!seen.add(name))
            {
                int k = 2;
                while (!seen.add(name + "_" + k))
                {
                    k++;
                }
                name = name + "_" + k;
            }

            builder.add(name, f.getType());
        }
    }

    return builder.build();
}

@Value.Immutable
public interface Config extends RelRule.Config
{
    E6JoinToMultiJoinWithOrder.Config DEFAULT = ImmutableE6JoinToMultiJoinWithOrder.Config.of()
        .withOperandFor(LogicalJoin.class);

    @Override default E6JoinToMultiJoinWithOrder toRule() {
        return new E6JoinToMultiJoinWithOrder(this);
    }

    /** Defines an operand tree for the given classes. */
    default E6JoinToMultiJoinWithOrder.Config withOperandFor(Class<? extends Join> joinClass) {
        return withOperandSupplier(b0 ->
            b0.operand(joinClass).inputs(
                b1 -> b1.operand(RelNode.class).anyInputs(),
                b2 -> b2.operand(RelNode.class).anyInputs()))
            .as(E6JoinToMultiJoinWithOrder.Config.class);
    }
}
} /// ////// End of class
