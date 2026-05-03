package org.apache.calcite.sql.validate.implicit;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.validate.SqlValidatorScope;
import org.checkerframework.checker.nullness.qual.Nullable;

public class E6TypeCoersionHelper
{

public static boolean coerceOperandType(@Nullable SqlValidatorScope scope, SqlCall call, int index,
    RelDataType targetType, AbstractTypeCoercion typeCoercion)
{
    return typeCoercion.coerceOperandType(scope, call, index, targetType);
}

public static boolean needToCast(SqlValidatorScope scope, SqlNode node, RelDataType toType,
    AbstractTypeCoercion typeCoercion)
{
    return typeCoercion.needToCast(scope, node, toType);
}

}
