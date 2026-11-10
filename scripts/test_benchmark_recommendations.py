"""Regression checks for the offline split, candidate pool and metrics."""

from collections import defaultdict
from datetime import datetime
import math
import unittest

from benchmark_recommendations import DEFAULT, benchmark, metrics, phase, temporal_cutoffs


def at(day):
    return datetime(2026, 1, day)


class RecommendationBenchmarkTest(unittest.TestCase):
    def test_split_keeps_simultaneous_checkouts_in_one_period(self):
        events = [("u", book, at(day), "CHECKOUT") for book, day in enumerate((1, 1, 2, 3))]
        self.assertEqual(temporal_cutoffs(events), (at(2), at(3)))
        self.assertIsNone(temporal_cutoffs(events[:3]))

    def test_future_checkouts_do_not_enter_scores_or_inject_candidates(self):
        rows = defaultdict(list, book=[
            [1, "Already read", "", "", "A", 2020, at(1).isoformat()],
            [2, "Future favorite", "", "", "B", 2020, at(1).isoformat()],
            [3, "Future catalog entry", "", "", "B", 2020, at(4).isoformat()],
            [4, "Popular", "", "", "B", 2025, at(1).isoformat()],
        ])
        events = [("u", 1, at(1), "CHECKOUT"), ("another", 4, at(1), "CHECKOUT"),
                  ("u", 1, at(3), "CHECKOUT"), ("u", 2, at(3), "CHECKOUT"),
                  ("u", 3, at(4), "CHECKOUT"), ("someone", 2, at(5), "CHECKOUT")]
        cases, stats = phase(rows, events, at(3), None, 1)
        _, relevant, scores, _ = cases[0]
        self.assertEqual(relevant, {2})
        self.assertEqual(set(scores), {4})
        self.assertEqual(stats["excluded_repeat_targets"], 1)
        self.assertEqual(stats["excluded_catalog_targets"], 1)
        self.assertEqual(metrics(cases[:1], DEFAULT, 1, 3)["recall"], 0)

    def test_cold_tfidf_uses_the_same_popularity_baseline(self):
        cases = [("cold", {2}, {1: (0, 1, 0), 2: (0, 0, 1)}, False)]
        pop = metrics(cases, (0, 0, 1), 1, 2)
        content = metrics(cases, (1, 0, 0), 1, 2, content_fallback=True)
        self.assertEqual(pop, content)
        self.assertEqual(content["ndcg"], 1)
        self.assertEqual(metrics(cases, DEFAULT, 1, 2)["recall"], 0)

    def test_ndcg_discounts_the_rank_and_empty_cohort_has_no_score(self):
        cases = [("sparse", {2}, {1: (1, 0, 0), 2: (0.5, 0, 0)}, True)]
        report = metrics(cases, (1, 0, 0), 2, 2)
        self.assertEqual(report["precision"], 0.5)
        self.assertEqual(report["recall"], 1)
        self.assertAlmostEqual(report["ndcg"], 1 / math.log2(3))
        self.assertIsNone(metrics([], DEFAULT, 2, 2)["ndcg"])

    def test_missing_checkout_timestamp_is_not_imputed(self):
        rows = defaultdict(list, loan=[["u", 1, None, None]])
        report = benchmark(rows, None, 10, 3000)
        self.assertEqual(report["status"], "insufficient_distinct_checkout_times")
        self.assertEqual(report["missing_checkout_excluded"], 1)
        self.assertEqual(report["test"], {})

    def test_weight_selection_uses_validation_and_is_independent_of_test_targets(self):
        creation = at(1).isoformat()
        titles = ("apple pear", "volcano handbook", "ocean guide", "apple pear",
                  "mountain trails", "city roads")
        rows = defaultdict(list, book=[
            [index, title, "", "", None, 2020, creation]
            for index, title in enumerate(titles, 1)
        ], reader=[["u", "Địa lý", creation]],
            subject=[[2, 1, "geology", 1.0, creation]],
            affinity=[["dia ly", 1, 1.0, creation]],
            loan=[["u", 1, creation, "CHECKOUT"], ["v", 3, creation, "CHECKOUT"],
                  ["u", 2, at(2).isoformat(), "CHECKOUT"],
                  ["u", 5, at(3).isoformat(), "CHECKOUT"]])
        first = benchmark(rows, (at(2), at(3)), 1, 10)
        self.assertEqual(first["status"], "evaluated")
        self.assertNotEqual(first["weights"]["HYBRID_TUNED"], DEFAULT)
        self.assertEqual(max(run["ndcg"] for run in first["validation_grid"]), 1)
        rows["loan"][-1][1] = 6
        second = benchmark(rows, (at(2), at(3)), 1, 10)
        self.assertEqual(first["weights"], second["weights"])
        self.assertEqual(first["validation_grid"], second["validation_grid"])


if __name__ == "__main__":
    unittest.main()
