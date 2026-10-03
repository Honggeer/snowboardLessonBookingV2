package com.geer.snowboard.v2.media.adapter.out.persistence;
import com.geer.snowboard.v2.media.application.port.out.*;
import com.geer.snowboard.v2.media.domain.MediaAsset;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Repository
public class JdbcMediaStore implements MediaStore {
    // Persistence records stay here; applications only receive explicitly mapped domain values.
    private record AssetRecord(String id,String owner,String purpose,String type,long expected,Long actual,String staging,
        String source,String frozen,String status,String key,String fingerprint,Instant created,Instant expires,Instant unreferenced,
        Instant terminal,int width,int height,double duration,String error,Instant stageCleaned,boolean released,String cursor,String versionCursor,Instant sweep) {
        MediaAsset domain(){return new MediaAsset(id,owner,purpose,type,expected,actual,staging,source,frozen,status,key,fingerprint,created,expires,unreferenced,terminal,width,height,duration,error,stageCleaned,released,cursor,versionCursor,sweep);}
    }
    private final JdbcTemplate jdbc;
    public JdbcMediaStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private static Instant instant(ResultSet r,String key)throws SQLException{Timestamp t=r.getTimestamp(key);return t==null?null:t.toInstant();}
    private static AssetRecord record(ResultSet r,int row)throws SQLException{
        long actual=r.getLong("actual_size");Long size=r.wasNull()?null:actual;
        return new AssetRecord(r.getString("id"),r.getString("owner_id"),r.getString("purpose"),r.getString("content_type"),r.getLong("expected_size"),size,
            r.getString("staging_key"),r.getString("source_version"),r.getString("frozen_key"),r.getString("status"),r.getString("request_key"),r.getString("fingerprint"),
            instant(r,"created_at"),instant(r,"upload_expires_at"),instant(r,"unreferenced_at"),instant(r,"terminal_at"),r.getInt("width"),r.getInt("height"),r.getDouble("duration_seconds"),r.getString("error_code"),instant(r,"stage_cleaned_at"),r.getBoolean("quota_released"),r.getString("sweep_cursor"),r.getString("sweep_version_cursor"),instant(r,"sweep_at"));
    }
    private MediaAsset find(String sql,Object...args){return jdbc.query(sql,JdbcMediaStore::record,args).stream().findFirst().map(AssetRecord::domain).orElse(null);}
    public MediaAsset get(String id){return find("SELECT * FROM media_asset WHERE id=?",id);}
    public MediaAsset lock(String id){return find("SELECT * FROM media_asset WHERE id=? FOR UPDATE",id);}
    public MediaAsset byRequest(String owner,String key){return find("SELECT * FROM media_asset WHERE owner_id=? AND request_key=?",owner,key);}
    public long lockQuota(String owner){jdbc.update("INSERT IGNORE INTO media_quota (owner_id) VALUES (?)",owner);return jdbc.queryForObject("SELECT reserved_bytes FROM media_quota WHERE owner_id=? FOR UPDATE",Long.class,owner);}
    public void reserve(String owner,long size){jdbc.update("UPDATE media_quota SET reserved_bytes=reserved_bytes+? WHERE owner_id=?",size,owner);}
    public long activeUploads(String owner){return jdbc.queryForObject("SELECT COUNT(*) FROM media_asset WHERE owner_id=? AND status IN ('UPLOADING','VERIFYING')",Long.class,owner);}
    public long hourlyUploads(String owner,Instant since){return jdbc.queryForObject("SELECT COUNT(*) FROM media_asset WHERE owner_id=? AND created_at>=?",Long.class,owner,since);}
    public void insert(MediaAsset a){jdbc.update("INSERT INTO media_asset (id,owner_id,purpose,content_type,expected_size,staging_key,frozen_key,status,request_key,fingerprint,created_at,upload_expires_at,unreferenced_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",a.id(),a.ownerId(),a.purpose(),a.contentType(),a.expectedSize(),a.stagingKey(),a.frozenKey(),a.status(),a.requestKey(),a.fingerprint(),a.createdAt(),a.expiresAt(),a.unreferencedAt());}
    public void verifying(String id,String version,long size,Instant now){
        if(jdbc.update("UPDATE media_asset SET status='VERIFYING',source_version=?,actual_size=? WHERE id=? AND status='UPLOADING'",version,size,id)==1)
            jdbc.update("INSERT INTO media_job (asset_id,kind,next_run_at) VALUES (?,'VERIFY',?)",id,now);
    }
    public List<String> referenceIds(String slot){return jdbc.query("SELECT asset_id FROM media_reference WHERE consumer='GEER' AND slot=?",(r,n)->r.getString(1),slot);}
    public void replaceReferences(String slot,Map<String,String> ids,Instant now){
        var old=referenceIds(slot);jdbc.update("DELETE FROM media_reference WHERE consumer='GEER' AND slot=?",slot);
        ids.forEach((purpose,id)->jdbc.update("INSERT INTO media_reference (consumer,slot,purpose,asset_id) VALUES ('GEER',?,?,?)",slot,purpose,id));
        for(String id:old)if(!referenced(id))jdbc.update("UPDATE media_asset SET unreferenced_at=? WHERE id=?",now,id);
    }
    public boolean referenced(String id){return jdbc.queryForObject("SELECT COUNT(*) FROM media_reference WHERE asset_id=?",Long.class,id)>0;}
    public boolean publishedReference(String id){return jdbc.queryForObject("SELECT COUNT(*) FROM media_reference WHERE asset_id=? AND consumer='GEER' AND slot='PUBLISHED'",Long.class,id)>0;}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Job claim(Instant now,Instant until){
        var jobs=jdbc.query("SELECT id,asset_id,kind,attempts,claim_token FROM media_job WHERE (status='PENDING' AND next_run_at<=?) OR (status='RUNNING' AND lease_until<=?) ORDER BY next_run_at,id LIMIT 1 FOR UPDATE SKIP LOCKED",(r,n)->new Job(r.getLong(1),r.getString(2),r.getString(3),r.getInt(4)+1,r.getLong(5)+1),now,now);
        if(jobs.isEmpty())return null;var j=jobs.getFirst();
        jdbc.update("UPDATE media_job SET status='RUNNING',attempts=?,claim_token=?,lease_until=? WHERE id=?",j.attempts(),j.token(),until,j.id());return j;
    }
    public boolean claimed(Job job){return jdbc.queryForObject("SELECT COUNT(*) FROM media_job WHERE id=? AND status='RUNNING' AND claim_token=?",Long.class,job.id(),job.token())==1;}
    private boolean lockClaim(Job job,Instant now){return jdbc.query("SELECT id FROM media_job WHERE id=? AND status='RUNNING' AND claim_token=? AND lease_until>? FOR UPDATE",(r,n)->r.getLong(1),job.id(),job.token(),now).size()==1;}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void ready(Job job,MediaObjects.Verified result,Instant now){
        // Asset -> job is also used by cleanup. No network calls occur while these locks are held.
        var a=lock(job.assetId());if(a==null||!lockClaim(job,now)||!a.status().equals("VERIFYING"))return;
        jdbc.update("UPDATE media_asset SET status='READY',actual_size=?,width=?,height=?,duration_seconds=?,terminal_at=?,unreferenced_at=?,error_code=NULL WHERE id=?",result.size(),result.width(),result.height(),result.duration(),now,now,a.id());
        jdbc.update("UPDATE media_job SET status='DONE',lease_until=NULL,error_code=NULL WHERE id=? AND claim_token=?",job.id(),job.token());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void failed(Job job,String state,String code,Instant now,Instant retry){
        var a=lock(job.assetId());if(a==null||!lockClaim(job,now))return;
        if(retry!=null)jdbc.update("UPDATE media_job SET status='PENDING',lease_until=NULL,next_run_at=?,error_code=? WHERE id=?",retry,code,job.id());
        else{
            jdbc.update("UPDATE media_job SET status='FAILED',lease_until=NULL,error_code=? WHERE id=?",code,job.id());
            if(job.kind().equals("VERIFY")&&a.status().equals("VERIFYING"))jdbc.update("UPDATE media_asset SET status=?,terminal_at=?,error_code=? WHERE id=?",state,now,code,a.id());
        }
    }
    public List<MediaAsset> cleanupCandidates(Instant now,int limit){
        return jdbc.query("SELECT a.* FROM media_asset a WHERE ((a.status='UPLOADING' AND a.created_at<?) OR (a.status NOT IN ('UPLOADING','VERIFYING') AND a.terminal_at<?)) AND (a.sweep_at IS NULL OR a.sweep_at<?) AND NOT EXISTS (SELECT 1 FROM media_job j WHERE j.asset_id=a.id AND j.kind='CLEAN' AND j.status IN ('PENDING','RUNNING','FAILED')) ORDER BY COALESCE(a.sweep_at,a.created_at),a.id LIMIT ?",JdbcMediaStore::record,now.minusSeconds(86400),now.minusSeconds(600),now.minusSeconds(3600),limit).stream().map(AssetRecord::domain).toList();
    }
    public void expire(String id,Instant now){jdbc.update("UPDATE media_asset SET status='EXPIRED',terminal_at=?,error_code='UPLOAD_EXPIRED' WHERE id=? AND status='UPLOADING' AND created_at<?",now,id,now.minusSeconds(86400));}
    public void enqueueCleanup(String id,Instant now){jdbc.update("INSERT INTO media_job (asset_id,kind,next_run_at) VALUES (?,'CLEAN',?) ON DUPLICATE KEY UPDATE attempts=IF(status='DONE',0,attempts),next_run_at=IF(status='DONE',VALUES(next_run_at),next_run_at),status=IF(status='DONE','PENDING',status)",id,now);}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public boolean beginCleanup(Job job,Instant now){
        var a=lock(job.assetId());if(a==null||!lockClaim(job,now)||!a.terminal())return false;
        var leases=jdbc.queryForObject("SELECT COUNT(*) FROM media_job WHERE asset_id=? AND kind='VERIFY' AND (status IN ('PENDING','RUNNING') OR lease_until>?)",Long.class,a.id(),now);
        if(leases>0||a.terminalAt()==null||a.terminalAt().isAfter(now.minusSeconds(600)))return false;
        boolean unused=!referenced(a.id());
        boolean remove=unused&&(a.status().equals("DELETING")||a.status().equals("DELETED")
                || a.status().equals("READY")&&a.unreferencedAt().isBefore(now.minusSeconds(7*86400L))
                || !a.status().equals("READY")&&a.terminalAt().isBefore(now.minusSeconds(86400)));
        if(remove&&!a.status().equals("DELETED"))jdbc.update("UPDATE media_asset SET status='DELETING' WHERE id=?",a.id());
        return true;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void skippedCleanup(Job job,Instant now){
        var a=lock(job.assetId());if(a==null||!lockClaim(job,now))return;
        // A safety-window skip schedules a later scan without claiming an object was deleted.
        jdbc.update("UPDATE media_asset SET sweep_at=? WHERE id=?",now,a.id());
        jdbc.update("UPDATE media_job SET status='DONE',lease_until=NULL,error_code=NULL WHERE id=?",job.id());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void cleaned(Job job,boolean deleted,String cursor,String versionCursor,Instant now){
        var a=lock(job.assetId());if(a==null||!lockClaim(job,now))return;
        boolean complete=cursor==null&&versionCursor==null;
        jdbc.update("UPDATE media_asset SET sweep_cursor=?,sweep_version_cursor=?,sweep_at=? WHERE id=?",cursor,versionCursor,complete?now:a.sweepAt(),a.id());
        if(complete){
            jdbc.update("UPDATE media_asset SET stage_cleaned_at=? WHERE id=?",now,a.id());
            if(deleted){
                if(!a.quotaReleased()){jdbc.update("UPDATE media_quota SET reserved_bytes=GREATEST(0,reserved_bytes-?) WHERE owner_id=?",a.expectedSize(),a.ownerId());}
                jdbc.update("UPDATE media_asset SET status='DELETED',quota_released=TRUE WHERE id=? AND status IN ('DELETING','DELETED')",a.id());
            }
            jdbc.update("UPDATE media_job SET status='DONE',lease_until=NULL,error_code=NULL WHERE id=?",job.id());
        }else jdbc.update("UPDATE media_job SET status='PENDING',attempts=0,lease_until=NULL,next_run_at=? WHERE id=?",now.plusSeconds(1),job.id());
    }
}
