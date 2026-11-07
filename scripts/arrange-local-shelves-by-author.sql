\set ON_ERROR_STOP on
-- Local demo layout only. Take and verify a fresh backup first. Default is a rollback preview.
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
    IF (SELECT md5(string_agg(isbn, E'\n' ORDER BY book_id))
        FROM books WHERE book_id BETWEEN 271575 AND 541496)
           IS DISTINCT FROM '693232894536f885e0e9a18fd9ad6129'
       OR EXISTS (SELECT 1 FROM shelves WHERE capacity IS NOT NULL)
       OR EXISTS (SELECT 1 FROM book_copies c LEFT JOIN books b USING(book_id) WHERE b.book_id IS NULL) THEN
        RAISE EXCEPTION 'Expected the verified, unbounded local demo catalog.';
    END IF;
END $$;

\ir ../backend/src/main/resources/db/changelog/changesets/47-shelf-author-groups.sql

CREATE TEMP TABLE copy_before ON COMMIT DROP AS
SELECT c.barcode, c.book_id, c.shelf_id, c.status,
       (c.shelf_id IS NOT NULL AND (
           c.book_id NOT BETWEEN 271575 AND 541496
           OR c.barcode <> 'BC-' || (c.book_id - 269922) || '-1'
           OR c.created_at IS DISTINCT FROM timestamp '2026-10-09 14:58:21.900779'
           OR c.status IS DISTINCT FROM 'AVAILABLE'
           OR c.receipt_detail_id IS NOT NULL OR c.locked_by_user IS NOT NULL
           OR coalesce(c.locked_until > now(), false)
           OR EXISTS (SELECT 1 FROM loan_details l WHERE l.barcode=c.barcode)
           OR EXISTS (SELECT 1 FROM inventory_details i WHERE i.barcode=c.barcode)
       )) AS protect_location
FROM book_copies c;
ALTER TABLE copy_before ADD PRIMARY KEY (barcode);
CREATE INDEX ON copy_before(book_id);
ANALYZE copy_before;

CREATE TEMP TABLE placement_books ON COMMIT DROP AS
SELECT b.book_id, b.author, b.title,
       CASE WHEN NOT EXISTS (
           SELECT 1 FROM book_subject_assignments a JOIN recommendation_subjects s USING(subject_id)
           WHERE a.book_id=b.book_id AND s.active
       ) THEN shelf_author_group(b.author,b.title) END AS author_group
FROM books b;
ALTER TABLE placement_books ADD PRIMARY KEY (book_id);
ANALYZE placement_books;

-- Repurpose only the shelves whose sole active type is the earlier intake rule.
CREATE TEMP TABLE intake_shelves ON COMMIT DROP AS
SELECT shelf.shelf_id,
       row_number() OVER (ORDER BY rack.floor_id, rack.bookshelf_id, shelf.shelf_id) AS position
FROM shelves shelf JOIN bookshelves rack USING(bookshelf_id)
JOIN shelf_subject_rules r ON r.shelf_id=shelf.shelf_id AND r.active
JOIN recommendation_subjects s ON s.subject_id=r.subject_id AND s.active AND s.code='SHELF_UNCLASSIFIED'
WHERE NOT EXISTS (
    SELECT 1 FROM shelf_subject_rules other JOIN recommendation_subjects kind USING(subject_id)
    WHERE other.shelf_id=shelf.shelf_id AND other.active AND kind.active AND other.subject_id<>r.subject_id
);

-- Allocate one shelf per letter, then use largest remainders to distribute the rest by copy count.
CREATE TEMP TABLE author_shelves ON COMMIT DROP AS
WITH groups AS (
    SELECT s.code, s.subject_id, count(c.barcode) AS copies
    FROM recommendation_subjects s
    LEFT JOIN placement_books b ON b.author_group=s.code
    LEFT JOIN copy_before c USING(book_id)
    WHERE s.active AND s.code LIKE 'SHELF\_AUTHOR\_%'
    GROUP BY s.subject_id
), quotas AS (
    SELECT *, copies * ((SELECT count(*) FROM intake_shelves) - count(*) OVER ())
                         / sum(copies) OVER () AS share
    FROM groups
), ranked AS (
    SELECT *, row_number() OVER (ORDER BY share-floor(share) DESC, code) AS extra_order
    FROM quotas
), slots AS (
    SELECT code, subject_id, row_number() OVER (ORDER BY code='SHELF_AUTHOR_OTHER', code, slot) AS position
    FROM ranked CROSS JOIN LATERAL generate_series(1, (1 + floor(share)
        + CASE WHEN extra_order <= (SELECT count(*) FROM intake_shelves) -
            (SELECT sum(1 + floor(share)) FROM quotas) THEN 1 ELSE 0 END)::integer) slot
)
SELECT slots.code, slots.subject_id, intake_shelves.shelf_id, slots.position
FROM slots JOIN intake_shelves USING(position);

DO $$
BEGIN
    IF (SELECT count(*) FROM intake_shelves) <> 148
       OR (SELECT count(*) FROM author_shelves) <> 148
       OR (SELECT count(DISTINCT code) FROM author_shelves) <> 27
       OR EXISTS (
            SELECT 1 FROM shelf_subject_rules r JOIN recommendation_subjects s USING(subject_id)
            WHERE r.active AND s.active AND s.code='SHELF_UNCLASSIFIED'
              AND NOT EXISTS (SELECT 1 FROM intake_shelves i WHERE i.shelf_id=r.shelf_id)
       ) THEN
        RAISE EXCEPTION 'The local intake layout differs from the verified starting point.';
    END IF;
END $$;

INSERT INTO shelf_subject_rules (shelf_id,subject_id,active)
SELECT shelf_id,subject_id,true FROM author_shelves
UNION
SELECT c.shelf_id,s.subject_id,true
FROM copy_before c JOIN placement_books b USING(book_id)
JOIN recommendation_subjects s ON s.code=b.author_group AND s.active
WHERE c.protect_location
ON CONFLICT (shelf_id,subject_id) DO UPDATE SET active=true, updated_at=now();

-- Copies of each title share a home shelf. Titles within a letter are distributed in author order.
CREATE TEMP TABLE author_homes ON COMMIT DROP AS
WITH destinations AS (
    SELECT code, array_agg(shelf_id ORDER BY position) AS shelf_ids
    FROM author_shelves GROUP BY code
), partitions AS (
    SELECT b.book_id, d.shelf_ids,
           ntile(cardinality(d.shelf_ids)) OVER (
               PARTITION BY b.author_group ORDER BY lower(b.author),lower(b.title),b.book_id
           ) AS slot
    FROM placement_books b JOIN destinations d ON d.code=b.author_group
    WHERE EXISTS (SELECT 1 FROM copy_before c WHERE c.book_id=b.book_id)
)
SELECT book_id,shelf_ids[slot] AS shelf_id FROM partitions;
ALTER TABLE author_homes ADD PRIMARY KEY (book_id);

CREATE TEMP TABLE plan ON COMMIT DROP AS
SELECT c.barcode,
       CASE WHEN c.protect_location THEN c.shelf_id
            WHEN b.author_group IS NOT NULL THEN h.shelf_id
            ELSE coalesce(c.shelf_id, thematic.shelf_id) END AS shelf_id
FROM copy_before c JOIN placement_books b USING(book_id)
LEFT JOIN author_homes h USING(book_id)
LEFT JOIN LATERAL (
    SELECT r.shelf_id
    FROM book_subject_assignments a JOIN shelf_subject_rules r USING(subject_id)
    JOIN recommendation_subjects s USING(subject_id)
    WHERE c.shelf_id IS NULL AND b.author_group IS NULL
      AND a.book_id=c.book_id AND r.active AND s.active
    ORDER BY a.primary_subject DESC,a.confidence DESC,r.shelf_id LIMIT 1
) thematic ON true;
ALTER TABLE plan ADD PRIMARY KEY (barcode);

UPDATE book_copies c SET shelf_id=p.shelf_id, updated_at=now()
FROM plan p WHERE c.barcode=p.barcode AND c.shelf_id IS DISTINCT FROM p.shelf_id;

DELETE FROM shelf_subject_rules r USING recommendation_subjects s, intake_shelves i
WHERE r.shelf_id=i.shelf_id AND r.subject_id=s.subject_id AND s.code='SHELF_UNCLASSIFIED';

UPDATE recommendation_subjects SET active=false, updated_at=now() WHERE code='SHELF_UNCLASSIFIED';

DO $$
BEGIN
    IF (SELECT md5(string_agg(jsonb_build_array(b.barcode,b.book_id,b.status,p.shelf_id)::text,
                                E'\n' ORDER BY b.barcode COLLATE "C"))
        FROM copy_before b JOIN plan p ON p.barcode=b.barcode)
           IS DISTINCT FROM
       (SELECT md5(string_agg(jsonb_build_array(c.barcode,c.book_id,c.status,c.shelf_id)::text,
                                E'\n' ORDER BY c.barcode COLLATE "C")) FROM book_copies c)
       OR EXISTS (
           SELECT 1 FROM book_copies c JOIN placement_books b USING(book_id)
           WHERE c.shelf_id IS NULL OR NOT EXISTS (
               SELECT 1 FROM shelf_subject_rules r JOIN recommendation_subjects s USING(subject_id)
               WHERE r.shelf_id=c.shelf_id AND r.active AND s.active
                 AND (s.code=b.author_group OR EXISTS (
                     SELECT 1 FROM book_subject_assignments a WHERE a.book_id=c.book_id AND a.subject_id=r.subject_id
                 ))
           )
       )
       OR EXISTS (
           SELECT 1 FROM shelves sh WHERE NOT EXISTS (
               SELECT 1 FROM shelf_subject_rules r JOIN recommendation_subjects s USING(subject_id)
               WHERE r.shelf_id=sh.shelf_id AND r.active AND s.active
           )
       ) THEN
        RAISE EXCEPTION 'Post-update barcode, status or placement verification failed.';
    END IF;
END $$;

SELECT count(*) FILTER (WHERE b.shelf_id IS NULL) AS newly_assigned,
       count(*) FILTER (WHERE b.shelf_id IS NOT NULL AND b.shelf_id<>p.shelf_id) AS relocated,
       count(*) FILTER (WHERE b.protect_location) AS protected_existing
FROM copy_before b JOIN plan p ON p.barcode=b.barcode;
SELECT count(*) FILTER (WHERE c.shelf_id IS NULL) AS unplaced,
       count(*) FILTER (WHERE b.author_group IS NOT NULL) AS author_copies,
       count(*) FILTER (WHERE b.author_group IS NULL) AS thematic_copies
FROM book_copies c JOIN placement_books b USING(book_id);

\if :apply
COMMIT;
\else
ROLLBACK;
\endif
