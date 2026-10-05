package com.geer.snowboard.v2.media;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.geer.snowboard.v2.media.application.port.in.MediaJobs;
import com.geer.snowboard.v2.media.application.port.in.MediaOperations;
import com.geer.snowboard.v2.coachprofile.application.port.in.CoachProfileOperations;
import com.geer.snowboard.v2.sharedkernel.Actor;
import com.geer.snowboard.v2.sharedkernel.BusinessProblem;
import java.util.concurrent.*;
import com.geer.snowboard.v2.media.application.port.out.*;
import com.geer.snowboard.v2.media.domain.MediaAsset;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties={"identity.mail.worker.enabled=false","booking.mail.worker.enabled=false","media.worker.enabled=false","spring.session.jdbc.cleanup-cron=-"})
@AutoConfigureMockMvc @Testcontainers
class MediaLifecycleTest {
 @Container static final MySQLContainer mysql=new MySQLContainer("mysql:8.4").withDatabaseName("media_lifecycle").withUsername("media_test").withPassword("test_password")
  .withCommand("--log-bin-trust-function-creators=1"); // This test-only database permits the rollback failure trigger.
 @DynamicPropertySource static void config(DynamicPropertyRegistry r){r.add("spring.datasource.url",mysql::getJdbcUrl);r.add("spring.datasource.username",mysql::getUsername);r.add("spring.datasource.password",mysql::getPassword);r.add("identity.verification-key",()->"a-private-test-key-with-at-least-32-bytes");}
 @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired MediaJobs jobs; @Autowired MediaStore store; @Autowired FakeObjects objects; @Autowired MediaOperations operations;
 @Autowired CoachProfileOperations profiles;
 @TestConfiguration static class Fakes { @Bean @Primary FakeObjects mediaObjects(){return new FakeObjects();} }
 static class FakeObjects implements MediaObjects {
  String latest="A",frozenVersion;int sweeps;boolean fail;MediaFailure failure;
  public String uploadUrl(MediaAsset a){return "https://storage.test/upload";}
  public Uploaded uploaded(MediaAsset a){return new Uploaded(a.expectedSize(),latest);}
  public Verified freezeAndVerify(MediaAsset a){if(failure!=null)throw failure;if(fail)throw new MediaFailure("STORAGE_UNAVAILABLE",false);frozenVersion=a.sourceVersion();return new Verified(a.expectedSize(),100,80,0);}
  public String readUrl(MediaAsset a,Instant expiry){return "https://cdn.test/"+a.id();}
  public Sweep sweep(MediaAsset a,boolean frozen,String cursor,String version){sweeps++;return new Sweep(true,null,null);}
 }
 @BeforeEach void reset(){
  jdbc.update("DELETE FROM media_reference");jdbc.update("DELETE FROM media_job");jdbc.update("DELETE FROM media_asset");jdbc.update("DELETE FROM media_quota");
  jdbc.update("DELETE FROM coach_profile_publish_request");jdbc.update("DELETE FROM coach_profile_page");objects.fail=false;objects.failure=null;objects.latest="A";objects.sweeps=0;
 }
 @Test void uploadedVersionIsPinnedAndDraftPublicationIsAtomicAndIdempotent()throws Exception{
  String coach=account(),key=UUID.randomUUID().toString();objects.latest="A";
  String id=request(coach,key);
  mvc.perform(post("/api/coach/media/uploads/{id}/complete",id).with(user(coach).roles("COACH")).with(csrf())).andExpect(status().isAccepted());
  objects.latest="B";
  mvc.perform(post("/api/coach/media/uploads/{id}/complete",id).with(user(coach).roles("COACH")).with(csrf())).andExpect(status().isAccepted());
  assertThat(jobs.runOne()).isTrue();
  mvc.perform(get("/api/coach/media/uploads/{id}",id).with(user(coach).roles("COACH"))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));
  assertThat(objects.frozenVersion).isEqualTo("A");
  String draft=mvc.perform(get("/api/coach/profile/draft").with(user(coach).roles("COACH"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  Number version=JsonPath.read(draft,"$.version");
  mvc.perform(put("/api/coach/profile/draft").with(user(coach).roles("COACH")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
    .content("{\"expectedVersion\":"+version+",\"content\":{\"displayName\":\"GEER\",\"tagline\":\"我的教学\",\"heroId\":\""+id+"\"}}"))
    .andExpect(status().isOk());
  String publishKey=UUID.randomUUID().toString(), body="{\"draftVersion\":"+(version.longValue()+1)+"}";
  for(int i=0;i<2;i++)mvc.perform(post("/api/coach/profile/publish").with(user(coach).roles("COACH")).with(csrf()).header("Idempotency-Key",publishKey).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
  mvc.perform(get("/api/coach-profile")).andExpect(status().isOk()).andExpect(jsonPath("$.published").value(true)).andExpect(jsonPath("$.content.tagline").value("我的教学"));
  mvc.perform(post("/api/coach/profile/publish").with(user(coach).roles("COACH")).with(csrf()).header("Idempotency-Key",publishKey).contentType(MediaType.APPLICATION_JSON).content("{\"draftVersion\":999}"))
    .andExpect(status().isConflict());
 }
 @Test void transientFailureRetriesAndDeletedTombstonesAreSweptAgain()throws Exception{
  String coach=account();String id=request(coach,UUID.randomUUID().toString());
  mvc.perform(post("/api/coach/media/uploads/{id}/complete",id).with(user(coach).roles("COACH")).with(csrf())).andExpect(status().isAccepted());
  objects.fail=true;assertThat(jobs.runOne()).isTrue();
  assertThat(jdbc.queryForObject("SELECT status FROM media_asset WHERE id=?",String.class,id)).isEqualTo("VERIFYING");
  objects.fail=false;jdbc.update("UPDATE media_job SET next_run_at=? WHERE asset_id=?",Instant.now().minusSeconds(1),id);assertThat(jobs.runOne()).isTrue();
  jdbc.update("UPDATE media_asset SET created_at=?,upload_expires_at=?,terminal_at=?,unreferenced_at=? WHERE id=?",Instant.now().minusSeconds(10*86400),Instant.now().minusSeconds(10*86400),Instant.now().minusSeconds(10*86400),Instant.now().minusSeconds(8*86400),id);
  jobs.scheduleCleanup();assertThat(jobs.runOne()).isTrue();
  assertThat(jdbc.queryForObject("SELECT status FROM media_asset WHERE id=?",String.class,id)).isEqualTo("DELETED");
  int previous=objects.sweeps;jdbc.update("UPDATE media_asset SET sweep_at=? WHERE id=?",Instant.now().minusSeconds(7200),id);
  jobs.scheduleCleanup();assertThat(jobs.runOne()).isTrue();assertThat(objects.sweeps).isGreaterThan(previous);
 }
 @Test void probeTimeoutRetriesRemainVerifyingAndExhaustAsFailed()throws Exception{
  String coach=account(),id=request(coach,UUID.randomUUID().toString());
  operations.complete(new Actor(coach,"COACH","Test"),id);objects.failure=new MediaFailure("PROBE_TIMEOUT",false);
  for(int attempt=1;attempt<=4;attempt++){
   jdbc.update("UPDATE media_job SET next_run_at=? WHERE asset_id=?",Instant.now().minusSeconds(1),id);
   Instant before=Instant.now();assertThat(jobs.runOne()).isTrue();
   assertThat(store.get(id).status()).isEqualTo(attempt<4?"VERIFYING":"FAILED");
   assertThat(jdbc.queryForObject("SELECT status FROM media_job WHERE asset_id=?",String.class,id)).isEqualTo(attempt<4?"PENDING":"FAILED");
   assertThat(jdbc.queryForObject("SELECT error_code FROM media_job WHERE asset_id=?",String.class,id)).isEqualTo("PROBE_TIMEOUT");
   if(attempt<4){
    long delay=new long[]{60,300,900}[attempt-1];
    assertThat(jdbc.queryForObject("SELECT next_run_at FROM media_job WHERE asset_id=?",java.sql.Timestamp.class,id).toInstant()).isAfter(before.plusSeconds(delay-1));
    assertThat(jobs.runOne()).isFalse();
   }
  }
  assertThat(jobs.runOne()).isFalse();assertThat(store.get(id).errorCode()).isEqualTo("PROBE_TIMEOUT");
 }
 @Test void probeTimeoutCanRecoverOnTheNextAttemptWithoutRejectingTheAsset()throws Exception{
  String coach=account(),id=request(coach,UUID.randomUUID().toString());operations.complete(new Actor(coach,"COACH","Test"),id);
  objects.failure=new MediaFailure("PROBE_TIMEOUT",false);assertThat(jobs.runOne()).isTrue();assertThat(store.get(id).status()).isEqualTo("VERIFYING");
  objects.failure=null;jdbc.update("UPDATE media_job SET next_run_at=? WHERE asset_id=?",Instant.now().minusSeconds(1),id);
  assertThat(jobs.runOne()).isTrue();assertThat(store.get(id).status()).isEqualTo("READY");
 }
 @Test void aStaleWorkerCannotCommitAndPublishedAssetsCannotBeDeleted()throws Exception{
  String coach=account(),id=request(coach,UUID.randomUUID().toString());
  mvc.perform(post("/api/coach/media/uploads/{id}/complete",id).with(user(coach).roles("COACH")).with(csrf())).andExpect(status().isAccepted());
  Instant now=Instant.now();var first=store.claim(now,now.plusSeconds(300));
  jdbc.update("UPDATE media_job SET lease_until=? WHERE id=?",now.minusSeconds(1),first.id());var second=store.claim(now,now.plusSeconds(300));
  store.ready(first,new MediaObjects.Verified(4,1,1,0),now);assertThat(store.get(id).status()).isEqualTo("VERIFYING");
  store.ready(second,new MediaObjects.Verified(4,1,1,0),now);assertThat(store.get(id).status()).isEqualTo("READY");
  jdbc.update("INSERT INTO media_reference (consumer,slot,purpose,asset_id) VALUES ('GEER','PUBLISHED','HERO',?)",id);
  jdbc.update("UPDATE media_asset SET terminal_at=?,unreferenced_at=?,upload_expires_at=? WHERE id=?",now.minusSeconds(10*86400),now.minusSeconds(8*86400),now.minusSeconds(10*86400),id);
  jobs.scheduleCleanup();assertThat(jobs.runOne()).isTrue();assertThat(store.get(id).status()).isEqualTo("READY");
 }
 @Test void concurrentUploadRequestsReserveOnlyOneSlot()throws Exception{
  String coach=account();var start=new CountDownLatch(1);
  try(var executor=Executors.newFixedThreadPool(2)){
   java.util.concurrent.Callable<Integer> attempt=()->{start.await();try{operations.request(new Actor(coach,"COACH","Test"),"HERO","image/png",4,UUID.randomUUID().toString());return 201;}catch(BusinessProblem p){return p.status();}};
   var a=executor.submit(attempt);var b=executor.submit(attempt);start.countDown();
   assertThat(java.util.List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
  }
  assertThat(jdbc.queryForObject("SELECT reserved_bytes FROM media_quota WHERE owner_id=?",Long.class,coach)).isEqualTo(4);
 }
 @Test void missingVersionFailsClosedAndRetriesStopAfterFourAttempts()throws Exception{
  String coach=account(),id=request(coach,UUID.randomUUID().toString());objects.latest="null";
  mvc.perform(post("/api/coach/media/uploads/{id}/complete",id).with(user(coach).roles("COACH")).with(csrf())).andExpect(status().isServiceUnavailable());
  assertThat(store.get(id).sourceVersion()).isNull();assertThat(store.get(id).status()).isEqualTo("UPLOADING");
  objects.latest="A";mvc.perform(post("/api/coach/media/uploads/{id}/complete",id).with(user(coach).roles("COACH")).with(csrf())).andExpect(status().isAccepted());
  objects.fail=true;for(int i=0;i<4;i++){jdbc.update("UPDATE media_job SET next_run_at=? WHERE asset_id=?",Instant.now().minusSeconds(1),id);assertThat(jobs.runOne()).isTrue();}
  assertThat(store.get(id).status()).isEqualTo("FAILED");assertThat(jobs.runOne()).isFalse();
 }
 @Test void safetyWindowDoesNotRecordObjectsAsAlreadyCleaned()throws Exception{
  String coach=account(),id=request(coach,UUID.randomUUID().toString());
  jdbc.update("UPDATE media_asset SET created_at=?,upload_expires_at=? WHERE id=?",Instant.now().minusSeconds(90000),Instant.now().minusSeconds(89000),id);
  jobs.scheduleCleanup();assertThat(jobs.runOne()).isTrue();
  assertThat(store.get(id).status()).isEqualTo("EXPIRED");assertThat(store.get(id).stageCleanedAt()).isNull();assertThat(objects.sweeps).isZero();
 }
 @Test void publicationStorageFailureRollsBackSnapshotAndMediaReferences()throws Exception{
  String coach=account();var actor=new Actor(coach,"COACH","Test");
  String first=request(coach,UUID.randomUUID().toString());operations.complete(actor,first);jobs.runOne();
  profiles.save(actor,0,java.util.Map.of("displayName","GEER","tagline","Published","heroId",first));
  profiles.publish(actor,1,UUID.randomUUID().toString());
  String second=request(coach,UUID.randomUUID().toString());operations.complete(actor,second);jobs.runOne();
  profiles.save(actor,1,java.util.Map.of("displayName","GEER","tagline","Private draft","heroId",second));
  // Fail after the profile and media changes, using only this isolated test database.
  jdbc.execute("CREATE TRIGGER fail_profile_publication BEFORE INSERT ON coach_profile_publish_request FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test publication failure'");
  try{assertThatThrownBy(()->profiles.publish(actor,2,UUID.randomUUID().toString())).isInstanceOf(RuntimeException.class);}
  finally{jdbc.execute("DROP TRIGGER fail_profile_publication");}
  assertThat(profiles.published().version()).isEqualTo(1);
  assertThat(profiles.published().content()).containsEntry("tagline","Published").containsEntry("heroId",first);
  assertThat(store.referenceIds("PUBLISHED")).containsExactly(first);
  assertThat(store.referenceIds("DRAFT")).containsExactly(second);
 }
 @Test void applicationPortsRejectWrongRolesOwnersAndUnverifiedReferences()throws Exception{
  String coach=account(),id=request(coach,UUID.randomUUID().toString());var actor=new Actor(coach,"COACH","Test");
  var other=new Actor(UUID.randomUUID().toString(),"COACH","Other");
  assertThatThrownBy(()->operations.status(other,id)).isInstanceOfSatisfying(BusinessProblem.class,p->assertThat(p.status()).isEqualTo(404));
  assertThatThrownBy(()->operations.complete(other,id)).isInstanceOfSatisfying(BusinessProblem.class,p->assertThat(p.status()).isEqualTo(404));
  assertThatThrownBy(()->operations.status(new Actor(coach,"STUDENT","Wrong role"),id)).isInstanceOfSatisfying(BusinessProblem.class,p->assertThat(p.status()).isEqualTo(403));
  profiles.save(actor,0,java.util.Map.of("displayName","GEER"));
  assertThatThrownBy(()->profiles.save(other,1,java.util.Map.of("displayName","Other"))).isInstanceOfSatisfying(BusinessProblem.class,p->assertThat(p.status()).isEqualTo(403));
  assertThatThrownBy(()->profiles.save(actor,1,java.util.Map.of("heroId",id))).isInstanceOfSatisfying(BusinessProblem.class,p->assertThat(p.status()).isEqualTo(409));
  assertThat(profiles.draft(actor).version()).isEqualTo(1);
 }
 @Test void realPublicationAllowsOnlyWechatQrAndIndependentSocialFields()throws Exception{
  String coach=account();var actor=new Actor(coach,"COACH","Test");
  String hero=request(coach,UUID.randomUUID().toString()),qr=requestReadyQr(coach,actor,hero);
  var json=tools.jackson.databind.json.JsonMapper.builder().build();long version=0;
  for(var extra:java.util.List.of(java.util.Map.of("wechatQrId",qr),java.util.Map.of("xhsAccount","小红书 自由昵称"),
      java.util.Map.of("douyinAccount","抖音号 @GEER"),java.util.Map.of("xhsUrl","http://short.example.test/profile"),
      java.util.Map.of("douyinUrl","主页稍后补充"))){
   var content=new java.util.HashMap<>(java.util.Map.of("displayName","GEER","tagline","教学","heroId",hero));content.putAll(extra);
   mvc.perform(put("/api/coach/profile/draft").with(user(coach).roles("COACH")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
    .content(json.writeValueAsString(java.util.Map.of("expectedVersion",version,"content",content)))).andExpect(status().isOk());version++;
   mvc.perform(post("/api/coach/profile/publish").with(user(coach).roles("COACH")).with(csrf()).header("Idempotency-Key",UUID.randomUUID().toString())
    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("draftVersion",version)))).andExpect(status().isOk());
   var response=mvc.perform(get("/api/coach-profile")).andExpect(status().isOk()).andExpect(jsonPath("$.published").value(true));
   for(var entry:extra.entrySet())response.andExpect(jsonPath("$.content."+entry.getKey()).value(entry.getValue()));
  }
 }
 private String requestReadyQr(String coach,Actor actor,String hero)throws Exception{
  operations.complete(actor,hero);assertThat(jobs.runOne()).isTrue();
  String qr=request(coach,UUID.randomUUID().toString(),"WECHAT_QR");operations.complete(actor,qr);assertThat(jobs.runOne()).isTrue();return qr;
 }
 private String request(String coach,String key)throws Exception{
  return request(coach,key,"HERO");
 }
 private String request(String coach,String key,String purpose)throws Exception{
  String b="{\"purpose\":\""+purpose+"\",\"contentType\":\"image/png\",\"size\":4}";
  String result=mvc.perform(post("/api/coach/media/uploads").with(user(coach).roles("COACH")).with(csrf()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(b)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
  mvc.perform(post("/api/coach/media/uploads").with(user(coach).roles("COACH")).with(csrf()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(b)).andExpect(status().isOk());return JsonPath.read(result,"$.id");
 }
 private String account(){var rows=jdbc.queryForList("SELECT id FROM identity_account WHERE role='COACH'",String.class);if(!rows.isEmpty())return rows.getFirst();String id=UUID.randomUUID().toString();jdbc.update("INSERT INTO identity_account (id,email,email_key,name,role,password_hash,verified_at,created_at) VALUES (?,?,?,'Test','COACH','unused',?,?)",id,id+"@test.invalid",id+"@test.invalid",Instant.now(),Instant.now());return id;}
}
