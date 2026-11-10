"""Offline Top-K comparison on a read-only HUIT PostgreSQL snapshot."""

import argparse
from collections import Counter, defaultdict
from datetime import datetime, timedelta
from hashlib import sha256
from heapq import nlargest
import json
import math
from pathlib import Path
import re
import subprocess
import unicodedata


DEFAULT = (0.50, 0.35, 0.15)
STOP_WORDS = set("a an and the of for to in on with va voi cua cho trong mot nhung cac ve".split())
SNAPSHOT_SQL = """
BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
SELECT json_build_array('book', book_id, title, author, publisher, c.name,
                        publish_year, b.created_at)
FROM books b LEFT JOIN categories c USING(category_id)
WHERE NULLIF(BTRIM(title), '') IS NOT NULL ORDER BY book_id;
SELECT json_build_array('subject', a.book_id, s.subject_id, s.code || ' ' || s.name,
                        a.confidence, GREATEST(a.created_at, s.created_at))
FROM book_subject_assignments a JOIN recommendation_subjects s USING(subject_id)
WHERE s.active ORDER BY a.book_id, s.subject_id;
SELECT json_build_array('affinity', a.department_key, a.subject_id, a.weight,
                        GREATEST(a.created_at, s.created_at))
FROM department_subject_affinities a JOIN recommendation_subjects s USING(subject_id)
WHERE a.active AND s.active ORDER BY a.department_key, a.subject_id, a.affinity_id;
SELECT json_build_array('reader', user_id, department, created_at)
FROM users WHERE user_type = 'STUDENT' ORDER BY user_id;
SELECT json_build_array('loan', lt.user_id, b.book_id, ld.checkout_at, ld.checkout_time_source)
FROM loan_details ld JOIN loan_tickets lt USING(ticket_id)
JOIN users u ON u.user_id = lt.user_id
LEFT JOIN book_copies bc ON bc.barcode = ld.barcode
JOIN books b ON b.book_id = COALESCE(ld.circulation_book_id, bc.book_id)
WHERE u.user_type = 'STUDENT' AND COALESCE(lt.status, '') NOT IN ('CANCELLED', 'REJECTED')
  AND NULLIF(BTRIM(b.title), '') IS NOT NULL
  AND (ld.checkout_at IS NOT NULL OR ld.return_date IS NOT NULL OR ld.return_status IN
      ('BORROWING', 'BORROWED', 'OVERDUE', 'RETURNED', 'LOST', 'DAMAGED', 'SETTLED'))
ORDER BY ld.checkout_at, ld.detail_id;
ROLLBACK;
"""


def read_snapshot(container):
    command = ["docker", "exec", "-i", container, "sh", "-c",
               'PGCLIENTENCODING=UTF8 psql -XqAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"']
    result = subprocess.run(command, input=SNAPSHOT_SQL.encode(), stdout=subprocess.PIPE, check=True)
    rows = defaultdict(list)
    for line in result.stdout.splitlines():
        kind, *values = json.loads(line)
        rows[kind].append(values)
    return rows, sha256(result.stdout).hexdigest()


def folded(value):
    decomposed = unicodedata.normalize("NFD", (value or "").lower().replace("đ", "d"))
    return "".join(char for char in decomposed if not unicodedata.category(char).startswith("M"))


def department_key(value):
    return re.sub(r"[^a-z0-9]+", " ", folded(value)).strip()


def weighted_terms(book, subjects):
    terms = Counter()
    fields = zip(book[:4], (3, 1, 1, 2))
    for value, weight in [*fields, *((subject[1], 4) for subject in subjects)]:
        tokens = [token for token in re.findall(r"[a-z0-9]+", folded((value or "").lower().replace("c++", "cpp")))
                  if len(token) >= 2 and token not in STOP_WORDS]
        for token in tokens:
            terms[token] += weight
        for left, right in zip(tokens, tokens[1:]):
            terms[f"{left}_{right}"] += weight
    return terms


def days_between(later, earlier):
    return (later - earlier).total_seconds() / 86400


def content_scores(books, subjects, history, candidates, cutoff):
    recent = sorted(history, key=lambda book_id: history[book_id][-1], reverse=True)[:100]
    if not recent:
        return {}
    terms = {book_id: weighted_terms(books[book_id], subjects.get(book_id, ()))
             for book_id in (*recent, *candidates)}
    df = Counter(term for document in terms.values() for term in document)
    idf = {term: math.log((len(terms) + 1) / (count + 1)) + 1 for term, count in df.items()}
    profile = Counter()
    for book_id in recent:
        dates = history[book_id]
        # Renewal history is unavailable as of each cutoff. Current totals would leak.
        weight = 0.5 ** (int(days_between(cutoff, dates[-1])) / 180) * (1 + math.log1p(len(dates)))
        for term, count in terms[book_id].items():
            profile[term] += weight * (1 + math.log(count)) * idf[term]
    profile_norm = math.hypot(*profile.values())
    if not profile_norm:
        return {}
    scores = {}
    for book_id in candidates:
        vector = {term: (1 + math.log(count)) * idf[term] for term, count in terms[book_id].items()}
        norm = math.hypot(*vector.values())
        if norm:
            scores[book_id] = sum(profile[term] * weight for term, weight in vector.items()) / (profile_norm * norm)
    return scores


def temporal_cutoffs(events):
    times = sorted({row[2] for row in events})
    if len(times) < 3:
        return None
    val_index = min(len(times) - 2, max(1, len(times) * 6 // 10))
    test_index = min(len(times) - 1, max(val_index + 1, len(times) * 8 // 10))
    return times[val_index], times[test_index]


def available_at(value, cutoff):
    return value is not None and datetime.fromisoformat(value) <= cutoff


def phase(rows, events, start, end, pool_size):
    books = {book_id: (title, author, publisher, category, year)
             for book_id, title, author, publisher, category, year, created_at in rows["book"]
             if available_at(created_at, start)}
    subjects, by_subject, by_category, affinities = (defaultdict(list) for _ in range(4))
    for book_id, subject_id, text, confidence, created_at in rows["subject"]:
        if book_id in books and available_at(created_at, start):
            subjects[book_id].append((subject_id, text, confidence))
            by_subject[subject_id].append(book_id)
    for key, subject_id, weight, created_at in rows["affinity"]:
        if available_at(created_at, start):
            affinities[key].append((subject_id, weight))
    for book_id, book in books.items():
        if book[3]:
            by_category[book[3]].append(book_id)

    histories = defaultdict(lambda: defaultdict(list))
    departments = {user: department_key(department) for user, department, created_at in rows["reader"]
                   if available_at(created_at, start)}
    future = defaultdict(set)
    popularity = Counter()
    for user, book_id, at, _ in events:
        if at < start:
            histories[user][book_id].append(at)
            if book_id in books and at >= start - timedelta(days=365):
                popularity[book_id] += 0.5 ** (days_between(start, at) / 91.25)
        elif end is None or at < end:
            future[user].add(book_id)

    most_popular = sorted(popularity, key=lambda book_id: (-popularity[book_id], book_id))[:1000]
    max_pop = max(popularity.values(), default=0)
    fallback = sorted(books, key=lambda book_id: (-(books[book_id][4] or 0), books[book_id][0].lower(), book_id))
    cases = []
    repeated = unavailable = skipped_users = 0
    for user, target in future.items():
        history = histories[user]
        repeated += len(target.intersection(history))
        novel = target.difference(history)
        relevant = novel.intersection(books)
        unavailable += len(novel) - len(relevant)
        if not relevant:
            skipped_users += 1
            continue
        usable_history = {book_id: dates for book_id, dates in history.items() if book_id in books}
        department = dict(affinities[departments.get(user, "")])
        target_subjects = set(department)
        target_categories = set()
        for book_id in usable_history:
            target_subjects.update(item[0] for item in subjects.get(book_id, ()))
            if books[book_id][3]:
                target_categories.add(books[book_id][3])
        sources = (most_popular[:min(1000, pool_size // 3)],
                   sorted({book_id for subject in target_subjects for book_id in by_subject[subject]}),
                   sorted({book_id for category in target_categories for book_id in by_category[category]}),
                   fallback)
        candidates = {}
        for source in sources:
            for book_id in source:
                if len(candidates) >= pool_size:
                    break
                if book_id not in history:
                    candidates[book_id] = None
        content = content_scores(books, subjects, usable_history, candidates, start)
        scores = {}
        for book_id in candidates:
            major = max((department.get(subject_id, 0) * confidence
                         for subject_id, _, confidence in subjects.get(book_id, ())), default=0)
            pop = math.log1p(popularity[book_id]) / math.log1p(max_pop) if max_pop else 0
            scores[book_id] = (content.get(book_id, 0), major, pop)
        cohort = "cold" if not history else "sparse" if len(history) < 5 else "warm"
        cases.append((cohort, relevant, scores, bool(content)))
    return cases, {
        "catalog_at_cutoff": len(books), "users_with_targets": len(future),
        "eligible_users": len(cases), "skipped_users": skipped_users,
        "excluded_repeat_targets": repeated, "excluded_catalog_targets": unavailable,
    }


def metrics(cases, weights, k, catalog_size, content_fallback=False):
    covered = set()
    precision = recall = hits = ndcg = retrieval_recall = 0.0
    for _, relevant, scores, has_content in cases:
        effective_weights = (0, 0, 1) if content_fallback and not has_content else weights
        top = nlargest(k, scores, key=lambda book_id: (
            sum(w * s for w, s in zip(effective_weights, scores[book_id])), -book_id))
        covered.update(top)
        ranks = [rank for rank, book_id in enumerate(top, 1) if book_id in relevant]
        precision += len(ranks) / k
        recall += len(ranks) / len(relevant)
        hits += bool(ranks)
        dcg = sum(1 / math.log2(rank + 1) for rank in ranks)
        ideal = sum(1 / math.log2(rank + 1) for rank in range(1, min(k, len(relevant)) + 1))
        ndcg += dcg / ideal
        retrieval_recall += len(relevant.intersection(scores)) / len(relevant)
    count = len(cases)
    return {"users": count, "precision": precision / count if count else None,
            "recall": recall / count if count else None, "hit_rate": hits / count if count else None,
            "ndcg": ndcg / count if count else None,
            "candidate_recall": retrieval_recall / count if count else None,
            "coverage": len(covered) / catalog_size if count and catalog_size else None}


def benchmark(rows, cutoffs, k, pool_size):
    events = sorted(((user, book, datetime.fromisoformat(at), source)
                     for user, book, at, source in rows["loan"] if at),
                    key=lambda row: (row[2], row[1], row[0]))
    cutoffs = cutoffs or temporal_cutoffs(events)
    report = {"top_k": k, "candidate_pool": pool_size, "catalog_snapshot": len(rows["book"]),
              "eligible_checkout_events": len(events),
              "readers_with_checkout_events": len({row[0] for row in events}),
              "missing_checkout_excluded": len(rows["loan"]) - len(events),
              "checkout_time_sources": dict(Counter(row[3] or "UNSPECIFIED" for row in events)),
              "status": "insufficient_distinct_checkout_times", "test": {}}
    if cutoffs is None:
        return report
    validation_start, test_start = cutoffs
    validation, validation_stats = phase(rows, events, validation_start, test_start, pool_size)
    test, test_stats = phase(rows, events, test_start, None, pool_size)
    report.update({"validation_start": validation_start.isoformat(), "test_start": test_start.isoformat(),
                   "train_events": sum(at < validation_start for _, _, at, _ in events),
                   "validation_events": sum(validation_start <= at < test_start for _, _, at, _ in events),
                   "test_events": sum(at >= test_start for _, _, at, _ in events),
                   "validation_diagnostics": validation_stats, "test_diagnostics": test_stats,
                   "small_test_cohort": len(test) < 30,
                   "status": "evaluated" if test else "no_eligible_test_targets"})
    models = {"POPULARITY": (0, 0, 1), "TFIDF": (1, 0, 0), "HYBRID_DEFAULT": DEFAULT,
              "HYBRID_WITHOUT_DEPARTMENT": (0.50 / 0.65, 0, 0.15 / 0.65),
              "HYBRID_WITHOUT_CONTENT": (0, 0.35 / 0.50, 0.15 / 0.50)}
    if validation and report["train_events"]:
        grid = [DEFAULT] + [(i / 10, j / 10, (10 - i - j) / 10)
                            for i in range(1, 9) for j in range(1, 10 - i)]
        runs = [{"weights": w, **metrics(validation, w, k, validation_stats["catalog_at_cutoff"])} for w in grid]
        report["validation_grid"] = runs
        if len({(run["ndcg"], run["recall"]) for run in runs}) > 1:
            best = max(runs, key=lambda run: (run["ndcg"], run["recall"],
                       -sum((weight - default) ** 2 for weight, default in zip(run["weights"], DEFAULT))))
            models["HYBRID_TUNED"] = best["weights"]
        else:
            report["tuning_note"] = "Validation did not distinguish the weights; keep the configured default."
    else:
        report["tuning_note"] = "No training events or eligible validation users; weights were not tuned."
    report["weights"] = models
    for cohort in ("all", "cold", "sparse", "warm"):
        group = [case for case in test if cohort == "all" or case[0] == cohort]
        report["test"][cohort] = {
            name: metrics(group, w, k, test_stats["catalog_at_cutoff"], content_fallback=name == "TFIDF")
            for name, w in models.items()}
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--container", default="library_postgres")
    parser.add_argument("--out", type=Path, default=Path(".codex-tmp/recommendation-benchmark/report.json"))
    parser.add_argument("--top-k", type=int, default=10)
    parser.add_argument("--candidate-pool", type=int, default=3000)
    parser.add_argument("--validation-start", type=datetime.fromisoformat)
    parser.add_argument("--test-start", type=datetime.fromisoformat)
    args = parser.parse_args()
    if not 1 <= args.top_k <= args.candidate_pool <= 10000:
        parser.error("Require 1 <= top-k <= candidate-pool <= 10000")
    cutoffs = None
    if args.validation_start or args.test_start:
        if (not args.validation_start or not args.test_start or
                args.validation_start.tzinfo or args.test_start.tzinfo or
                args.validation_start >= args.test_start):
            parser.error("Provide both local timestamps, with validation-start before test-start")
        cutoffs = args.validation_start, args.test_start
    rows, digest = read_snapshot(args.container)
    report = {"snapshot_sha256": digest, **benchmark(rows, cutoffs, args.top_k, args.candidate_pool)}
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f'{report["status"]}: {report["eligible_checkout_events"]} checkout events; {args.out}')


if __name__ == "__main__":
    main()
