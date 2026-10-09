import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { UserService } from '../../../services/user.service';
import { CirculationService } from '../../../services/circulation.service';

@Component({
  selector: 'app-circulation',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './circulation.html',
  styleUrls: ['./circulation.css']
})
export class Circulation {
  activeTab: 'borrow' | 'return' = 'borrow';
  
  // Real state
  scannedStudent: any = null;
  scannedBooks: any[] = [];
  transactionSuccess = false;

  constructor(
    private userService: UserService,
    private circulationService: CirculationService
  ) {}

  switchTab(tab: 'borrow' | 'return') {
    this.activeTab = tab;
    this.resetForm();
  }

  scanStudentId(event: any) {
    if (event.key === 'Enter' || event.type === 'click') {
      const input = event.target.value?.trim();
      if (input) {
        this.userService.getStudentById(input).subscribe({
          next: (student: any) => {
            this.scannedStudent = student;
            this.transactionSuccess = false;
          },
          error: (err: any) => {
            console.error('Student not found', err);
            alert('Không tìm thấy sinh viên!');
          }
        });
      }
    }
  }

  scanBookBarcode(event: any) {
    if (event.key === 'Enter' || event.type === 'click') {
      const barcode = event.target.value?.trim();
      if (barcode) {
        // Since backend might not have getBookCopyByBarcode implemented properly yet, we'll try to fetch it
        // and if it fails, we push a basic object to allow the transaction to proceed.
        this.circulationService.getBookCopyByBarcode(barcode).subscribe({
          next: (book: any) => {
            if (!this.scannedBooks.find(b => b.barcode === barcode)) {
              this.scannedBooks.push({...book, barcode});
            }
            event.target.value = '';
            this.transactionSuccess = false;
          },
          error: (err: any) => {
            console.warn('Book copy fetch failed, using fallback for barcode:', barcode);
            if (!this.scannedBooks.find(b => b.barcode === barcode)) {
              this.scannedBooks.push({
                barcode: barcode,
                title: 'Unknown Title (Barcode: ' + barcode + ')',
                uid: Math.random().toString(36).substring(7)
              });
            }
            event.target.value = '';
            this.transactionSuccess = false;
          }
        });
      }
    }
  }

  removeBook(uid: string) {
    this.scannedBooks = this.scannedBooks.filter(b => b.uid !== uid && b.barcode !== uid);
  }

  completeTransaction() {
    if (this.scannedBooks.length > 0 && (this.activeTab === 'return' || this.scannedStudent)) {
      const barcodes = this.scannedBooks.map(b => b.barcode);
      
      if (this.activeTab === 'borrow') {
        this.circulationService.checkout(this.scannedStudent.mssv, barcodes).subscribe({
          next: () => {
            this.transactionSuccess = true;
            this.scannedBooks = [];
            setTimeout(() => this.resetForm(), 3000);
          },
          error: (err: any) => {
            console.error('Checkout failed', err);
            alert('Lỗi khi mượn sách!');
          }
        });
      } else {
        this.circulationService.returnBooks(barcodes).subscribe({
          next: () => {
            this.transactionSuccess = true;
            this.scannedBooks = [];
            setTimeout(() => this.resetForm(), 3000);
          },
          error: (err: any) => {
            console.error('Return failed', err);
            alert('Lỗi khi trả sách!');
          }
        });
      }
    }
  }

  resetForm() {
    this.scannedStudent = null;
    this.scannedBooks = [];
    this.transactionSuccess = false;
  }
}
