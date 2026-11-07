\set ON_ERROR_STOP on
-- Local demo data only. Verify a pg_dump backup before running with apply=1.
\if :{?apply}
\else
\set apply 0
\endif

BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '180s';
LOCK TABLE books, shelves, book_copies, book_subject_assignments, recommendation_subjects,
    shelf_subject_rules, loan_details, inventory_details IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
    IF (SELECT count(*) FROM book_copies c
        WHERE c.book_id BETWEEN 271575 AND 541496
          AND c.barcode = 'BC-' || (c.book_id - 269922) || '-1'
          AND c.created_at = timestamp '2026-10-09 14:58:21.900779') <> 269922
       OR (SELECT md5(string_agg(isbn, E'\n' ORDER BY book_id))
           FROM books WHERE book_id BETWEEN 271575 AND 541496)
          IS DISTINCT FROM '693232894536f885e0e9a18fd9ad6129'
       OR EXISTS (SELECT 1 FROM book_copies c LEFT JOIN books b USING(book_id) WHERE b.book_id IS NULL)
       OR EXISTS (SELECT 1 FROM shelves WHERE capacity IS NOT NULL) THEN
        RAISE EXCEPTION 'Expected the verified seed catalog and an unbounded demo layout.';
    END IF;
END $$;

INSERT INTO recommendation_subjects (code, name, description)
VALUES ('SHELF_UNCLASSIFIED', U&'Ch\1EDD ph\00E2n lo\1EA1i',
        U&'Ch\1EC9 nh\1EADn b\1EA3n sao c\1EE7a \0111\1EA7u s\00E1ch ch\01B0a c\00F3 ch\1EE7 \0111\1EC1 ho\1EA1t \0111\1ED9ng.')
ON CONFLICT (code) DO NOTHING;

CREATE TEMP TABLE copy_before ON COMMIT DROP AS
SELECT c.barcode, c.book_id, c.status, c.shelf_id,
       (c.shelf_id IS NOT NULL
        AND c.book_id BETWEEN 271575 AND 541496
        AND c.barcode = 'BC-' || (c.book_id - 269922) || '-1'
        AND c.created_at = timestamp '2026-10-09 14:58:21.900779'
        AND c.shelf_id IN ((abs(hashtext(coalesce(b.publisher, 'Unknown'))) % 160) + 1,
                           (abs(hashtext(coalesce(b.publisher, ''))) % 160) + 1)
        AND c.status = 'AVAILABLE'
        AND c.receipt_detail_id IS NULL AND c.locked_by_user IS NULL
        AND (c.locked_until IS NULL OR c.locked_until <= now())
        AND NOT EXISTS (SELECT 1 FROM loan_details l WHERE l.barcode=c.barcode)
        AND NOT EXISTS (SELECT 1 FROM inventory_details i WHERE i.barcode=c.barcode)) AS can_move
FROM book_copies c JOIN books b USING(book_id);
ALTER TABLE copy_before ADD PRIMARY KEY (barcode);

CREATE TEMP TABLE active_book_subjects ON COMMIT DROP AS
SELECT a.book_id, a.subject_id, a.primary_subject, a.confidence
FROM book_subject_assignments a JOIN recommendation_subjects s USING(subject_id)
WHERE s.active AND s.code <> 'SHELF_UNCLASSIFIED';
CREATE INDEX ON active_book_subjects(book_id);
ANALYZE active_book_subjects;

-- Preserve existing active rules. Give each uncovered subject one of the least occupied
-- unconfigured shelves. Keep shelves with protected copies out of that pool.
WITH current_rules AS (
    SELECT r.shelf_id, r.subject_id
    FROM shelf_subject_rules r JOIN recommendation_subjects s USING(subject_id)
    WHERE r.active AND s.active
), missing_subjects AS (
    SELECT s.subject_id, row_number() OVER (ORDER BY s.subject_id) AS position
    FROM recommendation_subjects s
    WHERE s.active AND s.code <> 'SHELF_UNCLASSIFIED'
      AND NOT EXISTS (SELECT 1 FROM current_rules r WHERE r.subject_id=s.subject_id)
), free_shelves AS (
    SELECT shelf.shelf_id, row_number() OVER (ORDER BY count(c.barcode), shelf.shelf_id) AS position
    FROM shelves shelf LEFT JOIN copy_before c ON c.shelf_id=shelf.shelf_id
    WHERE NOT EXISTS (SELECT 1 FROM current_rules r WHERE r.shelf_id=shelf.shelf_id)
    GROUP BY shelf.shelf_id
    HAVING count(c.barcode) FILTER (WHERE NOT c.can_move)=0
)
INSERT INTO shelf_subject_rules (shelf_id, subject_id, active)
SELECT shelf_id, subject_id, true FROM missing_subjects JOIN free_shelves USING(position)
ON CONFLICT (shelf_id, subject_id) DO UPDATE SET active=true, updated_at=now();

INSERT INTO shelf_subject_rules (shelf_id, subject_id, active)
SELECT shelf.shelf_id, intake.subject_id, true
FROM shelves shelf CROSS JOIN recommendation_subjects intake
WHERE intake.active AND intake.code='SHELF_UNCLASSIFIED'
  AND NOT EXISTS (
      SELECT 1 FROM shelf_subject_rules r JOIN recommendation_subjects s USING(subject_id)
      WHERE r.shelf_id=shelf.shelf_id AND r.active AND s.active
  )
ON CONFLICT (shelf_id, subject_id) DO UPDATE SET active=true, updated_at=now();

CREATE TEMP TABLE allowed_shelves ON COMMIT DROP AS
SELECT r.subject_id, array_agg(r.shelf_id ORDER BY r.shelf_id) AS shelf_ids
FROM shelf_subject_rules r JOIN recommendation_subjects s USING(subject_id)
WHERE r.active AND s.active GROUP BY r.subject_id;

CREATE TEMP TABLE copy_plan ON COMMIT DROP AS
SELECT c.barcode, c.shelf_id, c.can_move,
       CASE WHEN classified.keep_here OR (classified.shelf_ids IS NULL AND c.shelf_id=ANY(intake.shelf_ids))
            THEN c.shelf_id
            ELSE targets.shelf_ids[1 + mod(row_number() OVER (
                PARTITION BY targets.shelf_ids ORDER BY c.barcode
            ) - 1, cardinality(targets.shelf_ids))]
       END AS target_shelf
FROM copy_before c
CROSS JOIN (
    SELECT shelves.shelf_ids FROM allowed_shelves shelves
    JOIN recommendation_subjects s USING(subject_id) WHERE s.code='SHELF_UNCLASSIFIED'
) intake
LEFT JOIN LATERAL (
    SELECT shelves.shelf_ids, bool_or(c.shelf_id=ANY(shelves.shelf_ids)) OVER () AS keep_here
    FROM active_book_subjects a JOIN allowed_shelves shelves USING(subject_id)
    WHERE a.book_id=c.book_id
    ORDER BY a.primary_subject DESC, a.confidence DESC, a.subject_id
    LIMIT 1
) classified ON true
CROSS JOIN LATERAL (SELECT coalesce(classified.shelf_ids,intake.shelf_ids) AS shelf_ids) targets
WHERE c.shelf_id IS NOT NULL;
ALTER TABLE copy_plan ADD PRIMARY KEY (barcode);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM recommendation_subjects s WHERE s.active AND NOT EXISTS
               (SELECT 1 FROM allowed_shelves a WHERE a.subject_id=s.subject_id))
       OR EXISTS (SELECT 1 FROM shelves s WHERE NOT EXISTS
                 (SELECT 1 FROM allowed_shelves a WHERE s.shelf_id=ANY(a.shelf_ids)))
       OR (SELECT count(*) FROM copy_plan) <> (SELECT count(*) FROM copy_before WHERE shelf_id IS NOT NULL)
       OR EXISTS (SELECT 1 FROM copy_plan WHERE target_shelf IS NULL OR (NOT can_move AND target_shelf<>shelf_id)) THEN
        RAISE EXCEPTION 'Plan leaves an uncovered subject or would change a protected barcode.';
    END IF;
END $$;

UPDATE book_copies c SET shelf_id=p.target_shelf, updated_at=now()
FROM copy_plan p WHERE c.barcode=p.barcode AND c.shelf_id<>p.target_shelf;

DO $$
BEGIN
    IF (SELECT md5(string_agg(jsonb_build_array(b.barcode, b.book_id, b.status,
                       coalesce(p.target_shelf,b.shelf_id))::text, E'\n' ORDER BY b.barcode COLLATE "C"))
        FROM copy_before b LEFT JOIN copy_plan p ON p.barcode=b.barcode)
       IS DISTINCT FROM
       (SELECT md5(string_agg(jsonb_build_array(c.barcode, c.book_id, c.status, c.shelf_id)::text,
                       E'\n' ORDER BY c.barcode COLLATE "C")) FROM book_copies c)
    OR EXISTS (
        SELECT 1 FROM book_copies c WHERE c.shelf_id IS NOT NULL
          AND NOT EXISTS (
              SELECT 1 FROM active_book_subjects a JOIN allowed_shelves r USING(subject_id)
              WHERE a.book_id=c.book_id AND c.shelf_id=ANY(r.shelf_ids)
          ) AND NOT (
              NOT EXISTS (SELECT 1 FROM active_book_subjects a WHERE a.book_id=c.book_id)
              AND EXISTS (
                  SELECT 1 FROM allowed_shelves r JOIN recommendation_subjects s USING(subject_id)
                  WHERE s.code='SHELF_UNCLASSIFIED' AND c.shelf_id=ANY(r.shelf_ids)
              )
          )
    ) THEN
        RAISE EXCEPTION 'Placement verification failed.';
    END IF;
END $$;

SELECT (SELECT count(*) FROM shelves) AS configured_shelves,
       (SELECT cardinality(a.shelf_ids) FROM allowed_shelves a JOIN recommendation_subjects s USING(subject_id)
        WHERE s.code='SHELF_UNCLASSIFIED') AS intake_shelves,
       count(*) FILTER (WHERE target_shelf<>shelf_id) AS copies_moved
FROM copy_plan;
SELECT count(*) FILTER (WHERE c.shelf_id IS NULL) AS still_unplaced,
       count(*) FILTER (WHERE c.shelf_id IS NOT NULL AND a.book_id IS NOT NULL) AS placed_with_subject,
       count(*) FILTER (WHERE c.shelf_id IS NOT NULL AND a.book_id IS NULL) AS placed_in_intake
FROM book_copies c LEFT JOIN (SELECT DISTINCT book_id FROM active_book_subjects) a USING(book_id);

\if :apply
COMMIT;
\else
ROLLBACK;
\endif
