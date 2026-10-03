package com.geer.snowboard.v2.media.adapter.out.storage;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("media.storage")
public record MediaStorageSettings(String mode,String region,String endpoint,String publicEndpoint,
        String stagingBucket,String frozenBucket,String cdnBaseUrl,String keyPairId,String signingKeyPath) {}
