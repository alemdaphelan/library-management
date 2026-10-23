# GÓI BÀN GIAO KIỂM THỬ CHỨC NĂNG

Thư mục này dành cho thành viên kiểm thử độc lập sau khi nhận repository đã được chốt ở gói 12D. Mục tiêu là xác nhận hệ thống chạy được trên môi trường khác, các role có đúng quyền và các luồng nghiệp vụ chính không bị lỗi.

## Thứ tự thực hiện

1. Đọc `01_HUONG_DAN_DUNG_MOI_TRUONG_TEST.md` và dựng hệ thống.
2. Ghi mã commit/nhánh, hệ điều hành, trình duyệt và thiết bị vào biên bản.
3. Thực hiện `06_CAU_HINH_FIREBASE_VA_KIEM_THU_PUSH.md` để chốt package ID `vn.edu.huit.library`, cấu hình Firebase và chuẩn bị APK nghiệm thu.
4. Thực hiện `02_CHECKLIST_TEST_TOAN_BO_HE_THONG.md` từ đầu đến cuối.
5. Mỗi lỗi tạo một bản sao từ `03_MAU_BAO_LOI.md`, kèm ảnh/video/log đã che dữ liệu nhạy cảm.
6. Tổng hợp kết quả vào `04_BIEN_BAN_NGHIEM_THU.md`.
7. Đối chiếu phiên bản, tài khoản demo và gate trong `05_THONG_TIN_BAN_GIAO_12D.md`.
8. Gửi lại lỗi có bước tái hiện rõ ràng cho thành viên code; sau khi nhận bản sửa chỉ chạy lại ca lỗi và nhóm hồi quy liên quan.
9. Với bản sửa UAT-A, chạy thêm `07_UAT_A_KIEM_THU_LAI.md` trước khi tiếp tục các gói UAT còn lại.
10. Với bản sửa UAT-B, chạy `08_UAT_B_GIO_MUON_VA_TRA_NHIEU_PHIEU.md` để xác nhận giới hạn mượn, thời hạn giữ và trả sách liên phiếu.
11. Với bản sửa UAT-C, chạy `09_UAT_C_DANH_GIA_TU_SACH_VA_TIM_KIEM_NOI_DUNG.md` để kiểm tra dữ liệu tương tác sách thật.
12. Với bản sửa UAT-D, chạy `10_UAT_D_THONG_BAO_TRANG_THAI_VA_THE_THU_VIEN.md` để kiểm tra modal thông báo, cài đặt kênh nhận tin, trạng thái tài khoản và thẻ thư viện điện tử.
13. Với bản sửa UAT-E, chạy `11_UAT_E_TRANG_CHU_VA_CHONG_SPAM_GIU_CHO.md` để kiểm tra trang chủ không còn dữ liệu giả và quy tắc ba lần không nhận sách.
14. Với bản chốt UAT-F, chạy `12_UAT_F_DE_XUAT_MUA_SACH_VA_QUY_DINH.md` để kiểm tra hồ sơ đề xuất đầy đủ, duyệt tạo đầu sách 0 bản sao và nội dung quy định cuối cùng.

## Nguyên tắc

- Không đưa `.env`, API key, JWT, mật khẩu, OTP hoặc dữ liệu cá nhân vào Git, ảnh hay báo lỗi.
- Không kiểm thử trên dữ liệu production.
- Không tự sửa mã nguồn trước khi báo lỗi, ngoại trừ thay đổi cấu hình triển khai/package ID được chỉ định rõ trong `06_CAU_HINH_FIREBASE_VA_KIEM_THU_PUSH.md`; luôn ghi đúng commit đang kiểm thử.
- Không đánh dấu `FAIL` cho chức năng ngoài phạm vi: đặt phòng, kiosk, triển khai Internet hoặc các mở rộng AI chưa cam kết.
- Không đánh dấu `FAIL` cho giới hạn mượn theo tiền thế chân hoặc gửi SMS: phạm vi hiện tại dùng giới hạn 3 tài liệu, công nợ và trạng thái thẻ; kênh thông báo là trong ứng dụng, email và Firebase Push.
- Nếu thiếu dữ liệu hoặc tài khoản khiến không thể test, dùng trạng thái `BLOCKED`, không dùng `FAIL`.

## Nguồn tham chiếu

- Bản snapshot tiện tra cứu nằm trong `00_TAI_LIEU_THAM_CHIEU`.
- Tiến độ và phạm vi: `../Docs/BANG_DANH_GIA_TIEN_DO_CHI_TIET_TUAN_6_DEN_10.md`.
- Nghiệp vụ mất/hỏng, thu phạt: `../Docs/CHINH_SACH_MAT_HONG_VA_THU_PHAT_9C.md`.
- Kết quả kiểm thử cục bộ: `../Docs/TUAN_11_KIEM_THU_TICH_HOP_VA_VAN_HANH_CUC_BO.md`.
- RAG: `../Docs/TUAN_12A_CHATBOT_RAG_GEMINI.md` và `../Docs/TUAN_12B_DANH_GIA_CHATBOT_RAG.md`.
- Bản chốt kỹ thuật: `../Docs/TUAN_12D_CHOT_REPOSITORY_VA_BAN_GIAO.md`.
- Mobile/FCM: `../Docs/TUAN_12E_HOAN_THIEN_MOBILE_VA_FIREBASE.md`.
- API tương tác: `http://localhost:8080/swagger-ui/index.html` sau khi Backend chạy.

## Trạng thái trước khi bàn giao

Chức năng tuần 1–11, các gói 12A–12E và các bản sửa UAT-A đến UAT-F đã hoàn thành ở mức mã nguồn. Gate cục bộ cuối ngày 24/10/2026 đạt **122/122 Backend unit test**, **82/82 Web test** và **Web production build**. Ba integration test dùng Testcontainers chưa được chạy lại ở lượt cuối vì máy hiện tại không có Docker daemon; thành viên kiểm thử phải chạy full Backend suite sau khi dựng Docker và ghi kết quả vào biên bản.

Thành viên kiểm thử/triển khai nhận luôn phần đổi package ID sang `vn.edu.huit.library`, cấu hình Firebase và xác nhận FCM trên thiết bị thật theo tài liệu `06_CAU_HINH_FIREBASE_VA_KIEM_THU_PUSH.md`; lỗi phản hồi sẽ được chuyển lại cho thành viên code.
