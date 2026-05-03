/*
 * Copyright (c) 2024 Uniphi Inc
 * All rights reserved.
 *
 * File Name: E6SqlTypeFactoryImpl.java
 *
 * Created On: 2024-08-16
 */

package org.apache.calcite.sql.type;

import org.apache.calcite.config.CalciteForkSettings;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFamily;
import org.apache.calcite.rel.type.RelDataTypeImpl;
import org.apache.calcite.rel.type.RelDataTypeSystem;
import org.apache.calcite.sql.SqlCollation;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.nio.charset.Charset;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

/**
 * Extended to change functionality of leastRestrictive method and create sql type with our expected scale changes are
 * in line no. 361, 435, 466
 */

public class E6SqlTypeFactoryImpl extends SqlTypeFactoryImpl
{

public E6SqlTypeFactoryImpl(RelDataTypeSystem typeSystem)
{
    super(typeSystem);
}

@Override
public @Nullable RelDataType leastRestrictive(List<RelDataType> types, SqlTypeMappingRule mappingRule)
{
    requireNonNull(types, "types");
    requireNonNull(mappingRule, "mappingRule");
    checkArgument(types.size() >= 1, "types.size >= 1");

    RelDataType type0 = types.get(0);
    if (type0.getSqlTypeName() != null)
    {
        RelDataType resultType = leastRestrictiveSqlType(types);
        if (resultType != null)
        {
            return resultType;
        }
        return leastRestrictiveByCast(types, mappingRule);
    }

    return super.leastRestrictive(types, mappingRule);
}

@SuppressWarnings("deprecation")
private @Nullable RelDataType leastRestrictiveSqlType(List<RelDataType> types)
{
    RelDataType resultType = null;
    int nullCount = 0;
    int nullableCount = 0;
    int javaCount = 0;
    int anyCount = 0;

    for (RelDataType type : types)
    {
        final SqlTypeName typeName = type.getSqlTypeName();
        if (typeName == null)
        {
            return null;
        }
        if (typeName == SqlTypeName.ANY)
        {
            anyCount++;
        }
        if (type.isNullable())
        {
            ++nullableCount;
        }
        if (typeName == SqlTypeName.NULL)
        {
            ++nullCount;
        }
        if (isJavaType(type))
        {
            ++javaCount;
        }
    }

    //  if any of the inputs are ANY, the output is ANY
    if (anyCount > 0)
    {
        return createTypeWithNullability(createSqlType(SqlTypeName.ANY), nullCount > 0 || nullableCount > 0);
    }

    for (int i = 0; i < types.size(); ++i)
    {
        RelDataType type = types.get(i);
        RelDataTypeFamily family = type.getFamily();

        final SqlTypeName typeName = type.getSqlTypeName();
        if (typeName == SqlTypeName.NULL)
        {
            continue;
        }

        // Convert Java types; for instance, JavaType(int) becomes INTEGER.
        // Except if all types are either NULL or Java types.
        if (isJavaType(type) && javaCount + nullCount < types.size())
        {
            final RelDataType originalType = type;
            type = typeName.allowsPrecScale(true, true) ? createSqlType(typeName, type.getPrecision(), type.getScale())
                                                        : typeName.allowsPrecScale(true, false) ? createSqlType(
                                                            typeName, type.getPrecision()) : createSqlType(typeName);
            type = createTypeWithNullability(type, originalType.isNullable());
        }

        if (resultType == null)
        {
            resultType = type;
            SqlTypeName sqlTypeName = resultType.getSqlTypeName();
            if (sqlTypeName == SqlTypeName.ROW)
            {
                return leastRestrictiveStructuredType(types);
            }
            if (sqlTypeName == SqlTypeName.ARRAY || sqlTypeName == SqlTypeName.MULTISET)
            {
                return leastRestrictiveArrayMultisetType(types, sqlTypeName);
            }
            if (sqlTypeName == SqlTypeName.MAP)
            {
                return leastRestrictiveMapType(types, sqlTypeName);
            }
        }

        RelDataTypeFamily resultFamily = resultType.getFamily();
        SqlTypeName resultTypeName = resultType.getSqlTypeName();

        if (resultFamily != family)
        {
            return null;
        }
        if (SqlTypeUtil.inCharOrBinaryFamilies(type))
        {
            Charset charset1 = type.getCharset();
            Charset charset2 = resultType.getCharset();
            SqlCollation collation1 = type.getCollation();
            SqlCollation collation2 = resultType.getCollation();

            final int precision = SqlTypeUtil.maxPrecision(resultType.getPrecision(), type.getPrecision());

            // If either type is LOB, then result is LOB with no precision.
            // Otherwise, if either is variable width, result is variable
            // width.  Otherwise, result is fixed width.
            if (SqlTypeUtil.isLob(resultType))
            {
                resultType = createSqlType(resultType.getSqlTypeName());
            }
            else if (SqlTypeUtil.isLob(type))
            {
                resultType = createSqlType(type.getSqlTypeName());
            }
            else if (SqlTypeUtil.isBoundedVariableWidth(resultType))
            {
                resultType = createSqlType(resultType.getSqlTypeName(), precision);
            }
            else
            {
                // this catch-all case covers type variable, and both fixed

                SqlTypeName newTypeName = type.getSqlTypeName();

                if (typeSystem.shouldConvertRaggedUnionTypesToVarying())
                {
                    if (resultType.getPrecision() != type.getPrecision())
                    {
                        if (newTypeName == SqlTypeName.CHAR)
                        {
                            newTypeName = SqlTypeName.VARCHAR;
                        }
                        else if (newTypeName == SqlTypeName.BINARY)
                        {
                            newTypeName = SqlTypeName.VARBINARY;
                        }
                    }
                }

                resultType = createSqlType(newTypeName, precision);
            }
            Charset charset = null;
            // TODO:  refine collation combination rules
            SqlCollation collation0 = collation1 != null && collation2 != null
                                      ? SqlCollation.getCoercibilityDyadicOperator(collation1, collation2) : null;
            SqlCollation collation = null;
            if ((charset1 != null) || (charset2 != null))
            {
                if (charset1 == null)
                {
                    charset = charset2;
                    collation = collation2;
                }
                else if (charset2 == null)
                {
                    charset = charset1;
                    collation = collation1;
                }
                else if (charset1.equals(charset2))
                {
                    charset = charset1;
                    collation = collation1;
                }
                else if (charset1.contains(charset2))
                {
                    charset = charset1;
                    collation = collation1;
                }
                else
                {
                    charset = charset2;
                    collation = collation2;
                }
            }
            if (charset != null)
            {
                resultType = createTypeWithCharsetAndCollation(resultType, charset,
                    collation0 != null ? collation0 : requireNonNull(collation, "collation"));
            }
        }
        else if (SqlTypeUtil.isExactNumeric(type))
        {
            if (SqlTypeUtil.isExactNumeric(resultType))
            {
                // TODO: come up with a cleaner way to support
                // interval + datetime = datetime
                if (types.size() > (i + 1))
                {
                    RelDataType type1 = types.get(i + 1);
                    if (SqlTypeUtil.isDatetime(type1))
                    {
                        resultType = type1;
                        return createTypeWithNullability(resultType, nullCount > 0 || nullableCount > 0);
                    }
                }
                if (!type.equals(resultType))
                {
                    if (!typeName.allowsPrec() && !resultTypeName.allowsPrec())
                    {
                        // use the bigger primitive
                        if (type.getPrecision() > resultType.getPrecision())
                        {
                            resultType = type;
                        }
                    }
                    else
                    {
                        // Let the result type have precision (p), scale (s)
                        // and number of whole digits (d) as follows: d =
                        // max(p1 - s1, p2 - s2) s <= max(s1, s2) p = s + d

                        int p1 = resultType.getPrecision();
                        int p2 = type.getPrecision();
                        int s1 = resultType.getScale();
                        int s2 = type.getScale();
                        final int maxPrecision = typeSystem.getMaxNumericPrecision();
                        final int maxScale = typeSystem.getMaxNumericScale();

                        int dout = Math.max(p1 - s1, p2 - s2);
                        dout = Math.min(dout, maxPrecision);

                        int scale = Math.max(s1, s2);
                        scale = Math.min(scale, maxPrecision - dout);
                        scale = Math.min(scale, maxScale);

                        int precision = dout + scale;
                        assert precision <= maxPrecision;
                        assert precision > 0 || (resultType.getSqlTypeName() == SqlTypeName.DECIMAL && precision == 0
                            && scale == 0);

                        resultType = createSqlType(SqlTypeName.DECIMAL, precision, scale);
                    }
                }
            }
            else if (SqlTypeUtil.isApproximateNumeric(resultType))
            {
                // already approximate; promote to double just in case
                // TODO:  only promote when required
                if (SqlTypeUtil.isDecimal(type))
                {
                    // Only promote to double for decimal types
                    resultType = createDoublePrecisionType();
                }
            }
            else
            {
                return null;
            }
        }
        else if (SqlTypeUtil.isApproximateNumeric(type))
        {
            if (SqlTypeUtil.isApproximateNumeric(resultType))
            {
                if (type.getPrecision() > resultType.getPrecision())
                {
                    resultType = type;
                }
            }
            else if (SqlTypeUtil.isExactNumeric(resultType))
            {
                if (SqlTypeUtil.isDecimal(resultType))
                {
                    resultType = createDoublePrecisionType();
                }
                else
                {
                    resultType = type;
                }
            }
            else
            {
                return null;
            }
        }
        else if (SqlTypeUtil.isInterval(type))
        {
            // TODO: come up with a cleaner way to support
            // interval + datetime = datetime
            if (types.size() > (i + 1))
            {
                RelDataType type1 = types.get(i + 1);
                if (SqlTypeUtil.isDatetime(type1))
                {
                    resultType = leastRestrictiveIntervalDatetimeType(type1, type);
                    return createTypeWithNullability(resultType, nullCount > 0 || nullableCount > 0);
                }
            }

            if (!type.equals(resultType))
            {
                // TODO jvs 4-June-2005:  This shouldn't be necessary;
                // move logic into IntervalSqlType.combine
                Object type1 = resultType;
                resultType = ((IntervalSqlType) resultType).combine(this, (IntervalSqlType) type);
                resultType = ((IntervalSqlType) resultType).combine(this, (IntervalSqlType) type1);
            }
        }
        else if (SqlTypeUtil.isDatetime(type))
        {
            // TODO: come up with a cleaner way to support
            // datetime +/- interval (or integer) = datetime
            if (types.size() > (i + 1))
            {
                RelDataType type1 = types.get(i + 1);
                final boolean isInterval1 = SqlTypeUtil.isInterval(type1);
                final boolean isInt1 = SqlTypeUtil.isIntType(type1);
                // E6data change
                // is date and interval is there then return timestamp
                if(CalciteForkSettings.databricks() && isInterval1 && SqlTypeUtil.isDate(resultType))
                {
                    resultType = createSqlType(SqlTypeName.TIMESTAMP);
                    return createTypeWithNullability(resultType, nullCount > 0 || nullableCount > 0);
                }

                else if (isInterval1 || isInt1)
                {
                    resultType = leastRestrictiveIntervalDatetimeType(type, type1);
                    return createTypeWithNullability(resultType, nullCount > 0 || nullableCount > 0);
                }
                // (E6DATA IMPL) NOT PART OF CALCITE BACKPORT
                // get the least restrictive datetime
                else if (!resultTypeName.getName().equals(type1.getSqlTypeName().getName()))
                {
                    resultType = getLeastRestrictiveDateTime(resultType, type1);
                }
            }

            if (type.getSqlTypeName() == resultType.getSqlTypeName() && type.getSqlTypeName().allowsPrec()
                && type.getPrecision() != resultType.getPrecision())
            {
                final int precision = SqlTypeUtil.maxPrecision(resultType.getPrecision(), type.getPrecision());

                resultType = createSqlType(type.getSqlTypeName(), precision);
            }
        }
        else
        {
            // TODO:  datetime precision details; for now we let
            // leastRestrictiveByCast handle it
            return null;
        }
    }
    if (resultType != null && nullableCount > 0)
    {
        resultType = createTypeWithNullability(resultType, true);
    }
    return resultType;
}

private RelDataType createDoublePrecisionType()
{
    return createSqlType(SqlTypeName.DOUBLE);
}

private RelDataType getLeastRestrictiveDateTime(RelDataType type, RelDataType comparingType)
{
    int dateTimePrecedenceType1 = getTypePrecedenceForDatetime(type);
    int dateTimePrecedenceType2 = getTypePrecedenceForDatetime(comparingType);

    // if any of type is not datetime type then return first type
    if ((dateTimePrecedenceType1 == -1 || dateTimePrecedenceType2 == -1)
        || dateTimePrecedenceType1 < dateTimePrecedenceType2)
    {
        return type;
    }

    return comparingType;
}

/**
 * lower number has higher precedence
 */
private int getTypePrecedenceForDatetime(RelDataType type)
{
    switch (type.getSqlTypeName())
    {
    case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
        return 0;
    case TIMESTAMP:
        return 1;
    case DATE:
        return 2;
    default:
        return -1;
    }
}

private @Nullable RelDataType leastRestrictiveByCast(List<RelDataType> types, SqlTypeMappingRule mappingRule)
{
    RelDataType resultType = types.get(0);
    boolean anyNullable = resultType.isNullable();
    for (int i = 1; i < types.size(); i++)
    {
        RelDataType type = types.get(i);
        if (type.getSqlTypeName() == SqlTypeName.NULL)
        {
            anyNullable = true;
            continue;
        }

        if (type.isNullable())
        {
            anyNullable = true;
        }

        if (SqlTypeUtil.canCastFrom(type, resultType, mappingRule))
        {
            resultType = type;
        }
        else
        {
            if (!SqlTypeUtil.canCastFrom(resultType, type, mappingRule))
            {
                return null;
            }
        }
    }
    if (anyNullable)
    {
        return createTypeWithNullability(resultType, true);
    }
    else
    {
        return resultType;
    }
}

/**
 * Override to limit scale to 6
 * if scale is 9 then exception is coming in regression
 * query-1628-count_validate_6.txt
 * scale is going out of range
 */
@Override
public RelDataType createSqlType(SqlTypeName typeName, int precision, int scale)
{
    if (typeName == SqlTypeName.OTHER && "EMBEDDING_VECTOR".equals(typeName.getName()))
    {
        return new EmbeddingVectorType();
    }

    if (!CalciteForkSettings.castDoubleToDecimalEnabled())
    {
        if (scale > CalciteForkSettings.defaultScaleForDecimal())
        {
            scale = CalciteForkSettings.defaultScaleForDecimal();
        }
    }
    else
    {
        if (scale > CalciteForkSettings.decimalRoundOffScale())
        {
            scale = CalciteForkSettings.decimalRoundOffScale();
        }
    }
    assertBasic(typeName);
    assert (precision >= 0) || (precision == RelDataType.PRECISION_NOT_SPECIFIED);
    final int maxPrecision = typeSystem.getMaxPrecision(typeName);
    if (maxPrecision >= 0 && precision > maxPrecision)
    {
        precision = maxPrecision;
    }
    RelDataType newType = new BasicSqlType(typeSystem, typeName, precision, scale);
    newType = SqlTypeUtil.addCharsetAndCollation(newType, this);
    return canonize(newType);
}

private static void assertBasic(SqlTypeName typeName)
{
    assert typeName != null;
    assert typeName != SqlTypeName.MULTISET : "use createMultisetType() instead";
    assert typeName != SqlTypeName.ARRAY : "use createArrayType() instead";
    assert typeName != SqlTypeName.MAP : "use createMapType() instead";
    assert typeName != SqlTypeName.ROW : "use createStructType() instead";
    assert !SqlTypeName.INTERVAL_TYPES.contains(typeName) : "use createSqlIntervalType() instead";
}

private static class EmbeddingVectorType extends RelDataTypeImpl
{
    EmbeddingVectorType()
    {
        super();
    }

    @Override
    public SqlTypeName getSqlTypeName()
    {
        return SqlTypeName.OTHER;
    }

    @Override
    public boolean isNullable()
    {
        return true;
    }

    @Override
    public int getPrecision()
    {
        return -1;
    }

    @Override
    public int getScale()
    {
        return -1;
    }

    @Override
    public String getFullTypeString()
    {
        return "EMBEDDING_VECTOR";
    }

    @Override
    protected void generateTypeString(StringBuilder sb, boolean withDetail)
    {
        sb.append(getFullTypeString());
        sb.append(" Embedding Vector");
    }
}

} /// ////// End of class
