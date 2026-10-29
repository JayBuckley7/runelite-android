import java.util.Arrays;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.internal.Strings;

/** Run against the release APK without bundling either dependency in the probe. */
public final class TestReleasePluginApi {
    public static void main(String[] args) throws Exception {
        TestReleaseProtobuf.main(args);
        if (!Strings.isNullOrEmpty("") || Strings.isNullOrEmpty("token")) throw new AssertionError("jopt empty check");
        if (!"a,b".equals(Strings.join(new String[]{"a", "b"}, ","))) throw new AssertionError("array join");
        if (!"a/b".equals(Strings.join(Arrays.asList("a", "b"), "/"))) throw new AssertionError("list join");
        if (!"[value]".equals(Strings.surround("value", '[', ']'))) throw new AssertionError("surround");
        OptionParser parser = new OptionParser();
        parser.accepts("limit").withRequiredArg().ofType(Integer.class);
        OptionSet options = parser.parse("--limit", "3");
        if (!Integer.valueOf(3).equals(options.valueOf("limit"))) throw new AssertionError("option conversion");
        Strings.class.getDeclaredMethods();
        OptionParser.class.getDeclaredMethods();
        System.out.println("PASS: release jopt names, overloads, conversion and reflection");
    }
}
