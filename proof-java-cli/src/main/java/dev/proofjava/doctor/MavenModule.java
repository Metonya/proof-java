package dev.proofjava.doctor;

/**
 * One module found by {@link MavenProjectScanner} or {@link
 * GradleProjectScanner}: {@code id} (recommended usage: the Maven
 * artifactId or Gradle project name, matching INPUT-MODEL.md's own {@code
 * --module} recommendation) and {@code root} (repo-relative, forward-slash
 * - the same shape {@code --module id=root-dir} expects on the command
 * line, so a discovered module can be handed straight to {@code analyze}
 * with no translation). The name predates Gradle support (D-96) - the
 * shape itself was always build-tool-agnostic, so it was reused rather
 * than duplicated into an identical {@code GradleModule} record.
 */
public record MavenModule(String id, String root) {
}
