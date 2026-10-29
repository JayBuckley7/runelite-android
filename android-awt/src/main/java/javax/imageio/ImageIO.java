package javax.imageio;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

/**
 * Subset of {@code javax.imageio.ImageIO} backed by {@link android.graphics.BitmapFactory}.
 * Decodes PNG/JPEG/WEBP/etc. into a fresh {@link BufferedImage} of TYPE_INT_ARGB. The
 * Bitmap is recycled before we return — the caller owns the int[] in the BufferedImage.
 */
public final class ImageIO {
    private ImageIO() {
    }

    public static void setUseCache(boolean useCache) {
        // BitmapFactory doesn't expose a disk-cache toggle; no-op.
    }

    public static boolean getUseCache() {
        return false;
    }

    public static BufferedImage read(InputStream input) throws IOException {
        if (input == null) throw new IllegalArgumentException("null InputStream");
        Bitmap bitmap = BitmapFactory.decodeStream(input);
        return bitmapToBufferedImage(bitmap);
    }

    public static BufferedImage read(File input) throws IOException {
        if (input == null) throw new IllegalArgumentException("null File");
        if (!input.isFile() || !input.canRead()) throw new IOException("Cannot read image file: " + input);
        Bitmap bitmap = BitmapFactory.decodeFile(input.getAbsolutePath());
        return bitmapToBufferedImage(bitmap);
    }

    public static BufferedImage read(URL input) throws IOException {
        if (input == null) throw new IllegalArgumentException("null URL");
        try (InputStream is = input.openStream()) {
            return read(is);
        }
    }

    public static boolean write(java.awt.image.RenderedImage im, String formatName, OutputStream output) throws IOException {
        if (output == null || im == null) throw new IllegalArgumentException("Image and output are required");
        Bitmap.CompressFormat fmt = formatFor(formatName);
        if(fmt==null)return false;
        int width=im.getWidth(),height=im.getHeight();
        int[] pixels;
        if(im instanceof BufferedImage) pixels=((BufferedImage)im).getRGB(0,0,width,height,null,0,width);
        else {
            pixels=new int[width*height];java.awt.image.Raster raster=im.getData();java.awt.image.ColorModel model=im.getColorModel();Object element=null;
            for(int y=0;y<height;y++)for(int x=0;x<width;x++){
                element=raster.getDataElements(im.getMinX()+x,im.getMinY()+y,element);pixels[y*width+x]=model.getRGB(element);
            }
        }
        Bitmap bmp=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        bmp.setPixels(pixels,0,width,0,0,width,height);
        try {
            return bmp.compress(fmt, 100, output);
        } finally {
            bmp.recycle();
        }
    }

    public static boolean write(java.awt.image.RenderedImage im, String formatName, File output) throws IOException {
        if(formatFor(formatName)==null)return false;
        try (java.io.FileOutputStream os = new java.io.FileOutputStream(output)) {
            return write(im, formatName, os);
        }
    }

    private static BufferedImage bitmapToBufferedImage(Bitmap bitmap) throws IOException {
        if (bitmap == null) return null;
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        bitmap.getPixels(out.backingArray(), 0, w, 0, 0, w, h);
        bitmap.recycle();
        return out;
    }

    private static Bitmap.CompressFormat formatFor(String name) {
        if (name == null) throw new IllegalArgumentException("formatName");
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (n.equals("jpeg") || n.equals("jpg")) return Bitmap.CompressFormat.JPEG;
        if (n.equals("webp")) {
            // WEBP_LOSSLESS was split out of the deprecated WEBP enum in API 30. Our minSdk
            // is 26, so gate the newer constant on Build.VERSION and fall back to plain WEBP
            // (still lossless mode in practice) on older devices.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                return Bitmap.CompressFormat.WEBP_LOSSLESS;
            }
            @SuppressWarnings("deprecation")
            Bitmap.CompressFormat legacy = Bitmap.CompressFormat.WEBP;
            return legacy;
        }
        return n.equals("png")?Bitmap.CompressFormat.PNG:null;
    }
}
