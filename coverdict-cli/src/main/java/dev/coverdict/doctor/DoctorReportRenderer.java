package dev.coverdict.doctor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Human-readable rendering of a {@code doctor} run: one block per module,
 * then a copy-pasteable {@code analyze} invocation built only from modules
 * that actually have usable evidence - the WTA dogfood's own command was
 * hand-assembled from 20+ lines the user had to get exactly right; this is
 * that command, generated instead of typed.
 */
public final class DoctorReportRenderer {

    private DoctorReportRenderer() {
    }

    public static String render(List<ModuleDiagnosis> diagnoses) {
        StringBuilder sb = new StringBuilder();
        sb.append("coverdict doctor: ").append(diagnoses.size()).append(" module(s) found\n\n");

        for (ModuleDiagnosis d : diagnoses) {
            sb.append(d.module().id()).append(" (").append(d.module().root()).append(")\n");
            for (DoctorCheck c : d.checks()) {
                sb.append("  ").append(symbol(c.status())).append(' ').append(c.message()).append('\n');
            }
            sb.append('\n');
        }

        long blockers = diagnoses.stream().filter(ModuleDiagnosis::hasBlocker).count();
        List<ModuleDiagnosis> usable = diagnoses.stream().filter(ModuleDiagnosis::usableForAnalyze).toList();

        if (blockers > 0) {
            sb.append(blockers).append(" module(s) have at least one BLOCKER - fix those first, or run 'doctor --fix' to resolve what it can.\n\n");
        }

        if (usable.isEmpty()) {
            sb.append("No module has a usable JaCoCo report yet - nothing to suggest a command for.\n");
            return sb.toString();
        }

        sb.append("Suggested command (fill in <ref>, or replace --base <ref> with --uncommitted/--no-vcs):\n\n");
        sb.append(suggestedCommand(usable));
        return sb.toString();
    }

    private static String suggestedCommand(List<ModuleDiagnosis> usable) {
        StringBuilder sb = new StringBuilder();
        sb.append("  java -jar coverdict.jar analyze \\\n");
        sb.append("    --base <ref> \\\n");
        for (ModuleDiagnosis d : usable) {
            sb.append("    --module ").append(d.module().id()).append('=').append(d.module().root()).append(" \\\n");
        }
        for (ModuleDiagnosis d : usable) {
            sb.append("    --report ").append(d.module().id()).append('=').append(d.jacocoReportPath()).append(" \\\n");
        }
        sb.append("    --out coverdict-verdict.json\n");

        List<ModuleDiagnosis> withPerTest = usable.stream().filter(d -> d.perTestClasspath() != null).toList();
        List<ModuleDiagnosis> withMutation = usable.stream().filter(d -> d.mutationClasspath() != null).toList();
        if (!withPerTest.isEmpty() || !withMutation.isEmpty()) {
            sb.append("\n  L2/L3 evidence is also available for: ");
            sb.append(withPerTest.stream().map(d -> d.module().id()).collect(Collectors.joining(", ")));
            sb.append(" - add --per-test-report/--mutation-report plus the matching "
                + "--per-test-classpath/--mutation-classpath <id>=<path> flags to collect it.\n");
        }
        return sb.toString();
    }

    private static String symbol(CheckStatus status) {
        return switch (status) {
            case OK -> "[ok]  ";
            case WARN -> "[warn]";
            case BLOCKER -> "[FAIL]";
        };
    }
}
