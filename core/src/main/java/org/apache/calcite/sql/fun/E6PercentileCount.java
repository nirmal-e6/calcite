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
package org.apache.calcite.sql.fun;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.sql.SqlAggFunction;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlCallBinding;
import org.apache.calcite.sql.SqlFunctionCategory;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlOperandCountRange;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.type.SqlOperandCountRanges;
import org.apache.calcite.sql.type.SqlReturnTypeInference;
import org.apache.calcite.sql.type.SqlSingleOperandTypeChecker;
import org.apache.calcite.sql.type.SqlTypeFamily;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.validate.SqlValidator;
import org.apache.calcite.sql.validate.SqlValidatorScope;
import org.apache.calcite.util.Optionality;

import java.math.BigDecimal;

import static org.apache.calcite.sql.type.OperandTypes.UNIT_INTERVAL_NUMERIC_LITERAL;
import static org.apache.calcite.util.Static.RESOURCE;

/**
 * E6 percentile aggregate variant used while rewriting ordered percentile aggregates under window
 * calls.
 */
public class E6PercentileCount extends SqlAggFunction {
  private static final SqlReturnTypeInference PERCENTILE_CONT_RETURN_TYPE =
      opBinding -> {
        RelDataType argType = opBinding.getOperandType(0);
        RelDataTypeFactory typeFactory = opBinding.getTypeFactory();
        RelDataType doubleType = typeFactory.createSqlType(SqlTypeName.DOUBLE);
        if (argType.getSqlTypeName() == SqlTypeName.ARRAY) {
          return typeFactory.createTypeWithNullability(
              typeFactory.createArrayType(doubleType, -1), true);
        }
        return typeFactory.createTypeWithNullability(doubleType, true);
      };

  private static final SqlSingleOperandTypeChecker UNIT_INTERVAL_NUMERIC_LITERAL_OR_ARRAY =
      new UnitIntervalOrArrayTypeChecker();

  public E6PercentileCount() {
    this(Optionality.MANDATORY);
  }

  public E6PercentileCount(Optionality groupOrder) {
    super(
        "PERCENTILE_CONT",
        null,
        SqlKind.PERCENTILE_CONT,
        PERCENTILE_CONT_RETURN_TYPE,
        null,
        UNIT_INTERVAL_NUMERIC_LITERAL_OR_ARRAY,
        SqlFunctionCategory.SYSTEM,
        true,
        false,
        groupOrder);
  }

  @Override public RelDataType deriveType(SqlValidator validator, SqlValidatorScope scope,
      SqlCall call) {
    final SqlValidatorScope operandScope = scope.getOperandScope(call);
    return validateOperands(validator, operandScope, call);
  }

  @Override public boolean isPercentile() {
    return true;
  }

  /**
   * Operand checker for a numeric literal in [0, 1], or an array constructor containing numeric
   * literals in [0, 1].
   */
  private static class UnitIntervalOrArrayTypeChecker implements SqlSingleOperandTypeChecker {
    @Override public boolean checkSingleOperandType(
        SqlCallBinding callBinding, SqlNode operand, int iFormalOperand, boolean throwOnFailure) {
      if (operand instanceof SqlCall) {
        final SqlCall call = (SqlCall) operand;
        if (call.getKind() == SqlKind.ARRAY_VALUE_CONSTRUCTOR
            || "ARRAY".equals(call.getOperator().getName())) {
          for (SqlNode elt : call.getOperandList()) {
            if (!checkUnitIntervalLiteralElement(callBinding, elt, throwOnFailure)) {
              return false;
            }
          }
          return true;
        }
      }
      return UNIT_INTERVAL_NUMERIC_LITERAL.checkSingleOperandType(
          callBinding, operand, iFormalOperand, throwOnFailure);
    }

    private boolean checkUnitIntervalLiteralElement(
        SqlCallBinding callBinding, SqlNode operand, boolean throwOnFailure) {
      if (!(operand instanceof SqlLiteral)) {
        return true;
      }
      final SqlLiteral arg = (SqlLiteral) operand;
      if (arg.getTypeName().getFamily() != SqlTypeFamily.NUMERIC) {
        if (throwOnFailure) {
          throw callBinding.newError(
              RESOURCE.argumentMustBeNumericLiteralInRange(
                  callBinding.getOperator().getName(), 0, 1));
        }
        return false;
      }
      final BigDecimal value = arg.getValueAs(BigDecimal.class);
      if (value == null
          || value.compareTo(BigDecimal.ZERO) < 0
          || value.compareTo(BigDecimal.ONE) > 0) {
        if (throwOnFailure) {
          throw callBinding.newError(
              RESOURCE.argumentMustBeNumericLiteralInRange(
                  callBinding.getOperator().getName(), 0, 1));
        }
        return false;
      }
      return true;
    }

    @Override public boolean checkOperandTypes(SqlCallBinding callBinding, boolean throwOnFailure) {
      return checkSingleOperandType(callBinding, callBinding.operand(0), 0, throwOnFailure);
    }

    @Override public SqlOperandCountRange getOperandCountRange() {
      return SqlOperandCountRanges.of(1);
    }

    @Override public String getAllowedSignatures(SqlOperator op, String opName) {
      return opName + "(<NUMERIC_LITERAL> | <ARRAY>)";
    }

    @Override public Consistency getConsistency() {
      return Consistency.NONE;
    }

    @Override public boolean isOptional(int i) {
      return false;
    }
  }
}
