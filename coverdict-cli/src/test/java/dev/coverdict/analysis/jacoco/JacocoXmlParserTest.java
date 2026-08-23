package dev.coverdict.analysis.jacoco;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.coverdict.analysis.AnalysisException;

/**
 * Runs against the checked-in fixtures under fixtures/jacoco/ (repo root),
 * one level above this module's basedir - Surefire's default working
 * directory is the module basedir.
 */
class JacocoXmlParserTest {

    private static final Path FIXTURES = Path.of("../fixtures/jacoco");

    private final JacocoXmlParser parser = new JacocoXmlParser();

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

    @Test
    void rejectsMalformedXmlStructurally() {
        AnalysisException e = assertThrows(AnalysisException.class,
            () -> parser.parse(FIXTURES.resolve("malformed.xml")));
        assertEquals("MALFORMED_JACOCO_XML", e.code());
    }

    @Test
    void rejectsExternalEntityReferenceWithoutFetchingOrExpandingIt() {
        // xxe.xml's <report name="&xxe;"> references an external entity
        // pointing at a local file, in an attribute value - which XML 1.0
        // forbids outright, so Xerces rejects it as a well-formedness error
        // before any fetch attempt (never reaching IS_SUPPORTING_EXTERNAL_
        // ENTITIES). The parser reclassifies that failure to this specific
        // code per SECURITY-POLICY.md #1, instead of a generic parse error.
        AnalysisException e = assertThrows(AnalysisException.class,
            () -> parser.parse(FIXTURES.resolve("xxe.xml")));
        assertEquals("XML_ENTITY_REFERENCE_REJECTED", e.code());
    }

    @Test
    void rejectsEntityReferenceEncounteredInSkippedElementContent() {
        // xxe-content.xml exercises the actual ENTITY_REFERENCE event check
        // in skipSubtree - the code path xxe.xml's attribute-value case
        // never reaches, since that one fails during XML well-formedness
        // parsing before any stream event is even emitted for the element.
        AnalysisException e = assertThrows(AnalysisException.class,
            () -> parser.parse(FIXTURES.resolve("xxe-content.xml")));
        assertEquals("XML_ENTITY_REFERENCE_REJECTED", e.code());
    }

    @Test
    void preservesUnicodeAndSpacesInPackageAndFileNames() {
        JacocoReport report = parser.parse(FIXTURES.resolve("unicode-and-spaces.xml"));
        SourceFileReport file = report.sourceFiles().get(0);
        assertEquals("com/exämple/wëird pkg", file.packageName());
        assertEquals("Ünïcödé File.java", file.fileName());
        assertEquals(2, file.lines().size());
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
