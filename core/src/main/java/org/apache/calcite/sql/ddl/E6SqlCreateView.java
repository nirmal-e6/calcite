/*
 * Copyright (c) 2024 Uniphi Inc
 * All rights reserved.
 *
 * File Name: E6SqlCreateView.java
 *
 * Created On: 2024-12-02
 */

package org.apache.calcite.sql.ddl;

import org.apache.calcite.sql.*;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.util.ImmutableNullableList;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;
import java.util.Objects;

// custom Create View for "If NOT EXISTS" parameter

/**
 * Parse tree for {@code CREATE VIEW} statement.
 */
public class E6SqlCreateView extends SqlCreate
{

public final SqlIdentifier name;
public final @Nullable SqlNodeList columnList;
public final SqlNode query;

private static final SqlOperator OPERATOR = new SqlSpecialOperator("CREATE VIEW", SqlKind.CREATE_VIEW);

protected E6SqlCreateView(SqlParserPos pos, boolean replace, SqlIdentifier name, @Nullable SqlNodeList columnList,
    SqlNode query, boolean ifNotExists)
{
    super(OPERATOR, pos, replace, ifNotExists);
    this.name = Objects.requireNonNull(name, "name");
    this.columnList = columnList; // may be null
    this.query = Objects.requireNonNull(query, "query");
}

@SuppressWarnings("nullness")
@Override
public List<SqlNode> getOperandList()
{
    return ImmutableNullableList.of(name, columnList, query);
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
    writer.keyword("VIEW");
    name.unparse(writer, leftPrec, rightPrec);
    if (columnList != null)
    {
        SqlWriter.Frame frame = writer.startList("(", ")");
        for (SqlNode c : columnList)
        {
            writer.sep(",");
            c.unparse(writer, 0, 0);
        }
        writer.endList(frame);
    }
    writer.keyword("AS");
    writer.newlineAndIndent();
    query.unparse(writer, 0, 0);
}

} /// ////// End of class
