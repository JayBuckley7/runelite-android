package javax.swing.event;

import java.util.EventObject;
import javax.swing.tree.TreePath;

public class TreeModelEvent extends EventObject {
    private static final long serialVersionUID = 5061611472566089751L;
    protected TreePath path;
    protected int[] childIndices;
    protected Object[] children;

    public TreeModelEvent(Object source, TreePath path) {
        super(source);
        this.path = path;
    }

    public TreeModelEvent(Object source, TreePath path, int[] indices, Object[] children) {
        super(source);
        this.path = path; this.childIndices = indices; this.children = children;
    }

    public TreeModelEvent(Object source, Object[] path) {
        super(source);
        this.path = path == null ? null : new TreePath(path);
    }

    public TreeModelEvent(Object source, Object[] path, int[] indices, Object[] children) {
        this(source, path == null ? null : new TreePath(path), indices, children);
    }
    public Object[] getPath() { return path == null ? null : path.getPath(); }
    public TreePath getTreePath() { return path; }
    public int[] getChildIndices() { return childIndices == null ? null : childIndices.clone(); }
    public Object[] getChildren() { return children == null ? null : children.clone(); }
}
