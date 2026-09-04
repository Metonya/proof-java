package dev.proofjava.doctor;

/**
 * One reactor module found by {@link MavenProjectScanner}: {@code id}
 * (recommended usage: the Maven artifactId, matching M0-CLI-INPUT.md's own
 * {@code --module} recommendation) and {@code root} (repo-relative,
 * forward-slash - the same shape {@code --module id=root-dir} expects on
 * the command line, so a discovered module can be handed straight to
 * {@code analyze} with no translation).
 */
public record MavenModule(String id, String root) {
}
