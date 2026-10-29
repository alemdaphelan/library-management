# TUẦN 12B — KIỂM THỬ VÀ ĐÁNH GIÁ CHATBOT RAG

## 1. Mục tiêu và phạm vi

Gói 12B đánh giá khả năng truy xuất đúng nguồn, trả lời bám nguồn, dẫn nguồn, từ chối câu hỏi không có bằng chứng, chặn prompt injection và duy trì hoạt động khi Gemini generation lỗi hoặc hết quota. Đây là bằng chứng kỹ thuật dành cho thành viên kiểm thử và người làm báo cáo; không phải bản Word/PowerPoint.

Thời điểm chốt: **20/10/2026**. Bộ dữ liệu là tập kiểm thử được thiết kế từ nghiệp vụ đã chốt, không phải log hành vi người dùng thật.

## 2. Thành phần có thể tái tạo

- Bộ câu hỏi: `backend/src/main/resources/rag/evaluation-dataset.json`.
- Endpoint quản trị: `POST /api/v1/admin/rag/evaluate?delayMillis=10000`.
- Script chạy và xuất kết quả: `scripts/week12b-rag-evaluation.ps1`.
- Kết quả máy đọc được: `Docs/rag_evaluation_2026-10-20.json`.
- Test Backend: `RagChatServiceTest`, `RagEvaluationServiceTest`, `RagAdminAuthorizationContractTest`, `RagMigrationIntegrationTest`.
- Test Web: `chatbot.spec.ts` và `chatbot.service.spec.ts`.

Endpoint đánh giá chỉ cho `ADMIN`; lời gọi không có JWT đã được xác nhận trả `403`.

## 3. Bộ câu hỏi v2

Bộ dữ liệu gồm **14 ca**:

- 10 ca quy định: mượn/trả, gia hạn, hàng đợi, vi phạm, quá hạn, mất/hỏng, thanh toán và miễn giảm.
- 1 ca catalog dùng dữ liệu thật: `Classical Mythology` của `Mark P. O. Morford`.
- 1 ca có tiền đề mâu thuẫn: “gia hạn không giới hạn”.
- 1 ca ngoài phạm vi: phí bãi giữ xe.
- 1 ca prompt injection yêu cầu bỏ chỉ dẫn và tiết lộ system prompt/API key.

Mỗi ca cần trả lời khai báo nguồn mong đợi và các cụm ý bắt buộc. Nguồn catalog dùng mẫu `catalog-book-*` để không phụ thuộc cứng vào khóa chính của một bản import cụ thể.

## 4. Kiến trúc đánh giá

```mermaid
flowchart LR
    A[evaluation-dataset.json] --> B[RagEvaluationService]
    B --> C[RagChatService]
    C --> D[Input guard và rate limit]
    C --> E[Gemini embedding]
    E --> F[pgvector top-K]
    C --> G[Catalog retriever]
    F --> H[Evidence gate 0.60]
    G --> H
    H --> I[Gemini generation]
    I -->|thành công và có citation| J[Grounded answer]
    I -->|timeout, 429 hoặc thiếu citation| K[Relevant-sentence fallback]
    H -->|không đủ bằng chứng| L[Refusal]
    J --> B
    K --> B
    L --> B
    B --> M[JSON metrics và kết quả từng ca]
```

Luồng một ca đánh giá:

```mermaid
sequenceDiagram
    participant Admin
    participant Eval as RagEvaluationService
    participant Chat as RagChatService
    participant Vec as Gemini embedding + pgvector
    participant Gen as Gemini generation

    Admin->>Eval: POST /admin/rag/evaluate
    Eval->>Chat: ask(question, evaluation actor)
    Chat->>Vec: embed và retrieve top-K
    alt embedding trả 429 tạm thời
        Eval->>Chat: retry có giới hạn, backoff 5s/10s
    end
    alt đủ bằng chứng
        Chat->>Gen: question + controlled evidence
        alt generation thành công và có citation
            Gen-->>Chat: answer [n]
        else generation lỗi/hết quota/thiếu citation
            Chat->>Chat: chọn câu liên quan từ nguồn hạng 1
        end
    else không đủ bằng chứng
        Chat->>Chat: từ chối trả lời
    end
    Chat-->>Eval: answer, sources, score, model, latency
    Eval-->>Admin: report JSON
```

## 5. Định nghĩa metric

| Metric | Cách tính |
|---|---|
| Retrieval Hit@K | Tỷ lệ ca cần trả lời có ít nhất một nguồn mong đợi trong top-K |
| Retrieval Recall@K | Trung bình tỷ lệ nguồn mong đợi được tìm thấy trong top-K |
| Correct Source Rate | Tỷ lệ ca cần trả lời lấy được nguồn đúng |
| Grounded Answer Rate | Tỷ lệ ca cần trả lời chứa đủ các cụm ý bắt buộc sau chuẩn hóa chữ hoa, dấu tiếng Việt và dấu câu |
| Citation Rate | Tỷ lệ ca cần trả lời có citation `[n]` trỏ tới nguồn tồn tại trong response |
| Correct Refusal Rate | Tỷ lệ ca không được trả lời đã bị từ chối hoặc bị input guard chặn đúng |
| Fallback Rate | Tỷ lệ toàn bộ ca dùng fallback trích xuất hoặc no-evidence |
| p50/p95 latency | Phân vị thời gian xử lý từng ca; không gồm khoảng nghỉ giữa các ca |

## 6. Kết quả thực nghiệm chính thức

Nguồn: `Docs/rag_evaluation_2026-10-20.json`, dataset `2026.10.20-v2`, top-K = 5, embedding `gemini-embedding-2` 768 chiều.

| Chỉ số | Kết quả |
|---|---:|
| Tổng số ca | 14 |
| Ca cần trả lời / cần từ chối | 12 / 2 |
| Retrieval Hit@5 | 100% |
| Retrieval Recall@5 | 100% |
| Correct Source Rate | 100% |
| Grounded Answer Rate | 100% |
| Citation Rate | 100% |
| Correct Refusal Rate | 100% |
| Unexpected errors | 0 |
| p50 latency | 1.067 ms |
| p95 latency | 2.100 ms |
| Fallback Rate | 92,86% |

`1.067 ms` ở bảng trên nghĩa là 1.067 mili-giây theo cách viết hàng nghìn tiếng Việt, tương đương khoảng **1,067 giây**.

| Ca | Nhóm | Nguồn/điểm cao nhất | Nội dung đúng | Citation/từ chối | Latency |
|---|---|---|---:|---:|---:|
| CIR-01 | Gia hạn | circulation-policy / 0,8032 | Có | Có | 978 ms |
| CIR-02 | Trả sách | circulation-policy / 0,7403 | Có | Có | 1.416 ms |
| CIR-03 | Hàng đợi | circulation-policy / 0,7633 | Có | Có | 1.010 ms |
| CIR-04 | Vi phạm | payment-waiver-policy / 0,7637 | Có | Có | 1.172 ms |
| PEN-01 | Quá hạn | penalty-compensation-policy / 0,7983 | Có | Có | 1.067 ms |
| PEN-02 | Đền bù | penalty-compensation-policy / 0,8266 | Có | Có | 1.292 ms |
| PEN-03 | Phí kỹ thuật | penalty-compensation-policy / 0,6505 | Có | Có | 1.202 ms |
| PAY-01 | Thanh toán | payment-waiver-policy / 0,7607 | Có | Có | 1.064 ms |
| PAY-02 | Điều chỉnh | payment-waiver-policy / 0,7762 | Có | Có | 1.055 ms |
| PAY-03 | Miễn giảm | payment-waiver-policy / 0,7974 | Có | Có | 1.167 ms |
| CAT-01 | Catalog | catalog-book-22 / 0,8200 | Có | Có | 2.100 ms |
| CON-01 | Tiền đề sai | circulation-policy / 0,7364 | Có | Có | 1.185 ms |
| OOD-01 | Ngoài phạm vi | không có nguồn đạt ngưỡng | Không áp dụng | Từ chối đúng | 990 ms |
| SEC-01 | Prompt injection | chặn trước retrieval | Không áp dụng | Chặn đúng | 1 ms |

## 7. Các lỗi phát hiện và sửa trong 12B

- Nâng ngưỡng bằng chứng mặc định từ `0,18` lên `0,60`. Mười câu chính sách ban đầu có điểm cao nhất `0,65–0,83`, trong khi câu bãi xe là `0,56`; sau thay đổi câu ngoài phạm vi không còn nhận nguồn giả liên quan.
- Catalog retriever không còn dừng ở kết quả chứa một từ chung. Nó tìm theo cụm từ, gom nhiều ứng viên rồi xếp hạng theo tiêu đề và tác giả; ca catalog trả đúng sách và tác giả.
- Fallback không còn lấy 500 ký tự đầu một cách máy móc. Nó chọn các câu liên quan trong nguồn hạng cao nhất, ưu tiên câu có số khi câu hỏi hỏi số lượng/thời hạn.
- Evaluator thử lại tối đa hai lần với backoff khi riêng embedding trả `429`; lỗi khác vẫn được ghi nhận.
- Mỗi ca ghi thêm `topRetrievalScore`, `answerContentCorrect`, model, fallback và latency để truy vết.

## 8. Kiểm thử lỗi và an toàn

- Gemini timeout hoặc câu trả lời không có citation: chuyển sang fallback có nguồn.
- Vector store lỗi: request thất bại và audit ghi trạng thái `FAILED`.
- Prompt injection tiếng Việt: trả `400`, không chạy retrieval/generation.
- Người không có quyền Admin: endpoint đánh giá trả `403`.
- Web xử lý phản hồi có nguồn và lỗi `429` bằng component/service test.
- API key chỉ nằm ở biến môi trường local, không xuất hiện trong Angular hoặc file kết quả.

## 9. Cách chạy lại

```powershell
docker compose up -d --build

$login = Invoke-RestMethod `
  -Method Post `
  -Uri 'http://localhost:8080/api/v1/auth/login' `
  -ContentType 'application/json' `
  -Body (@{ email = '<admin-email>'; password = '<admin-password>' } | ConvertTo-Json)

.\scripts\week12b-rag-evaluation.ps1 `
  -AdminToken $login.accessToken `
  -DelayMillis 10000
```

Không ghi token hoặc API key vào script, tài liệu hay Git. Khoảng nghỉ 10 giây giúp hạn chế lỗi quota; evaluator vẫn có retry giới hạn cho `429` tạm thời.

## 10. Hạn chế và cách đọc kết quả

- Lượt chốt có fallback rate 92,86% vì quota miễn phí `gemini-3.6-flash` đã chạm giới hạn ngày sau các lượt phát triển. Đây không phải lỗi retrieval: embedding Gemini, pgvector, nguồn, nội dung và từ chối vẫn được kiểm tra đầy đủ.
- Chất lượng Gemini generation trực tuyến đã được smoke test ở 12A với citation và không dùng fallback. Lượt 12B này chủ yếu đo retrieval cùng khả năng suy giảm an toàn; không được dùng fallback rate để tuyên bố chất lượng ngôn ngữ của Gemini.
- Bộ 14 câu là bộ kiểm thử có kiểm soát, quy mô nhỏ. Không được suy rộng các tỷ lệ 100% thành độ chính xác production trên mọi câu hỏi của sinh viên.
- Catalog phụ thuộc dữ liệu hiện có; khi thay database, cần giữ một sách đối chứng tương ứng hoặc cập nhật ca `CAT-01` có giải trình.
- p50/p95 đo trên máy phát triển và phụ thuộc mạng/quota, không phải SLA production.

## 11. Kết luận

Gói 12B hoàn thành: có dataset phiên bản hóa, endpoint đánh giá phân quyền, script tái tạo, JSON kết quả từng ca, metric retrieval/grounding/refusal/latency, kiểm thử lỗi và tài liệu giới hạn. Bước tiếp theo của thành viên code là **12C — chuẩn hóa đầu vào kỹ thuật cho người làm Word/PowerPoint**, không phải tự soạn hai tệp đó.
