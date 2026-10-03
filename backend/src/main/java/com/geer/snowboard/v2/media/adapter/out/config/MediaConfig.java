package com.geer.snowboard.v2.media.adapter.out.config;
import com.geer.snowboard.v2.media.adapter.out.storage.MediaStorageSettings;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import java.util.concurrent.*;
@Configuration
@EnableConfigurationProperties(MediaStorageSettings.class)
public class MediaConfig {
    @Bean(name="mediaExecutor",destroyMethod="shutdownNow")
    ExecutorService mediaExecutor(){
        return new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{var t=new Thread(r,"media-io");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    }
}
