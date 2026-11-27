#!/usr/bin/env python3
"""Smoke test for the minified release APK on an emulator (run by CI; needs `adb` on PATH).

R8 problems (missing keep rules, serializers, Hilt) only show up at run time, so this installs the
release APK, walks onboarding -> guest -> Home -> a bus search -> results, and fails on any crash.
Usage: smoke_release.py path/to/app-release.apk
"""

import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "com.jatri.app"


def adb(*args: str, check: bool = True) -> str:
    return subprocess.run(["adb", *args], check=check, capture_output=True, text=True).stdout


def screen() -> ET.Element:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    return ET.fromstring(adb("exec-out", "cat", "/sdcard/ui.xml"))


def texts(root: ET.Element) -> list[str]:
    return [n.get("text") or n.get("content-desc") or "" for n in root.iter("node")]


def find(root: ET.Element, labels: list[str]) -> ET.Element | None:
    for n in root.iter("node"):
        if (n.get("text") or n.get("content-desc") or "").strip() in labels:
            return n
    return None


def find_containing(root: ET.Element, parts: list[str]) -> ET.Element | None:
    for n in root.iter("node"):
        t = n.get("text") or n.get("content-desc") or ""
        if any(p in t for p in parts) and n.get("clickable") == "true":
            return n
    # Compose often puts the click on the parent of the text node.
    for parent in root.iter("node"):
        if parent.get("clickable") != "true":
            continue
        for n in parent.iter("node"):
            t = n.get("text") or n.get("content-desc") or ""
            if any(p in t for p in parts):
                return parent
    return None


def swipe(up: bool) -> None:
    a, b = ("1600", "600") if up else ("600", "1600")
    adb("shell", "input", "swipe", "540", a, "540", b, "300")
    time.sleep(1)


def crashed() -> str | None:
    log = adb("logcat", "-d", "-b", "crash", check=False)
    return log if PKG in log else None


def wait_for(labels: list[str], timeout: float = 25) -> ET.Element:
    end = time.time() + timeout
    root = None
    while time.time() < end:
        if (c := crashed()) is not None:
            sys.exit(f"App crashed:\n{c}")
        root = screen()
        if (n := find(root, labels)) is not None:
            return n
        time.sleep(1.5)
    sys.exit(f"Timed out waiting for {labels}. On screen: {[t for t in texts(root) if t]}")


def tap(node: ET.Element) -> None:
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(1.5)


def step(name: str, labels: list[str], press: bool = True) -> None:
    print(f"- {name}", flush=True)
    node = wait_for(labels)
    if press:
        tap(node)


def main(apk: str) -> None:
    adb("logcat", "-c", "-b", "all", check=False)
    adb("install", "-r", apk)
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
    step("onboarding: skip", ["বাদ দিন", "Skip"])
    step("login: continue as guest", ["অতিথি হিসেবে চালিয়ে যান", "Continue as guest"])
    step("home is shown", ["কোথায় যেতে চান?", "Where would you like to go?"], press=False)
    print("- pick the Dhaka → Cox's Bazar popular route", flush=True)
    for _ in range(6):
        node = find_containing(screen(), ["কক্সবাজার", "Cox"])
        if node is not None:
            tap(node)
            break
        swipe(up=True)
    else:
        sys.exit("Popular route not found on Home")
    for _ in range(6):
        swipe(up=False)
    print("- search", flush=True)
    node = wait_for(["টিকিট খুঁজুন", "Search Tickets", "যাত্রার অপশন", "Available trips"])
    if (node.get("text") or "") in ("টিকিট খুঁজুন", "Search Tickets"):
        tap(node)
    step("results are shown", ["যাত্রার অপশন", "Available trips"], press=False)
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(2)
    if (c := crashed()) is not None:
        sys.exit(f"App crashed:\n{c}")
    pid = adb("shell", "pidof", PKG, check=False).strip()
    if not pid:
        sys.exit("App is not running at the end of the smoke test")
    print("Release smoke test passed.")


if __name__ == "__main__":
    main(sys.argv[1])
