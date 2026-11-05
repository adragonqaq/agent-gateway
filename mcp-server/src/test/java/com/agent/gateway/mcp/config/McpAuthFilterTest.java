package com.agent.gateway.mcp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Bearer 鉴权过滤器单测：关闭 / 通过 / 拒绝三条路径。
 */
class McpAuthFilterTest {

    @Test
    void disabledAllowsWithoutHeader() throws ServletException, IOException {
        McpAuthProperties props = new McpAuthProperties();
        props.setToken("");
        McpAuthFilter filter = new McpAuthFilter(props);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        verify(chain).doFilter(req, res);
        assertEquals(200, res.getStatus());
    }

    @Test
    void enabledRejectsMissingBearer() throws ServletException, IOException {
        McpAuthProperties props = new McpAuthProperties();
        props.setToken("demo-secret");
        McpAuthFilter filter = new McpAuthFilter(props);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        verify(chain, never()).doFilter(req, res);
        assertEquals(401, res.getStatus());
    }

    @Test
    void enabledAcceptsMatchingBearer() throws ServletException, IOException {
        McpAuthProperties props = new McpAuthProperties();
        props.setToken("demo-secret");
        McpAuthFilter filter = new McpAuthFilter(props);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp");
        req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer demo-secret");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        verify(chain).doFilter(req, res);
    }
}
