package java.lang;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Stub of {@code java.lang.ProcessHandle} (JDK 9+). Android lacks the class; RuneLite
 * uses it for {@code current().pid()} logging — we return {@link android.os.Process#myPid()}.
 */
public interface ProcessHandle extends Comparable<ProcessHandle> {
    static ProcessHandle current() {
        return CurrentHandle.INSTANCE;
    }

    static Optional<ProcessHandle> of(long pid) {
        if (pid <= 0) throw new IllegalArgumentException("pid must be positive");
        if (pid == current().pid()) return Optional.of(current());
        return new java.io.File("/proc/" + pid).canRead() ? Optional.of(new PidHandle(pid)) : Optional.empty();
    }

    static Stream<ProcessHandle> allProcesses() {
        java.io.File[] entries = new java.io.File("/proc").listFiles();
        if (entries == null) return Stream.of(current());
        return java.util.Arrays.stream(entries).filter(f -> f.getName().matches("[0-9]+") && f.canRead())
            .map(f -> (ProcessHandle) new PidHandle(Long.parseLong(f.getName())));
    }

    long pid();

    default Optional<ProcessHandle> parent() {
        try {
            for (String line : java.nio.file.Files.readAllLines(java.nio.file.Paths.get("/proc/" + pid() + "/status")))
                if (line.startsWith("PPid:")) { long parent = Long.parseLong(line.substring(5).trim()); return parent > 0 ? of(parent) : Optional.empty(); }
        } catch (Exception unavailable) { }
        return Optional.empty();
    }
    default Stream<ProcessHandle> children() { return allProcesses().filter(p -> p.parent().map(parent -> parent.pid() == pid()).orElse(false)); }
    default Stream<ProcessHandle> descendants() { return children().flatMap(child -> Stream.concat(Stream.of(child), child.descendants())); }
    default Info info() { return new StubInfo(); }
    default boolean isAlive() { return pid() == android.os.Process.myPid() || new java.io.File("/proc/" + pid()).exists(); }
    @Override default int compareTo(ProcessHandle other) { return Long.compare(pid(), other.pid()); }
    default boolean supportsNormalTermination() { return false; }
    default boolean destroy() { return false; }
    default boolean destroyForcibly() { return false; }
    default java.util.concurrent.CompletableFuture<ProcessHandle> onExit() {
        if (pid() == current().pid()) throw new IllegalStateException("Cannot wait for the current process to exit");
        java.util.concurrent.CompletableFuture<ProcessHandle> result = new java.util.concurrent.CompletableFuture<>();
        Thread waiter = new Thread(() -> {
            try {
                while (isAlive() && !result.isCancelled()) Thread.sleep(100);
                result.complete(this);
            } catch (InterruptedException interrupted) { result.completeExceptionally(interrupted); Thread.currentThread().interrupt(); }
        }, "ProcessHandle-wait");
        waiter.setDaemon(true); waiter.start(); return result;
    }

    interface Info {
        Optional<String> command();
        Optional<String> commandLine();
        Optional<String[]> arguments();
        Optional<java.time.Instant> startInstant();
        Optional<java.time.Duration> totalCpuDuration();
        Optional<String> user();
    }

    final class CurrentHandle implements ProcessHandle {
        static final CurrentHandle INSTANCE = new CurrentHandle();
        @Override public long pid() {
            return android.os.Process.myPid();
        }
    }

    final class PidHandle implements ProcessHandle {
        private final long id;
        PidHandle(long id) { this.id = id; }
        @Override public long pid() { return id; }
        @Override public boolean equals(Object other) { return other instanceof ProcessHandle && ((ProcessHandle) other).pid() == id; }
        @Override public int hashCode() { return Long.hashCode(id); }
    }

    final class StubInfo implements Info {
        @Override public Optional<String> command() { return Optional.empty(); }
        @Override public Optional<String> commandLine() { return Optional.empty(); }
        @Override public Optional<String[]> arguments() { return Optional.empty(); }
        @Override public Optional<java.time.Instant> startInstant() { return Optional.empty(); }
        @Override public Optional<java.time.Duration> totalCpuDuration() { return Optional.empty(); }
        @Override public Optional<String> user() { return Optional.empty(); }
    }
}
