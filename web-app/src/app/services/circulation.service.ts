import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';

export interface BorrowRequest {
  bookId: number;
  ticketType?: 'BORROW' | 'ONSITE';
  pickupDate?: string;
  note?: string;
}

export interface BorrowResponse {
  success: boolean;
  message: string;
  ticketId?: number;
  ticketType?: string;
  status: string;
  barcode?: string;
  pickupDate?: string;
  dueDate?: string;
  bookTitle?: string;
}

export interface BatchBorrowRequest {
  bookIds: number[];
  ticketType?: 'BORROW' | 'ONSITE';
  pickupDate?: string;
  note?: string;
}

export interface BatchBorrowResponse {
  success: boolean;
  message: string;
  ticketId: number;
  ticketType: string;
  status: string;
  pickupDeadline: string;
  dueDate: string;
  bookIds: number[];
  bookTitles: string[];
  barcodes: string[];
}

export type LoanTicketStatus =
  'PENDING' | 'RESERVED' | 'BORROWING' | 'OVERDUE' | 'RETURNED' | 'CANCELLED' | 'REJECTED';

export interface LoanStateResponse {
  ticketId: number;
  previousStatus: LoanTicketStatus;
  status: LoanTicketStatus;
  message: string;
  changedAt: string;
}

export interface LoanItem {
  ticketId: number;
  detailId: number;
  bookId?: number;
  bookTitle: string;
  bookCover?: string;
  author?: string;
  barcode: string;
  ticketType: string;
  status: LoanTicketStatus;
  createdAt?: string;
  pickupDeadline?: string;
  dueDate?: string;
  returnDate?: string;
  daysRemaining?: number;
  renewalCount: number;
  lateFineAccrued: number;
}

export interface LoanTicketSummary {
  ticketId: number;
  userId?: string;
  mssv?: string;
  readerName?: string;
  department?: string;
  cardStatus?: string;
  ticketType: string;
  status: LoanTicketStatus;
  createdAt?: string;
  nearestDueDate?: string;
  itemCount: number;
  overdueItemCount: number;
  totalLateFine: number;
}

export interface LoanTicketDetail {
  ticketId: number;
  userId?: string;
  mssv?: string;
  readerName?: string;
  ticketType: string;
  status: LoanTicketStatus;
  decisionNote?: string;
  createdAt?: string;
  items: LoanItem[];
}

export interface BookCopy {
  barcode: string;
  bookId: number;
  conditionNote?: string;
  status: string;
  shelfId?: number;
  shelfName?: string;
  bookshelfId?: number;
  bookshelfName?: string;
  floorId?: number;
  floorName?: string;
  location?: string;
}

export interface CirculationOperationResponse {
  operationId: string;
  operationType: 'CHECKOUT' | 'RETURN';
  ticketIds: number[];
  barcodes: string[];
  status: string;
  message: string;
  processedAt: string;
  replayed: boolean;
}

export interface QrPayloadResponse {
  payload: string;
  expiresAt: string;
}

export interface QrResolveResponse {
  type: 'LOAN_TICKET' | 'USER_CARD';
  subjectId: string;
  userId: string;
  readerName?: string;
  mssv?: string;
  cardStatus?: string;
  ticketId?: number;
  ticketStatus?: LoanTicketStatus;
  allowedActions: string[];
  items: LoanItem[];
  activeLoanCount?: number;
  totalDebt?: number;
  expiresAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class CirculationService {
  private apiUrl = `${environment.apiUrl}/loans`;
  private bookCopyApiUrl = `${environment.apiUrl}/book-copies`;
  private qrApiUrl = `${environment.apiUrl}/api/v1/qr`;

  constructor(private http: HttpClient) { }

  /**
   * Độc giả gửi yêu cầu mượn hoặc đặt giữ trước sách trực tuyến qua OPAC
   */
  reserveBook(payload: BorrowRequest): Observable<BorrowResponse> {
    return this.http.post<BorrowResponse>(`${this.apiUrl}/reserve`, payload);
  }

  reserveBooks(payload: BatchBorrowRequest): Observable<BatchBorrowResponse> {
    return this.http.post<BatchBorrowResponse>(`${this.apiUrl}/reserve-batch`, payload);
  }

  /**
   * Lấy danh sách phiếu mượn và đặt giữ của độc giả hiện tại
   */
  getMyLoans(): Observable<LoanItem[]> {
    return this.http.get<LoanItem[]>(`${this.apiUrl}/my-loans`);
  }

  /** Danh sách phiếu thật dành cho Admin/Librarian. */
  getStaffLoans(): Observable<LoanTicketSummary[]> {
    return this.http.get<LoanTicketSummary[]>(`${this.apiUrl}/staff`);
  }

  /**
   * Thủ thư/Admin lấy chi tiết một phiếu trước khi duyệt hoặc từ chối.
   */
  getLoan(ticketId: number): Observable<LoanTicketDetail> {
    return this.http.get<LoanTicketDetail>(`${this.apiUrl}/${ticketId}`);
  }

  getActiveLoanItemByBarcode(barcode: string): Observable<LoanItem> {
    return this.http.get<LoanItem>(`${this.apiUrl}/active-item/${encodeURIComponent(barcode)}`);
  }

  /**
   * Hủy yêu cầu đặt giữ trước khi chưa nhận sách
   */
  cancelReservation(ticketId: number): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/cancel/${ticketId}`, {});
  }

  /**
   * Bạn đọc gia hạn thời gian mượn sách (thêm 10 ngày)
   */
  renewLoan(ticketId: number, detailId?: number): Observable<any> {
    const url = detailId
      ? `${this.apiUrl}/renew/${ticketId}/items/${detailId}`
      : `${this.apiUrl}/renew/${ticketId}`;
    return this.http.post<any>(url, {});
  }

  approveReservation(ticketId: number): Observable<LoanStateResponse> {
    return this.http.post<LoanStateResponse>(`${this.apiUrl}/${ticketId}/approve`, {});
  }

  rejectReservation(ticketId: number, reason: string): Observable<LoanStateResponse> {
    return this.http.post<LoanStateResponse>(`${this.apiUrl}/${ticketId}/reject`, { reason });
  }

  /**
   * Thủ thư thực hiện mượn sách tại quầy (dành cho Admin / Circulation desk)
   */
  checkout(
    ticketId: number,
    barcodes: string[],
    idempotencyKey: string = crypto.randomUUID()
  ): Observable<CirculationOperationResponse> {
    return this.http.post<CirculationOperationResponse>(`${this.apiUrl}/checkout`, { idempotencyKey, ticketId, barcodes });
  }

  manualCheckout(
    mssv: string,
    ticketType: 'BORROW' | 'ONSITE',
    barcodes: string[],
    note?: string,
    idempotencyKey: string = crypto.randomUUID(),
  ): Observable<CirculationOperationResponse> {
    return this.http.post<CirculationOperationResponse>(`${this.apiUrl}/manual-checkout`, {
      idempotencyKey,
      mssv: mssv.trim(),
      ticketType,
      barcodes,
      note: note?.trim() || undefined,
    });
  }

  /**
   * Thủ thư thực hiện trả sách tại quầy
   */
  returnBooks(
    barcodes: string[],
    idempotencyKey: string = crypto.randomUUID(),
    returnConditionNote?: string
  ): Observable<CirculationOperationResponse> {
    return this.http.post<CirculationOperationResponse>(`${this.apiUrl}/return`, {
      idempotencyKey,
      barcodes,
      returnConditionNote: returnConditionNote?.trim() || undefined,
    });
  }
  
  /**
   * Lấy thông tin bản sao vật lý theo mã vạch
   */
  getBookCopyByBarcode(barcode: string): Observable<BookCopy> {
    return this.http.get<BookCopy>(`${this.bookCopyApiUrl}/${encodeURIComponent(barcode)}`);
  }

  /** Tạo QR phiếu mượn có chữ ký và thời hạn từ Backend. */
  getTicketQrPayload(ticketId: number): Observable<QrPayloadResponse> {
    return this.http.get<QrPayloadResponse>(`${this.qrApiUrl}/tickets/${ticketId}`);
  }

  resolveQrPayload(payload: string): Observable<QrResolveResponse> {
    return this.http.post<QrResolveResponse>(`${this.qrApiUrl}/resolve`, { payload });
  }
}
