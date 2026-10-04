package com.geer.snowboard.v2.catalog.adapter.out.module;

import com.geer.snowboard.v2.catalog.application.port.in.CourseOperations.Cover;
import com.geer.snowboard.v2.catalog.application.port.out.CourseMedia;
import com.geer.snowboard.v2.media.application.port.in.MediaOperations;
import com.geer.snowboard.v2.sharedkernel.BusinessProblem;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class CourseMediaAdapter implements CourseMedia {
    private final MediaOperations media;
    public CourseMediaAdapter(MediaOperations media) { this.media = media; }
    public void save(String owner,String courseId,String assetId) {
        media.replaceReferences(owner,"COURSE:"+courseId,"PUBLISHED",assetId==null?Map.of():Map.of("COURSE_COVER",assetId));
    }
    public Cover published(String courseId,String assetId) {
        if(assetId==null)return null;
        try {
            var link=media.publishedFor("COURSE:"+courseId,Map.of("COURSE_COVER",assetId)).get("COURSE_COVER");
            return link==null?null:new Cover(link.id(),link.url(),link.expiresAt(),link.width(),link.height());
        } catch(BusinessProblem problem) {
            if(problem.status()!=503)throw problem;
            return null;
        }
    }
}
