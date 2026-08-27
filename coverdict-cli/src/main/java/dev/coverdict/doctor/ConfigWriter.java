package dev.coverdict.doctor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes {@code coverdict.config.json}'s {@code modules} array (D-66) from
 * a diagnosed reactor - {@code doctor --write-config}'s output. Only
 * modules with a usable JaCoCo report are written; a module with a
 * BLOCKER is left out entirely rather than written with a broken binding,
 * the same filter {@link DoctorReportRenderer} applies to its suggested
 * command.
 *
 * <p>Hand-written JSON, not a library - the document is a short, fixed
 * shape (id/root/report/classpath strings), and every other JSON writer in
 * this codebase ({@code VerdictJsonWriter}, {@code MutationJsonWriter}) is
 * hand-written for the same reason: {@code ConfigLoader} deliberately
 * avoids adding {@code json-schema-validator} to the shipped jar just to
 * read one small file (D-40), and pulling in a full JSON writer here for a
 * document this simple would be the same trade in reverse.
 */
public final class ConfigWriter {

    private ConfigWriter() {
    }

    /** @return true if a config file was written (false if there was nothing usable to write). */
    public static boolean write(List<ModuleDiagnosis> diagnoses, Path targetFile) {
        List<ModuleDiagnosis> usable = diagnoses.stream().filter(ModuleDiagnosis::usableForAnalyze).toList();
        if (usable.isEmpty()) {
            return false;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"modules\": [\n");
        for (int i = 0; i < usable.size(); i++) {
            ModuleDiagnosis d = usable.get(i);
            sb.append("    {\n");
            sb.append("      \"id\": ").append(jsonString(d.module().id())).append(",\n");
            sb.append("      \"root\": ").append(jsonString(d.module().root())).append(",\n");
            sb.append("      \"report\": ").append(jsonString(d.jacocoReportPath()));
            if (d.perTestClasspath() != null) {
                sb.append(",\n      \"perTestClasspath\": ").append(jsonString(d.perTestClasspath()));
            }
            if (d.mutationClasspath() != null) {
                sb.append(",\n      \"mutationClasspath\": ").append(jsonString(d.mutationClasspath()));
            }
            sb.append("\n    }").append(i < usable.size() - 1 ? "," : "").append('\n');
        }
        sb.append("  ]\n}\n");

        try {
            Files.writeString(targetFile, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("Could not write " + targetFile, e);
        }
        return true;
    }

    /** Every value written here is a repo-relative path or a module id - never attacker-controlled text, but escaping stays real JSON escaping rather than an assumption that it is never needed. */
    private static String jsonString(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
