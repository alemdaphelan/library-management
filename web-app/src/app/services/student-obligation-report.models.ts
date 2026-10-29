export type ObligationMode = 'OVERDUE' | 'DEBT' | 'EITHER';
export interface ObligationFilters { mode: ObligationMode; facultyId: string; q: string; }
export interface AcademicFaculty { facultyId: string; facultyName: string; }
export interface ClassificationCoverage {
  totalStudents: number; classifiedStudents: number; unclassifiedStudents: number; complete: boolean;
  unmappedDepartments: { rawDepartment: string | null; studentCount: number }[];
}
export interface StudentObligation {
  userId: string; mssv: string | null; fullName: string | null; rawDepartment: string | null;
  facultyId: string; facultyName: string; majorName: string | null; mappingStatus: string;
  overdueItemCount: number; earliestDueDate: string | null; overdueDays: number;
  chargedAmount: number; paidOrDepositAmount: number; waivedAmount: number; adjustedAmount: number;
  outstandingAmount: number; walletDebt: number | null; walletDifference: number | null; reconciliationDifference: number;
}
export interface ObligationOverdueItem {
  userId: string; ticketId: number; loanDetailId: number; barcode: string; bookTitle: string | null;
  ticketType: string; dueDate: string; overdueDays: number;
}
export interface ObligationLedgerEntry {
  userId: string; transactionId: number; ticketId: number | null; loanDetailId: number | null;
  penaltyCaseId: number | null; type: string; reason: string | null; status: string;
  amount: number; satisfiedAmount: number | null; outstandingAmount: number;
  paymentMethod: string | null; receiptNumber: string | null; createdAt: string | null;
}
export interface StudentObligationReport {
  snapshotAt: string; timezone: string; currency: string;
  filter: { mode: ObligationMode; facultyId: string | null; query: string };
  page: number; size: number; totalStudents: number; students: StudentObligation[];
  overdueItems: ObligationOverdueItem[]; ledgerEntries: ObligationLedgerEntry[];
}
