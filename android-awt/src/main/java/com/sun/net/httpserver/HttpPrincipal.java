package com.sun.net.httpserver;

public class HttpPrincipal implements java.security.Principal {
    private final String username, realm;
    public HttpPrincipal(String username, String realm) {
        this.username = java.util.Objects.requireNonNull(username);
        this.realm = java.util.Objects.requireNonNull(realm);
    }
    public String getUsername() { return username; }
    public String getRealm() { return realm; }
    public String getName() { return realm + ":" + username; }
    public String toString() { return getName(); }
    public boolean equals(Object other) { return other instanceof HttpPrincipal && username.equals(((HttpPrincipal) other).username) && realm.equals(((HttpPrincipal) other).realm); }
    public int hashCode() { return getName().hashCode(); }
}
