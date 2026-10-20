# Tuần 11 — Kiểm thử, tích hợp và vận hành cục bộ

Ngày chốt: 19/10/2026

## 1. Phạm vi đã thống nhất

Tuần 11 được nghiệm thu trên môi trường phát triển cục bộ. Việc đưa Web lên hosting hoặc máy chủ công cộng không thuộc đợt này và được để lại sau. Phạm vi gồm quality gate, E2E nghiệp vụ, kiểm thử đồng thời, kiểm thử tải, rà soát bảo mật, Docker smoke test và diễn tập backup/restore.

## 2. Quality gate

| Thành phần | Kết quả | Phạm vi |
|---|---:|---|
| Backend | 101/101 test | Unit, contract, PostgreSQL/Redis/Mailpit Testcontainers và HTTP E2E |
| Web Angular | 66/66 test | Component/service/guard và production build |
| Mobile Flutter | 5/5 test | Widget, parse gợi ý và response JSON/text/rỗng |
| Mobile static analysis | Không có lỗi | `flutter analyze` |
| Dependency Web | 0 lỗ hổng theo `npm audit` | Đã cập nhật Angular 21 và công cụ build trong cùng major |
| Docker Compose | Hợp lệ | `docker compose --env-file .env.example config --quiet` |

Workflow `.github/workflows/week6-quality.yml` được nâng thành **Week 11 quality gate** nhưng giữ nguyên tên tệp để không làm hỏng lịch sử. CI chạy Backend test/package, Web audit/build/test, Mobile analyze/test/build APK debug và kiểm tra manifest Docker Compose.

## 3. E2E nghiệp vụ bắt buộc

Test HTTP `week11HttpE2eCoversLoginSearchReservationQrCheckoutOverdueFineAndReturn` chạy qua controller, Spring Security, service và PostgreSQL thật:

1. Sinh viên và thủ thư đăng nhập để nhận JWT.
2. Tra cứu sách qua API catalog.
3. Sinh viên đặt giữ một bản sao đang sẵn sàng.
4. Thủ thư duyệt phiếu.
5. Sinh viên tạo QR phiếu; thủ thư xác thực QR và nhận hành động `CHECKOUT` do server quyết định.
6. Thủ thư checkout bằng barcode và idempotency key.
7. Test đưa hạn trả về quá khứ, chạy maintenance, kiểm tra tiền phạt và công nợ được ghi nhận.
8. Thủ thư trả sách; phiếu, chi tiết và bản sao chuyển về trạng thái cuối đúng.

Test cũ về hai checkout đồng thời vẫn xác nhận chỉ một transaction được phép thành công. Test rollback vẫn xác nhận lỗi ghi audit không để lại phiếu, chi tiết hoặc bản sao ở trạng thái dở dang.

## 4. Kiểm thử hiệu năng

Kịch bản cục bộ dùng `scripts/week11-load-test.mjs`, gọi catalog trên database hiện tại:

| Chỉ số | Kết quả |
|---|---:|
| Số request | 200 |
| Mức đồng thời | 20 |
| HTTP 200 | 200/200 |
| Lỗi | 0 |
| p50 | 618,01 ms |
| p95 | 807,54 ms |
| Max | 1.089,75 ms |
| Thông lượng | 30,90 request/giây |
| Ngưỡng nghiệm thu p95 | ≤ 1.500 ms |

Kết quả chi tiết nằm trong `week11_performance_2026-10-19.json`. Đây là benchmark trên máy phát triển và không được diễn giải thành cam kết tải production.

## 5. Rà soát bảo mật

- CORS không còn dùng wildcard khi bật credential; chỉ các origin trong `CORS_ALLOWED_ORIGINS` được chấp nhận.
- Nginx không tự chèn `Access-Control-Allow-Origin: *`; Spring Security là nguồn cấu hình CORS duy nhất.
- Test tự động xác nhận portal cục bộ được phép và origin lạ bị từ chối.
- Các endpoint QR resolve, checkout, return, quản trị, đánh giá thuật toán và tài liệu số tiếp tục có contract test phân quyền.
- Angular Router và chuỗi công cụ build đã được nâng lên bản vá trong Angular 21; `npm audit` trả về 0 lỗ hổng.
- Health endpoint chỉ công khai trạng thái tổng quát, không lộ chi tiết thành phần.
- Credential thật không được đưa vào Git; `.env.example` chỉ chứa giá trị minh họa.

Đây là rà soát phù hợp phạm vi đồ án, không thay thế pentest độc lập hoặc kiểm toán production.

## 6. Vận hành cục bộ, smoke test và khôi phục

- Backend, PostgreSQL và Redis có healthcheck; Nginx chờ Backend healthy trước khi nhận request.
- Có endpoint `/actuator/health` và script `scripts/week11-smoke.ps1` kiểm tra manifest, health và catalog.
- `scripts/backup-postgres.ps1` tạo backup PostgreSQL định dạng custom.
- `scripts/verify-backup-restore.ps1` chỉ restore vào database tạm có tiền tố `huit_week11_verify_`, kiểm tra số bảng rồi xóa database tạm.
- Lần diễn tập ngày 19/10/2026 tạo backup 18.933.805 byte và restore thành công 48 bảng; database chính không bị ghi đè.

## 7. Lỗi được sửa trong Tuần 11

Mobile trước đây luôn giải mã mọi response thành JSON. Endpoint hủy đặt giữ trả văn bản thuần nên thao tác có thể phát sinh `FormatException` sau khi Backend đã commit. `ApiService` hiện hỗ trợ đúng ba dạng response thành công: JSON, văn bản và body rỗng; đã có regression test.

## 8. Kết luận

Tuần 11 hoàn thành trong phạm vi đã thống nhất: chạy và nghiệm thu cục bộ, không triển khai Web ra Internet. Công việc còn chuyển sang phần kỹ thuật Tuần 12 là đóng gói bản demo, dữ liệu/tài khoản demo, kịch bản trình diễn và bàn giao minh chứng; không mở rộng thêm tính năng mới.
