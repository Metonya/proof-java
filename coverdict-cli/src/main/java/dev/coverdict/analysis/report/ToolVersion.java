package dev.coverdict.analysis.report;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Reads the same Maven-filtered {@code coverdict-version.properties} resource
 * {@code dev.coverdict.cli.VersionProvider} uses for {@code --version} - one
 * source of truth, so the verdict JSON's {@code tool.version}/{@code
 * schemaVersion} can never silently drift from what {@code --version} prints.
 */
public final class ToolVersion {

    private static final String RESOURCE = "/coverdict-version.properties";

    private ToolVersion() {
    }

    public record Info(String version, String schemaVersion, String pitestVersion) {
    }

    public static Info read() {
        Properties properties = new Properties();
        try (InputStream in = ToolVersion.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + RESOURCE);
            }
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Info(properties.getProperty("version"), properties.getProperty("schemaVersion"),
            properties.getProperty("pitestVersion"));
    }
}
