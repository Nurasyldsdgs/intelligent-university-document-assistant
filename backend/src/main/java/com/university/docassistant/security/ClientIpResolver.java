package com.university.docassistant.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * The client's IP address for rate limiting. By default the TCP peer address (request.getRemoteAddr()): a client
 * can put anything into X-Forwarded-For, so trusting it would let an attacker pick a fresh "IP" for every attempt.
 * Behind a reverse proxy that sets the header, enable {@code app.security.trust-forwarded-for}; the right-most entry
 * is then used, because that is the one the proxy itself appended (entries to its left came from the client).
 */
@Component
public class ClientIpResolver {

    private final boolean trustForwardedFor;

    public ClientIpResolver(@Value("${app.security.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    public String resolve(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                String[] hops = forwarded.split(",");
                String last = hops[hops.length - 1].trim();
                if (!last.isEmpty()) {
                    return last;
                }
            }
        }
        return request.getRemoteAddr();
    }
}
