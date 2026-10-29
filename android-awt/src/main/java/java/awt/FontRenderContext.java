package java.awt;
/** Retained name for older shim callers. Desktop APIs use java.awt.font.FontRenderContext. */
@Deprecated
public class FontRenderContext extends java.awt.font.FontRenderContext {
    public FontRenderContext(){super(null,false,false);}
    public FontRenderContext(java.awt.geom.AffineTransform transform,boolean aa,boolean fractional){super(transform,aa,fractional);}
}
