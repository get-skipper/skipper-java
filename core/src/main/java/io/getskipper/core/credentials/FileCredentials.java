package io.getskipper.core.credentials;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads credentials from a JSON file on disk.
 * Suitable for local development where the service account key file is present.
 */
public record FileCredentials(String path) implements SkipperCredentials {

    @Override
    public byte[] resolve() {
        try {
            return Files.readAllBytes(Path.of(path));
        } catch (IOException e) {
            throw new UncheckedIOException("[skipper] Failed to read credentials file: " + path, e);
        }
    }
}
