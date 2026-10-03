package com.geer.snowboard.v2.media.adapter.out.storage;

import com.geer.snowboard.v2.media.application.port.out.*;
import com.geer.snowboard.v2.media.domain.MediaAsset;
import com.geer.snowboard.v2.media.domain.MediaRules;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;
import software.amazon.awssdk.services.cloudfront.model.CannedSignerRequest;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;

@Component
public class S3MediaObjects implements MediaObjects {
    private final MediaStorageSettings settings;private final ContentProbe probe;
    private S3Client client;private S3Presigner signer;private volatile boolean initialized;
    public S3MediaObjects(MediaStorageSettings settings,ContentProbe probe){
        this.settings=settings;this.probe=probe;
        if("aws".equals(settings.mode())){
            required(settings.region());required(settings.stagingBucket());required(settings.frozenBucket());
            required(settings.cdnBaseUrl());required(settings.keyPairId());required(settings.signingKeyPath());
            if(!settings.cdnBaseUrl().startsWith("https://")||!Files.isReadable(Path.of(settings.signingKeyPath())))throw new IllegalStateException("Media distribution configuration is incomplete");
        }
    }
    private static void required(String value){if(value==null||value.isBlank())throw new IllegalStateException("Media storage configuration is incomplete");}
    private boolean local(){return "local".equals(settings.mode());}
    private synchronized void init(){
        if(initialized)return;
        if(!local()&&!"aws".equals(settings.mode()))throw unavailable();
        try{
            required(settings.region());required(settings.stagingBucket());required(settings.frozenBucket());
            if(settings.stagingBucket().equals(settings.frozenBucket()))throw unavailable();
            var credentials=local()?StaticCredentialsProvider.create(AwsBasicCredentials.create("local-only","local-only")):DefaultCredentialsProvider.builder().build();
            var service=S3Configuration.builder().pathStyleAccessEnabled(local()).build();
            var builder=S3Client.builder().region(Region.of(settings.region())).credentialsProvider(credentials)
                .serviceConfiguration(service).httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(30)))
                .overrideConfiguration(c->c.apiCallTimeout(Duration.ofSeconds(30)).apiCallAttemptTimeout(Duration.ofSeconds(30)))
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED).responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
            var presigner=S3Presigner.builder().region(Region.of(settings.region())).credentialsProvider(credentials).serviceConfiguration(service);
            if(local()){
                required(settings.endpoint());required(settings.publicEndpoint());
                builder.endpointOverride(URI.create(settings.endpoint()));presigner.endpointOverride(URI.create(settings.publicEndpoint()));
            }
            client=builder.build();signer=presigner.build();
            if(local()){
                for(String bucket:new String[]{settings.stagingBucket(),settings.frozenBucket()}){
                    try{client.headBucket(r->r.bucket(bucket));}catch(S3Exception e){if(e.statusCode()!=404)throw e;client.createBucket(r->r.bucket(bucket));}
                }
                client.putBucketVersioning(r->r.bucket(settings.stagingBucket()).versioningConfiguration(v->v.status(BucketVersioningStatus.ENABLED)));
            }
            if(client.getBucketVersioning(r->r.bucket(settings.stagingBucket())).status()!=BucketVersioningStatus.ENABLED)throw unavailable();
            initialized=true;
        }catch(MediaFailure e){closeClients();throw e;}
        catch(RuntimeException e){closeClients();throw unavailable();}
    }
    @Override public String uploadUrl(MediaAsset a){
        init();Duration remaining=Duration.between(Instant.now(),a.expiresAt());if(remaining.isNegative()||remaining.isZero())throw unavailable();
        try{return signer.presignPutObject(PutObjectPresignRequest.builder().signatureDuration(remaining)
            .putObjectRequest(r->r.bucket(settings.stagingBucket()).key(a.stagingKey()).contentType(a.contentType())).build()).url().toString();}
        catch(RuntimeException e){throw unavailable();}
    }
    @Override public Uploaded uploaded(MediaAsset a){
        init();try{var head=client.headObject(r->r.bucket(settings.stagingBucket()).key(a.stagingKey()));return new Uploaded(head.contentLength(),head.versionId());}
        catch(RuntimeException e){throw unavailable();}
    }
    @Override public Verified freezeAndVerify(MediaAsset a){
        init();if(a.sourceVersion()==null||a.sourceVersion().isBlank()||a.sourceVersion().equals("null"))throw new MediaFailure("SOURCE_VERSION_MISSING",true);
        Path temp=null;
        try{
            String source=settings.stagingBucket()+"/"+encode(a.stagingKey())+"?versionId="+encode(a.sourceVersion());
            client.copyObject(r->r.copySource(source).destinationBucket(settings.frozenBucket()).destinationKey(a.frozenKey())
                .metadataDirective(MetadataDirective.REPLACE).contentType(a.contentType()).contentDisposition("inline"));
            temp=Files.createTempFile("geer-media-",".upload");long count=0,limit=Math.min(a.expectedSize(),MediaRules.limit(a.purpose()));
            try(ResponseInputStream<GetObjectResponse> input=client.getObject(r->r.bucket(settings.frozenBucket()).key(a.frozenKey()));var output=Files.newOutputStream(temp)){
                if(input.response().contentLength()!=a.expectedSize())throw new MediaFailure("SIZE_MISMATCH",true);
                byte[] buffer=new byte[65536];int n;
                while((n=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("interrupted");count+=n;if(count>limit)throw new MediaFailure("FILE_TOO_LARGE",true);output.write(buffer,0,n);}
            }
            if(count!=a.expectedSize())throw new MediaFailure("SIZE_MISMATCH",true);
            var result=probe.inspect(temp,a.purpose(),a.contentType());return new Verified(count,result.width(),result.height(),result.duration());
        }catch(MediaFailure e){throw e;}
        catch(IOException|RuntimeException e){throw unavailable();}
        finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){temp.toFile().deleteOnExit();}}
    }
    @Override public String readUrl(MediaAsset a,Instant expires){
        // CloudFront signing is local cryptography; it needs no S3 request or IAM credentials.
        if(!"aws".equals(settings.mode()))init();
        try{
            if(local())return signer.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(Duration.between(Instant.now(),expires))
                .getObjectRequest(r->r.bucket(settings.frozenBucket()).key(a.frozenKey())).build()).url().toString();
            String resource=settings.cdnBaseUrl().replaceAll("/$","")+"/"+a.frozenKey();
            return CloudFrontUtilities.create().getSignedUrlWithCannedPolicy(CannedSignerRequest.builder().resourceUrl(resource).keyPairId(settings.keyPairId())
                .privateKey(Path.of(settings.signingKeyPath())).expirationDate(expires).build()).url();
        }catch(Exception e){throw unavailable();}
    }
    @Override public Sweep sweep(MediaAsset a,boolean frozen,String cursor,String version){
        init();try{
            boolean frozenPhase=cursor!=null&&cursor.startsWith("F:");String marker=frozenPhase?cursor.substring(2):cursor;
            if(marker!=null&&marker.isEmpty())marker=null;
            String bucket=frozenPhase?settings.frozenBucket():settings.stagingBucket(),key=frozenPhase?a.frozenKey():a.stagingKey();
            String keyMarker=marker;
            var page=client.listObjectVersions(r->r.bucket(bucket).prefix(key).maxKeys(100).keyMarker(keyMarker).versionIdMarker(version));
            // Prefix search is not an ownership check. Only delete this asset's exact recorded key.
            for(var v:page.versions())if(key.equals(v.key()))client.deleteObject(r->r.bucket(bucket).key(key).versionId(v.versionId()));
            for(var v:page.deleteMarkers())if(key.equals(v.key()))client.deleteObject(r->r.bucket(bucket).key(key).versionId(v.versionId()));
            if(Boolean.TRUE.equals(page.isTruncated()))return new Sweep(false,(frozenPhase?"F:":"")+page.nextKeyMarker(),page.nextVersionIdMarker());
            if(!frozenPhase&&frozen)return sweep(a,true,"F:",null);
            return new Sweep(true,null,null);
        }catch(RuntimeException e){throw unavailable();}
    }
    private static String encode(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8).replace("+","%20");}
    private static MediaFailure unavailable(){return new MediaFailure("STORAGE_UNAVAILABLE",false);}
    @PreDestroy public synchronized void closeClients(){initialized=false;if(client!=null)client.close();if(signer!=null)signer.close();client=null;signer=null;}
}
