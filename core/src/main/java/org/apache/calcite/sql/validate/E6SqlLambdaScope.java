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

// custom SqlLambdaScope

package org.apache.calcite.sql.validate;

import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlLambda;
import org.apache.calcite.util.Litmus;

import static org.apache.calcite.util.Static.RESOURCE;

public class E6SqlLambdaScope extends SqlLambdaScope {
  private final SqlLambda sqlLambda;

  public E6SqlLambdaScope(SqlValidatorScope parent, SqlLambda lambdaExpr) {
    super(parent, lambdaExpr);
    sqlLambda = lambdaExpr;
  }

  @Override public SqlQualified fullyQualify(SqlIdentifier identifier) {
    SqlIdentifier name = new SqlIdentifier(identifier.names.get(0), identifier.getParserPosition());
    boolean found =
        sqlLambda.getParameters().stream().anyMatch(param -> param.equalsDeep(name, Litmus.IGNORE));
    if (found) {
      return SqlQualified.create(this, 1, null, identifier);
    } else {
      throw validator.newValidationError(
          identifier,
          RESOURCE.paramNotFoundInLambdaExpression(identifier.toString(), sqlLambda.toString()));
    }
  }
}
