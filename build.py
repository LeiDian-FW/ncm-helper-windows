#!/usr/bin/env python3
"""构建 Windows 桌面版：编译 → 可执行 jar → jpackage 便携应用 → ZIP。

依赖：JDK 25+（含 jpackage），可用 JAVA_HOME 或本目录 jdk25/。

用法：
  python build.py              # 完整构建（jar + app-image + zip）
  python build.py --jar-only   # 只构建 jar（CI 测试用，更快）
"""
import os
import shutil
import subprocess
import sys
try:  # 某些 Windows 环境默认 cp1252，无法输出中文
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass
import zipfile

APP_NAME = "NCM Helper"
APP_VERSION = "1.1.0"
MIN_JDK = 17
MAIN_CLASS = "io.github.spwncm.ncmdesktop.App"

BASE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(BASE, "src", "main", "java")
BUILD = os.path.join(BASE, "build")
CLASSES = os.path.join(BUILD, "classes")
DIST = os.path.join(BUILD, "dist")
APP_DIR = os.path.join(BUILD, "app")
JAR = os.path.join(DIST, "ncm-helper.jar")
ICON = os.path.join(BASE, "assets", "icon.ico")
ZIP_OUT = os.path.join(BUILD, "NCM-Helper-Windows-%s.zip" % APP_VERSION)


def find_tool(name):
    exe = name + (".exe" if os.name == "nt" else "")
    local = os.path.join(BASE, "jdk25", "bin", exe)
    if os.path.isfile(local):
        return local
    jh = os.environ.get("JAVA_HOME")
    if jh:
        cand = os.path.join(jh, "bin", exe)
        if os.path.isfile(cand):
            return cand
    found = shutil.which(name)
    if not found:
        sys.exit("找不到 %s：请安装 JDK %d+ 并设置 JAVA_HOME" % (name, MIN_JDK))
    return found


def run(cmd):
    print("+", " ".join(cmd))
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        print(r.stdout)
        print(r.stderr)
        sys.exit(r.returncode)
    if (r.stdout or "").strip():
        print(r.stdout.strip())


def collect_sources():
    out = []
    for dp, dn, fn in os.walk(SRC):
        for f in fn:
            if f.endswith(".java"):
                out.append(os.path.join(dp, f))
    return out


def build_jar():
    for d in (CLASSES, DIST):
        shutil.rmtree(d, ignore_errors=True)
        os.makedirs(d, exist_ok=True)
    javac = find_tool("javac")
    jar_tool = find_tool("jar")
    sources = collect_sources()
    print("编译 %d 个 Java 文件…" % len(sources))
    run([javac, "-encoding", "UTF-8", "--release", "17", "-d", CLASSES] + sources)
    manifest = os.path.join(BUILD, "MANIFEST.MF")
    with open(manifest, "w", encoding="utf-8", newline="") as f:
        f.write("Manifest-Version: 1.0\r\nMain-Class: %s\r\n\r\n" % MAIN_CLASS)
    run([jar_tool, "--create", "--file", JAR, "--manifest", manifest, "-C", CLASSES, "."])
    print("jar: %s (%.1f KB)" % (JAR, os.path.getsize(JAR) / 1024))


def build_app_image():
    jpackage = find_tool("jpackage")
    shutil.rmtree(APP_DIR, ignore_errors=True)
    os.makedirs(APP_DIR, exist_ok=True)
    cmd = [
        jpackage,
        "--type", "app-image",
        "--name", APP_NAME,
        "--app-version", APP_VERSION,
        "--input", DIST,
        "--main-jar", os.path.basename(JAR),
        "--main-class", MAIN_CLASS,
        "--dest", APP_DIR,
        "--java-options", "-Dfile.encoding=UTF-8",
        "--add-modules", "java.base,java.desktop",
        "--vendor", "spwncm",
        "--description", "网易云 NCM 自动处理工具",
    ]
    if os.path.isfile(ICON):
        cmd += ["--icon", ICON]
    run(cmd)
    app_path = os.path.join(APP_DIR, APP_NAME)
    print("应用目录: %s" % app_path)
    return app_path


def build_zip(app_path):
    if os.path.exists(ZIP_OUT):
        os.remove(ZIP_OUT)
    root_name = os.path.basename(app_path)
    with zipfile.ZipFile(ZIP_OUT, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for dp, dn, fn in os.walk(app_path):
            for f in fn:
                full = os.path.join(dp, f)
                rel = os.path.relpath(full, app_path)
                z.write(full, os.path.join(root_name, rel))
    print("便携 ZIP: %s (%.1f MB)" % (ZIP_OUT, os.path.getsize(ZIP_OUT) / 1048576))
    return ZIP_OUT


def main():
    jar_only = "--jar-only" in sys.argv
    build_jar()
    if jar_only:
        return
    app_path = build_app_image()
    build_zip(app_path)


if __name__ == "__main__":
    main()
