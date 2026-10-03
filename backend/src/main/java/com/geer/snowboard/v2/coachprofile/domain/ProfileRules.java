package com.geer.snowboard.v2.coachprofile.domain;

import com.geer.snowboard.v2.sharedkernel.BusinessProblem;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ProfileRules {
    public static final Map<String,Integer> LIMITS = Map.ofEntries(
        Map.entry("displayName",40),Map.entry("tagline",160),Map.entry("bio",3000),Map.entry("philosophy",1000),
        Map.entry("specialties",200),Map.entry("languages",80),Map.entry("region",160),Map.entry("casiLevel",80),
        Map.entry("xhsAccount",80),Map.entry("xhsUrl",2048),Map.entry("douyinAccount",80),Map.entry("douyinUrl",2048),
        Map.entry("wechatId",64),Map.entry("heroId",36),Map.entry("certificateId",36),Map.entry("wechatQrId",36),
        Map.entry("videoId",36),Map.entry("posterId",36));
    public static final Map<String,String> MEDIA_FIELDS = Map.of("heroId","HERO","certificateId","CERTIFICATE",
        "wechatQrId","WECHAT_QR","videoId","HIGHLIGHT_VIDEO","posterId","VIDEO_POSTER");
    private ProfileRules() {}
    public static Map<String,String> normalize(Map<String,String> input) {
        if(input==null || !LIMITS.keySet().containsAll(input.keySet())) throw new BusinessProblem(400,"主页字段不合法");
        var result=new LinkedHashMap<String,String>();
        LIMITS.forEach((key,max)->{
            String value=input.get(key); value=value==null?"":value.strip();
            if(value.codePointCount(0,value.length())>max) throw new BusinessProblem(400,"主页字段过长："+key);
            if(key.endsWith("Id") && !key.equals("wechatId") && !value.isEmpty()
                    && !value.matches("[0-9a-fA-F-]{36}")) throw new BusinessProblem(400,"无效媒体引用");
            result.put(key,value);
        });
        link(result.get("xhsUrl"),Set.of("xiaohongshu.com","xhslink.com"));
        link(result.get("douyinUrl"),Set.of("douyin.com"));
        return Map.copyOf(result);
    }
    public static void publishable(Map<String,String> c) {
        if(blank(c,"displayName") || blank(c,"tagline") || blank(c,"heroId"))
            throw new BusinessProblem(400,"发布前请填写称呼、一句话介绍并上传人物照片");
        pair(c,"casiLevel","certificateId","认证说明和证书图片须同时填写");
        pair(c,"xhsAccount","xhsUrl","小红书账号和链接须同时填写");
        pair(c,"douyinAccount","douyinUrl","抖音账号和链接须同时填写");
        pair(c,"videoId","posterId","视频和封面须同时上传");
        if(!blank(c,"wechatQrId") && blank(c,"wechatId")) throw new BusinessProblem(400,"请填写微信号");
    }
    private static boolean blank(Map<String,String> c,String key) {return c.getOrDefault(key,"").isBlank();}
    private static void pair(Map<String,String> c,String a,String b,String message) {
        if(blank(c,a)!=blank(c,b)) throw new BusinessProblem(400,message);
    }
    private static void link(String value,Set<String> domains) {
        if(value.isEmpty())return;
        try {
            URI uri=URI.create(value); String host=uri.getHost();
            if(!"https".equalsIgnoreCase(uri.getScheme()) || host==null || uri.getUserInfo()!=null
                    || (uri.getPort()!=-1 && uri.getPort()!=443)
                    || domains.stream().noneMatch(d->host.equalsIgnoreCase(d)||host.toLowerCase(java.util.Locale.ROOT).endsWith("."+d)))
                throw new IllegalArgumentException();
        }catch(IllegalArgumentException e){throw new BusinessProblem(400,"社交链接须为对应平台的 HTTPS 主页链接");}
    }
}
