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
        System.out.println("PASS: 12 scale/anchor combinations, full pixel/input agreement, unrelated UI preservation, 100% identity and small viewport fit");
    }
}
