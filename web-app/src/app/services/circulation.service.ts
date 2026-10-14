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

@Injectable({
  providedIn: 'root'
})
export class CirculationService {
  private apiUrl = `${environment.apiUrl}/loans`;
  private bookCopyApiUrl = `${environment.apiUrl}/book-copies`;

  constructor(private http: HttpClient) { }

  /**
   * Độc giả gửi yêu cầu mượn hoặc đặt giữ trước sách trực tuyến qua OPAC
   */
  reserveBook(payload: BorrowRequest): Observable<BorrowResponse> {
    return this.http.post<BorrowResponse>(`${this.apiUrl}/reserve`, payload);
  }

  /**
   * Lấy danh sách phiếu mượn và đặt giữ của độc giả hiện tại
   */
  getMyLoans(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/my-loans`);
  }

  /**
   * Hủy yêu cầu đặt giữ trước khi chưa nhận sách
   */
  cancelReservation(ticketId: number): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/cancel/${ticketId}`, {});
  }

  /**
   * Bạn đọc gia hạn thời gian mượn sách (thêm 7 ngày)
   */
  renewLoan(ticketId: number): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/renew/${ticketId}`, {});
  }

  /**
   * Thủ thư thực hiện mượn sách tại quầy (dành cho Admin / Circulation desk)
   */
  checkout(studentId: string, bookBarcodes: string[]): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/checkout`, { studentId, bookBarcodes });
  }

  /**
   * Thủ thư thực hiện trả sách tại quầy
   */
  returnBooks(bookBarcodes: string[]): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/return`, { bookBarcodes });
  }
  
  /**
   * Lấy thông tin bản sao vật lý theo mã vạch
   */
  getBookCopyByBarcode(barcode: string): Observable<any> {
    return this.http.get<any>(`${this.bookCopyApiUrl}/${barcode}`);
  }
}
