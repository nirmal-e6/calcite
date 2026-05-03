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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Calc;
import org.apache.calcite.rel.core.Project;
import org.apache.calcite.rel.logical.LogicalCalc;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rel.logical.LogicalWindow;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.*;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.tools.RelBuilderFactory;
import org.apache.calcite.util.ImmutableIntList;
import org.apache.calcite.util.Pair;
import org.apache.calcite.util.Util;
import org.apache.calcite.util.graph.DefaultDirectedGraph;
import org.apache.calcite.util.graph.DefaultEdge;
import org.apache.calcite.util.graph.DirectedGraph;
import org.apache.calcite.util.graph.TopologicalOrderIterator;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

// shaded to add logs
// getting flaky errors in regression
// java.lang.UnsupportedOperationException: unoptimised plan window ended up in expressions
// new change (20-06-2025)
// using E6CalcRelSplitter instead of CalcRelSplitter for alias bug fix

/**
 * Planner rule that slices a
 * {@link Project}
 * into sections which contain windowed
 * aggregate functions and sections which do not.
 *
 * <p>The sections which contain windowed agg functions become instances of
 * {@link LogicalWindow}.
 * If the {@link LogicalCalc} does not contain
 * any windowed agg functions, does nothing.
 *
 * <p>There is also a variant that matches
 * {@link Calc} rather than {@code Project}.
 */
public abstract class E6ProjectToWindowRule
    extends RelRule<E6ProjectToWindowRule.Config>
    implements TransformationRule {

/** Creates a ProjectToWindowRule. */
protected E6ProjectToWindowRule(Config config) {
    super(config);
}

/**
 * Instance of the rule that applies to a
 * {@link Calc} that contains
 * windowed aggregates and converts it into a mixture of
 * {@link LogicalWindow} and {@code Calc}.
 *
 * @see CoreRules#CALC_TO_WINDOW
 */
public static class E6CalcToWindowRule extends E6ProjectToWindowRule {
    /** Creates a CalcToWindowRule. */
    protected E6CalcToWindowRule(E6CalcToWindowRuleConfig config) {
        super(config);
    }

    @Override public void onMatch(RelOptRuleCall call) {
        final Calc calc = call.rel(0);
        assert calc.containsOver();
        final E6CalcRelSplitter transform =
            new WindowedAggRelSplitter(calc, call.builder(), calc.getRowType().getFieldNames());
        RelNode newRel = transform.execute();
        call.transformTo(newRel);
    }

    /** Rule configuration. */
    @Value.Immutable
    public interface E6CalcToWindowRuleConfig extends E6ProjectToWindowRule.Config {
        E6CalcToWindowRuleConfig DEFAULT = ImmutableE6CalcToWindowRuleConfig.of()
            .withOperandSupplier(b ->
                b.operand(Calc.class)
                    .predicate(Calc::containsOver)
                    .anyInputs())
            .withDescription("E6ProjectToWindowRule");

        @Override default E6CalcToWindowRule toRule() {
            return new E6CalcToWindowRule(this);
        }
    }
}

/**
 * Instance of the rule that can be applied to a
 * {@link Project} and that produces, in turn,
 * a mixture of {@code LogicalProject}
 * and {@link LogicalWindow}.
 *
 * @see CoreRules#PROJECT_TO_LOGICAL_PROJECT_AND_WINDOW
 */
public static class E6ProjectToLogicalProjectAndWindowRule
    extends E6ProjectToWindowRule {

    private final Logger LOG = LoggerFactory.getLogger(E6ProjectToLogicalProjectAndWindowRule.class);
    /** Creates a ProjectToLogicalProjectAndWindowRule. */
    protected E6ProjectToLogicalProjectAndWindowRule(
        E6ProjectToLogicalProjectAndWindowRuleConfig config) {
        super(config);
    }

    @Deprecated // to be removed before 2.0
    public E6ProjectToLogicalProjectAndWindowRule(
        RelBuilderFactory relBuilderFactory) {
        this(E6ProjectToLogicalProjectAndWindowRuleConfig.DEFAULT
            .withRelBuilderFactory(relBuilderFactory)
            .as(E6ProjectToLogicalProjectAndWindowRuleConfig.class));
    }

    @Override public void onMatch(RelOptRuleCall call) {
        Project project = call.rel(0);
        LOG.trace("onMatch called in Project to window Rule with Node : {}", project.explain());
        assert project.containsOver();
        final RelNode input = project.getInput();
        final RexProgram program =
            RexProgram.create(
                input.getRowType(),
                project.getProjects(),
                null,
                project.getRowType(),
                project.getCluster().getRexBuilder());
        // temporary LogicalCalc, never registered
        final LogicalCalc calc = LogicalCalc.create(input, program);
        final E6CalcRelSplitter transform = new WindowedAggRelSplitter(calc,
            call.builder(), project.getRowType().getFieldNames()) {
            @Override protected RelNode handle(RelNode rel) {
                if (!(rel instanceof LogicalCalc)) {
                    return rel;
                }
                final LogicalCalc calc = (LogicalCalc) rel;
                final RexProgram program = calc.getProgram();
                relBuilder.push(calc.getInput());
                if (program.getCondition() != null) {
                    relBuilder.filter(
                        program.expandLocalRef(program.getCondition()));
                }
                if (!program.projectsOnlyIdentity()) {
                    relBuilder.project(
                        Util.transform(program.getProjectList(), program::expandLocalRef),
                        calc.getRowType().getFieldNames());
                }
                return relBuilder.build();
            }
        };
        RelNode newRelNode = transform.execute();
        LOG.trace("onMatch call completed with node : {}", newRelNode.explain());
        call.transformTo(newRelNode);
    }

    /** Rule configuration. */
    @Value.Immutable
    public interface E6ProjectToLogicalProjectAndWindowRuleConfig extends E6ProjectToWindowRule.Config {
        E6ProjectToLogicalProjectAndWindowRuleConfig DEFAULT =
            ImmutableE6ProjectToLogicalProjectAndWindowRuleConfig.of()
                .withOperandSupplier(b ->
                    b.operand(Project.class)
                        .predicate(Project::containsOver)
                        .anyInputs())
                .withDescription("ProjectToWindowRule:project");

        @Override default E6ProjectToLogicalProjectAndWindowRule toRule() {
            return new E6ProjectToLogicalProjectAndWindowRule(this);
        }
    }
}

/**
 * Splitter that distinguishes between windowed aggregation expressions
 * (calls to {@link RexOver}) and ordinary expressions.
 */
static class WindowedAggRelSplitter extends E6CalcRelSplitter {
    private static final RelType[] REL_TYPES = {
        new RelType("CalcRelType") {
            @Override protected boolean canImplement(RexFieldAccess field) {
                return true;
            }

            @Override protected boolean canImplement(RexDynamicParam param) {
                return true;
            }

            @Override protected boolean canImplement(RexLiteral literal) {
                return true;
            }

            @Override protected boolean canImplement(RexCall call) {
                return !(call instanceof RexOver);
            }

            @Override protected RelNode makeRel(RelOptCluster cluster,
                RelTraitSet traitSet, RelBuilder relBuilder, RelNode input,
                RexProgram program) {
                assert !program.containsAggs();
                program = program.normalize(cluster.getRexBuilder(), null);
                return super.makeRel(cluster, traitSet, relBuilder, input,
                    program);
            }
        },
        new RelType("WinAggRelType") {
            @Override protected boolean canImplement(RexFieldAccess field) {
                return false;
            }

            @Override protected boolean canImplement(RexDynamicParam param) {
                return false;
            }

            @Override protected boolean canImplement(RexLiteral literal) {
                return false;
            }

            @Override protected boolean canImplement(RexCall call) {
                return call instanceof RexOver;
            }

            @Override protected boolean supportsCondition() {
                return false;
            }

            @Override protected RelNode makeRel(RelOptCluster cluster, RelTraitSet traitSet,
                RelBuilder relBuilder, RelNode input, RexProgram program) {
                Preconditions.checkArgument(program.getCondition() == null,
                    "WindowedAggregateRel cannot accept a condition");
                return LogicalWindow.create(cluster, traitSet, relBuilder, input,
                    program);
            }
        }
    };

    WindowedAggRelSplitter(Calc calc, RelBuilder relBuilder, List<String> parentFieldNames) {
        super(calc, relBuilder, REL_TYPES, parentFieldNames);
    }

    @Override protected List<Set<Integer>> getCohorts() {
        // Two RexOver will be put in the same cohort
        // if the following conditions are satisfied
        // (1). They have the same RexWindow
        // (2). They are not dependent on each other
        final List<RexNode> exprs = this.program.getExprList();
        final DirectedGraph<Integer, DefaultEdge> graph =
            createGraphFromExpression(exprs);
        final List<Integer> rank = getRank(graph);

        final List<Pair<RexWindow, Set<Integer>>> windowToIndices = new ArrayList<>();
        for (int i = 0; i < exprs.size(); ++i) {
            final RexNode expr = exprs.get(i);
            if (expr instanceof RexOver) {
                final RexOver over = (RexOver) expr;

                // If we can found an existing cohort which satisfies the two conditions,
                // we will add this RexOver into that cohort
                boolean isFound = false;
                for (Pair<RexWindow, Set<Integer>> pair : windowToIndices) {
                    // Check the first condition
                    if (pair.left.equals(over.getWindow())) {
                        // Check the second condition
                        boolean hasDependency = false;
                        for (int ordinal : pair.right) {
                            if (isDependent(graph, rank, ordinal, i)) {
                                hasDependency = true;
                                break;
                            }
                        }

                        if (!hasDependency) {
                            pair.right.add(i);
                            isFound = true;
                            break;
                        }
                    }
                }

                // This RexOver cannot be added into any existing cohort
                if (!isFound) {
                    final Set<Integer> newSet = new HashSet<>(ImmutableList.of(i));
                    windowToIndices.add(Pair.of(over.getWindow(), newSet));
                }
            }
        }

        final List<Set<Integer>> cohorts = new ArrayList<>();
        for (Pair<RexWindow, Set<Integer>> pair : windowToIndices) {
            cohorts.add(pair.right);
        }
        return cohorts;
    }

    private static boolean isDependent(final DirectedGraph<Integer, DefaultEdge> graph,
        final List<Integer> rank,
        final int ordinal1,
        final int ordinal2) {
        if (rank.get(ordinal2) > rank.get(ordinal1)) {
            return isDependent(graph, rank, ordinal2, ordinal1);
        }

        // Check if the expression in ordinal1
        // could depend on expression in ordinal2 by Depth-First-Search
        final Deque<Integer> dfs = new ArrayDeque<>();
        final Set<Integer> visited = new HashSet<>();
        dfs.push(ordinal2);
        while (!dfs.isEmpty()) {
            int source = dfs.pop();
            if (visited.contains(source)) {
                continue;
            }

            if (source == ordinal1) {
                return true;
            }

            visited.add(source);
            for (DefaultEdge e : graph.getOutwardEdges(source)) {
                int target = (int) e.target;
                if (rank.get(target) <= rank.get(ordinal1)) {
                    dfs.push(target);
                }
            }
        }

        return false;
    }

    private static List<Integer> getRank(DirectedGraph<Integer, DefaultEdge> graph) {
        final int[] rankArr = new int[graph.vertexSet().size()];
        int rank = 0;
        for (int i : TopologicalOrderIterator.of(graph)) {
            rankArr[i] = rank++;
        }
        return ImmutableIntList.of(rankArr);
    }

    private static DirectedGraph<Integer, DefaultEdge> createGraphFromExpression(
        final List<RexNode> exprs) {
        final DirectedGraph<Integer, DefaultEdge> graph =
            DefaultDirectedGraph.create();
        for (int i = 0; i < exprs.size(); i++) {
            graph.addVertex(i);
        }

        new RexBiVisitorImpl<Void, Integer>(true) {
            @Override public Void visitLocalRef(RexLocalRef localRef, Integer i) {
                graph.addEdge(localRef.getIndex(), i);
                return null;
            }
        }.visitEachIndexed(exprs);

        assert graph.vertexSet().size() == exprs.size();
        return graph;
    }
}

/** Rule configuration. */
public interface Config extends RelRule.Config {
    @Override E6ProjectToWindowRule toRule();
}
}
