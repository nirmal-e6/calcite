/*
 * Copyright (c) 2025 Uniphi Inc
 * All rights reserved.
 *
 * File Name: ChainedConvertletTable.java
 *
 * Created On: 2025-01-03
 */

package org.apache.calcite.sql2rel;

import org.apache.calcite.sql.SqlCall;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ChainedConvertletTable implements SqlRexConvertletTable
{

private final List<SqlRexConvertletTable> tables;

public ChainedConvertletTable(List<SqlRexConvertletTable> tables)
{
    List<SqlRexConvertletTable> res = new ArrayList<>(tables.size());

    for (SqlRexConvertletTable table : tables)
    {
        addFlattened(res, table);
    }

    this.tables = res;
}

@Override
public @Nullable SqlRexConvertlet get(SqlCall call)
{
    for (SqlRexConvertletTable table : tables)
    {
        SqlRexConvertlet res = table.get(call);
        if (res != null)
        {
            return res;
        }
    }
    return null;
}

private void addFlattened(List<SqlRexConvertletTable> res, SqlRexConvertletTable table)
{
    if (table instanceof ChainedConvertletTable)
    {
        ChainedConvertletTable chained = (ChainedConvertletTable) table;
        for (SqlRexConvertletTable nested : chained.tables)
        {
            addFlattened(res, nested);
        }
    }
    else
    {
        res.add(table);
    }
}

} /// ////// End of class
