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

import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.core.Filter;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexTableInputRef;
import org.apache.calcite.rex.RexUtil;
import org.apache.calcite.sql.SqlKind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OptimizeFilterWithOR {
  public Filter OptimizeFilterWithOR(Filter filter, RelMetadataQuery mq) {

    Filter filterCopy = filter.copy(filter.getTraitSet(), filter.getInput(), filter.getCondition());
    Set<RexTableInputRef.RelTableRef> tableSet = mq.getTableReferences(filterCopy);
    if (tableSet == null) {
      return null;
    }
    Set<String> tableNames = getTableNames(tableSet);
    List<RexNode> newExpressions = new ArrayList<>();
    if (filter.getCondition().isA(SqlKind.OR)) {
      RexNode orClause = filter.getCondition();
      if (orClause.isA(SqlKind.OR)) {
        for (String tableName : tableNames) {
          if (tableName == null) {
            continue;
          }
          RexNode newExpression = extractOrClause(filter, orClause, tableName, mq);
          if (newExpression != null) {
            newExpressions.add(newExpression);
          }
        }
      }
    } else if (filter.getCondition().isA(SqlKind.AND)) {
      List<RexNode> conjuncts = RelOptUtil.conjunctions(filter.getCondition());
      for (RexNode conjunct : conjuncts) {
        if (conjunct.isA(SqlKind.OR)) {
          for (String tableName : tableNames) {
            if (tableName == null) {
              continue;
            }
            RexNode newExpression = extractOrClause(filter, conjunct, tableName, mq);
            if (newExpression != null) {
              newExpressions.add(newExpression);
            }
          }
        }
      }
    }

    // Don't create a new Filter node if newExpressions is a singleton expression
    if (!newExpressions.isEmpty() && newExpressions.size() > 1) {
      List<RexNode> finalRexNodeList = new ArrayList<>();
      finalRexNodeList.add(filter.getCondition());
      finalRexNodeList.addAll(newExpressions);
      RexNode newRexNode =
          RexUtil.composeConjunction(filter.getCluster().getRexBuilder(), finalRexNodeList, true);
      // Create a new filter with the rewritten condition
      Filter newFilter = filter.copy(filter.getTraitSet(), filter.getInput(), newRexNode);
      // Replace the original filter with the new filter
      return newFilter;
    }
    return null;
  }

  RexNode extractOrClause(Filter filter, RexNode orClause, String table, RelMetadataQuery mq) {
    List<RexNode> orArgs = RelOptUtil.disjunctions(orClause);
    List<RexNode> clauselist = new ArrayList<>();
    for (RexNode orArg : orArgs) {
      List<RexNode> subClauses = new ArrayList<>();
      RexNode subclause = null;
      /* OR arguments should be ANDs or expression from same table*/
      if (orArg.isA(SqlKind.AND)) {
        List<RexNode> andArgs = RelOptUtil.conjunctions(orArg);
        for (RexNode andArg : andArgs) {
          Filter filterCopy = filter.copy(filter.getTraitSet(), filter.getInput(), andArg);
          Set<RexNode> expressionWithTableNames =
              mq.getExpressionLineage(filterCopy, filterCopy.getCondition());
          if (expressionWithTableNames == null) {
            continue;
          }
          Set<String> namesOfTablesInvolved = getTablesInvolved(expressionWithTableNames);
          if (namesOfTablesInvolved.size() == 1
              && namesOfTablesInvolved.iterator().next().equals(table)) {
            subClauses.add(andArg);
          }
        }
      } else {
        Filter filterCopy = filter.copy(filter.getTraitSet(), filter.getInput(), orArg);
        Set<RexNode> expressionWithTableNames =
            mq.getExpressionLineage(filterCopy, filterCopy.getCondition());
        if (expressionWithTableNames == null) {
          continue;
        }
        Set<String> namesOfTablesInvolved = getTablesInvolved(expressionWithTableNames);
        if (namesOfTablesInvolved.size() == 1
            && namesOfTablesInvolved.iterator().next().equals(table)) {
          subClauses.add(orArg);
        }
      }
      if (subClauses.isEmpty()) {
        return null;
      }
      // OK, add subclause(s) to the result OR.  If we found more than one,
      // we need an AND node.  But if we found only one, and it is itself an
      // OR node, add its subclauses to the result instead; this is needed
      // to preserve AND/OR flatness (ie, no OR directly underneath OR).
      subclause = RexUtil.composeConjunction(filter.getCluster().getRexBuilder(), subClauses, true);
      if (subclause.isA(SqlKind.OR)) {
        clauselist.add(subclause);
      } else {
        clauselist.add(subclause);
      }
    }
    // If we got a restriction clause from every arm, wrap them up in an OR
    // node.  (In theory the OR node might be unnecessary, if there was only
    // one arm --- but then the input OR node was also redundant.)

    if (!clauselist.isEmpty()) {
      return RexUtil.composeDisjunction(filter.getCluster().getRexBuilder(), clauselist, true);
    } else {
      return null;
    }
  }

  private Set<String> getTableNames(Set<RexTableInputRef.RelTableRef> tableSet) {
    Set<String> tableNames = new HashSet<>();
    for (RexTableInputRef.RelTableRef table : tableSet) {
      tableNames.add(table.toString());
    }
    return tableNames;
  }

  private Set<String> getTablesInvolved(Set<RexNode> expressionWithTableNames) {
    Set<String> namesOfTablesInvolved = new HashSet<>();
    for (RexNode ex : expressionWithTableNames) {
      if (ex instanceof RexCall) {
        List<RexNode> operands = ((RexCall) ex).getOperands();
        for (RexNode component : operands) {
          if (component instanceof RexTableInputRef) {
            namesOfTablesInvolved.add(((RexTableInputRef) component).getTableRef().toString());
          }
        }
      }
    }
    return namesOfTablesInvolved;
  }
} ///////// End of class
