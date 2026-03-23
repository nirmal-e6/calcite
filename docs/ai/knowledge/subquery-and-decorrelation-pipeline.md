# Subquery And Decorrelation Pipeline

## Purpose and scope

This doc maps how current Calcite handles subqueries and decorrelation from SQL
syntax through logical and physical planning. It is for users and maintainers
who need a reliable mental model for debugging, diagnosing, and composing
planner programs in this area.

If names such as `EXISTS`, `IN`, `LEFT_MARK`, `Correlate`, or
`LITERAL_AGG(true)` are still unfamiliar, start with `SQL + operator primer`
later in this doc, then come back to the stage-by-stage pipeline.

## Reading order

Use the doc in the order that matches your job:

- for the full planner story, read `One-screen mental model`, then `Stage 1`
  through `Stage 6`
- for custom optimizer design, continue from `Planning the pipeline in
  practice` after the stage-by-stage sections
- for executor-support auditing, jump to `SQL + operator primer`, then
  `DataFusion execution spec for residual Calcite surfaces`, then `Pipeline
  edge cases and current gaps`

## Why this matters

The same SQL subquery can be:

- legal or illegal at validation time
- represented as a `RexSubQuery`
- rewritten into `Join`, `Correlate`, or `ConditionalCorrelate`
- decorrelated by one of two algorithms
- left correlated and implemented physically as a nested loop

Wrong fixes often happen because the visible symptom appears late, but the
owning invariant lives earlier.

## One-screen mental model

1. The parser admits subquery syntax and produces a `SqlNode` tree.
2. The validator assigns SQL meaning, including scalar-subquery legality.
3. `SqlToRelConverter` lowers validated SQL into `RelNode`.
4. With the default `expand=false`, subqueries are usually represented as
   `RexSubQuery` expressions whose inner `rel` can already contain correlated
   variable references.
5. From there Calcite follows one of two families of flow:
   - converter-driven decorrelation: `SqlToRelConverter.decorrelate(...)` or
     `PlannerImpl.rel(...)`
   - program-driven planning: `Programs.subQuery(...)` rewrites `RexSubQuery`
     into join or correlate forms, then `Programs.decorrelate()` removes
     remaining correlation
6. If decorrelation is disabled or incomplete, convention rules can still
   implement `Correlate` or `ConditionalCorrelate` physically.

## Main entry points and files

- Parser grammar owner: `core/src/main/codegen/templates/Parser.jj`
- SQL reference for legal subquery positions:
  `site/_docs/reference.md`
- Validator scalar-subquery checks:
  `core/src/main/java/org/apache/calcite/sql/validate/SqlValidatorImpl.java`
- SQL-to-rel conversion and subquery discovery:
  `core/src/main/java/org/apache/calcite/sql2rel/SqlToRelConverter.java`
- `RexSubQuery` representation:
  `core/src/main/java/org/apache/calcite/rex/RexSubQuery.java`
- Rule-driven subquery removal:
  `core/src/main/java/org/apache/calcite/rel/rules/SubQueryRemoveRule.java`
- Built-in planner programs:
  `core/src/main/java/org/apache/calcite/tools/Programs.java`
- Legacy decorrelator:
  `core/src/main/java/org/apache/calcite/sql2rel/RelDecorrelator.java`
- Top-down general decorrelator:
  `core/src/main/java/org/apache/calcite/sql2rel/TopDownGeneralDecorrelator.java`

## Stage 1: Parser and validator boundaries

### Parser

The parser mostly owns syntax admission. At this stage the important question
is whether Calcite can build the right `SqlNode` shape, not whether the
subquery is legal in its final context.

For subqueries, the parser commonly produces:

- `SqlSelect` for nested queries
- `SqlBasicCall` for `IN`, `EXISTS`, `SOME`, `NOT IN`, and related wrappers
- constructor forms such as array or multiset query constructors

### Validator

The validator is the first stage that enforces SQL-visible subquery semantics.
Current reference docs say that `IN`, `EXISTS`, `UNIQUE`, and scalar subqueries
may appear anywhere an expression may appear, including `SELECT`, `WHERE`,
`JOIN ... ON`, and aggregate arguments.

The most important validator-side restriction is select-list scalar subqueries.
`SqlValidatorImpl.handleScalarSubQuery(...)` checks that:

- the select-list subquery projects exactly one column
- the exposed type is the inner single column, made nullable
- normal aliasing and row-type bookkeeping still apply

If a shape fails here, do not debug decorrelation. The problem is still SQL
meaning, not relational rewrite.

## Stage 2: `SqlToRelConverter`

`SqlToRelConverter` is where SQL subqueries stop being only SQL syntax and
become either `RexSubQuery` expressions or eagerly expanded joins.

### The default path is `expand=false`

The default `SqlToRelConverter.Config` sets:

- `isExpand() = false`
- `isDecorrelationEnabled() = true`
- `isTrimUnusedFields() = false`
- `isTopDownGeneralDecorrelationEnabled() = false`

The `expand=true` path still exists, but the config docs explicitly say it is
deprecated and not the recommended direction for new work.

### How subqueries are found

`replaceSubQueries(...)` calls `findSubQueries(...)` first, then
`substituteSubQuery(...)` for each registered node.

Important details:

- `findSubQueries(...)` propagates `RelOptUtil.Logic` through the SQL tree so
  Calcite knows whether a subquery must preserve full three-valued logic or can
  approximate `UNKNOWN`.
- `WHERE` and similar filter positions usually start with
  `UNKNOWN_AS_FALSE`.
- General expressions often use `TRUE_FALSE_UNKNOWN`.
- `pushDownNotForIn(...)` runs before conversion of `WHERE`, so `NOT` is pushed
  into `IN` or `NOT IN` early. This matters because `NOT IN` and `IN` have
  different null semantics and therefore different rewrites.

### What `RexSubQuery` stores

With `expand=false`, Calcite keeps subqueries as `RexSubQuery`.

`RexSubQuery` stores:

- the operator kind, such as `IN`, `EXISTS`, `SCALAR_QUERY`, `SOME`
- outer operands, such as the left keys of `x IN (subquery)`
- the inner relational tree in `rel`

Type behavior is already encoded here:

- `IN` and `SOME` derive boolean nullability from both outer operands and inner
  row types
- `EXISTS` is non-nullable boolean
- scalar subqueries expose the inner single column type, made nullable

This is an important debugging point: once you see `RexSubQuery`, the SQL has
already crossed into relational representation even if no rule has fired yet.

### Special cases in conversion

#### `IN` list threshold

`getInSubQueryThreshold()` controls whether a literal `IN` list becomes:

- an `OR` tree via `convertInToOr(...)`
- or a join against an inline table or subquery shape

Small lists tend to stay as predicates. Larger lists, or lists involving
identifiers, shift toward relational rewriting.

#### Non-correlated subqueries can be folded externally

`SubQueryConverter` is a hook for callers that want to evaluate
non-correlated subqueries into constants. `convertNonCorrelatedSubQuery(...)`
only uses it when:

- the converter says it can convert subqueries
- the subquery is non-correlated

Converted results are cached in `mapConvertedNonCorrSubqs`.

This hook is not the main correlated-subquery pipeline. It is a side path for
constant folding of non-correlated cases.

#### `expand=true` still does eager rewrite

When `expand=true`, `substituteSubQuery(...)` takes a very different path:

- `EXISTS`, `IN`, `SOME`, and scalar queries can be rewritten during
  conversion, often via `convertExists(...)`
- `bb.register(...)` attaches the rewritten relational tree to the outer query
- uncorrelated cases may collapse to constants immediately

This path is still tested, but current config docs say not to treat it as the
preferred embedding strategy.

### Two current converter-era flows

Calcite currently uses both of these flows:

- direct decorrelation from a tree that still contains `RexSubQuery`
- explicit subquery-removal rules followed by decorrelation

Do not assume that all callers go through `Programs.subQuery()` first. Many
converter tests, and `PlannerImpl.rel(...)`, exercise direct decorrelation on
`expand=false` plans.

## Stage 3: Rule-driven subquery removal

If you choose the program-driven path, the main owner is `Programs.subQuery()`.

### What `Programs.subQuery()` does

`Programs.subQuery(...)` builds one of two HEP stages:

- legacy stage:
  `FILTER_SUB_QUERY_TO_CORRELATE`,
  `PROJECT_SUB_QUERY_TO_CORRELATE`,
  `JOIN_SUB_QUERY_TO_CORRELATE`,
  `PROJECT_OVER_SUM_TO_SUM0_RULE`
- top-down-aware stage:
  `FILTER_SUB_QUERY_TO_MARK_CORRELATE`,
  `PROJECT_SUB_QUERY_TO_MARK_CORRELATE`,
  `JOIN_SUB_QUERY_TO_CORRELATE`,
  `PROJECT_OVER_SUM_TO_SUM0_RULE`

The choice comes from planner context via
`CalciteConnectionConfig.topDownGeneralDecorrelationEnabled()`.

### What `SubQueryRemoveRule` rewrites

`SubQueryRemoveRule` handles:

- scalar subqueries
- collection subqueries
- `SOME`
- `IN`
- `EXISTS`
- `UNIQUE`

The important distinction is whether the inner relational tree uses correlated
variables:

- uncorrelated cases generally rewrite to joins directly
- correlated cases rewrite to `Correlate` or mark-correlate forms, which are
  then the decorrelator's job

### Legacy correlate rewrites versus mark-correlate rewrites

The legacy rules target plain correlate or join forms.

The mark-correlate rules target `LEFT_MARK` semantics, which are especially
useful for `IN`, `SOME`, and `EXISTS` when null-sensitive marker behavior must
be preserved. `rewriteToMarkJoin(...)` uses `JoinRelType.LEFT_MARK`.

When the rewrite is correlated, `RelBuilder.join(...)` does not create a plain
`LogicalJoin`. It creates a `LogicalConditionalCorrelate` so the left-mark
condition stays attached. `ConditionalCorrelate` exists specifically because
for `SOME` and `IN`, the condition cannot always be pulled away safely.

### Join-subquery caveats

`JOIN_SUB_QUERY_TO_CORRELATE` is powerful, but it is also a recurring source
of edge cases in `JOIN ... ON` handling. Current rule tests cover cases such
as:

- references to right-side columns in `ON`
- references to both sides of the join
- left-join `EXISTS` and `NOT EXISTS` cases

Treat join-condition subquery rewrite as more fragile than filter or project
rewrite. This is especially true when you are composing a custom rule program.

## Stage 4: Legacy `RelDecorrelator`

`RelDecorrelator` is the default decorrelation engine unless the top-down flag
is enabled.

### Entry point

The main entry point is:

- `RelDecorrelator.decorrelateQuery(rootRel, relBuilder)`

There are overloads that accept:

- `decorrelationRules` for the rule-based pre-pass
- `preDecorrelateRules` for the internal cleanup phase before the recursive
  decorrelation walk

### Phase 0: build `CorelMap`

Before any rewrite, `CorelMapBuilder` walks the tree and records:

- `mapCorToCorRel`: which `CorrelationId` is provided by which correlate
- `mapRefRelToCorRef`: which relational expressions refer to which correlated
  variables
- `mapFieldAccessToCorRef`: how correlated field accesses map to correlation
  definitions

Crucially, `CorelMapBuilder` descends into `RexSubQuery.rel`. That is why the
legacy decorrelator can still discover correlation when the outer tree still
contains `RexSubQuery`.

### Phase 1: remove obvious correlation by rule

`removeCorrelationViaRule(...)` is the first pre-pass.

The default rule set is:

- `RemoveSingleAggregateRule`
- `RemoveCorrelationForScalarProjectRule`
- `RemoveCorrelationForScalarAggregateRule`

This is a simplification stage, not the whole algorithm. `RelDecorrelatorTest`
shows that running with no rules can still decorrelate many queries, but the
result is usually less reduced and may keep extra aggregate structure.

### Phase 2: internal pre-decorrelation cleanup

If correlation remains, the decorrelator runs an internal HEP cleanup phase.

The default pre-decorrelation rules include:

- `AdjustProjectForCountAggregateRule` variants
- `FilterIntoJoin`
- a custom `FilterProjectTranspose` that refuses to push a correlated filter
- `FilterCorrelateRule`
- `FilterFlattenCorrelatedConditionRule`

This stage is easy to underestimate. A lot of successful decorrelation depends
on normalizing the tree into shapes the main algorithm can reason about.

### Phase 3: extract correlated computations

After the HEP cleanup, `CorrelateProjectExtractor` pulls correlated
computations out of RHS projects above correlates. Then `CorelMap` is rebuilt,
because the tree shape has changed.

### Phase 4: recursive decorrelation using `Frame`

The core algorithm dispatches by operator type through `decorrelateRel(...)`.

The central data structure is `Frame`, which records:

- the original relational expression
- its decorrelated replacement
- where original outputs now live
- where correlation definitions are now produced in the output

The algorithm also keeps `frameStack`, which tracks visible outer correlation
definitions while walking nested scopes.

This is the right mental model for reading the code: the decorrelator is not
only replacing field accesses; it is rebuilding a tree while preserving both
row outputs and the transport of correlated values.

### Phase 5: expression rewriting and value transport

`DecorrelateRexShuttle` rewrites:

- `RexFieldAccess` on correlated variables into input refs that point at newly
  produced columns
- input refs into their post-rewrite positions

When a subtree needs correlated values that are not already present in its
current output, the algorithm may attach a value generator. This is how
correlation information is carried through operators that otherwise would not
produce it.

### Operators that get special handling

The legacy decorrelator has specialized logic for several operator classes,
including:

- `Sort`, with dedicated paths such as `decorrelateSortWithRowNumber(...)` and
  `decorrelateSortAsAggregate(...)`
- `Aggregate`
- `SetOp`
- `Project`
- `Filter`
- `Correlate`
- `Join`

If you are debugging a wrong result or an assertion, look for the
operator-specific `decorrelateRel(...)` overload first rather than reading the
whole file top to bottom.

### Hard invariants

Two invariants matter most:

- the final row type must match the original row type ignoring field names
- correlated field mappings must stay consistent after every rewrite

The first invariant is checked explicitly at the end of `decorrelateQuery(...)`
and is a common source of assertion failures when field mappings drift.

### Common failure classes

Current tests and issue coverage show repeated stress areas:

- multiple `CorrelationId`s in one correlated filter
- correlated subqueries over set operators
- `RexFieldAccess` in join conditions
- `LIMIT 1` and `OFFSET` interactions
- sort decorrelation with row-number or aggregate rewriting
- row-type or field-offset drift

These are the places where you should read tests before changing code.

## Stage 5: `TopDownGeneralDecorrelator`

`TopDownGeneralDecorrelator` is the newer, experimental algorithm.

### Current status

Its class comment still says it is not yet integrated into other modules and
must be called separately. Current code is more nuanced than that:

- `Programs.DecorrelateProgram` calls it when
  `topDownGeneralDecorrelationEnabled` is true
- `PlannerImpl.rel(...)` calls it when the same flag is true
- `CalcitePrepareImpl` also routes to it behind that flag

Inference from current code: treat it as wired but still opt-in and
experimental, not as the repo-wide default path.

### Intended usage

The class usage note recommends:

1. generate the initial plan with `SqlToRelConverter` without removing
   subqueries eagerly
2. apply subquery-removal rules, preferably mark-correlate rules for filter and
   project
3. call `TopDownGeneralDecorrelator.decorrelateQuery(...)`
4. continue with later optimizations

For join subqueries, the note is more cautious: there is not yet a specially
tailored join rewrite comparable to the filter and project mark rules.

### Internal structure

The algorithm runs a pre HEP phase before decorrelation:

- custom `FilterProjectTranspose` that avoids pushing filters across `V2M`
  projects
- `FILTER_INTO_JOIN`
- `FILTER_CORRELATE`

It uses a `HepPlanner` with `noDag=true` to avoid shared-subtree aliasing when
nested correlations would otherwise corrupt memoized per-node decorrelation
state.

Core pieces to know:

- `detectCorrelatedExpressions(...)` marks which subtrees still contain
  correlation
- `correlateElimination(...)` and `unnestInternal(...)` eliminate correlates
- `UnnestedQuery` extends the legacy `Frame` concept for top-down unnesting
- `CorrelatedExprRewriter` rewrites correlated expressions against the current
  unnested context

After decorrelation it runs a post HEP phase:

- custom `FilterProjectTranspose`
- `FILTER_INTO_JOIN`
- `MARK_TO_SEMI_OR_ANTI_JOIN_RULE`
- `PROJECT_MERGE`
- `PROJECT_REMOVE`

### Strengths and caveats

Strengths:

- better fit for left-mark semantics
- explicit support for newer mark-correlate rewrites
- dedicated test coverage for filter `EXISTS`, `SOME`, `NOT IN`, set-op
  subqueries, and nested cases

Caveats:

- still experimental
- join-subquery handling is less specialized than filter and project handling
- on unsupported operators the implementation catches
  `UnsupportedOperationException` and may leave the original plan in place

## Stage 6: What happens after decorrelation

### The built-in standard program

`Programs.standard()` currently runs:

1. `Programs.subQuery(...)`
2. `DecorrelateProgram`
3. `measure(...)`
4. `TrimFieldsProgram`
5. main planner optimization
6. `calc(...)`

This ordering is deliberate. The `DecorrelateProgram` comment explicitly says
field trimming must happen after decorrelation, because trimming earlier can
confuse field offsets.

### Physical fallback when correlation remains

If decorrelation is disabled or incomplete, Calcite can still implement
correlated plans physically.

Important physical surfaces:

- `Correlate` is a logical nested-loop operator, not a normal join
- `EnumerableCorrelateRule` converts `LogicalCorrelate` to
  `EnumerableCorrelate`
- `EnumerableConditionalCorrelateRule` converts `LogicalConditionalCorrelate`
  to `EnumerableConditionalCorrelate`

For left-mark semantics:

- uncorrelated cases may become `LEFT_MARK` joins directly
- correlated cases can become `ConditionalCorrelate`
- enumerable conditional correlate currently only supports `LEFT_MARK`

This is not only a fallback story. If you intentionally disable decorrelation,
you must confirm your target convention actually has physical support for the
remaining correlated operators.

## Decorrelation and costing

Current Calcite does not treat decorrelation as a cost-based choice between a
correlated plan and a decorrelated plan.

What current code does instead:

- `Programs.standard()` runs `subQuery(...)` and `DecorrelateProgram` before
  the main planner optimization pass
- `DecorrelateProgram` checks only `forceDecorrelate`; if it is `true`, it
  runs `RelDecorrelator` or `TopDownGeneralDecorrelator`, otherwise it leaves
  the tree unchanged
- `Prepare` uses the same `forceDecorrelate` gate
- `SqlToRelConverter` also treats decorrelation as a capability/config choice,
  not as a cost-based search

There are two important nuances:

- a surviving `Correlate` is still a normal relational node with row-count and
  self-cost logic, so Calcite can cost it if you keep it in the plan
- `TopDownGeneralDecorrelator` contains a few local heuristics that mention
  cost, but those are algorithm-internal assumptions, not a planner-wide
  comparison of correlated versus decorrelated alternatives

What this means in practice:

- yes, decorrelation can absolutely be more expensive at runtime on some
  backends
- no, Calcite's built-in pipeline does not currently keep both alternatives
  alive and let Volcano choose between them
- if you want true cost-based choice, you need custom work to preserve the
  correlated form, generate the decorrelated form, and compare both using a
  cost model that reflects your executor

Practical recommendation for a DataFusion-based backend:

- until your executor has strong explicit `Correlate` / apply support, treat
  decorrelation as a normalization boundary rather than a cost optimization
- revisit cost-based preservation only after correlated fallback is both
  executable and credibly costed in your own stack

## Planning the pipeline in practice

This section is the operational guide for deciding where subquery removal and
decorrelation should sit in a real optimizer, and which Calcite entrypoint is
actually controlling that behavior.

### `Prepare` and JDBC

`Prepare` is more than just `Programs.standard()`.

Its current flow is:

1. `convertQuery(...)`
2. `flattenTypes(...)`
3. if `forceDecorrelate` is true, run direct decorrelation immediately
4. optimize using the default program, which is `Programs.standard()`

The property default is `forceDecorrelate=true`.

For most server-style or JDBC-style usage, this is the closest thing to the
default Calcite subquery pipeline, but it is worth remembering that the direct
decorrelation step can happen before the standard program stack.

### `Frameworks` and `PlannerImpl`

`Frameworks.ConfigBuilder` starts with no programs. That means planner programs
are not implicitly populated the way `Prepare` does.

Also, `PlannerImpl.rel(...)` is a special convenience path:

- it validates SQL
- calls `convertQuery(...)`
- flattens types
- immediately runs decorrelation directly

It does not run `Programs.standard()`.

This distinction matters a lot:

- `Planner.rel()` is not the same thing as `Prepare.optimize(...)`
- `Planner.transform(...)` only runs the programs you explicitly provided
- if you need exact control over late subquery removal and decorrelation
  ordering, `Planner.rel()` alone is the wrong abstraction

From there, the practical question is how to place the subquery and
decorrelation stages in your own pipeline.

### If you want the default stable Calcite pipeline

- Leave `SqlToRelConverter.Config.withExpand(false)` at its default.
- Do not trim fields before decorrelation.
- Keep `forceDecorrelate=true` unless you intentionally want correlated
  physical operators.
- Prefer `Programs.standard()` or the `Prepare` path when you want the normal
  subquery-removal plus decorrelation sequence.

This is the safest default for embedders that want Calcite's maintained
behavior rather than a custom experimental plan.

### If you are using `SqlToRelConverter` directly

Use the converter pipeline explicitly:

1. `convertQuery(...)`
2. `flattenTypes(...)` if your caller normally does so
3. `decorrelate(query, rootRel)` if you want converter-driven decorrelation
4. trim later, not earlier

This path is valid even with `expand=false`. Current converter tests exercise
`withDecorrelate(true).withExpand(false)` directly.

Use this path when:

- you are reproducing or debugging `sql2rel`
- you want to inspect `RexSubQuery` before planner programs
- you do not need the full `Programs.standard()` stage sequence

### If you are composing custom `Program`s

For a custom multi-stage optimizer, treat subquery removal plus decorrelation
as an early dedicated normalization stage. It does not have to be literally
the first transformation in the whole pipeline, but it should happen before
broad join reordering, multi-join formation, or aggressive filter/project
movement.

The important distinction is:

- small syntax-normalizing cleanup before subquery removal is usually useful
- broad structural optimization before subquery removal is usually not

Useful and generally safe pre-rules:

- `PROJECT_TO_LOGICAL_PROJECT_AND_WINDOW` if window syntax may still need to
  be expanded before later rules
- `PROJECT_REDUCE_EXPRESSIONS`
- `FILTER_REDUCE_EXPRESSIONS`
- `PROJECT_MERGE`
- `PROJECT_REMOVE`

Why these are the usual safe pre-rules:

- they simplify local expression or project structure
- they do not depend on decorrelated joins already existing
- current tests explicitly use `PROJECT_TO_LOGICAL_PROJECT_AND_WINDOW` before
  subquery/decorrelation scenarios that need it
- `PROJECT_MERGE` is already conservative around correlation; for example,
  current tests keep it unchanged rather than incorrectly merging through
  correlation variables

Rules to avoid before the subquery stage unless you have a very specific
reason:

- `FILTER_INTO_JOIN`
- `JOIN_CONDITION_PUSH`
- broad `FILTER_PROJECT_TRANSPOSE` use
- join commute / associate / hypergraph / multi-join formation rules
- executor- or physical-oriented rewrites

Why to avoid them that early:

- `Programs.subQuery(...)` itself intentionally does not include them
- moving predicates around can relocate a subquery from `Filter` or `Project`
  into `Join`, which pushes ownership onto `JOIN_SUB_QUERY_TO_CORRELATE`
- join-condition subqueries are currently the most fragile area; for example,
  some shapes that reference both join sides remain unchanged
- both decorrelators already do their own targeted internal cleanup, so
  duplicating broad movement beforehand rarely buys much

The stable legacy sequence is:

```java
Programs.sequence(
    Programs.ofRules(
        CoreRules.PROJECT_TO_LOGICAL_PROJECT_AND_WINDOW,
        CoreRules.PROJECT_REDUCE_EXPRESSIONS,
        CoreRules.FILTER_REDUCE_EXPRESSIONS,
        CoreRules.PROJECT_MERGE,
        CoreRules.PROJECT_REMOVE),
    Programs.subQuery(DefaultRelMetadataProvider.INSTANCE),
    Programs.decorrelate(),
    Programs.trim(),
    Programs.ofRules(myRules))
```

Notes:

- `Programs.standard()` already adds more around this sequence, notably
  `measure(...)` and `calc(...)`.
- `Programs.subQuery(...)` and `Programs.decorrelate()` choose legacy versus
  top-down behavior from planner context. If you do not supply a
  `CalciteConnectionConfig`, they fall back to defaults.
- Do not put trim before decorrelation.
- Keep the subquery-removal stage separate from broad rule soup. It is easier
  to debug and closer to how Calcite itself structures the pipeline.
- `Programs.decorrelate()` already runs targeted internal cleanup. The legacy
  path applies rule-based correlate removal and post-decorrelation
  `FILTER_INTO_JOIN` / `JOIN_CONDITION_PUSH`; the top-down path applies its
  own custom pre/post cleanup internally. Do not reflexively duplicate those
  rules in the immediately surrounding program unless you are intentionally
  running another cleanup pass later.

### `RuleCollection` versus individual rule instances

Use `addRuleCollection(...)` when:

- the rules form one conceptual phase
- internal order should not matter
- you want a compact stage, like Calcite's built-in subquery HEP program

Use `addRuleInstance(...)` when:

- one rule must clearly run before another
- you want cleanup between specific rewrites
- you are debugging rule interaction and need deterministic sequencing

Use `addSubprogram(...)` when:

- a sequence should run to fixpoint as a unit
- repeating the whole stage matters more than the fixpoint of each individual
  instruction

The key trap is that `addRuleCollection(...)` does not give ordered semantics
inside the collection. If order matters, do not pretend a collection is a
sequence.

### If you want a custom multi-stage logical optimizer

For most embedders, the best custom shape is:

1. small pre-normalization
2. dedicated subquery-removal stage
3. immediate decorrelation
4. trim or equivalent width cleanup after the decorrelation boundary
5. only then broad logical optimization

Stable legacy-oriented recipe:

```java
Programs.sequence(
    Programs.ofRules(
        CoreRules.PROJECT_TO_LOGICAL_PROJECT_AND_WINDOW,
        CoreRules.PROJECT_REDUCE_EXPRESSIONS,
        CoreRules.FILTER_REDUCE_EXPRESSIONS,
        CoreRules.PROJECT_MERGE,
        CoreRules.PROJECT_REMOVE),
    Programs.subQuery(DefaultRelMetadataProvider.INSTANCE),
    Programs.decorrelate(),
    Programs.trim(),
    Programs.ofRules(myLogicalRules),
    Programs.calc(DefaultRelMetadataProvider.INSTANCE))
```

When to prefer this recipe:

- you want the most stable current Calcite path
- your executor can handle ordinary joins plus `Correlate` fallback more
  easily than residual `LEFT_MARK`
- you want planner behavior that stays close to `Programs.standard()`

Top-down-oriented recipe:

```java
Programs.sequence(
    Programs.ofRules(
        CoreRules.PROJECT_TO_LOGICAL_PROJECT_AND_WINDOW,
        CoreRules.PROJECT_REDUCE_EXPRESSIONS,
        CoreRules.FILTER_REDUCE_EXPRESSIONS,
        CoreRules.PROJECT_MERGE,
        CoreRules.PROJECT_REMOVE),
    Programs.subQuery(DefaultRelMetadataProvider.INSTANCE),
    Programs.decorrelate(),
    Programs.trim(),
    Programs.ofRules(myLogicalRules),
    Programs.calc(DefaultRelMetadataProvider.INSTANCE))
```

The program shape is the same; the behavioral switch is planner context:

- set `topDownGeneralDecorrelationEnabled=true`
- keep `forceDecorrelate=true`

What changes under the hood:

- `Programs.subQuery(...)` switches `Filter` and `Project` removal from
  correlate rules to mark-correlate rules
- `Programs.decorrelate()` switches from `RelDecorrelator` to
  `TopDownGeneralDecorrelator`

Additional top-down cautions:

- this path is better aligned with null-sensitive `IN` / `SOME` / `EXISTS`
  handling
- `Join` subqueries are still more conservative than `Filter` / `Project`
  cases
- if your executor does not support residual `LEFT_MARK`, either keep the
  stable legacy path or be prepared to lower remaining mark semantics yourself

If your executor limitations are the primary constraint, choose the stage by
the most advanced surface you are willing to execute:

- willing to execute only ordinary joins and aggregates: run subquery removal
  and decorrelation as early as possible
- willing to execute `Correlate` / `ConditionalCorrelate` fallback: keep the
  same early stage, but you can tolerate decorrelation gaps
- willing to execute raw `RexSubQuery`: almost no backend should choose this;
  add a bridge layer instead

The standard-like default is to trim immediately after subquery removal and
decorrelation, then run broader optimization. Move trim later only if one of
your own later logical stages explicitly needs the untrimmed shape.

### If you are evaluating top-down general decorrelation

Use it intentionally, not accidentally.

Recommended shape:

- set planner context `topDownGeneralDecorrelationEnabled=true`
- keep `expand=false`
- prefer `FILTER_SUB_QUERY_TO_MARK_CORRELATE` and
  `PROJECT_SUB_QUERY_TO_MARK_CORRELATE`
- be more cautious with `JOIN_SUB_QUERY_TO_CORRELATE`
- run decorrelation before broader cleanup that would erase evidence you need
  to debug

This path is a good fit when you specifically want better left-mark and null
semantics for correlated `IN`, `SOME`, and `EXISTS`, and you are willing to
debug an experimental algorithm.

### If you want to track and eventually migrate to `TopDownGeneralDecorrelator`

The safest rollout shape is dual-path, not big-bang.

Recommended migration plan:

1. Keep the legacy path as the production default.
2. Add a flag-controlled top-down path that changes only
   `topDownGeneralDecorrelationEnabled`, not the rest of your surrounding
   program stack.
3. Run the same representative query corpus through both paths and capture the
   plan at three checkpoints:
   - after `Programs.subQuery(...)`
   - after decorrelation
   - at the executor translation boundary
4. Classify differences into:
   - same final shape
   - better decorrelation from top-down
   - residual `LEFT_MARK` / `ConditionalCorrelate` that your executor can run
   - unsupported or unchanged correlation that still needs legacy rescue

Signals that it is becoming production-ready for your environment:

- no top-down-only failures on your real query corpus
- no unexpected unchanged correlated plans caused by internal
  `UnsupportedOperationException` fallback
- stable handling of the stress areas already called out in `new-decorr.iq`,
  especially join-condition correlation, `LIMIT 1` / sort shapes, nested
  correlation, multi-level correlation, and left-mark cleanup
- parity or better results for null-sensitive `IN`, `NOT IN`, `SOME`, and
  `EXISTS`
- your executor either never sees residual `LEFT_MARK` /
  `ConditionalCorrelate`, or supports them intentionally
- no need for manual query-shape allowlists to keep the top-down path working

Fallback strategy while migrating:

- keep a kill switch that returns to the legacy path immediately
- if top-down leaves unsupported residual correlation at the executor
  boundary, rerun the query through the legacy path
- for join-subquery-heavy workloads, the class usage note already suggests a
  hybrid rescue path: run `TopDownGeneralDecorrelator` first, then use
  `JOIN_SUB_QUERY_TO_CORRELATE` together with `RelDecorrelator` for greater
  stability

Once it is ready enough to become the default:

- set `topDownGeneralDecorrelationEnabled=true`
- keep `forceDecorrelate=true`
- keep mark-correlate removal for filter and project subqueries
- retain the legacy fallback path for a while as an operational escape hatch,
  even after changing the default

The next sections are reference material: use them to decode SQL constructs,
plan operators, and residual executor surfaces after you understand where the
subquery stage sits in your pipeline.

## SQL + operator primer

This section is the cheat sheet. It is intentionally compact and focuses on
the SQL constructs and rel surfaces that show up most often in subquery
removal, decorrelation, and executor compatibility checks.

### Shared sample data

Assume the following CTEs for all examples in this section:

```sql
WITH
outer_t(id, k, grp) AS (
  VALUES
    (1, 10, 'A'),
    (2, 20, 'A'),
    (3, CAST(NULL AS INTEGER), 'A'),
    (4, 20, 'B'),
    (5, 40, 'B'),
    (6, 10, 'C')
),
inner_t(k, grp, v) AS (
  VALUES
    (10, 'A', 'x'),
    (20, 'A', 'y'),
    (20, 'A', 'z'),
    (CAST(NULL AS INTEGER), 'A', 'n'),
    (20, 'B', 'p'),
    (CAST(NULL AS INTEGER), 'B', 'q')
),
inner_empty(k, grp, v) AS (
  SELECT *
  FROM inner_t
  WHERE 1 = 0
)
```

`outer_t`

| id | k    | grp |
| --- | ---- | --- |
| 1 | 10 | A |
| 2 | 20 | A |
| 3 | NULL | A |
| 4 | 20 | B |
| 5 | 40 | B |
| 6 | 10 | C |

`inner_t`

| k    | grp | v |
| --- | --- | --- |
| 10 | A | x |
| 20 | A | y |
| 20 | A | z |
| NULL | A | n |
| 20 | B | p |
| NULL | B | q |

Useful edge cases already embedded here:

- row `id=3` gives an outer `NULL`
- groups `A` and `B` contain inner `NULL`
- group `A` contains duplicates
- group `C` makes the correlated RHS empty
- `inner_empty` is the explicit empty uncorrelated RHS

### SQL constructs cheat sheet

Outputs below are written as `id -> result`.

#### Scalar subquery

```sql
SELECT id,
    (SELECT MAX(i.k)
     FROM inner_t i
     WHERE i.grp = o.grp) AS max_k
FROM outer_t o;
```

Output by `id`: `1 -> 20`, `2 -> 20`, `3 -> 20`, `4 -> 20`, `5 -> 20`,
`6 -> NULL`

Meaning: one value per outer row. Empty RHS becomes `NULL`.

Typical rewrite: `RexSubQuery(SCALAR_QUERY)` first; later a join or correlate.
If Calcite cannot prove the inner query is single-row, it usually adds
`Aggregate(SINGLE_VALUE(...))` before joining.

Rel-tree clue: `$SCALAR_QUERY({...})` means the scalar semantics are still
inside expression context and subquery removal has not finished yet.

#### `EXISTS`

```sql
SELECT id,
    EXISTS (
      SELECT 1
      FROM inner_t i
      WHERE i.grp = o.grp
        AND i.k = o.k) AS ex
FROM outer_t o;
```

Output by `id`: `1 -> TRUE`, `2 -> TRUE`, `3 -> FALSE`, `4 -> TRUE`,
`5 -> FALSE`, `6 -> FALSE`

Meaning: `TRUE` if any matching row exists. `EXISTS` never returns `NULL`.

Typical rewrite: `RexSubQuery(EXISTS)` to join/correlate, often with an
existence marker such as `LITERAL_AGG(true)` or `MIN(TRUE)`.

Rel-tree clue: if only yes/no existence is needed, later cleanup may reduce
the shape to `SEMI` or `ANTI` join-like behavior.

#### `NOT EXISTS`

```sql
SELECT id,
    NOT EXISTS (
      SELECT 1
      FROM inner_t i
      WHERE i.grp = o.grp
        AND i.k = o.k) AS not_ex
FROM outer_t o;
```

Output by `id`: `1 -> FALSE`, `2 -> FALSE`, `3 -> TRUE`, `4 -> FALSE`,
`5 -> TRUE`, `6 -> TRUE`

Meaning: the boolean inverse of `EXISTS`.

Typical rewrite: same subquery-removal path as `EXISTS`, then an outer `NOT`
or a later `ANTI`-style plan shape.

Rel-tree clue: `ANTI` is the join-type version of "keep the left row only when
no RHS match exists".

#### `IN`

```sql
SELECT id,
    k IN (
      SELECT i.k
      FROM inner_t i
      WHERE i.grp = o.grp) AS in_subq
FROM outer_t o;
```

Output by `id`: `1 -> TRUE`, `2 -> TRUE`, `3 -> NULL`, `4 -> TRUE`,
`5 -> NULL`, `6 -> FALSE`

Meaning: membership test with full three-valued logic.

Why the edge cases matter:

- `id=3`: outer key is `NULL`, so result is `NULL` even though group `A`
  contains rows
- `id=5`: no exact match exists, but the RHS has `NULL`, so the result is
  `NULL`, not `FALSE`
- `id=6`: empty correlated RHS gives `FALSE`

Typical rewrite: `LEFT` or `LEFT_MARK` shape plus helper aggregates such as
`COUNT(*)`, `COUNT(col)`, `LITERAL_AGG(true)`, and a `CASE` expression.

Rel-tree clue: if you see marker columns and `CASE`, you are usually looking at
null-sensitive `IN` semantics being preserved.

#### `NOT IN`

```sql
SELECT id,
    k NOT IN (
      SELECT i.k
      FROM inner_t i
      WHERE i.grp = o.grp) AS not_in_subq
FROM outer_t o;
```

Output by `id`: `1 -> FALSE`, `2 -> FALSE`, `3 -> NULL`, `4 -> FALSE`,
`5 -> NULL`, `6 -> TRUE`

Meaning: `NOT IN` is the most common null-semantics trap. It is not the same
as `NOT EXISTS`.

Typical rewrite: same null-sensitive machinery as `IN`, but with inverted
final logic. `pushDownNotForIn(...)` also matters because `NOT` is pushed early
before conversion.

Rel-tree clue: when `NOT IN` is wrong, inspect marker columns, null checks, and
the `CASE` around the final boolean first.

#### `SOME` / `ANY`

```sql
SELECT id,
    k > SOME (
      SELECT i.k
      FROM inner_t i
      WHERE i.grp = o.grp) AS gt_some
FROM outer_t o;
```

Output by `id`: `1 -> NULL`, `2 -> TRUE`, `3 -> NULL`, `4 -> NULL`,
`5 -> TRUE`, `6 -> FALSE`

Meaning: quantified comparison. It is `TRUE` if any RHS comparison is `TRUE`.
If none are `TRUE` but a `NULL` is involved, the result can be `NULL`.

Typical rewrite: extrema plus counts. Calcite often rewrites `> SOME` or
`< SOME` using `MIN` or `MAX`, `COUNT(*)`, `COUNT(col)`, and `CASE`.

Rel-tree clue: `SOME =` should already have normalized to `IN`; if it has not,
that is the first thing to inspect.

#### `UNIQUE`

```sql
SELECT id,
    UNIQUE (
      SELECT i.k
      FROM inner_t i
      WHERE i.grp = o.grp) AS uq
FROM outer_t o;
```

Output by `id`: `1 -> FALSE`, `2 -> FALSE`, `3 -> FALSE`, `4 -> TRUE`,
`5 -> TRUE`, `6 -> TRUE`

Meaning: `TRUE` when the non-null rows of the subquery are unique.

Typical rewrite: `UNIQUE` becomes `NOT EXISTS` over an aggregate that groups
the RHS and filters `COUNT(*) > 1`.

Rel-tree clue: if you are debugging `UNIQUE`, start from the generated
duplicate-finding aggregate, not from the final outer boolean.

#### `ARRAY`, `MULTISET`, and `MAP` query constructors

```sql
SELECT id,
    ARRAY(
      SELECT i.v
      FROM inner_t i
      WHERE i.grp = o.grp
      ORDER BY i.v) AS arr_v,
    MULTISET(
      SELECT i.k
      FROM inner_t i
      WHERE i.grp = o.grp) AS ms_k,
    MAP(
      SELECT i.v, COALESCE(CAST(i.k AS VARCHAR(10)), 'NULL')
      FROM inner_t i
      WHERE i.grp = o.grp) AS map_vk
FROM outer_t o;
```

Output by `grp`:

- ids `1`, `2`, `3` in group `A`: `arr_v = [n, x, y, z]`,
  `ms_k = {10, 20, 20, NULL}`,
  `map_vk = {'n': 'NULL', 'x': '10', 'y': '20', 'z': '20'}`
- ids `4`, `5` in group `B`: `arr_v = [p, q]`, `ms_k = {20, NULL}`,
  `map_vk = {'p': '20', 'q': 'NULL'}`
- id `6` in group `C`: empty collection / map

Meaning: these are collection-producing subqueries, not boolean tests.

Typical rewrite: `RexSubQuery(ARRAY|MULTISET|MAP)` to `Collect` plus join or
correlate.

Rel-tree clue: if you see `Collect(field=[x])`, you are in the
collection-constructor path, not the boolean-subquery path.

### Rel operators and join types you will see

Expect ordinary `Project`, `Filter`, `Join`, `Aggregate`, and sometimes `Sort`
around subquery rewrites. The table below focuses on the subquery-specific or
easy-to-misread surfaces.

| Surface | How to read it | Common source | Executor note |
| --- | --- | --- | --- |
| `RexSubQuery` | A subquery still embedded inside an expression | Raw `expand=false` output from `SqlToRelConverter` | Your executor needs explicit expression-level subquery support if this reaches execution |
| `LogicalJoin` | Ordinary relational join with no correlation | Uncorrelated rewrite or successful decorrelation | Standard surface; this is the ideal end state for many backends |
| `LogicalCorrelate` | Nested-loop style operator; RHS can read the current left row via `$corX` | Correlated scalar, `EXISTS`, or collection rewrite | Backend must support correlated execution if decorrelation does not remove it |
| `LogicalConditionalCorrelate` | Correlated mark join; like `Correlate`, but with an attached condition | Correlated `IN`, `SOME`, `EXISTS` under mark-correlate rules | Today this is specifically the `LEFT_MARK` path |
| `JoinRelType.LEFT_MARK` | Keep every left row and append a marker column instead of right payload columns | Null-sensitive `IN`, `NOT IN`, `SOME`, or mark-based `EXISTS` | Marker semantics are easy to get wrong; check null and empty-RHS handling |
| `JoinRelType.SEMI` | Keep the left row when a match exists; do not project right columns | Simplified `EXISTS` or later mark cleanup | Often a good executor target if you do not want full marker support |
| `JoinRelType.ANTI` | Keep the left row when no match exists; do not project right columns | Simplified `NOT EXISTS` or later mark cleanup | `ANTI` is the plan-level form of "not matched" |
| `Collect` | Pack RHS rows into a single collection value | `ARRAY`, `MULTISET`, `MAP` query constructors | Collection support is required if this survives to execution |
| `EnumerableCorrelate` | Physical nested-loop correlate | Decorrelator disabled or incomplete | This is a real runtime surface, not only a logical placeholder |
| `EnumerableConditionalCorrelate` | Physical correlated mark operator | Correlated `LEFT_MARK` path | Current enumerable support is specifically for `LEFT_MARK` |

### Rel fragments to learn first

These fragments are illustrative. Exact `Project` and `Calc` placement varies,
but the meaning of `correlation`, `requiredColumns`, `joinType`, and marker
columns is stable.

#### Correlated `EXISTS` shape

```text
LogicalProject(id=[$0], ex=[$3])
  LogicalCorrelate(correlation=[$cor0], joinType=[left], requiredColumns=[{1, 2}])
    LogicalTableScan(table=[[outer_t]])
    LogicalAggregate(group=[{}], indicator=[LITERAL_AGG(true)])
      LogicalProject(indicator=[true])
        LogicalFilter(condition=[AND(=($1, $cor0.k), =($2, $cor0.grp))])
          LogicalTableScan(table=[[inner_t]])
```

How to read it:

- `$cor0` is the current outer row
- `requiredColumns=[{1, 2}]` means the RHS reads `outer_t.k` and `outer_t.grp`
- `joinType=[left]` means every outer row survives
- the RHS aggregate builds an existence indicator for the current outer row

#### Correlated mark-semantics shape

```text
LogicalProject(id=[$0], in_subq=[$3])
  LogicalConditionalCorrelate(
      correlation=[$cor0],
      joinType=[left_mark],
      requiredColumns=[{1}],
      condition=[=($1, $3)])
    LogicalTableScan(table=[[outer_t]])
    LogicalProject(k=[$0])
      LogicalFilter(condition=[=($1, $cor0.grp)])
        LogicalTableScan(table=[[inner_t]])
```

How to read it:

- `left_mark` means Calcite is producing a marker result, not right payload
  columns
- `condition` is the match condition used for the mark
- the RHS can still read outer columns through `$cor0`
- this shape is common for null-sensitive `IN`, `NOT IN`, and `SOME`

#### Post-cleanup `SEMI` / `ANTI` shape

```text
LogicalJoin(joinType=[semi], condition=[AND(=($1, $3), =($2, $4))])
  LogicalTableScan(table=[[outer_t]])
  LogicalAggregate(group=[{0, 1}])
    LogicalTableScan(table=[[inner_t]])
```

How to read it:

- `SEMI` keeps only left rows with matches
- `ANTI` is the same pattern with `joinType=[anti]`
- right columns are not projected
- this is the simpler shape you often want after mark cleanup when full marker
  semantics are no longer needed

### Helper aggregates and expressions introduced by rewrite

| Surface | Why Calcite introduces it | Common source SQL | Notes |
| --- | --- | --- | --- |
| `SINGLE_VALUE(expr)` | Enforce scalar-subquery semantics when uniqueness is not known | Scalar subquery | If more than one row survives, execution can fail |
| `LITERAL_AGG(true)` | Carry an existence marker through aggregation | `EXISTS`, `IN`, `SOME` | Common in current `SubQueryRemoveRule` plans |
| `MIN(TRUE)` | Older existence-indicator aggregate form | `createExistsPlan(...)` style rewrites | Same job as a marker aggregate |
| `COUNT(*)` | Detect empty subquery | `IN`, `NOT IN`, `SOME` | Used in `CASE` branches |
| `COUNT(col)` | Detect whether RHS contains nulls | `IN`, `NOT IN`, `SOME` | Important for three-valued logic |
| `MIN(col)` / `MAX(col)` | Collapse quantified comparison to an extremum | `SOME` | Which one appears depends on the comparison operator |
| `GROUPING(...)` | Distinguish rolled-up rows in grouping-set based quantified rewrites | Some `SOME` rewrites | Shows up in more elaborate quantified-subquery plans |
| `CASE` | Encode SQL three-valued logic explicitly | `IN`, `NOT IN`, `SOME` | Usually the fastest way to spot null/empty handling |
| `IS NULL` / `IS NOT NULL` | Test marker columns or detect null keys | `EXISTS`, `IN`, `SOME` | Often the final boolean comes from these checks |
| `SUM0` | Null-safe aggregate cleanup after `PROJECT_OVER_SUM_TO_SUM0_RULE` | Cases where a subquery rewrite leaves `SUM` under a project | This is a helper from the subquery HEP stage, not a distinct SQL construct |

### Executor support audit

Use this as a support checklist for your backend.

- If execution starts before subquery removal, you need `RexSubQuery`
  expression support. Most embedders prefer to avoid this and execute only
  after rewrite.
- If execution starts after `Programs.subQuery(...)` but before decorrelation,
  you may need `Correlate`, `ConditionalCorrelate`, `LEFT_MARK`, `Collect`,
  `SINGLE_VALUE`, `LITERAL_AGG(true)`, `CASE`, and null-sensitive aggregates.
- If execution starts after successful decorrelation, you mostly need standard
  relational operators, but `SEMI` and `ANTI` can still remain.
- If decorrelation is disabled or incomplete, the physical fallback surface is
  `EnumerableCorrelate`, `EnumerableConditionalCorrelate`, and sometimes
  physical `LEFT_MARK` join implementations.
- If your executor does not support `LEFT_MARK`, keep the stable default
  pipeline and verify that later cleanup reduces marker plans before execution.
  Do not assume that all `NOT IN` or null-sensitive `IN` cases can be lowered
  to plain `SEMI` or `ANTI` early.
- If your backend is DataFusion-based and you want a robust fallback for
  incomplete rewrite or decorrelation, use the next section as the execution
  contract.

## DataFusion execution spec for residual Calcite surfaces

This section assumes you execute pre-enumerable Calcite `RelNode`s and
translate them into a DataFusion-based engine. If you already see
`EnumerableCorrelate` or `EnumerableConditionalCorrelate`, you are past the
right boundary: those are Calcite's own physical fallback operators, not the
input this spec targets.

Current DataFusion already covers `SEMI`, `ANTI`, `LEFT_MARK`, normal
`CASE`/`IS NULL`/`COUNT`/`MIN`/`MAX`, `GROUPING(...)`, and standard join and
aggregate plans. It does not directly physicalize raw subquery expressions or
Calcite-only apply operators.

### Recommended architecture for a direct physical bridge

If your system already serializes Calcite plans directly into DataFusion
physical plans, keep that high-level shape. The important change is to make
the adapter boundary explicit.

This is the reason not to force a detour through DataFusion logical plans just
to reach execution:

- Calcite uses stable positional field references in relational expressions
- DataFusion logical plans are centered around `Column { relation, name }` and
  `DFSchema` name-based lookup, and only physical planning later resolves that
  named reference to an ordinal
- therefore a Calcite-to-DataFusion logical translation layer has to preserve
  naming, qualification, and aliasing much more carefully than a positional
  Calcite bridge naturally wants to

For a Calcite-owned system, the recommended ownership boundary is:

1. Calcite owns SQL meaning, subquery removal, and primary decorrelation.
2. Your serialization layer owns normalization from Calcite `RelNode` into a
   small explicit execution contract that is still positional and
   Calcite-shaped.
3. That contract lowers either to native DataFusion physical operators or to
   custom physical extension operators in your fork.

Recommended concrete pipeline:

1. Run Calcite planning through your normal subquery and decorrelation stages.
2. Before serialization, normalize any residual raw subquery or correlation
   state into explicit bridge nodes such as `ScalarApply`, `ExistsApply`,
   `InApply`, `QuantifiedCompareApply`, `CollectApply`, `Correlate`, or
   `ConditionalCorrelate`.
3. Serialize ordinary joins, aggregates, filters, projects, `SEMI`, `ANTI`,
   and decorrelated `LEFT_MARK` directly to DataFusion physical plans.
4. Serialize residual apply or correlate surfaces to custom DataFusion
   extension execs.
5. Reject any plan that still contains raw `RexSubQuery` or correlated
   references with no owning apply/correlate node.

What this means in practice:

- the adapter boundary should accept explicit operators, not hidden subplans
  inside expressions
- the adapter boundary should stay positional even if the downstream
  DataFusion physical nodes eventually need column names for schema objects
- DataFusion should execute the explicit contract you hand it, not rediscover
  Calcite semantics from a partially lowered logical tree

This is also the right way to think about upstream or fork investment in
DataFusion:

- high-value work: explicit apply/correlate execution surfaces, better
  nullable-mark handling, helper aggregates such as `SINGLE_VALUE`, and more
  rewrite coverage for already-explicit logical patterns
- low-value primary fix: teaching the physical-expression layer to execute raw
  `ScalarSubquery`, `InSubquery`, `Exists`, or `OuterReferenceColumn`
  directly

`LogicalPlan::Subquery(_) => todo!()` matters mainly if you choose to hand
DataFusion logical subquery plans to its physical planner. In a direct
Calcite-to-physical bridge, the more important invariant is that raw subquery
state should never reach that boundary in the first place.

### DataFusion compatibility matrix

| Calcite surface | DataFusion today | Contract for a Calcite-to-DataFusion executor |
| --- | --- | --- |
| `LogicalJoin(joinType=[semi])` | Direct `JoinType::LeftSemi` | Map directly |
| `LogicalJoin(joinType=[anti])` | Direct `JoinType::LeftAnti` | Map directly; for `NOT IN` null-aware semantics, only use native null-aware anti when DataFusion's current restrictions fit, notably `LeftAnti` and the supported join-key shape |
| `LogicalJoin(joinType=[left_mark])` | Direct `JoinType::LeftMark`, but marker column is non-nullable `Boolean` | Direct-map only when Calcite marker type is non-nullable; otherwise lower to extra mark/`CASE` logic or use a custom nullable-mark implementation |
| `RexSubQuery` / raw subquery expression | No; raw subquery expressions are not physicalized, and `LogicalPlan::Subquery` is not executable | Normalize before physical planning |
| `LogicalCorrelate` | No native logical or physical node | Implement as a custom extension plan and nested-loop apply exec |
| `LogicalConditionalCorrelate` | No native logical or physical node | Implement as a custom extension plan with left-mark semantics |
| `Collect` | No native logical node | Lower `ARRAY` / `MULTISET` to collection aggregation; implement `MAP` explicitly |
| `SINGLE_VALUE` | No exact native aggregate | Implement exact semantics or lower to `CASE COUNT(*) ...` |
| `LITERAL_AGG(lit)` | No exact native aggregate | Lower structurally to a literal projection above the aggregate that creates the group row |
| `SUM0` | No exact native aggregate | Lower to `COALESCE(SUM(arg), typed_zero)` |

### Translation guardrails

- Do not route through DataFusion logical plans unless you intentionally want
  DataFusion to become a second semantic owner for name/alias resolution.
  For a Calcite-owned system, a direct physical bridge plus a small explicit
  normalization contract is the more robust architecture.
- Never feed raw Calcite subquery state into DataFusion physical planning.
  Current DataFusion still leaves `LogicalPlan::Subquery(_)` as `todo!()`, and
  its physical-expression planner rejects unsupported logical expressions such
  as `Exists`, `InSubquery`, `ScalarSubquery`, and `OuterReferenceColumn`.
- If a Calcite tree still contains `$corX` references after rewrite but there
  is no owning `Correlate` or `ConditionalCorrelate`, fail translation and
  treat it as a Calcite planner gap.
- Prefer DataFusion `LogicalPlan::Extension` plus an extension planner for
  true Calcite-only semantics such as `Correlate` and
  `ConditionalCorrelate`. Do not try to smuggle them through as native
  DataFusion `Subquery` nodes.

### Recommended bridge apply layer

If you want executor robustness when Calcite does not fully remove a subquery
or decorrelate correlation away, add a bridge layer between raw
`RexSubQuery` and backend translation.

The idea is simple: replace "expression with a hidden subplan inside it" with
"explicit apply operator with a well-defined output column".

Common bridge-node shape:

```text
ApplyNode {
  left: Rel
  right: Rel                // parameterized subplan template
  correlationId: CorrelationId?
  requiredColumns: BitSet
  resultField: Field
}
```

Common contract:

- output schema is `left.schema + resultField`
- for each left row `L`, bind `correlationId` using `requiredColumns`
- evaluate `right` under that binding to get `R(L)`
- fold `R(L)` according to the node kind into one value `V(L)`
- emit `concat(L, V(L))`

Two practical rules:

- if there is no outer input, use a synthetic one-row left input
- after creating the bridge node, rewrite the parent expression to read the
  appended result column rather than keeping the raw `RexSubQuery`

Recommended bridge nodes:

#### `ScalarApply`

Use for raw scalar subqueries.

```text
ScalarApply {
  left
  right
  correlationId?
  requiredColumns
  resultField
}
```

Contract per left row:

- `NULL` if `R(L)` has 0 rows
- the single projected value if `R(L)` has 1 row
- runtime error if `R(L)` has more than 1 row

This is the bridge form of `$SCALAR_QUERY({...})`.

#### `ExistsApply`

Use for `EXISTS` and `NOT EXISTS`.

```text
ExistsApply {
  left
  right
  correlationId?
  requiredColumns
  negated: boolean
  resultField: BOOLEAN NOT NULL
}
```

Contract per left row:

- `exists = R(L) is non-empty`
- result is `exists` for `EXISTS`
- result is `!exists` for `NOT EXISTS`

The result is never `NULL`.

#### `InApply`

Use for `IN` and `NOT IN`.

```text
InApply {
  left
  right
  correlationId?
  requiredColumns
  leftKeyExprs
  rightKeyExprs
  negated: boolean
  resultField: BOOLEAN NULLABLE
}
```

For each RHS row, compare left and right keys with SQL three-valued equality.
Then fold the row results:

- `IN`: `TRUE` if any comparison is `TRUE`; else `NULL` if no comparison is
  `TRUE` and at least one is `NULL`; else `FALSE`
- `NOT IN`: `FALSE` if any comparison is `TRUE`; else `NULL` if no comparison
  is `TRUE` and at least one is `NULL`; else `TRUE`

This gives the right edge cases automatically:

- empty RHS => `FALSE` for `IN`, `TRUE` for `NOT IN`
- left `NULL` or RHS `NULL` can produce `NULL`

#### `QuantifiedCompareApply`

Use for `SOME` / `ANY` / `ALL`.

```text
QuantifiedCompareApply {
  left
  right
  correlationId?
  requiredColumns
  leftExpr
  rightExpr
  op                        // =, <>, <, <=, >, >=
  quantifier: ANY | ALL
  resultField: BOOLEAN NULLABLE
}
```

For each RHS row, compute the three-valued comparison result:

- `ANY`: `TRUE` if any comparison is `TRUE`; else `NULL` if none are `TRUE`
  and at least one is `NULL`; else `FALSE`
- `ALL`: `FALSE` if any comparison is `FALSE`; else `NULL` if none are
  `FALSE` and at least one is `NULL`; else `TRUE`

Empty-RHS behavior:

- `ANY` => `FALSE`
- `ALL` => `TRUE`

#### `CollectApply`

Use for `ARRAY`, `MULTISET`, and `MAP`.

```text
CollectApply {
  left
  right
  correlationId?
  requiredColumns
  collectionKind: ARRAY | MULTISET | MAP
  resultField
}
```

Contract per left row:

- always emit one collection value
- empty RHS => empty collection, not `NULL`
- duplicates are preserved

Element rules:

- `ARRAY` / `MULTISET`: one-column RHS becomes scalar elements; multi-column
  RHS becomes row/struct elements
- `MAP`: RHS normally yields key/value rows; duplicate-key and null-key policy
  should be made explicit in your executor layer because `Collect` itself does
  not encode that runtime policy

#### `UniqueApply`

Use for `UNIQUE(subquery)` if you want an explicit bridge node rather than
normalizing immediately to `NOT EXISTS`.

```text
UniqueApply {
  left
  right
  correlationId?
  requiredColumns
  projectedExprs
  resultField: BOOLEAN NOT NULL
}
```

Contract per left row:

- discard RHS rows where any projected field is `NULL`
- return `TRUE` iff no remaining projected row appears more than once
- otherwise return `FALSE`

#### Parent rewrite rule

After creating a bridge node, the original expression site should read the new
result column:

- `SELECT ..., subquery_expr AS x` becomes `Apply -> Project(resultField AS x)`
- `WHERE subquery_predicate` becomes `Apply -> Filter(resultField)`
- `expr + scalar_subquery` becomes `Apply -> Project(expr + resultField)`

This is the main reason bridge nodes are easier to execute and debug than raw
`RexSubQuery`.

### `Correlate`: execution contract

Input fields:

- left input
- right input
- `correlationId`
- `requiredColumns`
- `joinType` in `{inner, left, semi, anti}`

Execution model:

- For each left row `L`, create a correlation environment keyed by
  `correlationId`.
- Evaluate the right plan under that environment to get `R(L)`.
- `requiredColumns` is the exact left-column subset the right side is allowed
  to depend on. Binding the full left row is fine internally, but it must
  behave as if only `requiredColumns` are visible.

Row-production semantics:

| `joinType` | Output for one left row `L` |
| --- | --- |
| `inner` | Emit `concat(L, r)` for every `r` in `R(L)` |
| `left` | Same as `inner`; if `R(L)` is empty, emit one `concat(L, nullRight)` |
| `semi` | Emit `L` once if `R(L)` is non-empty |
| `anti` | Emit `L` once if `R(L)` is empty |

Additional rules:

- Preserve left-row multiplicity. `SEMI` and `ANTI` are existence tests, so
  they never duplicate a left row.
- Preserve the right side's own multiplicity for `inner` and `left`.
- Nested correlates create nested environments by id; do not key them by field
  name.
- Recommended implementation: a custom `Correlate` extension node plus a
  nested-loop apply executor. Memoization by correlated key is an optimization,
  not a semantic requirement; only enable it for deterministic right subplans.

### `ConditionalCorrelate`: execution contract

This is Calcite's left-mark apply operator. It is still correlated, so it is
not the same thing as a decorrelated `LogicalJoin(joinType=[left_mark])`.

Input fields:

- everything from `Correlate`
- `condition`
- `joinType=[left_mark]`

Execution model for one left row `L`:

1. Bind `correlationId` as with `Correlate`.
2. Evaluate every right row `r` in `R(L)`.
3. Evaluate `condition(L, r)` in SQL three-valued logic.
4. Track:
   - `seenTrue`: at least one evaluation is `TRUE`
   - `seenUnknown`: no `TRUE`, but at least one evaluation is `NULL`
5. Emit exactly one output row `concat(L, mark)` where:
   - `mark = TRUE` if `seenTrue`
   - `mark = NULL` if not `seenTrue` and `seenUnknown`
   - `mark = FALSE` otherwise, including the empty-right case

Notes:

- If the Calcite marker column type is non-nullable boolean, the planner has
  proved the `NULL` branch unreachable. Treat a produced `NULL` as an executor
  bug in that case.
- DataFusion's native `LeftMark` schema appends a non-nullable boolean `mark`.
  Therefore direct mapping is only safe for decorrelated `LEFT_MARK` joins
  whose marker cannot be `UNKNOWN`, or for a translation that expands the
  logic into extra mark joins plus `CASE` the way DataFusion's own
  quantified-subquery rules do.
- For correlated `ConditionalCorrelate`, the robust default is a custom
  nullable-mark apply executor.

### Raw `RexSubQuery`: normalization contract

Treat a surviving `RexSubQuery` as a translation-time normalization problem,
not as a DataFusion physical-expression feature. A good last-resort strategy
is to lower it to one of your own apply nodes before you hand the plan to
DataFusion.

| `RexSubQuery.kind` | Required result |
| --- | --- |
| scalar | Execute the bound subquery, require one projected column, return `NULL` for 0 rows, the value for 1 row, and error for more than 1 row |
| `EXISTS` | `TRUE` if the bound subquery returns any row, else `FALSE` |
| `NOT EXISTS` | `TRUE` if the bound subquery returns no rows, else `FALSE` |
| `IN` | `FALSE` on empty RHS; `TRUE` if any non-null RHS row equals the left key; else `NULL` if the left key is `NULL` or any RHS key is `NULL`; else `FALSE` |
| `NOT IN` | `TRUE` on empty RHS; `FALSE` if any non-null RHS row equals the left key; else `NULL` if the left key is `NULL` or any RHS key is `NULL`; else `TRUE` |
| `SOME` / `ANY` | Fold the comparison results with SQL 3VL: `TRUE` if any row is `TRUE`; else `NULL` if none are `TRUE` and at least one is `NULL`; else `FALSE` |
| `ALL` | Fold the comparison results with SQL 3VL: `FALSE` if any row is `FALSE`; else `NULL` if none are `FALSE` and at least one is `NULL`; else `TRUE` |
| `UNIQUE` | Prefer to normalize exactly as Calcite does: `NOT EXISTS` over non-null projected rows grouped by the projected columns with `HAVING COUNT(*) > 1` |
| `ARRAY` / `MULTISET` / `MAP` | Normalize to `Collect` |

### `Collect`: execution contract

`Collect` collapses the entire input stream into exactly one output row with
one collection-valued field.

Common rules:

- The output cardinality is always one row.
- Empty input yields an empty collection, not `NULL`.
- Duplicates are preserved.
- Collection element order is the child-plan enumeration order. Without an
  explicit inner `Sort`, do not promise deterministic order across
  distributed execution.

Collection-specific rules:

- `ARRAY_QUERY_CONSTRUCTOR`: if the child row type has one field, collect that
  scalar field; otherwise collect a row/struct element.
- `MULTISET_QUERY_CONSTRUCTOR`: same element-shape rule as `ARRAY`, but bag
  semantics are the important contract, not ordering.
- `MAP_QUERY_CONSTRUCTOR`: the child row type is the map element row, normally
  key and value columns. If you lower this into DataFusion, `make_map` can
  help build the final value, but current DataFusion does not provide a native
  `map_agg`. A robust default is to aggregate keys and values separately and
  build the map afterward. If your runtime needs a duplicate-key rule, pick
  one explicitly and document it; Calcite's `Collect` node does not carry that
  policy for you.

### Helper aggregates: execution contract

| Surface | Exact meaning | Recommended lowering in a DataFusion-based executor |
| --- | --- | --- |
| `SINGLE_VALUE(x)` | Return the lone value, `NULL` on 0 rows, error on more than 1 row | Implement directly or lower to `CASE COUNT(*) WHEN 0 THEN NULL WHEN 1 THEN MIN(x) ELSE error('more than one value in agg SINGLE_VALUE') END` |
| `LITERAL_AGG(lit)` | For every produced aggregate group row, return the same literal `lit` | Do not add a new aggregate if you do not need one; project the literal above the aggregate that determines group existence |
| `SUM0(x)` | Same as `SUM(x)`, but return zero when no non-null input value exists | Lower to `COALESCE(SUM(x), typed_zero)` where `typed_zero` uses the Calcite aggregate result type |

## Pipeline edge cases and current gaps

Two superficially similar situations need to be separated first:

- correlated references inside `RexSubQuery.rel` before subquery-removal rules
  run are normal in the `expand=false` pipeline
- correlated references that survive after rewrite without an owning
  `Correlate` or `ConditionalCorrelate` are usually a planner gap

The table below is the practical debugging map.

| Symptom | Likely owner | What to inspect first | Practical move |
| --- | --- | --- | --- |
| Correlated refs exist only inside `RexSubQuery.rel`, but no `Correlate` node exists yet | Normal pre-rule state | Whether you are on direct converter decorrelation or rule-driven subquery removal | Either run direct decorrelation or run `Programs.subQuery(...)`; this is not automatically a bug |
| `$corX` appears after rewrite, but `requiredColumns` or `variablesSet` is empty or wrong | Planner rule / metadata bug | Producing rule, especially `variablesSet`, and nearby before/after plans | Treat this as a Calcite gap and fix the rule; do not ask the executor to infer hidden correlation |
| Top-down decorrelation fails because a rule dropped `variablesSet` | Rule interaction bug | `new-decorr.iq` coverage for `[CALCITE-7434]` and the producing rule's output | Fix Calcite or avoid the offending rule combination until fixed |
| `RexSubQuery` never disappears | Missing or misordered subquery phase | Whether `Programs.subQuery(...)` or direct decorrelation actually ran | Keep subquery removal in its own stage; do not bury it in broad rule soup |
| Multiple `CorrelationId`s appear in one filter | Decorrelator stress area | `RelDecorrelatorTest` cases for multiple correlation ids | Expect planner work, not executor work |
| Correlated `UNION`, `INTERSECT`, or other `SetOp` remains | Decorrelator stress area | `SetOp` handling in `RelDecorrelator` and top-down tests | If the backend cannot run correlated set ops, this usually requires a Calcite fix |
| `RexFieldAccess` in join conditions rewrites incorrectly | Decorrelator field-mapping bug | `RexFieldAccess` tests and operator-specific decorrelator logic | Fix field-offset or mapping logic in Calcite |
| `LIMIT 1`, `OFFSET`, or `Sort` around a correlated subquery breaks | Legacy sort decorrelation corner case | `decorrelateSortWithRowNumber(...)` and `decorrelateSortAsAggregate(...)` | Start from the operator-specific sort path before changing generic logic |
| Top-down decorrelator leaves the original plan in place | Experimental-path limitation | Whether `UnsupportedOperationException` was caught internally | Either disable the top-down flag or extend the algorithm; do not assume later rules will save the plan |
| `LogicalConditionalCorrelate` or `LEFT_MARK` reaches execution unexpectedly | Pipeline configuration or incomplete cleanup | Whether you enabled top-down mark rewrites and whether decorrelation ran to completion | Add backend support, or keep the stable default pipeline and avoid the experimental path |
| Backend reports unsupported `SINGLE_VALUE`, `Collect`, `LITERAL_AGG`, or physical correlate operators | Executor coverage gap | The `Executor support audit` section above | Decide explicitly: add executor support, or keep planning until these surfaces disappear |

The `variablesSet` question is the one most likely to waste debugging time:

- before subquery removal, a correlated variable can live inside
  `RexSubQuery.rel` with no `Correlate` node yet, and that is normal
- after rule-driven subquery removal, correlated references should either be
  owned by `Correlate` / `ConditionalCorrelate` or be eliminated
- if they are not, the likely fix is in Calcite's rule or metadata flow, not
  in the executor

When in doubt, inspect the tree at three checkpoints:

1. immediately after `convertQuery(...)`
2. immediately after `Programs.subQuery(...)`
3. immediately after decorrelation

That tells you whether the missing ownership started in conversion, rewrite, or
decorrelation.

## Debugging checklist

### Wrong stage? Use the knobs as ownership clues

- `withExpand(false)` versus `withExpand(true)`: tells you whether you are
  debugging `RexSubQuery` retention or eager expansion
- `withDecorrelate(false)` versus `withDecorrelate(true)`: isolates the
  decorrelator
- `forceDecorrelate`: controls decorrelation in `Prepare`
- `topDownGeneralDecorrelationEnabled`: selects the experimental path
- `withLateDecorrelate(true)` in rule tests: lets you inspect the tree before
  the final decorrelation stage

This is also why `docs/ai/knowledge/testing-and-fixtures.md` says to treat
these knobs as ownership clues.

### Symptom to first place to inspect

- Parse or validation failure: parser tests, validator code, and validator
  tests
- Wrong `RexSubQuery` shape: `SqlToRelConverter` and converter tests
- Unfamiliar SQL construct or rel operator: `SQL + operator primer`
- Wrong rule rewrite around `JOIN ... ON`: `SubQueryRemoveRule` and
  `RelOptRulesTest`
- Decorrelator assertion or wrong field mapping: `RelDecorrelator`,
  `RelDecorrelatorTest`
- Top-down-specific left-mark bug: `TopDownGeneralDecorrelator` and the
  top-down `RelOptRulesTest` cases
- Correlated refs with no owning correlate or wrong `requiredColumns`:
  `Pipeline edge cases and current gaps`, then the producing rule
- Unsupported executor surface such as `LEFT_MARK`, `Collect`,
  `LITERAL_AGG(true)`, or `SINGLE_VALUE`: `Executor support audit`, then
  `DataFusion execution spec for residual Calcite surfaces` if your backend is
  DataFusion-based
- Correlated operator reaches physical convention unexpectedly: enumerable
  correlate or conditional-correlate rules and tests

### Best existing tests to start from

- `core/src/test/java/org/apache/calcite/test/SqlToRelConverterTest.java`
- `core/src/test/java/org/apache/calcite/sql2rel/RelDecorrelatorTest.java`
- `core/src/test/java/org/apache/calcite/test/RelOptRulesTest.java`
- `core/src/test/resources/sql/sub-query.iq`
- `core/src/test/resources/sql/new-decorr.iq`
- `core/src/test/java/org/apache/calcite/test/enumerable/EnumerableCorrelateTest.java`
- `core/src/test/java/org/apache/calcite/test/enumerable/EnumerableHashJoinTest.java`
- `core/src/test/java/org/apache/calcite/test/enumerable/EnumerableJoinTest.java`

## Durable takeaways

- Default modern embedding should start from `expand=false`.
- There are two real current flows: direct converter decorrelation and
  program-driven subquery removal plus decorrelation.
- `Programs.standard()` is the safest model for custom program sequencing.
- Field trimming belongs after decorrelation.
- Mark-correlate rewrites are the best fit for the top-down algorithm.
- `Planner.rel()` is convenient, but it is not the same pipeline as
  `Programs.standard()`.
- When debugging, identify whether you are still in `SqlNode`, `RexSubQuery`,
  correlate, decorrelated join, or physical nested-loop territory before
  changing code.
