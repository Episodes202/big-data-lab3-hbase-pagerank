#!/usr/bin/env python3
"""Independent standard-library PageRank reference, not a distributed run."""
import argparse
import csv
from pathlib import Path
from typing import Dict, List, Set, Tuple


def read_graph(path: Path) -> Dict[str, Set[str]]:
    graph: Dict[str, Set[str]] = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        if not line.strip():
            continue
        fields = line.split("\t")
        if len(fields) != 2:
            raise ValueError("Expected page<TAB>links: " + line)
        source = fields[0].strip()
        destinations = {s.strip() for s in fields[1].split(",")} if fields[1].strip() else set()
        for node in {source} | destinations:
            if not node or any(c.isspace() or c in ",()" for c in node):
                raise ValueError("Invalid page name: " + repr(node))
            graph.setdefault(node, set())
        graph[source].update(destinations)
    if not graph:
        raise ValueError("Graph is empty")
    return graph


def pagerank(graph: Dict[str, Set[str]], iterations: int = 10,
             damping: float = 0.85) -> Tuple[Dict[str, float], List[dict]]:
    """Matrix-vector multiplication, independent of distributed edge aggregation."""
    nodes = sorted(graph)
    n = len(nodes)
    if n == 0 or iterations < 0 or not 0 <= damping <= 1:
        raise ValueError("Invalid graph, iteration count, or damping")
    index = {node: i for i, node in enumerate(nodes)}
    matrix = [[0.0 for _ in nodes] for _ in nodes]
    for source in nodes:
        destinations = graph[source]
        if not destinations:
            for destination in nodes:
                matrix[index[destination]][index[source]] = 1.0 / n
        else:
            for destination in destinations:
                matrix[index[destination]][index[source]] = 1.0 / len(destinations)
    ranks = [1.0 / n] * n
    history: List[dict] = []
    for step in range(1, iterations + 1):
        previous = ranks
        ranks = [(1.0 - damping) / n + damping * sum(
            matrix[i][j] * previous[j] for j in range(n)) for i in range(n)]
        row = {"iteration": step, **dict(zip(nodes, ranks)), "sum": sum(ranks),
               "l1_change": sum(abs(a - b) for a, b in zip(ranks, previous))}
        history.append(row)
    return dict(zip(nodes, ranks)), history


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--output", type=Path, default=Path("results/reference_result.txt"))
    parser.add_argument("--history", type=Path, default=Path("results/reference_iterations.csv"))
    args = parser.parse_args()
    ranks, history = pagerank(read_graph(args.input))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.history.parent.mkdir(parents=True, exist_ok=True)
    text = "\n".join(f"({node},{rank:.10f})" for node, rank in sorted(ranks.items())) + "\n"
    args.output.write_text(text, encoding="utf-8")
    with args.history.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(history[0]))
        writer.writeheader()
        writer.writerows(history)
    print("Independent local reference: 10 iterations, d=0.85, initial PR=1/N")
    print(text, end="")
    print(f"Unrounded rank sum={sum(ranks.values()):.15f}")


if __name__ == "__main__":
    main()
