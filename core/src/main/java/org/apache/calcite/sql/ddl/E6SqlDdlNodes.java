/*
 * Copyright (c) 2024 Uniphi Inc
 * All rights reserved.
 *
 * File Name: E6SqlDdlNodes.java
 *
 * Created On: 2024-12-02
 */

package org.apache.calcite.sql.ddl;

import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.SqlNumericLiteral;
import org.apache.calcite.sql.parser.SqlParserPos;

import javax.annotation.Nullable;

public class E6SqlDdlNodes
{

/**
 * Creates a CREATE VIEW.
 */
public static E6SqlCreateView createView(SqlParserPos pos, boolean replace, SqlIdentifier name, SqlNodeList columnList,
    SqlNode query, boolean ifNotExists)
{
    return new E6SqlCreateView(pos, replace, name, columnList, query, ifNotExists);
}

/**
 * Creates a CREATE INDEX.
 */
public static E6SqlCreateIndex createIndex(SqlParserPos pos, boolean replace, boolean ifNotExists,
    SqlIdentifier indexName, SqlIdentifier tableName, SqlNodeList columnList, SqlIdentifier type,
    @Nullable SqlNumericLiteral fpp, boolean enforce)
{
    return new E6SqlCreateIndex(pos, replace, ifNotExists, indexName, tableName, columnList, type, fpp, enforce);
}

} /// ////// End of class
