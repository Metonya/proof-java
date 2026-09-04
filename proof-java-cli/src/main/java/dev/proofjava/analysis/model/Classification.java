package dev.proofjava.analysis.model;

/** The five-way classification every changed Java path ends in (docs/M0-CLI-INPUT.md, M1c criterion 3, D-27). */
public enum Classification {
    MAPPED("mapped"),
    EXCLUDED("excluded"),
    NON_EXECUTABLE("non-executable"),
    UNSUPPORTED("unsupported"),
    UNKNOWN("unknown");

    private final String schemaValue;

    Classification(String schemaValue) {
        this.schemaValue = schemaValue;
    }

    public String schemaValue() {
        return schemaValue;
    }
}
