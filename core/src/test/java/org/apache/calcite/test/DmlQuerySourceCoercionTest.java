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
package org.apache.calcite.test;

import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.logical.LogicalTableModify;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.type.SqlTypeName;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import static java.util.Objects.requireNonNull;

/** Tests DML source-query coercion bookkeeping after validator rewrites. */
class DmlQuerySourceCoercionTest extends SqlToRelTestBase {

  @Test void testInsertWithUnusedCteUsesCoercedSourceTypes() {
    final LogicalTableModify modify =
        (LogicalTableModify) sql("insert into empnullables (empno, ename, mgr, hiredate)\n"
            + "with c as (\n"
            + "  select deptno, coalesce(max(mgr), 0) as m\n"
            + "  from empnullables\n"
            + "  group by deptno)\n"
            + "select 1, '', '', ''").toRel();
    final RelDataType rowType = modify.getInput(0).getRowType();
    assertRowFieldType(rowType, "EMPNO", SqlTypeName.INTEGER, false);
    assertRowFieldType(rowType, "ENAME", SqlTypeName.VARCHAR, false);
    assertRowFieldType(rowType, "MGR", SqlTypeName.INTEGER, false);
    assertRowFieldType(rowType, "HIREDATE", SqlTypeName.TIMESTAMP, false);
  }

  @Test void testInsertValuesUsesQueryResultNullability() {
    final LogicalTableModify modify =
        (LogicalTableModify) sql("insert into empnullables (empno, mgr)\n"
            + "values (1, ''), (2, cast(null as varchar(1)))").toRel();
    final RelDataType rowType = modify.getInput(0).getRowType();
    assertRowFieldType(rowType, "EMPNO", SqlTypeName.INTEGER, false);
    assertRowFieldType(rowType, "MGR", SqlTypeName.INTEGER, true);
  }

  @Test void testUpdateUsesCoercedSourceTypes() {
    final LogicalTableModify modify =
        (LogicalTableModify) sql("update empnullables\n"
            + "set mgr = '', hiredate = ''\n"
            + "where empno = 1").toRel();
    final List<RexNode> sourceExpressions = modify.getSourceExpressionList();
    assertThat(sourceExpressions, notNullValue());
    assertThat(sourceExpressions, hasSize(2));
    assertThat(sourceExpressions.get(0).getType().getSqlTypeName(), is(SqlTypeName.INTEGER));
    assertThat(sourceExpressions.get(0).getType().isNullable(), is(false));
    assertThat(sourceExpressions.get(1).getType().getSqlTypeName(), is(SqlTypeName.TIMESTAMP));
    assertThat(sourceExpressions.get(1).getType().isNullable(), is(false));
  }

  @Test void testMergeInsertUsesCoercedSourceTypes() {
    final LogicalTableModify modify =
        (LogicalTableModify) sql("merge into empnullables e\n"
            + "using (select 1 as empno from (values (true))) t\n"
            + "on e.empno = t.empno\n"
            + "when not matched then insert (empno, ename, mgr, hiredate)\n"
            + "values(t.empno, '', '', '')").toRel();
    final String plan = RelOptUtil.toString(modify.getInput(0));
    assertThat(plan, containsString("CAST(''):INTEGER NOT NULL"));
    assertThat(plan, containsString("CAST(''):TIMESTAMP(0) NOT NULL"));
  }

  private static void assertRowFieldType(RelDataType rowType, String fieldName,
      SqlTypeName sqlTypeName, boolean nullable) {
    final RelDataType fieldType =
        requireNonNull(rowType.getField(fieldName, true, false), fieldName).getType();
    assertThat(fieldType.getSqlTypeName(), is(sqlTypeName));
    assertThat(fieldType.isNullable(), is(nullable));
  }
}
