# TUẦN 12C — NGUỒN SỐ LIỆU VÀ CÁCH TÁI TẠO

Ngày lập: **20/10/2026**. Tài liệu này giúp truy ngược mọi con số dùng trong Word/PowerPoint về file kết quả hoặc lệnh chạy. Không ghi API key, token hay mật khẩu thật vào lệnh, ảnh hoặc repository.

## 1. Phân loại bằng chứng

| Nhãn | Ý nghĩa | Cách sử dụng |
|---|---|---|
| `REAL_LOCAL_INTEGRATION` | Chạy mã thật với dịch vụ/database cục bộ hoặc Testcontainers | Được mô tả là kiểm thử tích hợp cục bộ, không gọi là production |
| `LOCAL_BENCHMARK` | Đo hiệu năng trên máy phát triển | Luôn kèm cấu hình request/concurrency và cảnh báo không phải SLA |
| `SYNTHETIC_PROXY` | Kịch bản tổng hợp dựa trên taxonomy production | Dùng so sánh thuật toán; không gọi là hành vi thật |
| `CONTROLLED_EVALUATION_SET` | Bộ câu hỏi nghiệp vụ được thiết kế và phiên bản hóa | Dùng đánh giá regression; không suy rộng mọi câu hỏi thực tế |
| `REAL_HISTORY_AUDIT` | Thống kê lịch sử mượn hiện có | Dùng chứng minh dữ liệu thật chưa đủ, không tính metric giả |

## 2. Bảng nguồn chính thức

| Nhóm | Loại | File/lệnh nguồn | Kết quả chốt |
|---|---|---|---|
| Backend gate cập nhật 12E | `REAL_LOCAL_INTEGRATION` | `cd backend; ./mvnw test` | 121/121 test |
| Web gate sau 12B | Local automated test | `cd web-app; npm test -- --watch=false` | 69/69 test |
| Web production build | Local build | `cd web-app; npm run build` | Thành công |
| Gate tuần 11 | Local automated test | `TUAN_11_KIEM_THU_TICH_HOP_VA_VAN_HANH_CUC_BO.md` | Backend 101, Web 66, Mobile 5; analyze sạch; audit 0 |
| HTTP E2E tuần 11 | `REAL_LOCAL_INTEGRATION` | Test `week11HttpE2eCoversLoginSearchReservationQrCheckoutOverdueFineAndReturn` | Luồng chính đạt |
| Load catalog | `LOCAL_BENCHMARK` | `scripts/week11-load-test.mjs`; `week11_performance_2026-10-19.json` | 200 request, concurrency 20, p95 807,54 ms |
| Backup/restore | `REAL_LOCAL_INTEGRATION` | `scripts/backup-postgres.ps1`; `scripts/verify-backup-restore.ps1` | 18.933.805 byte, restore 48 bảng |
| Recommender | `SYNTHETIC_PROXY` | `recommendation_evaluation_2026-10-19.json` | 38 kịch bản Top 10; Hybrid Precision 1,0; Coverage 4,878% |
| Dữ liệu mượn recommender | `REAL_HISTORY_AUDIT` | Cùng JSON recommender | 2 tương tác/2 người; 0 người đủ điều kiện |
| RAG | `CONTROLLED_EVALUATION_SET` | `rag_evaluation_2026-10-20.json` | 14 ca; các tỷ lệ retrieval/grounding/citation/refusal đều 100% |

Không gộp metric proxy recommender với lịch sử mượn thật. Không gộp fallback rate của RAG với retrieval accuracy. Không so latency xếp hạng trong JVM của recommender với latency HTTP/RAG như cùng một phép đo.

## 3. Snapshot và checksum

| File | SHA-256 |
|---|---|
| `recommendation_evaluation_2026-10-19.json` | `199F3A0C21AF90CEAEA6758CB0DFAA71A1969DF3A4AE6BF796183B4586AD1C20` |
| `week11_performance_2026-10-19.json` | `4A32D0681340EAAEAF7B1693627C3A3DB9A321379AFE2020FC12A6DD20D58937` |
| `rag_evaluation_2026-10-20.json` | `841C3D4E6B52EEF54103D88914848F3F3CDE6751D89B990433BF808593AB7281` |

Kiểm tra lại checksum:

```powershell
Get-FileHash `
  .\Docs\recommendation_evaluation_2026-10-19.json, `
  .\Docs\week11_performance_2026-10-19.json, `
  .\Docs\rag_evaluation_2026-10-20.json `
  -Algorithm SHA256
```

Khi chạy lại thực nghiệm, timestamp, latency và checksum có thể đổi. Phải lưu snapshot mới bằng tên ngày mới; không ghi đè rồi tiếp tục dùng con số cũ.

## 4. Tái tạo quality gate

```powershell
Set-Location .\backend
.\mvnw.cmd test

Set-Location ..\web-app
npm ci
npm test -- --watch=false
npm run build

Set-Location ..\mobile-app
flutter pub get
flutter analyze
flutter test
```

Số test có thể tăng khi bổ sung test mới. Báo cáo cần ghi ngày chạy và tổng test của đúng lượt nghiệm thu, không cộng các tổng ở nhiều thời điểm.

## 5. Tái tạo vận hành và benchmark tuần 11

Chuẩn bị `.env` từ `.env.example`, thay giá trị minh họa bằng secret cục bộ, rồi:

```powershell
docker compose up -d --build
.\scripts\week11-smoke.ps1
node .\scripts\week11-load-test.mjs --requests 200 --concurrency 20
.\scripts\backup-postgres.ps1 -OutputPath .\backups\huit_library.dump
.\scripts\verify-backup-restore.ps1 -BackupPath .\backups\huit_library.dump
```

`verify-backup-restore.ps1` chỉ dùng database tạm có tiền tố an toàn và không được đổi thành database chính. Kết quả benchmark phụ thuộc máy, dữ liệu và tiến trình nền; giữ file JSON của từng lần chạy.

## 6. Tái tạo thực nghiệm recommender

Sau khi Backend và database đã chạy, đăng nhập Admin rồi gọi:

```http
GET /admin/recommendations/evaluation?k=10&scenarios=40
Authorization: Bearer <ADMIN_JWT>
```

Đối chiếu response với `TUAN_10E_DANH_GIA_THUAT_TOAN_GOI_Y.md`. Chỉ công bố metric lịch sử thật khi response báo đủ tối thiểu 30 người dùng hợp lệ và 200 tương tác; nếu chưa đủ, phải giữ nguyên disclaimer `SYNTHETIC_TOPIC_PROXY_FROM_PRODUCTION_TAXONOMY`.

## 7. Tái tạo thực nghiệm RAG

Sau khi cấu hình Gemini bằng biến môi trường Backend và không đưa key vào command history/tài liệu:

```powershell
$login = Invoke-RestMethod `
  -Method Post `
  -Uri 'http://localhost:8080/api/v1/auth/login' `
  -ContentType 'application/json' `
  -Body (@{ email = '<admin-email>'; password = '<admin-password>' } | ConvertTo-Json)

.\scripts\week12b-rag-evaluation.ps1 `
  -AdminToken $login.accessToken `
  -DelayMillis 10000
```

Nguồn dataset: `backend/src/main/resources/rag/evaluation-dataset.json`. Endpoint chỉ dành cho Admin. Nếu generation hết quota, hệ thống có thể dùng fallback trích xuất; phải báo riêng `fallbackRate` và không gọi lượt đó là đánh giá đầy đủ chất lượng sinh ngôn ngữ Gemini.

## 8. Số liệu chốt và cách diễn giải

### 8.1. Benchmark tuần 11

- 200/200 phản hồi HTTP 200, không lỗi.
- Mức đồng thời 20; p50 618,01 ms; p95 807,54 ms; max 1.089,75 ms.
- Thông lượng 30,90 request/giây; đạt ngưỡng nội bộ p95 ≤ 1.500 ms.
- Diễn giải đúng: benchmark catalog trên máy phát triển và database hiện tại.

### 8.2. Recommender

- Catalog phân loại: 4.387 sách; 38/40 kịch bản proxy hợp lệ.
- Popularity: Precision@10 0,102632; Coverage 0,342%.
- Content TF-IDF: Precision@10 0,984211; Coverage 4,764%.
- Hybrid: Precision@10 1,000000; Recall@10 0,167082; Coverage 4,878%; p95 85,162 ms.
- Diễn giải đúng: Hybrid tốt hơn Popularity trên proxy chủ đề; chưa đủ lịch sử thật để kết luận trên hành vi sinh viên.

### 8.3. RAG

- 14 ca: 12 cần trả lời, 2 cần từ chối/chặn.
- Hit@5, Recall@5, nguồn đúng, grounded content, citation và refusal: 100%; unexpected errors: 0.
- p50 1.067 ms và p95 2.100 ms theo ký hiệu hàng nghìn tiếng Việt, tương ứng khoảng 1,067 giây và 2,100 giây.
- Fallback rate 92,86% do quota generation ở lượt chốt; retrieval và evidence vẫn được kiểm tra.
- Diễn giải đúng: kết quả regression trên dataset v2 có kiểm soát, không phải độ chính xác production tổng quát.

## 9. Quy tắc đưa vào Word/PowerPoint

1. Mỗi bảng số liệu phải ghi nguồn file và ngày chạy.
2. Mỗi metric proxy/controlled set phải có cảnh báo ngay dưới bảng, không để ở phụ lục xa.
3. Không làm tròn 1,0 thành “AI chính xác tuyệt đối”; dùng “đạt 100% trên bộ kiểm thử 14 ca”.
4. Không dùng ảnh terminal làm nguồn duy nhất nếu đã có JSON.
5. Nếu người kiểm thử chạy lại và có kết quả khác, giữ cả snapshot cũ/mới, giải thích môi trường và dùng lượt nghiệm thu cuối được nhóm xác nhận.
6. Không chụp hoặc sao chép `.env`, API key, JWT, mật khẩu, OTP hay dữ liệu cá nhân.
7. Đặt quality gate, E2E, benchmark, recommender và RAG trong **Chương 5 Thử nghiệm và triển khai** theo mẫu báo cáo 2025; không phân tán các bảng metric theo thứ tự tuần.
8. Phần công nghệ, API và triển khai cục bộ thuộc **Chương 4 Cài đặt hệ thống**; công thức và thiết kế recommender/RAG thuộc **Chương 3 Thiết kế hệ thống**.
