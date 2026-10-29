package java.awt.geom;
import java.awt.Shape;
/** The float-precision Path2D variant used by desktop callers. */
public final class GeneralPath extends Path2D.Float {
    private static final long serialVersionUID=-8327096662768731142L;
    public GeneralPath(){super();}
    public GeneralPath(int rule){super(rule);}
    public GeneralPath(int rule,int capacity){super(rule,capacity);}
    public GeneralPath(Shape shape){super(shape);}
}
