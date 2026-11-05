# Checkpoint tích hợp main ngày 05 11 2026 đã hoàn tất

## Baseline và bản sao lưu

- Trước tích hợp: `main` tại `756870b271f9098f12c301515deb741005b49482`, 177 file local đã sửa hoặc chưa được theo dõi.
- Incoming: `origin/main` tại `b88ac60` — `feat: complete mobile workflows and recommendation experiments`, 192 đường dẫn thay đổi.
- Main đã fast-forward đến commit incoming. Phần local được khôi phục và giữ chưa commit, chưa push.
- Bản sao ZIP, patch và manifest: `.codex-tmp/merge-main-b88ac60/`. Đã đối chiếu đủ 177 file với stash trước khi làm sạch worktree.
- Stash dự phòng được giữ: `0e2dea1fdf3616d958a96c70b08fe2c4f1f5582d` — `codex: preserve local fixes and documents before main b88ac60`.

## Hòa giải

- Đã giải quyết 8 file conflict: 5 API Markdown bị incoming xóa để đổi sang Word, PROJECT_CONTEXT.md, LoanDetailEntity và LoanService.
- Giữ 5 Markdown có sửa mới ở local và các Word incoming. Không bỏ tài liệu rà soát sáu file ở batch trước.
- Giữ cả hai bộ cột recall và hợp đồng API của Web/Mobile. Hai luồng ghi cùng chứng cứ; đọc có fallback, lấy số ngày từ chính sách; thu hồi không được ghi đè mốc nhận sách hoặc báo mất.
- Khóa thu hồi theo thứ tự phiếu rồi chi tiết để tương thích luồng nhận trả; loại bỏ kiểm tra gia hạn bị lặp do merge.
- Giữ F02 thông báo bất đồng bộ sau commit và thay đổi sender/token handling của incoming; giữ các fix local khác.
- Chưa áp migration incoming vào DB ứng dụng. Kiểm tra migration phải dùng PostgreSQL cô lập.
- Cập nhật fixture đủ giá/cọc và bốn trường chính sách mới; sửa test reflection theo tham số bookId của API eligibility. Không hạ điều kiện giá/cọc hay phân quyền để làm test qua.
- Sửa preflight đang nhận nhầm cấu hình Firebase Android: chỉ loại trường client[].api_key[].current_key khỏi chuỗi quét tại đúng google-services.json. Các field và file khác vẫn quét; thêm script kiểm tra phạm vi và bước CI. Tham chiếu: https://firebase.google.com/docs/projects/api-keys. Không sửa giá trị cấu hình Firebase của incoming.
- PROJECT_CONTEXT.md đã hòa giải thông tin hai phía, sửa tham chiếu các Markdown chuyển sang Word và cập nhật baseline. Bộ sáu tài liệu rà soát giữ nguyên nội dung/hash đầu ra; README đánh dấu rõ snapshot trước b88ac60, không tự nhận là hồ sơ mới sau merge.

## Kiểm tra đã thực hiện

- Git: HEAD và origin/main cùng b88ac60, ahead/behind 0/0. Không còn unmerged paths, conflict marker hoặc lỗi git diff --check. Phần local tiếp tục chưa stage/chưa commit như trước; chưa push.
- Backend: biên dịch source và test source PASS bằng Maven cache 3.9.9/JDK 21, release Java 17.
- Backend full run đầu: 417 test, 3 failure, 4 error, 1 skipped. Nguyên nhân là fixture giá/cọc và reflection còn dùng hợp đồng cũ. Sau sửa, chạy lại 4 nhóm liên quan **40/40 PASS**. Kết quả mới nhất của **101 suite: 417 test, 0 failure, 0 error, 1 skipped** — tức 416 PASS. Không ghi lượt full đầu là PASS.
- Ca skipped là live-read-only probe của ShelfLocationIntegrationTest, cần bật chủ động. Không dùng DB ứng dụng cho lượt kiểm tra này.
- LoanRecallIntegrationTest: **7/7 PASS**, gồm Web→Mobile và Mobile→Web với recallReturnDays=3, giữ hai bộ chứng cứ và không tạo thêm thông báo/giao dịch. Mobile recall chặn sách đã nhận vật lý hoặc đã báo mất. LoanRecallMigrationIntegrationTest kiểm tra cả hai migration recall và policy cùng tồn tại trên PostgreSQL thật cô lập.
- Web: sau bổ sung fixture đúng interface mới, **58 file test, 342/342 PASS**, production build PASS. Còn cảnh báo CSS budget ở home/history, CommonJS qrcode và sourcemap ZXing; không chặn gate.
- Preflight: **0 lỗi, 3 cảnh báo CSV >50 MB**. docker compose --env-file .env.example config --quiet PASS; script test-preflight-secret-scan.ps1 PASS cú pháp và 4 ca phạm vi/âm.
- Bảo toàn: không thiếu file nào trong 177 file local ban đầu; sáu đầu ra tài liệu khớp SHA-256 trước merge. ZIP và stash còn để đối chiếu/khôi phục. Các thay đổi nội dung ngoài vùng overlap chỉ là hòa giải recall, fixture, CI/preflight và ghi chú/checkpoint đã nêu.

## Giới hạn và trạng thái dừng

- Không tìm thấy Flutter/Dart SDK trên PATH hoặc các vị trí cài đặt thông thường đã kiểm tra, nên chưa chạy flutter analyze/test/build. Không tuyên bố nghiệm thu Mobile hoặc push thiết bị thật.
- Không khởi chạy Web/backend phục vụ người dùng, không áp các migration incoming vào DB ứng dụng, không deploy và không push.
- Không còn bước xử lý conflict tự động. Lần tiếp theo đọc checkpoint này và git status trước khi commit/push hoặc chạy ứng dụng; đặc biệt phải rà migration/seed incoming trước khi cập nhật DB có dữ liệu thật.

Log và báo cáo máy đọc ở `.codex-tmp/merge-main-b88ac60/`: backend-compile.log, backend-tests.log, backend-rerun.log, backend-final-results.json, web-tests-final.log, web-build.log, preflight-final.log và final-verification.json. Maven Wrapper của checkout vẫn không dùng được; đã dùng cùng Maven cache/JDK được ghi trong context trước đó. Fetch dùng Git schannel theo từng lệnh để xác minh TLS qua kho chứng chỉ Windows; không tắt SSL verification và không đổi cấu hình Git toàn cục.
