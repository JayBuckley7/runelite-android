package javax.swing.undo;

import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;

/** Keeps a reversible edit history, including insignificant edits between user actions. */
public class UndoManager extends CompoundEdit implements UndoableEditListener {
    private static final long serialVersionUID = 1L;
    private int indexOfNextAdd;
    private int limit = 100;
    @Override public void undoableEditHappened(UndoableEditEvent e) { addEdit(e.getEdit()); }
    @Override public synchronized boolean addEdit(UndoableEdit edit) {
        trimEdits(indexOfNextAdd, edits.size()-1);
        boolean accepted=super.addEdit(edit);
        if (isInProgress()) accepted=true;
        indexOfNextAdd=edits.size();trimForLimit();return accepted;
    }
    protected void trimEdits(int from,int to) {
        if(from>to)return;
        for(int i=to;i>=from;i--){edits.get(i).die();edits.remove(i);}
        if(indexOfNextAdd>to)indexOfNextAdd-=to-from+1;
        else if(indexOfNextAdd>=from)indexOfNextAdd=from;
    }
    protected void trimForLimit() {
        if(limit<0 || edits.size()<=limit)return;
        int start=indexOfNextAdd-1-limit/2;
        int end=start+limit-1;
        if(start<0){end-=start;start=0;}
        if(end>=edits.size()){start-=end-edits.size()+1;end=edits.size()-1;}
        trimEdits(end+1,edits.size()-1);trimEdits(0,start-1);
    }
    protected UndoableEdit editToBeUndone() { for(int i=indexOfNextAdd-1;i>=0;i--) if(edits.get(i).isSignificant())return edits.get(i);return null; }
    protected UndoableEdit editToBeRedone() { for(int i=indexOfNextAdd;i<edits.size();i++)if(edits.get(i).isSignificant())return edits.get(i);return null; }
    protected void undoTo(UndoableEdit edit) { UndoableEdit current;do{current=edits.get(--indexOfNextAdd);current.undo();}while(current!=edit); }
    protected void redoTo(UndoableEdit edit) { UndoableEdit current;do{current=edits.get(indexOfNextAdd++);current.redo();}while(current!=edit); }
    @Override public synchronized void undo() { if(!isInProgress()){super.undo();return;} UndoableEdit edit=editToBeUndone();if(edit==null)throw new CannotUndoException();undoTo(edit); }
    @Override public synchronized void redo() { if(!isInProgress()){super.redo();return;} UndoableEdit edit=editToBeRedone();if(edit==null)throw new CannotRedoException();redoTo(edit); }
    @Override public synchronized boolean canUndo() { UndoableEdit edit=editToBeUndone();return isInProgress()?edit!=null&&edit.canUndo():super.canUndo(); }
    @Override public synchronized boolean canRedo() { UndoableEdit edit=editToBeRedone();return isInProgress()?edit!=null&&edit.canRedo():super.canRedo(); }
    public synchronized boolean canUndoOrRedo() { return indexOfNextAdd==edits.size()?canUndo():canRedo(); }
    public synchronized void undoOrRedo() { if(indexOfNextAdd==edits.size())undo();else redo(); }
    public synchronized String getUndoOrRedoPresentationName() { return indexOfNextAdd==edits.size()?getUndoPresentationName():getRedoPresentationName(); }
    @Override public synchronized String getUndoPresentationName() { UndoableEdit edit=editToBeUndone();return edit==null?"Undo":edit.getUndoPresentationName(); }
    @Override public synchronized String getRedoPresentationName() { UndoableEdit edit=editToBeRedone();return edit==null?"Redo":edit.getRedoPresentationName(); }
    public synchronized void discardAllEdits() { trimEdits(0,edits.size()-1);indexOfNextAdd=0; }
    @Override public synchronized void end() { super.end();trimEdits(indexOfNextAdd,edits.size()-1); }
    public synchronized void setLimit(int value) { if(!isInProgress())throw new RuntimeException("edit already ended");limit=value;trimForLimit(); }
    public synchronized int getLimit() { return limit; }
}
