package dev.coverdict.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;

import dev.coverdict.analysis.model.RuleIds;

/**
 * Reads {@code coverdict.config.json} (M0-CLI-INPUT.md's config surface).
 *
 * <p><strong>Why a hand-written strict reader rather than the JSON Schema
 * validator (D-40):</strong> the schema
 * ({@code schema/coverdict-config.schema.json}) is checked in and is the
 * contract, but {@code json-schema-validator} is a test-scope dependency, and
 * pulling it plus its transitive tree into the shipped jar to read one small
 * fixed-shape file is a poor trade - hard rule 9 makes every added runtime
 * dependency a licensing and inventory obligation. This reader enforces the
 * same strictness directly (unknown key, wrong type, or malformed JSON is a
 * hard error), and {@code ConfigLoaderTest} asserts that the reader and the
 * checked-in schema accept and reject the same documents, so the schema
 * cannot silently drift from the parser.
 *
 * <p>Reading is deliberately intolerant: an unknown key is a typo the user
 * wants to hear about, not something to ignore. Silently skipping it would let
 * a misspelled {@code supressions} suppress nothing while the user believed it
 * did (hard rule 3a).
 */
public final class ConfigLoader {

    /** M0-CLI-INPUT.md: used when {@code --config} is absent and this file exists at the repo root. */
    public static final String DEFAULT_FILE_NAME = "coverdict.config.json";

    /** SECURITY-POLICY.md #2: a config file is untrusted input; a real one is well under a kilobyte. */
    private static final int MAX_CONFIG_BYTES = 1024 * 1024;

    private ConfigLoader() {
    }

    /**
     * @param explicitPath the {@code --config} value, or null to fall back to
     *                     {@link #DEFAULT_FILE_NAME} at the repo root
     * @return the parsed config, or {@link CoverdictConfig#empty()} when no
     *         config applies. An explicitly named file that does not exist is
     *         an error; the implicit default one simply not being there is not.
     * @throws ConfigException on a missing explicit file, an oversized file,
     *                         malformed JSON, an unknown key, or a wrong value
     *                         type (all exit 2 - the invocation is invalid)
     */
    public static CoverdictConfig load(Path repoRoot, String explicitPath) {
        Path file;
        if (explicitPath != null) {
            file = repoRoot.resolve(explicitPath);
            if (!Files.isRegularFile(file)) {
                throw new ConfigException("--config file not found: " + explicitPath);
            }
        } else {
            file = repoRoot.resolve(DEFAULT_FILE_NAME);
            if (!Files.isRegularFile(file)) {
                return CoverdictConfig.empty();
            }
        }
        return parse(file);
    }

    private static CoverdictConfig parse(Path file) {
        byte[] bytes;
        try {
            long size = Files.size(file);
            if (size > MAX_CONFIG_BYTES) {
                throw new ConfigException("Config file " + file.getFileName() + " is " + size
                    + " bytes, over the " + MAX_CONFIG_BYTES + "-byte cap (SECURITY-POLICY.md #2).");
            }
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new ConfigException("Config file could not be read: " + e.getMessage());
        }

        JsonFactory factory = JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(20).build())
            .build();

        Integer languageLevel = null;
        String encoding = null;
        List<String> coverageExclusions = null;
        String findingsScope = null;
        List<String> customOracles = List.of();
        List<CoverdictConfig.ModuleConfig> modules = List.of();
        List<CoverdictConfig.Suppression> suppressions = List.of();

        try (JsonParser p = factory.createParser(new String(bytes, StandardCharsets.UTF_8))) {
            if (p.nextToken() != JsonToken.START_OBJECT) {
                throw new ConfigException("Config file must contain a single JSON object.");
            }
            Set<String> seen = new LinkedHashSet<>();
            while (p.nextToken() != JsonToken.END_OBJECT) {
                String key = p.currentName();
                if (!seen.add(key)) {
                    throw new ConfigException("Duplicate config key '" + key + "'.");
                }
                p.nextToken();
                switch (key) {
                    case "languageLevel" -> languageLevel = intValue(p, key);
                    case "encoding" -> encoding = stringValue(p, key);
                    case "coverageExclusions" -> coverageExclusions = stringArray(p, key);
                    case "findingsScope" -> findingsScope = enumValue(p, key, "all", "changed");
                    case "customOracles" -> customOracles = customOracleArray(p);
                    case "modules" -> modules = moduleConfigArray(p);
                    case "suppressions" -> suppressions = suppressionArray(p);
                    default -> throw new ConfigException("Unknown config key '" + key
                        + "'. Known keys: languageLevel, encoding, coverageExclusions, findingsScope, "
                        + "customOracles, modules, suppressions.");
                }
            }
        } catch (IOException e) {
            throw new ConfigException("Config file is not valid JSON: " + e.getMessage());
        }

        return new CoverdictConfig(languageLevel, encoding, coverageExclusions, findingsScope,
            customOracles, modules, suppressions);
    }

    private static int intValue(JsonParser p, String key) throws IOException {
        if (p.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            throw new ConfigException("Config key '" + key + "' must be an integer.");
        }
        return p.getIntValue();
    }

    private static String stringValue(JsonParser p, String key) throws IOException {
        if (p.currentToken() != JsonToken.VALUE_STRING || p.getText().isEmpty()) {
            throw new ConfigException("Config key '" + key + "' must be a non-empty string.");
        }
        return p.getText();
    }

    private static String enumValue(JsonParser p, String key, String... allowed) throws IOException {
        String value = stringValue(p, key);
        for (String candidate : allowed) {
            if (candidate.equals(value)) {
                return value;
            }
        }
        throw new ConfigException("Config key '" + key + "' must be one of " + String.join(", ", allowed)
            + ", got: " + value);
    }

    private static List<String> stringArray(JsonParser p, String key) throws IOException {
        if (p.currentToken() != JsonToken.START_ARRAY) {
            throw new ConfigException("Config key '" + key + "' must be an array of strings.");
        }
        List<String> values = new ArrayList<>();
        while (p.nextToken() != JsonToken.END_ARRAY) {
            if (p.currentToken() != JsonToken.VALUE_STRING || p.getText().isEmpty()) {
                throw new ConfigException("Config key '" + key + "' must contain only non-empty strings.");
            }
            values.add(p.getText());
        }
        return List.copyOf(values);
    }

    private static List<String> customOracleArray(JsonParser p) throws IOException {
        List<String> entries = stringArray(p, "customOracles");
        for (String entry : entries) {
            int hash = entry.indexOf('#');
            if (hash <= 0 || hash == entry.length() - 1 || entry.indexOf('#', hash + 1) >= 0) {
                throw new ConfigException("customOracles entry '" + entry
                    + "' must be fully.qualified.Type#methodPattern (exactly one '#', both sides non-empty).");
            }
        }
        return entries;
    }

    /**
     * Validated against {@link RuleIds#ALL}, the same set the engine emits
     * from - a suppression naming a rule that does not exist would suppress
     * nothing while reading as if it did (hard rule 3a), and would also make
     * the reader disagree with the config schema's own enum.
     */
    private static String ruleId(JsonParser p) throws IOException {
        String value = stringValue(p, "suppressions.rule");
        if (!RuleIds.ALL.contains(value)) {
            throw new ConfigException("Unknown rule id '" + value + "' in a suppressions entry. Known rules: "
                + String.join(", ", new java.util.TreeSet<>(RuleIds.ALL)) + ".");
        }
        return value;
    }

    /** D-66: the config-file shape of a {@code --module}/{@code --report}/{@code --per-test-classpath}/{@code --mutation-classpath} binding. */
    private static List<CoverdictConfig.ModuleConfig> moduleConfigArray(JsonParser p) throws IOException {
        if (p.currentToken() != JsonToken.START_ARRAY) {
            throw new ConfigException("Config key 'modules' must be an array of objects.");
        }
        List<CoverdictConfig.ModuleConfig> result = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        while (p.nextToken() != JsonToken.END_ARRAY) {
            CoverdictConfig.ModuleConfig module = oneModuleConfig(p);
            if (!ids.add(module.id())) {
                throw new ConfigException("Duplicate module id '" + module.id()
                    + "' in 'modules' - each id may be declared at most once (same rule as --module on the command line).");
            }
            result.add(module);
        }
        return List.copyOf(result);
    }

    private static CoverdictConfig.ModuleConfig oneModuleConfig(JsonParser p) throws IOException {
        if (p.currentToken() != JsonToken.START_OBJECT) {
            throw new ConfigException("Config key 'modules' must contain only objects.");
        }
        String id = null;
        String root = null;
        List<String> sourceRoots = null;
        List<String> testRoots = null;
        String report = null;
        String perTestClasspath = null;
        String mutationClasspath = null;
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String key = p.currentName();
            p.nextToken();
            switch (key) {
                case "id" -> id = stringValue(p, "modules.id");
                case "root" -> root = stringValue(p, "modules.root");
                case "sourceRoots" -> sourceRoots = stringArray(p, "modules.sourceRoots");
                case "testRoots" -> testRoots = stringArray(p, "modules.testRoots");
                case "report" -> report = stringValue(p, "modules.report");
                case "perTestClasspath" -> perTestClasspath = stringValue(p, "modules.perTestClasspath");
                case "mutationClasspath" -> mutationClasspath = stringValue(p, "modules.mutationClasspath");
                default -> throw new ConfigException("Unknown key '" + key + "' in a modules entry. Known keys: "
                    + "id, root, sourceRoots, testRoots, report, perTestClasspath, mutationClasspath.");
            }
        }
        if (id == null || root == null) {
            throw new ConfigException("Every modules entry needs 'id' and 'root'.");
        }
        return new CoverdictConfig.ModuleConfig(id, root, sourceRoots, testRoots, report, perTestClasspath,
            mutationClasspath);
    }

    private static List<CoverdictConfig.Suppression> suppressionArray(JsonParser p) throws IOException {
        if (p.currentToken() != JsonToken.START_ARRAY) {
            throw new ConfigException("Config key 'suppressions' must be an array of objects.");
        }
        List<CoverdictConfig.Suppression> result = new ArrayList<>();
        while (p.nextToken() != JsonToken.END_ARRAY) {
            if (p.currentToken() != JsonToken.START_OBJECT) {
                throw new ConfigException("Config key 'suppressions' must contain only objects.");
            }
            String rule = null;
            String pathGlob = null;
            String testMethodPattern = null;
            String reason = null;
            while (p.nextToken() != JsonToken.END_OBJECT) {
                String key = p.currentName();
                p.nextToken();
                switch (key) {
                    case "rule" -> rule = ruleId(p);
                    case "pathGlob" -> pathGlob = stringValue(p, "suppressions.pathGlob");
                    case "testMethodPattern" -> testMethodPattern = stringValue(p, "suppressions.testMethodPattern");
                    case "reason" -> reason = stringValue(p, "suppressions.reason");
                    default -> throw new ConfigException("Unknown key '" + key
                        + "' in a suppressions entry. Known keys: rule, pathGlob, testMethodPattern, reason.");
                }
            }
            if (rule == null || pathGlob == null || reason == null) {
                throw new ConfigException(
                    "Every suppressions entry needs 'rule', 'pathGlob' and 'reason' (reason is mandatory: "
                        + "docs/rules/README.md).");
            }
            result.add(new CoverdictConfig.Suppression(rule, pathGlob, testMethodPattern, reason));
        }
        return List.copyOf(result);
    }
}
