#!/usr/bin/env python3
"""Replace GPS latitude/longitude in Car Scanner fixture CSVs with synthetic coordinates.

Run before publishing fixtures to a public repo. Preserves column layout for parser tests.
"""
from __future__ import annotations

import csv
import re
import sys
from pathlib import Path

FAKE_LAT = "45.0000000"
FAKE_LON = "-93.0000000"

REPO_ROOT = Path(__file__).resolve().parents[1]
FIXTURES = REPO_ROOT / "backend/src/test/resources/fixtures"

LAT_LON_TAIL = re.compile(
    r'("?-?\d+\.\d+"?)\s*;\s*("?-?\d+\.\d+"?)\s*;\s*$'
)


def _norm_header(name: str) -> str:
    return name.strip().strip('"').lower()


def _is_lat_col(name: str) -> bool:
    n = _norm_header(name)
    return n == "latitude" or n.endswith(" latitude")


def _is_lon_col(name: str) -> bool:
    n = _norm_header(name)
    return n in ("longtitude", "longitude", "longtitude ", "longitude ")


def redact_comma_csv(path: Path) -> bool:
    raw = path.read_text(encoding="utf-8")
    if "latitude" not in raw.lower():
        return False

    reader = csv.reader(raw.splitlines())
    rows = list(reader)
    if not rows:
        return False

    header = rows[0]
    lat_cols = [i for i, h in enumerate(header) if _is_lat_col(h)]
    lon_cols = [i for i, h in enumerate(header) if _is_lon_col(h)]
    if not lat_cols and not lon_cols:
        return False

    changed = False
    for row in rows[1:]:
        for i in lat_cols:
            if i < len(row) and row[i].strip():
                row[i] = FAKE_LAT
                changed = True
        for i in lon_cols:
            if i < len(row) and row[i].strip():
                row[i] = FAKE_LON
                changed = True

    if not changed:
        return False

    import io

    buffer = io.StringIO()
    writer = csv.writer(buffer, lineterminator="\n")
    writer.writerows(rows)
    path.write_text(buffer.getvalue(), encoding="utf-8")
    return True


def redact_semicolon_csv(path: Path) -> bool:
    lines = path.read_text(encoding="utf-8").splitlines()
    if not lines or "latitude" not in lines[0].lower():
        return False

    out: list[str] = [lines[0]]
    changed = False
    for line in lines[1:]:
        if not line.strip():
            out.append(line)
            continue
        m = LAT_LON_TAIL.search(line)
        if m:
            lat_s, lon_s = m.group(1), m.group(2)
            try:
                lat = float(lat_s.strip('"'))
                lon = float(lon_s.strip('"'))
                if -90 <= lat <= 90 and -180 <= lon <= 180:
                    line = LAT_LON_TAIL.sub(f'"{FAKE_LAT}";"{FAKE_LON}";', line)
                    changed = True
            except ValueError:
                pass
        out.append(line)

    if changed:
        path.write_text("\n".join(out) + ("\n" if lines[-1].endswith("\n") else ""), encoding="utf-8")
    return changed


def main() -> int:
    if not FIXTURES.is_dir():
        print(f"Fixtures dir not found: {FIXTURES}", file=sys.stderr)
        return 1

    touched = 0
    for csv_path in sorted(FIXTURES.rglob("*.csv")):
        if csv_path.name.startswith("car-scanner-csv2"):
            continue
        first = csv_path.read_text(encoding="utf-8")[:200]
        if ";" in first and "LATITUDE" in first.upper():
            if redact_semicolon_csv(csv_path):
                print(f"redacted (vertical): {csv_path.relative_to(REPO_ROOT)}")
                touched += 1
        elif redact_comma_csv(csv_path):
            print(f"redacted (horizontal): {csv_path.relative_to(REPO_ROOT)}")
            touched += 1

    print(f"Done. {touched} file(s) updated.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
