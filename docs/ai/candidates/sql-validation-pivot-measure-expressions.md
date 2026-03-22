# Candidate Archive: `PIVOT` Measure Expressions And Front-End Layering

Keep this in quarantine until it is either promoted into reviewed knowledge,
rewritten, or deleted.

## Session-shaped lessons worth preserving for now

- For clause-local SQL features, classify the bug by layer first: parser
  admission, AST bookkeeping, validator legality, row-type derivation, or
  `sql2rel` lowering.
- If validation is relaxed but lowering still assumes the old shape, the fix is
  incomplete by design.
- Structural classifiers and public support policy are related but different.
- Incorrect consumed-column bookkeeping can silently become wrong implicit
  grouping.
- Vendor runtime behavior should not be copied blindly when it conflicts with
  documented semantics and a cleaner architectural model.

## Test order that proved useful

1. Parser tests for syntax admission under conformance.
2. Validator tests for legal and illegal measure forms.
3. `SqlToRelConverterTest` plan checks for decomposition and reconstruction.
4. End-to-end or external-engine probes only after the internal semantic model
   is clear.
- Related reviewed guide: `docs/ai/knowledge/sql-validation.md`
