#!/usr/bin/env python3
"""Arena (com.arena.aid) RSI v2 Pipeline: pickup, map, verify, checkpoint, ci."""
import json
import os
import pathlib
import re
import subprocess
import sys
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
REPO = "NikitHamal/arenaaid"
EMOJI_RE = re.compile(r"[\U0001F300-\U0001FAFF\u2600-\u27BF]")


def run(cmd: str, check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, shell=True, cwd=ROOT, text=True, capture_output=True, check=check)


def verify() -> int:
    java_root = ROOT / "app/src/main/java"
    res_root = ROOT / "app/src/main/res"
    bad = []

    if java_root.exists():
        for f in java_root.rglob("*.kt"):
            text = f.read_text(encoding="utf-8")
            lines = text.splitlines()
            if EMOJI_RE.search(text):
                bad.append(f"{f.relative_to(ROOT)}: contains banned emoji character")
            for idx, line in enumerate(lines):
                stripped = line.lstrip()
                if stripped.startswith(("import ", "//", "*", "/*")):
                    continue
                if re.search(r"\.shadow\s*\(", line):
                    bad.append(f"{f.relative_to(ROOT)}:{idx+1}: banned .shadow() modifier")
                if re.search(r"Brush\.(linear|radial|sweep|vertical|horizontal)Gradient", line):
                    bad.append(f"{f.relative_to(ROOT)}:{idx+1}: banned gradient Brush")
                if re.search(r"\bModalBottomSheet\(", line) and f.name not in ("ArenaSheet.kt", "ArenaSheetAndDialog.kt"):
                    bad.append(f"{f.relative_to(ROOT)}:{idx+1}: raw ModalBottomSheet, use ArenaModalSheet")
                if re.search(r"(?<!neb)(?<!arena)(?<![A-Za-z])horizontalScroll\(", line) and f.name != "SafeScroll.kt":
                    bad.append(f"{f.relative_to(ROOT)}:{idx+1}: bare horizontalScroll, use arenaHorizontalScroll")
                if re.search(r"\b(LazyRow|HorizontalPager)\(", line) and "fun " not in line:
                    window = "\n".join(lines[idx:idx + 9])
                    if "boundedWidth()" not in window:
                        bad.append(f"{f.relative_to(ROOT)}:{idx+1}: LazyRow/HorizontalPager without boundedWidth()")

    if res_root.exists():
        for f in res_root.rglob("*.xml"):
            text = f.read_text(encoding="utf-8")
            if EMOJI_RE.search(text):
                bad.append(f"{f.relative_to(ROOT)}: XML contains banned emoji character")

    if bad:
        print("M3X / RSI verification FAILED:")
        for item in bad:
            print("  -", item)
        return 1
    print("RSI verify: M3X guards, scrollers, sheets, shadow/gradient/emoji bans ALL OK.")
    return 0


def check_staged_secrets() -> int:
    diff = run("git diff --cached", check=False).stdout
    if re.search(r"ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}", diff):
        print("ABORT: Staged diff contains GitHub PAT!")
        return 1
    return 0


def checkpoint(msg: str) -> int:
    if verify() != 0:
        return 1
    run("git add -A")
    if check_staged_secrets() != 0:
        return 1
    status = run("git status --porcelain", check=False).stdout.strip()
    if not status:
        print("Nothing new to commit.")
    else:
        run(f'git commit -m "{msg}"')
    env = os.environ.copy()
    env["GIT_ASKPASS"] = "/tmp/gitenv.sh"
    env["GIT_TERMINAL_PROMPT"] = "0"
    push = subprocess.run(
        ["git", "push", "-u", "origin", "main"],
        cwd=ROOT,
        env=env,
        text=True,
        capture_output=True,
    )
    print(push.stdout or push.stderr)
    return push.returncode


def ci_status() -> int:
    token = pathlib.Path("/tmp/gitenv.sh").read_text().splitlines()[-1].replace("echo ", "").strip()
    headers = {"Authorization": f"token {token}", "Accept": "application/vnd.github+json"}
    req = urllib.request.Request(
        f"https://api.github.com/repos/{REPO}/actions/runs?branch=main&per_page=3",
        headers=headers,
    )
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode())
    runs = data.get("workflow_runs", [])
    if not runs:
        print("No CI runs yet.")
        return 0
    for r in runs[:1]:
        print(f"Run #{r['run_number']} [{r['status']}/{r.get('conclusion')}] {r['name']} ({r['head_sha'][:7]}) -> {r['html_url']}")
        jobs_req = urllib.request.Request(r["jobs_url"], headers=headers)
        with urllib.request.urlopen(jobs_req) as jresp:
            jobs = json.loads(jresp.read().decode()).get("jobs", [])
        for j in jobs:
            for s in j.get("steps", []):
                if s.get("status") == "in_progress" or s.get("conclusion") == "failure":
                    print(f"   Step [{s.get('status')}/{s.get('conclusion')}]: {s.get('name')}")
            if j.get("conclusion") == "failure":
                cmd_log = (
                    f'LOG_URL=$(curl -sI -H "Authorization: token {token}" '
                    f'"https://api.github.com/repos/{REPO}/actions/jobs/{j["id"]}/logs" '
                    f'| grep -i "^location:" | awk \'{{print $2}}\' | tr -d \'\\r\'); '
                    f'curl -s "$LOG_URL" | grep -E "e: |FAILURE:|What went wrong|> Task .* FAILED" -A 3 | tail -n 45'
                )
                out = run(cmd_log, check=False).stdout
                print("--- FAILED JOB LOG TAIL ---")
                print(out)
    return 0


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "verify"
    if cmd == "verify":
        sys.exit(verify())
    elif cmd == "checkpoint":
        sys.exit(checkpoint(sys.argv[2] if len(sys.argv) > 2 else "Update Arena Android app"))
    elif cmd == "ci":
        sys.exit(ci_status())
    elif cmd == "pickup":
        print(run("git status -sb", check=False).stdout)
        sys.exit(ci_status())
    else:
        print(f"Unknown command: {cmd}")
        sys.exit(1)
