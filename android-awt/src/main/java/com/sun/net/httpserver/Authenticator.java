package com.sun.net.httpserver;

public abstract class Authenticator {
    public abstract Result authenticate(HttpExchange exch);

    public abstract static class Result {}
    public static class Success extends Result {
        private final HttpPrincipal principal;
        public Success(HttpPrincipal principal) { this.principal = java.util.Objects.requireNonNull(principal); }
        public HttpPrincipal getPrincipal() { return principal; }
    }
    public static class Retry extends Result {
        private final int responseCode;
        public Retry(int responseCode) { this.responseCode = responseCode; }
        public int getResponseCode() { return responseCode; }
    }
    public static class Failure extends Result {
        private final int responseCode;
        public Failure(int responseCode) { this.responseCode = responseCode; }
        public int getResponseCode() { return responseCode; }
    }
}
