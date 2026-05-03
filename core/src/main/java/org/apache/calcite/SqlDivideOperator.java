package org.apache.calcite;

import org.apache.calcite.config.CalciteForkSettings;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.sql.SqlBinaryOperator;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlOperatorBinding;
import org.apache.calcite.sql.type.*;
import org.checkerframework.checker.nullness.qual.Nullable;

public class SqlDivideOperator extends SqlBinaryOperator
{

/**
 * Creates a SqlBinaryOperator.
 *
 * @param name Name of operator
 * @param kind Kind
 * @param prec Precedence
 * @param leftAssoc Left-associativity
 * @param operandTypeInference Strategy to infer operand types
 * @param operandTypeChecker Validator for operand types
 */
public SqlDivideOperator(String name, SqlKind kind, int prec, boolean leftAssoc,
    @Nullable SqlOperandTypeInference operandTypeInference, @Nullable SqlOperandTypeChecker operandTypeChecker)
{
    super(name, kind, prec, leftAssoc, null, operandTypeInference, operandTypeChecker);
}

public RelDataType inferReturnType(SqlOperatorBinding opBinding)
{
    Boolean isDecimal128 = CalciteForkSettings.decimal128Enabled();
    RelDataType operand1 = opBinding.getOperandType(0);
    RelDataType operand2 = opBinding.getOperandType(1);
    if (SqlTypeUtil.isExactNumeric(operand1) && SqlTypeUtil.isExactNumeric(operand2) && (SqlTypeUtil.isDecimal(operand1)
        || SqlTypeUtil.isDecimal(operand2)))
    {
        if (isDecimal128)
        {
            RelDataTypeFactory typeFactory = opBinding.getTypeFactory();
            RelDataType relDataType = typeFactory.getTypeSystem()
                .deriveDecimalDivideType(typeFactory, operand1, operand2);
            return relDataType != null && (operand1.isNullable() || operand2.isNullable())
                   ? typeFactory.createTypeWithNullability(relDataType, true) : relDataType;
        }
    }
    return ReturnTypes.DOUBLE_NULLABLE.inferReturnType(opBinding);
}

}
