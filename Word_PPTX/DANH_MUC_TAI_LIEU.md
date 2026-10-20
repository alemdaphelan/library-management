# DANH MỤC TÀI LIỆU TRONG GÓI WORD VÀ POWERPOINT

## 1. Khung nội dung

| File | Dùng để làm gì |
|---|---|
| `01_KHUNG_NOI_DUNG/TUAN_12C_KHUNG_WORD_PPT_THEO_MAU_2025.md` | Đề mục Mở đầu, 5 chương, Kết luận và khung 16 slide |
| `01_KHUNG_NOI_DUNG/TUAN_12C_GOI_BAN_GIAO_MINH_CHUNG_KY_THUAT.md` | Bảng role, API, truy vết, hình và màn hình |
| `01_KHUNG_NOI_DUNG/TUAN_12C_NGUON_SO_LIEU_VA_CACH_TAI_TAO.md` | Nguồn metric, checksum, cách diễn giải và chạy lại |
| `01_KHUNG_NOI_DUNG/BANG_DANH_GIA_TIEN_DO_CHI_TIET_TUAN_6_DEN_10.md` | Truy vết tiến độ; không dùng làm bố cục báo cáo |

## 2. Nghiệp vụ và quyết định thiết kế

| Nhóm | File chính |
|---|---|
| Phạm vi tuần 1–5 | `02_NGHIEP_VU/CHOT_PHAM_VI_VA_TRUY_VET_TUAN_1_DEN_5.md` |
| Mất/hỏng và thu phạt | `02_NGHIEP_VU/CHINH_SACH_MAT_HONG_VA_THU_PHAT_9C.md` |
| Tìm kiếm | `02_NGHIEP_VU/ADR-001-CHIEN-LUOC-TIM-KIEM-SACH.md` |
| Recommender | `02_NGHIEP_VU/TUAN_10A...` đến `TUAN_10E...` |
| Kiểm thử/vận hành | `02_NGHIEP_VU/TUAN_11_KIEM_THU_TICH_HOP_VA_VAN_HANH_CUC_BO.md` |
| RAG | `02_NGHIEP_VU/TUAN_12A...` và `TUAN_12B...` |
| Chốt kỹ thuật và bàn giao | `02_NGHIEP_VU/TUAN_12D_CHOT_REPOSITORY_VA_BAN_GIAO.md` |
| Mobile và Firebase | `02_NGHIEP_VU/TUAN_12E_HOAN_THIEN_MOBILE_VA_FIREBASE.md` |

## 3. Sơ đồ

- `analysis_class_diagram.mmd` và `design_class_diagram.mmd`.
- `huit_library_erd.mmd`.
- `system_architecture.mmd` và `local_deployment.mmd`.
- `circulation_state_machine.mmd`.
- `librarian_qr_flow.mmd` và `librarian_qr_wireframe.mmd`.
- `recommendation_pipeline.mmd`.
- `rag_pipeline.mmd` và `rag_query_sequence.mmd`.
- Thư mục `Mo_hinh_PNG` chứa Business/System Use Case dạng ảnh.

Người làm Word/PPT xuất Mermaid thành SVG hoặc PNG độ phân giải cao, rồi đặt caption theo chương. Không chụp ảnh màn hình mã Mermaid.

## 4. Số liệu

| File | Nội dung |
|---|---|
| `recommendation_evaluation_2026-10-19.json` | Đối chứng Popularity, Content và Hybrid; dữ liệu proxy |
| `week11_performance_2026-10-19.json` | Benchmark catalog cục bộ |
| `rag_evaluation_2026-10-20.json` | Đánh giá RAG 14 ca có kiểm soát |

Giá trị trình bày phải khớp JSON và giữ cảnh báo trong tài liệu nguồn số liệu.

## 5. Tài liệu Word gốc

- Mẫu báo cáo của khoa.
- Đề cương `CNTT-KLCN119.docx` và bản text tiện tra cứu.
- Business/System Use Case dạng Word.
- `Technologies.docx`.
- `Quy định thư viện.docx`.

`Phát thảo ứng dụng.docx` có dung lượng lớn và vẫn nằm tại `../Docs/Phát thảo ứng dụng.docx`; chỉ dùng khi cần đối chiếu phác thảo giao diện cũ, không xem là ảnh nghiệm thu hiện tại.

## 6. Thứ tự dùng tài liệu

1. Khung theo mẫu 2025.
2. Gói bàn giao trung tâm.
3. Nguồn số liệu.
4. Sơ đồ và tài liệu nghiệp vụ.
5. Ảnh chụp trên bản nghiệm thu.

Khi hai tài liệu mâu thuẫn, ưu tiên mã nguồn/test hiện tại và hỏi thành viên code trước khi viết.
