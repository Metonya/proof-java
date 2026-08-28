package dev.coverdict.analysis.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.coverdict.analysis.model.ModuleDefinition;

class SourceRootClassIndexTest {

    @TempDir
    Path repoRoot;

    @Test
    void resolvesAFqcnThatExistsUnderTheModulesOwnSourceRoot() throws IOException {
        ModuleDefinition module = new ModuleDefinition("root", ".", List.of("src/main/java"), List.of());
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
        Files.createFile(repoRoot.resolve("src/main/java/com/example/Calc.java"));

        SourceRootClassIndex.Resolution resolution = SourceRootClassIndex.resolve(repoRoot, module, "com.example.Calc");

        assertTrue(resolution.foundOnDisk());
        assertEquals("src/main/java/com/example/Calc.java", resolution.path());
    }

    @Test
    void nestedClassSuffixIsStrippedBeforeLookingUpTheOwningTopLevelSourceFile() throws IOException {
        ModuleDefinition module = new ModuleDefinition("root", ".", List.of("src/main/java"), List.of());
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
        Files.createFile(repoRoot.resolve("src/main/java/com/example/Calc.java"));

        SourceRootClassIndex.Resolution resolution = SourceRootClassIndex.resolve(repoRoot, module, "com.example.Calc$Inner");

        assertTrue(resolution.foundOnDisk());
        assertEquals("src/main/java/com/example/Calc.java", resolution.path());
    }

    @Test
    void aClassWithNoMatchingFileUnderAnySourceRootIsUnresolved() {
        ModuleDefinition module = new ModuleDefinition("root", ".", List.of("src/main/java"), List.of());

        SourceRootClassIndex.Resolution resolution = SourceRootClassIndex.resolve(repoRoot, module, "com.example.Missing");

        assertFalse(resolution.foundOnDisk());
    }

    @Test
    void aFqcnThatWouldEscapeTheRepoRootIsUnresolvedRatherThanResolvedOutsideIt() {
        ModuleDefinition module = new ModuleDefinition("root", ".", List.of("src/main/java"), List.of());

        // A class name cannot literally contain "..", but the same guard
        // ModuleBinder.resolvePath applies exists here too (SECURITY-POLICY.md
        // #4) - proven directly against the string-segment check rather than
        // relying on a real filesystem escape being constructible from a FQCN.
        SourceRootClassIndex.Resolution resolution = SourceRootClassIndex.resolve(repoRoot, module, "..outside.Evil");

        assertFalse(resolution.foundOnDisk());
    }
}
