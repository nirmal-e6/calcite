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
package org.apache.calcite.config;

import org.apache.calcite.runtime.CalciteContextException;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.validate.SqlValidatorException;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.text.MessageFormat;

import static java.util.Objects.requireNonNull;
import static org.apache.calcite.util.Static.RESOURCE;

/**
 * Temporary fork-owned settings provider for primer-migrated behavior.
 */
public final class CalciteForkSettings {
  private static final Provider DEFAULT_PROVIDER = new Provider() {};

  private static volatile Provider provider = DEFAULT_PROVIDER;

  private static final String DB_DOES_NOT_EXIST =
      "Db: %s doesn't exist in catalog: %s";
  private static final String CATALOG_DOES_NOT_EXIST =
      "Catalog: %s doesn't exist";

  private CalciteForkSettings() {
  }

  public static void setProvider(Provider provider) {
    CalciteForkSettings.provider = requireNonNull(provider, "provider");
  }

  public static boolean enableOuterJoinOpt() {
    return provider.enableOuterJoinOpt();
  }

  public static boolean decimal128Enabled() {
    return provider.decimal128Enabled();
  }

  public static boolean nativeExecutor() {
    return provider.nativeExecutor();
  }

  public static boolean optimizeFilterWithOr() {
    return provider.optimizeFilterWithOr();
  }

  public static boolean allowDuplicateAliasInProjection() {
    return provider.allowDuplicateAliasInProjection();
  }

  public static boolean relBuilderFix() {
    return provider.relBuilderFix();
  }

  public static boolean databricks() {
    return provider.databricks();
  }

  public static boolean castDoubleToDecimalEnabled() {
    return provider.castDoubleToDecimalEnabled();
  }

  public static int defaultScaleForDecimal() {
    return provider.defaultScaleForDecimal();
  }

  public static int decimalRoundOffScale() {
    return provider.decimalRoundOffScale();
  }

  public static boolean immediateConsistencyEnabled() {
    return provider.immediateConsistencyEnabled();
  }

  public static int inSubquerySetThreshold() {
    return provider.inSubquerySetThreshold();
  }

  public static boolean enableDecorrelateSortOpt() {
    return provider.enableDecorrelateSortOpt();
  }

  public static boolean enableDecorrelateTrace() {
    return provider.enableDecorrelateTrace();
  }

  public static String defaultListaggSeparator() {
    return provider.defaultListaggSeparator();
  }

  public static boolean refreshTable(String catalogName, String schemaName,
      String tableName) {
    return provider.refreshTable(catalogName, schemaName, tableName);
  }

  public static @Nullable String defaultCatalog(Object validator) {
    if (validator instanceof ValidatorCatalogDefaults) {
      return ((ValidatorCatalogDefaults) validator).getDefaultCatalog();
    }
    return null;
  }

  public static @Nullable String defaultSchema(Object validator) {
    if (validator instanceof ValidatorCatalogDefaults) {
      return ((ValidatorCatalogDefaults) validator).getDefaultSchema();
    }
    return null;
  }

  public static RuntimeException invalidSchemaException(SqlNode node,
      String dbName, @Nullable String catalogName) {
    return provider.invalidSchemaException(node, dbName, catalogName);
  }

  public static RuntimeException invalidCatalogException(SqlNode node,
      String catalogName) {
    return provider.invalidCatalogException(node, catalogName);
  }

  public static CalciteContextException validationException(SqlNode node,
      String message) {
    return contextException(node, new Throwable(message));
  }

  public static CalciteContextException validationException(SqlNode node,
      String format, Object... args) {
    return validationException(node, MessageFormat.format(format, args));
  }

  private static CalciteContextException contextException(SqlNode node,
      Throwable cause) {
    return RESOURCE.validatorContext(node.getParserPosition().getLineNum(),
        node.getParserPosition().getColumnNum(),
        node.getParserPosition().getEndLineNum(),
        node.getParserPosition().getEndColumnNum()).ex(cause);
  }

  /** Exposes validator-local catalog defaults to Calcite-owned code. */
  public interface ValidatorCatalogDefaults {
    String getDefaultCatalog();

    String getDefaultSchema();
  }

  /** Provides temporary fork settings. */
  public interface Provider {
    default boolean enableOuterJoinOpt() {
      return false;
    }

    default boolean decimal128Enabled() {
      return false;
    }

    default boolean nativeExecutor() {
      return false;
    }

    default boolean optimizeFilterWithOr() {
      return false;
    }

    default boolean allowDuplicateAliasInProjection() {
      return false;
    }

    default boolean relBuilderFix() {
      return false;
    }

    default boolean databricks() {
      return false;
    }

    default boolean castDoubleToDecimalEnabled() {
      return false;
    }

    default int defaultScaleForDecimal() {
      return 6;
    }

    default int decimalRoundOffScale() {
      return 6;
    }

    default boolean immediateConsistencyEnabled() {
      return false;
    }

    default int inSubquerySetThreshold() {
      return 5;
    }

    default boolean enableDecorrelateSortOpt() {
      return false;
    }

    default boolean enableDecorrelateTrace() {
      return false;
    }

    default String defaultListaggSeparator() {
      return "-";
    }

    default boolean refreshTable(String catalogName, String schemaName,
        String tableName) {
      return false;
    }

    default RuntimeException invalidSchemaException(SqlNode node,
        String dbName, @Nullable String catalogName) {
      return contextException(node, new SqlValidatorException(
          String.format(DB_DOES_NOT_EXIST, dbName, catalogName), null));
    }

    default RuntimeException invalidCatalogException(SqlNode node,
        String catalogName) {
      return contextException(node, new SqlValidatorException(
          String.format(CATALOG_DOES_NOT_EXIST, catalogName), null));
    }

  }
}
