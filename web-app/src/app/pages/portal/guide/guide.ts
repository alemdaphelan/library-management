import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LibraryPolicy, LibraryPolicyService } from '../../../services/library-policy.service';

@Component({
  selector: 'app-guide',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './guide.html',
  styleUrls: ['./guide.css']
})
export class Guide implements OnInit {
  policy: LibraryPolicy | null = null;
  policyError = '';

  constructor(private policyService: LibraryPolicyService) {}

  ngOnInit(): void {
    this.policyService.getPublicPolicy().subscribe({
      next: policy => this.policy = policy,
      error: () => this.policyError = 'Không thể tải quy định thư viện. Vui lòng thử lại sau.',
    });
  }

  get faqs() {
    if (!this.policy) return [];
    return [
      { q: 'Làm thế nào để mượn sách mang về?', a: `Đăng nhập, chọn tối đa ${this.policy.maximumOpenItems} đầu sách vào giỏ mượn, tạo một phiếu và đến quầy để thủ thư quét mã QR hoặc mã vạch, kiểm tra tình trạng rồi giao sách.` },
      { q: 'Thời hạn mượn sách là bao lâu?', a: `Mượn về trong ${this.policy.homeLoanDays} ngày và được gia hạn tối đa ${this.policy.maximumRenewalCount} lần, mỗi lần thêm ${this.policy.renewalDays} ngày nếu sách chưa quá hạn, tài khoản không có nợ, thẻ hoạt động và đầu sách không có người chờ.` },
      { q: 'Mức phạt khi trả sách trễ hạn?', a: `Tiền phạt tính riêng từng tài liệu từ ngày kế tiếp ngày phải trả: sách mượn về ${this.money(this.policy.homeLateFinePerDay)} VNĐ/tài liệu/ngày, tài liệu đọc tại chỗ ${this.money(this.policy.onsiteLateFinePerDay)} VNĐ/tài liệu/ngày.` },
      { q: 'Đặt giữ sách được giữ bao lâu?', a: `Yêu cầu trực tuyến đối với tài liệu có sẵn chỉ được giữ đến giờ đóng cửa trong ngày tạo phiếu. Tài liệu từ hàng đợi được giữ ${this.policy.waitlistHoldHours} giờ kể từ thông báo và kéo dài đến lần mở cửa tiếp theo nếu hạn rơi vào lúc thư viện đóng cửa.` },
    ];
  }

  money(value: number): string {
    return Number(value || 0).toLocaleString('vi-VN');
  }

}
