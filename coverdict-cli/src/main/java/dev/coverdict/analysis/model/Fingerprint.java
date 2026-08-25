package dev.coverdict.analysis.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * docs/rules/README.md's fingerprint definition: sha256(ruleId + " " +
 * moduleId + " " + repoRelativePath + " " + anchorSignature), first 16 hex
 * chars, lowercase. Shared by every rule family - the L0 oracle rules anchor
 * on a test method signature; L3's PSEUDO_TESTED_METHOD (M5) anchors on a
 * production method signature instead. Line numbers are deliberately
 * excluded so unrelated edits and moves don't change identity.
 */
public final class Fingerprint {

    private Fingerprint() {
    }

    public static String compute(String ruleId, String moduleId, String repoRelativePath, String anchorSignature) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = ruleId + " " + moduleId + " " + repoRelativePath + " " + anchorSignature;
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
