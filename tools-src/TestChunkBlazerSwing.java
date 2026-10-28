import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.*;

/** API/behavior checks runnable both on desktop Swing and the Android compatibility layer. */
public final class TestChunkBlazerSwing {
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    public static void main(String[] args) {
        final int[] calls = {0};
        JComboBox<String> measured = new JComboBox<>(new String[]{"a", "longer"});
        measured.setRenderer((l, v, i, selected, focus) -> {
            calls[0]++;
            JPanel row = new JPanel(); row.add(new JLabel(v)); return row;
        });
        Dimension firstSize = measured.getPreferredSize();
        int initialCalls = calls[0];
        for (int i = 0; i < 100; i++) measured.getPreferredSize();
        check(calls[0] == initialCalls, "Unchanged combo must not re-run renderers for measurement");
        firstSize.width = 99999;
        check(measured.getPreferredSize().width != 99999, "Defensive measurement copy");
        measured.addItem("a considerably longer item"); measured.getPreferredSize();
        check(calls[0] > initialCalls, "Model updates invalidate measurements");
        int beforeFont = calls[0];
        measured.setFont(new Font("Dialog", Font.PLAIN, 22)); measured.getPreferredSize();
        check(calls[0] > beforeFont, "Font updates invalidate measurements");
        JPanel nested = new JPanel(); nested.setLayout(new BoxLayout(nested, BoxLayout.Y_AXIS));
        final int[] sizes = {0};
        nested.add(new JLabel("child") {
            @Override public Dimension getPreferredSize() {
                sizes[0]++; new JLabel("detached renderer").setText("changed");
                return super.getPreferredSize();
            }
        });
        nested.getPreferredSize(); int once = sizes[0];
        for (int i = 0; i < 100; i++) nested.getPreferredSize();
        check(sizes[0] == once, "Detached renderer must not invalidate BoxLayout cache");
        ((JLabel) nested.getComponent(0)).setText("changed child"); nested.getPreferredSize();
        try { Component.class.getMethod("subtreeLayoutGeneration"); check(sizes[0] > once, "Attached child changes invalidate BoxLayout cache"); } catch (NoSuchMethodException desktop) { /* Headless desktop has no validation root. */ }
        EmptyBorder border = new EmptyBorder(2, 3, 4, 5);
        Insets in = border.getBorderInsets(); in.left = 99;
        check(border.getBorderInsets().left == 3, "Insets must be a copy");
        JPanel parent = new JPanel(); JPanel child = new JPanel(); JLabel leaf = new JLabel("leaf");
        parent.add(child); child.add(leaf);
        check(SwingUtilities.getAncestorOfClass(JPanel.class, leaf) == child, "Nearest ancestor");
        check(SwingUtilities.getAncestorOfClass(JPanel.class, parent) == null, "Exclude self");
        JList<String> list = new JList<>(new String[]{"one", "two", "three"});
        list.setBorder(border); list.setSize(100, 150);
        list.setCellRenderer((l, value, index, selected, focus) -> {
            JLabel label = new JLabel(value); label.setPreferredSize(new Dimension(40, 10 + index * 10)); return label;
        });
        check(list.getCellBounds(1,1).equals(new Rectangle(3,12,92,20)), "Variable-height cell bounds");
        check(list.getCellBounds(2,0).equals(new Rectangle(3,2,92,60)), "Reversed range");
        check(list.getCellBounds(-1,1) == null, "Invalid bounds");
        check(list.locationToIndex(new Point(4,13)) == 1, "Hit test");
        check(list.locationToIndex(new Point(0,999)) == 2, "Nearest row below list");
        check(new JList<>().locationToIndex(new Point()) == -1, "Empty list");
        list.repaint(new Rectangle(1,2,3,4));
        list.setFixedCellWidth(20); list.setFixedCellHeight(10); list.setVisibleRowCount(2);
        list.setLayoutOrientation(JList.VERTICAL_WRAP);
        check(list.getCellBounds(2,2).equals(new Rectangle(23,2,20,10)), "Vertical wrap");
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        check(list.getCellBounds(2,2).equals(new Rectangle(3,12,20,10)), "Horizontal wrap");
        StyledUI first = new StyledUI(); StyledUI second = new StyledUI();
        JComboBox<String> combo = new JComboBox<>(new String[]{"a", "b"});
        combo.setUI(first); check(first.created, "Custom arrow factory");
        combo.setUI(second); check(first.removed && second.created, "Delegate lifecycle");
        check(combo.getUI() == second, "Typed UI getter");
        combo.setSelectedIndex(1); check("b".equals(combo.getSelectedItem()), "Selection preserved");
        BasicArrowButton arrow = new BasicArrowButton(SwingConstants.SOUTH, Color.GRAY, Color.BLACK, Color.WHITE, Color.RED);
        check(arrow.getDirection() == SwingConstants.SOUTH, "Arrow direction");
        JRadioButton radio = new JRadioButton("Casual", true);
        radio.setSize(100,30); radio.setForeground(Color.WHITE); radio.setOpaque(false);
        BufferedImage image = new BufferedImage(100,30,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics(); radio.paint(g); g.dispose();
        int drawn = 0; for(int y=0;y<30;y++) for(int x=0;x<100;x++) if(image.getRGB(x,y)!=0) drawn++;
        check(drawn > 20, "Radio button must render");
        System.out.println("PASS: insets, ancestor ABI, list geometry/hit testing/wrapping, repaint, combo delegates/selection, arrow and radio rendering");
    }
    static final class StyledUI extends BasicComboBoxUI {
        boolean created, removed;
        @Override protected JButton createArrowButton() { created=true; return new BasicArrowButton(SwingConstants.SOUTH); }
        @Override public void uninstallUI(JComponent c) { removed=true; super.uninstallUI(c); }
    }
}
