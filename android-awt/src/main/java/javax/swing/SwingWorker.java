package javax.swing;
import java.util.*;
import java.util.concurrent.*;
import java.beans.*;
/** Background work with completion, publication and state notifications on the EDT. */
public abstract class SwingWorker<T,V> implements RunnableFuture<T> {
    public enum StateValue { PENDING,STARTED,DONE }
    private static final ExecutorService EXECUTOR=Executors.newFixedThreadPool(10,r->{Thread thread=new Thread(r,"SwingWorker");thread.setDaemon(true);return thread;});
    private final PropertyChangeSupport changes=new PropertyChangeSupport(this);
    private volatile StateValue state=StateValue.PENDING;
    private volatile int progress;
    private final java.util.List<V> chunks=new ArrayList<>();
    private boolean publishPending;
    private final FutureTask<T> future=new FutureTask<T>(()->{setState(StateValue.STARTED);return doInBackground();}) {
        protected void done(){SwingUtilities.invokeLater(()->{try{SwingWorker.this.done();}finally{setState(StateValue.DONE);}});}
    };
    protected abstract T doInBackground() throws Exception;
    protected void process(java.util.List<V> values){}
    protected void done(){}
    protected final void publish(V... values){synchronized(chunks){Collections.addAll(chunks,values);if(publishPending)return;publishPending=true;}SwingUtilities.invokeLater(()->{java.util.List<V> valuesToProcess;synchronized(chunks){valuesToProcess=new ArrayList<>(chunks);chunks.clear();publishPending=false;}process(valuesToProcess);});}
    protected final void setProgress(int value){if(value<0||value>100)throw new IllegalArgumentException("progress");int old=progress;progress=value;firePropertyChange("progress",old,value);}
    public final int getProgress(){return progress;}
    public final StateValue getState(){return state;}
    private void setState(StateValue value){StateValue old=state;state=value;firePropertyChange("state",old,value);}
    public final void firePropertyChange(String name,Object old,Object value){if(SwingUtilities.isEventDispatchThread())changes.firePropertyChange(name,old,value);else SwingUtilities.invokeLater(()->changes.firePropertyChange(name,old,value));}
    public final void addPropertyChangeListener(PropertyChangeListener listener){changes.addPropertyChangeListener(listener);}
    public final void removePropertyChangeListener(PropertyChangeListener listener){changes.removePropertyChangeListener(listener);}
    public final PropertyChangeSupport getPropertyChangeSupport(){return changes;}
    public final void execute(){EXECUTOR.execute(this);}
    public final void run(){future.run();}
    public final boolean cancel(boolean interrupt){return future.cancel(interrupt);}
    public final boolean isCancelled(){return future.isCancelled();}
    public final boolean isDone(){return future.isDone();}
    public final T get()throws InterruptedException,ExecutionException{return future.get();}
    public final T get(long timeout,TimeUnit unit)throws InterruptedException,ExecutionException,TimeoutException{return future.get(timeout,unit);}
}
