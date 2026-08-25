package dev.coverdict.analysis.oracle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;

import dev.coverdict.analysis.model.AnalysisReason;

/**
 * Builds JavaParser {@link TypeSolver}s from {@code --classpath <id>=<file>}
 * (M0-CLI-INPUT.md): each file lists one jar path per line. This is the
 * production counterpart of the fixture harness's jar solvers - until now the
 * only {@link JarTypeSolver} usage lived in a test (D-28 shipped
 * import-anchoring precisely because a real run had no classpath at all).
 *
 * <p>D-17 semantics are one-directional and enforced by what this class does
 * <em>not</em> do: a present classpath lets the symbol solver resolve a call
 * with certainty, but nothing here upgrades a finding's confidence on its own,
 * and an absent or partial classpath is never an error - it simply leaves the
 * two-tier resolution (D-28) where it already was.
 *
 * <p><strong>Per-module ids are accepted but the solvers are unioned.</strong>
 * The spec's surface is {@code <id>=<file>} per module, but {@link
 * JavaSourceParser} builds one {@link
 * com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver}
 * for the whole run, so every declared module's jars end up visible to every
 * module's parse. Splitting that into a parser per module is a real change to
 * the scan loop and buys nothing for v0.1's allowlist (the recognized
 * libraries are test-scope dependencies shared across a repo's modules); the
 * id is still required, and still validated against the declared modules, so
 * the argument stays checkable and the narrowing is recorded rather than
 * silently assumed.
 */
public final class ClasspathLoader {

    /**
     * SECURITY-POLICY.md #2 (resource limits): a classpath list is
     * untrusted input like any other. A real Maven dependency list is a few
     * hundred lines; this bound only rules out a pathological file.
     */
    private static final int MAX_JAR_ENTRIES = 10_000;

    private ClasspathLoader() {
    }

    /**
     * @param classpathFilesById already-parsed {@code <id>=<file>} pairs; ids
     *                           are assumed validated against declared modules
     *                           by the caller (an unknown id is an invalid
     *                           invocation, exit 2, not a warning).
     * @return solvers plus one warning per entry that could not be used -
     *         never an exception, never a silent drop (hard rule 3a).
     */
    public static Result load(Path repoRoot, Map<String, String> classpathFilesById) {
        List<TypeSolver> solvers = new ArrayList<>();
        List<AnalysisReason> warnings = new ArrayList<>();
        Set<String> seenJars = new LinkedHashSet<>();

        for (Map.Entry<String, String> entry : classpathFilesById.entrySet()) {
            String moduleId = entry.getKey();
            Path listFile = repoRoot.resolve(entry.getValue());
            List<String> lines;
            try {
                lines = Files.readAllLines(listFile, StandardCharsets.UTF_8);
            } catch (IOException e) {
                warnings.add(new AnalysisReason("CLASSPATH_FILE_UNREADABLE",
                    "Classpath list '" + entry.getValue() + "' for module '" + moduleId
                        + "' could not be read (" + e.getClass().getSimpleName()
                        + "); oracle resolution continues without it.", null, moduleId));
                continue;
            }

            for (String rawLine : lines) {
                String jarPath = rawLine.trim();
                if (jarPath.isEmpty() || jarPath.startsWith("#")) {
                    continue;
                }
                if (seenJars.size() >= MAX_JAR_ENTRIES) {
                    warnings.add(new AnalysisReason("CLASSPATH_TOO_LARGE",
                        "Classpath list '" + entry.getValue() + "' for module '" + moduleId + "' exceeds "
                            + MAX_JAR_ENTRIES + " entries (SECURITY-POLICY.md #2); the remainder was ignored.",
                        null, moduleId));
                    break;
                }
                // A jar lives outside the repo by nature (~/.m2/repository/...),
                // so the repo-root escape check that guards report-derived paths
                // (SECURITY-POLICY.md #4) deliberately does not apply here: this
                // path comes from the invoking user, like --repo or --out, not
                // from inside a parsed report.
                Path jar = repoRoot.resolve(jarPath);
                if (!seenJars.add(jar.toString())) {
                    continue; // same jar named by two modules - solve it once
                }
                try {
                    solvers.add(new JarTypeSolver(jar));
                } catch (IOException | RuntimeException e) {
                    warnings.add(new AnalysisReason("CLASSPATH_ENTRY_UNUSABLE",
                        "Classpath entry '" + jarPath + "' for module '" + moduleId + "' could not be opened as a jar ("
                            + e.getClass().getSimpleName() + "); oracle resolution continues without it.", null, moduleId));
                }
            }
        }
        return new Result(List.copyOf(solvers), List.copyOf(warnings));
    }

    /** Solvers to hand {@link OracleRuleEngine#scan}, plus warnings the verdict must surface. */
    public record Result(List<TypeSolver> solvers, List<AnalysisReason> warnings) {
    }
}
