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

import org.apache.calcite.sql.SqlKind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * RexShuttle that optimizes MATCH_RECOGNIZE LAST function calls.
 *
 * <p>Converts LAST(*.$n, offset) patterns:
 *
 * <ul>
 *   <li>LAST(*.$n, 0) -> RexInputRef(n) for direct field access
 *   <li>LAST(*.$n, offset) where offset > 0 -> LAG(RexInputRef(n), offset) for PREV functionality
 * </ul>
 *
 * <p>This optimization is critical for MATCH_RECOGNIZE to work with ExpressionNode infrastructure
 * which doesn't support LAST function calls directly.
 *
 * <p>Based on VectorSearchProjectBreakdownRule.replaceVectorSearchFunctionWithRexInputRef pattern.
 */
public class RexLastOptimizer extends RexShuttle {
  private final RexBuilder m_rexBuilder;

  public RexLastOptimizer(RexBuilder rexBuilder) {
    m_rexBuilder = rexBuilder;
  }

  @Override public RexNode visitCall(RexCall call) {
    if (isLastFunctionCall(call)) {
      return optimizeLastCall(call);
    }

    // Recursively process operands (same pattern as VectorSearchProjectBreakdownRule)
    List<RexNode> newOperands = new ArrayList<>();
    boolean changed = false;
    for (RexNode operand : call.getOperands()) {
      RexNode newOperand = operand.accept(this);
      newOperands.add(newOperand);
      if (newOperand != operand) {
        changed = true;
      }
    }

    if (changed) {
      return m_rexBuilder.makeCall(call.getType(), call.getOperator(), newOperands);
    }

    return call;
  }

  /**
   * Checks if the RexCall is a LAST function call that can be optimized.
   *
   * <p>Matches patterns: LAST(*.$n, 0) and LAST(*.$n, offset) where offset >= 0
   *
   * @param call the RexCall to check
   * @return true if the call can be optimized, false otherwise
   */
  private boolean isLastFunctionCall(RexCall call) {
    if (call.getKind() != SqlKind.LAST) {
      return false;
    }

    List<RexNode> operands = call.getOperands();
    if (operands.size() != 2) {
      return false;
    }

    // Second operand must be literal integer >= 0
    RexNode secondOperand = operands.get(1);
    if (!(secondOperand instanceof RexLiteral)) {
      return false;
    }

    RexLiteral literal = (RexLiteral) secondOperand;
    Integer offset = literal.getValueAs(Integer.class);
    if (offset == null || offset < 0) {
      return false;
    }

    // First operand must be RexPatternFieldRef (*.$n)
    RexNode firstOperand = operands.get(0);
    return firstOperand instanceof RexPatternFieldRef;
  }

  /**
   * Optimizes LAST(*.$n, offset) patterns:
   *
   * <ul>
   *   <li>LAST(*.$n, 0) -> RexInputRef(n) for direct field access
   *   <li>LAST(*.$n, offset) where offset > 0 -> LAG(RexInputRef(n), offset) for PREV functionality
   * </ul>
   *
   * <p>This follows the same pattern as
   * VectorSearchProjectBreakdownRule.replaceVectorSearchFunctionWithRexInputRef.
   *
   * @param call the LAST function call to optimize
   * @return the optimized RexNode (RexInputRef or LAG call)
   */
  private RexNode optimizeLastCall(RexCall call) {
    RexNode firstOperand = call.getOperands().get(0);
    RexNode secondOperand = call.getOperands().get(1);

    if (firstOperand instanceof RexPatternFieldRef) {
      RexPatternFieldRef patternFieldRef = (RexPatternFieldRef) firstOperand;
      int fieldIndex = patternFieldRef.getIndex();
      RexInputRef inputRef = new RexInputRef(fieldIndex, call.getType());

      // Get the offset value
      RexLiteral offsetLiteral = (RexLiteral) secondOperand;
      Integer offsetValue = offsetLiteral.getValueAs(Integer.class);
      int offset = offsetValue != null ? offsetValue : 0;

      if (offset == 0) {
        // LAST(*.$n, 0) -> RexInputRef(n) for direct field access
        return inputRef;
      } else {
        // LAST(*.$n, offset) where offset > 0 -> LAG(RexInputRef(n), offset) for PREV functionality
        List<RexNode> lagOperands = Arrays.asList(inputRef, offsetLiteral);
        return m_rexBuilder.makeCall(
            call.getType(), org.apache.calcite.sql.fun.SqlStdOperatorTable.LAG, lagOperands);
      }
    }

    // Shouldn't reach here if isLastFunctionCall returned true
    return call;
  }
} ///////// End of class
