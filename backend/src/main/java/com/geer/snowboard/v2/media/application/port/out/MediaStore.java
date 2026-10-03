package com.geer.snowboard.v2.media.application.port.out;
import com.geer.snowboard.v2.media.domain.MediaAsset;
import java.time.Instant;
import java.util.List;
import java.util.Map;
public interface MediaStore {
    record Job(long id,String assetId,String kind,int attempts,long token){}
    long lockQuota(String owner);
    void reserve(String owner,long size);
    MediaAsset get(String id);
    MediaAsset lock(String id);
    MediaAsset byRequest(String owner,String key);
    long activeUploads(String owner);
    long hourlyUploads(String owner,Instant since);
    void insert(MediaAsset asset);
    void verifying(String id,String sourceVersion,long actualSize,Instant now);
    List<String> referenceIds(String slot);
    void replaceReferences(String slot,Map<String,String> ids,Instant now);
    boolean publishedReference(String id);
    boolean referenced(String id);
    Job claim(Instant now,Instant leaseUntil);
    boolean claimed(Job job);
    void ready(Job job,MediaObjects.Verified result,Instant now);
    void failed(Job job,String state,String code,Instant now,Instant retryAt);
    List<MediaAsset> cleanupCandidates(Instant now,int limit);
    void expire(String id,Instant now);
    void enqueueCleanup(String id,Instant now);
    boolean beginCleanup(Job job,Instant now);
    void skippedCleanup(Job job,Instant now);
    void cleaned(Job job,boolean deleted,String keyCursor,String versionCursor,Instant now);
}
