package com.geer.snowboard.v2.catalog;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.geer.snowboard.v2.catalog.application.port.in.CourseOperations;
import com.geer.snowboard.v2.media.application.port.in.MediaJobs;
import com.geer.snowboard.v2.media.application.port.in.MediaOperations;
import com.geer.snowboard.v2.media.application.port.out.*;
import com.geer.snowboard.v2.media.domain.MediaAsset;
import com.geer.snowboard.v2.sharedkernel.Actor;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties={"identity.mail.worker.enabled=false","booking.mail.worker.enabled=false","media.worker.enabled=false","spring.session.jdbc.cleanup-cron=-"})
@AutoConfigureMockMvc @Testcontainers
class CourseCoverApiTest {
    @Container static final MySQLContainer mysql=new MySQLContainer("mysql:8.4")
            .withDatabaseName("course_cover").withUsername("course_test").withPassword("test_password")
            .withCommand("--log-bin-trust-function-creators=1");
    @DynamicPropertySource static void config(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",mysql::getJdbcUrl);r.add("spring.datasource.username",mysql::getUsername);
        r.add("spring.datasource.password",mysql::getPassword);r.add("identity.verification-key",()->"a-private-test-key-with-at-least-32-bytes");
    }
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired FakeObjects objects;
    @Autowired CourseOperations courses; @Autowired MediaOperations media; @Autowired MediaJobs jobs;
    @TestConfiguration static class Fakes { @Bean @Primary FakeObjects mediaObjects(){return new FakeObjects();} }
    static class FakeObjects implements MediaObjects {
        volatile boolean fail; volatile int reads;
        public String uploadUrl(MediaAsset a){return "https://storage.test/upload";}
        public Uploaded uploaded(MediaAsset a){return new Uploaded(a.expectedSize(),"version-A");}
        public Verified freezeAndVerify(MediaAsset a){return new Verified(a.expectedSize(),1600,900,0);}
        public String readUrl(MediaAsset a,Instant expiry){reads++;if(fail)throw new MediaFailure("STORAGE_UNAVAILABLE",false);return "https://cdn.test/"+a.id();}
        public Sweep sweep(MediaAsset a,boolean frozen,String cursor,String version){return new Sweep(true,null,null);}
    }
    @BeforeEach void reset(){
        jdbc.update("DELETE FROM media_reference");jdbc.update("DELETE FROM media_job");jdbc.update("DELETE FROM media_asset");jdbc.update("DELETE FROM media_quota");
        jdbc.update("DELETE FROM catalog_course");jdbc.update("DELETE FROM identity_account");objects.fail=false;objects.reads=0;
    }
    @Test void coverUploadUsesExistingValidationPipeline() throws Exception {
        String coach=account("COACH");
        var response=mvc.perform(post("/api/coach/media/uploads").with(user(coach).roles("COACH")).with(csrf())
                .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"COURSE_COVER\",\"contentType\":\"image/png\",\"size\":4}"))
                .andExpect(status().isCreated()).andReturn();
        String id=JsonPath.read(response.getResponse().getContentAsString(),"$.id");
        mvc.perform(post("/api/coach/media/uploads/{id}/complete",id).with(user(coach).roles("COACH")).with(csrf())).andExpect(status().isAccepted());
        assertThat(jobs.runOne()).isTrue();
        mvc.perform(get("/api/coach/media/uploads/{id}",id).with(user(coach).roles("COACH")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));
    }
    @Test void readyCoverAndCompositionAreSavedAndReadableOnlyThroughSavedReference() throws Exception {
        String coach=account("COACH"),student=account("STUDENT"),asset=asset(coach,"COURSE_COVER","READY");
        String id=create(coach,",\"coverAssetId\":\""+asset+"\",\"coverPositionX\":18.25,\"coverPositionY\":100");
        assertThat(jdbc.queryForObject("SELECT asset_id FROM media_reference WHERE consumer=?",String.class,"COURSE:"+id)).isEqualTo(asset);
        mvc.perform(get("/api/courses").with(user(student).roles("STUDENT"))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.items[0].coverAssetId").value(asset)).andExpect(jsonPath("$.items[0].coverPositionX").value(18.25))
                .andExpect(jsonPath("$.items[0].coverPositionY").value(100)).andExpect(jsonPath("$.items[0].cover.id").value(asset))
                .andExpect(jsonPath("$.items[0].cover.width").value(1600));
        int reads=objects.reads;assertThat(courses.findPublished(id)).isNotNull();assertThat(courses.lockPublished(id)).isNotNull();assertThat(objects.reads).isEqualTo(reads);
        jdbc.update("DELETE FROM media_reference WHERE consumer=?","COURSE:"+id);
        jdbc.update("INSERT INTO media_reference (consumer,slot,purpose,asset_id) VALUES ('COURSE:other','PUBLISHED','COURSE_COVER',?)",asset);
        mvc.perform(get("/api/courses").with(user(student).roles("STUDENT"))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].cover").isEmpty());
    }
    @Test void missingFieldsPreserveCoverPositionOnlyPatchWorksAndNewImageResetsPosition() throws Exception {
        String coach=account("COACH"),asset=asset(coach,"COURSE_COVER","READY"),second=asset(coach,"COURSE_COVER","READY");
        String id=create(coach,",\"coverAssetId\":\""+asset+"\",\"coverPositionX\":15,\"coverPositionY\":75");
        patch(coach,id,body("")).andExpect(status().isOk()).andExpect(jsonPath("$.coverAssetId").value(asset)).andExpect(jsonPath("$.coverPositionX").value(15));
        patch(coach,id,"{\"coverPositionX\":25.5}").andExpect(status().isOk()).andExpect(jsonPath("$.coverPositionX").value(25.5)).andExpect(jsonPath("$.coverPositionY").value(75));
        patch(coach,id,"{\"coverAssetId\":\""+second+"\"}").andExpect(status().isOk()).andExpect(jsonPath("$.coverPositionX").value(50)).andExpect(jsonPath("$.coverPositionY").value(50));
        patch(coach,id,"{\"coverAssetId\":null}").andExpect(status().isOk()).andExpect(jsonPath("$.coverAssetId").isEmpty()).andExpect(jsonPath("$.coverPositionX").value(50));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_reference WHERE consumer=?",Integer.class,"COURSE:"+id)).isZero();
    }
    @Test void twoCoursesAndGeerReferencesAreIsolatedAndArchiveClearsOnlyItsOwnReference() throws Exception {
        String coach=account("COACH"),a=asset(coach,"COURSE_COVER","READY"),b=asset(coach,"COURSE_COVER","READY"),hero=asset(coach,"HERO","READY");
        media.replaceReferences(coach,"PUBLISHED",Map.of("HERO",hero));
        String first=create(coach,",\"coverAssetId\":\""+a+"\""),second=create(coach,",\"coverAssetId\":\""+a+"\"");
        patch(coach,first,"{\"coverAssetId\":\""+b+"\"}").andExpect(status().isOk());
        for(int i=0;i<2;i++)mvc.perform(post("/api/coach/courses/{id}/archive",first).with(user(coach).roles("COACH")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.coverAssetId").isEmpty()).andExpect(jsonPath("$.coverPositionX").value(50));
        assertThat(jdbc.queryForList("SELECT asset_id FROM media_reference",String.class)).containsExactlyInAnyOrder(a,hero);
        assertThat(jdbc.queryForObject("SELECT cover_asset_id FROM catalog_course WHERE id=?",String.class,second)).isEqualTo(a);
        assertThat(media.published(Map.of("HERO",hero))).containsKey("HERO");
    }
    @Test void rejectsWrongOwnerPurposeStateAndUnauthorizedCourseMutation() throws Exception {
        // A second authenticated coach context exercises ownership; production permits one coach account.
        String coach=account("COACH"),other=account("STUDENT"),student=account("STUDENT");
        for(String state:List.of("UPLOADING","VERIFYING","REJECTED","FAILED","EXPIRED","DELETING","DELETED"))
            postCourse(coach,body(",\"coverAssetId\":\""+asset(coach,"COURSE_COVER",state)+"\""),UUID.randomUUID().toString()).andExpect(status().isConflict());
        postCourse(coach,body(",\"coverAssetId\":\""+asset(other,"COURSE_COVER","READY")+"\""),UUID.randomUUID().toString()).andExpect(status().isConflict());
        postCourse(coach,body(",\"coverAssetId\":\""+asset(coach,"HERO","READY")+"\""),UUID.randomUUID().toString()).andExpect(status().isBadRequest());
        String id=create(coach,"");patch(other,id,"{\"coverAssetId\":null}").andExpect(status().isForbidden());
        assertThatThrownBy(()->courses.update(new Actor(other,"COACH","Other"),id,new CourseOperations.UpdateCourse("title","","100","CAD")))
                .isInstanceOf(com.geer.snowboard.v2.sharedkernel.BusinessProblem.class)
                .extracting(e->((com.geer.snowboard.v2.sharedkernel.BusinessProblem)e).status()).isEqualTo(404);
        mvc.perform(get("/api/courses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/coach/courses").with(user(student).roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/coach/courses").with(user(student).roles("STUDENT")).with(csrf()).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(body(""))).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/coach/courses/{id}",id).with(user(coach).roles("COACH")).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }
    @Test void invalidPositionsAreRejectedAndNoImageAlwaysNormalizesToCenter() throws Exception {
        String coach=account("COACH"),id=create(coach,"");
        for(String value:List.of("null","-0.01","100.01","1.001","\"NaN\"","\"5\"","true","{}","1e1000")) {
            patch(coach,id,"{\"coverPositionX\":"+value+"}").andExpect(status().isBadRequest());
            postCourse(coach,body(",\"coverPositionY\":"+value),UUID.randomUUID().toString()).andExpect(status().isBadRequest());
        }
        patch(coach,id,"{\"coverPositionX\":0,\"coverPositionY\":100}").andExpect(status().isOk()).andExpect(jsonPath("$.coverPositionX").value(50)).andExpect(jsonPath("$.coverPositionY").value(50));
    }
    @Test void signingFailureDoesNotBreakCourseListOrBookingLookup() throws Exception {
        String coach=account("COACH"),student=account("STUDENT"),asset=asset(coach,"COURSE_COVER","READY");
        String id=create(coach,",\"coverAssetId\":\""+asset+"\"");objects.fail=true;
        mvc.perform(get("/api/courses").with(user(student).roles("STUDENT"))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(id)).andExpect(jsonPath("$.items[0].cover").isEmpty());
        assertThat(courses.findPublished(id).active()).isTrue();
    }
    @Test void sameCreationKeyReplaysAndCoverOrPositionChangesConflict() throws Exception {
        String coach=account("COACH"),a=asset(coach,"COURSE_COVER","READY"),b=asset(coach,"COURSE_COVER","READY"),key=UUID.randomUUID().toString();
        String request=body(",\"coverAssetId\":\""+a+"\",\"coverPositionX\":1.5");
        postCourse(coach,request,key).andExpect(status().isCreated());
        postCourse(coach,request.replace("1.5","1.50"),key).andExpect(status().isOk());
        postCourse(coach,request.replace(a,b),key).andExpect(status().isConflict());
        postCourse(coach,request.replace("1.5","2"),key).andExpect(status().isConflict());
        String oldKey=UUID.randomUUID().toString();postCourse(coach,body(""),oldKey).andExpect(status().isCreated());
        postCourse(coach,body(",\"coverAssetId\":null,\"coverPositionX\":20"),oldKey).andExpect(status().isOk());
    }
    @Test void databaseFailureRollsBackCourseAndReferenceReplacement() throws Exception {
        String coach=account("COACH"),a=asset(coach,"COURSE_COVER","READY"),b=asset(coach,"COURSE_COVER","READY"),id=create(coach,",\"coverAssetId\":\""+a+"\"");
        jdbc.execute("CREATE TRIGGER fail_course_reference BEFORE INSERT ON media_reference FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test failure'");
        try { patch(coach,id,"{\"coverAssetId\":\""+b+"\",\"coverPositionX\":0}").andExpect(status().is5xxServerError()); }
        finally {jdbc.execute("DROP TRIGGER fail_course_reference");}
        assertThat(jdbc.queryForObject("SELECT cover_asset_id FROM catalog_course WHERE id=?",String.class,id)).isEqualTo(a);
        assertThat(jdbc.queryForObject("SELECT asset_id FROM media_reference WHERE consumer=?",String.class,"COURSE:"+id)).isEqualTo(a);
        assertThat(jdbc.queryForObject("SELECT cover_position_x FROM catalog_course WHERE id=?",Double.class,id)).isEqualTo(50);
    }
    @Test void concurrentDuplicateCreationProducesOneCourseAndReference() throws Exception {
        String coach=account("COACH"),asset=asset(coach,"COURSE_COVER","READY"),key=UUID.randomUUID().toString();
        String content=body(",\"coverAssetId\":\""+asset+"\"");var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Integer> attempt=()->{start.await();return postCourse(coach,content,key).andReturn().getResponse().getStatus();};
            var first=executor.submit(attempt);var second=executor.submit(attempt);start.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,201);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_course WHERE coach_id=?",Integer.class,coach)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_reference WHERE asset_id=?",Integer.class,asset)).isEqualTo(1);
    }
    @Test void concurrentCrossReplacementAndCleanupKeepAllSavedCoversReady() throws Exception {
        String coach=account("COACH"),a=asset(coach,"COURSE_COVER","READY"),b=asset(coach,"COURSE_COVER","READY");
        String first=create(coach,",\"coverAssetId\":\""+a+"\""),second=create(coach,",\"coverAssetId\":\""+b+"\"");
        jdbc.update("UPDATE media_asset SET terminal_at=?,unreferenced_at=?",Instant.now().minusSeconds(10*86400),Instant.now().minusSeconds(8*86400));
        var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(3)) {
            var one=executor.submit(()->{start.await();return patch(coach,first,"{\"coverAssetId\":\""+b+"\"}").andReturn().getResponse().getStatus();});
            var two=executor.submit(()->{start.await();return patch(coach,second,"{\"coverAssetId\":\""+a+"\"}").andReturn().getResponse().getStatus();});
            var clean=executor.submit(()->{start.await();jobs.scheduleCleanup();while(jobs.runOne()){}return true;});start.countDown();
            assertThat(one.get(20,TimeUnit.SECONDS)).isEqualTo(200);assertThat(two.get(20,TimeUnit.SECONDS)).isEqualTo(200);assertThat(clean.get(20,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(jdbc.queryForList("SELECT status FROM media_asset",String.class)).containsOnly("READY");
        assertThat(jdbc.queryForList("SELECT asset_id FROM media_reference",String.class)).containsExactlyInAnyOrder(a,b);
    }
    private String create(String coach,String extra) throws Exception {
        return JsonPath.read(postCourse(coach,body(extra),UUID.randomUUID().toString()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),"$.id");
    }
    private static String body(String extra){return "{\"title\":\"滑行进阶\",\"description\":\"课程简介\",\"priceAmount\":\"100.00\",\"currency\":\"CAD\""+extra+"}";}
    private ResultActions postCourse(String coach,String content,String key) throws Exception {
        return mvc.perform(post("/api/coach/courses").with(user(coach).roles("COACH")).with(csrf()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(content));
    }
    private ResultActions patch(String coach,String id,String content) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/coach/courses/{id}",id).with(user(coach).roles("COACH")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(content));
    }
    private String account(String role) {
        String id=UUID.randomUUID().toString();jdbc.update("INSERT INTO identity_account (id,email,email_key,name,level,role,password_hash,verified_at,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                id,id+"@test.invalid",id+"@test.invalid","Test",role.equals("STUDENT")?"BEGINNER":null,role,"unused",Instant.now(),Instant.now());return id;
    }
    private String asset(String owner,String purpose,String status) {
        String id=UUID.randomUUID().toString();Instant now=Instant.now();
        jdbc.update("INSERT INTO media_asset (id,owner_id,purpose,content_type,expected_size,actual_size,staging_key,frozen_key,status,request_key,fingerprint,created_at,upload_expires_at,unreferenced_at,terminal_at,width,height) VALUES (?,?,?,'image/png',4,4,?,?,?,?,?,?,?, ?,?,1600,900)",
                id,owner,purpose,"staging/"+id,"frozen/"+id,status,UUID.randomUUID().toString(),"a".repeat(64),now,now.plusSeconds(600),now,now);return id;
    }
}
