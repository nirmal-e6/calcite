/*
 * Copyright (c) 2024 Uniphi Inc
 * All rights reserved.
 *
 * File Name: E6SqlCreateIndex.java
 *
 * Created On: 2024-12-17
 */

package org.apache.calcite.sql.ddl;

import org.apache.calcite.sql.*;
import org.apache.calcite.sql.parser.SqlParserPos;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

public class E6SqlCreateIndex extends SqlCreate
{

final public SqlIdentifier indexName;
final public SqlIdentifier tableName;
final public SqlNodeList columnList;
final public SqlIdentifier type;
@Nullable
public SqlNumericLiteral fpp;
final public boolean enforce;

private static final SqlOperator OPERATOR = new SqlSpecialOperator("CREATE INDEX", SqlKind.CREATE_INDEX);

public E6SqlCreateIndex(SqlParserPos pos, boolean replace, boolean ifNotExists, SqlIdentifier indexName,
    SqlIdentifier tableName, SqlNodeList columnList, SqlIdentifier type, @Nullable SqlNumericLiteral fpp,
    boolean enforce)
{
    super(OPERATOR, pos, replace, ifNotExists);
    this.indexName = Objects.requireNonNull(indexName, "indexName");
    this.tableName = Objects.requireNonNull(tableName, "tableName");
    this.columnList = Objects.requireNonNull(columnList, "columnList");
    this.type = Objects.requireNonNull(type, "type");
    this.fpp = fpp;
    this.enforce = enforce;
}

@Override
public List<SqlNode> getOperandList()
{
    return ImmutableList.of(indexName, tableName, columnList, type);
}

@Override
public void unparse(SqlWriter writer, int leftPrec, int rightPrec)
{
    if (getReplace())
    {
        writer.keyword("CREATE OR REPLACE");
    }
    else
    {
        writer.keyword("CREATE");
    }
    writer.keyword("INDEX");
    if (ifNotExists)
    {
        writer.keyword("IF NOT EXISTS");
    }
    tableName.unparse(writer, leftPrec, rightPrec);
    writer.keyword("ON");
    tableName.unparse(writer, leftPrec, rightPrec);
    writer.keyword("WITH");
    writer.keyword("(");
    writer.keyword("TYPE");
    writer.keyword("=");
    type.unparse(writer, leftPrec, rightPrec);
    writer.keyword(",");
    writer.keyword("COLUMNS");
    writer.keyword("=");
    SqlWriter.Frame frame = writer.startList("[", "]");
    for (SqlNode c : columnList)
    {
        writer.sep(",");
        c.unparse(writer, 0, 0);
    }
    writer.endList(frame);
    writer.keyword(",");
    writer.keyword("TYPE");
    writer.keyword("=");
    type.unparse(writer, leftPrec, rightPrec);
    writer.keyword(",");
    if (fpp != null)
    {
        writer.keyword("FPP");
        writer.keyword("=");
        fpp.unparse(writer, leftPrec, rightPrec);
        writer.keyword(",");
    }
    //    if(enforce){
    //        writer.keyword("ENFORCE"); writer.keyword("="); enforce.unparse(writer, leftPrec, rightPrec); writer.keyword(",");
    //    }
}

} /// ////// End of class
