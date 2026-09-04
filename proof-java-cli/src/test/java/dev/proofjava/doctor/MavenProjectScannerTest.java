package dev.proofjava.doctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Real pom.xml text, not a hand-built object model - the depth-tracking bug
 * this class shipped with (off by one: {@code <project>} itself is the
 * first {@code START_ELEMENT}, so its own children are depth 2, not depth
 * 1 - found live testing against coverdict's own reactor) only shows up
 * against a real multi-module document, not a unit-level mock.
 */
class MavenProjectScannerTest {

    @TempDir
    Path repoRoot;

    private void writePom(String relativePath, String content) throws IOException {
        Path file = repoRoot.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    @Test
    void findsASingleModuleRootPom() throws IOException {
        writePom("pom.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>dev.example</groupId>
              <artifactId>single-module</artifactId>
              <version>1.0</version>
              <packaging>jar</packaging>
            </project>
            """);

        List<MavenModule> modules = MavenProjectScanner.scan(repoRoot);

        assertEquals(List.of(new MavenModule("single-module", ".")), modules);
    }

    /**
     * Matches coverdict's own reactor shape exactly: a {@code packaging=pom}
     * aggregator declaring one {@code <module>}, which is itself a real
     * jar module.
     */
    @Test
    void aPackagingPomAggregatorIsNotItselfAModuleButItsChildrenAre() throws IOException {
        writePom("pom.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <artifactId>parent</artifactId>
              <packaging>pom</packaging>
              <modules>
                <module>child</module>
              </modules>
            </project>
            """);
        writePom("child/pom.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <artifactId>child</artifactId>
              <packaging>jar</packaging>
            </project>
            """);

        List<MavenModule> modules = MavenProjectScanner.scan(repoRoot);

        assertEquals(List.of(new MavenModule("child", "child")), modules);
    }

    @Test
    void followsNestedModulesAtAnyDepth() throws IOException {
        writePom("pom.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <artifactId>root</artifactId>
              <packaging>pom</packaging>
              <modules><module>level1</module></modules>
            </project>
            """);
        writePom("level1/pom.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <artifactId>level1</artifactId>
              <packaging>pom</packaging>
              <modules><module>level2</module></modules>
            </project>
            """);
        writePom("level1/level2/pom.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <artifactId>level2</artifactId>
            </project>
            """);

        List<MavenModule> modules = MavenProjectScanner.scan(repoRoot);

        assertEquals(List.of(new MavenModule("level2", "level1/level2")), modules);
    }

    /**
     * A {@code <parent>` block often repeats {@code <artifactId>} for the
     * parent coordinate - it must never be mistaken for this module's own,
     * and must not perturb depth tracking for what follows it.
     */
    @Test
    void ignoresArtifactIdInsideParentDependenciesAndProperties() throws IOException {
        writePom("pom.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <parent>
                <groupId>dev.example</groupId>
                <artifactId>should-be-ignored</artifactId>
                <version>1.0</version>
              </parent>
              <artifactId>real-module</artifactId>
              <dependencies>
                <dependency>
                  <artifactId>also-ignored</artifactId>
                </dependency>
              </dependencies>
              <properties>
                <artifactId>not-real-either</artifactId>
              </properties>
            </project>
            """);

        List<MavenModule> modules = MavenProjectScanner.scan(repoRoot);

        assertEquals(List.of(new MavenModule("real-module", ".")), modules);
    }

    @Test
    void aMissingRootPomProducesNoModules() {
        assertTrue(MavenProjectScanner.scan(repoRoot).isEmpty());
    }

    @Test
    void aMalformedChildPomStopsThatBranchWithoutFailingTheWholeScan() throws IOException {
        writePom("pom.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <artifactId>root</artifactId>
              <packaging>pom</packaging>
              <modules>
                <module>broken</module>
                <module>fine</module>
              </modules>
            </project>
            """);
        writePom("broken/pom.xml", "<not-even-xml");
        writePom("fine/pom.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <artifactId>fine</artifactId>
            </project>
            """);

        List<MavenModule> modules = MavenProjectScanner.scan(repoRoot);

        assertEquals(List.of(new MavenModule("fine", "fine")), modules);
    }

    /** A `<module>` path escaping the repo root (SECURITY-POLICY.md #4 precedent) is never followed. */
    @Test
    void aModulePathEscapingTheRepoRootIsNotFollowed() throws IOException {
        writePom("pom.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <artifactId>root</artifactId>
              <packaging>pom</packaging>
              <modules><module>../outside</module></modules>
            </project>
            """);

        assertTrue(MavenProjectScanner.scan(repoRoot).isEmpty());
    }
}
