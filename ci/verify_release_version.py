"""Reject APKs whose installed version or updater tag differs from the release."""

import argparse
import json
from pathlib import Path
import re
import subprocess


def verify(metadata, badging, build_config):
    package = re.search(r"^package: (.+)$", badging, re.MULTILINE)
    if package is None:
        raise ValueError("APK package metadata is missing")
    fields = dict(re.findall(r"(\w+)='([^']*)'", package.group(1)))
    expected = {
        "name": "net.runelite.mp",
        "versionName": metadata["tag"],
        "versionCode": str(metadata["versionCode"]),
    }
    for key, value in expected.items():
        if fields.get(key) != value:
            raise ValueError(f"APK {key}: expected {value!r}, got {fields.get(key)!r}")
    updater = re.search(r'\bString RELEASE_TAG = "([^"]+)";', build_config)
    if updater is None or updater.group(1) != metadata["tag"]:
        raise ValueError(f"BuildConfig.RELEASE_TAG must match {metadata['tag']!r}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("metadata", "apk", "build-config", "aapt"):
        parser.add_argument(f"--{name}", required=True)
    args = parser.parse_args()
    metadata = json.loads(Path(args.metadata).read_text(encoding="utf-8"))
    badging = subprocess.check_output(
        [args.aapt, "dump", "badging", args.apk], encoding="utf-8"
    )
    verify(metadata, badging, Path(args.build_config).read_text(encoding="utf-8"))
    print(f"Verified APK and updater version: {metadata['tag']} ({metadata['versionCode']})")


if __name__ == "__main__":
    main()
