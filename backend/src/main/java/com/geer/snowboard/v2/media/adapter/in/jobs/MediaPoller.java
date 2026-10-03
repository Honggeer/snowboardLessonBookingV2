package com.geer.snowboard.v2.media.adapter.in.jobs;
import com.geer.snowboard.v2.media.application.port.in.MediaJobs;
import java.util.concurrent.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name="media.worker.enabled",havingValue="true")
public final class MediaPoller {
    private final MediaJobs jobs;
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"media-poller");t.setDaemon(true);return t;});
    private static final System.Logger log=System.getLogger(MediaPoller.class.getName());
    public MediaPoller(MediaJobs jobs){this.jobs=jobs;}
    @PostConstruct void start(){scheduler.scheduleWithFixedDelay(()->safe(jobs::runOne),1,2,TimeUnit.SECONDS);scheduler.scheduleWithFixedDelay(()->safe(()->{jobs.scheduleCleanup();return true;}),1,3600,TimeUnit.SECONDS);}
    private void safe(Callable<Boolean> action){try{action.call();}catch(Exception e){log.log(System.Logger.Level.WARNING,"Media task polling failed; persisted tasks will be retried");}}
    @PreDestroy void close(){scheduler.shutdownNow();}
}
