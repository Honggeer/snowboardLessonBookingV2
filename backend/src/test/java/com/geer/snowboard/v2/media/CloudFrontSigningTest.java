package com.geer.snowboard.v2.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.geer.snowboard.v2.media.adapter.out.inspection.MediaContentProbe;
import com.geer.snowboard.v2.media.adapter.out.storage.MediaStorageSettings;
import com.geer.snowboard.v2.media.adapter.out.storage.S3MediaObjects;
import com.geer.snowboard.v2.media.domain.MediaAsset;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CloudFrontSigningTest {
    @TempDir Path temporary;

    @Test void signsFrozenResourceWithTheConfiguredKeyAndExpiryWithoutS3Access() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        Path key = temporary.resolve("test-private.pem");
        Files.writeString(key, "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n");
        var settings = new MediaStorageSettings("aws", "us-east-1", null, null,
                "test-stage", "test-frozen", "https://media.example.test", "test-key-id", key.toString());
        var storage = new S3MediaObjects(settings, new MediaContentProbe());
        Instant now = Instant.now(), expires = now.plusSeconds(900);
        var asset = new MediaAsset("test-id", "test-coach", "HERO", "image/png", 100, 100L,
                "staging/test-id", "source-version", "frozen/test-id", "READY", "request-key", "fingerprint",
                now, now.plusSeconds(600), now, now, 40, 30, 0, null, null, false, null, null, null);
        try {
            URI signed = URI.create(storage.readUrl(asset, expires));
            assertThat(signed.getScheme()).isEqualTo("https");
            assertThat(signed.getHost()).isEqualTo("media.example.test");
            assertThat(signed.getPath()).isEqualTo("/frozen/test-id");
            var query = Arrays.stream(signed.getRawQuery().split("&"))
                    .map(part -> part.split("=", 2))
                    .collect(Collectors.toMap(part -> part[0], part -> part[1]));
            assertThat(query.get("Expires")).isEqualTo(Long.toString(expires.getEpochSecond()));
            assertThat(query.get("Key-Pair-Id")).isEqualTo("test-key-id");
            String policy = "{\"Statement\":[{\"Resource\":\"https://media.example.test/frozen/test-id\","
                    + "\"Condition\":{\"DateLessThan\":{\"AWS:EpochTime\":" + expires.getEpochSecond() + "}}}]}";
            var verifier = Signature.getInstance("SHA1withRSA");
            verifier.initVerify(pair.getPublic());
            verifier.update(policy.getBytes(StandardCharsets.UTF_8));
            String encoded = query.get("Signature").replace('-', '+').replace('_', '=').replace('~', '/');
            assertThat(verifier.verify(Base64.getDecoder().decode(encoded))).isTrue();
        } finally {
            storage.closeClients();
        }
    }
}
