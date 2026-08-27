package dev.coverdict.doctor;

/** Severity of one {@link DoctorCheck}: {@code BLOCKER} means the suggested {@code analyze} invocation would fail or silently degrade for that module. */
public enum CheckStatus {
    OK,
    WARN,
    BLOCKER
}
