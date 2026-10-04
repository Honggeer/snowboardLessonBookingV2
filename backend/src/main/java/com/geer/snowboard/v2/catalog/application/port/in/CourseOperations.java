package com.geer.snowboard.v2.catalog.application.port.in;

import com.geer.snowboard.v2.sharedkernel.Actor;
import com.geer.snowboard.v2.sharedkernel.Creation;
import com.geer.snowboard.v2.sharedkernel.Page;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

public interface CourseOperations {
    record Course(String id, String coachId, String title, String description,
                  BigDecimal priceAmount, String currency, boolean active, String coverAssetId,
                  BigDecimal coverPositionX, BigDecimal coverPositionY) {
        public Course(String id,String coachId,String title,String description,BigDecimal priceAmount,String currency,boolean active) {
            this(id,coachId,title,description,priceAmount,currency,active,null,new BigDecimal("50"),new BigDecimal("50"));
        }
    }
    record Cover(String id,String url,Instant expiresAt,int width,int height) {}
    record CreateCourse(String title, String description, String priceAmount, String currency,
                        String coverAssetId, BigDecimal coverPositionX, BigDecimal coverPositionY, Set<String> fields) {
        public CreateCourse(String title,String description,String priceAmount,String currency) {
            this(title,description,priceAmount,currency,null,null,null,Set.of());
        }
    }
    record UpdateCourse(String title, String description, String priceAmount, String currency,
                        String coverAssetId, BigDecimal coverPositionX, BigDecimal coverPositionY, Set<String> fields) {
        public UpdateCourse(String title,String description,String priceAmount,String currency) {
            this(title,description,priceAmount,currency,null,null,null,Set.of("title","description","priceAmount","currency"));
        }
    }

    Creation<Course> create(Actor actor, CreateCourse command, String idempotencyKey);
    Page<Course> published(Actor actor, Integer limit, String cursor);
    Page<Course> coachCourses(Actor actor, Integer limit, String cursor);
    Course findPublished(String id);
    Course lockPublished(String id);
    Course update(Actor actor, String id, UpdateCourse command);
    Course archive(Actor actor, String id);
    Cover cover(Course course);
}
