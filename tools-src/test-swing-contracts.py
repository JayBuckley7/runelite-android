#!/usr/bin/env python3
"""Compile isolated pure Swing contracts and run on desktop plus Android shim sources."""
import argparse
from pathlib import Path
import shutil
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--java-home', type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
build = root / 'build' / 'compat-tests' / 'swing'
source = build / 'source'
classes = build / 'classes'
main = build / 'main'
for path in (source, classes, main):
    path.mkdir(parents=True, exist_ok=True)
names = [
    'DefaultButtonModel', 'ButtonGroup', 'DefaultComboBoxModel', 'AbstractListModel',
    'DefaultListModel', 'SpinnerNumberModel', 'Timer', 'ListSelectionModel',
    'DefaultListSelectionModel', 'SingleSelectionModel', 'DefaultSingleSelectionModel', 'text/AbstractDocument', 'text/PlainDocument',
    'text/DefaultStyledDocument', 'text/StyleContext', 'text/SimpleAttributeSet', 'UIDefaults', 'SwingWorker',
    'plaf/ColorUIResource', 'plaf/DimensionUIResource', 'plaf/InsetsUIResource', 'plaf/FontUIResource',
    'tree/DefaultTreeModel', 'tree/DefaultMutableTreeNode', 'tree/TreePath',
    'tree/TreeSelectionModel', 'tree/DefaultTreeSelectionModel', 'tree/RowMapper',
    'event/TreeSelectionEvent', 'event/TreeModelEvent', 'event/SwingPropertyChangeSupport',
    'undo/UndoManager', 'undo/AbstractUndoableEdit', 'undo/CompoundEdit',
    'undo/UndoableEditSupport', 'undo/StateEdit', 'undo/StateEditable',
]
files = []
for name in names:
    relative = Path('javax/swing') / (name + '.java')
    target = source / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(root / 'android-awt/src/main/java' / relative, target)
    files.append(str(target))
def tool(name):
    return str(args.java_home / 'bin' / name) if args.java_home else name
def run(command):
    subprocess.run(command, check=True)
run([tool('javac'), '-encoding', 'UTF-8', '--patch-module', 'java.desktop=' + str(source), '-d', str(classes)] + files)
run([tool('javac'), '-encoding', 'UTF-8', '-d', str(main), str(root / 'tools-src/TestSwingContracts.java')])
run([tool('java'), '-Djava.awt.headless=true', '-cp', str(main), 'TestSwingContracts'])
run([tool('java'), '-Djava.awt.headless=true', '--patch-module', 'java.desktop=' + str(classes), '-cp', str(main), 'TestSwingContracts'])
