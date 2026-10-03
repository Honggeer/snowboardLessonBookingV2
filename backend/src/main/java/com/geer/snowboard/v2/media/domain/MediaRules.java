package com.geer.snowboard.v2.media.domain;
import com.geer.snowboard.v2.sharedkernel.BusinessProblem;
import java.util.Set;
public final class MediaRules {
    public static final long IMAGE_LIMIT=8L*1024*1024,VIDEO_LIMIT=100L*1024*1024,QUOTA=1024L*1024*1024;
    public static final Set<String> PURPOSES=Set.of("HERO","CERTIFICATE","WECHAT_QR","VIDEO_POSTER","HIGHLIGHT_VIDEO");
    private MediaRules(){}
    public static long limit(String purpose){return "HIGHLIGHT_VIDEO".equals(purpose)?VIDEO_LIMIT:IMAGE_LIMIT;}
    public static void upload(String purpose,String type,long size){
        if(!PURPOSES.contains(purpose==null?"":purpose))throw new BusinessProblem(400,"不支持的媒体用途");
        if(size>limit(purpose))throw new BusinessProblem(413,"文件超过上传上限");
        if(size<=0)throw new BusinessProblem(400,"文件不能为空");
        boolean valid="HIGHLIGHT_VIDEO".equals(purpose)?"video/mp4".equals(type):Set.of("image/jpeg","image/png").contains(type==null?"":type);
        if(!valid)throw new BusinessProblem(400,"图片只支持 JPG/PNG，视频只支持 MP4");
    }
}
