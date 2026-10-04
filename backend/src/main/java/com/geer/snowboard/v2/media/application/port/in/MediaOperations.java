package com.geer.snowboard.v2.media.application.port.in;
import com.geer.snowboard.v2.sharedkernel.Actor;
import java.time.Instant;
import java.util.Map;
public interface MediaOperations {
    record Link(String id,String url,Instant expiresAt,int width,int height,double duration,long size){}
    record Upload(String id,String purpose,String status,long size,String uploadUrl,Map<String,String> headers,Instant expiresAt,
                  Link preview,String errorCode){}
    record Requested(Upload upload,boolean created){}
    Requested request(Actor actor,String purpose,String type,long size,String key);
    Upload complete(Actor actor,String id);
    Upload status(Actor actor,String id);
    void replaceReferences(String owner,String slot,Map<String,String> purposeIds);
    void replaceReferences(String owner,String consumer,String slot,Map<String,String> purposeIds);
    Map<String,Link> previews(String owner,Map<String,String> purposeIds);
    Map<String,Link> published(Map<String,String> purposeIds);
    Map<String,Link> publishedFor(String consumer,Map<String,String> purposeIds);
}
