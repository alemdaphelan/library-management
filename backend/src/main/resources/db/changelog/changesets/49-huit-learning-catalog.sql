--liquibase formatted sql
--changeset codex:49-huit-learning-catalog splitStatements:false

INSERT INTO categories (name, description, created_at, updated_at)
SELECT 'Giáo dục đại cương', 'Tài liệu nền tảng dùng chung cho các khoa và ngành', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Giáo dục đại cương');

CREATE TEMP TABLE huit_learning_seed (
    title text PRIMARY KEY,
    author text NOT NULL,
    publisher text,
    publish_year integer,
    category_name text NOT NULL,
    subject_code text NOT NULL,
    faculty_id text,
    major_code text,
    bibliography_url text NOT NULL,
    curriculum_url text,
    synopsis text NOT NULL
) ON COMMIT DROP;

INSERT INTO huit_learning_seed VALUES
    ('Đại số tuyến tính', 'Nguyễn Hữu Việt Hưng', 'Đại học Quốc gia Hà Nội', NULL,
     'Giáo dục đại cương', 'GENERAL_FOUNDATION', NULL, NULL,
     'https://phucvu.thuvientphcm.gov.vn/Item/ItemDetail/754627',
     'https://thuvien.huit.edu.vn/News/NewDetail/khoa-hoc-dinh-duong-va-am-thuc',
     'Tài liệu Toán nền tảng về ma trận, hệ phương trình tuyến tính và không gian vectơ.'),
    ('Giáo trình xác suất và thống kê', 'Phạm Xuân Kiều', 'Giáo dục', 2004,
     'Giáo dục đại cương', 'GENERAL_FOUNDATION', NULL, NULL,
     'https://thuvien.huit.edu.vn/digitaldocument/detail?fileid=3961&treeid=-1',
     'https://fit.huit.edu.vn/dao-tao/nganh-cong-nghe-thong-tin-he-dai-hoc',
     'Tài liệu tham khảo môn thống kê xác suất.'),
    ('Toán cao cấp A2 - C2', 'Nguyễn Văn Kính (chủ biên)', 'ĐHQG TP.HCM', NULL,
     'Giáo dục đại cương', 'GENERAL_FOUNDATION', NULL, NULL,
     'https://thuvien.huit.edu.vn/News/NewDetail/giao-trinh-dang-phat-hanh-tai-thu-vien-khoa-hoc-ung-dung',
     NULL, 'Giáo trình Toán nền tảng trong danh mục phát hành của Thư viện HUIT.'),
    ('Giáo trình Cơ sở dữ liệu', 'Hoàng Thị Liên Chi, Nguyễn Văn Lễ', NULL, NULL,
     'Công nghệ thông tin', 'IT_DATA_AI', 'FIT', '7480201',
     'https://thuvien.huit.edu.vn/News/NewDetail/giao-trinh-dang-phat-hanh-tai-thu-vien-cong-nghe-thong-tin',
     'https://fit.huit.edu.vn/dao-tao/nganh-cong-nghe-thong-tin-he-dai-hoc',
     'Giáo trình lưu hành nội bộ phục vụ ngành Công nghệ thông tin.'),
    ('Giáo trình Cấu trúc dữ liệu và giải thuật', 'Trần Văn Thọ', NULL, NULL,
     'Công nghệ thông tin', 'IT_DATA_AI', 'FIT', '7480201',
     'https://thuvien.huit.edu.vn/News/NewDetail/giao-trinh-dang-phat-hanh-tai-thu-vien-cong-nghe-thong-tin',
     'https://fit.huit.edu.vn/dao-tao/nganh-cong-nghe-thong-tin-he-dai-hoc',
     'Giáo trình lưu hành nội bộ về cấu trúc dữ liệu và thuật toán.'),
    ('Giáo trình Phân tích hóa lý thực phẩm 1',
     'Lê Thị Hồng Ánh (chủ biên), Nguyễn Thị Hải Hòa, Phạm Thị Cẩm Hoa, Nguyễn Cẩm Hường, Vũ Hoàng Yến', 'ĐHQG TP.HCM', NULL,
     'Khoa học', 'FOOD_TECH', 'FOODTECH', '7540101',
     'https://thuvien.huit.edu.vn/News/NewDetail/giao-trinh-dang-phat-hanh-tai-thu-vien-cong-nghe-thuc-pham',
     'https://foodtech.huit.edu.vn/Upload/file/Chuong%20trinh%20dao%20tao/nganh%20CNTP%20khoa%2008%20tro%20di/chuong%20trinh%20chung.pdf',
     'Giáo trình phục vụ học phần Phân tích hóa lý thực phẩm 1.'),
    ('Giáo trình Hóa sinh học thực phẩm',
     'Lê Thị Hồng Ánh (chủ biên), Trần Thị Minh Hà, Nguyễn Thủy Hà, Nguyễn Thu Sang, Lê Thị Thúy Hằng, Nguyễn Phan Khánh Hòa', 'ĐHQG TP.HCM', NULL,
     'Khoa học', 'FOOD_TECH', 'FOODTECH', '7540101',
     'https://thuvien.huit.edu.vn/News/NewDetail/giao-trinh-dang-phat-hanh-tai-thu-vien-cong-nghe-thuc-pham',
     'https://foodtech.huit.edu.vn/Upload/file/Chuong%20trinh%20dao%20tao/nganh%20CNTP%20khoa%2008%20tro%20di/chuong%20trinh%20chung.pdf',
     'Giáo trình phục vụ học phần Hóa sinh học thực phẩm.'),
    ('Giáo trình Hệ thống thông tin Kế toán',
     'Trần Phước (chủ biên), Phạm Anh Tuấn, Trần Diệu Hương', NULL, NULL,
     'Kinh tế', 'FINANCE_ACCOUNTING', 'FINANCE_ACCOUNTING', '7340301',
     'https://thuvien.huit.edu.vn/News/NewDetail/giao-trinh-dang-phat-hanh-tai-thu-vien-tai-chinh-ke-toan',
     'https://ktckt.huit.edu.vn/tckt-chuong-trinh-dao-tao/chuong-trinh-dao-tao-dai-hoc-nganh-ke-toan-ap-dung-cho-khoa-tuyen-sinh-tu-nam-2024',
     'Giáo trình lưu hành nội bộ phục vụ ngành Kế toán.'),
    ('Giáo trình Kế toán Tài chính doanh nghiệp – tập 1',
     'Trần Phước (chủ biên) và cộng sự', NULL, NULL,
     'Kinh tế', 'FINANCE_ACCOUNTING', 'FINANCE_ACCOUNTING', '7340301',
     'https://thuvien.huit.edu.vn/News/NewDetail/giao-trinh-dang-phat-hanh-tai-thu-vien-tai-chinh-ke-toan',
     'https://ktckt.huit.edu.vn/tckt-chuong-trinh-dao-tao/chuong-trinh-dao-tao-dai-hoc-nganh-ke-toan-ap-dung-cho-khoa-tuyen-sinh-tu-nam-2024',
     'Giáo trình lưu hành nội bộ về kế toán tài chính doanh nghiệp.');

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM huit_learning_seed s
        WHERE NOT EXISTS (SELECT 1 FROM categories c WHERE c.name = s.category_name)
           OR NOT EXISTS (SELECT 1 FROM recommendation_subjects r WHERE r.code = s.subject_code AND r.active)
           OR (s.major_code IS NOT NULL AND
               (SELECT count(*) FROM academic_majors a
                WHERE a.major_code = s.major_code AND a.faculty_id = s.faculty_id) <> 1)
    ) THEN
        RAISE EXCEPTION 'Missing or ambiguous HUIT learning catalog classification';
    END IF;
END $$;

-- Import bibliographic records only. Local copies, barcodes, files, prices and shelf positions require separate evidence.
CREATE TEMP TABLE huit_learning_inserted ON COMMIT DROP AS
WITH inserted AS (
    INSERT INTO books (title, author, publisher, publish_year, language, material_type, is_digital,
                       category_id, description, created_at, updated_at)
    SELECT s.title, s.author, s.publisher, s.publish_year, 'Tiếng Việt', 'BOOK', false,
           (SELECT min(c.category_id) FROM categories c WHERE c.name = s.category_name),
           concat_ws(E'\n', s.synopsis, 'Nguồn thư mục ' || s.bibliography_url,
                     'Nguồn chương trình ' || s.curriculum_url),
           now(), now()
    FROM huit_learning_seed s
    WHERE NOT EXISTS (
        SELECT 1 FROM books b
        WHERE lower(btrim(b.title)) = lower(s.title) AND lower(btrim(b.author)) = lower(s.author)
    )
    ORDER BY s.title
    RETURNING book_id, title
)
SELECT i.book_id, s.subject_code, s.faculty_id, s.major_code,
       concat_ws(' | ', s.bibliography_url, s.curriculum_url) source_reference
FROM inserted i JOIN huit_learning_seed s ON s.title = i.title;

INSERT INTO book_subject_assignments (book_id, subject_id, confidence, classification_source, evidence, primary_subject)
SELECT i.book_id, r.subject_id, 1, 'IMPORTED', i.source_reference, true
FROM huit_learning_inserted i JOIN recommendation_subjects r ON r.code = i.subject_code;

CREATE TEMP TABLE huit_learning_profiles ON COMMIT DROP AS
SELECT gen_random_uuid() profile_id, i.book_id, i.source_reference, a.major_id, a.major_name, a.major_code,
       a.faculty_id, f.faculty_name, localtimestamp recorded_at
FROM huit_learning_inserted i
JOIN academic_majors a ON a.major_code = i.major_code AND a.faculty_id = i.faculty_id
JOIN academic_faculties f ON f.faculty_id = a.faculty_id;

INSERT INTO material_classifications
    (profile_id, material_kind, material_id, version, source_reference, updated_by, updated_by_name, updated_at)
SELECT profile_id, 'BOOK', book_id, 1, source_reference, '00000000-0000-0000-0000-000000000049',
       'Liquibase · Danh mục học liệu HUIT', recorded_at
FROM huit_learning_profiles;

INSERT INTO material_major_assignments (assignment_id, profile_id, major_id)
SELECT gen_random_uuid(), profile_id, major_id FROM huit_learning_profiles;

INSERT INTO material_classification_history
    (event_id, profile_id, version, actor_id, actor_name, recorded_at, before_snapshot, after_snapshot)
SELECT gen_random_uuid(), p.profile_id, 1, p.updated_by, p.updated_by_name, p.updated_at, NULL,
       jsonb_build_object('version', 1, 'sourceReference', p.source_reference,
           'actorId', p.updated_by, 'actorName', p.updated_by_name, 'updatedAt', p.updated_at,
           'majors', jsonb_build_array(jsonb_build_object(
               'majorId', i.major_id, 'majorName', i.major_name, 'majorCode', i.major_code,
               'facultyId', i.faculty_id, 'facultyName', i.faculty_name)))::text
FROM huit_learning_profiles i JOIN material_classifications p USING (profile_id);
