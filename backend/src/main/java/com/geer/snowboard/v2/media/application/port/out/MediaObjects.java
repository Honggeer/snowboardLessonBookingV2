package com.geer.snowboard.v2.media.application.port.out;
import com.geer.snowboard.v2.media.domain.MediaAsset;
import java.time.Instant;
public interface MediaObjects {
    record Uploaded(long size,String versionId){}
    record Verified(long size,int width,int height,double duration){}
    record Sweep(boolean complete,String keyCursor,String versionCursor){}
    String uploadUrl(MediaAsset asset);
    Uploaded uploaded(MediaAsset asset);
    Verified freezeAndVerify(MediaAsset asset);
    String readUrl(MediaAsset asset,Instant expiresAt);
    Sweep sweep(MediaAsset asset,boolean frozen,String keyCursor,String versionCursor);
}
