import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-guide',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './guide.html',
  styleUrls: ['./guide.css']
})
export class Guide {
  faqs = [
    { q: 'Làm thế nào để mượn sách mang về?', a: 'Bạn cần sử dụng thẻ sinh viên hoặc ứng dụng HUIT SSO trên điện thoại. Chọn sách tại các kho mở, mang ra quầy thủ thư hoặc máy mượn trả tự động để làm thủ tục.' },
    { q: 'Thời hạn mượn sách là bao lâu?', a: 'Đối với sinh viên đại trà: 14 ngày. Đối với sinh viên tài năng/chất lượng cao: 21 ngày. Bạn có thể gia hạn 1 lần trên web portal nếu sách chưa bị người khác đặt trước.' },
    { q: 'Mức phạt khi trả sách trễ hạn?', a: 'Quy định hiện hành của Thư viện HUIT: 2,000 VNĐ / 1 cuốn / 1 ngày trễ. Nếu làm mất sách, bạn phải đền bù cuốn mới tương đương hoặc đền tiền gấp 3 lần giá trị sách.' },
    { q: 'Sinh viên có được mượn tài liệu số (E-book) không?', a: 'Hoàn toàn được. Các tài liệu số có thể xem trực tiếp trên Web Portal hoặc tải PDF (có mã hóa DRM) để đọc offline trong 7 ngày.' }
  ];
}
