import java.util.*;
import java.util.function.*;
import java.util.regex.*;

public class DesktopRegexFixture {
    private static final Pattern FIELD=Pattern.compile("(?<word>a+)");
    public static Pattern identity(Pattern pattern){return pattern;}
    public static String exercise(String text,String regex,int limit) {
        try {
            Pattern pattern=identity(Pattern.compile(regex));
            StringBuilder out=new StringBuilder(Arrays.toString(text.split(regex,limit)));
            out.append('|').append(Arrays.toString(pattern.split(text,limit)));
            out.append('|').append(Arrays.toString(text.splitWithDelimiters(regex,limit)));
            out.append('|').append(text.matches(regex));
            out.append('|').append(text.replaceAll(regex,"#")).append('|').append(text.replaceFirst(regex,"#"));
            Matcher matcher=pattern.matcher(text);
            while(matcher.find()) {
                MatchResult match=matcher.toMatchResult();
                out.append('|').append(match.start()).append(':').append(match.end()).append(':').append(match.group());
            }
            return out.toString();
        } catch(PatternSyntaxException error){return "INVALID";}
    }
    public static String extended() {
        Function<String,Pattern> factory=Pattern::compile;
        BiFunction<String,String,String[]> split=String::split;
        Function<String,String[]> bound="a,b,"::split;
        Pattern named=factory.apply("(?<word>a+)");
        Matcher matcher=named.matcher("xxaaayy").region(2,5);
        if(!matcher.matches() || !matcher.group("word").equals("aaa"))throw new AssertionError("named groups/region");
        if(!Arrays.equals(split.apply("a,b,",","),bound.apply(",")))throw new AssertionError("method references");
        if(!FIELD.asPredicate().test("a") || !FIELD.asMatchPredicate().test("aaa"))throw new AssertionError("predicate");
        if(!Pattern.compile("é",Pattern.CANON_EQ).matcher("e\u0301").matches())throw new AssertionError("canonical equivalence");
        if(!Pattern.compile("\\p{IsExtended_Pictographic}").matcher("😀").matches())throw new AssertionError("emoji");
        String replaced=named.matcher("aaa").replaceAll(m->"["+m.group("word")+"]");
        if(!replaced.equals("[aaa]"))throw new AssertionError("functional replacement");
        if(named.matcher("aaa").results().count()!=1)throw new AssertionError("results stream");
        if(!Pattern.quote("a.b").equals("\\Qa.b\\E") || !Matcher.quoteReplacement("$").equals("\\$"))throw new AssertionError("quoting");
        try { String text=null;text.split(",");throw new AssertionError("null swallowed"); }catch(NullPointerException expected){}
        return "PASS extended APIs, named groups, regions, flags, method references, functional replacement, streams, Unicode";
    }
}
