package dev.coverdict.config;

/**
 * A config file that exists but cannot be trusted: missing when explicitly
 * named, oversized, malformed, or carrying an unknown key or a wrong value
 * type. Always an invalid invocation (exit 2, no JSON written) - the run never
 * proceeds on a half-understood configuration (hard rule 3a).
 */
public class ConfigException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConfigException(String message) {
        super(message);
    }
}
