import java.nio.file.*;
import java.util.function.IntPredicate;

/** Pin the six JDK 21 emoji properties rather than depending on the device ICU version. */
public class GenerateEmojiProperties {
    public static void main(String[] args) throws Exception {
        if (!System.getProperty("java.version").equals("21.0.6")) throw new IllegalStateException("Use pinned JDK 21.0.6");
        String[] names={"isEmoji","isEmojiPresentation","isEmojiModifier","isEmojiModifierBase","isEmojiComponent","isExtendedPictographic"};
        IntPredicate[] tests={Character::isEmoji,Character::isEmojiPresentation,Character::isEmojiModifier,Character::isEmojiModifierBase,Character::isEmojiComponent,Character::isExtendedPictographic};
        StringBuilder out=new StringBuilder("// Generated from Temurin 21.0.6+7 Unicode properties. See META-INF/desktop-regex notices.\npackage net.runelite.mp.compat.regex;\nfinal class EmojiProperties {\n");
        for(int i=0;i<names.length;i++) {
            out.append("private static final int[] ").append(names[i]).append("Ranges={");
            for(int cp=0;cp<=Character.MAX_CODE_POINT;cp++) if(tests[i].test(cp)) {
                int start=cp;while(cp<Character.MAX_CODE_POINT && tests[i].test(cp+1))cp++;
                out.append(start).append(',').append(cp).append(',');
            }
            out.append("};\nstatic boolean ").append(names[i]).append("(int cp){return contains(").append(names[i]).append("Ranges,cp);}\n");
        }
        out.append("private static boolean contains(int[] ranges,int cp){int lo=0,hi=ranges.length/2-1;while(lo<=hi){int mid=(lo+hi)>>>1;if(cp<ranges[2*mid])hi=mid-1;else if(cp>ranges[2*mid+1])lo=mid+1;else return true;}return false;}\n}\n");
        Files.writeString(Path.of(args[0]),out);
    }
}
