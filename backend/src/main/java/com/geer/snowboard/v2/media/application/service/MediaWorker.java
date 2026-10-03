package com.geer.snowboard.v2.media.application.service;
import com.geer.snowboard.v2.media.application.port.in.MediaJobs;
import com.geer.snowboard.v2.media.application.port.out.*;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
@Service
public class MediaWorker implements MediaJobs {
    private final MediaStore store;private final MediaObjects objects;private final Clock clock;private final ExecutorService executor;
    public MediaWorker(MediaStore store,MediaObjects objects,Clock clock,@Qualifier("mediaExecutor") ExecutorService executor){this.store=store;this.objects=objects;this.clock=clock;this.executor=executor;}
    public boolean runOne(){
        var job=store.claim(clock.instant(),clock.instant().plusSeconds(300));if(job==null)return false;
        Future<?> task=null;
        try{
            task=executor.submit(()->process(job));task.get(180,TimeUnit.SECONDS);
        }catch(InterruptedException e){if(task!=null)task.cancel(true);Thread.currentThread().interrupt();retry(job,new MediaFailure("WORKER_INTERRUPTED",false));}
        catch(TimeoutException e){if(task!=null)task.cancel(true);retry(job,new MediaFailure("WORKER_TIMEOUT",false));}
        catch(RejectedExecutionException e){retry(job,new MediaFailure("WORKER_BUSY",false));}
        catch(ExecutionException e){retry(job,e.getCause() instanceof MediaFailure m?m:new MediaFailure("STORAGE_UNAVAILABLE",false));}
        return true;
    }
    private void process(MediaStore.Job job){
        var a=store.get(job.assetId());if(a==null||!store.claimed(job))return;
        if(job.kind().equals("VERIFY")){
            if(!a.status().equals("VERIFYING"))return;
            store.ready(job,objects.freezeAndVerify(a),clock.instant());
        }else{
            Instant now=clock.instant();
            if(!store.beginCleanup(job,now)){store.skippedCleanup(job,now);return;}
            a=store.get(a.id());boolean deleted=a.status().equals("DELETING")||a.status().equals("DELETED");
            if(now.isBefore(a.expiresAt().plusSeconds(86400))){store.skippedCleanup(job,now);return;}
            var sweep=objects.sweep(a,deleted,a.sweepCursor(),a.sweepVersionCursor());
            store.cleaned(job,deleted,sweep.keyCursor(),sweep.versionCursor(),clock.instant());
        }
    }
    private void retry(MediaStore.Job job,MediaFailure error){
        int retries=job.kind().equals("VERIFY")?3:8;Instant next=null;
        if(!error.invalid()&&job.attempts()<=retries){long delay=job.kind().equals("VERIFY")?new long[]{60,300,900}[job.attempts()-1]:Math.min(86400,60L<<(job.attempts()-1));next=clock.instant().plusSeconds(delay);}
        store.failed(job,error.invalid()?"REJECTED":"FAILED",error.code(),clock.instant(),next);
    }
    public void scheduleCleanup(){
        Instant now=clock.instant();for(var a:store.cleanupCandidates(now,10)){
            if(a.status().equals("UPLOADING"))store.expire(a.id(),now);
            store.enqueueCleanup(a.id(),now);
        }
    }
}
