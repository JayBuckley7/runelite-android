package com.sun.net.httpserver;

import java.io.IOException;

public abstract class Filter {
    protected Filter() {}

    public abstract void doFilter(HttpExchange exchange, Chain chain) throws IOException;
    public abstract String description();

    public static class Chain {
        private final java.util.Iterator<Filter> filters;
        private final HttpHandler handler;
        public Chain(java.util.List<Filter> filters, HttpHandler handler) {
            this.filters = new java.util.ArrayList<>(filters).iterator();
            this.handler = java.util.Objects.requireNonNull(handler);
        }
        public void doFilter(HttpExchange exchange) throws IOException {
            if (filters.hasNext()) filters.next().doFilter(exchange, this); else handler.handle(exchange);
        }
    }
}
