#!/usr/bin/env python3
"""Validate chooser/model contracts plus native bridge dispatch without Android UI."""
import argparse
from pathlib import Path
import shutil
import subprocess

p = argparse.ArgumentParser()
p.add_argument('--java-home', type=Path)
a = p.parse_args()
root = Path(__file__).resolve().parent.parent
build = root / 'build/compat-tests/dialogs'
source, classes, main = (build / x for x in ('source', 'classes', 'main'))
for path in (source, classes, main):
    path.mkdir(parents=True, exist_ok=True)
files = []
for name in ('JFileChooser', 'JOptionPane', 'JColorChooser', 'colorchooser/ColorSelectionModel', 'colorchooser/DefaultColorSelectionModel'):
    path = Path('javax/swing') / (name + '.java')
    target = source / path
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(root / 'android-awt/src/main/java' / path, target)
    files.append(str(target))
def run(name, *args):
    tool = str(a.java_home / 'bin' / name) if a.java_home else name
    encoding = ['-encoding', 'UTF-8'] if name == 'javac' else []
    subprocess.run([tool, *encoding, *map(str, args)], check=True)
run('javac', '--patch-module', 'java.desktop=' + str(source), '-d', classes, *files)
run('javac', '--patch-module', 'java.desktop=' + str(source), '-d', main, *files, root / 'tools-src/TestSwingDialogContracts.java')
run('java', '-Djava.awt.headless=true', '-cp', main, 'TestSwingDialogContracts')
run('java', '-Djava.awt.headless=true', '--patch-module', 'java.desktop=' + str(classes), '-cp', main, 'TestSwingDialogContracts', 'bridge')
