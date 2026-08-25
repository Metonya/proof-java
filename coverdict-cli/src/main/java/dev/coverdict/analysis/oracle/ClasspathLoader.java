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

    /** Shared across every warning message this class writes (SonarQube java:S1192). */
    private static final String FOR_MODULE = "' for module '";

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
            String listFilePath = entry.getValue();
            // An unreadable file's warning is added inside readLines(); this
            // loop then simply has zero lines to iterate, same as an empty
            // file - the two cases need no separate handling here.
            for (String rawLine : readLines(repoRoot, moduleId, listFilePath, warnings)) {
                if (resolveLine(repoRoot, moduleId, listFilePath, rawLine, seenJars, solvers, warnings)
                        == LineOutcome.CAP_REACHED) {
                    break;
                }
            }
        }
        return new Result(List.copyOf(solvers), List.copyOf(warnings));
    }

    /** @return the file's lines, or an empty list (with a warning already recorded) if it could not be read. */
    private static List<String> readLines(Path repoRoot, String moduleId, String listFilePath,
                                           List<AnalysisReason> warnings) {
        try {
            return Files.readAllLines(repoRoot.resolve(listFilePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            warnings.add(new AnalysisReason("CLASSPATH_FILE_UNREADABLE",
                "Classpath list '" + listFilePath + FOR_MODULE + moduleId
                    + "' could not be read (" + e.getClass().getSimpleName()
                    + "); oracle resolution continues without it.", null, moduleId));
            return List.of();
        }
    }

    private enum LineOutcome { SKIPPED, CAP_REACHED, RESOLVED }

    /**
     * One line of a classpath list file. Kept to a single {@code break}/
     * {@code continue}-free shape (SonarQube java:S135) by returning an
     * outcome instead of controlling the caller's loop directly - the caller
     * only ever needs to know whether the cap was hit.
     */
    private static LineOutcome resolveLine(Path repoRoot, String moduleId, String listFilePath, String rawLine,
                                            Set<String> seenJars, List<TypeSolver> solvers,
                                            List<AnalysisReason> warnings) {
        String jarPath = rawLine.trim();
        if (jarPath.isEmpty() || jarPath.startsWith("#")) {
            return LineOutcome.SKIPPED;
        }
        if (seenJars.size() >= MAX_JAR_ENTRIES) {
            warnings.add(new AnalysisReason("CLASSPATH_TOO_LARGE",
                "Classpath list '" + listFilePath + FOR_MODULE + moduleId + "' exceeds "
                    + MAX_JAR_ENTRIES + " entries (SECURITY-POLICY.md #2); the remainder was ignored.",
                null, moduleId));
            return LineOutcome.CAP_REACHED;
        }
        // A jar lives outside the repo by nature (~/.m2/repository/...), so
        // the repo-root escape check that guards report-derived paths
        // (SECURITY-POLICY.md #4) deliberately does not apply here: this path
        // comes from the invoking user, like --repo or --out, not from inside
        // a parsed report.
        Path jar = repoRoot.resolve(jarPath);
        if (!seenJars.add(jar.toString())) {
            return LineOutcome.SKIPPED; // same jar named by two modules - solve it once
        }
        try {
            solvers.add(new JarTypeSolver(jar));
        } catch (IOException | RuntimeException e) {
            warnings.add(new AnalysisReason("CLASSPATH_ENTRY_UNUSABLE",
                "Classpath entry '" + jarPath + FOR_MODULE + moduleId + "' could not be opened as a jar ("
                    + e.getClass().getSimpleName() + "); oracle resolution continues without it.", null, moduleId));
        }
        return LineOutcome.RESOLVED;
    }

    /** Solvers to hand {@link OracleRuleEngine#scan}, plus warnings the verdict must surface. */
    public record Result(List<TypeSolver> solvers, List<AnalysisReason> warnings) {
    }
}
