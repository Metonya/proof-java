package dev.proofjava.analysis.oracle;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import dev.proofjava.analysis.model.ModuleDefinition;

/**
 * Configures one {@link JavaParser} for a whole analysis run:
 * {@code --language-level}/{@code --encoding} (INPUT-MODEL.md, now actually
 * consumed rather than only recorded as provenance) plus a
 * {@link JavaSymbolSolver} combining a {@link ReflectionTypeSolver} (JDK
 * classes - always resolvable, no classpath needed) with one
 * {@link JavaParserTypeSolver} per declared source/test root (resolves the
 * repo's own types, including same-file nested classes, without any jar).
 */
final class JavaSourceParser {

    private final JavaParser parser;

    JavaSourceParser(Path repoRoot, List<ModuleDefinition> modules, int languageLevel, String encoding) {
        this(repoRoot, modules, languageLevel, encoding, List.of());
    }

    /** @param extraTypeSolvers test-only hook (fixture harness jars, K4) - never populated in production. */
    JavaSourceParser(Path repoRoot, List<ModuleDefinition> modules, int languageLevel, String encoding, List<TypeSolver> extraTypeSolvers) {
        ParserConfiguration config = new ParserConfiguration()
            .setLanguageLevel(mapLanguageLevel(languageLevel))
            .setCharacterEncoding(Charset.forName(encoding));

        CombinedTypeSolver combined = new CombinedTypeSolver();
        combined.add(new ReflectionTypeSolver(false));
        for (ModuleDefinition module : modules) {
            addIfDirectory(combined, config, repoRoot, module.sourceRoots());
            addIfDirectory(combined, config, repoRoot, module.testRoots());
        }
        for (TypeSolver extra : extraTypeSolvers) {
            combined.add(extra);
        }
        config.setSymbolResolver(new JavaSymbolSolver(combined));
        this.parser = new JavaParser(config);
    }

    /** @return empty when the file could not even be read, or was read but did not parse at the configured language level (INPUT-MODEL.md: classify unsupported, never crash). */
    java.util.Optional<CompilationUnit> parse(Path absolutePath) {
        try {
            ParseResult<CompilationUnit> result = parser.parse(absolutePath);
            return result.isSuccessful() ? result.getResult() : java.util.Optional.empty();
        } catch (java.io.IOException | java.io.UncheckedIOException e) {
            return java.util.Optional.empty();
        }
    }

    private static void addIfDirectory(CombinedTypeSolver combined, ParserConfiguration config, Path repoRoot, List<String> roots) {
        for (String root : roots) {
            Path dir = repoRoot.resolve(root);
            if (Files.isDirectory(dir)) {
                combined.add(new JavaParserTypeSolver(dir, config));
            }
        }
    }

    private static ParserConfiguration.LanguageLevel mapLanguageLevel(int n) {
        return switch (n) {
            case 8 -> ParserConfiguration.LanguageLevel.JAVA_8;
            case 9 -> ParserConfiguration.LanguageLevel.JAVA_9;
            case 10 -> ParserConfiguration.LanguageLevel.JAVA_10;
            case 11 -> ParserConfiguration.LanguageLevel.JAVA_11;
            case 12 -> ParserConfiguration.LanguageLevel.JAVA_12;
            case 13 -> ParserConfiguration.LanguageLevel.JAVA_13;
            case 14 -> ParserConfiguration.LanguageLevel.JAVA_14;
            case 15 -> ParserConfiguration.LanguageLevel.JAVA_15;
            case 16 -> ParserConfiguration.LanguageLevel.JAVA_16;
            case 17 -> ParserConfiguration.LanguageLevel.JAVA_17;
            case 18 -> ParserConfiguration.LanguageLevel.JAVA_18;
            case 19 -> ParserConfiguration.LanguageLevel.JAVA_19;
            case 20 -> ParserConfiguration.LanguageLevel.JAVA_20;
            case 21 -> ParserConfiguration.LanguageLevel.JAVA_21;
            default -> throw new IllegalArgumentException("unsupported language level: " + n
                + " (supported: " + OracleRuleEngine.MIN_LANGUAGE_LEVEL
                + "-" + OracleRuleEngine.MAX_LANGUAGE_LEVEL + ")");
        };
    }
}
