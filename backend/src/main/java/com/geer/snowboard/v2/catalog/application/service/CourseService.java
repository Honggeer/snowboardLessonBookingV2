package com.geer.snowboard.v2.catalog.application.service;

import com.geer.snowboard.v2.catalog.application.port.in.CourseOperations;
import com.geer.snowboard.v2.catalog.application.port.out.CourseStore;
import com.geer.snowboard.v2.sharedkernel.Actor;
import com.geer.snowboard.v2.sharedkernel.BusinessProblem;
import com.geer.snowboard.v2.sharedkernel.Creation;
import com.geer.snowboard.v2.sharedkernel.Page;
import com.geer.snowboard.v2.sharedkernel.RequestKeys;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseService implements CourseOperations {
    private final CourseStore store;
    public CourseService(CourseStore store) { this.store = store; }

    @Override @Transactional
    public Creation<Course> create(Actor actor, CreateCourse command, String idempotencyKey) {
        actor.require("COACH");
        if (command == null) throw new BusinessProblem(400, "请填写课程信息");
        String key = RequestKeys.key(idempotencyKey);
        String title = clean(command.title(), 100, "课程名称");
        String description = description(command.description());
        BigDecimal price = price(command.priceAmount(), command.currency());
        String fingerprint = RequestKeys.fingerprint(title + "\u0000" + description + "\u0000" + price.toPlainString() + "\u0000CAD");
        Course existing = store.findByKey(actor.id(), key);
        if (existing != null) return replay(existing, store.fingerprint(actor.id(), key), fingerprint);
        Course course = new Course(UUID.randomUUID().toString(), actor.id(), title, description, price, "CAD", true);
        if (store.insert(course, key, fingerprint)) return new Creation<>(course, true);
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

    @Override @Transactional
    public Course update(Actor actor, String id, UpdateCourse command) {
        actor.require("COACH");
        if (command == null) throw new BusinessProblem(400, "请填写课程信息");
        String title = clean(command.title(), 100, "课程名称");
        String description = description(command.description());
        BigDecimal price = price(command.priceAmount(), command.currency());
        Course course = ownLocked(actor, id);
        if (!course.active()) throw new BusinessProblem(409, "已下架课程不能修改");
        store.update(id, title, description, price);
        return store.findById(id);
    }

    @Override @Transactional
    public Course archive(Actor actor, String id) {
        actor.require("COACH");
        Course course = ownLocked(actor, id);
        if (course.active()) store.archive(id);
        return store.findById(id);
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
