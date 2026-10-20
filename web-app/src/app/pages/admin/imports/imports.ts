import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ImportsService } from '../../../services/imports.service';

@Component({
  selector: 'app-imports',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './imports.html',
  styleUrls: ['./imports.css']
})
export class Imports implements OnInit {
  isDrawerOpen = false;
  stats: any = {};
  importReceipts: any[] = [];
  suppliers: any[] = [];
  selectedSupplierId?: number;

  newImportLines = [
    { isbn: '', title: '', qty: 1, price: 0 }
  ];

  constructor(private importsService: ImportsService) {}

  ngOnInit() {
    this.fetchData();
  }

  fetchData() {
    this.importsService.getStats().subscribe({
      next: (data) => this.stats = data,
      error: (err) => console.error('Error fetching stats', err)
    });

    this.importsService.getImportReceipts().subscribe({
      next: (data) => this.importReceipts = data,
      error: (err) => console.error('Error fetching receipts', err)
    });

    this.importsService.getSuppliers().subscribe({
      next: (data) => this.suppliers = data,
      error: (err) => console.error('Error fetching suppliers', err)
    });
  }

  openDrawer() {
    this.isDrawerOpen = true;
    document.body.style.overflow = 'hidden';
  }

  closeDrawer() {
    this.isDrawerOpen = false;
    document.body.style.overflow = '';
  }

  addLine() {
    this.newImportLines.push({ isbn: '', title: '', qty: 1, price: 0 });
  }

  removeLine(index: number) {
    if (this.newImportLines.length > 1) {
      this.newImportLines.splice(index, 1);
    }
  }

  saveImport() {
    if (!this.selectedSupplierId || this.newImportLines.some(line => !line.isbn.trim() || line.qty < 1 || line.price < 0)) {
      alert('Vui lòng chọn nhà cung cấp và nhập ISBN, số lượng, đơn giá hợp lệ.');
      return;
    }
    const importData = {
      supplierId: this.selectedSupplierId,
      lines: this.newImportLines.map(line => ({
        isbn: line.isbn.trim(),
        quantity: Number(line.qty),
        unitPrice: Number(line.price)
      }))
    };
    this.importsService.saveImport(importData).subscribe({
      next: () => {
        alert('Đã lưu phiếu nhập thành công!');
        this.closeDrawer();
        this.fetchData();
        this.selectedSupplierId = undefined;
        this.newImportLines = [{ isbn: '', title: '', qty: 1, price: 0 }];
      },
      error: (err) => {
        console.error('Error saving import', err);
        alert('Lỗi khi lưu phiếu nhập!');
      }
    });
  }

  get totalQuantity(): number {
    return this.newImportLines.reduce((sum, line) => sum + (Number(line.qty) || 0), 0);
  }

  get totalAmount(): number {
    return this.newImportLines.reduce((sum, line) => sum + (Number(line.qty) || 0) * (Number(line.price) || 0), 0);
  }
}
