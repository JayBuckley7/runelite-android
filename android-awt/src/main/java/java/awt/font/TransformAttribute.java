package java.awt.font;

import java.awt.geom.AffineTransform;
import java.io.Serializable;

public final class TransformAttribute implements Serializable {
    private static final long serialVersionUID = 3356247357827709530L;
    public static final TransformAttribute IDENTITY = new TransformAttribute(null);
    private final AffineTransform transform;
    public TransformAttribute(AffineTransform transform) { this.transform = transform == null ? new AffineTransform() : new AffineTransform(transform); }
    public AffineTransform getTransform() { return new AffineTransform(transform); }
    public boolean isIdentity() { return transform.isIdentity(); }
    public boolean equals(Object other) { return other instanceof TransformAttribute && transform.equals(((TransformAttribute)other).transform); }
    public int hashCode() { return transform.hashCode(); }
}
