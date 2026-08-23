package dev.coverdict.analysis.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.coverdict.analysis.AnalysisException;
import dev.coverdict.analysis.jacoco.JacocoReport;
import dev.coverdict.analysis.jacoco.JacocoXmlParser;
import dev.coverdict.analysis.model.AnalysisReason;
import dev.coverdict.analysis.model.ModuleDefinition;
import dev.coverdict.analysis.model.ResolvedSourceFile;

class ModuleBinderTest {

    private static final Path FIXTURES = Path.of("../fixtures/jacoco");
    private final JacocoXmlParser parser = new JacocoXmlParser();

    @TempDir
    Path repoRoot;

    @Test
    void resolvesToRepoRelativePathWhenFileExistsOnDisk() throws IOException {
        createFile(repoRoot.resolve("src/main/java/com/example/Calc.java"));
        ModuleDefinition demo = new ModuleDefinition("demo", ".", List.of("src/main/java"), List.of("src/test/java"));
        JacocoReport report = parser.parse(FIXTURES.resolve("mixed-coverage.xml"));

        BindingResult result = new ModuleBinder(repoRoot)
            .bind(List.of(demo), Map.of("demo", List.of(report)));

        assertEquals(1, result.resolvedFiles().size());
        ResolvedSourceFile file = result.resolvedFiles().get(0);
        assertEquals("src/main/java/com/example/Calc.java", file.repoRelativePath());
        assertTrue(file.foundOnDisk());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void warnsButKeepsTheFileWhenNotFoundOnDisk() {
        // Deliberately NOT created on disk.
        ModuleDefinition demo = new ModuleDefinition("demo", ".", List.of("src/main/java"), List.of("src/test/java"));
        JacocoReport report = parser.parse(FIXTURES.resolve("mixed-coverage.xml"));

        BindingResult result = new ModuleBinder(repoRoot)
            .bind(List.of(demo), Map.of("demo", List.of(report)));

        // Hard rule 3a: missing evidence is a warning, never a silent drop -
        // overall coverage for this file must stay computable.
        assertEquals(1, result.resolvedFiles().size());
        assertFalse(result.resolvedFiles().get(0).foundOnDisk());
        assertEquals(1, result.warnings().size());
        AnalysisReason warning = result.warnings().get(0);
        assertEquals("MISSING_SOURCE_FILE", warning.code());
        assertEquals("src/main/java/com/example/Calc.java", warning.path());
        assertEquals("demo", warning.module());
    }

    @Test
    void triesSourceRootsInOrderAndFindsTheSecond() throws IOException {
        createFile(repoRoot.resolve("src/generated/java/com/example/Calc.java"));
        ModuleDefinition demo = new ModuleDefinition("demo", ".",
            List.of("src/main/java", "src/generated/java"), List.of("src/test/java"));
        JacocoReport report = parser.parse(FIXTURES.resolve("mixed-coverage.xml"));

        BindingResult result = new ModuleBinder(repoRoot)
            .bind(List.of(demo), Map.of("demo", List.of(report)));

        assertEquals("src/generated/java/com/example/Calc.java", result.resolvedFiles().get(0).repoRelativePath());
        assertTrue(result.resolvedFiles().get(0).foundOnDisk());
    }

    @Test
    void rejectsTheSameClassReportedByTwoDifferentReports() {
        ModuleDefinition demo = new ModuleDefinition("demo", ".", List.of("src/main/java"), List.of());
        JacocoReport a = parser.parse(FIXTURES.resolve("duplicate-a.xml"));
        JacocoReport b = parser.parse(FIXTURES.resolve("duplicate-b.xml"));

        ModuleBinder binder = new ModuleBinder(repoRoot);
        List<ModuleDefinition> modules = List.of(demo);
        Map<String, List<JacocoReport>> reportsByModuleId = Map.of("demo", List.of(a, b));
        AnalysisException e = assertThrows(AnalysisException.class, () -> binder.bind(modules, reportsByModuleId));

        assertEquals("DUPLICATE_CLASS_IDENTITY", e.code());
        assertTrue(e.getMessage().contains("Dup.java"), e.getMessage());
    }

    @Test
    void rejectsAReportBoundToAnUndeclaredModule() {
        JacocoReport report = parser.parse(FIXTURES.resolve("mixed-coverage.xml"));

        ModuleBinder binder = new ModuleBinder(repoRoot);
        List<ModuleDefinition> noModules = List.of();
        Map<String, List<JacocoReport>> reportsByModuleId = Map.of("ghost", List.of(report));
        AnalysisException e = assertThrows(AnalysisException.class, () -> binder.bind(noModules, reportsByModuleId));

        assertEquals("UNDECLARED_MODULE", e.code());
    }

    @Test
    void preservesUnicodeAndSpacesWhenJoiningSourceRootAndPackagePath() {
        ModuleDefinition demo = new ModuleDefinition("demo", ".", List.of("src"), List.of());
        JacocoReport report = parser.parse(FIXTURES.resolve("unicode-and-spaces.xml"));

        BindingResult result = new ModuleBinder(repoRoot)
            .bind(List.of(demo), Map.of("demo", List.of(report)));

        assertEquals("src/com/exämple/wëird pkg/Ünïcödé File.java", result.resolvedFiles().get(0).repoRelativePath());
    }

    private static void createFile(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, "// fixture placeholder\n");
    }
}
