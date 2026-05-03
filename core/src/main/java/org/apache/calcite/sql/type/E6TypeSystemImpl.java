package org.apache.calcite.sql.type;

import org.apache.calcite.config.CalciteForkSettings;
import org.apache.calcite.rel.type.*;
import org.checkerframework.checker.nullness.qual.Nullable;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class E6TypeSystemImpl extends RelDataTypeSystemImpl
{

public static final int MAX_DECIMAL_PRECISION = 38;
private static final int MAX_DECIMAL_SCALE = 38;
private static final int DEFAULT_DECIMAL_PRECISION = 38; // For backward compatibility
public static final int MAX_DOUBLE_PRECISION = 19;
private static final int MINIMUM_DECIMAL_ADJUSTED_SCALE = 6;
public static final boolean ALLOW_DECIMAL_PRECISION_LOSS = true; // TODO: Make it configurable

private static final E6TypeSystemImpl INSTANCE = new E6TypeSystemImpl();

public static RelDataTypeSystem getInstance()
{
    return INSTANCE;
}

private E6TypeSystemImpl()
{
}

private static boolean isDecimal128Enabled()
{
    return CalciteForkSettings.decimal128Enabled();
}

@Override
public int getMaxNumericPrecision()
{
    return isDecimal128Enabled() ? MAX_DECIMAL_PRECISION : super.getMaxNumericPrecision();
}

@Override
public int getMaxNumericScale()
{
    return isDecimal128Enabled() ? MAX_DECIMAL_SCALE : super.getMaxNumericScale();
}

@Override
public int getMaxScale(SqlTypeName typeName)
{
    if (isDecimal128Enabled() && isDecimal(typeName))
    {
        return getMaxNumericScale();
    }
    return super.getMaxScale(typeName);
}

@Override
public int getDefaultPrecision(SqlTypeName typeName)
{
    if (isDecimal128Enabled() && isDecimal(typeName))
    {
        return DEFAULT_DECIMAL_PRECISION;
    }
    return super.getDefaultPrecision(typeName);
}

@Override
public int getMaxPrecision(SqlTypeName typeName)
{
    if (isDecimal128Enabled() && isDecimal(typeName))
    {
        return getMaxNumericPrecision();
    }
    if (isDatetime(typeName))
    {
        return 6;
    }
    return super.getMaxPrecision(typeName);
}

private static boolean isDatetime(SqlTypeName typeName)
{
    switch (typeName)
    {
    case TIMESTAMP:
    case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
    case TIMESTAMP_TZ:
    case TIME:
    case TIME_WITH_LOCAL_TIME_ZONE:
    case TIME_TZ:
        return true;
    default:
        return false;
    }
}

@Override
public @Nullable RelDataType deriveDecimalDivideType(RelDataTypeFactory typeFactory, RelDataType operand1,
    RelDataType operand2)
{
    if (isDecimal128Enabled()
        && SqlTypeUtil.isExactNumeric(operand1) && SqlTypeUtil.isExactNumeric(operand2)
        && (SqlTypeUtil.isDecimal(operand1) || SqlTypeUtil.isDecimal(operand2)))
    {
        int p1 = operand1.getPrecision();
        int s1 = operand1.getScale();
        int p2 = operand2.getPrecision();
        int s2 = operand2.getScale();
        int precision = computePrecision(p1, s1, p2, s2);
        int scale = computeScale(p1, s1, p2, s2);
        return typeFactory.createSqlType(SqlTypeName.DECIMAL, precision, scale);
    }
    return super.deriveDecimalDivideType(typeFactory, operand1, operand2);
}

private int computePrecision(int a_precision, int a_scale, int b_precision, int b_scale)
{
    return min(a_precision - a_scale + b_scale + max(6, a_scale + b_precision + 1), 38);
}

private int computeScale(int a_precision, int a_scale, int b_precision, int b_scale)
{
    int raw_precision = a_precision - a_scale + b_scale + max(6, a_scale + b_precision + 1);
    int raw_scale = max(6, a_scale + b_precision + 1);
    int integral = raw_precision - raw_scale;
    int scale;
    if (integral > 32)
    {
        scale = 6;
    }
    else
    {
        scale = 38 - integral;
    }
    return min(raw_scale, scale);
}

private static boolean isDecimal(SqlTypeName typeName)
{
    return SqlTypeName.DECIMAL.equals(typeName);
}

private RelDataType bounded(RelDataTypeFactory typeFactory, int precision, int scale)
{
    return typeFactory.createSqlType(SqlTypeName.DECIMAL, Math.min(precision, MAX_DECIMAL_PRECISION),
        Math.min(scale, MAX_DECIMAL_SCALE));
}

private RelDataType adjustPrecisionScale(RelDataTypeFactory typeFactory, int precision, int scale)
{
    // Assumptions: No negative scale
    assert (precision >= scale);

    if (precision <= MAX_DECIMAL_PRECISION)
    {
        // Adjustment only needed when we exceed max precision
        return typeFactory.createSqlType(SqlTypeName.DECIMAL, precision, scale);
    }
    else if (scale < 0)
    {
        // Decimal can have negative scale (SPARK-24468). In this case, we cannot allow a precision
        // loss since we would cause a loss of digits in the integer part.
        // In this case, we are likely to meet an overflow.
        return typeFactory.createSqlType(SqlTypeName.DECIMAL, MAX_DECIMAL_PRECISION, scale);
    }
    else
    {
        // Precision/scale exceed maximum precision. Result must be adjusted to MAX_PRECISION.
        int intDigits = precision - scale;
        // If original scale is less than MINIMUM_ADJUSTED_SCALE, use original scale value; otherwise
        // preserve at least MINIMUM_ADJUSTED_SCALE fractional digits
        int minScaleValue = Math.min(scale, MINIMUM_DECIMAL_ADJUSTED_SCALE);
        // The resulting scale is the maximum between what is available without causing a loss of
        // digits for the integer part of the decimal and the minimum guaranteed scale, which is
        // computed above
        int adjustedScale = Math.max(MAX_DECIMAL_PRECISION - intDigits, minScaleValue);

        return typeFactory.createSqlType(SqlTypeName.DECIMAL, MAX_DECIMAL_PRECISION, adjustedScale);
    }
}

@Override
public @Nullable RelDataType deriveDecimalMultiplyType(RelDataTypeFactory typeFactory, RelDataType type1,
    RelDataType type2)
{
    if (!isDecimal128Enabled())
    {
        return super.deriveDecimalMultiplyType(typeFactory, type1, type2);
    }
    if (SqlTypeUtil.isInterval(type1) || SqlTypeUtil.isInterval(type2))
    {
        return super.deriveDecimalMultiplyType(typeFactory, type1, type2);
    }
    // Only handle cases where at least one operand is decimal
    if (!SqlTypeUtil.isDecimal(type1) && !SqlTypeUtil.isDecimal(type2))
    {
        // Neither is decimal - let parent handle it
        return super.deriveDecimalMultiplyType(typeFactory, type1, type2);
    }

    type1 = RelDataTypeFactoryImpl.isJavaType(type1) ? typeFactory.decimalOf(type1) : type1;
    type2 = RelDataTypeFactoryImpl.isJavaType(type2) ? typeFactory.decimalOf(type2) : type2;

    int p1 = type1.getPrecision();
    int p2 = type2.getPrecision();
    int s1 = type1.getScale();
    int s2 = type2.getScale();

    int resultScale;

    if (SqlTypeUtil.isDecimal(type1) && SqlTypeUtil.isDecimal(type2))
    {
        // Both are decimal - standard behavior
        resultScale = s1 + s2;
    }
    else
    {
        // One decimal, one non-decimal
        RelDataType decimalType = SqlTypeUtil.isDecimal(type1) ? type1 : type2;
        RelDataType otherType = SqlTypeUtil.isDecimal(type1) ? type2 : type1;

        if (SqlTypeUtil.isApproximateNumeric(otherType))
        {
            // Float/double with decimal
            int decimalScale = decimalType.getScale();
            resultScale = Math.max(decimalScale, MINIMUM_DECIMAL_ADJUSTED_SCALE);
        }
        else
        {
            // Int with decimal - use decimal's scale
            resultScale = decimalType.getScale();
        }
    }

    // Calculate result precision
    int resultPrecision = p1 + p2 + 1;

    RelDataType ret;
    if (ALLOW_DECIMAL_PRECISION_LOSS)
    {
        ret = adjustPrecisionScale(typeFactory, resultPrecision, resultScale);
    }
    else
    {
        ret = bounded(typeFactory, resultPrecision, resultScale);
    }

    return ret;
}

} ///////// End of class
