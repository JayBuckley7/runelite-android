import javax.swing.Timer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TestAndroidTimers {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch repeated = new CountDownLatch(3);
        Timer timer = new Timer(25, e -> { calls.incrementAndGet(); repeated.countDown(); });
        timer.start();
        check(repeated.await(3, TimeUnit.SECONDS), "Repeating timer never fired");
        timer.stop();
        Thread.sleep(80);
        int stopped = calls.get();
        Thread.sleep(100);
        check(!timer.isRunning() && calls.get() == stopped, "Stopped timer still fires");
        CountDownLatch once = new CountDownLatch(1);
        AtomicInteger singleCalls = new AtomicInteger();
        Timer single = new Timer(25, e -> { singleCalls.incrementAndGet(); once.countDown(); });
        single.setRepeats(false);
        single.start();
        check(once.await(3, TimeUnit.SECONDS), "One-shot timer never fired");
        Thread.sleep(100);
        check(singleCalls.get() == 1 && !single.isRunning(), "One-shot timer repeated");
        single.restart();
        Thread.sleep(150);
        check(singleCalls.get() == 2 && !single.isRunning(), "One-shot restart failed");
        System.out.println("PASS: Android Swing repeating, stop, one-shot and restart timers");
    }
}
