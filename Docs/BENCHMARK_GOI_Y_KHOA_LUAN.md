# Benchmark gợi ý sách cho khóa luận

Benchmark kiểm tra phương pháp nào xếp các đầu sách sinh viên sẽ mượn lần đầu cao hơn trong top 10. Kết quả dùng để giải thích lựa chọn thuật toán trên cùng dữ liệu và điều kiện. Pilot DB local hiện có 12 lượt checkout của một sinh viên và chưa có người dùng đủ điều kiện chấm Test.

## Phương pháp đối chứng

| Phương pháp | Tín hiệu xếp hạng |
|---|---|
| Popularity | Lượt checkout trong 365 ngày trước mốc dự đoán, giảm trọng số theo thời gian |
| TF-IDF + Cosine | Nhan đề, tác giả, nhà xuất bản, thể loại và chủ đề so với hồ sơ mượn. Thiếu hồ sơ thì dùng Popularity |
| Hybrid mặc định | Nội dung 0,50, ngành học 0,35 và độ phổ biến 0,15 |
| Hybrid chọn trên Validation | Ba trọng số dương có tổng bằng 1, thử bước 0,10 và bộ mặc định. Chọn NDCG cao nhất, sau đó xét Recall và ưu tiên bộ gần mặc định hơn |
| Hai biến thể Hybrid | Bỏ điểm ngành hoặc bỏ điểm nội dung rồi chuẩn hóa lại hai trọng số còn lại |

Giữ bộ mặc định nếu Validation không có mẫu hoặc các bộ trọng số cho cùng kết quả. Chỉ đưa Hybrid đã chọn vào bảng Test khi Validation phân biệt được các bộ trọng số.

## Cách đánh giá

1. Đọc một snapshot nhất quán của PostgreSQL. Dùng thời điểm checkout đã lưu, loại phiếu hủy hoặc từ chối và báo số dòng thiếu timestamp. Giữ riêng nguồn thời gian checkout trong báo cáo.
2. Dùng hai mốc thời gian chung cho mọi sinh viên. Train nằm trước Validation, Validation kết thúc trước Test. Các checkout trùng thời điểm nằm cùng một giai đoạn. Mặc định chọn mốc theo các thời điểm checkout phân biệt. Khi viết khóa luận, ghi trước hai mốc của kỳ quan sát để chạy lại được. Nghiên cứu về chia dữ liệu gợi ý ghi nhận phép chia có thể làm thay đổi thứ hạng mô hình. [Gusak và cộng sự, RecSys 2025](https://arxiv.org/abs/2507.16289)
3. Tại mỗi mốc, giới hạn đầu sách theo thời điểm tạo trong DB, chỉ dùng các nhãn chủ đề và ánh xạ ngành đã được tạo tại mốc đó. Dùng lượt checkout trước mốc để dựng hồ sơ và tính độ phổ biến. Đến mốc Test, các lượt thuộc Validation đã nằm trong quá khứ và được dùng làm lịch sử với trọng số đã cố định. Loại sách đã mượn khỏi ứng viên và mục tiêu mượn lần đầu. Báo riêng mục tiêu bị loại vì sách chưa tồn tại tại mốc.
4. Dùng cùng tập ứng viên cho mọi cách xếp hạng. Tập này có tối đa 3.000 sách mỗi sinh viên theo mặc định, gom từ sách phổ biến, chủ đề, thể loại trong lịch sử và danh mục dự phòng. So sánh biến thể bỏ tín hiệu ở bước **xếp hạng** trên cùng tập ứng viên. Luôn báo Candidate Recall để thấy giới hạn khi bước lấy ứng viên bỏ sót mục tiêu.
5. Báo NDCG@10, Recall@10, Precision@10, Hit Rate@10 và độ bao phủ danh mục. NDCG ưu tiên sách đúng nằm cao trong top 10. Báo riêng nhóm chưa có lịch sử, nhóm có 1–4 đầu sách từng mượn và nhóm có từ 5 đầu sách. Một nhóm rỗng nhận giá trị null.

Đây là benchmark offline của công thức xếp hạng. Nó giữ nguyên các thành phần chính của Hybrid và dùng chung tập ứng viên để đối chứng. Tập ứng viên khác đường chạy API, dữ liệu mô tả và ngành học lấy theo snapshot hiện tại, và số lần gia hạn được bỏ vì DB thiếu lịch sử gia hạn theo từng mốc. Kết quả đo chất lượng xếp hạng, còn độ trễ API cần đo riêng. Việc so với baseline đơn giản là một phần của đánh giá thực nghiệm có thể tái lập. [Ferrari Dacrema và cộng sự, RecSys 2019](https://arxiv.org/abs/1907.06902)

## Chạy lại

Chạy từ thư mục gốc của repo khi container PostgreSQL đã hoạt động.

~~~powershell
py -3 scripts/benchmark_recommendations.py
~~~

Để dùng hai mốc đã định trước, truyền thêm các tham số sau và thay ngày theo kỳ quan sát.

~~~powershell
py -3 scripts/benchmark_recommendations.py --validation-start 2026-10-01 --test-start 2026-11-01
~~~

`--container` chọn container khác. `--top-k` và `--candidate-pool` cố định quy mô phép so sánh. Kết quả mặc định nằm tại `.codex-tmp/recommendation-benchmark/report.json`. Báo cáo lưu hash của snapshot, mốc chia, số mẫu, các bộ trọng số và các chỉ số tổng hợp. File chỉ chứa kết quả tổng hợp.

## Pilot trên DB local ngày 08 tháng 11 năm 2026

| Chỉ tiêu | Kết quả |
|---|---:|
| Đầu sách trong snapshot | 269.925 |
| Checkout có timestamp | 12 |
| Sinh viên có checkout | 1 |
| Số lượt Train / Validation / Test | 7 / 2 / 3 |
| Sinh viên đủ điều kiện chấm Validation / Test | 0 / 0 |

Validation có hai mục tiêu mượn lại. Test có một mục tiêu mượn lại và hai đầu sách thiếu điều kiện tồn tại tại mốc dự đoán theo thời điểm tạo trong DB. Benchmark ghi `no_eligible_test_targets`, các chỉ số là null và giữ bộ trọng số mặc định. Kết quả này là kiểm tra khả năng chạy trên DB demo. Dữ liệu đó chưa đủ để chọn thuật toán dựa trên chất lượng dự đoán.

Trong khóa luận, dùng **NDCG@10 làm chỉ số chính** và đọc Recall@10 cùng Candidate Recall. Nếu Hybrid tốt hơn baseline trên Test có đủ mẫu, hai biến thể bỏ tín hiệu giúp xác định phần đóng góp của ngành học và nội dung. Nếu kết quả ngang nhau, trình bày kết quả đó và giải thích lựa chọn triển khai dựa trên lợi ích cold start, độ bao phủ và chi phí vận hành đã đo. Để hoàn tất đánh giá HUIT, cần lịch sử checkout ở nhiều sinh viên qua các kỳ quan sát và danh mục có mốc tồn tại kiểm chứng được.
