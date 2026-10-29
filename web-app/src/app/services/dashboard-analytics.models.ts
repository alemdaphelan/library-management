export type Period = 'MONTH'|'QUARTER'|'YEAR';
export interface AnalyticsRange { from: string; until: string; period: Period; }
export interface Snapshot { snapshotAt: string; timezone: string; }
export interface Page { page: number; size: number; }
export interface ItemPage<T> extends Snapshot, Page { total: number; items: T[]; }
export interface CirculationCounts { checkoutItems: number; physicalReturnItems: number; checkoutTickets: number; physicalReturnTickets: number; }
export interface CirculationCoverage { checkoutRecordsWithoutTime: number; normalReturnsWithoutTime: number; ambiguousIncidentReturns: number; periodCheckoutsWithoutCurrentCatalog: number; complete: boolean; }
export interface CirculationReport extends Snapshot { range: AnalyticsRange; totals: CirculationCounts; buckets: {start: string; until: string; counts: CirculationCounts}[]; coverage: CirculationCoverage; }
export type Ranking = 'MOST_BORROWED'|'LEAST_BORROWED'|'NEVER_RECORDED'|'NO_RECORDED_IN_PERIOD'|'CURRENTLY_BORROWED';
export interface RankedBook { bookId: number; title: string; author: string; recordedInPeriod: number; recordedLifetime: number; currentlyBorrowed: number; copyCount: number; position: number; }
export interface RankingPage extends Snapshot, Page { totalBooks: number; books: RankedBook[]; coverage: CirculationCoverage; }
export interface OverdueItem { userId: string; mssv: string; fullName: string; ticketId: number; loanDetailId: number; barcode: string; title: string; dueAt: string; }
export interface OverduePage extends Snapshot, Page { totalItems: number; items: OverdueItem[]; }
export interface Faculty { facultyId: string; facultyName: string; }
export interface Major extends Faculty { majorId: string; majorName: string; majorCode: string|null; }
export interface AcademicCatalog { faculties: Faculty[]; majors: Major[]; }
export type MaterialType = 'ALL'|'PHYSICAL'|'DIGITAL'|'HYBRID';
export type MaterialState = 'ALL'|'AVAILABLE'|'BORROWING'|'RESERVED'|'LOST'|'DAMAGED'|'MAINTENANCE'|'RETIRED'|'NO_COPIES'|'NO_DIGITAL_FILE';
export interface HoldingFilter { facultyId: string; majorId: string; type: MaterialType; state: MaterialState; q: string; }
export interface HoldingsCounts { materials: number; catalogTitles: number; physicalTitles: number; digitalTitles: number; standaloneDigitalTitles: number; totalCopies: number; availableCopies: number; borrowedCopies: number; reservedCopies: number; lostCopies: number; damagedCopies: number; maintenanceCopies: number; retiredCopies: number; otherCopies: number; digitalFiles: number; unmappedMaterials: number; }
export interface HoldingGroup extends Faculty { majorId: string|null; majorName: string|null; counts: HoldingsCounts; }
export interface HoldingsReport extends Snapshot { totals: HoldingsCounts; faculties: HoldingGroup[]; majors: HoldingGroup[]; coverage?: {totalPhysicalCopies:number;copiesWithoutCurrentCatalog:number;linkedDigitalFilesWithoutCurrentCatalog:number}; }
export interface Material { kind: 'BOOK'|'DIGITAL'; id: number; title: string; author: string; type: MaterialType; mappingStatus: string; counts: HoldingsCounts; majors: Major[]; }
export interface MaterialPage extends Snapshot, Page { totalMaterials: number; materials: Material[]; }
export interface MaterialCopy { barcode: string; status: string; conditionNote: string|null; location: string; }
export interface MaterialAsset { id: number; name: string; format: string; size: number; accessLevel: string; }
export interface ClassificationState { version: number; sourceReference: string|null; majors: Major[]; updatedByName: string|null; updatedAt: string|null; }
export interface Classification { current: ClassificationState; history: {eventId: string; actorName: string; at: string; before: ClassificationState; after: ClassificationState}[]; }
export interface Money { chargesAccrued: number; moneyReceived: number; cashReceived: number; bankReceived: number; vnpayReceived: number; otherReceived: number; allocatedPayments: number; depositApplied: number; waived: number; adjusted: number; depositTopups: number; }
export interface FinanceReport extends Snapshot { totals: Money; buckets: {start: string; until: string; amounts: Money}[]; feeTypes: {feeType: string; chargesAccrued: number}[]; currentDebt: {ledgerOutstanding: number; walletDebt: number; walletMinusLedger: number; satisfiedIncludingReductions: number}; coverage: {chargesWithoutIssueTime: number; chargesWithoutDepositEvidence: number; depositApplicationsWithoutTime: number; paymentsWithoutPaidTime: number; reductionsWithoutIssueTime: number; paymentAllocationMismatches: number; receiptMinusEligibleAllocations: number; invalidAllocationRows: number}; }
export type EventKind = 'CHARGE'|'PAYMENT'|'DEPOSIT_APPLICATION'|'WAIVER'|'ADJUSTMENT'|'DEPOSIT_TOPUP';
export interface FinanceEvent { kind: EventKind; transactionId: number; at: string; feeType: string; amount: number; method: string|null; receiptNumber: string|null; parentTransactionId: number|null; }
export interface Allocation { allocationId: number; debtTransactionId: number; feeType: string; amount: number; eligible: boolean; }
export interface ImportCounts { completedReceipts: number; importedTitles: number; firstRecordedImportTitles: number; importedCopies: number; }
export interface StockReport extends Snapshot { totals: ImportCounts; buckets: {start: string; until: string; counts: ImportCounts; liquidatedCopies: number|null; destroyedCopies: number|null}[]; coverage: {completedReceiptsWithoutTime: number; invalidDetailRows: number; titlesWithUndatedImport: number}; disposals: {status: string; reason: string; liquidatedCopies: number|null; destroyedCopies: number|null}; }
export interface AnalyticsReceipt { receiptId: number; receiptCode: string; completedAt: string; importedTitles: number; importedCopies: number; }
