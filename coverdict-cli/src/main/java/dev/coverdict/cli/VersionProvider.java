package dev.coverdict.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import picocli.CommandLine.IVersionProvider;

/** Reads the version Maven filtered into {@code coverdict-version.properties}. */
class VersionProvider implements IVersionProvider {

    private static final String RESOURCE = "/coverdict-version.properties";

    @Override
    public String[] getVersion() {
        Properties properties = new Properties();
        try (InputStream in = VersionProvider.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + RESOURCE);
            }
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new String[] {
            "coverdict " + properties.getProperty("version"),
            "verdict schema " + properties.getProperty("schemaVersion")
        };
    }
}
