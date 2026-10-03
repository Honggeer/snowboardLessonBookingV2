package com.geer.snowboard.v2.coachprofile.application.port.in;
import com.geer.snowboard.v2.sharedkernel.Actor;
import java.time.Instant;
import java.util.Map;
public interface CoachProfileOperations {
    record Media(String id,String url,Instant expiresAt,int width,int height,double duration,long size) {}
    record View(boolean published,long version,long publishedVersion,Map<String,String> content,Map<String,Media> media) {}
    View published();
    View draft(Actor actor);
    View save(Actor actor,long expectedVersion,Map<String,String> content);
    long publish(Actor actor,long draftVersion,String key);
}
