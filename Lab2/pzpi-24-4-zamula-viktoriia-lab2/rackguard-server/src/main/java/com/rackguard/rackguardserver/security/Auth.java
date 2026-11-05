package com.rackguard.rackguardserver.security;

import com.rackguard.rackguardserver.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;


public final class Auth {
    private Auth() {}

    public record UserPrincipal(Integer userId, String role) {}
    public record DevicePrincipal(String deviceId, Integer doorId) {}

    private static Object principal() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "error.unauthorized");
        return a.getPrincipal();
    }

    public static Integer userId() {
        if (principal() instanceof UserPrincipal u) return u.userId();
        throw new ApiException(HttpStatus.UNAUTHORIZED, "error.unauthorized");
    }

    public static DevicePrincipal device() {
        if (principal() instanceof DevicePrincipal d) return d;
        throw new ApiException(HttpStatus.UNAUTHORIZED, "error.unauthorized");
    }
}
