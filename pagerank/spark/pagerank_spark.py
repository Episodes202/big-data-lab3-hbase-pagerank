#!/usr/bin/env python3
"""RDD PageRank: ten iterations, uniform teleportation and dangling redistribution."""
import argparse
from typing import Iterable, List, Tuple

from pyspark import SparkConf, SparkContext, StorageLevel


def parse_line(line: str) -> Tuple[str, Tuple[str, ...]]:
    parts = line.lstrip("\ufeff").split("\t")
    if len(parts) != 2:
        raise ValueError("Expected page<TAB>comma-separated outgoing links: " + line)
    source = parts[0].strip()
    links = tuple(sorted(set(x.strip() for x in parts[1].split(",")))) if parts[1].strip() else ()
    for page in (source,) + links:
        if not page or any(c.isspace() or c in ",()" for c in page):
            raise ValueError("Invalid page name: " + repr(page))
    return source, links


def contributions(item: Tuple[str, Tuple[Tuple[str, ...], float]]) -> Iterable[Tuple[str, float]]:
    _, (links, rank) = item
    if links:
        share = rank / len(links)
        for target in links:
            yield target, share


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", help="HDFS or local input path")
    parser.add_argument("output", help="new HDFS or local output directory")
    parser.add_argument("--partitions", type=int, default=2)
    args = parser.parse_args()
    if args.partitions < 1:
        parser.error("--partitions must be positive")
    sc = SparkContext(conf=SparkConf().setAppName("Lab3 PageRank 10 iterations"))
    sc.setLogLevel("WARN")
    try:
        parsed = sc.textFile(args.input).filter(lambda line: bool(line.strip())).map(parse_line)
        # Empty records ensure that destination-only pages remain in the complete graph.
        records = parsed.flatMap(lambda item: [(item[0], item[1])] + [(v, ()) for v in item[1]])
        links = records.reduceByKey(lambda a, b: tuple(sorted(set(a) | set(b))), args.partitions)
        links = links.persist(StorageLevel.MEMORY_AND_DISK)
        n = links.count()
        if not n:
            raise ValueError("Graph is empty")
        ranks = links.mapValues(lambda _: 1.0 / n).persist(StorageLevel.MEMORY_AND_DISK)
        ranks.count()
        damping = 0.85
        print(f"N={n}; damping={damping:.2f}; iterations=10")
        for iteration in range(1, 11):
            joined = links.join(ranks).persist(StorageLevel.MEMORY_AND_DISK)
            dangling = joined.filter(lambda item: not item[1][0]).map(lambda item: item[1][1]).sum()
            incoming = joined.flatMap(contributions).reduceByKey(lambda a, b: a + b, args.partitions)
            # Capture each iteration's scalar in a default argument for lazy RDD evaluation.
            base = (1.0 - damping) / n + damping * dangling / n
            updated = links.leftOuterJoin(incoming).mapValues(
                lambda value, b=base: b + damping * (value[1] if value[1] is not None else 0.0)
            ).persist(StorageLevel.MEMORY_AND_DISK)
            total = updated.values().sum()  # materialize before releasing prior state
            print(f"iteration={iteration} rank_sum={total:.15f}")
            ranks.unpersist()
            joined.unpersist()
            ranks = updated
        ranks.sortByKey().map(lambda item: f"({item[0]},{item[1]:.10f})").saveAsTextFile(args.output)
        ranks.unpersist()
        links.unpersist()
    finally:
        sc.stop()


if __name__ == "__main__":
    main()
