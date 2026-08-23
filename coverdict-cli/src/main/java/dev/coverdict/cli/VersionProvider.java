package dev.coverdict.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.function.Supplier;

import picocli.CommandLine.IVersionProvider;

/** Reads the version Maven filtered into {@code coverdict-version.properties}. */
class VersionProvider implements IVersionProvider {

    private static final String RESOURCE = "/coverdict-version.properties";

    private final Supplier<InputStream> resourceLoader;

    /** Used by picocli, which instantiates version providers via a no-arg constructor. */
    VersionProvider() {
        this(() -> VersionProvider.class.getResourceAsStream(RESOURCE));
    }

    /** Package-private: lets tests exercise the missing-resource and malformed-properties paths. */
    VersionProvider(Supplier<InputStream> resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public String[] getVersion() {
        Properties properties = new Properties();
        try (InputStream in = resourceLoader.get()) {
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
