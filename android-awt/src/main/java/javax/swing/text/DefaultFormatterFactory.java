package javax.swing.text;
import javax.swing.JFormattedTextField;
public class DefaultFormatterFactory extends JFormattedTextField.AbstractFormatterFactory {
    private JFormattedTextField.AbstractFormatter defaultFormat,displayFormat,editFormat,nullFormat;
    public DefaultFormatterFactory(){}
    public DefaultFormatterFactory(JFormattedTextField.AbstractFormatter format){defaultFormat=format;}
    public DefaultFormatterFactory(JFormattedTextField.AbstractFormatter format,JFormattedTextField.AbstractFormatter display){this(format);displayFormat=display;}
    public DefaultFormatterFactory(JFormattedTextField.AbstractFormatter format,JFormattedTextField.AbstractFormatter display,JFormattedTextField.AbstractFormatter edit){this(format,display);editFormat=edit;}
    public DefaultFormatterFactory(JFormattedTextField.AbstractFormatter format,JFormattedTextField.AbstractFormatter display,JFormattedTextField.AbstractFormatter edit,JFormattedTextField.AbstractFormatter nil){this(format,display,edit);nullFormat=nil;}
    public JFormattedTextField.AbstractFormatter getFormatter(JFormattedTextField field){if(field==null)return null;if(field.getValue()==null&&nullFormat!=null)return nullFormat;return field.isFocusOwner()&&editFormat!=null?editFormat:displayFormat!=null?displayFormat:defaultFormat;}
    public void setDefaultFormatter(JFormattedTextField.AbstractFormatter format){defaultFormat=format;}public JFormattedTextField.AbstractFormatter getDefaultFormatter(){return defaultFormat;}
    public void setDisplayFormatter(JFormattedTextField.AbstractFormatter format){displayFormat=format;}public JFormattedTextField.AbstractFormatter getDisplayFormatter(){return displayFormat;}
    public void setEditFormatter(JFormattedTextField.AbstractFormatter format){editFormat=format;}public JFormattedTextField.AbstractFormatter getEditFormatter(){return editFormat;}
    public void setNullFormatter(JFormattedTextField.AbstractFormatter format){nullFormat=format;}public JFormattedTextField.AbstractFormatter getNullFormatter(){return nullFormat;}
}
