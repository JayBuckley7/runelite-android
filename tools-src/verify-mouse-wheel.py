"""Check physical-wheel step accumulation without Android."""
from pathlib import Path
import os
import subprocess
root = Path('build/verify-mouse-wheel')
root.mkdir(parents=True, exist_ok=True)
source = root / 'VerifyMouseWheel.java'
source.write_text(r'''import net.runelite.mp.input.MouseWheelSteps;
public class VerifyMouseWheel {
    static void check(int expected, int actual) { if (expected != actual) throw new AssertionError(expected + " != " + actual); }
    public static void main(String[] args) {
        MouseWheelSteps wheel = new MouseWheelSteps();
        Object world = new Object(), panel = new Object();
        for (int i=0; i<100; i++) check(1, wheel.add(1, world));
        for (int i=0; i<100; i++) check(-1, wheel.add(-1, world));
        check(0, wheel.add(.25, world)); check(0, wheel.add(.25, world));
        check(0, wheel.add(.25, world)); check(1, wheel.add(.25, world));
        check(0, wheel.add(.75, world)); check(0, wheel.add(.25, panel));
        check(0, wheel.add(-.25, panel)); check(-3, wheel.add(-3, panel));
        check(0, wheel.add(Double.NaN, panel)); check(1, wheel.add(1, panel));
        System.out.println("PASS: wheel direction, burst counts, fractional steps and target isolation");
    }
}
''', encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
suffix = '.exe' if os.name == 'nt' else ''
production = Path('android/src/androidMain/java/net/runelite/mp/input/MouseWheelSteps.java')
subprocess.run([str(java / ('javac' + suffix)), '-d', str(root), str(production), str(source)], check=True)
subprocess.run([str(java / ('java' + suffix)), '-cp', str(root), 'VerifyMouseWheel'], check=True)
