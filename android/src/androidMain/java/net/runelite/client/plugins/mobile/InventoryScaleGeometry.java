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
	private InventoryScaleGeometry(Rectangle source, Rectangle destination, int width, int height)
	{
		this.source = new Rectangle(source);
		this.destination = new Rectangle(destination);
		this.width = width;
		this.height = height;
	}

	/** Pack the modern inventory and its two independently positioned tab rows as one panel.
	 * Only the actual widget rectangles are sampled, never the empty space between them. */
	public static InventoryScaleGeometry[] modernGroup(Rectangle body, Rectangle first, Rectangle second,
		int percent, int width, int height)
	{
		Rectangle[] sources = {body, first, second};
		boolean sameRow = first != null && second != null && Math.abs(first.y - second.y) < Math.min(first.height, second.height) / 2;
		Rectangle[] slots = new Rectangle[3];
		int tabWidth = sameRow ? first.width + second.width : Math.max(first == null ? 0 : first.width, second == null ? 0 : second.width);
		int groupWidth = Math.max(body.width, tabWidth);
		slots[0] = new Rectangle(groupWidth - body.width, 0, body.width, body.height);
		int bottom = body.height;
		// Keep the game's left-to-right or top-to-bottom tab order in both responsive layouts.
		int a = first != null && second != null && (sameRow ? first.x > second.x : first.y > second.y) ? 2 : 1;
		int b = 3 - a;
		int x = groupWidth - tabWidth;
		for (int id : new int[]{a, b})
		{
			Rectangle source = sources[id];
			if (source == null) continue;
			slots[id] = new Rectangle(sameRow ? x : groupWidth - source.width, bottom, source.width, source.height);
			if (sameRow) x += source.width;
			else bottom += source.height;
		}
		if (sameRow) bottom += Math.max(first.height, second.height);
		Rectangle logical = new Rectangle(body.x + body.width - groupWidth, body.y + body.height - bottom, groupWidth, bottom);
		InventoryScaleGeometry group = new InventoryScaleGeometry(logical, percent, width, height);
		InventoryScaleGeometry[] result = new InventoryScaleGeometry[3];
		for (int id = 0; id < 3; id++)
		{
			Rectangle slot = slots[id];
			if (slot == null) continue;
			int left = group.destination.x + slot.x * group.destination.width / groupWidth;
			int top = group.destination.y + slot.y * group.destination.height / bottom;
			int right = group.destination.x + (slot.x + slot.width) * group.destination.width / groupWidth;
			int end = group.destination.y + (slot.y + slot.height) * group.destination.height / bottom;
			result[id] = new InventoryScaleGeometry(sources[id], new Rectangle(left, top, right - left, end - top), width, height);
		}
		return result;
	}
	public int sourceX(int x) { return source.x + (x - destination.x) * source.width / destination.width; }
	public int sourceY(int y) { return source.y + (y - destination.y) * source.height / destination.height; }
	public void composite(int[] pixels, int[] scratch)
	{
		capture(pixels, scratch);
		clearSource(pixels);
		paint(pixels, scratch);
	}
	public void capture(int[] pixels, int[] scratch)
	{
		for (int y = 0; y < source.height; y++)
			System.arraycopy(pixels, (source.y + y) * width + source.x, scratch, y * source.width, source.width);
	}
	public void clearSource(int[] pixels)
	{
		for (int y = source.y; y < source.y + source.height; y++)
			java.util.Arrays.fill(pixels, y * width + source.x, y * width + source.x + source.width, 0);
	}
	public void paint(int[] pixels, int[] scratch)
	{
		for (int y = destination.y; y < destination.y + destination.height; y++)
			for (int x = destination.x; x < destination.x + destination.width; x++)
				pixels[y * width + x] = scratch[(sourceY(y) - source.y) * source.width + sourceX(x) - source.x];
	}
}
