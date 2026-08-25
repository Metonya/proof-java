package dev.coverdict.analysis.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.coverdict.analysis.metrics.MetricsEngine;
import dev.coverdict.analysis.model.AnalysisReason;
import dev.coverdict.analysis.model.Confidence;
import dev.coverdict.analysis.model.Finding;
import dev.coverdict.analysis.model.Severity;

/** SECURITY-POLICY.md #4: a control character in any rendered field never reaches the terminal raw. */
class TextRendererTest {

    private static final char ESC = 0x1b;
    private static final char BEL = 0x07;
    // Built by concatenation, not a ""-style literal - Java's unicode
    // preprocessing runs on raw source text before tokenizing, so a literal
    // backslash immediately followed by 'u' in source is translated into the
    // escape target itself, not left as six literal characters.
    private static final String ESCAPED_ESC = "\\" + "u001b";
    private static final String ESCAPED_BEL = "\\" + "u0007";

    private ModuleInput module() {
        return new ModuleInput("root", ".", List.of("src/main/java"), List.of("src/test/java"), List.of());
    }

    @Test
    void controlCharactersInAFindingPathAndMessageAreEscapedNotRenderedRaw() {
        String maliciousPath = "src/test/java/com/example/" + ESC + "[31mEvilTest.java";
        String maliciousMessage = "message with a bell " + BEL + " in it";
        Finding finding = new Finding("NO_RECOGNIZED_ORACLE", Severity.WARNING, Confidence.HIGH, "root",
            maliciousPath, 1, 1, "com.example.EvilTest#m()", maliciousMessage, "suggestion", "0123456789abcdef", null);

        VerdictDocument doc = new VerdictDocument("0.1.0", "0.1.0-TEST", true, List.of(),
            17, "UTF-8", List.of(), List.of(module()), "no-vcs", "all", null, MetricsEngine.compute(List.of()),
            NewCodeCoverage.unavailable("unavailable_no_vcs"), List.of(), List.of(finding), List.of());

        String rendered = TextRenderer.render(doc);

        assertFalse(rendered.indexOf(ESC) >= 0, rendered);
        assertFalse(rendered.indexOf(BEL) >= 0, rendered);
        assertTrue(rendered.contains(ESCAPED_ESC), rendered);
        assertTrue(rendered.contains(ESCAPED_BEL), rendered);
    }

    @Test
    void controlCharactersInAnIncompleteReasonAreEscaped() {
        AnalysisReason reason = new AnalysisReason("SOME_CODE", "bad " + ESC + "[0m message");
        VerdictDocument doc = new VerdictDocument("0.1.0", "0.1.0-TEST", false, List.of(reason),
            17, "UTF-8", List.of(), List.of(), "no-vcs", "all", null, MetricsEngine.compute(List.of()),
            NewCodeCoverage.unavailable("unavailable_no_vcs"), List.of(), List.of(), List.of());

        String rendered = TextRenderer.render(doc);

        assertFalse(rendered.indexOf(ESC) >= 0, rendered);
    }
}
