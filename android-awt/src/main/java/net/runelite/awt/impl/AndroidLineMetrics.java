package net.runelite.awt.impl;
public final class AndroidLineMetrics extends java.awt.font.LineMetrics {
    private final android.graphics.Paint.FontMetrics metrics;private final int count;private final float size;
    public AndroidLineMetrics(java.awt.Font font,int count){metrics=BufferedImageFontMetrics.paintFor(font).getFontMetrics();this.count=count;size=font.getSize2D();}
    public int getNumChars(){return count;}public float getAscent(){return -metrics.ascent;}public float getDescent(){return metrics.descent;}public float getLeading(){return metrics.leading;}public float getHeight(){return getAscent()+getDescent()+getLeading();}
    public int getBaselineIndex(){return 0;}public float[] getBaselineOffsets(){return new float[]{0,(metrics.ascent+metrics.descent)/2,metrics.ascent};}
    public float getStrikethroughOffset(){return -size*0.3f;}public float getStrikethroughThickness(){return Math.max(1,size/16);}
    public float getUnderlineOffset(){return metrics.descent/2;}public float getUnderlineThickness(){return Math.max(1,size/16);}
}
