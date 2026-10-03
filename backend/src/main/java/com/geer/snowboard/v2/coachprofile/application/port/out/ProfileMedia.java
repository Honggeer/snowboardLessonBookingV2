package com.geer.snowboard.v2.coachprofile.application.port.out;
import com.geer.snowboard.v2.coachprofile.application.port.in.CoachProfileOperations.Media;
import java.util.Map;
public interface ProfileMedia {
    void replace(String ownerId,String slot,Map<String,String> purposeIds);
    Map<String,Media> previews(String ownerId,Map<String,String> purposeIds);
    Map<String,Media> published(Map<String,String> purposeIds);
}
