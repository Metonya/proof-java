package dev.proofjava.analysis.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

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
}
