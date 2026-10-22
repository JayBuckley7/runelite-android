package net.runelite.client.plugins.mobile;

import java.awt.Rectangle;
import java.util.function.IntConsumer;

/** Immutable scrollbar geometry published by the client thread for touch taps. */
public final class MobileScrollbars
{
    private static volatile Target[] targets = new Target[0];

    private MobileScrollbars() {}

    static final class Target
    {
        final Rectangle bounds;
        final double scaleY;
        final IntConsumer tap;

        Target(Rectangle bounds, double scaleY, IntConsumer tap)
        {
            this.bounds = bounds;
            this.scaleY = scaleY;
            this.tap = tap;
        }
    }

    static void publish(Target[] snapshot)
    {
        targets = snapshot;
    }

    public static boolean tap(int x, int y)
    {
        for (Target target : targets)
        {
            if (target.bounds.contains(x, y))
            {
                target.tap.accept((int) ((y - target.bounds.y) / target.scaleY));
                return true;
            }
        }
        return false;
    }

    static int scrollPosition(int y, int height, int contentHeight, int current)
    {
        int max = Math.max(0, contentHeight - height);
        if (max == 0) return 0;
        int next;
        if (y < 16) next = current - 4;
        else if (y >= height - 16) next = current + 4;
        else
        {
            int thumb = Math.max(8, (height - 32) * height / contentHeight);
            int track = height - 32 - thumb;
            next = track > 0 ? (y - 16 - thumb / 2) * max / track : current;
        }
        return Math.max(0, Math.min(max, next));
    }
}
