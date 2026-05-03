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

import com.google.common.collect.ImmutableList;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.type.OperandTypes;
import org.apache.calcite.sql.type.ReturnTypes;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.util.Optionality;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import static java.util.Objects.requireNonNull;

/**
 * <code>Sum</code> is an aggregator which returns the sum of the values which
 * go into it. It has precisely one argument of numeric type (<code>int</code>,
 * <code>long</code>, <code>float</code>, <code>double</code>), and the result
 * is the same type.
 */
public class SqlSumAggFunction extends SqlAggFunction
{

private static final int MAX_DOUBLE_PRECISION = 19;

private static BooleanSupplier decimal128EnabledSupplier = () -> false;

//~ Instance fields --------------------------------------------------------

@Deprecated // to be removed before 2.0
private final RelDataType type;

//~ Constructors -----------------------------------------------------------

public SqlSumAggFunction(RelDataType type)
{
    super("SUM", null, SqlKind.SUM, null, null, OperandTypes.NUMERIC,
        SqlFunctionCategory.NUMERIC, false, false, Optionality.FORBIDDEN);
    this.type = type;
}

//~ Methods ----------------------------------------------------------------

@Override
public RelDataType inferReturnType(SqlOperatorBinding opBinding)
{
    RelDataType operandType = opBinding.getOperandType(0);
    if (operandType.getSqlTypeName().equals(SqlTypeName.DECIMAL))
    {
      if(operandType.getPrecision() > MAX_DOUBLE_PRECISION)
      {
        if (operandType.getScale() == 0 || decimal128EnabledSupplier.getAsBoolean())
        {
          return operandType;
        }
      }
    }
    return Objects.requireNonNull(ReturnTypes.DOUBLE_NULLABLE.inferReturnType(opBinding));
}

@SuppressWarnings("deprecation")
@Override
public List<RelDataType> getParameterTypes(RelDataTypeFactory typeFactory)
{
    return ImmutableList.of(type);
}

@Deprecated // to be removed before 2.0
public RelDataType getType()
{
    return type;
}

public static void setDecimal128EnabledSupplier(BooleanSupplier supplier)
{
    decimal128EnabledSupplier = requireNonNull(supplier, "supplier");
}

@SuppressWarnings("deprecation")
@Override
public RelDataType getReturnType(RelDataTypeFactory typeFactory)
{
    return type;
}

@Override
public <T extends Object> @Nullable T unwrap(Class<T> clazz)
{
    if (clazz.isInstance(SqlSplittableAggFunction.SumSplitter.INSTANCE))
    {
        return clazz.cast(SqlSplittableAggFunction.SumSplitter.INSTANCE);
    }
    return super.unwrap(clazz);
}

@Override
public SqlAggFunction getRollup()
{
    return this;
}

}
