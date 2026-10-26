import java.io.*;
import java.util.*;

public class TestDesktopRegexRuntime {
    public static void main(String[] args)throws Exception {
        String[] patterns={"(?=<img=[0-9]*>|<asis>.*?</asis>)|(?<=<img=[0-9]*>|</asis>)","(?<=a+)","(?<![0-9]+)x","(?<=a*)b","(?<=a{1,8})",",","","(?=a)","a*","\\s+","(?i)ab","[日本語]+","(a)\\1","\\p{L}+","\\X","(?<name>a+)b","(?<=😀+)x","["};
        if(args[0].equals("write")) {
            List<String> texts=new ArrayList<>(Arrays.asList("","a","a,b,","x<img=123>y","<img=1><img=99999999999999>","前<asis>English <img=3></asis>後","日本語😀x","aaaaab","ab\ncd","e\u0301","👩‍💻"));
            Random random=new Random(42);String[] tokens={"a","b","x","1","2"," ","\n","日本","😀","<img=123>","<asis>","</asis>",","};
            for(int n=0;n<150;n++){StringBuilder s=new StringBuilder();for(int i=0;i<random.nextInt(20);i++)s.append(tokens[random.nextInt(tokens.length)]);texts.add(s.toString());}
            try(DataOutputStream out=new DataOutputStream(new FileOutputStream(args[1]))) {
                for(String regex:patterns)for(String text:texts)for(int limit:new int[]{-1,0,1,2,5}) {
                    out.writeUTF(regex);out.writeUTF(text);out.writeInt(limit);out.writeUTF(DesktopRegexFixture.exercise(text,regex,limit));
                }
            }
            System.out.println("Desktop oracle cases="+patterns.length*texts.size()*5);
        } else {
            int count=0, failures=0;
            try(DataInputStream in=new DataInputStream(new FileInputStream(args[1]))) {
                while(in.available()>0){String regex=in.readUTF(),text=in.readUTF();int limit=in.readInt();String expected=in.readUTF();String actual=DesktopRegexFixture.exercise(text,regex,limit);count++;if(!actual.equals(expected)){if(failures++<8)System.out.println("Mismatch case="+count+" regex="+regex);}}
            }
            System.out.println("Runtime="+System.getProperty("java.vm.name")+" cases="+count+" mismatches="+failures);
            if(failures>0)throw new AssertionError("desktop parity failed");
        }
        System.out.println(DesktopRegexFixture.extended());
    }
}
