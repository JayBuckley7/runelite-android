package java.awt.font;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
public class FontRenderContext {
    private final AffineTransform transform;
    private final Object antialias,fractional;
    protected FontRenderContext(){this(null,false,false);}
    public FontRenderContext(AffineTransform transform,boolean aa,boolean fractional){this(transform,aa?RenderingHints.VALUE_TEXT_ANTIALIAS_ON:RenderingHints.VALUE_TEXT_ANTIALIAS_OFF,fractional?RenderingHints.VALUE_FRACTIONALMETRICS_ON:RenderingHints.VALUE_FRACTIONALMETRICS_OFF);}
    public FontRenderContext(AffineTransform transform,Object aa,Object fractional){this.transform=transform==null?new AffineTransform():new AffineTransform(transform);this.antialias=aa;this.fractional=fractional;}
    public AffineTransform getTransform(){return new AffineTransform(transform);}
    public boolean isAntiAliased(){return antialias!=RenderingHints.VALUE_TEXT_ANTIALIAS_OFF && antialias!=RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT;}
    public boolean usesFractionalMetrics(){return fractional==RenderingHints.VALUE_FRACTIONALMETRICS_ON;}
    public boolean isTransformed(){return !transform.isIdentity();}
    public int getTransformType(){return transform.getType();}
    public Object getAntiAliasingHint(){return antialias;}
    public Object getFractionalMetricsHint(){return fractional;}
    public boolean equals(FontRenderContext other){return other!=null&&transform.equals(other.transform)&&antialias.equals(other.antialias)&&fractional.equals(other.fractional);}
    public boolean equals(Object other){return other instanceof FontRenderContext&&equals((FontRenderContext)other);}
    public int hashCode(){return transform.hashCode()^antialias.hashCode()^fractional.hashCode();}
}
