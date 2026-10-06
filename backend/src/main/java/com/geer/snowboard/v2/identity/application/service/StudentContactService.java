package com.geer.snowboard.v2.identity.application.service;

import com.geer.snowboard.v2.identity.application.port.in.StudentContactOperations;
import com.geer.snowboard.v2.identity.application.port.out.IdentityStore;
import com.geer.snowboard.v2.identity.domain.ContactPhone;
import com.geer.snowboard.v2.sharedkernel.Actor;
import com.geer.snowboard.v2.sharedkernel.BusinessProblem;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentContactService implements StudentContactOperations {
    private final IdentityStore store;
    public StudentContactService(IdentityStore store) { this.store = store; }

    @Override public Contact current(Actor actor) {
        requireAccount(actor, "STUDENT");
        return new Contact(store.studentPhone(actor.id()));
    }
    @Override @Transactional public Contact save(Actor actor, SaveContact command) {
        requireAccount(actor, "STUDENT");
        ContactPhone phone;
        try { phone = ContactPhone.parse(command == null ? null : command.phone()); }
        catch (IllegalArgumentException invalid) {
            throw new BusinessProblem(400, "请填写有效的联系电话。");
        }
        store.saveStudentPhone(actor.id(), phone.value());
        return new Contact(phone.value());
    }
    @Override public Map<String, String> phonesForCoach(Actor actor, Set<String> studentIds) {
        requireAccount(actor, "COACH");
        if (studentIds == null || studentIds.size() > 50 || studentIds.stream().anyMatch(id -> id == null || id.isBlank()))
            throw new BusinessProblem(400, "无效联系人查询");
        return studentIds.isEmpty() ? Map.of() : store.studentPhones(studentIds);
    }
    private void requireAccount(Actor actor, String role) {
        if (actor == null || actor.id() == null) throw new BusinessProblem(401, "请先登录");
        actor.require(role);
        var account = store.findById(actor.id());
        if (account == null || account.verifiedAt() == null) throw new BusinessProblem(401, "登录已失效");
        if (!role.equals(account.role())) throw new BusinessProblem(403, "此账号无权执行该操作");
    }
}
