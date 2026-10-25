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
    { q: 'Làm thế nào để mượn sách mang về?', a: 'Đăng nhập, chọn tối đa 3 đầu sách vào giỏ mượn, tạo một phiếu và đến quầy để thủ thư quét mã QR hoặc mã vạch, kiểm tra tình trạng rồi giao sách.' },
    { q: 'Thời hạn mượn sách là bao lâu?', a: 'Mượn về trong 10 ngày và được gia hạn tối đa 1 lần thêm 10 ngày nếu sách chưa quá hạn, tài khoản không có nợ, thẻ hoạt động và đầu sách không có người chờ.' },
    { q: 'Mức phạt khi trả sách trễ hạn?', a: 'Tiền phạt tính riêng từng tài liệu từ ngày kế tiếp ngày phải trả: sách nhãn trắng 1.000 VNĐ/tài liệu/ngày, sách nhãn cam đọc trong ngày 5.000 VNĐ/tài liệu/ngày.' },
    { q: 'Đặt giữ sách được giữ bao lâu?', a: 'Yêu cầu trực tuyến đối với tài liệu có sẵn chỉ được giữ đến giờ đóng cửa trong ngày tạo phiếu. Tài liệu từ hàng đợi được giữ 48 giờ kể từ thông báo và kéo dài đến lần mở cửa tiếp theo nếu hạn rơi vào lúc thư viện đóng cửa.' }
  ];
}
