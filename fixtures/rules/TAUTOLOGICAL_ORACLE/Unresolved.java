package rules.tautological_oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

// Deliberately not compilable: ExternalConstants is absent from the fixture
// classpath. When an operand cannot be resolved to a constant, this rule
// stays silent - a wrong tautology claim is the costliest false positive.
// expect: none method=unresolvedConstantOperand
class Unresolved {

    @Test
    void unresolvedConstantOperand() {
        assertEquals(ExternalConstants.EXPECTED, 42);
    }
}
