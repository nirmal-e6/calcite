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
package org.apache.calcite.rex;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.sql.SqlCollation;
import org.apache.calcite.sql.SqlIntervalQualifier;
import org.apache.calcite.sql.type.IntervalSqlType;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.type.SqlTypeUtil;
import org.apache.calcite.util.NlsString;
import org.apache.calcite.util.TimeString;
import org.apache.calcite.util.TimeWithTimeZoneString;
import org.apache.calcite.util.TimestampString;
import org.apache.calcite.util.TimestampWithTimeZoneString;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

public class E6RexBuilder extends RexBuilder {

  /**
   * Creates a RexBuilder.
   *
   * @param typeFactory Type factory
   */
  public E6RexBuilder(RelDataTypeFactory typeFactory) {
    super(typeFactory);
  }

  // Override for not trimming decimal type
  /**
   * Internal method to create a call to a literal. Code outside this package should call one of the
   * type-specific methods such as {@link #makeDateLiteral(DateString)}, {@link
   * #makeLiteral(boolean)}, {@link #makeLiteral(String)}.
   *
   * @param o Value of literal, must be appropriate for the type
   * @param type Type of literal
   * @param typeName SQL type of literal
   * @return Literal
   */
  @Override protected RexLiteral makeLiteral(@Nullable Comparable o, RelDataType type,
      SqlTypeName typeName) {
    // All literals except NULL have NOT NULL types.
    type = typeFactory.createTypeWithNullability(type, o == null);
    int p;
    switch (typeName) {
    case CHAR:
        // Character literals must have a charset and collation. Populate
        // from the type if necessary.
        assert o instanceof NlsString;
      NlsString nlsString = (NlsString) o;
      if (nlsString.getCollation() == null
            || nlsString.getCharset() == null
            || !Objects.equals(nlsString.getCharset(), type.getCharset())
            || !Objects.equals(nlsString.getCollation(), type.getCollation())) {
          assert type.getSqlTypeName() == SqlTypeName.CHAR
              || type.getSqlTypeName() == SqlTypeName.VARCHAR;
        Charset charset = requireNonNull(type.getCharset(), "type.getCharset()");
        final SqlCollation collation = requireNonNull(type.getCollation(), "type.getCollation()");
        o = new NlsString(nlsString.getValue(), charset.name(), collation);
      }
      break;
    case TIME:
    case TIME_WITH_LOCAL_TIME_ZONE:
        assert o instanceof TimeString;
      p = type.getPrecision();
      if (p == RelDataType.PRECISION_NOT_SPECIFIED) {
        p = 0;
      }
      o = ((TimeString) o).round(p);
      break;
    case TIME_TZ:
        assert o instanceof TimeWithTimeZoneString;
      p = type.getPrecision();
      if (p == RelDataType.PRECISION_NOT_SPECIFIED) {
        p = 0;
      }
      o = ((TimeWithTimeZoneString) o).round(p);
      break;
    case TIMESTAMP:
    case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
        assert o instanceof TimestampString;
      p = type.getPrecision();
      if (p == RelDataType.PRECISION_NOT_SPECIFIED) {
        p = 0;
      }
      o = ((TimestampString) o).round(p);
      break;
    case TIMESTAMP_TZ:
        assert o instanceof TimestampWithTimeZoneString;
      p = type.getPrecision();
      if (p == RelDataType.PRECISION_NOT_SPECIFIED) {
        p = 0;
      }
      o = ((TimestampWithTimeZoneString) o).round(p);
      break;
    case DECIMAL:
      if (o == null) {
        break;
      }
        assert o instanceof BigDecimal;
      if (type instanceof IntervalSqlType) {
        SqlIntervalQualifier qualifier = ((IntervalSqlType) type).getIntervalQualifier();
        o = ((BigDecimal) o).multiply(qualifier.getUnit().multiplier);
        typeName = type.getSqlTypeName();
      } else if (type.getScale() != RelDataType.SCALE_NOT_SPECIFIED) {
          // don't trim datatype based on scale
          // getting RC issue after trimming
          // query-460-Comparision-Less than.txt in regression
        o = new BigDecimal(((BigDecimal) o).toPlainString());
          //                o = ((BigDecimal) o).setScale(type.getScale(),
          // typeFactory.getTypeSystem().roundingMode());
          //                if (type.getScale() < 0)
          //                {
          //                    o = new BigDecimal(((BigDecimal) o).toPlainString());
          //                }
      }
      break;
    default:
      break;
    }
    if (typeName == SqlTypeName.DECIMAL && !SqlTypeUtil.isValidDecimalValue((BigDecimal) o, type)) {
      throw new IllegalArgumentException(
          "Cannot convert " + o + " to " + type + " due to overflow");
    }
    return new RexLiteral(o, type, typeName);
  }
}
