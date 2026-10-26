"""Import the regex engine from the pinned local Temurin 21.0.6+7 source archive."""
import hashlib
import pathlib
import sys
import zipfile

jdk = pathlib.Path(sys.argv[1])
root = pathlib.Path(__file__).resolve().parents[1]
dest = root / 'android/src/androidMain/java/net/runelite/mp/compat/regex'
dest.mkdir(parents=True, exist_ok=True)
release = (jdk / 'release').read_text()
if 'JAVA_VERSION="21.0.6"' not in release:
    raise SystemExit('Expected the pinned JDK 21.0.6 source distribution')
with zipfile.ZipFile(jdk / 'lib/src.zip') as archive:
    sources = [n for n in archive.namelist() if n.startswith('java.base/java/util/regex/') and n.endswith('.java') and not n.endswith('PrintPattern.java')]
    sources += ['java.base/jdk/internal/util/regex/Grapheme.java']
    for name in sources:
        source = archive.read(name).decode()
        source = source.replace('java.util.regex', 'net.runelite.mp.compat.regex')
        source = source.replace('package jdk.internal.util.regex;', 'package net.runelite.mp.compat.regex;')
        source = source.replace('import jdk.internal.util.ArraysSupport;\n', '')
        source = source.replace('import jdk.internal.util.regex.Grapheme;\n', '')
        source = source.replace('ArraysSupport.newLength(', 'RegexPlatform.newLength(')
        source = source.replace('sun.text.Normalizer.getCombiningClass(c)', 'android.icu.lang.UCharacter.getCombiningClass(c)')
        for method in ['isEmojiPresentation', 'isEmojiModifierBase', 'isEmojiModifier', 'isEmojiComponent', 'isExtendedPictographic', 'isEmoji']:
            source = source.replace('Character::' + method, 'EmojiProperties::' + method)
            source = source.replace('Character.' + method + '(', 'EmojiProperties.' + method + '(')
        (dest / pathlib.Path(name).name).write_text(source, encoding='utf-8')
notice = root / 'android/src/androidMain/resources/META-INF/desktop-regex'
notice.mkdir(parents=True, exist_ok=True)
for name in ['LICENSE', 'ADDITIONAL_LICENSE_INFO', 'ASSEMBLY_EXCEPTION', 'unicode.md']:
    license_text = (jdk / 'legal/java.base' / name).read_text(encoding='utf-8')
    (notice / name).write_text('\n'.join(line.rstrip() for line in license_text.splitlines()).rstrip() + '\n', encoding='utf-8')
(notice / 'NOTICE.txt').write_text(
    'OpenJDK regex engine, Eclipse Temurin 21.0.6+7. GPLv2 with Classpath exception.\n'
    'Sources retain their original copyright and license headers.\n'
    'Adaptations: isolated package; Android ICU combining classes; local array growth;\n'
    'generated JDK 21 emoji property ranges. Other Character properties use Android.\n'
    'Regeneration: tools-src/vendor-desktop-regex.py and GenerateEmojiProperties.java.\n'
    'src.zip SHA256: ' + hashlib.sha256((jdk / 'lib/src.zip').read_bytes()).hexdigest() + '\n')
