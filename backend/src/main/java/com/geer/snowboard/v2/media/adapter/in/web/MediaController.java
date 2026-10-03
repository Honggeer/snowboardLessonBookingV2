package com.geer.snowboard.v2.media.adapter.in.web;
import com.geer.snowboard.v2.bootstrap.ActorResolver;
import com.geer.snowboard.v2.media.application.port.in.MediaOperations;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
public final class MediaController {
    record UploadRequest(String purpose,String contentType,long size){}
    record LinkReply(String id,String url,Instant expiresAt,int width,int height,double duration,long size){}
    record UploadReply(String id,String purpose,String status,long size,String uploadUrl,Map<String,String> headers,Instant expiresAt,LinkReply preview,String errorCode){}
    private final MediaOperations media;private final ActorResolver actors;
    public MediaController(MediaOperations media,ActorResolver actors){this.media=media;this.actors=actors;}
    @PostMapping("/api/coach/media/uploads") public ResponseEntity<UploadReply> request(@RequestBody UploadRequest r,@RequestHeader("Idempotency-Key") String key){var result=media.request(actors.current(),r.purpose(),r.contentType(),r.size(),key);return ResponseEntity.status(result.created()?201:200).body(reply(result.upload()));}
    @PostMapping("/api/coach/media/uploads/{id}/complete") public ResponseEntity<UploadReply> complete(@PathVariable String id){var u=media.complete(actors.current(),id);return ResponseEntity.status(u.status().equals("VERIFYING")?202:200).body(reply(u));}
    @GetMapping("/api/coach/media/uploads/{id}") public ResponseEntity<UploadReply> status(@PathVariable String id){return ResponseEntity.ok().header("Cache-Control","no-store").body(reply(media.status(actors.current(),id)));}
    private static UploadReply reply(MediaOperations.Upload u){var m=u.preview();return new UploadReply(u.id(),u.purpose(),u.status(),u.size(),u.uploadUrl(),u.headers(),u.expiresAt(),m==null?null:new LinkReply(m.id(),m.url(),m.expiresAt(),m.width(),m.height(),m.duration(),m.size()),u.errorCode());}
}
