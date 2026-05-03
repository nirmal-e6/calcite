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

import static java.util.Objects.requireNonNull;

/**
 * Temporary fork-owned settings provider for primer-migrated behavior.
 */
public final class CalciteForkSettings {
  private static final Provider DEFAULT_PROVIDER = new Provider() {};

  private static volatile Provider provider = DEFAULT_PROVIDER;

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

  public static boolean databricksLeastRestrictive() {
    return provider.databricksLeastRestrictive();
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

    default boolean databricksLeastRestrictive() {
      return false;
    }
  }
}
