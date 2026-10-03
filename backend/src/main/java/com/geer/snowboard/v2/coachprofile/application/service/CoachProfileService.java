package com.geer.snowboard.v2.coachprofile.application.service;
import com.geer.snowboard.v2.coachprofile.application.port.in.CoachProfileOperations;
import com.geer.snowboard.v2.coachprofile.application.port.out.ProfileStore;
import com.geer.snowboard.v2.coachprofile.application.port.out.ProfileMedia;
import com.geer.snowboard.v2.coachprofile.domain.ProfileRules;
import com.geer.snowboard.v2.sharedkernel.*;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
@Service
public class CoachProfileService implements CoachProfileOperations {
    private final ProfileStore store; private final ProfileMedia media;
    public CoachProfileService(ProfileStore store,ProfileMedia media){this.store=store;this.media=media;}
    @Override public View published(){
        var p=store.read(); if(p==null || p.publishedVersion()==0)return new View(false,0,0,Map.of(),Map.of());
        return new View(true,p.publishedVersion(),p.publishedVersion(),p.published(),media.published(ids(p.published())));
    }
    @Override public View draft(Actor actor){
        actor.require("COACH");var p=store.read();
        if(p==null)return new View(false,0,0,ProfileRules.normalize(Map.of("displayName","GEER")),Map.of());
        own(actor,p);return draftView(p);
    }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public View save(Actor actor,long expectedVersion,Map<String,String> content){
        actor.require("COACH");var c=ProfileRules.normalize(content);var p=store.lock(actor.id()); own(actor,p);
        if(p.draftVersion()!=expectedVersion)throw new BusinessProblem(409,"草稿版本已更新，请重新载入");
        media.replace(actor.id(),"DRAFT",ids(c));store.save(expectedVersion+1,c);
        // Signing URLs needs no remote call and is kept out of the locking path.
        return new View(p.publishedVersion()>0,expectedVersion+1,p.publishedVersion(),c,Map.of());
    }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public long publish(Actor actor,long version,String key){
        actor.require("COACH");key=RequestKeys.key(key);String fingerprint=RequestKeys.fingerprint("PUBLISH:"+version);
        var p=store.lock(actor.id());own(actor,p);var old=store.publication(actor.id(),key);
        if(old!=null){if(!fingerprint.equals(old.fingerprint()))throw new BusinessProblem(409,"请求键已用于其他发布内容");return old.version();}
        if(version!=p.draftVersion())throw new BusinessProblem(409,"草稿版本已更新，请先保存并重新预览");
        ProfileRules.publishable(p.draft());media.replace(actor.id(),"PUBLISHED",ids(p.draft()));
        long published=p.publishedVersion()+1;store.publish(published,p.draft());store.recordPublication(actor.id(),key,fingerprint,published);
        return published;
    }
    private View draftView(ProfileStore.Snapshot p){return new View(p.publishedVersion()>0,p.draftVersion(),p.publishedVersion(),p.draft(),media.previews(p.ownerId(),ids(p.draft())));}
    private static void own(Actor a,ProfileStore.Snapshot p){if(!a.id().equals(p.ownerId()))throw new BusinessProblem(403,"无权编辑此主页");}
    private static Map<String,String> ids(Map<String,String> c){var ids=new TreeMap<String,String>();ProfileRules.MEDIA_FIELDS.forEach((f,p)->{String id=c.get(f);if(id!=null&&!id.isBlank())ids.put(p,id);});return ids;}
}
