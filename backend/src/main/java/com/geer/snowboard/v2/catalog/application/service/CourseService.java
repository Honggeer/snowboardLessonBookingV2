package com.geer.snowboard.v2.catalog.application.service;

import com.geer.snowboard.v2.catalog.application.port.in.CourseOperations;
import com.geer.snowboard.v2.catalog.application.port.out.CourseStore;
import com.geer.snowboard.v2.catalog.application.port.out.CourseMedia;
import com.geer.snowboard.v2.sharedkernel.Actor;
import com.geer.snowboard.v2.sharedkernel.BusinessProblem;
import com.geer.snowboard.v2.sharedkernel.Creation;
import com.geer.snowboard.v2.sharedkernel.Page;
import com.geer.snowboard.v2.sharedkernel.RequestKeys;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class CourseService implements CourseOperations {
    private final CourseStore store;
    private final CourseMedia media;
    private static final BigDecimal CENTER = new BigDecimal("50.00");
    public CourseService(CourseStore store,CourseMedia media) { this.store = store; this.media = media; }

    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public Creation<Course> create(Actor actor, CreateCourse command, String idempotencyKey) {
        actor.require("COACH");
        if (command == null) throw new BusinessProblem(400, "请填写课程信息");
        String key = RequestKeys.key(idempotencyKey);
        String title = clean(command.title(), 100, "课程名称");
        String description = description(command.description());
        BigDecimal price = price(command.priceAmount(), command.currency());
        String asset=asset(command.coverAssetId());
        BigDecimal x=position(command.coverPositionX(),command.fields().contains("coverPositionX"),CENTER);
        BigDecimal y=position(command.coverPositionY(),command.fields().contains("coverPositionY"),CENTER);
        if(asset==null){x=CENTER;y=CENTER;}
        String fingerprint = RequestKeys.fingerprint(title + "\u0000" + description + "\u0000" + price.toPlainString() + "\u0000CAD"
                +(asset==null?"":"\u0000"+asset+"\u0000"+x.toPlainString()+"\u0000"+y.toPlainString()));
        Course existing = store.findByKey(actor.id(), key);
        if (existing != null) return replay(existing, store.fingerprint(actor.id(), key), fingerprint);
        Course course = new Course(UUID.randomUUID().toString(), actor.id(), title, description, price, "CAD", true,asset,x,y);
        if (store.insert(course, key, fingerprint)) {
            media.save(actor.id(),course.id(),asset);
            return new Creation<>(course, true);
        }
        existing = store.findByKey(actor.id(), key);
        if (existing == null) throw new BusinessProblem(409, "课程创建冲突，请重试");
        return replay(existing, store.fingerprint(actor.id(), key), fingerprint);
    }

    @Override public Page<Course> published(Actor actor, Integer limit, String cursor) {
        actor.require("STUDENT");
        return store.list(null, RequestKeys.limit(limit), cursor);
    }

    @Override public Page<Course> coachCourses(Actor actor, Integer limit, String cursor) {
        actor.require("COACH");
        return store.list(actor.id(), RequestKeys.limit(limit), cursor);
    }

    @Override public Course findPublished(String id) {
        Course course = store.findById(id);
        return course != null && course.active() ? course : null;
    }
    @Override public Course lockPublished(String id) {
        Course course = store.lockById(id);
        return course != null && course.active() ? course : null;
    }

    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public Course update(Actor actor, String id, UpdateCourse command) {
        actor.require("COACH");
        if (command == null) throw new BusinessProblem(400, "请填写课程信息");
        Course course = ownLocked(actor, id);
        if (!course.active()) throw new BusinessProblem(409, "已下架课程不能修改");
        var fields=command.fields();
        String title=fields.contains("title")?clean(command.title(),100,"课程名称"):course.title();
        String description=fields.contains("description")?description(command.description()):course.description();
        BigDecimal price=fields.contains("priceAmount")||fields.contains("currency")
                ?price(fields.contains("priceAmount")?command.priceAmount():course.priceAmount().toPlainString(),
                    fields.contains("currency")?command.currency():course.currency()):course.priceAmount();
        String asset=fields.contains("coverAssetId")?asset(command.coverAssetId()):course.coverAssetId();
        boolean changed=!Objects.equals(asset,course.coverAssetId());
        BigDecimal x=position(command.coverPositionX(),fields.contains("coverPositionX"),changed?CENTER:course.coverPositionX());
        BigDecimal y=position(command.coverPositionY(),fields.contains("coverPositionY"),changed?CENTER:course.coverPositionY());
        if(asset==null){x=CENTER;y=CENTER;}
        media.save(actor.id(),id,asset);
        store.update(new Course(id,course.coachId(),title,description,price,course.currency(),true,asset,x,y));
        return store.findById(id);
    }

    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public Course archive(Actor actor, String id) {
        actor.require("COACH");
        Course course = ownLocked(actor, id);
        media.save(actor.id(),id,null);
        if (course.active()) store.archive(id);
        return store.findById(id);
    }

    @Override public Cover cover(Course course) {
        return course.active()?media.published(course.id(),course.coverAssetId()):null;
    }
    private static String asset(String value) {
        if(value!=null&&!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new BusinessProblem(400,"无效封面素材");
        return value;
    }
    private static BigDecimal position(BigDecimal value,boolean supplied,BigDecimal fallback) {
        if(!supplied)return fallback;
        if(value==null||value.signum()<0||value.compareTo(new BigDecimal("100"))>0)
            throw new BusinessProblem(400,"构图位置须在 0–100 之间");
        try{return value.setScale(2,RoundingMode.UNNECESSARY);}
        catch(ArithmeticException e){throw new BusinessProblem(400,"构图位置最多两位小数");}
    }
    private Course ownLocked(Actor actor, String id) {
        Course course = store.lockById(id);
        if (course == null || !actor.id().equals(course.coachId())) throw new BusinessProblem(404, "课程不存在");
        return course;
    }
    private static String description(String value) {
        String result = value == null ? "" : value.strip();
        if (result.length() > 1000) throw new BusinessProblem(400, "课程介绍过长");
        return result;
    }
    private static BigDecimal price(String value, String currency) {
        if (!"CAD".equals(currency)) throw new BusinessProblem(400, "币种仅支持 CAD");
        BigDecimal price;
        try { price = new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY); }
        catch (RuntimeException error) { throw new BusinessProblem(400, "价格须为最多两位小数"); }
        if (price.signum() < 0 || price.compareTo(new BigDecimal("99999999.99")) > 0)
            throw new BusinessProblem(400, "课程价格超出范围");
        return price;
    }

    private static Creation<Course> replay(Course course, String actual, String expected) {
        if (!expected.equals(actual)) throw new BusinessProblem(409, "同一请求键对应了不同课程内容");
        return new Creation<>(course, false);
    }
    private static String clean(String value, int max, String name) {
        if (value == null || value.isBlank() || value.strip().length() > max)
            throw new BusinessProblem(400, name + "不能为空或过长");
        return value.strip();
    }
}
