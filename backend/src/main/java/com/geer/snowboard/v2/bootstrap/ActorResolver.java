package com.geer.snowboard.v2.bootstrap;

import com.geer.snowboard.v2.identity.application.port.in.IdentityOperations;
import com.geer.snowboard.v2.sharedkernel.Actor;
import com.geer.snowboard.v2.sharedkernel.BusinessProblem;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public final class ActorResolver {
    private final IdentityOperations identity;
    public ActorResolver(IdentityOperations identity) { this.identity = identity; }

    public Actor current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated())
            throw new BusinessProblem(401, "请先登录");
        var account = identity.findById(authentication.getName());
        if (account == null) throw new BusinessProblem(401, "登录已失效");
        return new Actor(account.id(), account.role(), account.name());
    }
}
