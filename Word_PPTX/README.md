# GÓI BÀN GIAO WORD VÀ POWERPOINT

Thư mục này dành cho thành viên viết báo cáo và làm slide. Nội dung đã được sắp theo mẫu báo cáo khóa luận 2025, không theo thứ tự tuần phát triển.

## Bắt đầu từ đây

1. Mở `01_KHUNG_NOI_DUNG/TUAN_12C_KHUNG_WORD_PPT_THEO_MAU_2025.md`.
2. Dùng `05_MAU_BAO_CAO/Mau bao cao_Do an_Khoa luan_2025.docx` làm file hình thức chính thức.
3. Dùng `01_KHUNG_NOI_DUNG/TUAN_12C_GOI_BAN_GIAO_MINH_CHUNG_KY_THUAT.md` để tra role, API, truy vết và màn hình.
4. Dùng `01_KHUNG_NOI_DUNG/TUAN_12C_NGUON_SO_LIEU_VA_CACH_TAI_TAO.md` trước khi chèn bất kỳ con số nào.
5. Lấy sơ đồ trong `03_SO_DO`, số liệu máy đọc được trong `04_SO_LIEU` và tài liệu nghiệp vụ trong `02_NGHIEP_VU`.
6. Dùng `02_NGHIEP_VU/TUAN_12D_CHOT_REPOSITORY_VA_BAN_GIAO.md` cho quality gate và `02_NGHIEP_VU/TUAN_12E_HOAN_THIEN_MOBILE_VA_FIREBASE.md` cho Mobile/FCM.
7. Thực hiện `DANH_SACH_ANH_CAN_CHUP.md` trên đúng commit đã được nghiệm thu.

## Nguyên tắc sử dụng

- File trong thư mục này là snapshot cập nhật đến ngày **21/10/2026**. Bản gốc vẫn nằm trong `../Docs`.
- Nếu mã nguồn hoặc số liệu thay đổi sau vòng nghiệm thu, phải cập nhật snapshot trước khi chốt Word/PPT.
- Không đổi số liệu, bỏ disclaimer hoặc viết rằng đã triển khai Internet nếu nhóm chưa nghiệm thu việc đó.
- Không chèn API key, JWT, password, OTP, `.env` hoặc dữ liệu cá nhân vào báo cáo/slide.
- Tạo mục lục, danh mục hình và danh mục bảng tự động trong Word.
- PowerPoint dùng cùng tên chức năng, sơ đồ và số liệu với báo cáo.

## Cấu trúc thư mục

| Thư mục | Nội dung |
|---|---|
| `01_KHUNG_NOI_DUNG` | Khung 5 chương, gói bàn giao trung tâm và nguồn số liệu |
| `02_NGHIEP_VU` | Phạm vi, quy định, thiết kế và hồ sơ tuần 10–12 |
| `03_SO_DO` | Nguồn Mermaid và PNG Use Case |
| `04_SO_LIEU` | JSON recommender, tải và RAG |
| `05_MAU_BAO_CAO` | Mẫu Word chính thức |
| `06_TAI_LIEU_GOC` | Các file Word gốc về đề cương, Use Case, công nghệ và quy định |

Danh mục đầy đủ nằm trong `DANH_MUC_TAI_LIEU.md`.
