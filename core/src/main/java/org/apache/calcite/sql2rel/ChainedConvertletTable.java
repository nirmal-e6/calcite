/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.sql2rel;

import org.apache.calcite.sql.SqlCall;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ChainedConvertletTable implements SqlRexConvertletTable {

  private final List<SqlRexConvertletTable> tables;

  public ChainedConvertletTable(List<SqlRexConvertletTable> tables) {
    List<SqlRexConvertletTable> res = new ArrayList<>(tables.size());

    for (SqlRexConvertletTable table : tables) {
      addFlattened(res, table);
    }

    this.tables = res;
  }

  @Override public @Nullable SqlRexConvertlet get(SqlCall call) {
    for (SqlRexConvertletTable table : tables) {
      SqlRexConvertlet res = table.get(call);
      if (res != null) {
        return res;
      }
    }
    return null;
  }

  private void addFlattened(List<SqlRexConvertletTable> res, SqlRexConvertletTable table) {
    if (table instanceof ChainedConvertletTable) {
      ChainedConvertletTable chained = (ChainedConvertletTable) table;
      for (SqlRexConvertletTable nested : chained.tables) {
        addFlattened(res, nested);
      }
    } else {
      res.add(table);
    }
  }
} /// ////// End of class
