# HUIT Library - Cổng Tri thức Số (Khóa Luận Tốt Nghiệp)

![HUIT Library Banner](https://img.shields.io/badge/HUIT%20Library-C%E1%BB%95ng%20Tri%20Th%E1%BB%A9c%20S%E1%BB%91-0284c7?style=for-the-badge)

Dự án Hệ thống Thư viện thông minh dành cho sinh viên và thủ thư Đại học Công Thương TP.HCM (HUIT). Dự án bao gồm hệ sinh thái toàn diện với Phân hệ Web (Angular) và Phân hệ Mobile App (Flutter), kết hợp sức mạnh của Trí tuệ Nhân tạo (Google Gemini) để nâng cao trải nghiệm người dùng.

## 🌟 Tính năng nổi bật

### Phân hệ Sinh viên
*   **Trang chủ & Tra cứu:** Giao diện tối giản, hiện đại chuẩn Academic Blue. Tìm kiếm tài liệu, sách mượn nhanh chóng.
*   **Gợi ý Cá nhân hóa:** Thuật toán AI phân tích lịch sử và chuyên ngành để đưa ra các đầu sách phù hợp nhất.
*   **Trợ lý ảo AI (Chatbot RAG):** Tích hợp Google Gemini 3.6 Flash và kiến trúc RAG, giải đáp lập tức các quy định, giờ mở cửa và hướng dẫn mượn trả bằng giọng văn tự nhiên.
*   **Theo dõi sách đang mượn (Mobile):** Quản lý thời hạn trả sách, thanh tiến độ thông minh (Progress Bar) và thông báo mức phạt trực quan.
*   **Thẻ thư viện QR:** Đăng nhập một lần (HUIT SSO) và tạo mã QR dùng để mượn/trả sách ngay trên điện thoại.

### Phân hệ Thủ thư (Sắp ra mắt)
*   **Quản lý Sách & Danh mục:** Thêm, sửa, xóa thông tin tài liệu.
*   **Theo dõi Mượn/Trả:** Quản lý quy trình lưu thông tài liệu.
*   **Quét Mã vạch (Mobile App):** Dùng camera điện thoại để quét mã vạch sách hoặc mã QR sinh viên, xử lý nghiệp vụ mượn/trả trong vòng 2 giây.

## 🛠️ Công nghệ sử dụng (Tech Stack)

*   **Frontend Web:** Angular 17, HTML5, Vanilla CSS (Glassmorphism, Animations).
*   **Mobile App:** Flutter & Dart.
*   **Backend & Cơ sở dữ liệu:** C# / .NET, Entity Framework, SQL Server.
*   **Trí tuệ nhân tạo (AI):** Google Gemini 3.6 Flash API, Kiến trúc RAG (Retrieval-Augmented Generation).

## 📁 Cấu trúc thư mục (Project Structure)

```text
KhoaLuan/
│
├── web-app/            # Phân hệ Web (Angular)
│   ├── src/app/        # Source code Angular (Components, Pages, Services)
│   └── ...
│
├── mobile-app/         # Phân hệ Mobile App (Flutter)
│   ├── lib/screens/    # Màn hình giao diện (Home, Login, Search, Borrowed)
│   └── ...
│
└── README.md           # Tài liệu mô tả dự án
```

## 🚀 Hướng dẫn cài đặt (Setup Instructions)

### 1. Phân hệ Web (Angular)
Yêu cầu: Node.js (v18+) và Angular CLI.
```bash
cd web-app
npm install
npm run start
```
Truy cập ứng dụng tại: `http://localhost:4200`

### 2. Phân hệ Mobile App (Flutter)
Yêu cầu: Flutter SDK và Android Studio / VS Code.
```bash
cd mobile-app
flutter pub get
flutter run
```

## 👥 Nhóm Thực hiện (Contributors)
*   Sinh viên thực hiện: ... *(Điền tên các thành viên vào đây)*
*   MSSV: ...
*   Giảng viên hướng dẫn: ... *(Điền tên GV vào đây)*

---
*Đồ án Khóa luận Tốt nghiệp - Khoa Công nghệ Thông tin - Trường Đại học Công Thương TP.HCM (HUIT)*
