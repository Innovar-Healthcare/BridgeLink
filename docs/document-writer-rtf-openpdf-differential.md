# Document Writer: iText → OpenPDF/OpenRTF Differential Finding (v26.6.0 → HEAD)

**Context:** CVE-07 removed iText 2.1.7 + itext-rtf 2.1.7 from the Document Writer connector
(Phase 22) and replaced them with OpenPDF 2.0.5 (PDF path continues via openhtmltopdf + PDFBox,
unaffected) and OpenRTF 1.2.1 (the RTF path's actual library swap). This note captures the
differential-comparison finding produced while closing the regression-safety-net gaps this swap
shipped without (Phase 22.1, Phase 22.2), and the auditable coverage story for how those gaps
were closed.

## The differential finding

1. **`DocumentDispatcher.java` is byte-identical v26.6.0 → HEAD.** Only the jars changed — no
   product source edit was needed or made anywhere in this coverage work (test-only, CVE-07).

2. **The PDF path is unchanged jar-for-jar.** `createPDF` renders through `openhtmltopdf` +
   `PDFBox`, neither of which Phase 22 touched. No PDF regression is possible from this swap;
   `encryptPDF`'s `PDDocument`/`StandardProtectionPolicy` encryption path is likewise untouched.

3. **The RTF path swapped iText 2.1.7 → OpenPDF 2.0.5 + OpenRTF 1.2.1.** On well-behaved
   templates (a single paragraph, a single simple table cell) both libraries render
   **byte-identical RTF output**, modulo two inert differences: an inert generator watermark
   comment token and an inert footnote-separator configuration block. Neither affects rendered
   content.

4. **On realistic multi-table + heading clinical templates, the libraries diverge sharply.** The
   old iText 2.1.7 `HtmlParser` throws `ClassCastException: Table cannot be cast to
   TextElementArray` on a template with an `<h1>` heading followed by two sibling `<table>`
   elements — a shape any real clinical multi-section report (vitals + medications, for example)
   is likely to produce. OpenRTF renders this exact shape correctly, extracting all three text
   sources (heading + both table tokens) faithfully via `RTFEditorKit`. **This is a strict
   superset of iText's capability — an improvement, not a regression.** The crash cannot be
   re-run against the old library today (iText/itext-rtf were removed under CVE-07), so the new
   coverage instead proves the new path renders this shape content-faithfully.

## The auditable coverage story

Phase 22.1's `DocRenderSeamTest` (added when the swap itself landed) exercised only a single
well-behaved RTF template and a single-cell table — it never touched the divergent multi-table
path, and it added no end-to-end channel-level coverage for the Document Writer connector at
all. Two follow-up plans closed those gaps:

- **SC-1 (seam-level complex-table differential) — plan 22.2-01.** Added
  `DocRenderSeamTest#testComplexMultiTableRtfRendersContentFaithful`: an `<h1>` heading plus two
  separate sibling `<table>` elements (tokens `VITALS_TABLE_TOKEN_22P2` /
  `MEDS_TABLE_TOKEN_22P2`) driven through the real `createRTF` seam, asserting the `{\rtf`
  control header plus all three text sources in the `RTFEditorKit`-extracted output. This is the
  exact template shape that crashed iText 2.1.7 (finding #4 above).

- **SC-2 (end-to-end smoke coverage) — plan 22.2-02.** Rather than write a new smoke test, this
  plan **extended the pre-existing `StubChannelsTest.docWriter()`** (Phase 18, plan 18-07), which
  already deployed a Document Writer channel and asserted the Phase 22 PDF/RTF fidelity baseline.
  A new "Complex RTF Destination" (`metaDataId` 3) was added to `doc-writer-test.xml`, writing a
  distinct on-disk file `output-complex.rtf` from the same `VITALS_TABLE_TOKEN_22P2` /
  `MEDS_TABLE_TOKEN_22P2` template shape as the seam-level fixture above, reusing the exact same
  tokens for auditability. `docWriter()` was extended to poll for that file and assert both
  table tokens survive the real deploy → pump → `DocumentDispatcher.createRTF()` → disk round
  trip — proving the complex-table finding end-to-end, not just at the reflective seam. The two
  Phase-22 baseline assertions (`output.pdf` / `output.rtf` containing `EXPECTED_PATIENT`) were
  left byte-for-byte unchanged.

- **SC-3 (encrypted-PDF round trip) — already seam-proven, on-disk variant added this phase.**
  `DocRenderSeamTest#testEncryptPdfPasswordRoundTrip` (Phase 22.1) already proved the
  `encryptPDF` reflective seam: `PDDocument.load` without a password throws
  `InvalidPasswordException`, and loads correctly with the fixture password `s3cret`. This phase
  adds the **on-disk, channel-deployed variant**: a new "Encrypted PDF Destination"
  (`metaDataId` 4, `documentType=pdf`, `encrypt=true`, `password=s3cret`) writing
  `output-enc.pdf`, with `docWriter()` extended to mirror the exact same load/reject/open
  assertion shape against the real file the deployed channel produced.

- **D-05 (SENT-count gate).** `pumpAll()`'s destination-targeting list was widened from
  `[1, 2]` to `[1, 2, 3, 4]`, and `docWriter()`'s `assertThreeLevels` minimum-sent threshold was
  raised from `1` to `4` — the channel-level `sent` statistic sums the per-destination `SENT`
  count across every destination connector, so a silently-dropped destination now fails the L1
  gate instead of passing unnoticed.

- **DW-1 (non-ASCII/accented fidelity) — plan 22.2-01.** Folded into the same seam-level plan as
  a bonus fixture: `DocRenderSeamTest#testNonAsciiFidelityAcrossPdfAndRtf` empirically proved (via
  a standalone probe against the real jars, not guesswork) that `DocumentDispatcher.java:192`'s
  platform-default `getBytes()` charset encode round-trips correctly through both the `createPDF`
  and `createRTF` legs on this JVM. The live `//TODO verify the character encoding` at
  `DocumentDispatcher.java` ~308 remains a latent portability risk on a non-UTF-8-default
  platform, but is not a reproducible defect here — flagged as a candidate follow-up, not filed
  as a fix (test-only phase; D-06 explicitly forbids a product-code change even if a weakness had
  been found).

## Verification

- Seam-level fixtures: `server/test/com/mirth/connect/connectors/doc/DocRenderSeamTest.java`,
  green in the standard `**/*Test.class` batch on JDK 17 and JDK 21.
- End-to-end smoke: `smoke-tests/src/com/mirth/connect/smoketest/StubChannelsTest.java#docWriter`,
  green via `smoke-tests/run-smoke-test.sh` on JDK 21 (embedded-Derby runtime requirement, Phase 20
  platform constraint — `smoke-tests/build.xml` still compiles `--release 17`).

## Bottom line

The RTF library swap (CVE-07) is a net improvement for Document Writer users: PDF output is
provably unaffected, well-behaved RTF templates render byte-identically (modulo two inert
tokens), and the one template shape where the two libraries genuinely diverge is a case where the
old library crashed and the new one succeeds. That finding is now backed by real, falsifiable
coverage at both the reflective seam and the deployed-channel level, closing the CVE-07
regression-safety-net gap this swap originally shipped without.
