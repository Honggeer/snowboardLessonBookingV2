package com.geer.snowboard.v2.coachprofile.adapter.out.module;
import com.geer.snowboard.v2.coachprofile.application.port.out.ProfileMedia;
import com.geer.snowboard.v2.coachprofile.application.port.in.CoachProfileOperations.Media;
import com.geer.snowboard.v2.media.application.port.in.MediaOperations;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
@Component
public final class ProfileMediaAdapter implements ProfileMedia {
    private final MediaOperations media;
    public ProfileMediaAdapter(MediaOperations media){this.media=media;}
    public void replace(String owner,String slot,Map<String,String> ids){media.replaceReferences(owner,slot,ids);}
    public Map<String,Media> previews(String owner,Map<String,String> ids){return map(media.previews(owner,ids));}
    public Map<String,Media> published(Map<String,String> ids){return map(media.published(ids));}
    private static Map<String,Media> map(Map<String,MediaOperations.Link> links){return links.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,e->{var m=e.getValue();return new Media(m.id(),m.url(),m.expiresAt(),m.width(),m.height(),m.duration(),m.size());}));}
}
