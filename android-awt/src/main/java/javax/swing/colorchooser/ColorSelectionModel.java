package javax.swing.colorchooser;

import java.awt.Color;
import javax.swing.event.ChangeListener;

public interface ColorSelectionModel {
    Color getSelectedColor();
    void setSelectedColor(Color color);
    void addChangeListener(ChangeListener listener);
    void removeChangeListener(ChangeListener listener);
}
