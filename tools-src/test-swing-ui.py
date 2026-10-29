#!/usr/bin/env python3
"""Exercise the opt-in Swing diagnostic APK on an emulator using real Android UI."""
import argparse
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument('--adb', required=True)
p.add_argument('--serial', required=True)
p.add_argument('--output', type=Path, required=True)
p.add_argument('--cases', default='input,choice,form,color,combo,directory,dialog,runtime,audio,graphics,dex')
a = p.parse_args()
if not re.fullmatch(r'emulator-\d+', a.serial):
    p.error('This diagnostic runner only targets an emulator.')
a.output.mkdir(parents=True, exist_ok=True)
package = 'net.runelite.mp.compatprobe'
activity = package + '/net.runelite.mp.compatprobe.SwingContractProbeActivity'

def adb(*args):
    return subprocess.run([a.adb, '-s', a.serial, *args], check=True, capture_output=True,
                          text=True, encoding='utf-8', errors='replace', timeout=60).stdout

def wait(function, timeout=60):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        last = function()
        if isinstance(last, ET.Element) or last:
            return last
        time.sleep(.3)
    raise AssertionError('Timed out waiting for diagnostic state: ' + str(last))

def nodes():
    status = adb('shell', 'uiautomator', 'dump', '/sdcard/compat-contract-ui.xml')
    if 'dumped to' not in status:
        return []
    return list(ET.fromstring(adb('shell', 'cat', '/sdcard/compat-contract-ui.xml')).iter('node'))

def node(text=None, kind=None):
    return next((n for n in nodes() if (text is None or n.get('text', '').casefold() == text.casefold()) and
                 (kind is None or n.get('class') == kind)), None)

def tap(n):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))

def tap_text(text):
    tap(wait(lambda: node(text)))

def long_press(n):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    x, y = str((x1 + x2) // 2), str((y1 + y2) // 2)
    adb('shell', 'input', 'swipe', x, y, x, y, '900')

def replace_input(field, value):
    tap(field)
    # ADB can inject keys before the newly focused IME connection is ready.
    time.sleep(1)
    adb('shell', 'input', 'keycombination', '113', '29')
    time.sleep(.2)
    adb('shell', 'input', 'text', value)
    wait(lambda: node(value, 'android.widget.EditText'))

def logs(pid):
    return adb('logcat', '-d', '--pid=' + pid)

def process_id():
    try:
        return adb('shell', 'pidof', package).strip()
    except subprocess.CalledProcessError:
        return None

for case in a.cases.split(','):
    started = time.monotonic()
    command = case.removesuffix('-cancel')
    if command in ('file', 'multi'):
        for suffix in ('a', 'b'):
            fixture = a.output / ('compat-import-' + suffix + '.txt')
            fixture.write_text('compat import ' + suffix.upper(), encoding='utf-8')
            adb('push', str(fixture), '/sdcard/Download/' + fixture.name)
    if command == 'save':
        adb('shell', 'rm', '-f', '/sdcard/Download/compat-export.json')
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    launched = adb('shell', 'am', 'start', '-W', '-S', '-n', activity, '--es', 'probe', command)
    # Slow emulator launches can outlast ActivityManager's own wait window.
    # The process and fixture BEGIN marker below still have to appear.
    if 'Status: ok' not in launched and 'Status: timeout' not in launched:
        raise AssertionError('Diagnostic launch failed: ' + launched)
    pid = wait(process_id)
    def begun():
        content = logs(pid)
        if 'SETUP FAILED' in content:
            raise AssertionError(content)
        return 'BEGIN ' + command in content
    wait(begun, 120)
    if case.endswith('-cancel'):
        if command == 'file':
            wait(lambda: node('Downloads'))
            adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
            expected = 'RESULT file=1 count=0'
        else:
            tap_text('Cancel')
            expected = 'RESULT input=null' if command == 'input' else 'RESULT ' + command + '=1 count=0'
    elif case == 'input':
        replace_input(wait(lambda: node('seed', 'android.widget.EditText')), 'abc')
        tap_text('OK')
        expected = 'RESULT input=abc'
    elif case == 'choice':
        tap_text('Beta'); tap_text('Gamma'); tap_text('OK')
        expected = 'RESULT choice=Gamma'
    elif case == 'form':
        wait(lambda: node('Swing form'))
        view = wait(lambda: node(kind='android.view.View'))
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', view.get('bounds')))
        adb('shell', 'input', 'tap', str(x1 + 25), str(y1 + (y2 - y1) * 5 // 6))
        tap_text('OK')
        expected = 'RESULT form=0 text=seed checked=false'
    elif case == 'color':
        replace_input(wait(lambda: node(kind='android.widget.EditText')), 'blue')
        tap_text('OK')
        expected = 'RESULT color=-16776961'
    elif case == 'combo':
        tap_text('Gamma')
        expected = 'EVENT combo selected=Gamma'
    elif case == 'directory':
        tap_text('Select folder')
        expected = 'RESULT directory=0 count=1 readable=true'
    elif case == 'dialog':
        tap_text('CLOSE')
        expected = 'RESULT dialog passed'
    elif case == 'file':
        tap_text('compat-import-a.txt')
        expected = 'RESULT file=0 count=1 readable=true'
    elif case == 'multi':
        long_press(wait(lambda: node('compat-import-a.txt')))
        tap_text('compat-import-b.txt')
        tap_text('Select')
        expected = 'RESULT multi=0 count=2 readable=true'
    elif case == 'save':
        tap_text('Save')
        expected = 'RESULT save=0 count=1'
    else:
        expected = 'RESULT ' + case + ' passed'
    try:
        def finished():
            content = logs(pid)
            if 'FAILED ' + case in content:
                raise AssertionError(content)
            return expected in content
        wait(finished, 1200 if case == 'client' else 120 if case in ('graphics', 'dex') else 60)
        if case == 'save':
            def exported():
                try:
                    return adb('shell', 'cat', '/sdcard/Download/compat-export.json') == '{"compat":true}'
                except subprocess.CalledProcessError:
                    return False
            wait(exported)
        print('PASS', case, '%.1fs' % (time.monotonic() - started), flush=True)
    finally:
        (a.output / (case + '.log')).write_text(logs(pid), encoding='utf-8')
