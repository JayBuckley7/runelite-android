"""Check color editor conversion against RuneLite's decimal ARGB storage."""
from pathlib import Path
import os
import subprocess

root = Path('build/verify-config-colors')
root.mkdir(parents=True, exist_ok=True)
source = root / 'VerifyConfigColors.java'
source.write_text(r'''import net.runelite.mp.input.ConfigColors;
public class VerifyConfigColors {
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        for (int color : new int[]{0, 0x00123456, 0x80123456, 0xff123456, -1}) {
            int parsed = ConfigColors.parseHex(ConfigColors.hex(color, true), true);
            check(parsed == color);
            check(Integer.parseInt(Integer.toString(parsed)) == color);
            check(ConfigColors.parseHex(ConfigColors.hex(color, false), false) == (color | 0xff000000));
        }
        check(ConfigColors.parseHex(" #aBcDeF ", false) == 0xffabcdef);
        check(ConfigColors.hex(0x00123456, true).equals("00123456"));
        check(ConfigColors.hex(0xff000001, false).equals("000001"));
        for (String invalid : new String[]{"", "FFF", "GGGGGG", "-00001", "1234567", "123456789"}) {
            check(ConfigColors.parseHex(invalid, false) == null);
            check(ConfigColors.parseHex(invalid, true) == null);
        }
        check(ConfigColors.parseHex("12345678", false) == null);
        check(ConfigColors.parseHex("123456", true) == null);
        System.out.println("PASS: transparent/opaque ARGB, decimal storage, hex padding, invalid input");
    }
}
''', encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
suffix = '.exe' if os.name == 'nt' else ''
production = Path('android/src/androidMain/java/net/runelite/mp/input/ConfigColors.java')
subprocess.run([str(java / ('javac' + suffix)), '-d', str(root), str(production), str(source)], check=True)
subprocess.run([str(java / ('java' + suffix)), '-cp', str(root), 'VerifyConfigColors'], check=True)
