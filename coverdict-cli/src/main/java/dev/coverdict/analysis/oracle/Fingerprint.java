package dev.coverdict.analysis.oracle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** docs/rules/README.md's fingerprint definition: sha256(ruleId + " " + moduleId + " " + repoRelativePath + " " + testMethodSignature), first 16 hex chars, lowercase. */
final class Fingerprint {

    private Fingerprint() {
    }

    static String compute(String ruleId, String moduleId, String repoRelativePath, String testMethodSignature) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = ruleId + " " + moduleId + " " + repoRelativePath + " " + testMethodSignature;
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available on every JVM", e);
        }
    }
}
