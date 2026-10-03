package com.geer.snowboard.v2.media.application.service;
import com.geer.snowboard.v2.media.application.port.in.MediaOperations;
import com.geer.snowboard.v2.media.application.port.out.*;
import com.geer.snowboard.v2.media.domain.*;
import com.geer.snowboard.v2.sharedkernel.*;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
@Service
public class MediaService implements MediaOperations {
    private final MediaStore store;private final MediaObjects objects;private final Clock clock;
    public MediaService(MediaStore store,MediaObjects objects,Clock clock){this.store=store;this.objects=objects;this.clock=clock;}
    @Override public Requested request(Actor actor,String purpose,String type,long size,String key){
        actor.require("COACH");MediaRules.upload(purpose,type,size);key=RequestKeys.key(key);
        String fp=RequestKeys.fingerprint(purpose+"\0"+type+"\0"+size);
        // Reserve and persist before signing. A failed signer leaves a traceable expiring asset.
        var reservation=reserve(actor,purpose,type,size,key,fp);return new Requested(view(reservation.upload(),true),reservation.created());
    }
    private record Reserved(MediaAsset upload,boolean created){}
    private Reserved reserve(Actor actor,String purpose,String type,long size,String key,String fp){
        return reservations.reserve(actor,purpose,type,size,key,fp);
    }
    // A separate transactional bean keeps external signing outside a database lock.
    private MediaReservations reservations;
    @org.springframework.beans.factory.annotation.Autowired public void reservations(MediaReservations value){this.reservations=value;}
    @Override public Upload complete(Actor actor,String id){
        actor.require("COACH");var a=owned(actor,id);
        if(!a.status().equals("UPLOADING"))return view(a,false);
        if(clock.instant().isAfter(a.createdAt().plus(Duration.ofHours(24))))throw new BusinessProblem(409,"上传已过期，请重新选择文件");
        MediaObjects.Uploaded uploaded;
        try{uploaded=objects.uploaded(a);}catch(MediaFailure e){throw new BusinessProblem(503,"无法确认文件，请稍后重试");}
        if(uploaded.versionId()==null||uploaded.versionId().isBlank()||uploaded.versionId().equals("null"))
            throw new BusinessProblem(503,"存储版本配置异常，文件暂不可用");
        if(uploaded.size()>MediaRules.limit(a.purpose()))throw new BusinessProblem(413,"实际文件超过上传上限");
        if(uploaded.size()!=a.expectedSize())throw new BusinessProblem(400,"上传大小不匹配，请重新选择文件");
        reservations.complete(actor,id,uploaded,clock.instant());return view(owned(actor,id),false);
    }
    @Override public Upload status(Actor actor,String id){actor.require("COACH");return view(owned(actor,id),false);}
    private MediaAsset owned(Actor actor,String id){var a=store.get(id);if(a==null||!actor.id().equals(a.ownerId()))throw new BusinessProblem(404,"上传不存在");return a;}
    private Upload view(MediaAsset a,boolean ticket){
        String url=null;var headers=Map.<String,String>of();
        if(ticket&&a.status().equals("UPLOADING")&&clock.instant().isBefore(a.expiresAt())){
            try{url=objects.uploadUrl(a);}catch(MediaFailure e){throw new BusinessProblem(503,"媒体存储暂不可用，请稍后重试");}
            headers=Map.of("Content-Type",a.contentType());
        }
        Link preview=a.status().equals("READY")?link(a):null;
        return new Upload(a.id(),a.purpose(),a.status(),a.expectedSize(),url,headers,a.expiresAt(),preview,a.errorCode());
    }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public void replaceReferences(String owner,String slot,Map<String,String> ids){
        if(!Set.of("DRAFT","PUBLISHED").contains(slot))throw new IllegalArgumentException("slot");
        var all=new TreeSet<String>(store.referenceIds(slot));all.addAll(ids.values());
        for(String id:all){var a=store.lock(id);if(ids.containsValue(id)){
            if(a==null||!owner.equals(a.ownerId())||!a.status().equals("READY"))throw new BusinessProblem(409,"素材尚未验证完成或已失效");
            if(!a.id().equals(ids.get(a.purpose())))throw new BusinessProblem(400,"素材用途不匹配");
        }}
        store.replaceReferences(slot,ids,clock.instant());
    }
    @Override public Map<String,Link> previews(String owner,Map<String,String> ids){
        var result=new HashMap<String,Link>();ids.forEach((p,id)->{var a=store.get(id);if(a!=null&&a.ownerId().equals(owner)&&a.purpose().equals(p)&&a.status().equals("READY"))result.put(p,link(a));});return Map.copyOf(result);
    }
    @Override public Map<String,Link> published(Map<String,String> ids){
        var result=new HashMap<String,Link>();ids.forEach((p,id)->{var a=store.get(id);if(a!=null&&a.status().equals("READY")&&a.purpose().equals(p)&&store.publishedReference(id))result.put(p,link(a));});return Map.copyOf(result);
    }
    private Link link(MediaAsset a){Instant expires=clock.instant().plusSeconds(900);String url;
        try{url=objects.readUrl(a,expires);}catch(MediaFailure e){throw new BusinessProblem(503,"媒体地址暂不可用，请重试");}
        return new Link(a.id(),url,expires,a.width(),a.height(),a.duration(),a.actualSize()==null?a.expectedSize():a.actualSize());}
    @Service public static class MediaReservations {
        private final MediaStore store;private final Clock clock;
        public MediaReservations(MediaStore store,Clock clock){this.store=store;this.clock=clock;}
        @Transactional(isolation=Isolation.READ_COMMITTED)
        public Reserved reserve(Actor actor,String purpose,String type,long size,String key,String fp){
            long reserved=store.lockQuota(actor.id());var existing=store.byRequest(actor.id(),key);
            if(existing!=null){if(!fp.equals(existing.fingerprint()))throw new BusinessProblem(409,"请求键已用于其他文件");return new Reserved(existing,false);}
            if(store.activeUploads(actor.id())>0)throw new BusinessProblem(409,"请先完成当前上传或等待上传过期");
            Instant now=clock.instant();
            if(store.hourlyUploads(actor.id(),now.minusSeconds(3600))>=20)throw new BusinessProblem(429,"上传太频繁，请一小时后重试");
            if(reserved+size>MediaRules.QUOTA)throw new BusinessProblem(409,"媒体空间已满，请等待旧素材清理");
            String id=UUID.randomUUID().toString();
            var a=new MediaAsset(id,actor.id(),purpose,type,size,null,"staging/"+actor.id()+"/"+id,null,"frozen/"+actor.id()+"/"+id,"UPLOADING",key,fp,now,now.plusSeconds(600),now,null,0,0,0,null,null,false,null,null,null);
            store.insert(a);store.reserve(actor.id(),size);return new Reserved(a,true);
        }
        @Transactional(isolation=Isolation.READ_COMMITTED)
        public void complete(Actor actor,String id,MediaObjects.Uploaded uploaded,Instant now){
            var a=store.lock(id);if(a==null||!actor.id().equals(a.ownerId()))throw new BusinessProblem(404,"上传不存在");
            if(a.status().equals("UPLOADING"))store.verifying(id,uploaded.versionId(),uploaded.size(),now);
            else if(Set.of("DELETING","DELETED","EXPIRED").contains(a.status()))throw new BusinessProblem(409,"上传已过期");
        }
    }
}
