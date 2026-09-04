package dev.proofjava.analysis.jacoco;

/**
 * One {@code <line nr mi ci mb cb>} record from a JaCoCo XML report.
 * Instruction-level, not source-text-level: {@code missedInstructions}/
 * {@code coveredInstructions} count bytecode instructions attributed to this
 * source line, and {@code missedBranches}/{@code coveredBranches} count
 * branch outcomes on it (0/0 when the line has no branch).
 */
public record LineCoverage(
    int number,
    int missedInstructions,
    int coveredInstructions,
    int missedBranches,
    int coveredBranches
) {

    /** jacoco-line membership: any instruction on this line ran (D-04). */
    public boolean isCovered() {
        return coveredInstructions > 0;
    }

    /** strict-line membership: covered AND no missed instruction remains (D-19). Branch completeness is not folded in. */
    public boolean isFullyCovered() {
        return coveredInstructions > 0 && missedInstructions == 0;
    }
}
