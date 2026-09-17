import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-circulation',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './circulation.html',
  styleUrls: ['./circulation.css']
})
export class Circulation {
  activeTab: 'borrow' | 'return' = 'borrow';
  
  // Fake state
  scannedStudent: any = null;
  scannedBooks: any[] = [];
  transactionSuccess = false;

  // Mock Data
  mockStudent = {
    id: '20012111',
    name: 'Nguyễn Văn Anh',
    class: '10DHPM1',
    major: 'Công nghệ phần mềm',
    status: 'active', // active, locked, warning
    borrowLimit: 5,
    currentlyBorrowed: 2,
    avatar: 'N'
  };

  mockBook = {
    isbn: '978-604-2-27764-1',
    title: 'Cấu trúc Dữ liệu & Giải thuật',
    author: 'Nguyễn Đức Nghĩa',
    location: 'Tầng 3 - Kệ IT-204',
    condition: 'Tốt'
  };

  switchTab(tab: 'borrow' | 'return') {
    this.activeTab = tab;
    this.resetForm();
  }

  scanStudentId(event: any) {
    // Giả lập quét hoặc gõ Enter
    if (event.key === 'Enter' || event.type === 'click') {
      this.scannedStudent = this.mockStudent;
      this.transactionSuccess = false;
    }
  }

  scanBookBarcode(event: any) {
    if (event.key === 'Enter' || event.type === 'click') {
      // Add fake book to cart
      this.scannedBooks.push({...this.mockBook, uid: Math.random().toString(36).substring(7)});
      event.target.value = ''; // clear input
      this.transactionSuccess = false;
    }
  }

  removeBook(uid: string) {
    this.scannedBooks = this.scannedBooks.filter(b => b.uid !== uid);
  }

  completeTransaction() {
    if (this.scannedBooks.length > 0 && (this.activeTab === 'return' || this.scannedStudent)) {
      this.transactionSuccess = true;
      this.scannedBooks = [];
      setTimeout(() => {
        this.resetForm();
      }, 3000); // Tự động reset sau 3s
    }
  }

  resetForm() {
    this.scannedStudent = null;
    this.scannedBooks = [];
    this.transactionSuccess = false;
  }
}
