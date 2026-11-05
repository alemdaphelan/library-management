# Backend máy bạn/server — kiểm tra các lỗi trong 6.docx

Mobile trong ảnh dùng **backend khác máy đang sửa code**. Đã kiểm tra chỉ đọc rồi dừng DB local; không áp migration, thay giá hay số dư vào nhầm DB. Bản sửa code không tự cập nhật server máy bạn.

## Đồng bộ code trước khi thử lại

Cần cả backend lẫn Mobile của bản sửa này, không chỉ cài APK mới. Backend phải có:

- `/proposals/majors` cho tài khoản đã đăng nhập, trả ngành có `majorId`, `majorName` từ `academic_majors`.
- Loan item trả `itemStatus` chuẩn hóa, `borrowedAt`, `physicalReturnAt`, giá trị/số nguồn và `returnable`; đếm hạn mức loại item đã xử lý/nhận trả.
- Cấu hình giá demo và migration **44-repair-official-major-catalog**. Migration 44 chỉ bổ sung ngành bị thiếu từ mapping chính thức hiện có, giữ mã/tên/nguồn thủ công và lịch sử. Không lấy trọng số gợi ý hay các chip tìm kiếm làm mã ngành đề xuất.

Trước nâng DB đang có dữ liệu, sao lưu và xem `databasechangelog` của **đúng server**. Migration 39/40 cũ có seed tiền cọc UAT; không chạy lại seed hoặc bỏ/chỉnh changelog để cố đưa cọc về 100k. Tài khoản trong ảnh còn 50k sau phí sửa chữa là dữ liệu cần giữ. Chưa có thông tin DB server thật nên task này không khẳng định trạng thái các migration trên server đó.

## Giá demo để kiểm thử mượn 2–3 cuốn

Thêm/sửa cấu hình backend trong `.env` của **môi trường kiểm thử** (không phải chỉ `--dart-define` của Mobile):

```dotenv
SPRING_PROFILES_ACTIVE=dev
CATALOG_DEMO_PRICING_ENABLED=true
```

Compose bản sửa đã truyền biến này vào backend. Sau khi xác nhận DB và các migration phù hợp, build/restart riêng backend bằng quy trình triển khai hiện có. Nếu dùng Compose từ thư mục dự án:

```powershell
docker compose up -d --no-deps --build backend
```

Lệnh trên áp dụng cho server có DB/dependency đã sẵn sàng; task này **không tự chạy lệnh triển khai đó** trên máy bạn. Nếu chạy JAR trực tiếp, truyền cùng hai biến môi trường vào process backend và dùng JAR mới build.

Giá trị mượn demo ổn định theo từng ID sách, **15–65k VND**, nguồn `DEMO_GENERATED`; cùng resolver được dùng cho eligibility, phiếu/QR và kiểm tra hạn mức. Không random lại mỗi lần mở màn. Không ghi đè giá catalog/manual, bảng phí đền bù, cọc hay chứng từ cũ. Chế độ này mặc định tắt, chỉ cho dev/test; bật ở default/prod/production sẽ bị từ chối thay vì âm thầm dùng giá rẻ.

Đây là lựa chọn runtime cho giá trị mượn demo, không bắt buộc chạy migration 43 để đổi giá lưu trong catalog. Nếu đã áp 43, giá demo đã lưu vẫn giữ được; không cần sửa giá lịch sử. Giá CATALOG/manual 150k vẫn giữ nguyên có chủ đích.

Cọc **50k vẫn dưới mức tối thiểu 100k**: phải nộp bổ sung qua luồng có chứng từ thì mới đủ điều kiện, dù giá sách rẻ. Sau đó tổng giá trị sách vẫn phải nằm trong cọc + biên độ và tối đa số cuốn theo chính sách; không tự nạp tiền hoặc hạ chính sách để làm test PASS.

## Các bước đối chiếu trên môi trường thật

1. Đăng nhập trên Mobile kết nối đúng URL backend. Mở đề xuất, tải lại ngành; nếu server trả 404, app hiển thị yêu cầu cập nhật hệ thống thay vì nói danh mục rỗng. Nếu trả 200 `[]`, kiểm tra `academic_majors` và `academic_department_mappings` trên đúng DB, migration 30/34/44. Không dùng dữ liệu DB local làm bằng chứng cho server khác.
2. Kiểm tra một sách chưa có giá thủ công: eligibility và item phiếu/QR phải cùng giá demo khi bật cờ. Nếu vẫn 150k, kiểm tra process/JAR/container mới, giá nguồn manual hay fallback và biến môi trường ở process backend thực tế.
3. Với một sách đang mượn thật, Home và mục “Đang mượn” phải cùng phân loại; sách đã nhận vật lý/hư hỏng đã xử lý không còn chiếm slot. Lịch sử hiện trạng thái xử lý và ngày nhận trả nếu có, không dùng hạn cũ để hiện “Còn 9 ngày”. Không tự biến hồ sơ đã nhận sách thành đang mượn chỉ vì còn hạn.
4. Màn công nợ: khoản nộp cọc hiển thị `+100000 đ`, có giải thích đây là tiền nộp vào quỹ cọc; số dư ở đầu màn vẫn là 50k theo API thẻ. Số âm trong raw ledger là quy ước kế toán, không phải trừ thêm cọc.

URL, phiên bản server và DB máy bạn chưa được cung cấp/kiểm tra trực tiếp. Các bước trên vẫn cần nghiệm thu trên môi trường đó; test tự động trong task dùng DB cô lập và API/camera giả cho UI.
