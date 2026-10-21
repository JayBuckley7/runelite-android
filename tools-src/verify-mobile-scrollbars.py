"""Exercise the production skill-guide scrollbar tap routing without Android."""
from pathlib import Path
import os
import subprocess

root = Path('build/verify-mobile-scrollbars')
root.mkdir(parents=True, exist_ok=True)
source = root / 'VerifyMobileScrollbars.java'
source.write_text(r'''package net.runelite.client.plugins.mobile;
import java.awt.Rectangle;
public final class VerifyMobileScrollbars {
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    public static void main(String[] args) {
        check(MobileScrollbars.scrollPosition(0, 200, 1000, 100) == 96);
        check(MobileScrollbars.scrollPosition(199, 200, 1000, 100) == 104);
        check(MobileScrollbars.scrollPosition(0, 200, 1000, 0) == 0);
        check(MobileScrollbars.scrollPosition(199, 200, 1000, 800) == 800);
        check(MobileScrollbars.scrollPosition(16, 200, 1000, 100) == 0);
        check(MobileScrollbars.scrollPosition(183, 200, 1000, 100) == 800);
        check(MobileScrollbars.scrollPosition(80, 200, 0, 100) == 0);
        int[] y = {-1};
        MobileScrollbars.publish(new MobileScrollbars.Target[] {
            new MobileScrollbars.Target(new Rectangle(200, 100, 32, 400), 2, v -> y[0] = v)
        });
        check(MobileScrollbars.tap(215, 120) && y[0] == 10);
        check(MobileScrollbars.tap(215, 498) && y[0] == 199);
        check(!MobileScrollbars.tap(199, 120));
        check(!MobileScrollbars.tap(232, 120));
        check(!MobileScrollbars.tap(215, 500));
        MobileScrollbars.publish(new MobileScrollbars.Target[0]);
        check(!MobileScrollbars.tap(215, 120));
        System.out.println("Skill-guide scrollbar arrows, limits, track, scaling and clearing passed");
    }
}
''', encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
suffix = '.exe' if os.name == 'nt' else ''
production = Path('android/src/androidMain/java/net/runelite/client/plugins/mobile/MobileScrollbars.java')
subprocess.run([str(java / ('javac' + suffix)), '-d', str(root), str(production), str(source)], check=True)
subprocess.run([str(java / ('java' + suffix)), '-cp', str(root), 'net.runelite.client.plugins.mobile.VerifyMobileScrollbars'], check=True)
