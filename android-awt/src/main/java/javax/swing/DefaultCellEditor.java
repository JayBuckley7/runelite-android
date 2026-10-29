package javax.swing;
import java.awt.Component;
import java.util.EventObject;
import javax.swing.table.TableCellEditor;
import javax.swing.tree.TreeCellEditor;
public class DefaultCellEditor extends AbstractCellEditor implements TableCellEditor, TreeCellEditor {
    private static final long serialVersionUID=1L;
    protected JComponent editorComponent;
    protected int clickCountToStart=1;
    public DefaultCellEditor(JTextField field){editorComponent=field;clickCountToStart=2;field.addActionListener(e->stopCellEditing());}
    public DefaultCellEditor(JCheckBox box){editorComponent=box;box.addActionListener(e->stopCellEditing());}
    public DefaultCellEditor(JComboBox<?> combo){editorComponent=combo;combo.addActionListener(e->stopCellEditing());}
    public Component getComponent(){return editorComponent;}
    public void setClickCountToStart(int count){clickCountToStart=count;}
    public int getClickCountToStart(){return clickCountToStart;}
    public boolean isCellEditable(EventObject event){return !(event instanceof java.awt.event.MouseEvent)||((java.awt.event.MouseEvent)event).getClickCount()>=clickCountToStart;}
    public Object getCellEditorValue(){if(editorComponent instanceof JTextField)return ((JTextField)editorComponent).getText();if(editorComponent instanceof JCheckBox)return ((JCheckBox)editorComponent).isSelected();return ((JComboBox<?>)editorComponent).getSelectedItem();}
    protected void setValue(Object value){if(editorComponent instanceof JTextField)((JTextField)editorComponent).setText(value==null?"":value.toString());else if(editorComponent instanceof JCheckBox)((JCheckBox)editorComponent).setSelected(Boolean.TRUE.equals(value));else ((JComboBox<?>)editorComponent).setSelectedItem(value);}
    public Component getTableCellEditorComponent(JTable table,Object value,boolean selected,int row,int column){setValue(value);return editorComponent;}
    public Component getTreeCellEditorComponent(JTree tree,Object value,boolean selected,boolean expanded,boolean leaf,int row){setValue(value);return editorComponent;}
}
