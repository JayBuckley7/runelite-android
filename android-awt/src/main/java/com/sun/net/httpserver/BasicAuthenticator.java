package com.sun.net.httpserver;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public abstract class BasicAuthenticator extends Authenticator {
    protected final String realm;
    private final Charset charset;
    public BasicAuthenticator(String realm) { this(realm, StandardCharsets.ISO_8859_1); }
    public BasicAuthenticator(String realm, Charset charset) {
        if (realm == null || charset == null) throw new NullPointerException();
        if (realm.indexOf('"') >= 0 || realm.indexOf('\r') >= 0 || realm.indexOf('\n') >= 0) throw new IllegalArgumentException("Invalid realm");
        this.realm = realm; this.charset = charset;
    }
    public String getRealm() { return realm; }
    @Override public Result authenticate(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null) {
            challenge(exchange); return new Retry(401);
        }
        if (!authorization.regionMatches(true, 0, "Basic ", 0, 6)) return new Failure(401);
        try {
            String credentials = new String(Base64.getDecoder().decode(authorization.substring(6).trim()), charset);
            int colon = credentials.indexOf(':');
            if (colon >= 0 && checkCredentials(credentials.substring(0, colon), credentials.substring(colon + 1)))
                return new Success(new HttpPrincipal(credentials.substring(0, colon), realm));
        } catch (IllegalArgumentException invalid) { }
        challenge(exchange); return new Failure(401);
    }
    private void challenge(HttpExchange exchange) {
        exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"" + realm + "\"" + (charset.equals(StandardCharsets.UTF_8) ? ", charset=\"UTF-8\"" : ""));
    }
    public abstract boolean checkCredentials(String username, String password);
}
