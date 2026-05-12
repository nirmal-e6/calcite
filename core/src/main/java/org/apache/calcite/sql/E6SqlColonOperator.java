package org.apache.calcite.sql;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.*;

import java.util.Arrays;

import static java.util.Objects.requireNonNull;
import static org.apache.calcite.util.Static.RESOURCE;

public class E6SqlColonOperator extends SqlSpecialOperator
{

public E6SqlColonOperator()
{
    super("COLON", SqlKind.COLON, 100, true, RETURN_TYPE_INFERENCE, TYPE_INFERENCE, TYPE_CHECKER);
}

public static final SqlReturnTypeInference RETURN_TYPE_INFERENCE = (operatorBinding) ->
{
    // The third operand is the type to cast to (we combine the cast into this operator).
    RelDataType operandType = operatorBinding.getOperandType(2);
    if (operandType.getSqlTypeName().equals(SqlTypeName.ANY))
    {
        // When no cast is specified, check the type of the first operand
        RelDataType firstOperandType = operatorBinding.getOperandType(0);
        // If the first operand is VARCHAR/STRING, keep it as VARCHAR to allow string operations
        if (firstOperandType.getSqlTypeName() == SqlTypeName.VARCHAR)
        {
            return firstOperandType;
        }
        else
        {
            // Otherwise, return VARIANT type to let the executor handle the extraction
            return operatorBinding.getTypeFactory().createSqlType(SqlTypeName.VARIANT);
        }
    }
    return operandType;
};

public static final SqlOperandTypeInference TYPE_INFERENCE = (callBinding, returnType, operandTypes) ->
{
    // Get the first operand type
    SqlNode firstOperand = callBinding.operands().get(0);
    RelDataType firstOperandType = callBinding.getValidator().deriveType(callBinding.getScope(), firstOperand);
    callBinding.getValidator().setValidatedNodeType(firstOperand, firstOperandType);

    // Get the type of JSON path string
    SqlNode jsonPath = callBinding.operands().get(1);
    RelDataType jsonPathType = callBinding.getValidator().deriveType(callBinding.getScope(), jsonPath);
    callBinding.getValidator().setValidatedNodeType(jsonPath, jsonPathType);

    // Get the cast type
    SqlNode castNode = callBinding.operands().get(2);
    RelDataType castType = callBinding.getValidator().deriveType(callBinding.getScope(), castNode);
    if (castType.getSqlTypeName().equals(SqlTypeName.ANY))
    {
        // We fall back to ANY type in the parser when there's no cast specified.
        // In that case, we assume that no cast is happening and the cast type is the first operand's type.
        callBinding.getValidator().setValidatedNodeType(castNode, firstOperandType);
    }
    else
    {
        callBinding.getValidator().setValidatedNodeType(castNode, castType);
    }
};

public static final SqlOperandTypeChecker TYPE_CHECKER = new SqlOperandTypeChecker()
{
    private boolean ensureLiteral(SqlCallBinding callBinding, int position, boolean throwOnFailure)
    {
        if (!callBinding.isOperandLiteral(position, false))
        {
            if (throwOnFailure)
            {
                throw callBinding.getValidator()
                    .newValidationError(callBinding.getCall(), RESOURCE.argumentMustBeLiteral("COLON"));
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean checkOperandTypes(SqlCallBinding callBinding, boolean throwOnFailure)
    {
        SqlNode firstOperand = callBinding.operand(0);
        RelDataType firstOperandType = SqlTypeUtil.deriveType(callBinding, firstOperand);

        // the first operand can be a variant or a string
        boolean isVariant = VariantTypeUtil.checkVariantType(firstOperandType);
        boolean isString = OperandTypes.STRING.checkSingleOperandType(callBinding, callBinding.operand(0), 0, false);
        if (!isVariant && !isString)
        {
            if (throwOnFailure)
            {
                throw callBinding.newValidationSignatureError();
            }
            return false;
        }

        // 2nd operand is the JSON path - always a literal string
        OperandTypes.STRING.checkSingleOperandType(callBinding, callBinding.operand(1), 0, throwOnFailure);
        return ensureLiteral(callBinding, 1, throwOnFailure);
    }

    @Override
    public SqlOperandCountRange getOperandCountRange()
    {
        return SqlOperandCountRanges.of(3);
    }

    @Override
    public String getAllowedSignatures(SqlOperator op, String opName)
    {
        return "column:a.json.path or column:a.json.path::type";
    }

    @Override
    public boolean isOptional(int i)
    {
        return false;
    }
};

@Override
public ReduceResult reduceExpr(int ordinal, TokenSequence list)
{
    SqlNode left = list.node(ordinal - 1);
    SqlNode secondArg = list.node(ordinal + 1);
    SqlNode thirdArg = list.node(ordinal + 2);
    return new ReduceResult(ordinal - 1, ordinal + 3, createCall(SqlParserPos.sum(
        Arrays.asList(requireNonNull(left, "left").getParserPosition(),
            requireNonNull(secondArg, "secondArg").getParserPosition(),
            requireNonNull(thirdArg, "thirdArg").getParserPosition(), list.pos(ordinal))), left, secondArg, thirdArg));
}

@Override
public SqlOperandCountRange getOperandCountRange()
{
    return SqlOperandCountRanges.of(3);
}

@Override
public void unparse(SqlWriter writer, SqlCall call, int leftPrec, int rightPrec)
{
    assert call.operandCount() == 3;
    call.operand(0).unparse(writer, leftPrec, rightPrec);
    writer.literal(":");
    call.operand(1).unparse(writer, leftPrec, rightPrec);
    writer.literal("::");
    String castType = call.operand(2).toString();
    writer.literal(castType);
}

}
