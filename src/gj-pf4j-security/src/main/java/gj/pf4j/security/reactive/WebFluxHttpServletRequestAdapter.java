/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security.reactive;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapts a WebFlux {@link ServerWebExchange} to {@link HttpServletRequest}
 * so that {@code IPluginAuthenticationProvider} works for both MVC and WebFlux.
 */
class WebFluxHttpServletRequestAdapter implements HttpServletRequest {

    private final ServerHttpRequest request;
    private final byte[] cachedBody;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final Cookie[] cookies;
    private final Map<String, String[]> parameterMap;

    WebFluxHttpServletRequestAdapter(ServerWebExchange exchange) {
        this(exchange, new byte[0]);
    }

    WebFluxHttpServletRequestAdapter(ServerWebExchange exchange, byte[] cachedBody) {
        this.request = exchange.getRequest();
        this.cachedBody = cachedBody;
        this.cookies = buildCookies(request);
        this.parameterMap = buildParameterMap(request);
    }

    private static Cookie[] buildCookies(ServerHttpRequest request) {
        var cookieMap = request.getCookies();
        if (cookieMap == null || cookieMap.isEmpty()) {
            return new Cookie[0];
        }
        List<Cookie> list = new ArrayList<>();
        for (Map.Entry<String, List<HttpCookie>> entry : cookieMap.entrySet()) {
            for (HttpCookie hc : entry.getValue()) {
                Cookie cookie = new Cookie(hc.getName(), hc.getValue());
                cookie.setPath("/");
                cookie.setMaxAge(-1);
                list.add(cookie);
            }
        }
        return list.toArray(new Cookie[0]);
    }

    private static Map<String, String[]> buildParameterMap(ServerHttpRequest request) {
        String query = request.getURI().getRawQuery();
        if (query == null || query.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> temp = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = java.net.URLDecoder.decode(pair.substring(0, idx),
                        java.nio.charset.StandardCharsets.UTF_8);
                String value = java.net.URLDecoder.decode(pair.substring(idx + 1),
                        java.nio.charset.StandardCharsets.UTF_8);
                temp.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            } else if (idx < 0 && !pair.isEmpty()) {
                String key = java.net.URLDecoder.decode(pair,
                        java.nio.charset.StandardCharsets.UTF_8);
                temp.computeIfAbsent(key, k -> new ArrayList<>()).add("");
            }
        }
        Map<String, String[]> result = new LinkedHashMap<>();
        temp.forEach((k, v) -> result.put(k, v.toArray(new String[0])));
        return Collections.unmodifiableMap(result);
    }

    @Override public String getHeader(String name) {
        return request.getHeaders().getFirst(name);
    }
    @Override public String getRequestURI() {
        return request.getPath().pathWithinApplication().value();
    }
    @Override public String getMethod() {
        return request.getMethod().name();
    }
    @Override public StringBuffer getRequestURL() {
        return new StringBuffer(request.getURI().toString());
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        byte[] body = cachedBody;
        return new ServletInputStream() {
            private int index = 0;
            @Override public int read() { return index < body.length ? body[index++] & 0xFF : -1; }
            @Override public boolean isFinished() { return index >= body.length; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) { }
        };
    }

    @Override public Enumeration<String> getHeaders(String name) {
        List<String> values = request.getHeaders().get(name);
        return values != null ? Collections.enumeration(values) : Collections.emptyEnumeration();
    }
    @Override public Enumeration<String> getHeaderNames() {
        return Collections.enumeration(request.getHeaders().keySet());
    }

    @Override public Object getAttribute(String name) {
        return attributes.get(name);
    }
    @Override public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(attributes.keySet());
    }
    @Override public void setAttribute(String name, Object o) {
        attributes.put(name, o);
    }
    @Override public void removeAttribute(String name) {
        attributes.remove(name);
    }

    @Override public String getAuthType() { return null; }
    @Override public Cookie[] getCookies() { return cookies; }
    @Override public long getDateHeader(String name) { return -1; }
    @Override public int getIntHeader(String name) { return -1; }
    @Override public String getPathInfo() { return null; }
    @Override public String getPathTranslated() { return null; }
    @Override public String getContextPath() { return ""; }
    @Override public String getQueryString() {
        return request.getURI().getRawQuery();
    }
    @Override public String getRemoteUser() { return null; }
    @Override public boolean isUserInRole(String role) { return false; }
    @Override public Principal getUserPrincipal() { return null; }
    @Override public String getRequestedSessionId() { return null; }
    @Override public String getServletPath() { return ""; }
    @Override public HttpSession getSession(boolean create) { return null; }
    @Override public HttpSession getSession() { return null; }
    @Override public String changeSessionId() { return null; }
    @Override public boolean isRequestedSessionIdValid() { return false; }
    @Override public boolean isRequestedSessionIdFromCookie() { return false; }
    @Override public boolean isRequestedSessionIdFromURL() { return false; }
    @Override public boolean authenticate(HttpServletResponse response) { return false; }
    @Override public void login(String username, String password) { }
    @Override public void logout() { }
    @Override public Collection<Part> getParts() { return List.of(); }
    @Override public Part getPart(String name) { return null; }
    @Override public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) { return null; }
    @Override public String getLocalAddr() {
        InetSocketAddress local = request.getLocalAddress();
        return local != null ? local.getAddress().getHostAddress() : "127.0.0.1";
    }
    @Override public String getLocalName() {
        InetSocketAddress local = request.getLocalAddress();
        return local != null ? local.getHostString() : "localhost";
    }
    @Override public int getLocalPort() {
        InetSocketAddress local = request.getLocalAddress();
        return local != null ? local.getPort() : 0;
    }
    @Override public String getProtocol() {
        String proto = request.getHeaders().getFirst("X-Forwarded-Proto");
        return proto != null ? proto : "HTTP/1.1";
    }
    @Override public String getRemoteAddr() {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        InetSocketAddress remote = request.getRemoteAddress();
        return remote != null ? remote.getAddress().getHostAddress() : "127.0.0.1";
    }
    @Override public String getRemoteHost() {
        InetSocketAddress remote = request.getRemoteAddress();
        return remote != null ? remote.getHostString() : "localhost";
    }
    @Override public int getRemotePort() {
        InetSocketAddress remote = request.getRemoteAddress();
        return remote != null ? remote.getPort() : 0;
    }
    @Override public String getScheme() {
        String proto = request.getHeaders().getFirst("X-Forwarded-Proto");
        return proto != null ? proto : request.getURI().getScheme();
    }
    @Override public String getServerName() {
        String host = request.getHeaders().getFirst("Host");
        if (host != null && !host.isBlank()) {
            int colon = host.indexOf(':');
            return colon > 0 ? host.substring(0, colon) : host;
        }
        String uriHost = request.getURI().getHost();
        return uriHost != null ? uriHost : "localhost";
    }
    @Override public int getServerPort() {
        String forwardedPort = request.getHeaders().getFirst("X-Forwarded-Port");
        if (forwardedPort != null && !forwardedPort.isBlank()) {
            return Integer.parseInt(forwardedPort.strip());
        }
        String host = request.getHeaders().getFirst("Host");
        if (host != null) {
            int colon = host.lastIndexOf(':');
            if (colon > 0 && colon < host.length() - 1) {
                return Integer.parseInt(host.substring(colon + 1));
            }
        }
        int uriPort = request.getURI().getPort();
        if (uriPort > 0) return uriPort;
        return "https".equals(getScheme()) ? 443 : 80;
    }
    @Override public BufferedReader getReader() throws IOException {
    return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(cachedBody)));
}
    @Override public String getCharacterEncoding() { return "UTF-8"; }
    @Override public void setCharacterEncoding(String env) { }
    @Override public int getContentLength() { return cachedBody.length; }
    @Override public long getContentLengthLong() { return cachedBody.length; }
    @Override public String getContentType() { return request.getHeaders().getFirst("Content-Type"); }
    @Override public String getParameter(String name) {
        String[] values = parameterMap.get(name);
        return values != null && values.length > 0 ? values[0] : null;
    }
    @Override public Enumeration<String> getParameterNames() {
        return Collections.enumeration(parameterMap.keySet());
    }
    @Override public String[] getParameterValues(String name) {
        String[] values = parameterMap.get(name);
        return values != null ? values : new String[0];
    }
    @Override public Map<String, String[]> getParameterMap() { return parameterMap; }
    @Override public Locale getLocale() { return Locale.getDefault(); }
    @Override public Enumeration<Locale> getLocales() { return Collections.enumeration(List.of(Locale.getDefault())); }
    @Override public boolean isSecure() { return "https".equals(getScheme()); }
    @Override public ServletContext getServletContext() { return null; }
    @Override public AsyncContext startAsync() { throw new IllegalStateException(); }
    @Override public AsyncContext startAsync(ServletRequest req, ServletResponse res) { throw new IllegalStateException(); }
    @Override public boolean isAsyncStarted() { return false; }
    @Override public boolean isAsyncSupported() { return false; }
    @Override public AsyncContext getAsyncContext() { throw new IllegalStateException(); }
    @Override public DispatcherType getDispatcherType() { return DispatcherType.REQUEST; }
    @Override public RequestDispatcher getRequestDispatcher(String path) { return null; }
    @Override public String getRequestId() { return null; }
    @Override public String getProtocolRequestId() { return null; }
    @Override public ServletConnection getServletConnection() { return null; }
}
