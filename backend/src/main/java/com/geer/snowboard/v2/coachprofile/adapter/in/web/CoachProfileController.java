package com.geer.snowboard.v2.coachprofile.adapter.in.web;
import com.geer.snowboard.v2.bootstrap.ActorResolver;
import com.geer.snowboard.v2.coachprofile.application.port.in.CoachProfileOperations;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
public final class CoachProfileController {
    record SaveRequest(long expectedVersion,Map<String,String> content) {}
    record PublishRequest(long draftVersion) {}
    record PublishReply(long version) {}
    record MediaReply(String id,String url,Instant expiresAt,int width,int height,double duration,long size) {}
    record ProfileReply(boolean published,long version,long publishedVersion,Map<String,String> content,Map<String,MediaReply> media) {}
    private final CoachProfileOperations profiles;private final ActorResolver actors;
    public CoachProfileController(CoachProfileOperations profiles,ActorResolver actors){this.profiles=profiles;this.actors=actors;}
    @GetMapping("/api/coach-profile") public ResponseEntity<ProfileReply> published(){return ResponseEntity.ok().header("Cache-Control","no-store").body(reply(profiles.published()));}
    @GetMapping("/api/coach/profile/draft") public ResponseEntity<ProfileReply> draft(){return ResponseEntity.ok().header("Cache-Control","no-store").body(reply(profiles.draft(actors.current())));}
    @PutMapping("/api/coach/profile/draft") public ProfileReply save(@RequestBody SaveRequest input){return reply(profiles.save(actors.current(),input.expectedVersion(),input.content()));}
    @PostMapping("/api/coach/profile/publish") public PublishReply publish(@RequestBody PublishRequest input,@RequestHeader("Idempotency-Key") String key){return new PublishReply(profiles.publish(actors.current(),input.draftVersion(),key));}
    private static ProfileReply reply(CoachProfileOperations.View v){return new ProfileReply(v.published(),v.version(),v.publishedVersion(),v.content(),v.media().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,e->{var m=e.getValue();return new MediaReply(m.id(),m.url(),m.expiresAt(),m.width(),m.height(),m.duration(),m.size());})));}
}
