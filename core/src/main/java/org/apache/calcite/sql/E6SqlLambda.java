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

// custom Sql Lambda

package org.apache.calcite.sql;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.validate.E6SqlLambdaScope;
import org.apache.calcite.sql.validate.SqlValidator;
import org.apache.calcite.sql.validate.SqlValidatorScope;

import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;

import static org.apache.calcite.sql.SqlPivot.stripList;

/** A <code>E6SqlLambda</code> is a node of a parse tree which represents a lambda expression. */
public class E6SqlLambda extends SqlLambda {

  public static final SqlOperator OPERATOR = new E6SqlLambda.E6SqlLambdaOperator();

  public E6SqlLambda(SqlParserPos pos, SqlNodeList parameters, SqlNode expression) {
    super(pos, parameters, expression);
  }

  @Override public SqlOperator getOperator() {
    return OPERATOR;
  }

  /**
   * The {@code SqlLambdaOperator} represents a lambda expression. The syntax : {@code IDENTIFIER ->
   * EXPRESSION} or {@code (IDENTIFIER, IDENTIFIER, ...) -> EXPRESSION}.
   */
  private static class E6SqlLambdaOperator extends SqlSpecialOperator {

    E6SqlLambdaOperator() {
      super("->", SqlKind.LAMBDA);
    }

    @Override public void unparse(SqlWriter writer, SqlCall call, int leftPrec, int rightPrec) {
      SqlNodeList parameters = call.operand(0);
      SqlNode expression = call.operand(1);
      if (parameters.size() != 1) {
        writer.list(SqlWriter.FrameTypeEnum.PARENTHESES, SqlWriter.COMMA, stripList(parameters));
      } else {
        parameters.unparse(writer, leftPrec, rightPrec);
      }
      writer.keyword(OPERATOR.getName());
      expression.unparse(writer, leftPrec, rightPrec);
    }

    @Override public RelDataType deriveType(SqlValidator validator, SqlValidatorScope scope,
        SqlCall call) {
      final E6SqlLambda lambdaExpr = (E6SqlLambda) call;
      final E6SqlLambdaScope lambdaScope = (E6SqlLambdaScope) scope;
      final List<String> paramNames =
          lambdaExpr.getParameters().stream().map(SqlNode::toString).collect(toImmutableList());
      final List<RelDataType> paramTypes =
          lambdaScope.getParameterTypes().values().stream().collect(toImmutableList());
      final RelDataType paramRowType =
          validator.getTypeFactory().createStructType(paramTypes, paramNames);
      final RelDataType returnType = validator.getValidatedNodeType(lambdaExpr.getExpression());
      return validator.getTypeFactory().createFunctionSqlType(paramRowType, returnType);
    }
  }
} ///////// End of class
