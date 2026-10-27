package javax.swing.table;
import javax.swing.*;
import java.awt.*;
public class DefaultTableCellRenderer extends JLabel implements TableCellRenderer {
    public DefaultTableCellRenderer() { setOpaque(true); }
    protected void setValue(Object value) { setText(value == null ? "" : value.toString()); }
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
        setFont(table.getFont());
        setForeground(selected ? table.getSelectionForeground() : table.getForeground());
        setBackground(selected ? table.getSelectionBackground() : table.getBackground());
        setValue(value); return this;
    }
    public static class UIResource extends DefaultTableCellRenderer implements javax.swing.plaf.UIResource {}
}
