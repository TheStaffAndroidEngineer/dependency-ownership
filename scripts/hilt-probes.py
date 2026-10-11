#!/usr/bin/env python3
"""Verify Hilt diagnostics in a disposable copy; never edit canonical sources.

The positive control must assemble before either deliberate defect is tested.
Each negative witness requires the specific Hilt diagnostic and SessionOwner
type, rather than counting an arbitrary failed Gradle invocation as success.
"""
from pathlib import Path
import datetime
import hashlib
import json
import re
import shutil
import subprocess


ROOT = Path(__file__).resolve().parents[1]
MODULE = Path(
    "hilt-app/src/main/kotlin/staff/android/ownership/hiltapp/SessionModule.kt"
)
WORK = ROOT / "build/hilt-probe-work"
OUT = ROOT / "evidence/hilt-probes" / datetime.datetime.now(
    datetime.timezone.utc
).strftime("%Y%m%dT%H%M%SZ")
EXCLUDED = {"build", ".gradle", ".kotlin", "evidence", "__pycache__", ".git"}
COMMAND = [
    "./gradlew", ":hilt-app:assembleDebug", "--no-configuration-cache",
    "--no-build-cache", "--rerun-tasks", "--console=plain",
]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def inputs(root):
    """Include source, manifests, catalog, wrapper and build configuration."""
    result = {}
    for path in sorted(root.rglob("*")):
        relative = path.relative_to(root)
        if not path.is_file() or any(part in EXCLUDED for part in relative.parts):
            continue
        if (path.suffix in {".kt", ".java", ".kts", ".xml", ".toml", ".properties"}
                or path.name in {"gradlew", "gradlew.bat", "gradle-wrapper.jar"}):
            result[str(relative)] = sha(path)
    return result


def write_report(report):
    (OUT / "results.json").write_text(json.dumps(report, indent=2) + "\n")


def main():
    OUT.mkdir(parents=True, exist_ok=False)
    original_inputs = inputs(ROOT)
    report = {
        "status": "RUNNING",
        "scope": "Hilt generated-graph validation for the actual app: positive assembly, "
                 "missing provider, duplicate provider. Not runtime scoping, object "
                 "revocation, performance, or a guarantee for all consumer graphs.",
        "canonical_inputs": original_inputs,
        "script_sha256": sha(Path(__file__)),
        "witnesses": [],
    }
    write_report(report)
    try:
        if WORK.exists():
            shutil.rmtree(WORK)
        shutil.copytree(ROOT, WORK, ignore=shutil.ignore_patterns(*EXCLUDED))
        original = (WORK / MODULE).read_text()
        provider = "fun owner(): SessionOwner = createSessionOwner()"
        if original.count(provider) != 1 or original.count("@Provides") != 1:
            raise AssertionError("SessionModule provider shape changed; review mutation")
        missing = original.replace("    @Provides\n", "", 1)
        if missing == original:
            raise AssertionError("Missing-provider mutation did not remove @Provides")
        # The second provider is deliberately in the same installed module.
        # Both return the same unqualified binding key: SessionOwner.
        closing = original.rfind("}")
        duplicate = original[:closing] + (
            "    @Provides\n"
            "    @Singleton\n"
            "    fun duplicateOwner(): SessionOwner = createSessionOwner()\n"
        ) + original[closing:]
        cases = [
            ("01-reference-graph", original, None),
            ("02-missing-session-owner", missing, "MissingBinding"),
            ("03-duplicate-session-owner", duplicate, "DuplicateBindings"),
        ]
        for name, source, expected in cases:
            (WORK / MODULE).write_text(source)
            snapshot = OUT / name
            snapshot.mkdir()
            manifest = inputs(WORK)
            for relative, expected_hash in manifest.items():
                destination = snapshot / "inputs" / relative
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(WORK / relative, destination)
                if sha(destination) != expected_hash:
                    raise AssertionError(f"Input snapshot mismatch: {relative}")
            (snapshot / "inputs.json").write_text(json.dumps(manifest, indent=2) + "\n")
            log = snapshot / "gradle.log"
            with log.open("w") as output:
                run = subprocess.run(
                    COMMAND, cwd=WORK, stdout=output, stderr=subprocess.STDOUT,
                    text=True, timeout=900,
                )
            output = log.read_text()
            diagnostic = [] if expected is None else [
                line for line in output.splitlines()
                if re.search(r"\[Dagger/" + re.escape(expected) + r"\].*SessionOwner", line)
            ]
            passed = (run.returncode == 0 if expected is None else
                      run.returncode != 0 and bool(diagnostic))
            row = {
                "name": name, "command": COMMAND, "exit_code": run.returncode,
                "expected_success": expected is None,
                "expected_diagnostic": expected, "diagnostic": diagnostic,
                "status": "PASS" if passed else "FAIL",
                "input_count": len(manifest),
                "input_manifest": str((snapshot / "inputs.json").relative_to(OUT)),
                "input_manifest_sha256": sha(snapshot / "inputs.json"),
                "changed_module_sha256": manifest[str(MODULE)],
                "log": str(log.relative_to(OUT)), "log_sha256": sha(log),
            }
            report["witnesses"].append(row)
            write_report(report)
            print(f"{name}: {row['status']}", flush=True)
            if not passed:
                raise AssertionError(f"{name}: expected Hilt witness absent; inspect {log}")
        if inputs(ROOT) != original_inputs:
            raise AssertionError("Canonical build/source inputs changed during probe execution")
        report["canonical_sources_unchanged"] = True
        report["status"] = "PASS"
        write_report(report)
        print(OUT, flush=True)
    except Exception as error:
        report["status"] = "FAIL"
        report["error"] = str(error)
        report["canonical_sources_unchanged"] = inputs(ROOT) == original_inputs
        write_report(report)
        raise


if __name__ == "__main__":
    main()
