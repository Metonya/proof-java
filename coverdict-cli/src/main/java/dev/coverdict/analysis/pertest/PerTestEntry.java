package dev.coverdict.analysis.pertest;

import java.util.List;

/**
 * One {@code class#method} PIT recorded coverage for, with its per-line test
 * map (schema {@code $defs/perTestEntry}). {@code className} is the
 * fully-qualified source name; {@code methodName} is PIT's own JVM method
 * name, including {@code <clinit>} for static initializers - the D-50 marker
 * {@link BlockLineResolver} uses to split the ambient bucket.
 */
public record PerTestEntry(String className, String methodName, List<PerTestLine> lines) {
}
