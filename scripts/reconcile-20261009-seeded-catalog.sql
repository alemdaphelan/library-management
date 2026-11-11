\set ON_ERROR_STOP on
-- One-time recovery for the verified October 9 seed cohort. Take a verified backup first.
-- psql -v apply=0 -f ... rolls the entire recovery back. Pass apply=1 to commit.
\if :{?apply}
\else
\set apply 0
\endif

BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '180s';
LOCK TABLE books,
book_copies,
receipt_details,
reviews,
wishlists,
waitlists,
loan_details,
book_subject_assignments,
digital_documents,
disposal_proposals,
material_classifications IN SHARE ROW EXCLUSIVE MODE;
CREATE TEMP TABLE verified_catalog_map ON COMMIT DROP AS
SELECT
    c.book_id AS old_id, b.book_id AS new_id
FROM
    book_copies c
    JOIN books b ON b.book_id = c.book_id + 269922
WHERE
    c.book_id BETWEEN 1653 AND 271574
    AND c.barcode = 'BC-' || c.book_id || '-1'
    AND c.created_at = timestamp '2026-10-09 14:58:21.900779'
    AND c.shelf_id IN ((abs(hashtext(coalesce(b.publisher, 'Unknown'))) % 160) + 1, (abs(hashtext(coalesce(b.publisher, ''))) % 160) + 1);

DO $$
BEGIN
    IF (SELECT count(*) FROM verified_catalog_map) <> 269922
       OR (SELECT count(*) FROM book_copies WHERE book_id BETWEEN 1653 AND 271574) <> 269922
       OR EXISTS (SELECT 1 FROM books WHERE book_id BETWEEN 1653 AND 271574)
       OR (SELECT md5(string_agg(isbn, E'\n' ORDER BY book_id)) FROM books WHERE book_id BETWEEN 271575 AND 541496)
           IS DISTINCT FROM '693232894536f885e0e9a18fd9ad6129'
       OR NOT EXISTS (SELECT 1 FROM databasechangelog WHERE id='load_books_csv_cleaned')
       OR NOT EXISTS (SELECT 1 FROM databasechangelog WHERE id='seed_book_copies_with_normalization') THEN
        RAISE EXCEPTION 'Seed cohort differs from the verified catalog. Recovery stopped.';
    END IF;
    IF EXISTS (SELECT 1 FROM disposal_proposals WHERE book_id BETWEEN 1653 AND 271574)
       OR EXISTS (SELECT 1 FROM book_subject_assignments WHERE book_id BETWEEN 1653 AND 271574)
       OR EXISTS (SELECT 1 FROM material_classifications WHERE material_kind='BOOK' AND material_id BETWEEN 1653 AND 271574) THEN
        RAISE EXCEPTION 'Unexpected source assignments or snapshots. Reconcile manually before updating.';
    END IF;
END $$;

ALTER TABLE verified_catalog_map
    ADD PRIMARY KEY (old_id);

CREATE TEMP TABLE copy_fingerprint ON COMMIT DROP AS
SELECT
    count(*) AS copies, md5(string_agg(concat_ws('|', barcode, status, shelf_id), E'\n' ORDER BY barcode)) AS fingerprint
FROM
    book_copies;

UPDATE
    book_copies t
SET
    book_id = m.new_id
FROM
    verified_catalog_map m
WHERE
    t.book_id = m.old_id;

UPDATE
    receipt_details t
SET
    book_id = m.new_id
FROM
    verified_catalog_map m
WHERE
    t.book_id = m.old_id;

UPDATE
    reviews t
SET
    book_id = m.new_id
FROM
    verified_catalog_map m
WHERE
    t.book_id = m.old_id;

UPDATE
    wishlists t
SET
    book_id = m.new_id::text
FROM
    verified_catalog_map m
WHERE
    t.book_id = m.old_id::text;

UPDATE
    waitlists t
SET
    book_id = m.new_id
FROM
    verified_catalog_map m
WHERE
    t.book_id = m.old_id;

UPDATE
    loan_details t
SET
    circulation_book_id = m.new_id
FROM
    verified_catalog_map m
WHERE
    t.circulation_book_id = m.old_id;

UPDATE
    digital_documents t
SET
    book_id = m.new_id
FROM
    verified_catalog_map m
WHERE
    t.book_id = m.old_id;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM book_copies c LEFT JOIN books b ON b.book_id=c.book_id WHERE b.book_id IS NULL)
       OR NOT EXISTS (
           SELECT 1 FROM copy_fingerprint before
           CROSS JOIN (SELECT count(*) copies, md5(string_agg(concat_ws('|',barcode,status,shelf_id), E'\n' ORDER BY barcode)) fingerprint FROM book_copies) after
           WHERE before.copies=after.copies AND before.fingerprint=after.fingerprint
       ) THEN
        RAISE EXCEPTION 'Recovery did not preserve all copies or left unmatched catalog links.';
    END IF;
END $$;

SELECT
    count(*) AS total_copies,
    count(*) FILTER (WHERE c.shelf_id IS NULL) AS unplaced_copies,
    count(*) FILTER (WHERE b.book_id IS NULL) AS unmatched_copies
FROM
    book_copies c
    LEFT JOIN books b ON b.book_id = c.book_id;

\if :apply
COMMIT;
\else
ROLLBACK;
\endif
