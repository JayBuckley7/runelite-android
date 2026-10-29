package javax.swing.text;
public class DateFormatter extends InternationalFormatter {
    public DateFormatter(){this(java.text.DateFormat.getDateInstance());}
    public DateFormatter(java.text.DateFormat format){super(format);}
}
