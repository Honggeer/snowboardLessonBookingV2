package com.geer.snowboard.v2.identity.adapter.in.web;

import com.geer.snowboard.v2.identity.application.port.in.IdentityOperations;
import com.geer.snowboard.v2.identity.application.port.in.StudentContactOperations;
import com.geer.snowboard.v2.sharedkernel.Actor;
import com.geer.snowboard.v2.sharedkernel.BusinessProblem;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public final class StudentContactController {
    public record SaveContactRequest(String phone) {}
    public record ContactResponse(String phone) {}
    private final StudentContactOperations contacts;
    private final IdentityOperations identity;
    public StudentContactController(StudentContactOperations contacts, IdentityOperations identity) {
        this.contacts = contacts; this.identity = identity;
    }
    @GetMapping("/api/student/contact")
    public ContactResponse current(Authentication authentication) {
        return new ContactResponse(contacts.current(actor(authentication)).phone());
    }
    @PatchMapping("/api/student/contact")
    public ContactResponse save(Authentication authentication, @RequestBody SaveContactRequest request) {
        return new ContactResponse(contacts.save(actor(authentication), new StudentContactOperations.SaveContact(request.phone())).phone());
    }
    // Resolve identity here to keep identity independent of bootstrap, which itself depends on identity.
    private Actor actor(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) throw new BusinessProblem(401, "请先登录");
        var account = identity.findById(authentication.getName());
        if (account == null) throw new BusinessProblem(401, "登录已失效");
        return new Actor(account.id(), account.role(), account.name());
    }
}
