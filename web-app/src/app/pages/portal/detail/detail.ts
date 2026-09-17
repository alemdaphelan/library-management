import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './detail.html',
  styleUrls: ['./detail.css']
})
export class Detail implements OnInit {
  book = {
    title: 'Học Máy & Trí Tuệ Nhân Tạo Hiện Đại: Lý Thuyết và Thực Hành Ứng Dụng',
    subtitle: 'Modern Machine Learning and Artificial Intelligence: Theoretical Foundations and Applied Practice',
    author: 'PGS.TS. Trần Văn Hùng',
    coAuthor: 'TS. Lê Hoàng Nam',
    category: 'Trí tuệ Nhân tạo & Học máy',
    publisher: 'NXB Đại Học Quốc Gia (2024)',
    isbn: '978-604-01-2839-4',
    pages: 450,
    cover: '/assets/images/cover_1.jpg', // Using our existing cover
    description: 'Giáo trình cung cấp nền tảng lý thuyết hệ thống hóa từ Supervised Learning, Deep Neural Networks, Attention Mechanisms đến Generative AI và Large Language Models (LLMs). Cuốn sách thiết kế đặc thù cho chương trình đào tạo kỹ sư Công nghệ thông tin và Khoa học dữ liệu HUIT, đi kèm 8 dự án mã nguồn thực nghiệm với PyTorch, TensorFlow...',
    callNo: '006.31 TR-H 2024',
    shelf: 'Kệ IT-204',
    availableCopies: 9
  };

  constructor() {}

  ngOnInit() {
    window.scrollTo(0, 0);
  }
}
