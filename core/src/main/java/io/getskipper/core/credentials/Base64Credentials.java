package io.getskipper.core.credentials;

import java.util.Base64;

/**
 * Decodes credentials from a Base64-encoded string.
 * Suitable for CI environments where the service account key is stored as a secret.
 *
 * <p>Set the {@code GOOGLE_CREDS_B64} environment variable to the Base64-encoded content
 * of the service account JSON file, then pass it here.
 */
public record Base64Credentials(String encoded) implements SkipperCredentials {

    @Override
    public byte[] resolve() {
        return Base64.getDecoder().decode(encoded);
    }
}
