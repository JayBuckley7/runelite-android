import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.swing.*;

/** Desktop-reference regression for overlapping plugin panels and their gear buttons. */
public class TestLayeredPane {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static JComponent block(Color color) {
        JComponent c = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(color); g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        c.setBounds(0, 0, 100, 100);
        return c;
    }
    // Same front-to-back traversal used by SwingPanelHost.
    private static Component hit(Component c, int x, int y) {
        if (c instanceof Container) for (Component child : ((Container)c).getComponents()) {
            if (child.isVisible() && child.getBounds().contains(x, y))
                return hit(child, x - child.getX(), y - child.getY());
        }
        return c;
    }
    public static void main(String[] args) {
        JLayeredPane pane = new JLayeredPane(); pane.setSize(100, 100);
        JComponent content = block(Color.RED), settings = block(Color.GREEN), gear = block(Color.BLUE);
        gear.setBounds(5, 5, 20, 20);
        pane.add(content, JLayeredPane.DEFAULT_LAYER);
        pane.add(settings, JLayeredPane.PALETTE_LAYER); settings.setVisible(false);
        pane.add(gear, JLayeredPane.MODAL_LAYER);
        check(hit(pane, 10, 10) == gear, "gear must receive touches above content");
        gear.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                settings.setVisible(!settings.isVisible()); content.setVisible(!settings.isVisible());
            }
        });
        Component target = hit(pane, 10, 10);
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 1, false, MouseEvent.BUTTON1));
        check(settings.isVisible() && !content.isVisible(), "gear opens settings");
        check(hit(pane, 50, 50) == settings, "settings now receives touches");
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.getGraphics(); pane.paint(g); g.dispose();
        check(image.getRGB(10, 10) == Color.BLUE.getRGB(), "gear painted above settings");
        check(image.getRGB(50, 50) == Color.GREEN.getRGB(), "settings painted above content");
        JComponent second = block(Color.YELLOW); pane.add(second, JLayeredPane.MODAL_LAYER);
        check(pane.getPosition(gear) == 0 && pane.getPosition(second) == 1, "same-layer add order");
        pane.moveToFront(second); check(pane.getPosition(second) == 0, "move to front");
        pane.moveToBack(second); check(pane.getPosition(second) == 1, "move to back");
        pane.setLayer(second, 300); check(pane.getComponent(0) == second, "change layer reorders");
        check(pane.highestLayer() == 300 && pane.lowestLayer() == 0, "layer bounds");
        check(pane.getComponentCountInLayer(200) == 1, "layer count");
        check(pane.getComponentsInLayer(200)[0] == gear, "layer members");
        check(JLayeredPane.getLayer(gear) == 200 && JLayeredPane.getLayeredPaneAbove(gear) == pane, "layer metadata and ancestor");
        pane.remove(second); check(pane.getPosition(second) == -1, "removed position");
        pane.removeAll(); check(pane.highestLayer() == 0 && pane.lowestLayer() == 0, "empty bounds");
        System.out.println("PASS: gear click, visibility, paint order, layer ordering and metadata");
    }
}
