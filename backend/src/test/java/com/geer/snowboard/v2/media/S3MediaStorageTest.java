package com.geer.snowboard.v2.media;
import static org.assertj.core.api.Assertions.*;
import com.geer.snowboard.v2.media.adapter.out.storage.*;
import com.geer.snowboard.v2.media.adapter.out.inspection.MediaContentProbe;
import com.geer.snowboard.v2.media.domain.MediaAsset;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
@Testcontainers
class S3MediaStorageTest {
 @Container static final GenericContainer<?> s3=new GenericContainer<>("adobe/s3mock:5.2.3").withExposedPorts(9090);
 @Test void browserPutPinsAnImmutableVersionSupportsRangesAndSweepsAllVersions()throws Exception{
  String endpoint="http://"+s3.getHost()+":"+s3.getMappedPort(9090);
  var storage=new S3MediaObjects(new MediaStorageSettings("local","us-east-1",endpoint,endpoint,"test-staging","test-frozen",null,null,null),new MediaContentProbe());
  String id=UUID.randomUUID().toString();Instant now=Instant.now();
  byte[] a=image(40,30), b=image(80,20);
  var upload=new MediaAsset(id,"test-coach","HERO","image/png",a.length,null,"staging/"+id,null,"frozen/"+id,"UPLOADING",id,"fingerprint",now,now.plusSeconds(600),now,null,0,0,0,null,null,false,null,null,null);
  var client=HttpClient.newHttpClient();String put=storage.uploadUrl(upload);
  assertThat(client.send(HttpRequest.newBuilder(URI.create(put)).header("Content-Type","image/png").PUT(HttpRequest.BodyPublishers.ofByteArray(a)).build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
  var actual=storage.uploaded(upload);assertThat(actual.versionId()).isNotBlank().isNotEqualTo("null");
  var frozen=new MediaAsset(id,"test-coach","HERO","image/png",a.length,actual.size(),upload.stagingKey(),actual.versionId(),upload.frozenKey(),"VERIFYING",id,"fingerprint",now,upload.expiresAt(),now,null,0,0,0,null,null,false,null,null,null);
  client.send(HttpRequest.newBuilder(URI.create(put)).header("Content-Type","image/png").PUT(HttpRequest.BodyPublishers.ofByteArray(b)).build(),HttpResponse.BodyHandlers.discarding());
  var checked=storage.freezeAndVerify(frozen);assertThat(checked.width()).isEqualTo(40);assertThat(checked.height()).isEqualTo(30);
  String get=storage.readUrl(frozen,now.plusSeconds(900));
  var range=client.send(HttpRequest.newBuilder(URI.create(get)).header("Range","bytes=0-7").GET().build(),HttpResponse.BodyHandlers.ofByteArray());
  assertThat(range.statusCode()).isEqualTo(206);assertThat(range.body()).isEqualTo(java.util.Arrays.copyOf(a,8));
  assertThat(storage.sweep(frozen,true,null,null).complete()).isTrue();
  assertThat(client.send(HttpRequest.newBuilder(URI.create(get)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
  // A request started before expiration can finish late. Tombstone scans must remove it too.
  client.send(HttpRequest.newBuilder(URI.create(put)).header("Content-Type","image/png").PUT(HttpRequest.BodyPublishers.ofByteArray(b)).build(),HttpResponse.BodyHandlers.discarding());
  assertThat(storage.sweep(frozen,true,null,null).complete()).isTrue();
  assertThatThrownBy(()->storage.uploaded(upload)).isInstanceOf(com.geer.snowboard.v2.media.application.port.out.MediaFailure.class);
 }
 private static byte[] image(int width,int height)throws Exception{var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();}
}
