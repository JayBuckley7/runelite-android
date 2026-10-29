#!/usr/bin/env python3
"""Run the actual compatibility implementations on the host JDK without an APK.

Android-only focus, clipboard and AudioTrack probes are TestRuntimeCoreContracts
and TestAudioLineContracts; they must additionally run on an emulator/device.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--output", type=Path)
    parser.add_argument("--h2-jar", type=Path, help="Pinned H2 runtime JAR for the actual JDBC timeout probe")
    args = parser.parse_args()
    repo = Path(__file__).resolve().parent.parent
    java = Path(args.java_home) / "bin" if args.java_home else None
    suffix = ".exe" if os.name == "nt" else ""

    def executable(name):
        found = str(java / (name + suffix)) if java else shutil.which(name)
        if not found or not Path(found).exists():
            raise SystemExit(f"{name} unavailable; set --java-home to JDK 11+")
        return found

    logs = []

    def run(label, name, *arguments):
        result = subprocess.run([executable(name), *map(str, arguments)], cwd=repo,
                                text=True, encoding="utf-8", errors="replace",
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=90)
        message = f"[{label}] exit={result.returncode}\n{result.stdout}"
        print(message, end="", flush=True)
        logs.append(message)
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text("\n".join(logs), encoding="utf-8")
        if result.returncode:
            raise SystemExit(result.returncode)

    with tempfile.TemporaryDirectory(prefix="runtime-contracts-") as directory:
        root = Path(directory)
        server, client, management, tests = (root / name for name in ("server", "client", "management", "tests"))
        for folder in (server, client, management, tests):
            folder.mkdir()
        source = repo / "android-awt/src/main/java"
        common = ["-encoding", "UTF-8", "-source", "8", "-target", "8"]
        run("compile HTTP server", "javac", *common, "-d", server, *sorted((source / "com/sun/net/httpserver").glob("*.java")))
        run("compile HTTP client", "javac", *common, "-d", client, *sorted((source / "java/net/http").glob("*.java")))
        run("compile management", "javac", *common, "-d", management,
            *sorted((source / "javax/management").glob("*.java")), source / "net/runelite/awt/impl/StubMBeanServer.java")
        run("compile HTTP tests", "javac", "-encoding", "UTF-8", "-d", tests,
            *[repo / "tools-src" / name for name in ("TestHttpServerContracts.java", "TestJavaHttpContracts.java", "TestJavaHttpTlsContracts.java")])
        run("compile management tests", "javac", "-encoding", "UTF-8", "-cp", management, "-d", tests,
            repo / "tools-src/TestManagementContracts.java")
        run("HTTP server", "java", "--patch-module", f"jdk.httpserver={server}", "-cp", tests, "TestHttpServerContracts")
        run("HTTP client", "java", "--patch-module", f"java.net.http={client}", "-cp", tests, "TestJavaHttpContracts")
        certificate = root / "test.p12"
        run("local TLS fixture", "keytool", "-genkeypair", "-alias", "test", "-storetype", "PKCS12", "-keystore", certificate,
            "-storepass", "test-only", "-keypass", "test-only", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost",
            "-keyalg", "RSA", "-validity", "1")
        run("TLS negative cases", "java", "--patch-module", f"java.net.http={client}", "-cp", tests, "TestJavaHttpTlsContracts", certificate)
        run("management", "java", "--patch-module", f"java.management={management}", "--add-exports",
            "java.management/net.runelite.awt.impl=ALL-UNNAMED", "-cp", tests, "TestManagementContracts")
        if args.h2_jar:
            h2 = root / "h2"
            h2.mkdir()
            h2_jar = root / "h2-runtime.jar"
            shutil.copyfile(args.h2_jar, h2_jar)
            run("compile H2 contracts", "javac", *common, "-cp", h2_jar, "-d", h2,
                repo / "android/src/androidMain/java/org/h2/jdbcx/JdbcDataSource.java",
                repo / "tools-src/TestH2Contracts.java")
            run("H2 contracts", "java", "-cp", str(h2) + os.pathsep + str(h2_jar), "TestH2Contracts")


if __name__ == "__main__":
    main()
