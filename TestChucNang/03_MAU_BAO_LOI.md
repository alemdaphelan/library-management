# MẪU BÁO LỖI

Tạo một file riêng cho mỗi lỗi, tên dạng `BUG-XXX-ten-ngan.md`.

## Thông tin chung

| Trường | Nội dung |
|---|---|
| Mã lỗi | BUG-XXX |
| Tiêu đề |  |
| Mức độ | Critical / High / Medium / Low |
| Trạng thái | New / Confirmed / Fixed / Retest Passed / Reopened |
| Người phát hiện |  |
| Ngày giờ |  |
| Commit/nhánh |  |
| Thành phần | Backend / Web / Mobile / Database / RAG / Recommender / Docker |
| Môi trường | OS, trình duyệt/thiết bị, phiên bản công cụ |
| Tài khoản/role | Chỉ ghi role hoặc mã tài khoản demo, không ghi mật khẩu |

## Tiền điều kiện

Mô tả dữ liệu và trạng thái cần có trước khi chạy. Ví dụ: phiếu đang `RESERVED`, bản sao có barcode demo, tài khoản không còn nợ.

## Các bước tái hiện

1.
2.
3.

## Kết quả mong đợi



## Kết quả thực tế



## Tần suất

- [ ] Luôn xảy ra.
- [ ] Thỉnh thoảng, ghi tỷ lệ.
- [ ] Chỉ xảy ra một lần.

## Bằng chứng

- Ảnh/video:
- HTTP method/endpoint/status:
- Thời điểm để tra log:
- Log đã che dữ liệu nhạy cảm:

## Phạm vi ảnh hưởng

Nêu dữ liệu có bị ghi dở dang, tiền/trạng thái có sai, role nào bị ảnh hưởng và có cách tránh tạm thời hay không.

## Kết quả kiểm thử lại

| Commit sửa | Ca chạy lại | Kết quả | Ghi chú |
|---|---|---|---|
|  |  |  |  |

### Phân loại mức độ

- `Critical`: mất dữ liệu, lộ secret, vượt quyền, tính tiền sai nghiêm trọng hoặc toàn hệ thống không dùng được.
- `High`: luồng bắt buộc không hoàn tất và không có cách tránh hợp lý.
- `Medium`: chức năng sai một phần hoặc có cách tránh.
- `Low`: lỗi hiển thị/câu chữ không cản trở nghiệp vụ.
