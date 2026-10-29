package javax.swing.text;
public class DefaultFormatter extends javax.swing.JFormattedTextField.AbstractFormatter {
    private Class<?> valueClass;
    private boolean commitsOnValidEdit,overwriteMode=true,allowsInvalid=true;
    private transient AbstractDocument document;
    private transient DocumentFilter previousFilter;
    public void install(javax.swing.JFormattedTextField field){
        super.install(field);
        if(field.getDocument() instanceof AbstractDocument){document=(AbstractDocument)field.getDocument();previousFilter=document.getDocumentFilter();document.setDocumentFilter(new DocumentFilter(){
            public void insertString(FilterBypass bypass,int offset,String text,AttributeSet attrs)throws BadLocationException{replace(bypass,offset,0,text,attrs);}
            public void remove(FilterBypass bypass,int offset,int length)throws BadLocationException{replace(bypass,offset,length,"",null);}
            public void replace(FilterBypass bypass,int offset,int length,String text,AttributeSet attrs)throws BadLocationException{
                String inserted=text==null?"":text;int count=length;
                if(overwriteMode&&count==0&&!inserted.isEmpty())count=Math.min(inserted.length(),bypass.getDocument().getLength()-offset);
                String current=bypass.getDocument().getText(0,bypass.getDocument().getLength());
                String candidate=current.substring(0,offset)+inserted+current.substring(offset+count);boolean valid;
                try{stringToValue(candidate);valid=true;}catch(java.text.ParseException invalid){valid=false;}
                if(!valid&&!allowsInvalid){invalidEdit();return;}
                bypass.replace(offset,count,inserted,attrs);setEditValid(valid);
                if(valid&&commitsOnValidEdit)try{field.commitEdit();}catch(java.text.ParseException invalid){setEditValid(false);}
            }
        });}
    }
    public void uninstall(){if(document!=null)document.setDocumentFilter(previousFilter);document=null;previousFilter=null;super.uninstall();}
    public Object stringToValue(String text)throws java.text.ParseException {Class<?> type=valueClass;if(type==null&&getFormattedTextField()!=null&&getFormattedTextField().getValue()!=null)type=getFormattedTextField().getValue().getClass();if(type==null||type==String.class)return text;try{return type.getConstructor(String.class).newInstance(text);}catch(ReflectiveOperationException e){throw new java.text.ParseException("Invalid "+type.getSimpleName(),0);}}
    public String valueToString(Object value)throws java.text.ParseException{return value==null?"":value.toString();}
    public void setValueClass(Class<?> type){valueClass=type;}
    public Class<?> getValueClass(){return valueClass;}
    public void setCommitsOnValidEdit(boolean value){commitsOnValidEdit=value;}
    public boolean getCommitsOnValidEdit(){return commitsOnValidEdit;}
    public void setOverwriteMode(boolean value){overwriteMode=value;}
    public boolean getOverwriteMode(){return overwriteMode;}
    public void setAllowsInvalid(boolean value){allowsInvalid=value;}
    public boolean getAllowsInvalid(){return allowsInvalid;}
}
