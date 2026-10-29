package javax.swing;
import java.awt.*;
public class JColorChooser extends JComponent {
    public static final String SELECTION_MODEL_PROPERTY = "selectionModel";
    public static final String PREVIEW_PANEL_PROPERTY = "previewPanel";
    private javax.swing.colorchooser.ColorSelectionModel selectionModel;
    private JComponent previewPanel;
    private boolean dragEnabled;
    private final javax.swing.event.ChangeListener changeListener = event -> repaint();
    public JColorChooser() { this(Color.WHITE); }
    public JColorChooser(Color initialColor) { this(new javax.swing.colorchooser.DefaultColorSelectionModel(initialColor)); }
    public JColorChooser(javax.swing.colorchooser.ColorSelectionModel model) { setSelectionModel(model); }
    public Color getColor() { return selectionModel.getSelectedColor(); }
    public void setColor(Color color) { selectionModel.setSelectedColor(color); }
    public void setColor(int rgb) { setColor(new Color(rgb)); }
    public void setColor(int red, int green, int blue) { setColor(new Color(red, green, blue)); }
    public javax.swing.colorchooser.ColorSelectionModel getSelectionModel() { return selectionModel; }
    public void setSelectionModel(javax.swing.colorchooser.ColorSelectionModel model) {
        if (model == null) throw new IllegalArgumentException("selection model cannot be null");
        javax.swing.colorchooser.ColorSelectionModel old = selectionModel;
        if (old != null) old.removeChangeListener(changeListener);
        selectionModel = model; model.addChangeListener(changeListener);
        firePropertyChange(SELECTION_MODEL_PROPERTY, old, model); repaint();
    }
    public JComponent getPreviewPanel() { return previewPanel; }
    public void setPreviewPanel(JComponent preview) {
        JComponent old = previewPanel;
        if (old != null) remove(old);
        previewPanel = preview;
        if (preview != null) add(preview);
        firePropertyChange(PREVIEW_PANEL_PROPERTY, old, preview); revalidate(); repaint();
    }
    public boolean getDragEnabled() { return dragEnabled; }
    public void setDragEnabled(boolean enabled) { dragEnabled = enabled; }
    @Override public Dimension getPreferredSize() { return isPreferredSizeSet() ? super.getPreferredSize() : new Dimension(220, 60); }
    @Override public void doLayout() { if (previewPanel != null) previewPanel.setBounds(0, 32, getWidth(), Math.max(0, getHeight() - 32)); }
    @Override protected void paintComponent(Graphics graphics) {
        Color color = getColor();
        graphics.setColor(color == null ? Color.WHITE : color); graphics.fillRect(4, 4, 24, 24);
        graphics.setColor(getForeground()); graphics.drawRect(4, 4, 24, 24);
        graphics.drawString("Choose color" + (color == null ? "" : String.format(" #%06X", color.getRGB() & 0xffffff)), 36, 22);
    }
    public interface DialogHandler { Color show(Component parent,String title,Color initial); }
    private static volatile DialogHandler handler;
    public static void setDialogHandler(DialogHandler h){handler=h;}
    public static Color showDialog(Component parent,String title,Color initial){DialogHandler h=handler;return h==null?null:h.show(parent,title,initial==null?Color.WHITE:initial);}
}
