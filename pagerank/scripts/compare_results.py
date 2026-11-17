#!/usr/bin/env python3
"""Check node sets, decimal10 format, expected numeric values and cross-engine agreement."""
import argparse
import re
from pathlib import Path
from typing import Dict


def read_result(path: Path) -> Dict[str, float]:
    values: Dict[str, float] = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        match = re.fullmatch(r"\(([^,()]+),(\d+\.\d{10})\)", line)
        if match is None:
            raise ValueError(f"Invalid decimal10 line in {path}: {line!r}")
        node, value = match.groups()
        if node in values:
            raise ValueError(f"Duplicate node {node} in {path}")
        values[node] = float(value)
    if not values:
        raise ValueError(f"Empty result: {path}")
    return values


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reference", type=Path)
    parser.add_argument("results", nargs="+", type=Path)
    args = parser.parse_args()
    expected = read_result(args.reference)
    for path in args.results:
        actual = read_result(path)
        if actual.keys() != expected.keys():
            raise AssertionError(f"Node sets differ: {path}")
        error = max(abs(actual[node] - expected[node]) for node in expected)
        if error > 1.01e-10:
            raise AssertionError(f"Maximum absolute error {error} exceeds 1.01e-10: {path}")
        if abs(sum(actual.values()) - 1.0) > len(actual) * 0.5e-10 + 1e-14:
            raise AssertionError(f"Rank mass is not one: {path}")
        print(f"PASS {path.name}: nodes={len(actual)}, max_abs_error={error:.12g}, sum={sum(actual.values()):.10f}")


if __name__ == "__main__":
    main()
