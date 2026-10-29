package com.sun.net.httpserver;

import java.util.List;
import java.util.Map;

public abstract class HttpContext {
    private final List<Filter> filters = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile Authenticator authenticator;
    protected HttpContext() {}

    public HttpHandler getHandler() { return null; }
    public void setHandler(HttpHandler h) {}
    public String getPath() { return "/"; }
    public HttpServer getServer() { return null; }
    public Map<String, Object> getAttributes() { return java.util.Collections.emptyMap(); }
    public List<Filter> getFilters() { return filters; }
    public synchronized Authenticator setAuthenticator(Authenticator auth) { Authenticator previous = authenticator; authenticator = auth; return previous; }
    public Authenticator getAuthenticator() { return authenticator; }
}
