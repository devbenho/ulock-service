package com.codgo.ulock.common.web;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolves the caller's IP address for the current request. Forwarded headers are applied by
 * {@code server.forward-headers-strategy}, so the remote address is already the client's.
 */
public final class ClientIp {

    private ClientIp() {}

    public static String current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRemoteAddr();
        }
        return null;
    }
}
