package dev.proofjava.analysis.report;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import dev.proofjava.analysis.jacoco.LineCoverage;
import dev.proofjava.analysis.metrics.Metric;
import dev.proofjava.analysis.metrics.MetricSet;
import dev.proofjava.analysis.model.AnalysisReason;
import dev.proofjava.analysis.model.ChangedFile;
import dev.proofjava.analysis.model.Classification;
import dev.proofjava.analysis.model.Confidence;
import dev.proofjava.analysis.model.Finding;
import dev.proofjava.analysis.model.LineRange;
import dev.proofjava.analysis.model.Severity;
import dev.proofjava.analysis.mutation.Mutant;
import dev.proofjava.analysis.mutation.MutatedMethod;
import dev.proofjava.analysis.mutation.MutationModuleEvidence;
import dev.proofjava.analysis.pertest.PerTestEntry;
import dev.proofjava.analysis.pertest.PerTestLine;
import dev.proofjava.analysis.pertest.PerTestModuleEvidence;
import dev.proofjava.analysis.vcs.VcsIdentity;

/**
 * Reads a verdict JSON matching {@code schema/proof-verdict.schema.json}
 * back into a {@link VerdictDocument} - the read-side counterpart of {@link
 * VerdictJsonWriter}, for {@code render-html}'s "render an already-produced
 * document, no fresh analysis" mode (D-78). Field order in the input does
 * not matter here (unlike the writer, which guarantees a specific order on
 * write) - this is a plain object-of-fields reader, never required to
 * reproduce byte-identical output.
 *
 * <p>Throws {@link VerdictJsonReadException} (never guesses a default, never
 * silently drops a malformed value into a zero/empty) when a required field
 * is missing or the wrong shape - the same hard-rule-3a posture every other
 * input-parsing surface in this codebase takes for untrusted/unexpected
 * input.
 */
public final class VerdictJsonReader {

    /** JSON field names repeated across several of this reader's block parsers (SonarQube java:S1192). */
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_MODULE = "module";
    private static final String FIELD_MODULES = "modules";
    private static final String FIELD_CLASS_NAME = "className";
    private static final String FIELD_METHOD_NAME = "methodName";

    private VerdictJsonReader() {
    }

    public static final class VerdictJsonReadException extends RuntimeException {
        public VerdictJsonReadException(String message) {
            super(message);
        }

        public VerdictJsonReadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static VerdictDocument read(InputStream in) throws IOException {
        JsonFactory factory = new JsonFactory();
        try (JsonParser p = factory.createParser(in)) {
            if (p.nextToken() != JsonToken.START_OBJECT) {
                throw new VerdictJsonReadException("expected a JSON object at the document root");
            }

            String schemaVersion = null;
            String toolVersion = null;
            boolean complete = false;
            List<AnalysisReason> incompleteReasons = List.of();
            String diffMode = null;
            String findingsScope = null;
            VcsIdentity identity = null;
            int languageLevel = 0;
            String encoding = null;
            List<String> exclusions = List.of();
            List<ModuleInput> modules = List.of();
            MetricSet overallMetrics = null;
            NewCodeCoverage newCode = null;
            List<ChangedFile> changedFiles = List.of();
            List<Finding> findings = List.of();
            List<AnalysisReason> warnings = List.of();
            List<PerTestModuleEvidence> perTest = null;
            List<MutationModuleEvidence> mutation = null;
            FileCoverageBlock fileCoverage = null;

            while (p.nextToken() != JsonToken.END_OBJECT) {
                String field = requireFieldName(p);
                p.nextToken();
                switch (field) {
                    case "schemaVersion" -> schemaVersion = p.getText();
                    case "tool" -> toolVersion = readToolVersion(p);
                    case "analysis" -> {
                        AnalysisBlock block = readAnalysisBlock(p);
                        complete = block.complete;
                        incompleteReasons = block.reasons;
                    }
                    case "inputs" -> {
                        InputsBlock block = readInputsBlock(p);
                        diffMode = block.diffMode;
                        findingsScope = block.findingsScope;
                        identity = block.identity;
                        languageLevel = block.languageLevel;
                        encoding = block.encoding;
                        exclusions = block.exclusions;
                        modules = block.modules;
                    }
                    case "coverage" -> {
                        CoverageBlock block = readCoverageBlock(p);
                        overallMetrics = block.overall;
                        newCode = block.newCode;
                    }
                    case "changedFiles" -> changedFiles = readArray(p, VerdictJsonReader::readChangedFile);
                    case "findings" -> findings = readArray(p, VerdictJsonReader::readFinding);
                    case "warnings" -> warnings = readArray(p, VerdictJsonReader::readReason);
                    case "perTest" -> perTest = readPerTestBlock(p);
                    case "mutation" -> mutation = readMutationBlock(p);
                    case "fileCoverage" -> fileCoverage = readFileCoverageBlock(p);
                    default -> p.skipChildren();
                }
            }

            requireNonNull(schemaVersion, "schemaVersion");
            requireNonNull(toolVersion, "tool.version");
            requireNonNull(diffMode, "inputs.diffMode");
            requireNonNull(findingsScope, "inputs.findingsScope");
            requireNonNull(encoding, "inputs.encoding");
            requireNonNull(overallMetrics, "coverage.overall");
            requireNonNull(newCode, "coverage.newCode");

            return new VerdictDocument(schemaVersion, toolVersion, complete, incompleteReasons, languageLevel,
                encoding, exclusions, modules, diffMode, findingsScope, identity, overallMetrics, newCode,
                changedFiles, findings, warnings, perTest, mutation, fileCoverage);
        }
    }

    private record AnalysisBlock(boolean complete, List<AnalysisReason> reasons) {
    }

    private static AnalysisBlock readAnalysisBlock(JsonParser p) throws IOException {
        expectObjectStart(p);
        boolean complete = false;
        List<AnalysisReason> reasons = List.of();
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = requireFieldName(p);
            p.nextToken();
            switch (field) {
                case FIELD_STATUS -> complete = "complete".equals(p.getText());
                case "incompleteReasons" -> reasons = readArray(p, VerdictJsonReader::readReason);
                default -> p.skipChildren();
            }
        }
        return new AnalysisBlock(complete, reasons);
    }

    private record InputsBlock(String diffMode, String findingsScope, String baseRef, VcsIdentity identity,
                                int languageLevel, String encoding, List<String> exclusions, List<ModuleInput> modules) {
    }

    private static InputsBlock readInputsBlock(JsonParser p) throws IOException {
        expectObjectStart(p);
        String diffMode = null;
        String findingsScope = null;
        String baseRef = null;
        VcsIdentity identity = null;
        int languageLevel = 0;
        String encoding = null;
        List<String> exclusions = List.of();
        List<ModuleInput> modules = List.of();
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = requireFieldName(p);
            p.nextToken();
            switch (field) {
                case "diffMode" -> diffMode = p.getText();
                case "findingsScope" -> findingsScope = p.getText();
                case "baseRef" -> baseRef = p.getText();
                case "resolved" -> identity = readResolvedIdentity(p, baseRef);
                case "languageLevel" -> languageLevel = p.getIntValue();
                case "encoding" -> encoding = p.getText();
                case "exclusions" -> exclusions = readArray(p, JsonParser::getText);
                case FIELD_MODULES -> modules = readArray(p, VerdictJsonReader::readModule);
                default -> p.skipChildren();
            }
        }
        // baseRef may arrive after `resolved` in a hand-composed document even though the CLI's
        // own writer always orders it first - re-attach it if identity was built too early.
        if (identity != null && baseRef != null && identity.baseRef() == null) {
            identity = new VcsIdentity(identity.head(), baseRef, identity.base(), identity.mergeBase(), identity.dirty());
        }
        return new InputsBlock(diffMode, findingsScope, baseRef, identity, languageLevel, encoding, exclusions, modules);
    }

    private static VcsIdentity readResolvedIdentity(JsonParser p, String baseRef) throws IOException {
        expectObjectStart(p);
        String base = null;
        String mergeBase = null;
        String head = null;
        boolean dirty = false;
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = requireFieldName(p);
            p.nextToken();
            switch (field) {
                case "base" -> base = p.getText();
                case "mergeBase" -> mergeBase = p.getText();
                case "head" -> head = p.getText();
                case "dirty" -> dirty = p.getBooleanValue();
                default -> p.skipChildren();
            }
        }
        return new VcsIdentity(head, baseRef, base, mergeBase, dirty);
    }

    private static ModuleInput readModule(JsonParser p) throws IOException {
        expectObjectStart(p);
        String id = null;
        String root = null;
        List<String> sourceRoots = List.of();
        List<String> testRoots = List.of();
        List<ReportInput> reports = List.of();
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = requireFieldName(p);
            p.nextToken();
            switch (field) {
                case "id" -> id = p.getText();
                case "root" -> root = p.getText();
                case "sourceRoots" -> sourceRoots = readArray(p, JsonParser::getText);
                case "testRoots" -> testRoots = readArray(p, JsonParser::getText);
                case "reports" -> reports = readArray(p, VerdictJsonReader::readReportInput);
                default -> p.skipChildren();
            }
        }
        return new ModuleInput(id, root, sourceRoots, testRoots, reports);
    }

    private static ReportInput readReportInput(JsonParser p) throws IOException {
        expectObjectStart(p);
        String path = null;
        String freshness = null;
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = requireFieldName(p);
            p.nextToken();
            switch (field) {
                case "path" -> path = p.getText();
                case "freshness" -> freshness = p.getText();
                default -> p.skipChildren();
            }
        }
        return new ReportInput(path, freshness);
    }

    private record CoverageBlock(MetricSet overall, NewCodeCoverage newCode) {
    }

    private static CoverageBlock readCoverageBlock(JsonParser p) throws IOException {
        expectObjectStart(p);
        MetricSet overall = null;
        NewCodeCoverage newCode = null;
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = requireFieldName(p);
            p.nextToken();
            switch (field) {
                case "overall" -> overall = readMetricSet(p);
                case "newCode" -> newCode = readNewCode(p);
                default -> p.skipChildren();
            }
        }
        return new CoverageBlock(overall, newCode);
    }

    /** {@code newCode} is a schema {@code oneOf}: a real {@link MetricSet} (has an engine-line mode etc.), or {@code {status: "unavailable_*"}}. Distinguished by which fields actually appear - never guessed from context. */
    private static NewCodeCoverage readNewCode(JsonParser p) throws IOException {
        expectObjectStart(p);
        String status = null;
        String engineModeId = null;
        Metric engineLine = null;
        Metric strictLine = null;
        Metric sonarCompatible = null;
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = requireFieldName(p);
            p.nextToken();
            switch (field) {
                case FIELD_STATUS -> status = p.getText();
                case MetricSet.JACOCO_LINE, MetricSet.COVERAGE_LINE -> {
                    engineModeId = field;
                    engineLine = readMetric(p);
                }
                case MetricSet.STRICT_LINE -> strictLine = readMetric(p);
                case MetricSet.SONAR_COMPATIBLE -> sonarCompatible = readMetric(p);
                default -> p.skipChildren();
            }
        }
        if (status != null) {
            return NewCodeCoverage.unavailable(status);
        }
        return NewCodeCoverage.available(new MetricSet(
                engineModeId == null ? MetricSet.JACOCO_LINE : engineModeId,
                engineLine, strictLine, sonarCompatible));
    }

    /**
     * The first mode is named after the engine that produced the document, so
     * both spellings are accepted and the one actually seen is remembered -
     * {@code render-html} takes any file matching the schema (D-78), and a
     * sibling engine's verdict must render as itself rather than be relabelled
     * with this engine's name.
     */
    private static MetricSet readMetricSet(JsonParser p) throws IOException {
        expectObjectStart(p);
        String engineModeId = null;
        Metric engineLine = null;
        Metric strictLine = null;
        Metric sonarCompatible = null;
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = requireFieldName(p);
            p.nextToken();
            switch (field) {
                case MetricSet.JACOCO_LINE, MetricSet.COVERAGE_LINE -> {
                    engineModeId = field;
                    engineLine = readMetric(p);
                }
                case MetricSet.STRICT_LINE -> strictLine = readMetric(p);
                case MetricSet.SONAR_COMPATIBLE -> sonarCompatible = readMetric(p);
                default -> p.skipChildren();
            }
        }
        return new MetricSet(
                engineModeId == null ? MetricSet.JACOCO_LINE : engineModeId,
                engineLine, strictLine, sonarCompatible);
    }

    private static Metric readMetric(JsonParser p) throws IOException {
        expectObjectStart(p);
        String numeratorName = null;
        int numerator = 0;
        String denominatorName = null;
        int denominator = 0;
        BigDecimal percent = null;
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = requireFieldName(p);
            p.nextToken();
            switch (field) {
                case "numeratorName" -> numeratorName = p.getText();
                case "numerator" -> numerator = p.getIntValue();
                case "denominatorName" -> denominatorName = p.getText();
                case "denominator" -> denominator = p.getIntValue();
                case "percent" -> percent = p.currentToken() == JsonToken.VALUE_NULL ? null : p.getDecimalValue();
                default -> p.skipChildren();
            }
        }
        return new Metric(numeratorName, numerator, denominatorName, denominator, percent);
    }

    private static ChangedFile readChangedFile(JsonParser p) throws IOException {
        expectObjectStart(p);
        String path = null;
        String module = null;
        Classification classification = null;
        Integer newLines = null;
        Integer coveredNewLines = null;
        List<LineRange> uncoveredNewRanges = List.of();
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = requireFieldName(p);
            p.nextToken();
            switch (field) {
                case "path" -> path = p.getText();
                case FIELD_MODULE -> module = p.getText();
                case "classification" -> classification = classificationFromSchemaValue(p.getText());
                case "newLines" -> newLines = p.getIntValue();
                case "coveredNewLines" -> coveredNewLines = p.getIntValue();
                case "uncoveredNewRanges" -> uncoveredNewRanges = readArray(p, VerdictJsonReader::readLineRange);
                default -> p.skipChildren();
            }
        }
        return new ChangedFile(path, module, classification, newLines, coveredNewLines, uncoveredNewRanges);
    }

    private static LineRange readLineRange(JsonParser p) throws IOException {
        expectArrayStart(p);
        p.nextToken();
        int start = p.getIntValue();
        p.nextToken();
        int end = p.getIntValue();
        expectToken(p, p.nextToken(), JsonToken.END_ARRAY);
        return new LineRange(start, end);
    }

    private static Classification classificationFromSchemaValue(String value) {
        for (Classification c : Classification.values()) {
            if (c.schemaValue().equals(value)) {
                return c;
            }
        }
        throw new VerdictJsonReadException("unrecognized changedFile classification: " + value);
    }

    private static Finding readFinding(JsonParser p) throws IOException {
        expectObjectStart(p);
        String rule = null;
        Severity severity = null;
        Confidence confidence = null;
        String module = null;
        String path = null;
        int startLine = 0;
        int endLine = 0;
        String testMethod = null;
        String productionMethod = null;
        String relatedTestMethod = null;
        String relatedPath = null;
        String message = null;
        String suggestedAction = null;
        String fingerprint = null;
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = requireFieldName(p);
            p.nextToken();
            switch (field) {
                case "rule" -> rule = p.getText();
                case "severity" -> severity = Severity.valueOf(p.getText());
                case "confidence" -> confidence = Confidence.valueOf(p.getText());
                case FIELD_MODULE -> module = p.getText();
                case "path" -> path = p.getText();
                case "startLine" -> startLine = p.getIntValue();
                case "endLine" -> endLine = p.getIntValue();
                case "testMethod" -> testMethod = p.getText();
                case "productionMethod" -> productionMethod = p.getText();
                case "relatedTestMethod" -> relatedTestMethod = p.getText();
                case "relatedPath" -> relatedPath = p.getText();
                case "message" -> message = p.getText();
                case "suggestedAction" -> suggestedAction = p.getText();
                case "fingerprint" -> fingerprint = p.getText();
                default -> p.skipChildren();
            }
        }
        return new Finding(rule, severity, confidence, module, path, startLine, endLine, testMethod, message,
            suggestedAction, fingerprint, productionMethod, relatedTestMethod, relatedPath);
    }

    private static AnalysisReason readReason(JsonParser p) throws IOException {
        expectObjectStart(p);
        String code = null;
        String message = null;
        String path = null;
        String module = null;
        Integer count = null;
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = requireFieldName(p);
            p.nextToken();
            switch (field) {
                case "code" -> code = p.getText();
                case "message" -> message = p.getText();
                case "path" -> path = p.getText();
                case FIELD_MODULE -> module = p.getText();
                case "count" -> count = p.getIntValue();
                default -> p.skipChildren();
            }
        }
        return new AnalysisReason(code, message, path, module, count);
    }

    private static List<PerTestModuleEvidence> readPerTestBlock(JsonParser p) throws IOException {
        expectObjectStart(p);
        List<PerTestModuleEvidence> modules = List.of();
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = requireFieldName(p);
            p.nextToken();
            if (FIELD_MODULES.equals(field)) {
                modules = readArray(p, VerdictJsonReader::readPerTestModule);
            } else {
                p.skipChildren();
            }
        }
        return modules;
    }

    /**
     * D-86: {@code tests} holds indexes into the module's own {@code testIds}
     * table, so entries are read raw and resolved once the whole module object
     * has been consumed - the table may appear before or after them.
     */
    private static PerTestModuleEvidence readPerTestModule(JsonParser p) throws IOException {
        expectObjectStart(p);
        String id = null;
        List<String> testIds = List.of();
        List<RawEntry> entries = List.of();
        List<RawEntry> ambient = List.of();
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = requireFieldName(p);
            p.nextToken();
            switch (field) {
                case "id" -> id = p.getText();
                case "testIds" -> testIds = readArray(p, JsonParser::getText);
                case "entries" -> entries = readArray(p, VerdictJsonReader::readRawPerTestEntry);
                case "ambient" -> ambient = readArray(p, VerdictJsonReader::readRawPerTestEntry);
                default -> p.skipChildren();
            }
        }
        return new PerTestModuleEvidence(id, resolve(entries, testIds), resolve(ambient, testIds));
    }

    private static List<PerTestEntry> resolve(List<RawEntry> raw, List<String> testIds) {
        return raw.stream()
            .map(entry -> new PerTestEntry(entry.className(), entry.methodName(),
                entry.lines().stream()
                    .map(line -> new PerTestLine(line.line(), line.testIndexes().stream()
                        .filter(i -> i >= 0 && i < testIds.size())
                        .map(testIds::get)
                        .toList()))
                    .toList()))
            .toList();
    }

    private static RawEntry readRawPerTestEntry(JsonParser p) throws IOException {
        expectObjectStart(p);
        String className = null;
        String methodName = null;
        List<RawLine> lines = List.of();
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = requireFieldName(p);
            p.nextToken();
            switch (field) {
                case FIELD_CLASS_NAME -> className = p.getText();
                case FIELD_METHOD_NAME -> methodName = p.getText();
                case "lines" -> lines = readArray(p, VerdictJsonReader::readRawPerTestLine);
                default -> p.skipChildren();
            }
        }
        return new RawEntry(className, methodName, lines);
    }

    private static RawLine readRawPerTestLine(JsonParser p) throws IOException {
        expectObjectStart(p);
        int line = 0;
        List<Integer> testIndexes = List.of();
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = requireFieldName(p);
            p.nextToken();
            switch (field) {
                case "line" -> line = p.getIntValue();
                case "tests" -> testIndexes = readArray(p, JsonParser::getIntValue);
                default -> p.skipChildren();
            }
        }
        return new RawLine(line, testIndexes);
    }

    private record RawEntry(String className, String methodName, List<RawLine> lines) {
    }

    private record RawLine(int line, List<Integer> testIndexes) {
    }

    private static List<MutationModuleEvidence> readMutationBlock(JsonParser p) throws IOException {
        expectObjectStart(p);
        List<MutationModuleEvidence> modules = List.of();
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = requireFieldName(p);
            p.nextToken();
            if (FIELD_MODULES.equals(field)) {
                modules = readArray(p, VerdictJsonReader::readMutationModule);
            } else {
                p.skipChildren();
            }
        }
        return modules;
    }

    /** D-86: {@code killingTests} holds indexes into the module's own {@code testIds} table, resolved once the object is fully read. */
    private static MutationModuleEvidence readMutationModule(JsonParser p) throws IOException {
        expectObjectStart(p);
        String id = null;
        List<String> testIds = List.of();
        List<RawMethod> methods = List.of();
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = requireFieldName(p);
            p.nextToken();
            switch (field) {
                case "id" -> id = p.getText();
                case "testIds" -> testIds = readArray(p, JsonParser::getText);
                case "methods" -> methods = readArray(p, VerdictJsonReader::readRawMutatedMethod);
                default -> p.skipChildren();
            }
        }
        return new MutationModuleEvidence(id, resolveMethods(methods, testIds), List.of());
    }

    private static List<MutatedMethod> resolveMethods(List<RawMethod> raw, List<String> testIds) {
        return raw.stream()
            .map(m -> new MutatedMethod(m.className(), m.methodName(), m.methodDescription(),
                m.firstLine(), m.lastLine(),
                m.mutants().stream()
                    .map(mut -> new Mutant(mut.mutator(), mut.line(), mut.status(),
                        mut.killingTestIndexes().stream()
                            .filter(i -> i >= 0 && i < testIds.size())
                            .map(testIds::get)
                            .toList()))
                    .toList()))
            .toList();
    }

    private static RawMethod readRawMutatedMethod(JsonParser p) throws IOException {
        expectObjectStart(p);
        String className = null;
        String methodName = null;
        String methodDescription = null;
        int firstLine = 0;
        int lastLine = 0;
        List<RawMutant> mutants = List.of();
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = requireFieldName(p);
            p.nextToken();
            switch (field) {
                case FIELD_CLASS_NAME -> className = p.getText();
                case FIELD_METHOD_NAME -> methodName = p.getText();
                case "methodDescription" -> methodDescription = p.getText();
                case "firstLine" -> firstLine = p.getIntValue();
                case "lastLine" -> lastLine = p.getIntValue();
                case "mutants" -> mutants = readArray(p, VerdictJsonReader::readRawMutant);
                default -> p.skipChildren();
            }
        }
        return new RawMethod(className, methodName, methodDescription, firstLine, lastLine, mutants);
    }

    private static RawMutant readRawMutant(JsonParser p) throws IOException {
        expectObjectStart(p);
        String mutator = null;
        int line = 0;
        String status = null;
        List<Integer> killingTestIndexes = List.of();
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = requireFieldName(p);
            p.nextToken();
            switch (field) {
                case "mutator" -> mutator = p.getText();
                case "line" -> line = p.getIntValue();
                case FIELD_STATUS -> status = p.getText();
                case "killingTests" -> killingTestIndexes = readArray(p, JsonParser::getIntValue);
                default -> p.skipChildren();
            }
        }
        return new RawMutant(mutator, line, status, killingTestIndexes);
    }

    private record RawMethod(String className, String methodName, String methodDescription,
                              int firstLine, int lastLine, List<RawMutant> mutants) {
    }

    private record RawMutant(String mutator, int line, String status, List<Integer> killingTestIndexes) {
    }

    private static FileCoverageBlock readFileCoverageBlock(JsonParser p) throws IOException {
        expectObjectStart(p);
        List<FileCoverageEntry> files = List.of();
        List<String> excluded = List.of();
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = requireFieldName(p);
            p.nextToken();
            switch (field) {
                case "files" -> files = readArray(p, VerdictJsonReader::readFileCoverageEntry);
                case "excluded" -> excluded = readArray(p, JsonParser::getText);
                default -> p.skipChildren();
            }
        }
        return new FileCoverageBlock(files, excluded);
    }

    private static FileCoverageEntry readFileCoverageEntry(JsonParser p) throws IOException {
        expectObjectStart(p);
        String module = null;
        String path = null;
        MetricSet metrics = null;
        List<LineCoverage> lines = List.of();
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = requireFieldName(p);
            p.nextToken();
            switch (field) {
                case FIELD_MODULE -> module = p.getText();
                case "path" -> path = p.getText();
                case "metrics" -> metrics = readMetricSet(p);
                case "lines" -> lines = readArray(p, VerdictJsonReader::readLineCoverage);
                default -> p.skipChildren();
            }
        }
        return new FileCoverageEntry(module, path, metrics, lines);
    }

    private static LineCoverage readLineCoverage(JsonParser p) throws IOException {
        expectArrayStart(p);
        int[] v = new int[5];
        for (int i = 0; i < 5; i++) {
            p.nextToken();
            v[i] = p.getIntValue();
        }
        expectToken(p, p.nextToken(), JsonToken.END_ARRAY);
        return new LineCoverage(v[0], v[1], v[2], v[3], v[4]);
    }

    private static String readToolVersion(JsonParser p) throws IOException {
        expectObjectStart(p);
        String version = null;
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = requireFieldName(p);
            p.nextToken();
            if ("version".equals(field)) {
                version = p.getText();
            } else {
                p.skipChildren();
            }
        }
        return version;
    }

    // ---- Low-level helpers ----

    private interface ElementReader<T> {
        T read(JsonParser p) throws IOException;
    }

    /** Assumes {@code p.currentToken()} is already {@code START_ARRAY}; consumes through the matching {@code END_ARRAY}. */
    private static <T> List<T> readArray(JsonParser p, ElementReader<T> reader) throws IOException {
        expectArrayStart(p);
        List<T> result = new ArrayList<>();
        while (p.nextToken() != JsonToken.END_ARRAY) {
            result.add(reader.read(p));
        }
        return result;
    }

    private static void expectObjectStart(JsonParser p) {
        expectToken(p, p.currentToken(), JsonToken.START_OBJECT);
    }

    private static void expectArrayStart(JsonParser p) {
        expectToken(p, p.currentToken(), JsonToken.START_ARRAY);
    }

    private static void expectToken(JsonParser p, JsonToken actual, JsonToken expected) {
        if (actual != expected) {
            throw new VerdictJsonReadException("expected " + expected + " but found " + actual + " at " + p.currentLocation());
        }
    }

    private static String requireFieldName(JsonParser p) throws IOException {
        String name = p.currentName();
        if (name == null) {
            throw new VerdictJsonReadException("expected a field name at " + p.currentLocation());
        }
        return name;
    }

    private static void requireNonNull(Object value, String fieldPath) {
        if (value == null) {
            throw new VerdictJsonReadException("missing required field: " + fieldPath);
        }
    }
}
