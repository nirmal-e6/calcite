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
package org.apache.calcite.sql.type;

import org.apache.calcite.rel.type.RelDataTypeImpl;

public class EmbeddingVectorType extends RelDataTypeImpl {

  public EmbeddingVectorType() {
    super();
  }

  @Override public SqlTypeName getSqlTypeName() {
    return SqlTypeName.OTHER;
  }

  @Override public boolean isNullable() {
    return true;
  }

  @Override public int getPrecision() {
    return -1;
  }

  @Override public int getScale() {
    return -1;
  }

  @Override public String getFullTypeString() {
    return "EMBEDDING_VECTOR";
  }

  @Override protected void generateTypeString(StringBuilder sb, boolean withDetail) {
    sb.append(getFullTypeString());
    sb.append(" Embedding Vector");
  }
} ///////// End of class
