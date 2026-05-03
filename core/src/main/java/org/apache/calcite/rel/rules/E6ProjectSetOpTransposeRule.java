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

// made a copy of Calcite's ProjectSetOpTransposeRule to not push constants below set operator

package org.apache.calcite.rel.rules;

import com.google.common.collect.ImmutableList;
import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Project;
import org.apache.calcite.rel.core.SetOp;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexOver;
import org.apache.calcite.tools.RelBuilderFactory;
import org.apache.calcite.util.Pair;
import org.immutables.value.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * Planner rule that pushes
 * a {@link LogicalProject}
 * past a {@link SetOp}.
 *
 * <p>The children of the {@code SetOp} will project
 * only the {@link RexInputRef}s referenced in the original
 * {@code LogicalProject}.
 *
 * @see CoreRules#PROJECT_SET_OP_TRANSPOSE
 */
@Value.Enclosing
@SuppressWarnings("deprecation")
public class E6ProjectSetOpTransposeRule
    extends RelRule<E6ProjectSetOpTransposeRule.Config>
    implements TransformationRule {

/** Creates a ProjectSetOpTransposeRule. */
protected E6ProjectSetOpTransposeRule(Config config) {
    super(config);
}

@Deprecated // to be removed before 2.0
public E6ProjectSetOpTransposeRule(
    PushProjector.ExprCondition preserveExprCondition,
    RelBuilderFactory relBuilderFactory) {
    this(Config.DEFAULT.withRelBuilderFactory(relBuilderFactory)
        .as(Config.class)
        .withPreserveExprCondition(preserveExprCondition));
}

//~ Methods ----------------------------------------------------------------

@Override
public boolean matches(RelOptRuleCall call)
{
    LogicalProject rel = call.rel(0);
    return shouldPushProjects(rel);
}

private boolean shouldPushProjects(LogicalProject project)
{
    List<RexNode> projects = project.getProjects();

    boolean hasLiteral = false;
    boolean hasColumn = false;
    boolean hasPushableNodes = false;

    for(RexNode node : projects)
    {
        if(isLiteral(node))
        {
            hasLiteral = true;
        }
        else if(isColumn(node))
        {
            hasColumn = true;
        }
        else if(isToPush(node))
        {
            hasPushableNodes = true;
        }
    }

    // if it has only column then push
    if(hasColumn && !hasLiteral && !hasPushableNodes)
    {
        return true;
    }
    // if it has pushable (other than literal) then also push
    else
    {
        return hasPushableNodes;
    }
}

private boolean isToPush(RexNode node)
{
    return !(node instanceof RexLiteral);
}

private boolean isColumn(RexNode node)
{
    return node instanceof RexInputRef;
}

private boolean isLiteral(RexNode node)
{
    return node instanceof RexLiteral;
}

@Override public void onMatch(RelOptRuleCall call) {
    LogicalProject origProject = call.rel(0);
    final SetOp setOp = call.rel(1);

    // cannot push project past a distinct
    if (!setOp.all) {
        return;
    }

    Pair<Pair<List<RexNode>,List<Integer>>, LogicalProject> constantAndProjectPair = getAllConstantsAndUpdatedProjection(
        origProject);
    Pair<List<RexNode>,List<Integer>> allConstantsFromProjections = constantAndProjectPair.left;
    LogicalProject newProject = constantAndProjectPair.right;

    // locate all fields referenced in the projection
    final PushProjector pushProjector =
        new PushProjector(newProject, null, setOp,
            config.preserveExprCondition(), call.builder());
    pushProjector.locateAllRefs();

    final List<RelNode> newSetOpInputs = new ArrayList<>();
    final int[] adjustments = pushProjector.getAdjustments();

    final RelNode node;
    if (newProject.containsOver()) {
        // should not push over past set-op but can push its operand down.
        for (RelNode input : setOp.getInputs()) {
            Project p = pushProjector.createProjectRefsAndExprs(input, true, false);
            // make sure that it is not a trivial project to avoid infinite loop.
            if (p.getRowType().equals(input.getRowType())) {
                return;
            }
            newSetOpInputs.add(p);
        }
        final SetOp newSetOp =
            setOp.copy(setOp.getTraitSet(), newSetOpInputs);
        node = pushProjector.createNewProject(newSetOp, adjustments);
    } else {
        // push some expressions below the set-op; this
        // is different from pushing below a join, where we decompose
        // to try to keep expensive expressions above the join,
        // because UNION ALL does not have any filtering effect,
        // and it is the only operator this rule currently acts on
        setOp.getInputs().forEach(input ->
            newSetOpInputs.add(
                pushProjector.createNewProject(
                    pushProjector.createProjectRefsAndExprs(
                        input, true, false), adjustments)));
        node = setOp.copy(setOp.getTraitSet(), newSetOpInputs);
    }

    RelNode finalNode = node;
    if(!allConstantsFromProjections.left.isEmpty())
    {
        List<RexNode> constantRexNodes = allConstantsFromProjections.left;
        List<Integer> constantNodeIndexes = allConstantsFromProjections.right;

        List<RelDataTypeField> fieldList = origProject.getRowType().getFieldList();
        int fieldListSize = fieldList.size();

        List<RexNode> newRexNodes = new ArrayList<>();
        List<String> newFieldNames = new ArrayList<>();

        int constantCount = 0;
        int rexNodeCount = 0;
        for(int i=0; i<fieldListSize; i++)
        {
            if(constantNodeIndexes.contains(i))
            {
                newRexNodes.add(constantRexNodes.get(constantCount));
                if(fieldList.get(i).getName().contains("$"))
                {
                    newFieldNames.add("$c" + constantCount);
                }
                else
                {
                    newFieldNames.add(fieldList.get(i).getName());
                }
                constantCount++;
            }
            else
            {
                newRexNodes.add(new RexInputRef(rexNodeCount, fieldList.get(i).getType()));
                newFieldNames.add(fieldList.get(i).getName());
                rexNodeCount++;
            }
        }

        finalNode = LogicalProject.create(node, ImmutableList.of(), newRexNodes, newFieldNames);
    }

    call.transformTo(finalNode);
}

private Pair<Pair<List<RexNode>, List<Integer>>, LogicalProject> getAllConstantsAndUpdatedProjection(LogicalProject originalProject)
{
    // to create new logical project
    List<RexNode> newRexNodeList = new ArrayList<>();
    List<String> newFieldNames = new ArrayList<>();

    // constants list
    List<RexNode> listOfConstants = new ArrayList<>();
    List<Integer> indexesOfConstants = new ArrayList<>();

    List<RexNode> projects = originalProject.getProjects();
    List<String> fieldNames = originalProject.getRowType().getFieldNames();
    for(int i=0; i<projects.size(); i++)
    {
        if(projects.get(i) instanceof RexLiteral)
        {
            listOfConstants.add(projects.get(i));
            indexesOfConstants.add(i);
        }
        else
        {
            newRexNodeList.add(projects.get(i));
            newFieldNames.add(fieldNames.get(i));
        }
    }

    if(listOfConstants.isEmpty())
    {
        return new Pair<>(new Pair<>(listOfConstants, indexesOfConstants), originalProject);
    }
    else
    {
        return new Pair<>(new Pair<>(listOfConstants, indexesOfConstants),
            LogicalProject.create(originalProject.getInput(), originalProject.getHints(), newRexNodeList, newFieldNames));
    }
}

private LogicalProject removeConstantsFromLogicalProject(LogicalProject originalProject)
{
    List<RexNode> newRexNodeList = new ArrayList<>();
    List<String> newFieldNames = new ArrayList<>();

    List<RexNode> projects = originalProject.getProjects();
    List<String> fieldNames = originalProject.getRowType().getFieldNames();
    for(int i=0; i<projects.size(); i++)
    {
        if(!(projects.get(i) instanceof RexLiteral))
        {
            newRexNodeList.add(projects.get(i));
            newFieldNames.add(fieldNames.get(i));
        }
    }
    if(newRexNodeList.isEmpty())
    {
        return originalProject;
    }

    return LogicalProject.create(originalProject.getInput(), originalProject.getHints(), newRexNodeList, newFieldNames);
}

/** Rule configuration. */
@Value.Immutable(singleton = false)
public interface Config extends RelRule.Config {
    Config DEFAULT = ImmutableE6ProjectSetOpTransposeRule.Config.builder()
        .withPreserveExprCondition(expr -> !(expr instanceof RexOver))
        .build()
        .withOperandSupplier(b0 ->
            b0.operand(LogicalProject.class).oneInput(b1 ->
                b1.operand(SetOp.class).anyInputs()));

    @Override default E6ProjectSetOpTransposeRule toRule() {
        return new E6ProjectSetOpTransposeRule(this);
    }

    /** Defines when an expression should not be pushed. */
    PushProjector.ExprCondition preserveExprCondition();

    /** Sets {@link #preserveExprCondition()}. */
    Config withPreserveExprCondition(PushProjector.ExprCondition condition);
}
}
