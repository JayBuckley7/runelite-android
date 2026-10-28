package javax.swing.table;

import java.awt.*;
import javax.swing.*;

public class JTableHeader extends JComponent {
    private TableColumnModel columnModel;
    private JTable table;
    private TableCellRenderer defaultRenderer = (t, value, selected, focus, row, col) -> {
        JLabel label = new JLabel(value == null ? "" : value.toString());
        label.setFont(getFont()); label.setForeground(getForeground());
        return label;
    };
    private boolean reorderingAllowed = true, resizingAllowed = true;
    public void setReorderingAllowed(boolean value) { reorderingAllowed = value; }
    public boolean getReorderingAllowed() { return reorderingAllowed; }
    public void setResizingAllowed(boolean value) { resizingAllowed = value; }
    public boolean getResizingAllowed() { return resizingAllowed; }
    public int columnAtPoint(Point p) {
        if (p.x < 0) return -1;
        int x = 0;
        for (int i = 0; i < columnModel.getColumnCount(); i++) {
            x += columnModel.getColumn(i).getWidth();
            if (p.x < x) return i;
        }
        return -1;
    }
    public JTableHeader() { this(new DefaultTableColumnModel()); }
    public JTableHeader(TableColumnModel model) { columnModel = model; setOpaque(true); }
    public TableColumnModel getColumnModel() { return columnModel; }
    public void setColumnModel(TableColumnModel model) { columnModel = model; revalidate(); repaint(); }
    public JTable getTable() { return table; }
    public void setTable(JTable table) { this.table = table; }
    public TableCellRenderer getDefaultRenderer() { return defaultRenderer; }
    public void setDefaultRenderer(TableCellRenderer renderer) { defaultRenderer = renderer; }
    @Override public Dimension getPreferredSize() {
        return new Dimension(columnModel.getTotalColumnWidth(), Math.max(20, getFontMetrics(getFont()).getHeight() + 4));
    }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int x = 0;
        for (int i = 0; i < columnModel.getColumnCount(); i++) {
            TableColumn column = columnModel.getColumn(i);
            TableCellRenderer renderer = column.getHeaderRenderer();
            if (renderer == null) renderer = defaultRenderer;
            Component cell = renderer.getTableCellRendererComponent(table, column.getHeaderValue(), false, false, -1, i);
            Graphics cg = g.create(x, 0, column.getWidth(), getHeight());
            cell.setBounds(0, 0, column.getWidth(), getHeight()); cell.paint(cg); cg.dispose();
            x += column.getWidth();
        }
    }
}
