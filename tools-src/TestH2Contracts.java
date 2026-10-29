import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.h2.jdbcx.JdbcDataSource;

/** Exercise the actual H2 engine and an unresponsive TCP peer, not a mocked driver. */
public final class TestH2Contracts {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setLoginTimeout(1);
        source.setURL("jdbc:h2:mem:compat_timeout");
        try (Connection connection = source.getConnection();
             ResultSet result = connection.createStatement().executeQuery("select 42")) {
            check(result.next() && result.getInt(1) == 42, "ordinary embedded H2 connection");
        }
        try {
            source.setLoginTimeout(-1);
            throw new AssertionError("negative timeout accepted");
        } catch (IllegalArgumentException expected) { }
        int globalTimeout = DriverManager.getLoginTimeout();
        CountDownLatch accepted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ServerSocket stalled = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            Thread peer = new Thread(() -> {
                try (Socket socket = stalled.accept()) {
                    accepted.countDown();
                    release.await(10, TimeUnit.SECONDS);
                } catch (Exception ignored) { }
            }, "H2-stalled-peer");
            peer.setDaemon(true);
            peer.start();
            source.setURL("jdbc:h2:tcp://localhost:" + stalled.getLocalPort() + "/mem:compat_timeout");
            long start = System.nanoTime();
            try {
                source.getConnection();
                throw new AssertionError("stalled H2 connection completed");
            } catch (SQLTimeoutException expected) {
                long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                check(elapsed >= 800 && elapsed < 3500, "bounded login timeout: " + elapsed);
                check(accepted.getCount() == 0, "real TCP connection was attempted");
            } finally {
                release.countDown();
                peer.join(2000);
            }
        }
        check(DriverManager.getLoginTimeout() == globalTimeout, "no global JDBC timeout mutation");
        source.setURL("jdbc:h2:mem:compat_after_timeout");
        try (Connection connection = source.getConnection()) {
            check(connection.isValid(1), "connection succeeds after timeout");
        }
        System.out.println("PASS: real H2 embedded connection, stalled TCP timeout, isolation and recovery");
    }
}
