#!/usr/bin/env python3
"""Generate a ready-made singleplayer test world for a PR's manual test.

Boots the zas-atmos dev server headless in a scratch game directory, builds a
fixture's structures through console commands, adds its gas, saves and stops,
then makes the world creative with cheats on. A second boot on a copy runs the
fixture's checks and fails if any expectation doesn't hold, so the installed
world is known to be in the state its test plan describes. The world goes into
the dev client's saves folder with the plan as TEST-PLAN.md, the headless
results appended.

Usage:
  python -I scripts/testworld/testworld.py <fixture> [--repo <checkout>]
                                           [--install <saves dir>]... [--skip-verify]

<fixture> is a module in scripts/testworld/fixtures/ (see the README there).
--repo is the checkout whose mods the server runs, by default this one; point
it at the PR's checkout. --install defaults to <this checkout>/zas-atmos/run/saves.
Needs only Python 3 and its standard library, plus the JDK the build uses.
"""

import argparse
import importlib.util
import os
import re
import shutil
import subprocess
import sys
import threading
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(HERE))
import nbt  # noqa: E402

MARKER = ".zas-testworld"
PORT = 25598

PROPERTIES = """\
level-name={level}
level-type=minecraft\\:flat
generate-structures=false
gamemode=creative
force-gamemode=false
difficulty=peaceful
spawn-monsters=false
spawn-animals=false
spawn-npcs=false
spawn-protection=0
online-mode=false
server-port={port}
view-distance=10
simulation-distance=10
enable-rcon=false
enable-query=false
motd=ZAS test world generator
"""

# Gamerules every test world gets: fixed noon, no weather, no mobs.
SETUP = [
    "gamerule doDaylightCycle false",
    "gamerule doWeatherCycle false",
    "gamerule doMobSpawning false",
    "gamerule doPatrolSpawning false",
    "gamerule doTraderSpawning false",
    "gamerule spawnRadius 0",
    "time set 6000",
    "weather clear",
]

ERRORS = re.compile(
    r"not loaded|Unknown or incomplete|Incorrect argument|Could not|Expected |<--\[HERE\]|Invalid|"
    r"No blocks were filled|Unknown block|Too many blocks",
    re.IGNORECASE,
)


def sign(x, y, z, rotation, *lines):
    """A standing oak sign with up to four lines. Rotation: 0 faces south, 4 west, 8 north, 12 east."""
    lines = list(lines) + [""] * (4 - len(lines))
    messages = ",".join("'\"%s\"'" % line for line in lines)
    return f"setblock {x} {y} {z} minecraft:oak_sign[rotation={rotation}]{{front_text:{{messages:[{messages}]}}}}"


class Server:
    """A dedicated server driven through its console."""

    def __init__(self, gamedir: Path, java_cmd, log_path: Path):
        self.lines = []
        self.lock = threading.Lock()
        self.log = open(log_path, "w", encoding="utf-8")
        self.proc = subprocess.Popen(
            java_cmd,
            cwd=gamedir,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
        threading.Thread(target=self._pump, daemon=True).start()

    def _pump(self):
        for line in self.proc.stdout:
            with self.lock:
                self.lines.append(line.rstrip("\n"))
            self.log.write(line)
            self.log.flush()

    def mark(self):
        with self.lock:
            return len(self.lines)

    def since(self, mark):
        with self.lock:
            return self.lines[mark:]

    def wait_for(self, pattern, start=0, timeout=300, count=1):
        rx = re.compile(pattern)
        deadline = time.time() + timeout
        while time.time() < deadline:
            hits = [line for line in self.since(start) if rx.search(line)]
            if len(hits) >= count:
                return hits
            if self.proc.poll() is not None:
                raise RuntimeError(f"server exited while waiting for {pattern!r}")
            time.sleep(0.25)
        raise TimeoutError(f"timed out waiting for {pattern!r} (x{count})")

    def run(self, command, settle=0.3):
        """Sends a console command; returns the log mark from before it."""
        m = self.mark()
        self.proc.stdin.write(command + "\n")
        self.proc.stdin.flush()
        time.sleep(settle)
        return m

    def output(self, mark):
        """Server-thread lines since a mark, without the log prefix."""
        return [line.split("]: ", 1)[-1] for line in self.since(mark) if "Server thread" in line]

    def stop(self, timeout=120):
        self.run("stop", settle=0)
        try:
            self.proc.wait(timeout)
        except subprocess.TimeoutExpired:
            self.proc.kill()
            raise
        time.sleep(0.5)
        self.log.close()


class Checks:
    """What a fixture's verify(check) receives: runs a command and checks its output."""

    def __init__(self, srv: Server):
        self.srv = srv
        self.results = []  # (label, command, lines, ok)
        self.failures = []

    def __call__(self, label, command, pattern=None, wait=2.0):
        """Runs a command, waits, and returns the first match of pattern in its output (or None).

        A pattern that matches nothing is a failure. With no pattern the output is only recorded.
        """
        mark = self.srv.run(command, settle=wait)
        lines = self.srv.output(mark)
        match = None
        if pattern is not None:
            rx = re.compile(pattern)
            match = next((m for m in (rx.search(line) for line in lines) if m), None)
        ok = pattern is None or match is not None
        self.results.append((label, command, lines, ok))
        if not ok:
            self.failures.append(f"{label}: `{command}` printed nothing matching {pattern!r}")
        return match

    def expect(self, label, condition):
        """Records a check across earlier results, such as two zone ids being equal or different."""
        self.results.append((label, None, [], bool(condition)))
        if not condition:
            self.failures.append(label)


def load_fixture(name):
    path = HERE / "fixtures" / f"{name}.py"
    if not path.exists():
        names = sorted(p.stem for p in (HERE / "fixtures").glob("*.py"))
        raise SystemExit(f"no fixture {name!r}; there are: {', '.join(names)}")
    spec = importlib.util.spec_from_file_location(f"fixtures.{name}", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def java_command(repo: Path):
    gradlew = repo / ("gradlew.bat" if os.name == "nt" else "gradlew")
    subprocess.run(
        [str(gradlew), "--console=plain", "-q", ":zas:classes", ":zas-atmos:classes",
         ":zas-atmos:prepareServerRun", ":zas-atmos:createServerLaunchScript"],
        cwd=repo,
        check=True,
    )
    if os.name == "nt":
        script = (repo / "zas-atmos/build/moddev/runServer.cmd").read_text(encoding="utf-8")
        line = next(l for l in script.splitlines() if "java" in l.lower() and l.startswith('"'))
        line = line.replace("%%", "%")  # cmd escaping
    else:
        script = (repo / "zas-atmos/build/moddev/runServer.sh").read_text(encoding="utf-8")
        line = next(l for l in script.splitlines() if "java" in l.lower() and l.startswith('"'))
    exe_end = line.index('"', 1)
    return [line[1:exe_end]] + line[exe_end + 1:].split()


def fresh_gamedir(path: Path, level: str):
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True)
    (path / "eula.txt").write_text("eula=true\n")
    (path / "server.properties").write_text(PROPERTIES.format(level=level, port=PORT))


def forceload(area, verb):
    x1, z1, x2, z2 = area
    return f"forceload {verb} {x1} {z1} {x2} {z2}"


def generate(fx, java, gamedir: Path):
    fresh_gamedir(gamedir, fx.LEVEL)
    srv = Server(gamedir, java, gamedir / "generate.log")
    srv.wait_for(r"Done \(", timeout=600)
    start = srv.mark()
    srv.run(forceload(fx.FORCELOAD, "add"), settle=1)
    time.sleep(8)  # let the forced chunks load and the atmos service capture them
    x, y, z = fx.SPAWN
    for command in SETUP + [f"setworldspawn {x} {y} {z}"] + fx.BUILD:
        srv.run(command, settle=0.15)
    time.sleep(2)
    srv.run("kill @e[type=!minecraft:player]")
    bad = [l for l in srv.since(start) if ERRORS.search(l) and "Server thread" in l]
    if bad:
        raise RuntimeError("building the fixture failed:\n" + "\n".join(bad))
    time.sleep(3)  # a few atmos ticks so the new geometry is mirrored
    m = srv.mark()
    for _, (gx, gy, gz), gas, moles in fx.GAS:
        srv.run(f"zas gas add {gx} {gy} {gz} {gas} {moles}", settle=0.2)
    if fx.GAS:
        srv.wait_for(r"Added .* into a zone", start=m, timeout=60, count=len(fx.GAS))
    time.sleep(3)
    zones = srv.output(srv.run("zas zones", settle=2))
    srv.run(forceload(fx.FORCELOAD, "remove"), settle=1)
    time.sleep(10)  # chunks beyond the spawn chunks unload, saving their zones' shares
    m = srv.run("save-all flush", settle=1)
    srv.wait_for(r"Saved the game", start=m, timeout=120)
    srv.stop()
    return zones


def patch_level(world: Path, level: str):
    name, root = nbt.load(world / "level.dat")
    data = root[b"Data"][1]
    data[b"allowCommands"] = (nbt.BYTE, 1)
    data[b"GameType"] = (nbt.INT, 1)
    data[b"Difficulty"] = (nbt.BYTE, 0)
    data[b"LevelName"] = (nbt.STRING, level.encode())
    data[b"raining"] = (nbt.BYTE, 0)
    data[b"thundering"] = (nbt.BYTE, 0)
    data[b"clearWeatherTime"] = (nbt.INT, 1_000_000)
    nbt.save(world / "level.dat", name, root)
    for leftover in ("level.dat_old", "session.lock"):
        (world / leftover).unlink(missing_ok=True)


def verify(fx, java, world: Path, gamedir: Path):
    fresh_gamedir(gamedir, fx.LEVEL)
    shutil.copytree(world, gamedir / fx.LEVEL)
    srv = Server(gamedir, java, gamedir / "verify.log")
    srv.wait_for(r"Done \(", timeout=600)
    time.sleep(4)
    checks = Checks(srv)
    try:
        fx.verify(checks)
    finally:
        srv.run("forceload remove all", settle=1)
        srv.stop()
    return checks


def report(checks: Checks):
    out = ["", "## Headless check", "",
           "Run by the generator on a copy of this world (dedicated server, no player, only the spawn",
           "chunks loaded unless a step force-loads more). The world you open is the unmodified original.",
           ""]
    for label, command, lines, ok in checks.results:
        out.append(f"- {'PASS' if ok else 'FAIL'}: {label}")
        if command:
            out.append(f"  - `/{command}`")
            out.extend(f"    - `{line}`" for line in lines)
    return "\n".join(out) + "\n"


def install(world: Path, plan: str, saves: Path, fixture: str):
    target = saves / world.name
    if target.exists():
        if not (target / MARKER).exists():
            raise RuntimeError(f"{target} exists and wasn't made by this script; not touching it")
        shutil.rmtree(target)
    saves.mkdir(parents=True, exist_ok=True)
    shutil.copytree(world, target, ignore=shutil.ignore_patterns("session.lock"))
    (target / MARKER).write_text(f"Generated by scripts/testworld/testworld.py {fixture}\n")
    (target / "TEST-PLAN.md").write_text(plan, encoding="utf-8")
    print("installed", target)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("fixture")
    ap.add_argument("--repo", type=Path, default=ROOT, help="checkout whose mods the server runs")
    ap.add_argument("--install", action="append", type=Path, help="saves folder to copy the world into")
    ap.add_argument("--skip-verify", action="store_true")
    a = ap.parse_args()

    fx = load_fixture(a.fixture)
    work = ROOT / "build" / "testworld" / a.fixture
    work.mkdir(parents=True, exist_ok=True)
    java = java_command(a.repo.resolve())

    print(f"generating '{fx.LEVEL}' with the mods from {a.repo}")
    zones = generate(fx, java, work / "gen")
    print("---- /zas zones after adding gas")
    print("\n".join(zones))

    out = work / "world" / fx.LEVEL
    if out.parent.exists():
        shutil.rmtree(out.parent)
    shutil.copytree(work / "gen" / fx.LEVEL, out, ignore=shutil.ignore_patterns("session.lock"))
    patch_level(out, fx.LEVEL)

    plan = fx.PLAN
    failed = False
    if not a.skip_verify:
        checks = verify(fx, java, out, work / "verify")
        plan += report(checks)
        print(report(checks))
        failed = bool(checks.failures)

    for d in ("gen", "verify"):
        for log in (work / d).glob("*.log"):
            shutil.copy2(log, work / log.name)
        shutil.rmtree(work / d, ignore_errors=True)
    (work / "TEST-PLAN.md").write_text(plan, encoding="utf-8")

    if failed:
        print("checks failed; the world was not installed. Logs and world in", work)
        for f in checks.failures:
            print("  -", f)
        sys.exit(1)
    for saves in a.install or [ROOT / "zas-atmos" / "run" / "saves"]:
        install(out, plan, saves, a.fixture)


if __name__ == "__main__":
    main()
