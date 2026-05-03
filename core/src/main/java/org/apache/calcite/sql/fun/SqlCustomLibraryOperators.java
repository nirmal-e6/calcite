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

import org.apache.calcite.sql.SqlAggFunction;
import org.apache.calcite.sql.SqlBinaryOperator;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.type.InferTypes;
import org.apache.calcite.sql.type.OperandTypes;
import org.apache.calcite.sql.type.ReturnTypes;
import org.apache.calcite.util.Optionality;

public class SqlCustomLibraryOperators {

  public static SqlAggFunction countIf(String name) {
    return SqlBasicAggFunction.create(
            name, SqlKind.COUNTIF, ReturnTypes.BIGINT, OperandTypes.BOOLEAN)
        .withDistinct(Optionality.FORBIDDEN);
  }

  public static SqlBinaryOperator leftShift(String name) {
    return new SqlBinaryOperator(
        name,
        SqlKind.LEFT_SHIFT,
        30,
        true,
        ReturnTypes.ARG0_NULLABLE,
        InferTypes.FIRST_KNOWN,
        OperandTypes.NUMERIC_NUMERIC);
  }

  public static SqlBinaryOperator rightShift(String name) {
    return new SqlBinaryOperator(
        name,
        SqlKind.RIGHT_SHIFT,
        30,
        true,
        ReturnTypes.ARG0_NULLABLE,
        InferTypes.FIRST_KNOWN,
        OperandTypes.NUMERIC_NUMERIC);
  }

  public static SqlBinaryOperator bitAnd(String name) {
    return new SqlBinaryOperator(
        name,
        SqlKind.BITWISE_AND,
        30,
        true,
        ReturnTypes.ARG0_NULLABLE,
        InferTypes.FIRST_KNOWN,
        OperandTypes.NUMERIC_NUMERIC);
  }
} /// ////// End of class
