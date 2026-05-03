/*
 * Copyright (c) 2024 Uniphi Inc
 * All rights reserved.
 *
 * File Name: DecodeTypeChecker.java
 *
 * Created On: 2024-06-19
 */

package org.apache.calcite.sql.validate.implicit;

import com.google.common.collect.ImmutableList;
import org.apache.calcite.linq4j.Ord;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.type.SqlOperandCountRanges;
import org.apache.calcite.sql.type.SqlOperandTypeChecker;
import org.apache.calcite.sql.type.SqlTypeFamily;
import org.apache.calcite.sql.type.SqlTypeUtil;
import org.apache.calcite.sql.validate.SqlValidatorScope;

import java.util.ArrayList;
import java.util.List;

import org.apache.calcite.config.CalciteForkSettings;

import static org.apache.calcite.util.Static.RESOURCE;

public class DecodeTypeChecker implements SqlOperandTypeChecker
{

@Override
public boolean checkOperandTypes(SqlCallBinding callBinding, boolean throwOnFailure)
{
    int nOperandCount = callBinding.getOperandCount();

    // 2-arg charset decode: DECODE(binary_expr, charset_string) — native executor only
    if (nOperandCount == 2 && CalciteForkSettings.nativeExecutor())
    {
        RelDataType arg1Type = callBinding.getOperandType(1);
        boolean valid = SqlTypeFamily.CHARACTER.contains(arg1Type);
        if (!valid && throwOnFailure)
        {
            throw callBinding.getValidator().newValidationError(
                callBinding.operand(1),
                RESOURCE.incompatibleValueType(SqlTypeFamily.CHARACTER.toString()));
        }
        return valid;
    }

    // 3+ arg conditional decode: DECODE(expr, search1, result1, ..., [default])
    List<SqlTypeFamily> expectedFamilies = new ArrayList<>();
    for (int i = 0; i < nOperandCount; i++)
    {
        expectedFamilies.add(SqlTypeFamily.ANY);
    }

    RelDataType selectType = callBinding.getOperandType(0);
    RelDataType resultType = callBinding.getOperandType(2);
    boolean isDefaultPresent = nOperandCount % 2 == 0;

    int size = isDefaultPresent ? nOperandCount - 1 : nOperandCount;
    expectedFamilies.set(0, selectType.getSqlTypeName().getFamily());
    for (int i = 1; i < size; i = i + 2)
    {
        expectedFamilies.set(i, selectType.getSqlTypeName().getFamily());
    }

    for (int i = 2; i < size; i = i + 2)
    {
        expectedFamilies.set(i, resultType.getSqlTypeName().getFamily());
    }

    if (isDefaultPresent)
    {
        expectedFamilies.set(nOperandCount - 1, resultType.getSqlTypeName().getFamily());
    }
    boolean needToCoerce = true;
    for (Ord<SqlNode> op : Ord.zip(callBinding.operands()))
    {
        needToCoerce = needToCoerce && checkSingleOperandType(callBinding, op.e, op.i, selectType, resultType,
            isDefaultPresent, nOperandCount, false);
    }

    // try to coerce type if it is allowed.
    if (!needToCoerce)
    {
        boolean coerced = false;
        if (callBinding.isTypeCoercionEnabled())
        {
            TypeCoercion typeCoercion = callBinding.getValidator().getTypeCoercion();
            AbstractTypeCoercion abstractTypeCoercion = (AbstractTypeCoercion) typeCoercion;
            ImmutableList.Builder<RelDataType> builder = ImmutableList.builder();
            for (int i = 0; i < callBinding.getOperandCount(); i++)
            {
                builder.add(callBinding.getOperandType(i));
            }
            ImmutableList<RelDataType> dataTypes = builder.build();
            coerced = coerce(expectedFamilies, abstractTypeCoercion, dataTypes, callBinding);
        }
        // re-validate the new nodes type.

        for (Ord<SqlNode> op1 : Ord.zip(callBinding.operands()))
        {
            if (!checkSingleOperandType(callBinding, op1.e, op1.i, selectType, resultType, isDefaultPresent,
                nOperandCount, throwOnFailure))
            {
                return false;
            }
        }
        return coerced;
    }

    return false;
}

private static boolean coerce(List<SqlTypeFamily> expectedFamilies, AbstractTypeCoercion abstractTypeCoercion,
    ImmutableList<RelDataType> dataTypes, SqlCallBinding callBinding)
{
    RelDataTypeFactory factory = callBinding.getTypeFactory();
    SqlValidatorScope scope = callBinding.getScope();
    SqlCall call = callBinding.getCall();
    boolean coerced = false;
    for (int i = 0; i < expectedFamilies.size(); i++)
    {
        RelDataType in = dataTypes.get(i);
        SqlTypeFamily expected = expectedFamilies.get(i);
        if (expected.getTypeNames().contains(in.getSqlTypeName()))
        {
            continue;
        }
        RelDataType coeredType = abstractTypeCoercion.implicitCast(in, expected);
        if (coeredType == null)
        {
            // STRING + NUMERIC -> BOOLEAN
            if ((SqlTypeUtil.isCharacter(in) || SqlTypeUtil.isNumeric(in)) && expected == SqlTypeFamily.BOOLEAN)
            {
                coeredType = expected.getDefaultConcreteType(factory);
            }
        }
        if (coeredType == in)
        {
            continue;
        }
        else if (coeredType == null)
        {
            coerced = false;
            break;
        }

        abstractTypeCoercion.coerceOperandType(scope, call, i, coeredType);
        coerced = true;
    }
    return coerced;
}

private boolean checkSingleOperandType(SqlCallBinding callBinding, SqlNode sqlNode, int iFormalOperand,
    RelDataType selectType, RelDataType resultType, boolean isDefaultPresent, int operandCount, boolean throwOnFailure)
{
    if (SqlUtil.isNullLiteral(sqlNode, false))
    {
        if (callBinding.isTypeCoercionEnabled())
        {
            return true;
        }
        else if (throwOnFailure)
        {
            throw callBinding.getValidator().newValidationError(sqlNode, RESOURCE.nullIllegal());
        }
        else
        {
            return false;
        }
    }
    RelDataType type = SqlTypeUtil.deriveType(callBinding, sqlNode);
    if (iFormalOperand == 0)
    {
        return selectType.getSqlTypeName() == type.getSqlTypeName();
    }
    if (isDefaultPresent && iFormalOperand == operandCount - 1)
    {
        return resultType.getSqlTypeName() == type.getSqlTypeName();
    }

    if (iFormalOperand % 2 == 0)
    {
        return resultType.getSqlTypeName() == type.getSqlTypeName();
    }

    if (iFormalOperand % 2 == 1)
    {
        return selectType.getSqlTypeName() == type.getSqlTypeName();
    }
    return true;
}

@Override
public SqlOperandCountRange getOperandCountRange()
{
    return SqlOperandCountRanges.from(CalciteForkSettings.nativeExecutor() ? 2 : 3);
}

@Override
public String getAllowedSignatures(SqlOperator op, String opName)
{
    String conditionalSig = "DECODE( <expr> , <search1> , <result1> [ , <search2> , <result2> ... ] [ , <default> ] )";
    return CalciteForkSettings.nativeExecutor()
        ? "DECODE( <binary_expr> , <charset> ) or " + conditionalSig
        : conditionalSig;
}

@Override
public Consistency getConsistency()
{
    return Consistency.NONE;
}

@Override
public boolean isOptional(int i)
{
    return false;
}

} /// ////// End of class
