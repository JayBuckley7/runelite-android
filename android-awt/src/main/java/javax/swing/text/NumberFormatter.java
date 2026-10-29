package javax.swing.text;
public class NumberFormatter extends InternationalFormatter {
    public NumberFormatter(){this(java.text.NumberFormat.getNumberInstance());}
    public NumberFormatter(java.text.NumberFormat format){super(format);}
}
