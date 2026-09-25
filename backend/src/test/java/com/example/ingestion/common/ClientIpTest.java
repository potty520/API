package com.example.ingestion.common;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 只有回环反代才能提供可信的 X-Forwarded-For; 直连客户端自带的该头必须被忽略,
 * 否则登录锁定与审计都能被伪造 IP 绕过。
 */
class ClientIpTest {

    private static HttpServletRequest request(String remoteAddr, String forwarded) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        when(request.getHeader("X-Forwarded-For")).thenReturn(forwarded);
        return request;
    }

    @Test
    void usesForwardedFirstHopFromLoopbackProxy() {
        assertEquals("203.0.113.9", ClientIp.of(request("127.0.0.1", "203.0.113.9, 10.0.0.1")));
        assertEquals("203.0.113.9", ClientIp.of(request("::1", "203.0.113.9")));
    }

    @Test
    void ignoresForwardedHeaderFromNonLoopbackPeer() {
        assertEquals("203.0.113.5", ClientIp.of(request("203.0.113.5", "8.8.8.8")));
    }

    @Test
    void fallsBackToPeerAddressWhenHeaderIsUnusable() {
        assertEquals("127.0.0.1", ClientIp.of(request("127.0.0.1", null)));
        assertEquals("127.0.0.1", ClientIp.of(request("127.0.0.1", "   ")));
        assertEquals("127.0.0.1", ClientIp.of(request("127.0.0.1", "not an ip; drop table")));
        assertEquals("", ClientIp.of(request(null, "8.8.8.8")));
    }
}
