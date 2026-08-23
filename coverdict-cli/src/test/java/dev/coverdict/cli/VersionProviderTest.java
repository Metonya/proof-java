package dev.coverdict.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class VersionProviderTest {

    @Test
    void readsVersionAndSchemaVersionFromResource() {
        String[] version = new VersionProvider().getVersion();
        assertTrue(version[0].startsWith("coverdict "), version[0]);
        assertTrue(version[1].startsWith("verdict schema "), version[1]);
    }

    @Test
    void missingResourceIsAnIllegalState() {
        // Simulates a jar built without the filtered properties file.
        VersionProvider provider = new VersionProvider(() -> null);
        IllegalStateException e = assertThrows(IllegalStateException.class, provider::getVersion);
        assertTrue(e.getMessage().contains("coverdict-version.properties"), e.getMessage());
    }

    @Test
    void unreadableResourceIsAnUncheckedIOException() {
        // Simulates a corrupted or truncated properties resource.
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("simulated corrupted resource");
            }
        };
        VersionProvider provider = new VersionProvider(() -> broken);
        assertThrows(UncheckedIOException.class, provider::getVersion);
    }

    @Test
    void missingPropertyKeysRenderAsNull() {
        // Properties.load succeeds but the expected keys are absent - the
        // provider does not crash, it surfaces "null" in the version string.
        InputStream empty = new ByteArrayInputStream(new byte[0]);
        VersionProvider provider = new VersionProvider(() -> empty);
        String[] version = provider.getVersion();
        assertEquals("coverdict null", version[0]);
        assertEquals("verdict schema null", version[1]);
    }

    @Test
    void charsetIsUtf8() {
        // A non-ASCII value must round-trip correctly, not mojibake.
        String properties = "version=0.1.0-café\n";
        InputStream in = new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8));
        VersionProvider provider = new VersionProvider(() -> in);
        assertEquals("coverdict 0.1.0-café", provider.getVersion()[0]);
    }
}
