package com.geer.snowboard.v2.media.adapter.out.inspection;

import com.geer.snowboard.v2.media.application.port.out.ContentProbe;
import com.geer.snowboard.v2.media.application.port.out.MediaFailure;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class MediaContentProbe implements ContentProbe {
    private final String executable;
    private final JsonMapper json=JsonMapper.builder().build();
    public MediaContentProbe(){this("");}
    @Autowired public MediaContentProbe(@Value("${media.ffprobe-path:}") String executable){
        this.executable=executable.isBlank()?defaultExecutable():executable;
    }
    private static String defaultExecutable(){
        for(String name:new String[]{".local/bin/ffprobe","backend/.local/bin/ffprobe"})
            if(Files.isExecutable(Path.of(name)))return Path.of(name).toAbsolutePath().toString();
        return "ffprobe";
    }
    @Override public Result inspect(Path file,String purpose,String type){
        return "HIGHLIGHT_VIDEO".equals(purpose)?video(file):image(file,type);
    }
    private Result image(Path file,String type){
        try(var stream=ImageIO.createImageInputStream(file.toFile())){
            if(stream==null)throw invalid();
            Iterator<ImageReader> readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext())throw invalid();ImageReader reader=readers.next();
            try{
                reader.setInput(stream,true,true);String format=reader.getFormatName();
                if(!("image/png".equals(type)&&format.equalsIgnoreCase("png")
                    || "image/jpeg".equals(type)&&(format.equalsIgnoreCase("jpeg")||format.equalsIgnoreCase("jpg"))))throw invalid();
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<=0||height<=0||width>4096||height>4096)throw new MediaFailure("IMAGE_DIMENSIONS",true);
                if(reader.read(0)==null)throw invalid();return new Result(width,height,0);
            }finally{reader.dispose();}
        }catch(IOException|IllegalArgumentException e){throw invalid();}
    }
    private Result video(Path file){
        Process process=null;
        var readers=Executors.newFixedThreadPool(2,r->{var t=new Thread(r,"media-probe-output");t.setDaemon(true);return t;});
        try{
            process=new ProcessBuilder(executable,"-v","error","-protocol_whitelist","file","-count_frames","-show_format","-show_streams","-of","json",file.toAbsolutePath().toString()).start();
            Process running=process;
            Future<byte[]> stdout=readers.submit(()->bounded(running.getInputStream()));
            Future<byte[]> stderr=readers.submit(()->bounded(running.getErrorStream()));
            if(!process.waitFor(30,TimeUnit.SECONDS)){process.destroyForcibly();throw new MediaFailure("PROBE_TIMEOUT",true);}
            byte[] output=stdout.get(2,TimeUnit.SECONDS);byte[] errors=stderr.get(2,TimeUnit.SECONDS);
            if(process.exitValue()!=0||errors.length>0)throw invalid();JsonNode data=json.readTree(output);
            JsonNode format=data.path("format"),streams=data.path("streams");
            String name=format.path("format_name").asString("");
            String brand=format.path("tags").path("major_brand").asString("");
            if(!name.contains("mp4")||!streams.isArray()||!java.util.Set.of("isom","iso2","iso3","iso4","iso5","iso6","iso7","iso8","iso9","mp41","mp42","avc1","dash","M4V ","MSNV").contains(brand))throw invalid();
            double duration=number(format.path("duration"));if(!Double.isFinite(duration)||duration<=0||duration>120)throw new MediaFailure("VIDEO_DURATION",true);
            int video=0,audio=0,width=0,height=0;
            for(JsonNode stream:streams){
                String kind=stream.path("codec_type").asString("");
                if(kind.equals("video")){
                    video++;if(!stream.path("codec_name").asString("").equals("h264")||!stream.path("pix_fmt").asString("").equals("yuv420p"))throw new MediaFailure("VIDEO_CODEC",true);
                    width=stream.path("width").asInt(0);height=stream.path("height").asInt(0);
                    double rate=fraction(stream.path("avg_frame_rate").asString("0/0"));
                    if(!Double.isFinite(rate)||rate<=0||rate>60||Math.max(width,height)>1920||Math.min(width,height)>1080||width<=0||height<=0)throw new MediaFailure("VIDEO_DIMENSIONS",true);
                }else if(kind.equals("audio")){
                    audio++;if(!stream.path("codec_name").asString("").equals("aac"))throw new MediaFailure("VIDEO_CODEC",true);
                }else throw invalid();
            }
            if(video!=1||audio>1)throw invalid();return new Result(width,height,duration);
        }catch(IOException e){throw new MediaFailure("PROBE_UNAVAILABLE",false);}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new MediaFailure("PROBE_TIMEOUT",false);}
        catch(ExecutionException|TimeoutException e){throw invalid();}
        catch(IllegalArgumentException e){throw invalid();}
        finally{if(process!=null&&process.isAlive())process.destroyForcibly();readers.shutdownNow();}
    }
    private static byte[] bounded(InputStream in)throws IOException{
        try(in){byte[] data=in.readNBytes(1024*1024+1);if(data.length>1024*1024)throw new IOException("probe output limit");return data;}
    }
    private static double fraction(String value){try{String[] parts=value.split("/");return Double.parseDouble(parts[0])/(parts.length>1?Double.parseDouble(parts[1]):1);}catch(RuntimeException e){return Double.NaN;}}
    private static double number(JsonNode n){try{return Double.parseDouble(n.asString(""));}catch(RuntimeException e){return Double.NaN;}}
    private static MediaFailure invalid(){return new MediaFailure("INVALID_CONTENT",true);}
}
