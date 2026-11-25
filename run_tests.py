#!/usr/bin/env python3
"""桌面版自测：核心解密 / 归档流水线 / 打包后的 CLI 端到端。

测试自包含：用 test/tools/build_test_ncm.py 合成 NCM，不依赖外部音乐文件。

用法： python run_tests.py
"""
import os
import shutil
import subprocess
import sys

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

BASE = os.path.dirname(os.path.abspath(__file__))
BUILD = os.path.join(BASE, "build")
CLASSES = os.path.join(BUILD, "classes")
JAR = os.path.join(BUILD, "dist", "ncm-helper.jar")
TEST_DIR = os.path.join(BASE, "test")
SAMPLES = os.path.join(TEST_DIR, "samples")
TEST_OUT = os.path.join(TEST_DIR, "out")
WORK = os.path.join(TEST_DIR, "run")
CLI_DIR = os.path.join(TEST_DIR, "cli")

HOST_JVM = ["-Xmx512M", "-XX:SoftMaxHeapSize=128M"]
ENV = dict(os.environ, PYTHONIOENCODING="utf-8", PYTHONUTF8="1",
           JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8")


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
        sys.exit("找不到 %s：请安装 JDK 17+ 并设置 JAVA_HOME" % name)
    return found


def run(cmd, expect=0):
    print("$", " ".join(cmd))
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8",
                       errors="replace", env=ENV)
    out = (r.stdout or "") + (r.stderr or "")
    print(out)
    if r.returncode != expect:
        raise SystemExit("失败(exit=%d): %s" % (r.returncode, " ".join(cmd)))
    return out


def ensure_build():
    if not os.path.isfile(JAR):
        run([sys.executable, os.path.join(BASE, "build.py"), "--jar-only"])


def ensure_samples():
    if not os.path.isfile(os.path.join(SAMPLES, "sample.ncm")):
        run([sys.executable, os.path.join(TEST_DIR, "tools", "build_test_ncm.py"), SAMPLES])


def main():
    javac = find_tool("javac")
    java = find_tool("java")
    ensure_build()
    ensure_samples()

    shutil.rmtree(TEST_OUT, ignore_errors=True)
    os.makedirs(TEST_OUT, exist_ok=True)
    sources = [os.path.join(TEST_DIR, "java", f)
               for f in os.listdir(os.path.join(TEST_DIR, "java")) if f.endswith(".java")]
    run([javac, "-encoding", "UTF-8", "-cp", CLASSES, "-d", TEST_OUT] + sources)

    ncm = os.path.join(SAMPLES, "sample.ncm")
    flac = os.path.join(SAMPLES, "sample.flac")

    run([java] + HOST_JVM + ["-cp", CLASSES + os.pathsep + TEST_OUT, "TestCore", ncm, flac])
    run([java] + HOST_JVM + ["-cp", CLASSES + os.pathsep + TEST_OUT, "TestPipeline", WORK, ncm])

    # 打包后的 jar CLI 端到端
    shutil.rmtree(CLI_DIR, ignore_errors=True)
    src = os.path.join(CLI_DIR, "src")
    lib = os.path.join(CLI_DIR, "lib")
    os.makedirs(src)
    os.makedirs(lib)
    shutil.copy2(ncm, os.path.join(src, "sample.ncm"))
    out = run([java, "-jar", JAR, "--cli", "--source", src, "--library", lib, "--no-lyrics"])
    if "converted=1" not in out or "failed=0" not in out:
        raise SystemExit("CLI 端到端结果异常")
    converted = [os.path.join(dp, f) for dp, dn, fn in os.walk(lib) for f in fn if f.endswith(".flac")]
    if len(converted) != 1:
        raise SystemExit("CLI 未正确归档，文件数=%d" % len(converted))
    if os.path.exists(os.path.join(src, "sample.ncm")):
        raise SystemExit("CLI 未清理源 NCM")
    print("CLI 端到端: 转换 1 成功，源文件已清理")

    print("\n===== 全部测试通过 =====")


if __name__ == "__main__":
    main()
