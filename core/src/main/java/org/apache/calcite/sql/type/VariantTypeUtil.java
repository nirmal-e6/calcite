package org.apache.calcite.sql.type;

import org.apache.calcite.rel.type.RelDataType;

public class VariantTypeUtil
{

public static boolean checkVariantType(RelDataType variantType)
{
    // this flow is assuming there is no separate variant type.
    // the first operand must be a struct type with two binary fields: value and metadata
    if (!variantType.isStruct())
    {
        return false;
    }
    else if (variantType.getFieldList().size() != 2)
    {
        return false;
    }
    else
    {
        SqlTypeName firstField = (variantType.getFieldList().get(0)).getType().getSqlTypeName();
        if (firstField != SqlTypeName.BINARY)
        {
            return false;
        }

        SqlTypeName secondField = (variantType.getFieldList().get(1)).getType().getSqlTypeName();
        return secondField == SqlTypeName.BINARY;
    }
}

}
