#!/usr/bin/env python3
"""Smoke test for the minified release APK on an emulator (run by CI; needs `adb` on PATH).

R8 problems (missing keep rules, serializers, Hilt) only show up at run time, so this installs the
release APK, walks onboarding -> guest -> Home -> a popular route's search -> results, and fails on any crash.
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


def screen_size() -> tuple[int, int]:
    m = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size"))
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)


def swipe(up: bool) -> None:
    """Scrolls about half a screen, using the emulator's real size."""
    w, h = screen_size()
    a, b = (int(h * 0.75), int(h * 0.35)) if up else (int(h * 0.35), int(h * 0.75))
    adb("shell", "input", "swipe", str(w // 2), str(a), str(w // 2), str(b), "400")
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
    out = adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
    total = re.search(r"TotalTime: (\d+)", out)
    print(f"- cold start: {total.group(1) if total else '?'} ms (emulator, no GPU)", flush=True)
    step("onboarding: skip", ["বাদ দিন", "Skip"])
    print("- login (if shown): continue as guest", flush=True)
    home = ["কোথায় যেতে চান?", "Where would you like to go?"]
    guest = ["অতিথি হিসেবে চালিয়ে যান", "Continue as guest"]
    node = wait_for(home + guest)
    if (node.get("text") or "") in guest:
        tap(node)
    step("home is shown", home, press=False)
    print("- pick the first popular route (e.g. Dhaka → Cox's Bazar)", flush=True)
    for _ in range(6):
        node = find_containing(screen(), ["→"])
        if node is not None:
            tap(node)
            break
        swipe(up=True)
    else:
        sys.exit(f"Popular route not found on Home. On screen: {[t for t in texts(screen()) if t]}")
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
