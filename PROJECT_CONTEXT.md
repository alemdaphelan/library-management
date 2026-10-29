# HUIT Library — project context

Snapshot: **2026-10-30 (Asia/Saigon)**. Integration baseline: **all remote feature branches (`feature/week-6-backend-apis`, `feature/week-11`, `feature/library-role`, and `usecase`) merged for publication to `main`**. Check `git log` and `git status` for the exact current revision; earlier checkpoint statements about uncommitted work describe their recording time. Verification: `Docs/BAN_GIAO_BATCH_THU_THU.md`.
Purpose: let a fresh coding session locate the right code without re-reading the repository. English keeps this technical map compact; UI and business documentation are mainly Vietnamese.
Scope: architecture, navigation and implementation landmarks verified by source/configuration inspection. This is not a line-by-line code audit, live deployment inventory or a new test report. Code wins when this map disagrees.

## 1. Product and stack

University library management for **STUDENT, LIBRARIAN, ADMIN**: reader accounts/cards, catalog, physical copies, borrowing/returns, inventory, penalties/debt, digital resources, book recommendations and RAG chatbot.

- Backend: **Java 17, Spring Boot 3.2.5**, Spring Security/JWT, JPA, Liquibase, Maven wrapper. Single modular Spring application, not separate deployed microservices.
- Web: **Angular 21.2**, TypeScript 5.9, RxJS 7.8; standalone application; Vitest via Angular unit-test builder. Node 22 in CI.
- Mobile: Flutter, **Dart SDK ^3.10.4**, `http`, shared preferences, QR scanning, Firebase messaging/local notifications.
- Main data: **PostgreSQL 15 + pgvector**; Redis for OTP/token revocation/recommendation cache. Digital files stored on disk/volume.
- Compose also starts MongoDB, Elasticsearch, Kafka/Zookeeper. Dependencies and legacy repositories exist, but catalog search uses PostgreSQL JPA. Do not infer an operational search/event pipeline from containers alone.
- External integrations: SMTP/Mailpit, Gemini, Firebase FCM, VNPAY sandbox; availability depends on configuration.
- Room booking is excluded and its remaining code is legacy; kiosk, live university sync and shift handover are deferred. See README scope before expanding them.

## 2. Repository map

```text
backend/
  src/main/java/com/huit/library/
    LibraryApplication.java        application bootstrap
    config/                        security, CORS, Swagger, Firebase, demo seeder
    core/security/                 JWT/filter/principal/token services
    core/entity/, core/response/   shared entities and ApiResponse
    modules/<domain>/             controller, dto, entity, repository, service
    integration/sync/entity/      legacy university sync log
    common/, exception/           inspect only if the task needs them
  src/main/resources/
    application.yml               application/env defaults
    db/changelog/                 Liquibase schema and seed data
    rag/                          curated policy Markdown and evaluation dataset
    data/                         large imported book CSV datasets
  src/test/java/com/huit/library/  domain unit/authorization tests and integration/
  src/test/resources/db/changelog/ focused integration migration masters
  pom.xml, Dockerfile, mvnw, mvnw.cmd, .mvn/wrapper/
  apache-maven-3.9.9/              bundled tool; skip during code searches
web-app/
  src/app/
    app.routes.ts, app.config.ts   routes, providers, HttpClient interceptor
    layouts/{portal-layout,admin-layout}/
    pages/{auth,portal,admin}/     component .ts/.html/.css and adjacent .spec.ts
    services/, guards/, interceptors/
    components/chatbot/           floating chatbot component
  src/environments/environment.ts API origin
  src/styles.css                  shared/global styling
  angular.json, package.json, package-lock.json
mobile-app/
  lib/main.dart                   startup -> push initialization -> SplashScreen
  lib/config/                     API URL and Firebase options
  lib/screens/, lib/services/, lib/widgets/book_cover.dart
  test/                           API, recommendations and widget tests
  android/, ios/, web/, windows/, linux/, macos/ platform projects
Docs/                             architecture Mermaid, policy, ADR, weekly reports
TestChucNang/                      Vietnamese UAT checklists and regression handoff
Word_PPTX/                        report/slide source pack; many copies of Docs
scripts/                          smoke/load/evaluation/preflight/backup tooling
nginx/conf.d/default.conf          gateway routing
.github/workflows/week6-quality.yml current CI, displayed name: Week 11 quality gate
docker-compose.yml, .env.example   local infrastructure and configuration template
```

Root `package.json` only declares `bcryptjs`; it is not an app workspace runner. Run npm commands in `web-app/`.
Root `Books.csv`, backend CSVs, `Docs/extract/`, `docx_content.txt`, temporary text exports, `backend_logs.txt`, `HashTest.java`, `test_auth.ps1` are datasets/exports/helpers, not the normal application entry points.

## 3. Backend domain map

All module paths below are relative to `backend/src/main/java/com/huit/library/modules/`. Most follow controller -> service -> repository/entity; DTOs define payloads. Controllers sometimes invoke services owned by another domain.

| Module | Main entry points and responsibility |
|---|---|
| `auth` | `AuthController/AuthService`: login, refresh, logout, OTP/reset, profile. `UserController/UserService`: students. `LibraryCardController/LibraryCardService`, `ReaderProfileService`: cards and reader profile. `UserEntity` uses UUID `userId`; `StaffRoleEntity` handles staff roles. |
| `catalog` | `BookController/BookService`, `BookCopyController/BookCopyService`, `CategoryController`, `LocationController/LocationService`. Books, barcode copies and Floor -> Bookshelf -> Shelf. `BookRepository` is the main search store; `BookElasticRepository/BookDocument` are not evidence of the main search path. |
| `circulation` | `LoanController/LoanService`: reservations, batches, personal loans, renewals. `CirculationStateService/StateMachine`: checkout/return/manual checkout and state enforcement; shared normal-return/completion helpers. `ReturnInspectionController/Service`: atomic mixed return/lost/damaged receipts and replay. `LibrarianQrController/Service`: signed QR. `WaitlistService`, `LibraryHoursService`, `ReservationNoShowService`, `CirculationMaintenanceService/Scheduler`. |
| `inventory` | `InventoryController/InventoryService`: supplier, located import and receipt detail. `InventoryCatalogController/CatalogIdentificationService/Isbn`: catalog identity/classification, waiting on Admin placement rules. `InventoryAuditService`: barcode audit. `DisposalController/Service`, `DisposalModels`, `Disposal*Entity/Repository`: audit-sourced old-copy proposals, separate from book purchase proposals; Admin decision/execution pending. |
| `dashboard` | Legacy `DashboardController/DashboardService`; `CirculationAnalyticsController/Service/Repository` for real checkout/physical-return buckets, rankings/current student overdue; `HoldingsAnalyticsController/Service/Repository` for current academic holdings and paged materials/copies/assets; `MaterialClassificationService` for staff sourced multi-major audit. `FinanceStockAnalyticsController/Service`, `FinanceAnalyticsRepository`, `ImportAnalyticsRepository`: charges/receipts/deposits/reductions/current debt and completed imports; disposal execution unavailable. DTO records in `CirculationAnalytics`/`HoldingsAnalytics`/`FinanceStockAnalytics`. |
| `interactive` | `BookProposalController/Service`: purchase proposals and moderation. `BookInteractionService`: reviews/wishlist exposed through catalog/user controllers. `FeedbackController`: complaints. Mongo `UserEventDocument/EventLogMongoRepository` also present. |
| `digital` | `DigitalAssetController/Service`: upload, metadata, authorized download, deletion and `DigitalUsageLogEntity`. File bytes on disk, metadata in PostgreSQL. |
| `penalty` | `PenaltyCaseController/Service`: lost/damaged books, replacement acceptance and waiver reviews. `LateFineService`, `OverdueViolationService`, `LibraryViolationService`; configurable compensation rates and library violations. |
| `finance` | `PaymentController/DebtLedgerService`: wallet/debt, partial payments, allocations/receipts, adjustment reviews. `OnlinePaymentController/Service`: VNPAY sessions and callbacks. |
| `reporting` | `StudentObligationReportController/Service/Repository`: staff student overdue/debt union and XLSX. `ProposalSummaryController/Service/Repository/ExcelExporter`: read-only purchase proposal grouping, faculty/date/status filters, group pages and full three-sheet XLSX. Both exporters use POI SXSSF. Academic faculty/department mapping entities represent explicit source mappings, not recommendation taxonomy. |
| `notification` | `NotificationController/Service`: persisted notifications and read state. `NotificationPreferenceService`, `PushDeviceService`, `FirebasePushNotificationService`, `EmailNotificationService`, `StaffNotificationService`; push events dispatched after commit. |
| `orientation` | `OrientationController/Service`: library orientation classes, enrollment, facilitator/student assignment, attendance and Excel import/template. Attendance entity/repository also exists in `auth`. |
| `recommendation` | `BookRecommendationService`: popularity/similar; `TfIdfSimilarityEngine`, `HybridRecommendationService`, `RecommendationCacheService`, `DepartmentKeyNormalizer`, `RecommendationEvaluationService/Controller`. Subject/department/book taxonomy entities. Public recommendation APIs live in `catalog/BookController`. |
| `rag` | `RagChatController/Service`: query orchestration. `RagInputGuard`, `RagRateLimiter`, `RagPrivacyFilter`; catalog/retrieval/suggestion services; chunker, embedding gateway, `GeminiApiClient`; knowledge bootstrap/index/store and admin evaluation. `RagKnowledgeStore` uses JdbcTemplate/pgvector, not JPA repositories. |
| `facilities`, `ai` | Facilities has room/kiosk legacy entities/controller; `ai` directory is not the RAG implementation. |

Shared auth: `config/SecurityConfig.java` and `core/security/{JwtAuthFilter,JwtUtils,TokenService,CustomUserDetails,CustomUserDetailsService}.java`. Method security is enabled. Check controller/service authorization as well as URL matchers; public GET `/books/**` does not by itself describe every endpoint's effective permissions.

## 4. API landmarks and business invariants

**Prefixes are mixed.** Preserve them unless a task explicitly migrates all callers, security rules, gateway and tests together. Do not prepend `/api/v1` universally. Payload/response shapes also vary; inspect DTO/controller and client service rather than assuming every response uses `ApiResponse`.

| API base | Main endpoints / owner |
|---|---|
| `/api/v1/auth` | login/logout/refresh-token, change-password, forgot-password/verify-otp/reset-password, GET/PUT me, avatar |
| `/api/v1/library-card` | GET current reader's card via `LibraryCardController` |
| `/users` | students CRUD/profile, wishlist; inspect `UserController` for exact subpaths |
| `/books`, `/categories`, `/book-copies`, `/locations` | search/CRUD, departments, ISBN lookup, reviews, copy status/location, shelf tree |
| `/books/recommendations[/popular]`, `/books/{id}/similar` | popularity/TF-IDF; `/books/recommendations/personalized` requires identity |
| `/admin/recommendations/evaluation` | Admin recommendation evaluator |
| `/loans` | reserve, reserve-batch, my-loans, my-eligibility, staff, `{ticketId}`, active-item/{barcode}, approve/reject, cancel, renewal, checkout/manual-checkout/return; POST return-inspection for mixed normal/incident items |
| `/waitlist` | join and my queue entries |
| `/api/v1/qr` | tickets/{ticketId}, cards/me, resolve |
| `/inventory`, `/dashboard` | receipts/suppliers/audits; staff GET `/inventory/receipts/{id}` for line locations/barcodes and `/inventory/catalog-identification?isbn=` OR `?bookId=` for catalog classification; dashboard stats |
| `/dashboard/analytics` | staff GET circulation, rankings, overdue, academic-catalog, holdings, materials; GET materials/{BOOK\|DIGITAL}/{id}/copies, digital-assets or classification; PUT classification with expected version, majorIds and mandatory sourceReference |
| holdings/materials filters | facultyId, majorId, exact type/state, optional literal q (title/author substring or exact ID, max 200), page/size on materials; same query affects totals/groups/list. Holdings Summary.coverage gives global physical copies and copies/linked files without current catalog attribution |
| `/dashboard/analytics/finance`, `/dashboard/analytics/stock-movements` | staff GET range/period reports; finance/entries?kind=, finance/payments/{id}/allocations, stock-movements/receipts are paged drill-down; current debt is never historical closing debt; disposal counts are null/WAITING_ADMIN_WORKFLOW |
| `/inventory/disposals` | staff GET candidates?auditId=, GET filtered/paged list, POST idempotent draft batch; GET/PUT `{id}`, POST `{id}/submit` or `/cancel`; owner-only mutations, no physical execution |
| `/proposals` | create, my, admin, admin/{id}/status; staff GET admin/summary, admin/summary/export, admin/summary/faculties |
| `/feedback`, `/api/v1/feedback` | complaint aliases |
| `/digital-assets` | list/upload, metadata update/delete, `{id}/download` |
| `/api/v1/penalty-cases`, `/api/v1/library-violations` | compensation, replacement, waiver, rates, violations |
| `/api/v1/transactions` | payments, ledger/{userId}, my-ledger, adjustments |
| `/api/v1/librarian-reports/student-obligations` | staff GET preview, `/export` full XLSX, `/faculties`, `/classification-coverage` audit of all students; filters mode=OVERDUE/DEBT/EITHER, facultyId, q; only preview accepts page/size |
| `/api/v1/online-payments` | POST sessions, GET sessions/{reference}, vnpay-return, vnpay-ipn |
| `/api/v1/notifications` | list, `{id}/read`, preferences, devices |
| `/orientation-classes` | class CRUD, assignment, attendance, template/import |
| `/api/v1/chatbot/query` | public POST RAG query, guarded/rate limited in RAG services |
| `/api/v1/admin/rag` | Admin status/reindex/evaluate |

Circulation must keep these relationships intact:

- Ticket states: `PENDING -> RESERVED -> BORROWING -> RETURNED` or `BORROWING -> OVERDUE -> RETURNED`. PENDING/RESERVED can become REJECTED/CANCELLED. Terminal states have no outgoing transitions; invalid transitions return 409.
- B01: successful single/batch reservations automatically transition PENDING -> RESERVED in the creation transaction (system actor null, audited reason); the reader still collects books via staff checkout. Staff receive `LOAN_RESERVED`; reader notification keeps compatible type `LOAN_APPROVED` with automatic-hold wording. Valid owned waitlist holds can be claimed by either flow; expired notifications cannot be revived. Batch locks use bookId order and keep the earliest applicable pickup deadline.
- Legacy `POST /loans/{id}/approve` remains staff-only but now revalidates eligibility, open-item limit, pending details, held-copy ownership and expiry. It automatically reserves valid PENDING tickets and returns valid RESERVED tickets without a duplicate transition. No bulk migration; GET detail remains read-only. Web counter invokes this compatibility path when loading old PENDING tickets; manual approval queue/buttons are removed. Loan list links active holds/legacy tickets to the counter and still allows reasoned rejection.
- `LoanTicketEntity` has optimistic versioning; checkout/return use transactions, repository locks and persisted idempotency (`CirculationOperationEntity`). Same key/payload replays the result; conflicting reuse returns 409. Preserve ticket/detail/copy consistency.
- B02: staff POST `/loans/return-inspection` accepts 1–3 loanDetailIds with NORMAL/DAMAGED/LOST and matching existing penalty DTO for incidents. It reuses late-fine/penalty/deposit policy inside one transaction; incidents remain LOST/MAINTENANCE and unresolved cases keep tickets open. Normal returns alone can promote FIFO. Legacy `/loans/return` is still supported; both return paths lock ticket -> details -> copies and accept SETTLED as a completed detail when closing the ticket.
- Inspection receipts snapshot per-item financial deltas, case IDs/statuses and reader balances in nullable `circulation_operations.result_payload` (migration 28). Same key/payload replays the original snapshot even after later payments, without recharging; changed payload or duplicate incidents return 409. Full batch rolls back on any failure. API examples/field meanings: `Docs/API_B02_KIEM_TRA_TRA_SACH.md`.
- B03 Web: `pages/admin/circulation/return-inspection.{ts,html,css}` stages barcode/manual/ticket/reader selections; only explicit confirmation calls `CirculationService.inspectReturn`. Validation/payload helpers: `return-inspection.form.ts`; request/receipt types: `services/return-inspection.models.ts`. Eligibility uses both ticket `status` and detail `itemStatus`, excluding existing incidents/SETTLED. Never call legacy normal return before recording an incident.
- Pending inspection keeps its serialized body/UUID in memory and per-user sessionStorage (`huit_lib_pending_inspection:<encoded profile identity>`), restoring without auto-send. Uncertain responses lock editing/tab changes; retry reuses original body/key, even after closing. Unsent cancellation performs no mutation. Receipt totals use server deltas (`lateFineAdded + penaltyAmount`, not cumulative late fine), with case/debt links. Ticket links use `/admin/circulation?referenceType=LOAN_TICKET&referenceId=<id>&mode=return`. Web/Mobile handoff and acceptance checklist: `Docs/HUONG_DAN_B03_QUAY_TRA_SACH.md`; Flutter has not been upgraded.
- B04 reports only `user_type=STUDENT`. Overdue uses actual due time and open ticket/detail states, excluding existing incidents/fine-stopped items; debt includes returned readers. Aggregate loans, charges, reductions and wallets separately per user before joining. Ledger debt uses payable positive FINE/REPAIR_FEE/COMPENSATION/TECHNICAL_FEE lines; paid-or-deposit excludes completed WAIVER_ADJUSTMENT/ADJUSTMENT amounts. Do not add PAYMENT/allocations or cumulative late fines again. Wallet-only debt remains visible as a reconciliation issue, not invented ledger debt. Each request is a read-only REPEATABLE_READ snapshot; GET does not accrue fees. Preview and export are separate current snapshots with identical filter semantics. See `Docs/API_B04_BAO_CAO_QUA_HAN_CONG_NO.md` for response/Excel columns and B05 handoff.
- Academic mapping: migration 29 creates `academic_faculties(faculty_id, faculty_name)` / `academic_department_mappings(department_value PK, faculty_id FK, major_name)`; migration 30 seeds 17 faculties and 39 majors verified from official HUIT directory/admissions on 2026-10-29. Stable faculty IDs are project IDs; major codes are official. Match explicit aliases with a Unicode/accent/case/punctuation-normalized key, protected by a unique expression index; preserve manual mappings and rawDepartment. CNTT and full IT-faculty names in repository student seeds now classify as FIT. Do not infer faculty from subject-affinity weights, or a specific major from a faculty-only label. Staff GET `/api/v1/librarian-reports/student-obligations/classification-coverage` audits ALL students (not just debt/overdue) and exposes missing department groups; require complete=true to claim full live-DB coverage. Null/unknown values remain visible for correction, never assigned a fabricated faculty. No live DB was running during catalog follow-up. Mapping management UI and historical faculty changes remain outside B04.
- B05 Web `/admin/borrows`: existing loan view plus lazy, retained `StudentObligationReport` child under `pages/admin/borrows/student-obligation-report.{ts,html,css}`. Separate mode/faculty/query/page-size filters call `services/student-obligation-report.service.ts` (models beside it). Preview uses server pagination; changed unapplied filters disable export/paging, and export sends only shown mode/faculty/query. Server money is displayed directly, with wallet/reconciliation issues and global classification coverage. Loan-list dates/status never filter the student report. Ticket output reuses the existing loan modal without losing report state; counter/case/debt links remain available. `report-file-saver.ts` handles Blob/attachment names and URL cleanup. See `Docs/HUONG_DAN_B05_BAO_CAO_WEB.md` for UX/checks; loan list's old aggregate late fine is now labeled cumulative, not outstanding debt.
- Maximum open items: 3. Standard take-home due period: 10 days; per-item renewal adds 10 days, at most once, subject to debt/overdue/card/waitlist rules. On-site deadlines use library hours; checkout has a one-hour fallback. Read `LoanService`, `CirculationStateService`, `LibraryHoursService` before changing deadlines.
- Waitlist is FIFO with a 48-hour hold adjusted for opening days/hours. Scheduler runs every 5 minutes by default; reminder/fine processing uses deduplication.
- Defaults in `application.yml`: late fine 1,000 VND/day, on-site late fine 5,000, technical processing fee 50,000. Compensation rules belong to penalty services and policy docs; do not infer all fees from the ordinary late rate.
- QR format `HUITQR1.<claims-base64url>.<hmac-base64url>`; default expiry 10 minutes. Resolve is ADMIN/LIBRARIAN only. QR identifies cards/tickets; mutations still go through circulation transaction services.
- VNPAY callback validation and payment application belong to `OnlinePaymentService`; preserve signature checks, ownership, amount validation and replay protection when modifying it.
- Digital uploads: PDF/EPUB/MP3/PPTX, 50 MB default. Preserve authorization for download and usage logs; storage root configured by `DIGITAL_STORAGE_PATH`.

Inventory B06/B07 contracts:

- Import lines require positive `shelfId`; old callers without it now receive 400. Optional `floorId`/`bookshelfId` are verified against the actual hierarchy; Web sends both. `InventoryService` prevalidates all books/locations before writes and holds joined hierarchy shared locks through the transaction (compatible with concurrent imports). Each physical copy stores its shelf and receiptDetailId; each receipt line snapshots its location path. Any line/copy failure rolls back receipt/details/copies/notifications. Digital books cannot generate physical copies. GET receipt detail bulk-loads linked copy barcodes; legacy unlinked copies are never guessed by bookId. No import idempotency key yet.
- Migration 31 adds nullable receipt_details.shelf_id/location_path and book_copies.receipt_detail_id, FKs and indexes; historical data is not backfilled. Location path remains the import-time snapshot after renaming a location.
- Staff catalog identification validates/canonicalizes ISBN-10/13, resolves an actual catalog edition, rejects unknown/ambiguous ISBN and checks bookId+ISBN consistency. Explicit bookId permits legacy books without valid ISBN. Classification uses existing category/DDC/active subject assignments with source/evidence, not recommendation affinity or an inferred academic major. ISBN lookup has no external metadata dependency.
- User confirmed Admin placement rules are not implemented. B07 ALWAYS returns `placementStatus=WAITING_ADMIN_RULES`, `suggestedShelfId=null`; Web selects location manually. No rules engine, priority/effective-date resolution or override enforcement exists yet. API and draft Admin contract (not agreed/implemented): `Docs/API_B06_B07_NHAP_KHO_VI_TRI.md`.

B08 purchase proposal reports: `ProposalSummaryService` filters faculty/from/to/status before grouping; preview pages groups, XLSX includes all matching groups/faculty counts/original sources. Valid equivalent ISBN-10/13 merge; different ISBNs never merge by title. Same ISBN with conflicting edition/year splits by full metadata and warns. Missing ISBN uses normalized title/author/publisher/edition/year; insufficient identity or invalid ISBN stays per-proposal. Normalize Unicode/case/whitespace but preserve significant punctuation and Vietnamese accents. Count original proposals and distinct non-null userIds separately; a faculty-filtered report counts only its sources. Academic mapping is current profile data, not historical faculty. Each request is read-only REPEATABLE_READ; preview/export are separate snapshots. No source mutation/approval or automatic sending. In-memory aggregation still reads all filtered sources; no large-volume load acceptance. API/UX/edge cases: `Docs/API_B08_TONG_HOP_DE_XUAT_MUA.md`.

B09–B10 disposal proposals: a completed audit and recognized scan anchor each physical barcode. AVAILABLE/DAMAGED/MAINTENANCE only; active holds/open loan details also block proposals even if copy status is inconsistent. Server hashes a source/copy snapshot, rechecks it under shared audit/scan + ordered copy locks, and saves whole draft batches (1–50) atomically. PostgreSQL advisory request-key lock plus stored owner/body handles creation retries; partial unique index permits only one DRAFT/SUBMITTED per barcode. Owner can edit DRAFT with version/current fingerprint, submit saved content or cancel DRAFT/SUBMITTED; history keeps actor and before/after snapshots per version. No copy status/location/hold changes, deletions, purchase-proposal reuse or actual liquidation events. User confirmed Admin workflow is absent: detail always says WAITING_ADMIN_WORKFLOW. Decision states, authoritative execution/result schema/API and B13 disposal-event data are NOT implemented; future states must extend CHECK/index predicates in a new migration. Contract/limits: `Docs/API_B09_B10_XU_LY_SACH_CU.md`.

B11 circulation analytics: nullable business timestamps on loan_details record checkout and physical receipt (NORMAL/DAMAGED at counter, never LOST settlement). Migration 33 backfills only proven BORROWING history/normal-return evidence; missing dates remain null and coverage is explicit. Item and distinct ticket counts differ; bucket ticket counts are not additive. Half-open from/until, MONTH/QUARTER/YEAR zero-fill, Asia/Ho_Chi_Minh, read-only REPEATABLE_READ. Five ranking modes distinguish positive least/most, lifetime never-recorded, zero-period and current loans; physical zero-copy heads included. Overdue is current STUDENT data with B04 links. SQL aggregation/paging; timestamp indexes, selective checkout EXPLAIN tested. Legacy weekly now counts actual checkout items, trending excludes reservations, borrowedCurrent accepts BORROWED/BORROWING/OVERDUE. B13 corrects legacy fineCollected to actual lifetime PAYMENT receipts by paidAt, excluding deposit applications/topups and charges.

B12 holdings: BOOK plus unlinked DIGITAL document are logical distinct material heads; linked files inherit BOOK classification and add file count only. Exact PHYSICAL/DIGITAL/HYBRID type filters; state selects heads, counts retain all copies of selected heads. Disjoint copy buckets include held AVAILABLE, retired raw statuses and unknown other; retired status is not a disposal event. Multi-major groups overlap while overall/faculty counts deduplicate. Staff may assign majors with mandatory source, optimistic version, advisory/root locks, atomic before/after history and immediate same-body retry. No automatic book mapping from recommender. Migration 34 seeds academic_majors from B04 mappings once; later catalog changes do not auto-sync. Profiles/history intentionally survive upstream deletion, but current APIs require existing source; no orphan-audit browser. No digital retirement lifecycle/API was added. Contract/limits: `Docs/API_B11_B12_DASHBOARD.md`. Web analytics UI is implemented in B14–B15 (see navigation below).

B13 finance/import analytics: all library ledger users (not STUDENT-only). Four charge types, completed PAYMENT receipts by paidAt (once per receipt), eligible allocations, captured deposit applications, signed waivers/adjustments and separate DEPOSIT_TOPUP. Current ledger/wallet debt and discrepancy are separate from period flows; historicalDebtAvailable=false. Migration 35 adds transaction depositAppliedAmount/At captured by LateFineService/PenaltyCaseService, never overwritten by later payment method/paidAt. Legacy positive satisfied amounts remain unknown; only paidAmount=0 backfills deposit zero. Adds receipt completedAt written after all import lines/copies within transaction; old COMPLETED receipts stay undated. Imports use receipt-detail quantities, distinct period titles and first recorded import titles (undated prior evidence prevents first-import claims), not Book.createdAt/current copies. Coverage flags missing timestamps/evidence and allocation discrepancies. No execution contract exists: liquidation/destruction totals/buckets are null with WAITING_ADMIN_WORKFLOW, not zero. New reports/drill-down are DB-aggregated/paged, read-only REPEATABLE_READ; no production load acceptance/new indexes. Contract: `Docs/API_B13_TAI_CHINH_NHAP_XU_LY.md`. B14–B15 replaces the old overview with explicit period/current money labels and unavailable states.

Recommendation: popularity fallback, TF-IDF/cosine similarity, hybrid personalized/cold-start. Config defaults: content/department/popularity weights **0.50/0.35/0.15**, hybrid candidate pool 3,000, cache TTL 30 minutes. Cache keys incorporate user context. Do not label proxy evaluation metrics as real borrowing behavior.

RAG: curated `src/main/resources/rag/*.md` -> chunk/index/embedding -> pgvector retrieval + catalog -> Gemini generation or grounded local fallback -> answer/citations. Default dimension 768, top K 5. No key means local fallback; do not put Gemini credentials in clients. Configured model strings are repository defaults, not a claim about provider availability.

## 5. Web and mobile navigation

Web bootstrap: `src/main.ts` -> `app.config.ts` -> `app.routes.ts`. `provideHttpClient(withInterceptors([authInterceptor]))`. Auth uses `services/auth.service.ts`, `guards/auth.guard.ts`, `interceptors/auth.interceptor.ts`.
Local storage keys: `huit_lib_token`, `huit_lib_role`, `huit_lib_user`, `huit_lib_profile`. AuthService profile is a BehaviorSubject. Primary roles above; the client Role union also includes ACCOUNTANT/TREASURER, so verify specific finance permissions before simplifying roles.

| Screen/task | Web implementation under `web-app/src/app/` | Mobile under `mobile-app/lib/` |
|---|---|---|
| Login/profile/card | `pages/auth/`, `pages/portal/profile/`, `services/auth.service.ts`, `user.service.ts` | `screens/login_screen.dart`, `profile_screen.dart`, `change_password_screen.dart`; `services/auth_service.dart` |
| Catalog/reviews/recommendations | `pages/portal/{home,search,detail}/`, `services/book.service.ts`, `home.service.ts`; admin `pages/admin/books/` | `screens/{home,search,detail}_screen.dart`, `services/recommendation_service.dart`, `api_service.dart` |
| Reservations/loans/checkout | `pages/portal/{borrow-cart,history}/`, `pages/admin/circulation/`, `services/{borrow-cart,circulation}.service.ts` | `screens/borrowed_screen.dart`, `librarian_qr_screen.dart`; `services/loan_service.dart`, `librarian_qr_service.dart` |
| Counter return/incident receipt | `pages/admin/circulation/return-inspection*`, `services/return-inspection.models.ts`; `pages/admin/borrows/` links to return mode | B03 only hands off the contract; inspect existing librarian screens before implementing native UI |
| Saved books/proposals | `pages/portal/{saved-books,suggest}/`, `pages/admin/suggestions/`, `services/proposal.service.ts`; B08 lazy retained `suggestions/proposal-summary*` + `services/proposal-summary.service.ts`, uses ReportFileSaver/xlsxFilename; source IDs open individual drawer | `screens/saved_books_screen.dart`, `suggest_book_screen.dart`; wishlist/proposal services; no B08 native UI change |
| Debt/VNPAY/missing/damaged | `pages/portal/my-penalties/`, `pages/admin/{borrows,penalties}/`, `services/penalty.service.ts` | inspect `borrowed_screen.dart` and portal URL usage for supported flows; do not assume feature parity |
| Student overdue/debt report | `pages/admin/borrows/student-obligation-report*`, `services/student-obligation-report.{service,models}.ts`, `report-file-saver.ts`; Backend `modules/reporting/` | no B04/B05 Mobile change |
| Inventory/locations | `pages/admin/{imports,inventory-audit,shelf-map}/`, matching imports/inventory-audit/location services; imports has per-line catalog lookup, location cascade, persisted receipt path/barcodes and adjacent tests | no inventory import caller found in `lib/`; inspect detail/QR screens for mobile location/scan behavior |
| Old-copy handling proposals | lazy retained `inventory-audit/disposal-workspace*`, `services/disposal.service*`; entry from completed audit or old-copy records tab. Per-row condition/reason/action/HTTP(S) evidence URL; versioned detail/history. Creation pending body/key restored per-user in sessionStorage without auto-send; uncertain edits require reload | no B09–B10 native UI change |
| Digital/chatbot | `pages/portal/digital/`, `pages/admin/digital-assets/`, `components/chatbot/`, corresponding services | consult screens/API usage before promising a native equivalent |
| Notifications | `pages/admin/notifications/`, both layouts, `services/notification.service.ts` | `screens/notifications_screen.dart`, notification/push_notification services, `config/firebase_options.dart` |
| Dashboard at `/admin` | `pages/admin/dashboard/dashboard` shares applied half-open dates/period across CirculationDashboard, FinanceDashboard, StockDashboard; HoldingsDashboard uses current snapshot/filter/search. Four child `.ts/.html`, common `analytics-shared.ts` (cancel/load states/pager) and `analytics.css`. `services/dashboard-analytics.{service,models}.ts`; no bulk raw history fetch. Sources/receipt/allocations/file download are explicit drill-down. `Docs/HUONG_DAN_B14_B15_DASHBOARD.md` | no Dashboard analytics caller found in mobile services/test |
| Orientation | `pages/admin/orientation/`, corresponding service | no dedicated screen listed in current `lib/screens/` |

Routes: portal `/`, `/search`, `/book/:id`, `/history`, `/suggest`, `/profile`, `/borrow-cart`, `/saved-books`, `/my-penalties`, `/digital`, `/guide`, `/about`. Auth `/login`, `/forgot-password`. Staff subtree `/admin` uses AuthGuard for parent/children and ADMIN/LIBRARIAN roles; page directory names mostly match child URLs. Templates/styles are separate files. Global style: `web-app/src/styles.css`; modal styles also in `layouts/portal-layout/portal-modals.css`; shared dialogs use `dialog.service.ts`.

Mobile: Material 3 -> `SplashScreen` -> login/main layout. Screens call `lib/services/*`; `ApiService` attaches JWT, decodes responses and raises ApiException. Auth persistence uses shared preferences. Native directories generally contain platform glue; application logic lives in `lib/`.

## 6. Database, configuration and running locally

- Master: `backend/src/main/resources/db/changelog/db.changelog-master.yaml` uses `includeAll` on `changesets/`; ordering is filename based. There are two `10-...` files. Latest observed: **35-finance-import-analytics-evidence.yaml** (nullable captured deposit amount/time and receipt completion time); 33 owns circulation business timestamps/indexes, 34 academic classification/audit. Migration 32 owns disposal proposals, not execution events. **User-authorized local update 2026-10-30:** existing `library_postgres`/`huit_library` migrated 28–35, 81 changesets, zero pending/lock released. Backup in ignored `backups/librarian-pre-b14-b16-20261030.dump`. Only PostgreSQL was started; backend container stayed stopped, new jar/bundle were built, not deployed. Recheck runtime before assuming services/schema are unchanged. Add migrations instead of rewriting applied history.
- Landmarks: 10 state normalization, 13 idempotency, 14 maintenance, 15 penalty/finance policy, 16 digital, 17 recommendation taxonomy, 18 RAG/pgvector, 19 push, 20 interactions, 21 notification preferences, 22 no-show, 23 proposals, 24 reviews/search, 25 wallets, 26 orientation, 27 online payments, 28 nullable inspection receipt snapshots (old operation rows remain compatible).
- Hibernate `ddl-auto: validate`; use Liquibase for schema. User IDs UUID; books/tickets/details use Long; copy barcode is a String identifier. Read actual entity relationships before joining.
- `config/DatabaseSeeder.java` is a separate demo normalization/seeding step controlled by `DATABASE_SEEDING_ENABLED`; it is not the whole migration system. Large CSVs seed catalog data.
- `.env.example` names required config; `.env` is private and ignored. Compose passes env into backend. Starting Spring directly requires its own environment setup; do not assume Spring loads root `.env` automatically.
- API origins: Web `http://127.0.0.1:8080` in environment.ts. Mobile `API_BASE_URL` dart define; Android emulator default `http://10.0.2.2:8080`, others `http://localhost:8080`. `WEB_PORTAL_URL` defaults to `http://localhost:4200`.
- Ports: Web dev 4200, backend 8080, gateway 80, PostgreSQL 5432, Redis 6379, Mailpit UI 8025/SMTP 1025. Swagger `/swagger-ui/index.html`; health `/actuator/health`.
- Compose starts backend/infrastructure, **does not build/serve Angular**. Nginx `/` currently serves a placeholder static root. Nginx forwards `/api/` and selected legacy paths; its allowlist is not every controller path (e.g. orientation). Verify gateway routes if adding/exposing an endpoint.
- Digital Compose volume `digital_assets_data` -> `/app/data/digital-assets`; local default `./data/digital-assets` relative to backend working directory.
- Firebase server defaults disabled; needs `FIREBASE_*` server config and mobile project config/defines. VNPAY defaults disabled; needs merchant/secret/return/IPN configuration. Consult UAT docs for actual device/sandbox acceptance.

Commands below are reference commands, not evidence services are currently running. Execute in the specified directory; do not restart an existing user's session unnecessarily.

| Working directory | Command | Purpose |
|---|---|---|
| repository root | `docker compose up -d --build` | full configured local backend/infra |
| repository root | `docker compose up -d postgres redis mailpit` | dependencies for local backend development |
| `backend` | `.\mvnw.cmd spring-boot:run` | Windows backend; environment must include JWT_SECRET etc. |
| `backend` | `.\mvnw.cmd test` | unit + integration; Docker needed for Testcontainers |
| `backend` | `.\mvnw.cmd --batch-mode -DskipTests package` | package (not test execution) |
| `backend` | `.\mvnw.cmd -Dtest=LoanServiceTest test` | example focused test; choose relevant class |
| `web-app` | `npm ci`; then `npm start` | reproducible install; development server |
| `web-app` | `npm test -- --watch=false`; `npm run build` | Angular tests and production build |
| `web-app` | `npm audit --audit-level=high` | dependency gate |
| `mobile-app` | `flutter pub get`; `flutter run` | mobile dependencies/run; real devices need API_BASE_URL |
| `mobile-app` | `flutter analyze`; `flutter test`; `flutter build apk --debug` | mobile gates |
| repository root | `docker compose --env-file .env.example config --quiet` | manifest validation without exposing resolved secrets |
| repository root | `.\scripts\week12d-preflight.ps1` | repository handoff checks |

On Linux/CI use `bash ./mvnw` in place of `.\mvnw.cmd`. Bundled Maven 3.9.9 is available if wrapper/tool setup needs investigation. POM pins Docker API property `api.version=1.44` for Testcontainers compatibility; preserve unless deliberately updating it.

Additional scripts: `week11-smoke.ps1`, `week11-load-test.mjs`, `week12b-rag-evaluation.ps1`, `backup-postgres.ps1`, `verify-backup-restore.ps1`. Read parameters before executing; load/evaluation/backup/restore can affect services or data.

## 7. Verification evidence, documentation and unresolved acceptance

- CI file `.github/workflows/week6-quality.yml` runs preflight, backend tests/package, web audit/build/tests, Flutter analyze/tests/debug APK, Compose validation. Filename is historical.
- Backend tests: `src/test/java/com/huit/library/modules/<domain>/`; authorization contract tests under controller; integration tests in `integration/` (`Week6IntegrationTest`, circulation/RAG/online-payment migration tests). Focused test changelogs live in test resources. Web specs are adjacent to components/services. Mobile tests: `api_service_test.dart`, `recommendation_service_test.dart`, `widget_test.dart`.
- Latest integration gate rerun on **2026-10-30**: backend **295/295** tests PASS; Web **243/243** tests PASS and production build PASS. The Web build retains non-blocking CSS budget and `qrcode` CommonJS warnings. Mobile was not rerun for this merge. Earlier full UAT evidence remains in `TestChucNang/23_UAT_H7_KET_QUA_HOI_QUY_VA_BAN_GIAO.md`.
- That report still requires real camera/browser testing, VNPAY sandbox credentials/callback acceptance and Firebase foreground/background/terminated device tests. Their current external status is not established by this snapshot.
- Historical reports list large CSV warnings and a non-blocking `qrcode` CommonJS build warning. Check new outputs before claiming they persist.
- `Docs/backend_context.md`, `Docs/api_context.md`, `backend/README.md` contain older partial-status statements for now-implemented features (digital, penalty, review/wishlist, etc.). Treat them as historical pointers and verify code. This map does not rewrite all older docs.
- Business reference: `Docs/CHINH_SACH_MAT_HONG_VA_THU_PHAT_9C.md`; search design `Docs/ADR-001-CHIEN-LUOC-TIM-KIEM-SACH.md`; recommendation `Docs/TUAN_10*.md`; RAG `Docs/TUAN_12A*`/`TUAN_12B*`; mobile/Firebase `Docs/TUAN_12E_HOAN_THIEN_MOBILE_VA_FIREBASE.md`.
- Diagrams: `Docs/{system_architecture,huit_library_erd,circulation_state_machine,recommendation_pipeline,rag_pipeline,rag_query_sequence,local_deployment}.mmd`. Use on demand, not all at task start.
- UAT: `TestChucNang/02_CHECKLIST_TEST_TOAN_BO_HE_THONG.md`, numbered scenario docs 07–22 and H7 handoff. Report/slide work starts at `Word_PPTX/README.md` and its checklist/index; avoid reading duplicated source packs for a coding task.

## 8. Next-session workflow and maintenance

Librarian backlog: `Docs/KE_HOACH_BATCH_THU_THU.md` maps 7 requirements to B01–B16. **B01–B06/B08/B11–B12/B14–B15 completed; B09–B10 librarian scope and B13 finance/import done; B16 regression/handoff performed, full acceptance still depends on Admin B07 placement rules and disposal decision/execution/events.** New UI `/admin` replaces the legacy overview with four analytics tabs. Latest merged-source gates 2026-10-30: 295 Backend tests PASS, 243 Web tests PASS and Web production build PASS; no mobile rerun. Verification/checkpoint: `Docs/BAN_GIAO_BATCH_THU_THU.md`, acceptance `TestChucNang/24_NGHIEM_THU_THU_THU_B01_B16.md`. User authorized migrations; local DB 28–35 applied, not a backend/client deployment. Live read-only evidence found **269922 copies without current catalog attribution**, all material heads currently unassigned, missing historical circulation/deposit/import timestamps. Show global coverage; never repair source associations or invent classifications/dates just to reconcile counts. Overview filters/group/list counts only attributed materials. B04 deep link: `/admin/borrows?view=report&q=<mssv/name>&reportMode=OVERDUE`, initial inputs on StudentObligationReport; period does not filter current report. Preserve all uncommitted work and do only the next requested work.

1. Read this map once, then `git status --short` and `git rev-parse --short HEAD`.
2. If HEAD changed, use a bounded `git diff --name-status 34d942b..HEAD -- backend/src web-app/src mobile-app/lib scripts docker-compose.yml` (or the updated baseline). Uncommitted changes need their own `git diff --name-only`; Git cannot reveal ignored local config changes.
3. Select the domain/screen above. Open its implementation, direct caller/DTO and relevant tests. Use scoped `rg -n` or `rg --files <directory>` for missing links. Broaden only as needed; do not dump all source, lockfiles, CSV or build output.
4. Make and verify the task. Report checks actually run and limitations actually observed.
5. If a documented fact changed, edit its section and update snapshot/baseline to the revision actually inspected. Do not set an uncreated commit as the baseline. Keep historical test evidence explicitly dated. Avoid accumulating per-task logs here.

This map saves repeated discovery; it cannot safely remove the need to read code being changed. It does not grant permission to publish, send messages or change external configuration.
