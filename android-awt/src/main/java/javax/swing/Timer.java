package javax.swing;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.Serializable;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.event.EventListenerList;

/** Schedules wakeups off-thread; all listener calls run on the Swing EDT. */
public class Timer implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final ScheduledExecutorService EXEC = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "AWT-Timer"); t.setDaemon(true); return t;
    });
    private final EventListenerList listeners = new EventListenerList();
    private int delay, initialDelay;
    private boolean repeats = true, coalesce = true;
    private volatile boolean running;
    private String actionCommand;
    private transient ScheduledFuture<?> task;
    private long generation;
    private final AtomicBoolean pending = new AtomicBoolean();
    private static boolean logTimers;
    public Timer(int delay, ActionListener listener) { checkDelay(delay); this.delay=initialDelay=delay; addActionListener(listener); }
    private static void checkDelay(int value) { if (value < 0) throw new IllegalArgumentException("negative delay"); }
    public void addActionListener(ActionListener l) { listeners.add(ActionListener.class,l); }
    public void removeActionListener(ActionListener l) { listeners.remove(ActionListener.class,l); }
    public ActionListener[] getActionListeners() { return listeners.getListeners(ActionListener.class); }
    public <T extends java.util.EventListener> T[] getListeners(Class<T> type) { return listeners.getListeners(type); }
    public int getDelay() { return delay; }
    public synchronized void setDelay(int value) { checkDelay(value); delay=value; }
    public int getInitialDelay() { return initialDelay; }
    public void setInitialDelay(int value) { checkDelay(value); initialDelay=value; }
    public boolean isRepeats() { return repeats; }
    public synchronized void setRepeats(boolean value) { repeats=value; }
    public boolean isCoalesce() { return coalesce; }
    public void setCoalesce(boolean value) { coalesce=value; }
    public String getActionCommand() { return actionCommand; }
    public void setActionCommand(String value) { actionCommand=value; }
    public synchronized void start() { if (!running) { running=true; schedule(++generation,initialDelay); } }
    private void schedule(long token,int wait) { task=EXEC.schedule(() -> wake(token),wait,TimeUnit.MILLISECONDS); }
    private synchronized void wake(long token) {
        if (!running || token!=generation) return;
        if (!coalesce || pending.compareAndSet(false,true)) SwingUtilities.invokeLater(() -> {
            synchronized (Timer.this) { if (token!=generation) return; }
            try { fireActionPerformed(new ActionEvent(this,ActionEvent.ACTION_PERFORMED,actionCommand,System.currentTimeMillis(),0)); }
            finally { pending.set(false); }
        });
        if (repeats) schedule(token,Math.max(1,delay)); else running=false;
    }
    public synchronized void stop() { running=false; generation++; pending.set(false); if(task!=null){task.cancel(false);task=null;} }
    public synchronized void restart() { stop();start(); }
    public boolean isRunning() { return running; }
    public static void setLogTimers(boolean value) { logTimers=value; }
    public static boolean getLogTimers() { return logTimers; }
    protected void fireActionPerformed(ActionEvent e) { for(ActionListener l:getActionListeners()) l.actionPerformed(e); }
}
