package io.getskipper.core.credentials;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Inline service account credentials constructed from individual fields.
 * Useful for programmatic configuration when fields are sourced from a secrets manager.
 */
public record ServiceAccountCredentials(
        String type,
        String projectId,
        String privateKeyId,
        String privateKey,
        String clientEmail,
        String clientId,
        String authUri,
        String tokenUri,
        String authProviderX509CertUrl,
        String clientX509CertUrl,
        String universeDomain) implements SkipperCredentials {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public byte[] resolve() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("type", type);
        map.put("project_id", projectId);
        map.put("private_key_id", privateKeyId);
        map.put("private_key", privateKey);
        map.put("client_email", clientEmail);
        map.put("client_id", clientId);
        map.put("auth_uri", authUri != null ? authUri : "https://accounts.google.com/o/oauth2/auth");
        map.put("token_uri", tokenUri != null ? tokenUri : "https://oauth2.googleapis.com/token");
        map.put("auth_provider_x509_cert_url",
                authProviderX509CertUrl != null ? authProviderX509CertUrl
                        : "https://www.googleapis.com/oauth2/v1/certs");
        map.put("client_x509_cert_url", clientX509CertUrl);
        map.put("universe_domain", universeDomain != null ? universeDomain : "googleapis.com");
        try {
            return MAPPER.writeValueAsBytes(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("[skipper] Failed to serialize service account credentials", e);
        }
    }
}
