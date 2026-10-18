package net.runelite.client.plugins.mobile;

import java.awt.Rectangle;

/** One immutable transform is shared by the pixel compositor and input inverse. */
public final class InventoryScaleGeometry
{
	public final Rectangle source, destination;
	public final int width, height;
	public InventoryScaleGeometry(Rectangle bounds, int percent, int width, int height)
	{
		this.width = width;
		this.height = height;
		source = new Rectangle(bounds);
		double scale = Math.min(Math.max(100, Math.min(200, percent)) / 100.0,
			Math.min((double) width / bounds.width, (double) height / bounds.height));
		int w = (int) Math.round(bounds.width * scale), h = (int) Math.round(bounds.height * scale);
		destination = new Rectangle(Math.max(0, Math.min(width - w, bounds.x + bounds.width - w)),
			Math.max(0, Math.min(height - h, bounds.y + bounds.height - h)), w, h);
	}
	public int sourceX(int x) { return source.x + (x - destination.x) * source.width / destination.width; }
	public int sourceY(int y) { return source.y + (y - destination.y) * source.height / destination.height; }
	public void composite(int[] pixels, int[] scratch)
	{
		for (int y = 0; y < source.height; y++)
			System.arraycopy(pixels, (source.y + y) * width + source.x, scratch, y * source.width, source.width);
		for (int y = source.y; y < source.y + source.height; y++)
			java.util.Arrays.fill(pixels, y * width + source.x, y * width + source.x + source.width, 0);
		for (int y = destination.y; y < destination.y + destination.height; y++)
			for (int x = destination.x; x < destination.x + destination.width; x++)
				pixels[y * width + x] = scratch[(sourceY(y) - source.y) * source.width + sourceX(x) - source.x];
	}
}
