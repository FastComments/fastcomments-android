#!/usr/bin/env python3
"""
Dual-emulator instrumented test orchestrator for FastComments Android.

Boots a sync server, builds APKs via Gradle, installs on two emulators,
and runs UserA/UserB test classes in parallel. Tests coordinate via
the sync server at localhost:9999.

Usage:
    python3 run_dual_emu_tests.py [--e2e-key KEY]
"""

import argparse
import http.server
import json
import os
import shlex
import subprocess
import sys
import threading
import time
from collections import defaultdict

SYNC_PORT = 9999


class SyncServer:
    """Tiny HTTP server for test coordination between two emulator processes."""

    def __init__(self, port):
        self.port = port
        self.ready = defaultdict(dict)      # round -> {role: True}
        self.data = {}                       # round -> json data
        self.waiters = defaultdict(list)     # (round, role) -> [event]
        self.lock = threading.Lock()
        self.server = None

    def start(self):
        handler = self._make_handler()
        self.server = http.server.ThreadingHTTPServer(("", self.port), handler)
        thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        thread.start()
        print(f"[sync] Server started on port {self.port}")

    def stop(self):
        if self.server:
            self.server.shutdown()

    def _make_handler(self):
        sync = self

        class Handler(http.server.BaseHTTPRequestHandler):
            def log_message(self, format, *args):
                print(f"[sync] {args[0]}")

            def do_POST(self):
                path = self.path.split("?")[0]
                params = dict(p.split("=") for p in self.path.split("?")[1].split("&")) if "?" in self.path else {}

                if path == "/ready":
                    role = params.get("role", "")
                    round_id = params.get("round", "default")
                    with sync.lock:
                        sync.ready[round_id][role] = True
                        key = (round_id, role)
                        for evt in sync.waiters.get(key, []):
                            evt.set()
                        sync.waiters[key] = []
                    self.send_response(200)
                    self.end_headers()
                    self.wfile.write(b'{"ok":true}')

                elif path == "/data":
                    round_id = params.get("round", "default")
                    length = int(self.headers.get("Content-Length", 0))
                    body = self.rfile.read(length) if length else b"{}"
                    with sync.lock:
                        sync.data[round_id] = json.loads(body)
                    self.send_response(200)
                    self.end_headers()
                    self.wfile.write(b'{"ok":true}')

                else:
                    self.send_response(404)
                    self.end_headers()

            def do_GET(self):
                path = self.path.split("?")[0]
                params = dict(p.split("=") for p in self.path.split("?")[1].split("&")) if "?" in self.path else {}

                if path == "/wait":
                    wait_for = params.get("waitFor", "")
                    round_id = params.get("round", "default")
                    timeout = float(params.get("timeout", "120"))

                    evt = threading.Event()
                    with sync.lock:
                        if sync.ready.get(round_id, {}).get(wait_for):
                            evt.set()
                        else:
                            sync.waiters[(round_id, wait_for)].append(evt)

                    ok = evt.wait(timeout=timeout)
                    self.send_response(200 if ok else 408)
                    self.end_headers()
                    self.wfile.write(json.dumps({"ok": ok}).encode())

                elif path == "/data":
                    round_id = params.get("round", "default")
                    with sync.lock:
                        d = sync.data.get(round_id, {})
                    self.send_response(200)
                    self.end_headers()
                    self.wfile.write(json.dumps(d).encode())

                elif path == "/health":
                    self.send_response(200)
                    self.end_headers()
                    self.wfile.write(b'{"ok":true}')

                else:
                    self.send_response(404)
                    self.end_headers()

        return Handler


def get_emulators():
    """Detect running emulators via adb devices."""
    result = subprocess.run(["adb", "devices"], capture_output=True, text=True)
    emulators = []
    for line in result.stdout.strip().split("\n")[1:]:
        parts = line.strip().split()
        if len(parts) >= 2 and parts[1] == "device" and parts[0].startswith("emulator-"):
            emulators.append(parts[0])
    return emulators


def get_avds():
    """List available AVDs."""
    result = subprocess.run(["emulator", "-list-avds"], capture_output=True, text=True)
    return [line.strip() for line in result.stdout.strip().split("\n") if line.strip()]


def start_emulators():
    """Start emulators if fewer than 2 are running. Returns list of serial IDs."""
    emulators = get_emulators()
    if len(emulators) >= 2:
        return emulators

    avds = get_avds()
    # Figure out which AVDs are already booted by checking running emulator processes
    running_avds = set()
    for serial in emulators:
        result = subprocess.run(
            ["adb", "-s", serial, "shell", "getprop", "ro.boot.qemu.avd_name"],
            capture_output=True, text=True
        )
        name = result.stdout.strip()
        if name:
            running_avds.add(name)

    available = [a for a in avds if a not in running_avds]
    needed = 2 - len(emulators)

    if len(available) < needed:
        print(f"ERROR: Need {needed} more AVD(s) but only {len(available)} available: {available}")
        print("Create AVDs with Android Studio or avdmanager.")
        sys.exit(1)

    launched_procs = []
    for avd in available[:needed]:
        print(f"[emu] Starting emulator: {avd}")
        proc = subprocess.Popen(
            ["emulator", "-avd", avd, "-read-only"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
        )
        launched_procs.append(proc)

    # Wait for emulators to appear in adb and finish booting
    print("[emu] Waiting for emulators to boot...")
    for _ in range(60):
        emulators = get_emulators()
        if len(emulators) >= 2:
            # Check all are fully booted
            all_booted = True
            for serial in emulators[:2]:
                result = subprocess.run(
                    ["adb", "-s", serial, "shell", "getprop", "sys.boot_completed"],
                    capture_output=True, text=True
                )
                if result.stdout.strip() != "1":
                    all_booted = False
                    break
            if all_booted:
                print(f"[emu] {len(emulators)} emulators ready")
                return emulators
        time.sleep(3)

    print("ERROR: Timed out waiting for emulators to boot")
    sys.exit(1)


def build_apks():
    """Build debug app and androidTest APKs via Gradle."""
    print("[build] Building APKs...")
    result = subprocess.run(
        ["./gradlew", ":app:assembleDebug", ":app:assembleDebugAndroidTest"],
        capture_output=True, text=True, timeout=300
    )
    if result.returncode != 0:
        print("[build] FAILED")
        print(result.stdout[-2000:] if len(result.stdout) > 2000 else result.stdout)
        print(result.stderr[-2000:] if len(result.stderr) > 2000 else result.stderr)
        sys.exit(1)
    print("[build] APKs built successfully")


def install_apks(serial):
    """Install app and test APKs on an emulator."""
    app_apk = "app/build/outputs/apk/debug/app-debug.apk"
    test_apk = "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

    for apk in [app_apk, test_apk]:
        if not os.path.exists(apk):
            print(f"[install] APK not found: {apk}")
            sys.exit(1)

        result = subprocess.run(
            ["adb", "-s", serial, "install", "-r", "-t", apk],
            capture_output=True, text=True, timeout=60
        )
        if result.returncode != 0:
            print(f"[install] Failed to install {apk} on {serial}")
            print(result.stderr)
            sys.exit(1)

    print(f"[install] APKs installed on {serial}")


def run_tests(serial, role, test_class, e2e_key):
    """Run instrumented tests on a specific emulator."""
    # Build the shell command as a single string because adb shell joins
    # list args with spaces, breaking values that contain spaces.
    shell_cmd = " ".join([
        "am", "instrument",
        "-w", "-r",
        "-e", "class", f"com.fastcomments.{test_class}",
        "-e", "FC_ROLE", shlex.quote(role),
        "-e", "FC_SYNC_URL", shlex.quote("http://10.0.2.2:9999"),
        "-e", "E2E_API_KEY", shlex.quote(e2e_key),
        "com.fastcomments.test/androidx.test.runner.AndroidJUnitRunner",
    ])

    cmd = ["adb", "-s", serial, "shell", shell_cmd]

    print(f"[{role}] Starting on {serial}...")
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    return proc


def stream_output(proc, prefix, failure_flag):
    """Stream process output with a prefix, filtering for relevant lines.
    Sets failure_flag[0] = True if test failures are detected.
    Note: INSTRUMENTATION_CODE: -1 means SUCCESS in Android instrumentation."""
    for line in iter(proc.stdout.readline, b""):
        text = line.decode("utf-8", errors="replace").rstrip()
        if "FAILURES!!!" in text:
            failure_flag[0] = True
        if any(kw in text for kw in [
            "INSTRUMENTATION_STATUS", "Test", "Error", "FAIL",
            "OK", "Exception", "assert", "pass", "INSTRUMENTATION_CODE"
        ]):
            print(f"[{prefix}] {text}")


def main():
    parser = argparse.ArgumentParser(description="Run dual-emulator live event tests")
    parser.add_argument("--e2e-key", default="T0ph B3st", help="E2E API key")
    args = parser.parse_args()

    # Start emulators if needed (with visible windows)
    emulators = start_emulators()

    emu_a, emu_b = emulators[0], emulators[1]
    print(f"[main] Emulator A: {emu_a}")
    print(f"[main] Emulator B: {emu_b}")

    # Build
    build_apks()

    # Install on both emulators in parallel
    install_threads = []
    for serial in [emu_a, emu_b]:
        t = threading.Thread(target=install_apks, args=(serial,))
        t.start()
        install_threads.append(t)
    for t in install_threads:
        t.join()

    # Start sync server
    sync = SyncServer(SYNC_PORT)
    sync.start()

    try:
        # Run UserA and UserB tests in parallel
        proc_a = run_tests(emu_a, "userA", "LiveEventUserA_UITests", args.e2e_key)
        proc_b = run_tests(emu_b, "userB", "LiveEventUserB_UITests", args.e2e_key)

        # Stream output from both, tracking failures
        fail_a = [False]
        fail_b = [False]
        thread_a = threading.Thread(target=stream_output, args=(proc_a, "UserA", fail_a), daemon=True)
        thread_b = threading.Thread(target=stream_output, args=(proc_b, "UserB", fail_b), daemon=True)
        thread_a.start()
        thread_b.start()

        # Wait for both to finish
        rc_a = proc_a.wait()
        rc_b = proc_b.wait()

        thread_a.join(timeout=5)
        thread_b.join(timeout=5)

        # am instrument often returns 0 even on failure — check output too
        passed = not fail_a[0] and not fail_b[0] and rc_a == 0 and rc_b == 0

        print(f"\n{'='*60}")
        print(f"User A: exit={rc_a} failed={fail_a[0]}")
        print(f"User B: exit={rc_b} failed={fail_b[0]}")
        if passed:
            print("ALL DUAL-EMULATOR TESTS PASSED")
        else:
            print("SOME TESTS FAILED")
        print(f"{'='*60}")

        sys.exit(0 if passed else 1)

    finally:
        sync.stop()


if __name__ == "__main__":
    main()
