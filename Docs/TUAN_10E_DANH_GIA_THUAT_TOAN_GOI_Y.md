# TUẦN 10E — ĐÁNH GIÁ THUẬT TOÁN GỢI Ý

Ngày hoàn thành: 19/10/2026.

## 1. Nguyên tắc đánh giá

Hệ thống tách hoàn toàn hai nguồn dữ liệu:

1. `PRODUCTION_LOAN_HISTORY`: lịch sử mượn thật, dùng để kiểm tra khả năng chia train/test theo thời gian.
2. `SYNTHETIC_TOPIC_PROXY_FROM_PRODUCTION_TAXONOMY`: kịch bản proxy sinh trong bộ nhớ từ taxonomy và ánh xạ ngành–chủ đề đang dùng ở production.

Không ghi dữ liệu synthetic vào PostgreSQL và không trình bày metric proxy như hành vi thật của sinh viên.

## 2. Kiểm tra lịch sử thật

Quy tắc holdout đã cài đặt: với mỗi người dùng có ít nhất 5 tương tác, 80% tương tác đầu theo thời gian làm train và 20% cuối làm test. Chỉ xem dữ liệu đủ ngưỡng công bố nội bộ khi có ít nhất 30 người dùng hợp lệ và 200 tương tác.

Kết quả hiện tại:

| Tương tác thật | Người dùng | Người đủ điều kiện | Train | Test | Đủ công bố |
|---:|---:|---:|---:|---:|:---:|
| 2 | 2 | 0 | 0 | 0 | Không |

Vì vậy project **không công bố Precision/Recall từ lịch sử thật** ở thời điểm này.

## 3. Bộ proxy theo chủ đề

- Catalog đã phân loại: 4.387 đầu sách.
- Yêu cầu: 40 kịch bản; chạy được 38 kịch bản ngành–chủ đề có đủ sách.
- Mỗi kịch bản dùng 3 sách làm hồ sơ sở thích.
- Relevant set là các sách còn lại cùng chủ đề với confidence ít nhất 0,70.
- Mỗi phương pháp trả Top 10; tổng cộng 380 lượt gợi ý/phương pháp.
- Candidate pool tối đa 1.500 sách/kịch bản.
- Chạy warm-up từng phương pháp trước khi ghi latency để giảm sai lệch JIT/class loading.

## 4. Kết quả đối chứng Top 10

| Phương pháp | Precision@10 | Recall@10 | HitRate@10 | Catalog Coverage | p50 latency | p95 latency |
|---|---:|---:|---:|---:|---:|---:|
| Popularity Baseline | 0,102632 | 0,001956 | 0,552632 | 0,003419 (0,342%) | 0,136 ms | 0,424 ms |
| Content TF-IDF | 0,984211 | 0,166917 | 1,000000 | 0,047641 (4,764%) | 53,043 ms | 95,092 ms |
| Proposed Hybrid | **1,000000** | **0,167082** | **1,000000** | **0,048780 (4,878%)** | 53,946 ms | **85,162 ms** |

### Diễn giải

- Popularity rất nhanh nhưng gợi ý lặp lại quanh một nhóm nhỏ, thể hiện qua Coverage chỉ 0,342%.
- Content-Based nhận diện chủ đề tốt, đạt Precision@10 khoảng 98,42% trên proxy.
- Hybrid đạt Precision@10 100% và Coverage cao nhất nhờ kết hợp nội dung với ngành; p95 đo được thấp hơn Content trong lần chạy này nhưng chênh lệch latency phải được kiểm tra lại qua nhiều môi trường trước khi kết luận hiệu năng.
- Recall thấp hơn Precision vì mỗi chủ đề có nhiều tài liệu relevant trong khi Top-K chỉ lấy 10 cuốn; đây là đặc tính của mẫu đánh giá, không phải lỗi tính toán.

## 5. API và khả năng tái lập

```http
GET /admin/recommendations/evaluation?k=10&scenarios=40
Authorization: Bearer <ADMIN_JWT>
```

Endpoint chỉ dành cho `ADMIN`, trả audit lịch sử thật, metric từng phương pháp, số kịch bản và disclaimer. Snapshot JSON của lần chạy đã lưu tại `recommendation_evaluation_2026-10-19.json`.

## 6. Công thức

```text
Precision@K = số tài liệu relevant trong Top-K / số tài liệu được gợi ý
Recall@K    = số tài liệu relevant trong Top-K / tổng relevant set
HitRate@K   = tỷ lệ kịch bản có ít nhất một tài liệu relevant trong Top-K
Coverage    = số đầu sách khác nhau từng được gợi ý / 4.387 sách đã phân loại
```

Latency chỉ đo phần xếp hạng của từng thuật toán trong JVM, không bao gồm HTTP, xác thực và thời gian tải dữ liệu ban đầu.

## 7. Kết luận được phép sử dụng trong báo cáo

“Trên bộ proxy kiểm tra độ phù hợp chủ đề gồm 38 kịch bản lấy từ taxonomy production, mô hình Hybrid đạt Precision@10 bằng 1,000 và Catalog Coverage bằng 4,878%, cao hơn Popularity Baseline. Tuy nhiên dữ liệu mượn thật mới có 2 tương tác của 2 người dùng, chưa đủ để kết luận mô hình có hiệu quả tương đương trên hành vi thực tế.”

Không được bỏ phần giới hạn dữ liệu khi đưa bảng này vào khóa luận hoặc slide bảo vệ.
