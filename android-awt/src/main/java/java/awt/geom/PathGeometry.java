package java.awt.geom;
import java.awt.Shape;
/** Shared winding and intersection tests over adaptively flattened curves. */
final class PathGeometry {
    private interface Edge{void accept(double x1,double y1,double x2,double y2);}
    private static void edges(PathIterator source,Edge edge){
        PathIterator it=new FlatteningPathIterator(source,0.001,16);double[]c=new double[6];double x=0,y=0,sx=0,sy=0;boolean started=false;
        while(!it.isDone()){
            int t=it.currentSegment(c);
            if(t==PathIterator.SEG_MOVETO){if(started)edge.accept(x,y,sx,sy);x=sx=c[0];y=sy=c[1];started=true;}
            else if(t==PathIterator.SEG_LINETO){edge.accept(x,y,c[0],c[1]);x=c[0];y=c[1];}
            else if(t==PathIterator.SEG_CLOSE){edge.accept(x,y,sx,sy);x=sx;y=sy;}
            it.next();
        }
        if(started)edge.accept(x,y,sx,sy);
    }
    static boolean contains(PathIterator it,double x,double y){
        if(!Double.isFinite(x)||!Double.isFinite(y))return false;
        int rule=it.getWindingRule();final int[]winding={0};
        edges(it,(a,b,c,d)->{if((b<=y&&d>y)||(d<=y&&b>y)){if(a+(y-b)*(c-a)/(d-b)>x)winding[0]+=d>b?1:-1;}});
        return rule==PathIterator.WIND_EVEN_ODD?(winding[0]&1)!=0:winding[0]!=0;
    }
    private static boolean edgeInside(double a,double b,double c,double d,double x,double y,double w,double h){
        double lo=0,hi=1,dx=c-a,dy=d-b;double[]p={-dx,dx,-dy,dy},q={a-x,x+w-a,b-y,y+h-b};
        for(int i=0;i<4;i++){if(p[i]==0){if(q[i]<=0)return false;}else{double t=q[i]/p[i];if(p[i]<0)lo=Math.max(lo,t);else hi=Math.min(hi,t);if(lo>=hi)return false;}}
        double m=(lo+hi)/2,px=a+m*dx,py=b+m*dy;return px>x&&px<x+w&&py>y&&py<y+h;
    }
    static boolean rectangle(Shape shape,double x,double y,double w,double h,boolean containing){
        if(w<=0||h<=0||!shape.getBounds2D().intersects(x,y,w,h))return false;
        boolean a=shape.contains(x,y),b=shape.contains(x+w,y),c=shape.contains(x,y+h),d=shape.contains(x+w,y+h);
        if(containing&&!(a&&b&&c&&d))return false;
        if(!containing&&(a||b||c||d))return true;
        final boolean[]crossing={false};edges(shape.getPathIterator(null),(ax,ay,bx,by)->{if(edgeInside(ax,ay,bx,by,x,y,w,h))crossing[0]=true;});return containing?!crossing[0]:crossing[0];
    }
}
