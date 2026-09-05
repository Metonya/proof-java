package dev.proofjava.analysis.jacoco;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import dev.proofjava.analysis.AnalysisException;

/**
 * Parses JaCoCo XML reports into {@link JacocoReport}. Reads only
 * {@code <package>}, {@code <sourcefile>}, {@code <line>}, and the LINE
 * {@code <counter>} at sourcefile/report level; {@code <class>}/{@code
 * <method>} subtrees and {@code <sessioninfo>} are structurally skipped, not
 * read - JaCoCo reports carry them but this analyzer's metrics don't need
 * method-level detail.
 *
 * <p>Follows SECURITY-POLICY.md #1: JaCoCo's own {@code <!DOCTYPE report
 * PUBLIC ... "report.dtd">} line is tolerated (well-formed XML requires
 * DOCTYPE syntax be accepted) but never processed - external entities and
 * the external DTD subset are never fetched, and any entity reference
 * encountered in content is a structured failure, not a silent pass-through.
 *
 * <p>SECURITY-POLICY.md #2: a report larger than {@link #maxReportBytes} is
 * rejected before the file is opened for streaming - {@code Files.size} costs
 * one stat call, far cheaper than starting a StAX parse only to fail deep
 * inside an attacker-sized document. Configurable only via the constructor
 * (no {@code --config} surface yet - see INPUT-MODEL.md).
 */
public final class JacocoXmlParser {

    private static final long DEFAULT_MAX_REPORT_BYTES = 256L * 1024 * 1024;

    private final long maxReportBytes;

    public JacocoXmlParser() {
        this(DEFAULT_MAX_REPORT_BYTES);
    }

    /** @param maxReportBytes test-only hook to exercise the cap without writing an attacker-sized fixture. */
    JacocoXmlParser(long maxReportBytes) {
        this.maxReportBytes = maxReportBytes;
    }

    public JacocoReport parse(Path xmlFile) {
        try {
            long size = Files.size(xmlFile);
            if (size > maxReportBytes) {
                throw new AnalysisException("REPORT_TOO_LARGE",
                    "JaCoCo report " + xmlFile + " is " + size + " bytes, exceeding the " + maxReportBytes
                        + "-byte limit (SECURITY-POLICY.md #2).");
            }
        } catch (IOException e) {
            throw new AnalysisException("UNREADABLE_REPORT",
                "Could not read JaCoCo report " + xmlFile + ": " + e.getMessage(), e);
        }
        try (InputStream in = Files.newInputStream(xmlFile)) {
            return parse(in, xmlFile.toString());
        } catch (IOException e) {
            throw new AnalysisException("UNREADABLE_REPORT",
                "Could not read JaCoCo report " + xmlFile + ": " + e.getMessage(), e);
        }
    }

    public JacocoReport parse(InputStream in, String sourceLabel) {
        XMLStreamReader reader = null;
        try {
            reader = secureFactory().createXMLStreamReader(in);
            return readReport(reader, sourceLabel);
        } catch (XMLStreamException e) {
            // XML 1.0 itself forbids an external entity reference inside an
            // attribute value (e.g. <report name="&xxe;">), so Xerces raises
            // this as a well-formedness error rather than the stream-level
            // ENTITY_REFERENCE event the read loop below watches for (that
            // event type is only emitted for a reference inside element
            // content). Both are "entity reference rejected before any
            // external fetch"; reclassify by message so both surface the
            // same, more specific code instead of a generic parse failure.
            String code = isEntityRelated(e.getMessage()) ? "XML_ENTITY_REFERENCE_REJECTED" : "MALFORMED_JACOCO_XML";
            throw new AnalysisException(code,
                "Malformed JaCoCo XML in " + sourceLabel + ": " + e.getMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException ignored) {
                    // best-effort close; nothing meaningful to report
                }
            }
        }
    }

    /**
     * OWASP StAX XXE guidance, documented as safe without a custom resolver:
     * SUPPORT_DTD=true (a DOCTYPE declaration does not itself fail parsing -
     * required to tolerate JaCoCo's own "report.dtd" DOCTYPE line) combined
     * with IS_SUPPORTING_EXTERNAL_ENTITIES=false (neither the external DTD
     * subset nor an external general entity is ever fetched).
     * IS_REPLACING_ENTITY_REFERENCES=false additionally stops internal
     * entities from being substituted into content; the read loop below
     * then treats any residual ENTITY_REFERENCE event as a hard failure.
     * A custom XMLResolver was tried and rejected: Xerces calls it even to
     * resolve the harmless "report.dtd" external subset reference, so a
     * resolver that throws breaks the very DOCTYPE tolerance this method
     * exists to provide - the three properties alone are sufficient and
     * correctly scoped.
     */
    private static XMLInputFactory secureFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, true);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        return factory;
    }

    private JacocoReport readReport(XMLStreamReader r, String sourceLabel) throws XMLStreamException {
        ReportAccumulator acc = new ReportAccumulator();
        while (r.hasNext()) {
            int event = r.next();
            if (event == XMLStreamConstants.ENTITY_REFERENCE) {
                throw entityReferenceRejected(r, sourceLabel);
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                acc.handleStartElement(r, sourceLabel);
            }
            // other events (whitespace, comments, sessioninfo start/end) are
            // consumed via skipSubtree when encountered as a child - nothing
            // to do for them at the top level.
        }
        if (!acc.sawReportElement) {
            throw new AnalysisException("MALFORMED_JACOCO_XML", "No <report> root element found in " + sourceLabel);
        }
        return new JacocoReport(acc.reportName == null ? "" : acc.reportName, acc.sourceFiles, acc.totalMissed, acc.totalCovered);
    }

    /** Mutable accumulator for {@link #readReport}, so the read loop itself stays a flat dispatch instead of nested state-tracking. */
    private final class ReportAccumulator {
        private String reportName;
        private final List<SourceFileReport> sourceFiles = new ArrayList<>();
        private int totalMissed;
        private int totalCovered;
        private boolean sawReportElement;

        void handleStartElement(XMLStreamReader r, String sourceLabel) throws XMLStreamException {
            String local = r.getLocalName();
            if (!sawReportElement) {
                if (!"report".equals(local)) {
                    throw new AnalysisException("MALFORMED_JACOCO_XML",
                        "Expected root element <report> in " + sourceLabel + ", found <" + local + ">");
                }
                sawReportElement = true;
                reportName = attr(r, "name");
            } else if ("package".equals(local)) {
                sourceFiles.addAll(readPackage(r, attrOrEmpty(r, "name"), sourceLabel));
            } else if ("counter".equals(local) && "LINE".equals(attr(r, "type"))) {
                totalMissed = intAttr(r, "missed");
                totalCovered = intAttr(r, "covered");
            } else {
                skipSubtree(r, sourceLabel);
            }
        }

        /** Moved here from the outer class (SonarQube java:S3398) - this was already its only caller. */
        private List<SourceFileReport> readPackage(XMLStreamReader r, String packageName, String sourceLabel) throws XMLStreamException {
            List<SourceFileReport> result = new ArrayList<>();
            // Uniform depth rule for this whole class: every START_ELEMENT we
            // observe here increments depth by one, and either a later
            // END_ELEMENT decrements it back (self-closing elements like <line>
            // and <counter> still emit a separate END_ELEMENT in StAX - there is
            // no distinct "empty element" event), or - when we hand the element
            // off to a subtree-consuming call (skipSubtree/readSourceFile) that
            // reads through and including its own END_ELEMENT - we decrement
            // immediately to compensate, since no separate END_ELEMENT event for
            // it will reach this loop.
            int depth = 1;
            while (depth > 0) {
                int event = r.next();
                switch (event) {
                    case XMLStreamConstants.ENTITY_REFERENCE -> throw entityReferenceRejected(r, sourceLabel);
                    case XMLStreamConstants.START_ELEMENT -> {
                        depth++;
                        String local = r.getLocalName();
                        if ("sourcefile".equals(local)) {
                            result.add(readSourceFile(r, packageName, attrOrEmpty(r, "name"), sourceLabel));
                            depth--; // readSourceFile already consumed through </sourcefile>
                        } else {
                            skipSubtree(r, sourceLabel);
                            depth--; // skipSubtree already consumed through its own end tag
                        }
                    }
                    case XMLStreamConstants.END_ELEMENT -> depth--;
                    default -> { /* ignore */ }
                }
            }
            return result;
        }
    }

    private SourceFileReport readSourceFile(XMLStreamReader r, String packageName, String fileName, String sourceLabel) throws XMLStreamException {
        List<LineCoverage> lines = new ArrayList<>();
        int reportedMissed = 0;
        int reportedCovered = 0;
        int depth = 1;
        while (depth > 0) {
            int event = r.next();
            switch (event) {
                case XMLStreamConstants.ENTITY_REFERENCE -> throw entityReferenceRejected(r, sourceLabel);
                case XMLStreamConstants.START_ELEMENT -> {
                    depth++;
                    String local = r.getLocalName();
                    if ("line".equals(local)) {
                        lines.add(new LineCoverage(
                            intAttr(r, "nr"),
                            intAttr(r, "mi"),
                            intAttr(r, "ci"),
                            intAttrOrZero(r, "mb"),
                            intAttrOrZero(r, "cb")
                        ));
                        // <line> has no children; its own END_ELEMENT arrives
                        // on the next iteration and decrements depth, same
                        // as any other element - no special-casing needed.
                    } else if ("counter".equals(local) && "LINE".equals(attr(r, "type"))) {
                        reportedMissed = intAttr(r, "missed");
                        reportedCovered = intAttr(r, "covered");
                    } else {
                        skipSubtree(r, sourceLabel);
                        depth--; // skipSubtree already consumed through its own end tag
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> depth--;
                default -> { /* ignore */ }
            }
        }
        return new SourceFileReport(packageName, fileName, lines, reportedMissed, reportedCovered);
    }

    /** Called right after the START_ELEMENT of the element to discard. */
    private void skipSubtree(XMLStreamReader r, String sourceLabel) throws XMLStreamException {
        int depth = 1;
        while (depth > 0) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            } else if (event == XMLStreamConstants.ENTITY_REFERENCE) {
                throw entityReferenceRejected(r, sourceLabel);
            }
        }
    }

    private static AnalysisException entityReferenceRejected(XMLStreamReader r, String sourceLabel) {
        String name;
        try {
            name = r.getLocalName();
        } catch (RuntimeException e) {
            name = "?";
        }
        return new AnalysisException("XML_ENTITY_REFERENCE_REJECTED",
            "Entity reference '&" + name + ";' encountered in " + sourceLabel + " and rejected (SECURITY-POLICY.md #1).");
    }

    private static boolean isEntityRelated(String message) {
        return message != null && message.toLowerCase(Locale.ROOT).contains("entity");
    }

    private static String attr(XMLStreamReader r, String name) {
        return r.getAttributeValue(null, name);
    }

    private static String attrOrEmpty(XMLStreamReader r, String name) {
        String v = attr(r, name);
        return v == null ? "" : v;
    }

    private static int intAttr(XMLStreamReader r, String name) {
        String v = attr(r, name);
        if (v == null) {
            throw new AnalysisException("MALFORMED_JACOCO_XML",
                "Missing required attribute '" + name + "' on <" + r.getLocalName() + ">");
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new AnalysisException("MALFORMED_JACOCO_XML",
                "Attribute '" + name + "' on <" + r.getLocalName() + "> is not an integer: '" + v + "'");
        }
    }

    /** mb/cb are omitted by JaCoCo when a line has no branch; absence means 0, not an error. */
    private static int intAttrOrZero(XMLStreamReader r, String name) {
        String v = attr(r, name);
        return v == null ? 0 : Integer.parseInt(v);
    }
}
