import java.awt.Rectangle;
import java.util.Arrays;
import net.runelite.client.plugins.mobile.InventoryScaleGeometry;

public final class VerifyInventoryScale {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        for (int percent : new int[]{100, 125, 150, 200}) {
            for (Rectangle source : new Rectangle[]{new Rectangle(200, 350, 80, 140), new Rectangle(0, 0, 80, 140), new Rectangle(100, 100, 80, 140)}) {
                InventoryScaleGeometry g = new InventoryScaleGeometry(source, percent, 320, 600);
                check(new Rectangle(0, 0, 320, 600).contains(g.destination), "offscreen destination");
                check(g.destination.contains(source), "original panel must be covered");
                int[] pixels = new int[320 * 600];
                for (int i = 0; i < pixels.length; i++) pixels[i] = i + 1;
                int[] original = pixels.clone();
                g.composite(pixels, new int[source.width * source.height]);
                for (int y = 0; y < 600; y++) for (int x = 0; x < 320; x++) {
                    int index = y * 320 + x;
                    if (g.destination.contains(x, y)) {
                        check(source.contains(g.sourceX(x), g.sourceY(y)), "input escaped inventory");
                        check(pixels[index] == original[g.sourceY(y) * 320 + g.sourceX(x)], "display/input mismatch");
                    } else check(pixels[index] == original[index], "unrelated UI changed");
                }
                if (percent == 100) check(Arrays.equals(pixels, original), "100% must be identity");
            }
        }
        InventoryScaleGeometry small = new InventoryScaleGeometry(new Rectangle(5, 5, 80, 140), 200, 100, 150);
        check(new Rectangle(0, 0, 100, 150).contains(small.destination), "small viewport overflow");
        // A later panel's source overlaps an earlier panel's enlarged destination.
        // Capturing every source first prevents that later panel sampling scaled pixels.
        InventoryScaleGeometry[] panels = {
            new InventoryScaleGeometry(new Rectangle(60, 60, 30, 30), 200, 100, 100),
            new InventoryScaleGeometry(new Rectangle(35, 35, 20, 20), 150, 100, 100)
        };
        int[] pixels = new int[10000];
        for (int i = 0; i < pixels.length; i++) pixels[i] = i + 1;
        int[] original = pixels.clone();
        int[][] snapshots = {new int[900], new int[400]};
        for (int i = 0; i < 2; i++) panels[i].capture(pixels, snapshots[i]);
        for (InventoryScaleGeometry g : panels) g.clearSource(pixels);
        for (int i = 0; i < 2; i++) panels[i].paint(pixels, snapshots[i]);
        for (int y = 0; y < 100; y++) for (int x = 0; x < 100; x++) {
            int expected = original[y * 100 + x];
            for (InventoryScaleGeometry g : panels)
                if (g.destination.contains(x, y)) expected = original[g.sourceY(y) * 100 + g.sourceX(x)];
            check(pixels[y * 100 + x] == expected, "overlapping panels contaminated their pixel sources");
        }
        for (boolean singleRow : new boolean[]{false, true}) for (int scale : new int[]{100, 150, 200}) {
            Rectangle body = new Rectangle(220, 220, 80, 140);
            Rectangle top = new Rectangle(220, 540, 80, 20);
            Rectangle bottom = singleRow ? new Rectangle(140, 540, 80, 20) : new Rectangle(220, 560, 80, 20);
            InventoryScaleGeometry[] group = InventoryScaleGeometry.modernGroup(body, top, bottom, scale, 320, 600);
            Rectangle union = new Rectangle(group[0].destination);
            for (InventoryScaleGeometry g : group) {
                union.add(g.destination);
                check(new Rectangle(0, 0, 320, 600).contains(g.destination), "modern piece offscreen");
                int[] data = new int[320 * 600];
                for (int n = 0; n < data.length; n++) data[n] = n + 1;
                g.composite(data, new int[g.source.width * g.source.height]);
                for (int y = g.destination.y; y < g.destination.y + g.destination.height; y++)
                    for (int x = g.destination.x; x < g.destination.x + g.destination.width; x++)
                        check(data[y * 320 + x] == g.sourceY(y) * 320 + g.sourceX(x) + 1, "modern tab click/pixel mismatch");
            }
            int bodyBottom = group[0].destination.y + group[0].destination.height;
            check(Math.min(group[1].destination.y, group[2].destination.y) == bodyBottom, "tabs detached from inventory");
            if (singleRow) check(group[1].destination.y == group[2].destination.y, "horizontal tabs stacked");
            else check(group[1].destination.y + group[1].destination.height == group[2].destination.y, "tab rows detached");
            check(union.height <= 360, "detached source gap was scaled");
        }
        System.out.println("PASS: 12 scale/anchor combinations, full pixel/input agreement, unrelated UI preservation, 100% identity small viewport fit, and modern inventory/tab grouping");
    }
}
