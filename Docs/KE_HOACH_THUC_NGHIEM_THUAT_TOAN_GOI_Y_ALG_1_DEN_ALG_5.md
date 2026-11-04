# Kế hoạch thực nghiệm và lựa chọn thuật toán gợi ý sách

## 1. Mục tiêu của tài liệu

Tài liệu này dùng để quản lý toàn bộ phần **so sánh thuật toán và chứng minh lý do lựa chọn mô hình Hybrid** cho đồ án thư viện HUIT.

Mục tiêu cuối cùng không phải khẳng định Hybrid luôn là thuật toán tốt nhất, mà phải chứng minh rằng:

- Hybrid phù hợp với đặc điểm dữ liệu hiện có của thư viện.
- Việc lựa chọn dựa trên số liệu thực nghiệm, không dựa trên cảm tính.
- Các phương pháp được so sánh công bằng trên cùng dữ liệu và cùng điều kiện.
- Kết quả có thể tái tạo để đưa vào báo cáo Word, PowerPoint và trả lời hội đồng.

Phạm vi thực nghiệm tối thiểu gồm ba phương pháp:

1. Gợi ý theo độ phổ biến — mô hình đối chứng cơ bản.
2. Gợi ý dựa trên nội dung sách.
3. Mô hình Hybrid — nội dung + chuyên ngành + độ phổ biến.

Lọc cộng tác có thể bổ sung nếu dữ liệu đủ dày. K-Means và mô hình học sâu chỉ cần phân tích ưu, nhược điểm nếu dữ liệu hiện tại không phù hợp để thực nghiệm đáng tin cậy.

---

## 2. Nguyên tắc thực hiện chung

- Thực hiện theo đúng thứ tự: `ALG-1 → ALG-2 → ALG-3 → ALG-4 → ALG-5`.
- Mọi thuật toán phải dùng cùng một bộ dữ liệu đầu vào, cách chia dữ liệu và danh sách ứng viên.
- Không dùng dữ liệu kiểm tra để chọn trọng số hoặc điều chỉnh thuật toán.
- Không để dữ liệu tương lai xuất hiện trong dữ liệu huấn luyện.
- Không tự nghĩ ra số liệu để đưa vào báo cáo.
- Nếu phải dùng dữ liệu mô phỏng vì dữ liệu thật chưa đủ, phải ghi rõ phần nào là mô phỏng.
- Mỗi lần chạy thực nghiệm phải lưu cấu hình, thời gian chạy, kết quả và phiên bản mã nguồn.
- Kết quả Hybrid không bắt buộc đứng đầu mọi chỉ số. Điều quan trọng là giải thích được sự đánh đổi giữa độ chính xác, độ bao phủ, người dùng mới và khả năng triển khai.

## 3. Bảng theo dõi tiến độ

| Gói | Nội dung | Trạng thái | Điều kiện bắt đầu | Kết quả chính |
|---|---|---|---|---|
| ALG-1 | Chuẩn hóa dữ liệu và thiết kế tập thực nghiệm | Đã hoàn thành | Dữ liệu sách, sinh viên và lịch sử mượn truy cập được | Pipeline tái tạo 10 tệp thống kê và Train/Validation/Test |
| ALG-2 | Xây dựng các thuật toán đối chứng | Đã hoàn thành | ALG-1 hoàn thành | Popularity và Content-Based chạy cùng snapshot và giao thức Top-10 |
| ALG-3 | Hoàn thiện Hybrid và tìm trọng số | Chưa làm | ALG-2 hoàn thành | Trọng số được chọn trên Validation |
| ALG-4 | Đánh giá và xuất số liệu đối chứng | Chưa làm | ALG-3 đã chốt | Bảng chỉ số, biểu đồ và kết luận thực nghiệm |
| ALG-5 | Đóng gói báo cáo và nội dung bảo vệ | Chưa làm | ALG-4 hoàn thành | Nội dung Word/PPT và bộ câu hỏi hội đồng |

Quy ước trạng thái: `Chưa làm`, `Đang làm`, `Chờ dữ liệu`, `Đã hoàn thành`.

---

# ALG-1 — Chuẩn hóa dữ liệu và thiết kế tập thực nghiệm

## Trạng thái triển khai

`ALG-1` đã hoàn thành ở mức mã nguồn, kiểm thử tự động và xuất dữ liệu thật. Pipeline dùng dữ liệu thật từ danh mục sách, tài khoản sinh viên và lịch sử lưu thông để tạo một bộ dữ liệu thực nghiệm chung. Popularity và Content-Based đã được triển khai ở `ALG-2`; Hybrid bắt đầu từ `ALG-3`.

## 1. Mục tiêu

Xác định dữ liệu thực tế của hệ thống có đặc điểm gì, mức độ thưa ra sao và tạo một bộ dữ liệu chung để mọi thuật toán được so sánh công bằng.

ALG-1 phải trả lời được các câu hỏi:

- Có bao nhiêu sinh viên, đầu sách và lượt tương tác hợp lệ?
- Bao nhiêu sinh viên chưa có lịch sử mượn?
- Bao nhiêu sách chưa từng được mượn?
- Thông tin tên sách, tác giả, thể loại, chuyên ngành và mô tả có đầy đủ không?
- Dữ liệu có đủ để dùng lọc cộng tác hay mô hình phức tạp không?

## 2. Nguồn dữ liệu cần kiểm tra

- Danh mục sách và thông tin mô tả sách.
- Tác giả, thể loại, chủ đề và chuyên ngành liên quan.
- Sinh viên và khoa/chuyên ngành của sinh viên.
- Phiếu mượn và chi tiết từng tài liệu trong phiếu.
- Trạng thái mượn, trả, quá hạn, hủy hoặc bị từ chối.
- Số lần gia hạn nếu hệ thống có lưu.
- Điểm đánh giá sách nếu có, nhưng không được giả định rằng dữ liệu đánh giá đủ nhiều.
- Thời gian phát sinh tương tác để chia dữ liệu theo trình tự thời gian.

## 3. Quy tắc tạo tương tác đã chốt

- Một lượt được xem là tương tác tích cực ngầm định khi đã có thời điểm giao sách hoặc có trạng thái `BORROWING`, `BORROWED`, `OVERDUE`, `RETURNED`, `LOST`, `DAMAGED`, `SETTLED` hay đã ghi nhận ngày trả.
- Loại hoàn toàn các phiếu `CANCELLED` và `REJECTED`; đây không được xem là bằng chứng sinh viên đã đọc sách.
- Mỗi cặp sinh viên–đầu sách chỉ tạo một dòng tương tác. Nếu mượn lại cùng sách, pipeline giữ `borrowCount`, `renewalCount`, `firstInteractionAt` và `lastInteractionAt` thay vì sinh nhiều cặp trùng.
- Trọng số phản hồi ngầm định được tính thống nhất:

```text
implicitWeight = 1
                 + 0,15 × min(tổng số lần gia hạn, 2)
                 + 0,10 × min(số lượt mượn lặp lại, 2)
```

- Khóa sinh viên, khóa sách và thời gian tương tác phải hợp lệ; tương tác ngoài tập sinh viên/sách đủ điều kiện bị loại.
- Không đưa MSSV, email, họ tên hoặc dữ liệu nhận dạng cá nhân vào các tệp xuất.

## 4. Thống kê bắt buộc

- Tổng số sinh viên.
- Tổng số đầu sách đủ điều kiện gợi ý.
- Tổng số tương tác hợp lệ.
- Số tương tác trung bình trên mỗi sinh viên.
- Số tương tác trung bình trên mỗi đầu sách.
- Tỷ lệ sinh viên không có lịch sử.
- Tỷ lệ sách chưa từng được mượn.
- Tỷ lệ thiếu thể loại, tác giả, chuyên ngành hoặc mô tả.
- Độ thưa của ma trận sinh viên–sách:

```text
Độ thưa = 1 - số cặp sinh viên–sách có tương tác
                 / (tổng sinh viên × tổng đầu sách)
```

## 5. Cách chia dữ liệu

Ưu tiên chia theo thời gian thay vì chia ngẫu nhiên, vì bài toán thực tế là dùng quá khứ để dự đoán tương lai.

Quy tắc chính thức là chia theo thời gian và giữ lại tối đa hai tương tác cuối của từng sinh viên:

- Có từ 3 tương tác trở lên: các tương tác đầu vào `Train`, tương tác gần cuối vào `Validation`, tương tác cuối vào `Test`.
- Có 2 tương tác: tương tác đầu vào `Train`, tương tác cuối vào `Test`.
- Có 1 tương tác: đưa vào `Test` để giữ đúng nhóm ít dữ liệu; người này không có lịch sử huấn luyện.
- Có 0 tương tác trong `Train`: thuộc nhóm người dùng mới khi đánh giá khởi đầu lạnh.

Trong từng sinh viên, pipeline sắp xếp theo `lastInteractionAt`, sau đó dùng khóa sách để phá hòa. Quy tắc này bảo đảm mọi tương tác `Train` không xảy ra sau `Validation` hoặc `Test`.

## 6. Nhóm đối tượng cần tách riêng

- **Sinh viên đã có lịch sử (`WARM`):** có ít nhất 3 tương tác trong `Train`, dùng để đánh giá cá nhân hóa đầy đủ.
- **Sinh viên mới (`NEW`):** không có tương tác trong `Train`, dùng để đánh giá xử lý khởi đầu lạnh.
- **Sách mới:** chưa có lượt mượn nhưng có thông tin nội dung.
- **Sinh viên ít dữ liệu (`SPARSE`):** chỉ có 1 hoặc 2 tương tác trong `Train`.

## 7. Đầu ra cần tạo

Pipeline tạo đúng 10 tệp trong thư mục đầu ra:

1. `summary.json` — thống kê quy mô, độ thưa, thiếu dữ liệu và nhóm khởi đầu lạnh.
2. `config.json` — phiên bản quy tắc lọc, gộp và chia tập.
3. `interactions_clean.csv` — các cặp sinh viên–sách đã làm sạch và gộp.
4. `train.csv` — lịch sử được phép dùng để xây dựng mô hình.
5. `validation.csv` — dữ liệu chọn cấu hình/trọng số.
6. `test.csv` — dữ liệu đánh giá cuối cùng.
7. `new_users.csv` — sinh viên không có lịch sử trong `Train`.
8. `new_books.csv` — đầu sách chưa có tương tác trong `Train`.
9. `data_dictionary.md` — ý nghĩa và kiểu dữ liệu từng cột.
10. `README.md` — quy tắc nghiệp vụ và hướng dẫn tái tạo.

Chạy từ thư mục `backend` khi Backend kết nối được CSDL cần đánh giá:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--library.recommendation.experiment.export-enabled=true --library.recommendation.experiment.output-dir=target/recommendation-alg1"
```

Mặc định pipeline không tự chạy khi khởi động ứng dụng. Phải bật rõ `library.recommendation.experiment.export-enabled=true`; kết quả mặc định nằm tại `backend/target/recommendation-alg1` và có thể tạo lại từ cùng CSDL/cấu hình.

## 8. Tiêu chí hoàn thành

- [x] Không còn tương tác hủy/từ chối trong tập tích cực.
- [x] Không có rò rỉ dữ liệu tương lai vào Train.
- [x] Mỗi bản ghi sách và sinh viên dùng trong thực nghiệm có khóa hợp lệ.
- [x] Có số liệu chứng minh mức độ thưa và tình trạng khởi đầu lạnh.
- [x] Có thể chạy lại quy trình và tạo cùng kết quả với cùng cấu hình.
- [x] Đã chốt chính thức cách chia Train/Validation/Test.

Kiểm thử tập trung:

```powershell
.\mvnw.cmd -q "-Dtest=RecommendationExperimentDatasetServiceTest,RecommendationExperimentExporterTest,RecommendationEvaluationServiceTest" test
```

Kết quả hiện tại: 3 lớp kiểm thử đạt, gồm kiểm thử chia tập/tính thống kê, kiểm thử đủ 10 tệp đầu ra và kiểm thử hồi quy dịch vụ đánh giá gợi ý hiện có.

## 9. Rủi ro cần tránh

- Chia ngẫu nhiên làm tương tác tương lai xuất hiện trong Train.
- Tính cả phiếu hủy thành sở thích của sinh viên.
- Dùng dữ liệu Test để chọn trọng số.
- Loại hết sinh viên mới khỏi đánh giá rồi kết luận mô hình xử lý tốt người dùng mới.

---

# ALG-2 — Xây dựng các thuật toán đối chứng

## Trạng thái triển khai

`ALG-2` đã hoàn thành ở mức mã nguồn, kiểm thử tự động và chạy thực nghiệm trên dữ liệu thật. Runner ALG-2 chỉ tạo **một snapshot dữ liệu**, xuất lại bộ ALG-1 từ snapshot đó rồi mới chạy cả hai mô hình đối chứng. Nhờ vậy số lượng Train/Validation/Test và danh mục ứng viên trong hai báo cáo luôn đồng nhất.

Kết quả thực nghiệm hiện tại:

| Thuộc tính | Giá trị |
|---|---:|
| Sinh viên trong snapshot | 3 |
| Đầu sách hợp lệ | 269.922 |
| Tương tác sau làm sạch | 4 |
| Train / Validation / Test | 2 / 1 / 1 |
| Người dùng đủ điều kiện đánh giá | 1 |
| Top-K | 10 |

Quy tắc ứng viên được chốt là toàn bộ danh mục hợp lệ, loại các sách người dùng đã có trong Train và Validation; Test chỉ được dùng làm đáp án đúng để tính chỉ số. Hai mô hình đều không nhìn thấy Test khi huấn luyện hoặc xếp hạng.

## 1. Mục tiêu

Xây dựng tối thiểu hai phương pháp đối chứng chạy trên đúng dữ liệu của ALG-1. Đây là căn cứ để biết Hybrid có thực sự mang lại cải thiện hay không.

## 2. Giao thức chung

Mọi phương pháp phải nhận cùng loại đầu vào và trả cùng loại đầu ra:

```text
Đầu vào: sinh viên + lịch sử Train + danh sách sách ứng viên
Đầu ra: Top-K sách, điểm xếp hạng và lý do gợi ý
```

Quy tắc ứng viên phải giống nhau:

- Chỉ dùng sách đang được phép xuất hiện trong danh mục.
- Loại sách không hợp lệ hoặc đã ngừng lưu hành.
- Thống nhất có loại sách sinh viên đã mượn khỏi danh sách hay không.
- Không để một phương pháp được nhìn thấy dữ liệu mà phương pháp khác không có.

## 3. Thuật toán đối chứng 1 — Độ phổ biến

### Cách hoạt động

- Đếm số lượt mượn hợp lệ của mỗi đầu sách trong tập Train.
- Sắp xếp giảm dần theo số lượt mượn.
- Dùng cùng danh sách Top-K cho mọi sinh viên.

### Vai trò

- Là mô hình đơn giản nhất để làm mốc so sánh.
- Hoạt động được với sinh viên mới.
- Cho thấy cá nhân hóa có thực sự tốt hơn việc chỉ gợi ý sách nổi tiếng hay không.

### Hạn chế

- Mọi sinh viên gần như nhận cùng kết quả.
- Dễ tập trung vào một nhóm nhỏ sách phổ biến.
- Độ bao phủ danh mục thường thấp.

Phiên bản đối chứng chính nên là phổ biến toàn cục. Nếu bổ sung phổ biến theo chuyên ngành thì phải ghi thành một biến thể riêng, không được trộn với mô hình đối chứng gốc.

## 4. Thuật toán đối chứng 2 — Dựa trên nội dung

### Tạo hồ sơ sách

Ghép các trường có sẵn, ví dụ:

```text
Tên sách + tác giả + thể loại/chủ đề + nhà xuất bản + mô tả
```

Sau đó dùng TF-IDF để biến văn bản thành vector đặc trưng.

### Tạo hồ sơ sinh viên

- Lấy vector của các sách trong lịch sử Train.
- Tính vector trung bình hoặc trung bình có trọng số.
- Có thể ưu tiên sách mượn gần đây hoặc từng gia hạn, nếu ALG-1 đã chốt quy tắc này.

### Xếp hạng

- Dùng độ tương đồng Cosine giữa hồ sơ sinh viên và từng sách ứng viên.
- Sắp xếp điểm từ cao xuống thấp và lấy Top-K.

### Xử lý sinh viên mới

Content-Based không có hồ sơ nếu sinh viên chưa mượn sách. Cần định nghĩa rõ phương án dự phòng, chẳng hạn trả sách phổ biến. Khi đánh giá, phải tách phần dự phòng khỏi khả năng cá nhân hóa thật sự của Content-Based.

## 5. Thuật toán tùy chọn — Lọc cộng tác

Chỉ triển khai nếu kết quả ALG-1 cho thấy đủ tương tác giữa sinh viên và sách. Nếu dữ liệu quá thưa, có thể không triển khai nhưng phải dùng thống kê để giải thích.

Lý do không ưu tiên:

- Hệ thống thiếu đánh giá sao, chủ yếu chỉ có phản hồi ngầm định.
- Nhiều sinh viên và sách mới chưa có lịch sử.
- Ma trận tương tác có thể quá thưa để tìm hàng xóm đáng tin cậy.

## 6. Kiểm thử cần có

- Một sinh viên có lịch sử nhận đúng K kết quả hợp lệ.
- Sinh viên mới không làm thuật toán lỗi.
- Sách mới có nội dung vẫn được Content-Based chấm điểm.
- Không gợi ý sách ngoài danh sách ứng viên.
- Cùng dữ liệu và seed cho cùng kết quả.
- Điểm được chuẩn hóa hoặc có quy tắc sắp xếp ổn định khi bằng điểm.

## 7. Đầu ra cần tạo

- Mã nguồn Popularity recommender.
- Mã nguồn Content-Based recommender.
- Giao diện gọi chung cho các thuật toán.
- Kết quả Top-K mẫu cho một số sinh viên đại diện.
- Kiểm thử tự động.
- Tệp cấu hình K và quy tắc lọc ứng viên.

## 8. Tiêu chí hoàn thành

- [x] Popularity và Content-Based chạy trên cùng tập Train.
- [x] Hai phương pháp trả cùng định dạng kết quả.
- [x] Có xử lý sinh viên mới và sách mới.
- [x] Có kiểm thử cho dữ liệu bình thường và trường hợp biên.
- [x] Chưa sử dụng tập Test để quyết định cấu hình.

## 9. Kết quả thực nghiệm thật

| Phương pháp | Precision@10 | Recall@10 | HitRate@10 | Coverage | Độ trễ trung bình |
|---|---:|---:|---:|---:|---:|
| Popularity | 0 | 0 | 0 | 0,000037 | 0,760047 ms |
| Content TF-IDF | 0 | 0 | 0 | 0,000037 | 2.142,919532 ms |

Các giá trị bằng 0 **không có nghĩa hai thuật toán vô dụng**. Sau khi chia dữ liệu đúng theo thời gian, hiện chỉ có một sinh viên đủ điều kiện đánh giá và một tài liệu trong Test. Vì vậy kết quả ALG-2 mới là bằng chứng rằng pipeline, giao thức đối chứng và phép đo chạy đúng; chưa đủ độ tin cậy thống kê để kết luận thuật toán nào tốt hơn. Báo cáo cuối phải giữ nguyên cảnh báo này và không tự suy diễn số liệu.

Các tệp đầu ra được tạo tại:

- `backend/target/recommendation-alg1/`: snapshot ALG-1 dùng chung.
- `backend/target/recommendation-alg2/alg2_metrics.csv`: bảng chỉ số hai mô hình.
- `backend/target/recommendation-alg2/alg2_sample_top_k.csv`: Top-K mẫu đã ẩn danh.
- `backend/target/recommendation-alg2/alg2_report.json`: báo cáo đầy đủ và chẩn đoán dữ liệu.
- `backend/target/recommendation-alg2/alg2_config.json`: Top-K và quy tắc lọc ứng viên.

`ALG-2` chưa triển khai Hybrid và chưa lựa chọn trọng số. Hai nội dung đó thuộc `ALG-3`.

---

# ALG-3 — Hoàn thiện Hybrid và lựa chọn trọng số

## 1. Mục tiêu

Kết hợp ba nguồn tín hiệu phù hợp với thư viện HUIT và lựa chọn trọng số dựa trên tập Validation.

Ba thành phần gồm:

1. Mức tương đồng nội dung sách.
2. Mức phù hợp với chuyên ngành sinh viên.
3. Mức độ phổ biến của sách.

## 2. Công thức tổng quát

```text
HybridScore = w_content × ContentScore
            + w_major × MajorScore
            + w_popularity × PopularityScore
```

Điều kiện:

```text
w_content + w_major + w_popularity = 1
```

Tất cả điểm thành phần phải được đưa về cùng thang, ưu tiên từ `0` đến `1`.

Bộ trọng số `0.50 / 0.35 / 0.15` chỉ là giả thuyết khởi đầu, chưa được xem là kết quả cuối cho đến khi thực nghiệm xác nhận.

## 3. Điểm phù hợp chuyên ngành

- Dùng dữ liệu ánh xạ giữa chuyên ngành và chủ đề/thể loại sách trong CSDL.
- Không viết cứng danh sách khoa và thể loại trong mã thuật toán.
- Có thể dùng điểm nhị phân `0/1` hoặc điểm liên tục nếu bảng ánh xạ có trọng số.
- Phải chuẩn hóa tên khoa/chuyên ngành để tránh cùng một ngành nhưng khác cách viết.

## 4. Xử lý khởi đầu lạnh

### Sinh viên mới

Khi chưa có lịch sử, `ContentScore` theo hồ sơ sinh viên không tồn tại. Có hai phương án hợp lệ:

- Đặt ContentScore bằng 0 rồi chuẩn hóa lại trọng số của chuyên ngành và độ phổ biến.
- Dùng một công thức riêng cho người dùng mới.

Phải chọn rõ một phương án và kiểm thử riêng.

### Sách mới

Sách mới có thể có `PopularityScore = 0`, nhưng vẫn được xếp hạng nhờ nội dung và chuyên ngành. Đây là một lý do quan trọng để Hybrid phù hợp hơn Popularity đơn thuần.

## 5. Tìm trọng số

Dùng tìm kiếm lưới trên tập Validation, ví dụ thử các trọng số với bước `0.05` hoặc `0.10`:

```text
w_content ∈ [0, 1]
w_major ∈ [0, 1]
w_popularity = 1 - w_content - w_major
```

Chỉ giữ các bộ trọng số hợp lệ, không âm và tổng bằng 1.

Mỗi bộ trọng số phải được ghi lại cùng các chỉ số Validation. Không được thử trọng số trên Test rồi chọn bộ đẹp nhất.

## 6. Tiêu chí chọn trọng số

Không nhất thiết chỉ tối đa hóa một chỉ số. Có thể chọn bộ cân bằng theo nguyên tắc:

- Precision/Recall/Hit Rate không thấp bất hợp lý.
- Coverage cao hơn Popularity.
- Kết quả cho sinh viên mới hợp lý.
- Thời gian phản hồi đáp ứng yêu cầu hệ thống.

Nếu dùng một điểm tổng hợp để chọn, công thức điểm tổng hợp cũng phải được ghi rõ trước khi xem Test.

## 7. Lý do gợi ý

Kết quả Hybrid nên trả lý do dễ hiểu, ví dụ:

- Phù hợp với chuyên ngành Công nghệ thông tin.
- Tương tự các sách bạn từng mượn.
- Được nhiều sinh viên quan tâm.
- Sách mới phù hợp với chủ đề bạn đang đọc.

Lý do phải dựa trên thành phần thực sự đóng góp vào điểm, không hiển thị câu viết cứng sai với kết quả.

## 8. Đầu ra cần tạo

- Công thức và cách chuẩn hóa điểm.
- Danh sách toàn bộ bộ trọng số đã thử.
- Kết quả Validation của từng bộ.
- Bộ trọng số được chọn và lý do chọn.
- Cấu hình Hybrid có thể thay đổi mà không sửa mã nguồn.
- Kiểm thử cho sinh viên có lịch sử, sinh viên mới và sách mới.

## 9. Tiêu chí hoàn thành

- [ ] Ba thành phần điểm đã chuẩn hóa cùng thang.
- [ ] Trọng số được chọn bằng Validation, không phải cảm tính.
- [ ] Có kết quả riêng cho người dùng mới và sách mới.
- [ ] Có lý do gợi ý tương ứng với điểm thực tế.
- [ ] Đã đóng băng cấu hình trước khi chạy Test ở ALG-4.

---

# ALG-4 — Đánh giá, đối chứng và xuất số liệu

## 1. Mục tiêu

Đánh giá các phương pháp trên tập Test chưa từng dùng để điều chỉnh mô hình, sau đó tạo bảng số liệu và biểu đồ dùng được trong báo cáo.

## 2. Phương pháp cần so sánh

Tối thiểu:

1. Popularity.
2. Content-Based.
3. Hybrid đã chốt ở ALG-3.

Có thể thêm Collaborative nếu ALG-2 đã triển khai và dữ liệu phù hợp.

## 3. Chỉ số bắt buộc

### Precision@10

Tỷ lệ sách phù hợp trong 10 sách được gợi ý:

```text
Precision@10 = số sách gợi ý trúng trong Test / 10
```

### Recall@10

Khả năng tìm lại các sách phù hợp mà sinh viên thực sự mượn trong Test:

```text
Recall@10 = số sách gợi ý trúng / tổng số sách phù hợp trong Test
```

### Hit Rate@10

Tỷ lệ sinh viên có ít nhất một sách trong Top 10 trùng với Test:

```text
HitRate@10 = số sinh viên có ít nhất một hit / số sinh viên đủ điều kiện
```

### Catalog Coverage

Độ bao phủ danh mục:

```text
Coverage = số đầu sách khác nhau từng được gợi ý
           / tổng số đầu sách đủ điều kiện
```

### Thời gian phản hồi

- Đo bằng mili-giây.
- Nên báo cáo trung vị `P50` và mức cao `P95`, không chỉ dùng một lần chạy.
- Chạy trên cùng máy, cùng dữ liệu và cùng K.

## 4. Đánh giá khởi đầu lạnh

Cần có bảng riêng cho:

- Sinh viên mới chưa có lịch sử.
- Sinh viên có rất ít lịch sử.
- Sách mới chưa có lượt mượn.

Với người dùng hoàn toàn mới không có Test lịch sử, có thể đánh giá gián tiếp bằng mức phù hợp chuyên ngành, độ đa dạng/bao phủ hoặc một kịch bản kiểm tra có kiểm soát. Phải ghi rõ đây không phải Precision/Recall theo lịch sử thực tế.

## 5. Quy tắc đánh giá công bằng

- Cùng tập Test.
- Cùng danh sách sách ứng viên.
- Cùng giá trị K.
- Cùng quy tắc loại sách đã mượn.
- Cùng môi trường phần cứng.
- Cùng seed nếu có thành phần ngẫu nhiên.
- Lặp lại đo thời gian đủ nhiều lần và loại giai đoạn khởi động nếu cần.

## 6. Bảng kết quả đề xuất

| Phương pháp | Precision@10 | Recall@10 | Hit Rate@10 | Coverage | P50 | P95 | Người dùng mới |
|---|---:|---:|---:|---:|---:|---:|---|
| Popularity | Số liệu thực nghiệm | Số liệu thực nghiệm | Số liệu thực nghiệm | Số liệu thực nghiệm | Số liệu thực nghiệm | Số liệu thực nghiệm | Nhận xét |
| Content-Based | Số liệu thực nghiệm | Số liệu thực nghiệm | Số liệu thực nghiệm | Số liệu thực nghiệm | Số liệu thực nghiệm | Số liệu thực nghiệm | Nhận xét |
| Hybrid | Số liệu thực nghiệm | Số liệu thực nghiệm | Số liệu thực nghiệm | Số liệu thực nghiệm | Số liệu thực nghiệm | Số liệu thực nghiệm | Nhận xét |

Không thay chữ “Số liệu thực nghiệm” bằng số tự ước lượng.

## 7. Biểu đồ nên tạo

- Biểu đồ cột so sánh Precision, Recall và Hit Rate.
- Biểu đồ Coverage của từng phương pháp.
- Biểu đồ thời gian phản hồi P50/P95.
- Biểu đồ hoặc bảng riêng cho người dùng mới.
- Nếu đã thử nhiều trọng số: biểu đồ thể hiện quan hệ giữa trọng số và kết quả Validation.

## 8. Đầu ra cần tạo

- Kết quả chi tiết theo từng sinh viên.
- Bảng tổng hợp theo từng thuật toán.
- Tệp CSV/JSON dùng để tạo bảng và biểu đồ.
- Các hình biểu đồ xuất từ số liệu thật.
- Tệp thông tin lần chạy gồm commit, cấu hình, thời gian, máy chạy và phiên bản dữ liệu.
- Bản kết luận nêu rõ điểm mạnh, yếu và sự đánh đổi.

## 9. Tiêu chí hoàn thành

- [ ] Tập Test chỉ được dùng sau khi chốt thuật toán và trọng số.
- [ ] Có đủ chỉ số Precision, Recall, Hit Rate, Coverage và Latency.
- [ ] Có phân tích người dùng mới/sách mới.
- [ ] Có bảng và biểu đồ được sinh từ tệp kết quả thật.
- [ ] Có thể truy ngược mỗi số liệu về cấu hình và lần chạy.
- [ ] Kết luận không che giấu chỉ số mà Hybrid không đứng đầu.

---

# ALG-5 — Đóng gói báo cáo, PowerPoint và nội dung bảo vệ

## 1. Mục tiêu

Chuyển toàn bộ kết quả ALG-1 đến ALG-4 thành nội dung dễ đưa vào báo cáo khóa luận và dễ trình bày trước hội đồng.

## 2. Cấu trúc nội dung báo cáo đề xuất

### 2.1. Đặc điểm bài toán

- Dữ liệu chủ yếu là lịch sử mượn, ít đánh giá sao.
- Ma trận tương tác thưa.
- Có nhiều sinh viên mới.
- Có sách mới chưa có lượt mượn.
- Chuyên ngành sinh viên liên quan trực tiếp đến nhu cầu đọc.

### 2.2. Các phương pháp được xem xét

- Popularity.
- Content-Based.
- Collaborative Filtering.
- K-Means.
- Mô hình học sâu.
- Hybrid.

Không cần triển khai tất cả, nhưng phải giải thích bằng đặc điểm dữ liệu vì sao một số phương pháp không được chọn làm phương án chính.

### 2.3. Phương pháp đề xuất

- Mô tả ba thành phần của Hybrid.
- Công thức tính điểm.
- Cách xử lý sinh viên mới và sách mới.
- Cách tìm trọng số trên Validation.
- Cách sinh lý do gợi ý.

### 2.4. Thiết kế thực nghiệm

- Nguồn dữ liệu.
- Quy tắc làm sạch.
- Cách chia Train/Validation/Test theo thời gian.
- Danh sách chỉ số.
- Điều kiện chạy công bằng.

### 2.5. Kết quả và phân tích

- Bảng số liệu ALG-4.
- Biểu đồ đối chứng.
- Phân tích điểm mạnh và điểm yếu.
- Phân tích người dùng mới.
- Phân tích độ bao phủ và thời gian phản hồi.

### 2.6. Hạn chế và hướng phát triển

- Dữ liệu lịch sử còn ít hoặc chưa cân bằng.
- Chưa có nhiều đánh giá rõ ràng từ người dùng.
- Ánh xạ chuyên ngành–chủ đề còn phụ thuộc chất lượng dữ liệu.
- Có thể nghiên cứu lọc cộng tác hoặc học sâu khi dữ liệu lớn hơn.
- Có thể bổ sung đánh giá trực tuyến sau khi triển khai thực tế.

## 3. Nội dung PowerPoint đề xuất

Có thể trình bày trong 5–7 slide:

1. Bài toán và đặc điểm dữ liệu.
2. Vì sao các phương pháp đơn lẻ chưa đủ.
3. Kiến trúc Hybrid và công thức điểm.
4. Thiết kế thực nghiệm.
5. Bảng/biểu đồ kết quả.
6. Xử lý người dùng mới và sách mới.
7. Kết luận, hạn chế và hướng phát triển.

## 4. Câu trả lời chuẩn bị cho hội đồng

### Tại sao không chỉ dùng lọc cộng tác?

Dữ liệu thư viện chủ yếu là lịch sử mượn và rất ít đánh giá điểm. Ma trận tương tác còn thưa, đồng thời có nhiều sinh viên và sách mới. Vì vậy, lọc cộng tác dùng riêng dễ gặp vấn đề khởi đầu lạnh và chưa khai thác được thông tin nội dung cùng chuyên ngành.

### Tại sao không dùng K-Means?

K-Means phù hợp để chia nhóm sinh viên hoặc sách, nhưng không trực tiếp tạo thứ tự Top 10 sách cho từng sinh viên. Sau khi phân cụm vẫn cần thêm một cơ chế chấm điểm và xếp hạng.

### Tại sao không dùng mô hình học sâu?

Quy mô và độ dày dữ liệu hiện tại chưa đủ để mô hình học sâu phát huy ưu thế. Mô hình cũng khó giải thích hơn, tốn tài nguyên hơn và không phù hợp bằng Hybrid trong phạm vi triển khai hiện tại.

### Tại sao chọn Hybrid?

Hybrid kết hợp được lịch sử đọc, nội dung sách, chuyên ngành và độ phổ biến. Nhờ đó hệ thống vừa cá nhân hóa cho sinh viên đã có lịch sử, vừa có phương án cho sinh viên mới và vẫn có thể gợi ý sách mới chưa có lượt mượn.

### Trọng số Hybrid dựa vào đâu?

Nhóm thử nhiều bộ trọng số trên tập Validation và chọn bộ có sự cân bằng tốt nhất theo các chỉ số đã định trước. Tập Test chỉ được dùng để đánh giá cuối, không dùng để chọn trọng số.

### Mô hình có phải tốt nhất không?

Không có một mô hình tốt nhất cho mọi bộ dữ liệu. Nhóm kết luận Hybrid là phương pháp phù hợp nhất trong phạm vi dữ liệu, nghiệp vụ và điều kiện triển khai của thư viện HUIT, dựa trên kết quả đối chứng thực tế.

## 5. Gói bàn giao cho người làm Word/PPT

- Tóm tắt dữ liệu ALG-1.
- Mô tả thuật toán ALG-2 và ALG-3.
- Công thức cùng bộ trọng số chính thức.
- Bảng kết quả và biểu đồ ALG-4.
- Danh sách hạn chế và hướng phát triển.
- Bộ câu hỏi–trả lời hội đồng.
- Đường dẫn tới mã nguồn thực nghiệm và tệp cấu hình.
- Ảnh chụp chức năng gợi ý trên Web/Mobile nếu cần minh họa.

## 6. Tiêu chí hoàn thành

- [ ] Báo cáo giải thích lựa chọn từ dữ liệu đến kết quả, không chỉ mô tả thuật toán.
- [ ] Mọi con số trong Word/PPT truy được về kết quả ALG-4.
- [ ] Có câu trả lời cho các lựa chọn thay thế thường bị hội đồng hỏi.
- [ ] Có nêu hạn chế, không tuyên bố Hybrid tốt nhất trong mọi trường hợp.
- [ ] Người làm Word/PPT có đủ bảng, biểu đồ và nội dung mà không phải tự suy đoán.

---

# Phụ lục triển khai và theo dõi

## 1. Cấu trúc triển khai hiện tại

Pipeline ALG-1 được đặt trong module Backend để đọc trực tiếp các repository hiện có và tránh tạo một luồng trích xuất dữ liệu khác với nghiệp vụ đang chạy:

```text
backend/src/main/java/com/huit/library/modules/recommendation/
  config/RecommendationExperimentExportRunner.java
  dto/RecommendationExperimentDataset.java
  service/RecommendationExperimentDatasetService.java
  service/RecommendationExperimentExporter.java

backend/target/recommendation-alg1/
  summary.json
  config.json
  interactions_clean.csv
  train.csv
  validation.csv
  test.csv
  new_users.csv
  new_books.csv
  data_dictionary.md
  README.md
```

Thư mục `target` là đầu ra có thể tái tạo và không đưa vào Git. Chỉ sao chép bảng, biểu đồ và tóm tắt đã kiểm chứng cần bàn giao sang thư mục tài liệu Word/PPT.

---

## 2. Nhật ký quyết định

Điền bảng này trong quá trình thực hiện để tránh quên lý do đã chọn:

| Ngày | Gói | Quyết định | Căn cứ | Người chốt |
|---|---|---|---|---|
| 04/11/2026 | ALG-1 | Chia thời gian theo từng sinh viên, giữ tối đa hai tương tác cuối cho Validation/Test; gộp cặp sinh viên–sách | Tránh rò rỉ tương lai, giữ được nhóm người dùng ít dữ liệu và thống kê số lần mượn lặp lại | Nhóm |
| Chưa cập nhật | ALG-3 | 0.50/0.35/0.15 chỉ là trọng số khởi đầu | Phải kiểm chứng trên Validation | Nhóm |

---

## 3. Tài liệu dự án liên quan

- [Tuần 10A — Nền dữ liệu gợi ý](TUAN_10A_NEN_DU_LIEU_GOI_Y.md)
- [Tuần 10B — Thuật toán cơ sở](TUAN_10B_THUAT_TOAN_CO_SO.md)
- [Tuần 10C — Hybrid và Cold Start](TUAN_10C_HYBRID_COLD_START.md)
- [Tuần 10D — Tích hợp giao diện gợi ý](TUAN_10D_TICH_HOP_GIAO_DIEN_GOI_Y.md)
- [Tuần 10E — Đánh giá thuật toán](TUAN_10E_DANH_GIA_THUAT_TOAN_GOI_Y.md)

Tài liệu tham khảo nền tảng:

- Robin Burke, *Hybrid Recommender Systems: Survey and Experiments*: <https://doi.org/10.1023/A:1021240730564>
- *Time to Split: Towards a Temporal Evaluation of Recommender Systems*: <https://doi.org/10.1145/3705328.3748164>
- Nghiên cứu về ảnh hưởng của lựa chọn thiết kế trong đánh giá ngoại tuyến: <https://doi.org/10.1007/s10791-020-09371-3>

---

## 4. Bước tiếp theo

ALG-1 và ALG-2 đã hoàn thành. Bước tiếp theo là **ALG-3 — hoàn thiện Hybrid và lựa chọn trọng số trên Validation**. Không dùng Test để chọn trọng số và không diễn giải các chỉ số ALG-2 hiện tại như một kết luận thống kê vì nhóm đánh giá mới chỉ có một người dùng.
