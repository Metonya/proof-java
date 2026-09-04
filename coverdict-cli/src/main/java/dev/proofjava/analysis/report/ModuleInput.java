package dev.proofjava.analysis.report;

import java.util.List;

/** Schema {@code inputs.modules[]}. */
public record ModuleInput(String id, String root, List<String> sourceRoots, List<String> testRoots, List<ReportInput> reports) {
}
