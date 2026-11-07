--liquibase formatted sql
--changeset codex:47-shelf-author-groups splitStatements:false
CREATE OR REPLACE FUNCTION shelf_author_group(author_name text, book_title text) RETURNS text
LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
    WITH initial AS (
        SELECT left(translate(upper(regexp_replace(normalize(coalesce(
            nullif(regexp_replace(author_name, '^[[:space:][:punct:]]+', ''), ''),
            regexp_replace(book_title, '^[[:space:][:punct:]]+', ''), ''
        ), NFD), U&'[\0300-\036f]', '', 'g')), 'ĐØŁÆŒ', 'DOLAO'), 1) AS letter
    )
    SELECT 'SHELF_AUTHOR_' || CASE WHEN letter COLLATE "C" ~ '^[A-Z]$' THEN letter ELSE 'OTHER' END
    FROM initial
$$;

INSERT INTO recommendation_subjects (code, name, description, active, created_at, updated_at)
SELECT 'SHELF_AUTHOR_' || chr(letter), 'Bộ sưu tập theo tác giả · ' || chr(letter),
       'Dùng cho đầu sách chưa có chủ đề hoạt động. Nếu thiếu tác giả, dùng chữ cái đầu của nhan đề.',
       true, now(), now()
FROM generate_series(ascii('A'), ascii('Z')) letter
UNION ALL
SELECT 'SHELF_AUTHOR_OTHER', 'Bộ sưu tập theo tác giả · Ký tự khác',
       'Dùng cho đầu sách chưa có chủ đề hoạt động có khóa xếp ngoài A–Z.',
       true, now(), now()
ON CONFLICT (code) DO NOTHING;
