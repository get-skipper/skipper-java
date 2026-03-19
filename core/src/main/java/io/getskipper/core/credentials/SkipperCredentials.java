package io.getskipper.core.credentials;

/**
 * Represents a strategy for resolving Google service account credentials.
 * Call {@link #resolve()} to obtain the raw JSON bytes of the service account key.
 */
public sealed interface SkipperCredentials
        permits FileCredentials, Base64Credentials, ServiceAccountCredentials {

    /**
     * Resolves the credentials and returns the raw JSON bytes of the service account key.
     *
     * @return raw UTF-8 JSON bytes of a Google service account key
     */
    byte[] resolve();
}
