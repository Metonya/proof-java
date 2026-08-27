package dev.coverdict.doctor;

/**
 * One diagnostic fact about a module - a report is fresh, a classpath list
 * has no code paths, a generated-source directory was found. {@code code}
 * is a stable machine-readable id (same spirit as {@code AnalysisReason},
 * D-64's warning codes) so a future scripted consumer does not have to
 * regex the message.
 */
public record DoctorCheck(String code, CheckStatus status, String message) {

    static DoctorCheck ok(String code, String message) {
        return new DoctorCheck(code, CheckStatus.OK, message);
    }

    static DoctorCheck warn(String code, String message) {
        return new DoctorCheck(code, CheckStatus.WARN, message);
    }

    static DoctorCheck blocker(String code, String message) {
        return new DoctorCheck(code, CheckStatus.BLOCKER, message);
    }
}
