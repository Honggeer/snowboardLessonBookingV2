package com.geer.snowboard.v2.media.application.port.out;
import java.nio.file.Path;
public interface ContentProbe {
    record Result(int width,int height,double duration){}
    Result inspect(Path file,String purpose,String contentType);
}
