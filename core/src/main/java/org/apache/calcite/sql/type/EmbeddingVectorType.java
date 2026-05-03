/*
 * Copyright (c) 2024 Uniphi Inc
 * All rights reserved.
 *
 * File Name: EmbeddingVectorType.java
 *
 * Created On: 2024-06-06
 */

package org.apache.calcite.sql.type;

import org.apache.calcite.rel.type.RelDataTypeImpl;

public class EmbeddingVectorType extends RelDataTypeImpl
{

public EmbeddingVectorType()
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

@Override protected void generateTypeString(StringBuilder sb, boolean withDetail)
{
    sb.append(getFullTypeString());
    sb.append(" Embedding Vector");
}
} ///////// End of class
