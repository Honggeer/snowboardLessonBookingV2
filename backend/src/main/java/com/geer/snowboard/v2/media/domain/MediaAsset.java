package com.geer.snowboard.v2.media.domain;
import java.time.Instant;
public record MediaAsset(String id,String ownerId,String purpose,String contentType,long expectedSize,Long actualSize,
        String stagingKey,String sourceVersion,String frozenKey,String status,String requestKey,String fingerprint,
        Instant createdAt,Instant expiresAt,Instant unreferencedAt,Instant terminalAt,int width,int height,double duration,
        String errorCode,Instant stageCleanedAt,boolean quotaReleased,String sweepCursor,String sweepVersionCursor,Instant sweepAt) {
    public boolean terminal(){return !status.equals("UPLOADING")&&!status.equals("VERIFYING");}
}
