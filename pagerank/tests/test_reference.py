"""Invariant tests: cycle, sinks, disconnected nodes, and input normalization."""
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from reference import pagerank, read_graph


class PageRankTests(unittest.TestCase):
    def test_cycle_is_uniform(self) -> None:
        ranks, history = pagerank({"A": {"B"}, "B": {"C"}, "C": {"A"}})
        self.assertEqual(len(history), 10)
        for rank in ranks.values():
            self.assertAlmostEqual(rank, 1.0 / 3, places=14)

    def test_all_sinks_are_uniform(self) -> None:
        ranks, _ = pagerank({"A": set(), "B": set()})
        self.assertEqual(ranks, {"A": 0.5, "B": 0.5})

    def test_sink_keeps_mass_and_zero_indegree(self) -> None:
        ranks, history = pagerank({"A": {"B"}, "B": set(), "C": set()})
        self.assertEqual(set(ranks), {"A", "B", "C"})
        self.assertGreater(ranks["B"], ranks["A"])
        for row in history:
            self.assertAlmostEqual(row["sum"], 1.0, places=14)
            self.assertGreater(min(row[n] for n in ("A", "B", "C")), 0)

    def test_target_only_and_duplicate_edges(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "graph.tsv"
            path.write_text("A\tB,B\nA\tC\n", encoding="utf-8")
            self.assertEqual(read_graph(path), {"A": {"B", "C"}, "B": set(), "C": set()})

    def test_empty_graph_rejected(self) -> None:
        with self.assertRaises(ValueError):
            pagerank({})


if __name__ == "__main__":
    unittest.main(verbosity=2)
