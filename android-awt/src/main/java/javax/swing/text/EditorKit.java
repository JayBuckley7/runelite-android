package javax.swing.text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import javax.swing.Action;

public abstract class EditorKit implements Cloneable {
    public EditorKit() {}

    public abstract Object clone();
    public abstract String getContentType();
    public abstract Action[] getActions();
    public abstract javax.swing.text.Caret createCaret();
    public abstract Document createDefaultDocument();

    public void install(JTextComponent c) {}
    public void deinstall(JTextComponent c) {}

    public void read(InputStream in, Document doc, int pos) throws IOException, BadLocationException { read(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8),doc,pos); }
    public void read(Reader in, Document doc, int pos) throws IOException, BadLocationException { StringBuilder text=new StringBuilder();char[] buffer=new char[4096];int count;while((count=in.read(buffer))!=-1)text.append(buffer,0,count);doc.insertString(pos,text.toString(),null); }
    public void write(OutputStream out, Document doc, int pos, int len) throws IOException, BadLocationException { java.io.Writer writer=new java.io.OutputStreamWriter(out,java.nio.charset.StandardCharsets.UTF_8);write(writer,doc,pos,len);writer.flush(); }
    public void write(Writer out, Document doc, int pos, int len) throws IOException, BadLocationException { out.write(doc.getText(pos,len)); }
}
