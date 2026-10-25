package net.runelite.awt.impl;

import java.util.Arrays;

/**
 * Non-rectangular clips as lists of disjoint device rects ({@code x0, y0, x1, y1} quads,
 * half-open). A clip shape is rasterized with the same pixel-centre rule as fills, and
 * identical consecutive rows are merged into bands, so the common rectilinear clip (a
 * rect minus a few widget rects, as RuneLite's world map overlay builds) becomes a handful
 * of rects while arbitrary shapes still clip exactly at pixel granularity.
 */
public final class ClipRegion {
    private ClipRegion() {}

    /** Rasterize {@code c} within [bx0,bx1) x [by0,by1). Returns quads, possibly empty. */
    public static int[] rasterize(Contours c, boolean evenOdd, int bx0, int by0, int bx1, int by1) {
        if (bx0 >= bx1 || by0 >= by1 || c.n == 0) return new int[0];
        // Edge list: x0, y0, x1, y1, dir per non-horizontal edge, top-first.
        int edgesMax = c.nCoords / 2;
        double[] ex = new double[edgesMax * 4];
        int[] dir = new int[edgesMax];
        int ne = 0;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        int off = 0;
        for (int k = 0; k < c.n; k++) {
            int cnt = c.counts[k];
            for (int i = 0; i < cnt; i++) {
                int j = i + 1 == cnt ? 0 : i + 1;
                double xa = c.xy[off + i * 2], ya = c.xy[off + i * 2 + 1];
                double xb = c.xy[off + j * 2], yb = c.xy[off + j * 2 + 1];
                if (ya == yb || Double.isNaN(xa + ya + xb + yb) || Double.isInfinite(xa + ya + xb + yb)) continue;
                if (ya < yb) {
                    ex[ne * 4] = xa; ex[ne * 4 + 1] = ya; ex[ne * 4 + 2] = xb; ex[ne * 4 + 3] = yb; dir[ne] = 1;
                } else {
                    ex[ne * 4] = xb; ex[ne * 4 + 1] = yb; ex[ne * 4 + 2] = xa; ex[ne * 4 + 3] = ya; dir[ne] = -1;
                }
                minY = Math.min(minY, Math.min(ya, yb));
                maxY = Math.max(maxY, Math.max(ya, yb));
                ne++;
            }
            off += cnt * 2;
        }
        if (ne == 0) return new int[0];
        int r0 = (int) Math.max(by0, Math.min(by1, Math.ceil(minY - 0.5)));
        int r1 = (int) Math.max(by0, Math.min(by1, Math.ceil(maxY - 0.5)));

        int[] out = new int[16];
        int no = 0;
        int[] prev = new int[0];
        int prevStart = r0;
        double[] xs = new double[ne];
        int[] ds = new int[ne];
        Integer[] order = new Integer[ne];
        for (int row = r0; row <= r1; row++) {
            int[] spans;
            if (row < r1) {
                double yc = row + 0.5;
                int n = 0;
                for (int e = 0; e < ne; e++) {
                    double y0 = ex[e * 4 + 1], y1 = ex[e * 4 + 3];
                    if (yc >= y0 && yc < y1) {
                        double x0 = ex[e * 4], x1 = ex[e * 4 + 2];
                        xs[n] = x0 + (yc - y0) * (x1 - x0) / (y1 - y0);
                        ds[n] = dir[e];
                        order[n] = n;
                        n++;
                    }
                }
                final double[] fxs = xs;
                Arrays.sort(order, 0, n, (a, b) -> Double.compare(fxs[a], fxs[b]));
                int[] sp = new int[n + 2];
                int ns = 0;
                int w = 0;
                double start = 0;
                for (int i = 0; i < n; i++) {
                    int idx = order[i];
                    boolean was = evenOdd ? (w & 1) != 0 : w != 0;
                    w += ds[idx];
                    boolean now = evenOdd ? (w & 1) != 0 : w != 0;
                    if (!was && now) start = xs[idx];
                    else if (was && !now) {
                        int a = (int) Math.max(bx0, Math.min(bx1, Math.ceil(start - 0.5)));
                        int b = (int) Math.max(bx0, Math.min(bx1, Math.ceil(xs[idx] - 0.5)));
                        if (a < b) {
                            if (ns > 0 && sp[ns - 1] >= a) sp[ns - 1] = Math.max(sp[ns - 1], b);
                            else { sp[ns++] = a; sp[ns++] = b; }
                        }
                    }
                }
                spans = Arrays.copyOf(sp, ns);
            } else {
                spans = new int[0];
            }
            if (!Arrays.equals(spans, prev)) {
                for (int i = 0; i < prev.length; i += 2) {
                    if (no + 4 > out.length) out = Arrays.copyOf(out, out.length * 2);
                    out[no++] = prev[i]; out[no++] = prevStart; out[no++] = prev[i + 1]; out[no++] = row;
                }
                prev = spans;
                prevStart = row;
            }
        }
        return Arrays.copyOf(out, no);
    }

    /** Pairwise intersection of two disjoint rect lists (the result is disjoint too). */
    public static int[] intersect(int[] a, int na, int[] b, int nb) {
        int[] out = new int[Math.max(4, Math.min(na * nb, 4096) * 4)];
        int no = 0;
        for (int i = 0; i < na; i++) {
            int ax0 = a[i * 4], ay0 = a[i * 4 + 1], ax1 = a[i * 4 + 2], ay1 = a[i * 4 + 3];
            for (int j = 0; j < nb; j++) {
                int x0 = Math.max(ax0, b[j * 4]), y0 = Math.max(ay0, b[j * 4 + 1]);
                int x1 = Math.min(ax1, b[j * 4 + 2]), y1 = Math.min(ay1, b[j * 4 + 3]);
                if (x0 < x1 && y0 < y1) {
                    if (no + 4 > out.length) out = Arrays.copyOf(out, out.length * 2);
                    out[no++] = x0; out[no++] = y0; out[no++] = x1; out[no++] = y1;
                }
            }
        }
        return Arrays.copyOf(out, no);
    }
}
