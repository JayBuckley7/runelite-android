package org.h2.jdbcx;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.h2.jdbc.JdbcConnection;

/**
 * Android entry point for H2's ordinary JDBC connections. Hub plugins such as
 * Polywoof use this name directly. The SQL engine and on-disk format stay H2's;
 * desktop JNDI, connection pooling and XA transactions are not implemented here.
 */
public final class JdbcDataSource implements DataSource {
    private String url = "";
    private String user = "";
    private char[] password = new char[0];
    private String description;
    private PrintWriter logWriter;
    private int loginTimeout;
    private static final java.util.concurrent.ThreadPoolExecutor CONNECTIONS = new java.util.concurrent.ThreadPoolExecutor(
        4, 4, 30, java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<Runnable>(16), r -> {
            Thread thread = new Thread(r, "H2-connection"); thread.setDaemon(true); return thread;
        });
    static { CONNECTIONS.allowCoreThreadTimeOut(true); }

    public String getURL() { return url; }
    public void setURL(String value) { url = value; }
    public String getUrl() { return getURL(); }
    public void setUrl(String value) { setURL(value); }
    public String getUser() { return user; }
    public void setUser(String value) { user = value; }
    public String getPassword() { return password == null ? null : new String(password); }
    public void setPassword(String value) { password = value == null ? null : value.toCharArray(); }
    public void setPasswordChars(char[] value) { password = value == null ? null : value.clone(); }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }

    @Override public Connection getConnection() throws SQLException {
        return connect(user, password == null ? null : password.clone());
    }
    @Override public Connection getConnection(String username, String pass) throws SQLException {
        return connect(username, pass);
    }
    private Connection connect(String username, Object passwordValue) throws SQLException {
        final String connectionUrl = url;
        int timeout = loginTimeout;
        if (timeout == 0) return new JdbcConnection(connectionUrl, null, username, passwordValue, false);
        java.util.concurrent.CompletableFuture<Connection> result = new java.util.concurrent.CompletableFuture<>();
        try { CONNECTIONS.execute(() -> {
            if (result.isDone()) return;
            try {
                Connection connection = new JdbcConnection(connectionUrl, null, username, passwordValue, false);
                if (!result.complete(connection)) connection.close();
            } catch (Throwable failure) { result.completeExceptionally(failure); }
        }); } catch (java.util.concurrent.RejectedExecutionException busy) { throw new SQLException("Too many pending H2 connections", busy); }
        try { return result.get(timeout, java.util.concurrent.TimeUnit.SECONDS); }
        catch (java.util.concurrent.TimeoutException e) {
            java.sql.SQLTimeoutException failure = new java.sql.SQLTimeoutException("H2 connection timed out", e);
            if (!result.completeExceptionally(failure)) {
                try { return result.get(); }
                catch (java.util.concurrent.ExecutionException completed) { throw connectionFailure(completed.getCause()); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new SQLException("Connection interrupted", interrupted); }
            }
            throw failure;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            SQLException failure = new SQLException("Connection interrupted", e);
            if (!result.completeExceptionally(failure)) result.thenAccept(connection -> { try { connection.close(); } catch (SQLException ignored) {} });
            throw failure;
        } catch (java.util.concurrent.ExecutionException e) { throw connectionFailure(e.getCause()); }
    }
    private static SQLException connectionFailure(Throwable failure) {
        return failure instanceof SQLException ? (SQLException) failure : new SQLException("H2 connection failed", failure);
    }
    @Override public PrintWriter getLogWriter() { return logWriter; }
    @Override public void setLogWriter(PrintWriter writer) { logWriter = writer; }
    @Override public int getLoginTimeout() { return loginTimeout; }
    @Override public void setLoginTimeout(int seconds) { if (seconds < 0) throw new IllegalArgumentException("negative timeout"); loginTimeout = seconds; }
    @Override public Logger getParentLogger() { return Logger.getLogger("org.h2"); }
    @Override public boolean isWrapperFor(Class<?> type) { return type != null && type.isInstance(this); }
    @Override public <T> T unwrap(Class<T> type) throws SQLException {
        if (isWrapperFor(type)) return type.cast(this);
        throw new SQLException("Not a wrapper for " + type);
    }
}
