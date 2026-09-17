import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-imports',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './imports.html',
  styleUrls: ['./imports.css']
})
export class Imports {
  isDrawerOpen = false;

  // Mock data for Quick Stats
  stats = {
    totalImports: 124,
    booksReceived: 1250,
    totalSpent: '45.000.000đ',
    pending: 3
  };

  // Mock data for Import Receipts
  importReceipts = [
    { id: 'PNK-20231001', date: '01/10/2023', supplier: 'NXB Giáo Dục', qty: 150, total: '15.000.000đ', status: 'Hoàn thành' },
    { id: 'PNK-20231015', date: '15/10/2023', supplier: 'NXB Trẻ', qty: 50, total: '3.500.000đ', status: 'Hoàn thành' },
    { id: 'PNK-20231102', date: '02/11/2023', supplier: 'NXB Kim Đồng', qty: 200, total: '12.000.000đ', status: 'Đang xử lý' },
    { id: 'PNK-20231110', date: '10/11/2023', supplier: 'Công ty Fahasa', qty: 85, total: '7.200.000đ', status: 'Hoàn thành' },
    { id: 'PNK-20231120', date: '20/11/2023', supplier: 'NXB Bách Khoa', qty: 120, total: '18.500.000đ', status: 'Đang xử lý' }
  ];

  // Mock data for Suppliers
  suppliers = ['NXB Giáo Dục', 'NXB Trẻ', 'NXB Kim Đồng', 'Công ty Fahasa', 'NXB Bách Khoa', 'NXB Thông tin Truyền thông'];

  // Form mock state
  newImportLines = [
    { isbn: '', title: '', qty: 1, price: 0 }
  ];

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
    alert('Đã lưu phiếu nhập thành công!');
    this.closeDrawer();
  }
}
