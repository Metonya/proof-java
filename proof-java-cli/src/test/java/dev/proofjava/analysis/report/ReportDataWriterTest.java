package dev.proofjava.analysis.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.proofjava.analysis.model.LineRange;
import dev.proofjava.analysis.pertest.PerTestEntry;
import dev.proofjava.analysis.pertest.PerTestLine;

/**
 * {@link ReportDataWriter#simplifyParams} had zero test coverage before
 * this - found while extracting {@code parseOneParam} out of it (SonarQube
 * java:S3776). A JVM method descriptor's parameter list is compact and easy
 * to get subtly wrong (array-dimension prefixes, the object-type
 * terminator, primitive codes), so it is exercised directly rather than
 * only indirectly through a report-rendering fixture.
 */
class ReportDataWriterTest {

    @Test
    void noParametersRendersAnEmptyPairOfParens() {
        assertEquals("()", ReportDataWriter.simplifyParams("()V"));
    }

    @Test
    void everyPrimitiveCodeIsRenderedByItsJavaName() {
        assertEquals("(byte, char, double, float, int, long, short, boolean)",
            ReportDataWriter.simplifyParams("(BCDFIJSZ)V"));
    }

    @Test
    void anObjectTypeIsCutToItsSimpleName() {
        assertEquals("(String)", ReportDataWriter.simplifyParams("(Ljava/lang/String;)V"));
    }

    @Test
    void aNestedClassIsCutAtTheDollarSignNotJustTheLastSlash() {
        assertEquals("(Entry)", ReportDataWriter.simplifyParams("(Ljava/util/Map$Entry;)V"));
    }

    @Test
    void arrayDimensionsAreAppendedOncePerDimension() {
        assertEquals("(int[], String[][])",
            ReportDataWriter.simplifyParams("([I[[Ljava/lang/String;)V"));
    }

    @Test
    void multipleParametersAreCommaSeparatedInDeclaredOrder() {
        assertEquals("(int, String, boolean)",
            ReportDataWriter.simplifyParams("(ILjava/lang/String;Z)V"));
    }

    /** A descriptor with no parens at all is handed back unchanged rather than guessed at. */
    @Test
    void aDescriptorWithNoParensIsReturnedVerbatim() {
        String malformed = "not a descriptor";
        assertEquals(malformed, ReportDataWriter.simplifyParams(malformed));
    }

    /** An object type missing its ';' terminator stops parsing rather than reading past the end. */
    @Test
    void anUnterminatedObjectTypeStopsRatherThanThrowing() {
        assertEquals("()", ReportDataWriter.simplifyParams("(Ljava/lang/String)V"));
    }

    // ---- formatPercent/formatInt/formatRanges/countLines (PSEUDO_TESTED_METHOD, self-scan) ----

    @Test
    void formatPercentAppendsAPercentSign() {
        assertEquals("87.5%", ReportDataWriter.formatPercent(new BigDecimal("87.5")));
    }

    @Test
    void formatPercentOfNullIsNotAvailableRatherThanZero() {
        assertEquals("n/a", ReportDataWriter.formatPercent(null));
    }

    /** English formatting regardless of the host locale (the report is English-only). */
    @Test
    void formatIntGroupsThousandsWithACommaNotAPeriod() {
        assertEquals("1,234", ReportDataWriter.formatInt(1234));
    }

    @Test
    void formatIntBelowOneThousandHasNoSeparator() {
        assertEquals("42", ReportDataWriter.formatInt(42));
    }

    @Test
    void formatRangesOfNoRangesIsADash() {
        assertEquals("-", ReportDataWriter.formatRanges(List.of()));
    }

    @Test
    void formatRangesCollapsesASingleLineRangeToOneNumber() {
        assertEquals("5", ReportDataWriter.formatRanges(List.of(new LineRange(5, 5))));
    }

    @Test
    void formatRangesJoinsMultipleRangesWithACommaAndSpace() {
        assertEquals("3-5, 9",
            ReportDataWriter.formatRanges(List.of(new LineRange(3, 5), new LineRange(9, 9))));
    }

    @Test
    void countLinesSumsLinesAcrossEveryEntry() {
        List<PerTestEntry> entries = List.of(
            new PerTestEntry("com.example.Foo", "bar", List.of(
                new PerTestLine(10, List.of("t1")), new PerTestLine(11, List.of("t1")))),
            new PerTestEntry("com.example.Foo", "baz", List.of(new PerTestLine(20, List.of("t2")))));

        assertEquals(3, ReportDataWriter.countLines(entries));
    }

    @Test
    void countLinesOfNoEntriesIsZero() {
        assertEquals(0, ReportDataWriter.countLines(List.of()));
    }
}
