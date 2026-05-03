/*
 * Copyright (c) 2023 Uniphi Inc
 * All rights reserved.
 *
 * File Name: SqlLibraryOperators.java
 *
 * Created On: 2023-07-17
 */

package org.apache.calcite.sql.fun;

import org.apache.calcite.sql.SqlAggFunction;
import org.apache.calcite.sql.SqlBinaryOperator;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.type.InferTypes;
import org.apache.calcite.sql.type.OperandTypes;
import org.apache.calcite.sql.type.ReturnTypes;
import org.apache.calcite.util.Optionality;

public class SqlCustomLibraryOperators
{

public static SqlAggFunction countIf(String name)
{
    return SqlBasicAggFunction.create(name, SqlKind.COUNTIF, ReturnTypes.BIGINT, OperandTypes.BOOLEAN)
        .withDistinct(Optionality.FORBIDDEN);
}

public static SqlBinaryOperator leftShift(String name)
{
    return new SqlBinaryOperator(name, SqlKind.valueOf("LEFT_SHIFT"), 30, true, ReturnTypes.ARG0_NULLABLE, InferTypes.FIRST_KNOWN,
        OperandTypes.NUMERIC_NUMERIC);
}

public static SqlBinaryOperator rightShift(String name)
{
    return new SqlBinaryOperator(name, SqlKind.valueOf("RIGHT_SHIFT"), 30, true, ReturnTypes.ARG0_NULLABLE, InferTypes.FIRST_KNOWN,
        OperandTypes.NUMERIC_NUMERIC);
}

public static SqlBinaryOperator bitAnd(String name)
{
    return new SqlBinaryOperator(name, SqlKind.valueOf("BITWISE_AND"), 30, true, ReturnTypes.ARG0_NULLABLE, InferTypes.FIRST_KNOWN,
        OperandTypes.NUMERIC_NUMERIC);
}

} /// ////// End of class
