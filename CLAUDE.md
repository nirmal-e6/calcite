# Apache Calcite

Apache Calcite is a dynamic data management framework providing SQL parsing, validation,
query optimization (cost-based and rule-based), and relational algebra. It serves as a
shared SQL front-end and optimizer for dozens of data systems (JDBC adapters for
Cassandra, Druid, Elasticsearch, MongoDB, Kafka, etc.) without owning data storage itself.

The processing pipeline is: **Parser → Validator → SqlToRelConverter → Planner (rules) → Execution**.

Build system: **Gradle** (not Maven).

## Build Commands

```bash
# Full build with tests
./gradlew build

# Build without tests
./gradlew assemble

# Build core module only
./gradlew :core:build

# Run all core tests
./gradlew :core:test

# Run a single test class
./gradlew :core:test --tests "org.apache.calcite.test.SqlValidatorTest"

# Run a single test method
./gradlew :core:test --tests "org.apache.calcite.test.SqlValidatorTest.testSomeMethod"

# Run tests matching a pattern
./gradlew :core:test --tests "*RelOptRulesTest*"

# Run slow tests (tagged @Tag("slow"), 6GB heap)
./gradlew :core:slowTests

# Check code style
./gradlew checkstyleMain checkstyleTest
```

## Key Directories

| Directory | Contents |
|-----------|----------|
| `core/` | Parser, validator, SqlToRelConverter, planner rules, rel operators, rex expressions |
| `testkit/` | Shared test infrastructure: fixtures, DiffRepository, mock catalogs |
| `babel/` | Extended parser supporting multiple SQL dialects |
| `linq4j/` | LINQ-style lazy evaluation engine (core dependency) |
| `plus/` | Additional SQL functions and features |
| `buildSrc/` | Gradle plugins for FMPP template and JavaCC parser generation |
| `server/` | DDL parser extensions |

## Core Source Layout

```
core/src/main/java/org/apache/calcite/
├── sql/            SQL parser, operators, SqlNode, SqlValidator, SqlCall
├── sql2rel/        SqlToRelConverter (SQL AST → relational algebra)
├── rel/            RelNode operators (join, filter, project, aggregate, sort)
├── rex/            Row expressions (RexNode, RexCall, RexInputRef, etc.)
├── plan/           RelOptPlanner, RelOptRule, cost model, traits
├── adapter/        Enumerable, JDBC, Java adapters
├── schema/         Schema, Table, Function definitions
├── prepare/        Query preparation and cataloging
├── runtime/        Built-in SQL function implementations
└── util/           General utilities (Pair, ImmutableBitSet, etc.)
```

## Test Infrastructure

### Test types
- **JUnit 5** unit tests in `core/src/test/java/org/apache/calcite/test/`
- **DiffRepository** (XML reference files) for plan-comparison tests
- **Quidem** (.iq files) for SQL execution tests in `core/src/test/resources/sql/`

### Key test classes
- `SqlValidatorTest` — SQL validation (578KB, thousands of test methods)
- `RelOptRulesTest` — Planner rule transformations (472KB)
- `SqlToRelConverterTest` — SQL-to-relational conversion
- `RelBuilderTest` — Relational algebra builder API

### Fluent test API
```java
// Validator test
sql("SELECT deptno FROM emp").ok();
sql("SELECT ^bad^ FROM emp").fails("...");

// Planner rule test
sql(query).withRule(CoreRules.FILTER_MERGE).check();       // assert plan changes
sql(query).withRule(CoreRules.FILTER_MERGE).checkUnchanged(); // assert no change
```

### DiffRepository workflow
Reference XML files live in `core/src/test/resources/org/apache/calcite/test/`.
Each `<TestCase>` has `<Resource>` elements for sql, planBefore, planAfter.

When adding or updating a test:
1. Run the test — it writes actual output to `build/diffrepo/test/<Class>_actual.xml`
2. Copy the actual file over the reference file to update expected results

### Testkit module
`testkit/src/main/java/org/apache/calcite/test/` provides:
- `SqlValidatorFixture` — fluent validator test builder
- `RelOptFixture` — fluent planner rule test builder
- `DiffRepository` — XML diff-based reference comparison
- `MockCatalogReaderSimple` — test catalog with emp, dept, etc.

## Parser Generation

The SQL parser is generated from `core/src/main/codegen/templates/Parser.jj`
using JavaCC, with FMPP templates for dialect customization. Generated sources
go to `build/` and should not be edited directly.

## CI

GitHub Actions runs on JDK 8, 11, 17, 21 across Linux and Windows.
Tests run with `--no-parallel`. Guava compatibility tested from 21.0 to 33.x.
