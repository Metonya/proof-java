package dev.proofjava.analysis.jacoco;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.proofjava.analysis.AnalysisException;

/**
 * Runs against the checked-in fixtures under fixtures/jacoco/ (repo root),
 * one level above this module's basedir - Surefire's default working
 * directory is the module basedir.
 */
class JacocoXmlParserTest {

    private static final Path FIXTURES = Path.of("../fixtures/jacoco");

    private final JacocoXmlParser parser = new JacocoXmlParser();

    @TempDir
    Path tempDir;

    @Test
    void parsesMixedCoverageFixtureWithAllThreeMetricsDistinguishable() {
        JacocoReport report = parser.parse(FIXTURES.resolve("mixed-coverage.xml"));

        assertEquals("mixed-coverage-fixture", report.name());
        assertEquals(1, report.sourceFiles().size());
        // D-04 parity target: report-level LINE counter, read verbatim.
        assertEquals(1, report.totalLineMissed());
        assertEquals(4, report.totalLineCovered());

        SourceFileReport calc = report.sourceFiles().get(0);
        assertEquals("com/example", calc.packageName());
        assertEquals("Calc.java", calc.fileName());
        assertEquals("com/example/Calc.java", calc.packageQualifiedPath());
        assertEquals(1, calc.reportedLineMissed());
        assertEquals(4, calc.reportedLineCovered());

        List<LineCoverage> lines = calc.lines();
        assertEquals(5, lines.size());

        long jacocoLineCovered = lines.stream().filter(LineCoverage::isCovered).count();
        long strictLineCovered = lines.stream().filter(LineCoverage::isFullyCovered).count();
        assertEquals(4, jacocoLineCovered, "jacoco-line numerator");
        assertEquals(3, strictLineCovered, "strict-line numerator: must be lower, branch completeness not folded in (D-19)");

        int coveredBranches = lines.stream().mapToInt(LineCoverage::coveredBranches).sum();
        int missedBranches = lines.stream().mapToInt(LineCoverage::missedBranches).sum();
        assertEquals(1, coveredBranches);
        assertEquals(3, missedBranches);

        // Line 12 (mi=2, ci=1): counted by jacoco-line, excluded by strict-line.
        LineCoverage partiallyCovered = lines.stream().filter(l -> l.number() == 12).findFirst().orElseThrow();
        assertTrue(partiallyCovered.isCovered());
        assertTrue(!partiallyCovered.isFullyCovered());
    }

    @Test
    void parsesEmptyReportWithZeroExecutableLines() {
        JacocoReport report = parser.parse(FIXTURES.resolve("empty-report.xml"));
        assertEquals(1, report.sourceFiles().size());
        assertTrue(report.sourceFiles().get(0).lines().isEmpty());
        assertEquals(0, report.totalLineMissed());
        assertEquals(0, report.totalLineCovered());
    }

    /**
     * Three fixtures, three distinct rejection mechanisms, same observable
     * contract (structured failure, never a partial parse):
     * <ul>
     *   <li>{@code malformed.xml} - plain XML well-formedness failure.</li>
     *   <li>{@code xxe.xml} - {@code <report name="&xxe;">} puts the external
     *       entity reference in an ATTRIBUTE value, which XML 1.0 forbids
     *       outright; Xerces rejects it before parsing ever reaches
     *       IS_SUPPORTING_EXTERNAL_ENTITIES, let alone fetches anything.</li>
     *   <li>{@code xxe-content.xml} - the same entity in ELEMENT CONTENT
     *       instead, which XML 1.0 does allow syntactically; this is the
     *       fixture that actually exercises the ENTITY_REFERENCE event check
     *       in {@link JacocoXmlParser#skipSubtree}, not just XML's own
     *       attribute-value rule.</li>
     *   <li>{@code wrong-root-element.xml} - well-formed XML whose root is
     *       not {@code <report>} at all.</li>
     *   <li>{@code missing-line-attribute.xml} / {@code
     *       non-numeric-line-attribute.xml} - a {@code <line>} missing a
     *       required attribute, or one that isn't an integer.</li>
     * </ul>
     * Per SECURITY-POLICY.md #1, both entity cases are reclassified from the
     * generic parse-failure code to the more specific one. (Not covered here:
     * the "no &lt;report&gt; root element found" fallback after the read
     * loop - every well-formed XML document has exactly one root element, so
     * that branch cannot be reached without a real parser bug; a document
     * with zero elements fails as MALFORMED_JACOCO_XML from Xerces itself,
     * in the outer catch, before this loop even starts.)
     */
    @ParameterizedTest
    @CsvSource({
        "malformed.xml,               MALFORMED_JACOCO_XML",
        "xxe.xml,                     XML_ENTITY_REFERENCE_REJECTED",
        "xxe-content.xml,             XML_ENTITY_REFERENCE_REJECTED",
        "wrong-root-element.xml,      MALFORMED_JACOCO_XML",
        "missing-line-attribute.xml,  MALFORMED_JACOCO_XML",
        "non-numeric-line-attribute.xml, MALFORMED_JACOCO_XML"
    })
    void rejectsBadXmlStructurallyWithoutFetchingOrExpandingEntities(String fixtureName, String expectedCode) {
        Path file = FIXTURES.resolve(fixtureName.trim());
        AnalysisException e = assertThrows(AnalysisException.class, () -> parser.parse(file));
        assertEquals(expectedCode.trim(), e.code());
    }

    /**
     * The two {@code <line>}-attribute fixtures above share one exception
     * code, which lets a mutant that fires the wrong one of intAttr's two
     * branches (missing vs. non-numeric) go unnoticed (PSEUDO_TESTED_METHOD,
     * self-scan) - the message text is what actually distinguishes them.
     */
    @Test
    void missingAndNonNumericLineAttributesProduceDistinctMessages() {
        Path missingFixture = FIXTURES.resolve("missing-line-attribute.xml");
        AnalysisException missing = assertThrows(AnalysisException.class, () -> parser.parse(missingFixture));
        assertTrue(missing.getMessage().contains("Missing required attribute 'ci'"), missing.getMessage());

        Path nonNumericFixture = FIXTURES.resolve("non-numeric-line-attribute.xml");
        AnalysisException nonNumeric = assertThrows(AnalysisException.class, () -> parser.parse(nonNumericFixture));
        assertTrue(nonNumeric.getMessage().contains("'mi'"), nonNumeric.getMessage());
        assertTrue(nonNumeric.getMessage().contains("not an integer"), nonNumeric.getMessage());
        assertTrue(nonNumeric.getMessage().contains("not-a-number"), nonNumeric.getMessage());
    }

    @Test
    void preservesUnicodeAndSpacesInPackageAndFileNames() {
        JacocoReport report = parser.parse(FIXTURES.resolve("unicode-and-spaces.xml"));
        SourceFileReport file = report.sourceFiles().get(0);
        assertEquals("com/exämple/wëird pkg", file.packageName());
        assertEquals("Ünïcödé File.java", file.fileName());
        assertEquals(2, file.lines().size());
    }

    /**
     * This fixture's {@code <line>} elements omit {@code mb}/{@code cb}
     * entirely - JaCoCo's own convention for "no branch on this line", which
     * must default to 0, not fail (PSEUDO_TESTED_METHOD, self-scan: no test
     * asserted on the branch counts for this fixture, only its names/count).
     */
    @Test
    void aLineWithNoBranchAttributesDefaultsBothBranchCountsToZero() {
        JacocoReport report = parser.parse(FIXTURES.resolve("unicode-and-spaces.xml"));
        SourceFileReport file = report.sourceFiles().get(0);
        for (LineCoverage line : file.lines()) {
            assertEquals(0, line.missedBranches(), line.toString());
            assertEquals(0, line.coveredBranches(), line.toString());
        }
    }

    @Test
    void crlfLineEndingsParseTheSameAsLf() throws IOException {
        // Built in-memory rather than as a checked-in fixture: .gitattributes
        // normalizes everything under fixtures/** to LF on commit (D-22), so
        // a checked-in CRLF file would lose the very line endings this test
        // exists to exercise (M1c criterion 7).
        String lfText = Files.readString(FIXTURES.resolve("mixed-coverage.xml"));
        String crlfText = lfText.replace("\n", "\r\n");
        Path crlfFile = tempDir.resolve("mixed-coverage-crlf.xml");
        Files.writeString(crlfFile, crlfText);

        JacocoReport lfReport = parser.parse(FIXTURES.resolve("mixed-coverage.xml"));
        JacocoReport crlfReport = parser.parse(crlfFile);

        assertEquals(lfReport.totalLineMissed(), crlfReport.totalLineMissed());
        assertEquals(lfReport.totalLineCovered(), crlfReport.totalLineCovered());
        assertEquals(lfReport.sourceFiles().get(0).lines(), crlfReport.sourceFiles().get(0).lines());
    }

    @Test
    void anXxeAttemptNeverReadsTheTargetFileItPointsAt() throws IOException {
        // Proof, not just a code assertion: build a marker file with unique
        // content at test time, point an entity at it, and confirm the
        // marker never surfaces anywhere in the failure - not just that
        // some AnalysisException was thrown (SECURITY-POLICY.md #1: "never a
        // file read, never a network attempt").
        String marker = "PROOF-XXE-CANARY-4f9d2b";
        Path secretFile = tempDir.resolve("secret.txt");
        Files.writeString(secretFile, marker);
        String secretUri = secretFile.toUri().toString();

        Path xmlFile = tempDir.resolve("xxe-live.xml");
        Files.writeString(xmlFile, String.join("\n",
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>",
            "<!DOCTYPE report [",
            "  <!ENTITY xxe SYSTEM \"" + secretUri + "\">",
            "]>",
            "<report name=\"ok\">",
            "  <injected>&xxe;</injected>",
            "  <package name=\"com/example\"/>",
            "</report>",
            ""));

        AnalysisException e = assertThrows(AnalysisException.class, () -> parser.parse(xmlFile));

        assertEquals("XML_ENTITY_REFERENCE_REJECTED", e.code());
        assertFalse(e.getMessage().contains(marker), e.getMessage());
    }

    @Test
    void rejectsAReportLargerThanTheConfiguredByteCap() {
        // A real 256 MB fixture is unwritable in a test; inject a cap small
        // enough that this repo's own real fixture trips it (SECURITY-POLICY.md #2).
        JacocoXmlParser cappedParser = new JacocoXmlParser(100);
        Path file = FIXTURES.resolve("mixed-coverage.xml");
        AnalysisException e = assertThrows(AnalysisException.class, () -> cappedParser.parse(file));
        assertEquals("REPORT_TOO_LARGE", e.code());
    }

    @Test
    void duplicateFixturesEachParseIndependently() {
        // The "same class in two reports" rejection is module-binding logic
        // (next pipeline stage), not the parser's job - the parser just
        // reads each file faithfully. This is the input those tests consume.
        JacocoReport a = parser.parse(FIXTURES.resolve("duplicate-a.xml"));
        JacocoReport b = parser.parse(FIXTURES.resolve("duplicate-b.xml"));
        assertEquals("com/example/Dup.java", a.sourceFiles().get(0).packageQualifiedPath());
        assertEquals("com/example/Dup.java", b.sourceFiles().get(0).packageQualifiedPath());
    }
}
