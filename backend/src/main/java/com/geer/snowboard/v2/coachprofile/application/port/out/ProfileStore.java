package com.geer.snowboard.v2.coachprofile.application.port.out;
import java.util.Map;
public interface ProfileStore {
    record Snapshot(String ownerId,long draftVersion,long publishedVersion,Map<String,String> draft,Map<String,String> published) {}
    record Publication(String fingerprint,long version) {}
    Snapshot read();
    Snapshot lock(String ownerId);
    void save(long version,Map<String,String> content);
    void publish(long version,Map<String,String> content);
    Publication publication(String owner,String key);
    void recordPublication(String owner,String key,String fingerprint,long version);
}
