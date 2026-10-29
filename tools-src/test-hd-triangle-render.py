#!/usr/bin/env python3
"""Check production scene varyings with an offscreen probe on one explicit ADB device."""

import argparse
import hashlib
import itertools
import json
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid


def define(source, name, value, required=True):
    pattern = rf"(?m)^([ \t]*#define[ \t]+{re.escape(name)}[ \t]+)[^\r\n]+"
    source, count = re.subn(pattern, lambda match: match[1] + str(value), source)
    if count != 1 and (required or count):
        raise ValueError(f"Expected one {name} definition, found {count}")
    return source


def display_body(source, body):
    pattern = (
        r"(#if DISPLAY_BASE_COLOR\s+if \(DISPLAY_BASE_COLOR == 1\) \{)"
        r"[^\n]*\n.*?\n[ \t]*\}\s*#endif"
    )
    source, count = re.subn(
        pattern, lambda match: match[1] + "\n" + body + "\n        }\n        #endif",
        source, flags=re.DOTALL,
    )
    if count != 1:
        raise ValueError(f"Expected one DISPLAY_BASE_COLOR branch, found {count}")
    return source


def generate_cases(vertex, fragment, destination):
    if not re.search(r"(?m)^#define ZONE_RENDERER 1\s*$", vertex):
        raise ValueError("The fixture requires generated zone-renderer mobile_scene shaders")
    # Accept old expanded shaders too, so the same runner can demonstrate a regression.
    position = "fPosition" if re.search(r"\bin vec3 fPosition\s*;", fragment) else "IN.position"
    uv = "fUv" if re.search(r"\bin vec2 fUv\s*;", fragment) else "IN.uv"
    destination.mkdir(parents=True, exist_ok=True)
    cases = []

    def add(name, mode, pom, normal, colors, centroid, body):
        sources = [vertex, fragment]
        for i, source in enumerate(sources):
            for key, value in (("ANDROID_VERTEX_COLORS", colors), ("CENTROID_QUALIFIER", centroid)):
                source = define(source, key, value)
            if i:
                source = define(source, "PARALLAX_OCCLUSION_MAPPING", pom)
                source = define(source, "NORMAL_MAPPING", normal)
                source = define(source, "DISPLAY_BASE_COLOR", 0 if mode == "material" else 1)
                if mode == "material":
                    marker = "        outputColor = mix(underlayColor, overlayColor, overlayMix);"
                    if source.count(marker) != 1:
                        raise ValueError("Expected one texture blend output")
                    source = source.replace(marker, marker + "\n            FragColor = vec4(outputColor.rgb, 1.0); return;", 1)
                else:
                    source = display_body(source, body)
            sources[i] = source
        paths = []
        for suffix, source in zip(("vert", "frag"), sources):
            path = destination / f"{name}_{suffix}.glsl"
            path.write_text(source, encoding="utf-8", newline="\n")
            paths.append(path.name)
        cases.append({"name": name, "mode": mode, "pom": pom, "normal": normal,
                      "vertex_colors": colors, "centroid": centroid, "shaders": paths})

    add("constant", "constant:64,128,191", 0, 0, 0, 0,
        "            FragColor = vec4(.25, .5, .75, 1.0);\n            return;")
    for colors, index in itertools.product((0, 1), (1, 2, 3)):
        add(f"base{index}_control_v{colors}", "base", 0, 0, colors, 0,
            f"            FragColor = vec4(linearToSrgb(baseColor{index}.rgb), 1.0);\n            return;")

    for mode, pom, normal, colors, centroid in itertools.product(
        ("base", "blend"), (0, 1), (0, 1), (0, 1), (0, 1)
    ):
        # Keep POM UVs, normal sampling, and base-color varyings live in both modes.
        # Their bounded contribution changes expected pixels by less than 2/255.
        body = f"""            vec3 fixtureBase = linearToSrgb((baseColor1 * fTexBlend.x +
                baseColor2 * fTexBlend.y + baseColor3 * fTexBlend.z).rgb);
            vec3 fixtureNormal = normalize(sampleNormalMap(material1, uv1,
                cotangent_frame(N, {position}, {uv} * -1.0)));
            vec3 fixtureKeepAlive = (vec3(fract(uv1), 0.0) +
                clamp(fixtureNormal * .5 + .5, 0.0, 1.0)) * .0005;
            vec3 fixtureResult = {"fixtureBase * .999" if mode == "base" else "fTexBlend * .9985 + fixtureBase * .0005"};
            FragColor = vec4(fixtureResult + fixtureKeepAlive, 1.0);
            return;"""
        add(f"{mode}_p{pom}_n{normal}_v{colors}_c{centroid}", mode, pom, normal, colors, centroid, body)
    # Distinct UBO entries and RGB texture layers catch struct-selection corruption.
    for pom, colors, centroid in itertools.product((0, 1), repeat=3):
        add(f"material_p{pom}_v{colors}_c{centroid}", "material", pom, 0, colors, centroid, "")
    return cases


def adb_command(adb, serial, arguments, timeout=120):
    try:
        result = subprocess.run([str(adb), "-s", serial, *arguments], capture_output=True,
                                text=True, encoding="utf-8", errors="replace", timeout=timeout)
        return result.returncode, result.stdout + result.stderr
    except subprocess.TimeoutExpired as error:
        output = error.stdout or b""
        if isinstance(output, bytes):
            output = output.decode("utf-8", errors="replace")
        return 124, output + "\nADB command timed out\n"


def run(args):
    adb, probe, shaders, output = (Path(value).resolve() for value in
                                 (args.adb, args.probe, args.shaders, args.output))
    if not args.serial.strip() or any(c in args.serial for c in "\r\n\0"):
        raise ValueError("An explicit nonempty ADB serial is required")
    for path in (adb, probe, shaders / "mobile_scene_vert.glsl", shaders / "mobile_scene_frag.glsl"):
        if not path.is_file():
            raise ValueError(f"Missing input: {path}")
    output.mkdir(parents=True, exist_ok=True)
    generated = output / "shaders"
    logs = output / "logs"
    logs.mkdir(exist_ok=True)
    sources = [(shaders / f"mobile_scene_{stage}.glsl").read_text(encoding="utf-8")
               for stage in ("vert", "frag")]
    cases = generate_cases(*sources, generated)
    remote = "/data/local/tmp/hd-triangles-" + uuid.uuid4().hex
    report = {
        "serial": args.serial, "probe_sha256": hashlib.sha256(probe.read_bytes()).hexdigest(),
        "source_sha256": {stage: hashlib.sha256(source.encode("utf-8")).hexdigest()
                          for stage, source in zip(("vertex", "fragment"), sources)},
        "results": [], "passed": 0, "failed": 0,
    }
    setup_log = []
    setup_ok = True
    for arguments in (["shell", "mkdir", "-p", remote],
                      ["push", str(probe), remote + "/probe"],
                      ["push", str(generated), remote + "/shaders"],
                      ["shell", "chmod", "700", remote + "/probe"]):
        code, text = adb_command(adb, args.serial, arguments)
        setup_log.append(text)
        if code:
            report["setup_error"] = {"command": arguments[0], "returncode": code}
            setup_ok = False
            break
    (output / "setup.log").write_text("\n".join(setup_log), encoding="utf-8")
    try:
        if setup_ok:
            for case in cases:
                started = time.monotonic()
                code, text = adb_command(adb, args.serial, ["shell", remote + "/probe",
                    *(remote + "/shaders/" + name for name in case["shaders"]), case["mode"]])
                (logs / (case["name"] + ".log")).write_text(text, encoding="utf-8")
                good = code == 0 and "gl_error_count=0" in text and re.search(
                    r"^fixture_check .* result=PASS$", text, re.MULTILINE) is not None
                metrics = [line for line in text.splitlines() if line.startswith(
                    ("rgba_range=", "rgb_sum=", "fixture_check ", "gl_error_count=", "gl_error "))]
                result = {key: value for key, value in case.items() if key != "shaders"}
                result.update(returncode=code, passed=good,
                              seconds=round(time.monotonic() - started, 3), metrics=metrics)
                report["results"].append(result)
                report["passed" if good else "failed"] += 1
                if "renderer" not in report:
                    for line in text.splitlines():
                        if line.startswith(("renderer=", "version=", "shading=")):
                            key, value = line.split("=", 1)
                            report[key] = value
                print(f"{'PASS' if good else 'FAIL'} {case['name']}", flush=True)
    finally:
        # This unique path was created above; no app storage or preexisting files are touched.
        code, text = adb_command(adb, args.serial, ["shell", "rm", "-rf", remote])
        if code:
            report["cleanup_error"] = {"returncode": code, "remote": remote}
            (output / "cleanup.log").write_text(text, encoding="utf-8")
        report["expected_cases"] = len(cases)
        report["success"] = setup_ok and report["failed"] == 0 and len(report["results"]) == len(cases)
        (output / "results.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"{report['passed']}/{len(cases)} passed; results: {output / 'results.json'}")
    return 0 if report["success"] else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ("adb", "serial", "probe", "shaders", "output"):
        parser.add_argument("--" + option, required=True)
    try:
        return run(parser.parse_args())
    except (OSError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
