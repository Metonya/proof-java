package dev.proofjava.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import dev.proofjava.analysis.report.HtmlRenderer;
import dev.proofjava.analysis.report.VerdictDocument;
import dev.proofjava.analysis.report.VerdictJsonReader;

/**
 * D-78: renders an already-produced verdict JSON as HTML, without running
 * any fresh analysis. The counterpart to {@code analyze --html-report}'s
 * "produce evidence and render it" path - this command only renders (hard
 * rule 7: still the same {@link HtmlRenderer}, one more reader over the
 * exact same document shape, this time read back from disk instead of
 * built in-process).
 *
 * <p>Built for {@code coverdict-vscode}'s "Export report" command: the
 * extension composes a verdict JSON from whatever coverage/per-test/mutation
 * state it currently holds and hands it here for rendering, at zero
 * re-analysis cost - see D-78 for why a fresh diff-derived re-scan is the
 * wrong default (it can come back emptier than what the user already had
 * on screen, silently discarding real evidence the extension already knew).
 */
@Command(name = "render-html", mixinStandardHelpOptions = true,
    description = "Render an existing verdict JSON (schema/coverdict-verdict.schema.json) as a standalone HTML report - no fresh analysis.")
class RenderHtmlCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(names = "--in", required = true, description = "Path to an existing verdict JSON file.")
    private String inOption;

    @Option(names = "--out", required = true, description = "HTML output path.")
    private String outOption;

    @Override
    public Integer call() {
        VerdictDocument doc;
        try (InputStream in = Files.newInputStream(Path.of(inOption))) {
            doc = VerdictJsonReader.read(in);
        } catch (IOException e) {
            spec.commandLine().getErr().println("coverdict: could not read " + inOption + ": " + e.getMessage());
            return ExitCode.INVALID_INPUT.value();
        } catch (VerdictJsonReader.VerdictJsonReadException e) {
            spec.commandLine().getErr().println("coverdict: " + inOption + " is not a valid verdict document: " + e.getMessage());
            return ExitCode.INVALID_INPUT.value();
        }

        try (OutputStream out = Files.newOutputStream(Path.of(outOption))) {
            out.write(HtmlRenderer.render(doc).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            spec.commandLine().getErr().println("coverdict: could not write " + outOption + ": " + e.getMessage());
            return ExitCode.INTERNAL_ERROR.value();
        }

        spec.commandLine().getOut().println("html report written to " + outOption);
        return ExitCode.COMPLETE.value();
    }
}
