import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './profile.html',
  styleUrls: ['./profile.css']
})
export class Profile {
  activeTab: 'info' | 'password' | 'settings' = 'info';

  constructor(private route: ActivatedRoute, private authService: AuthService) {
    this.route.queryParams.subscribe(params => {
      if (params['tab']) {
        this.activeTab = params['tab'];
      }
    });

    this.authService.fetchMyProfile().subscribe({
      next: (user: any) => {
        this.userInfo = {
          fullName: user.fullName || 'Chưa cập nhật',
          studentId: user.studentId || 'Chưa cập nhật',
          email: user.email || 'Chưa cập nhật',
          phone: user.phone || 'Chưa cập nhật',
          faculty: user.faculty || 'Chưa cập nhật'
        };
      },
      error: (err: any) => console.error('Failed to fetch profile', err)
    });
  }

  userInfo = {
    fullName: 'Đang tải...',
    studentId: 'Đang tải...',
    email: 'Đang tải...',
    phone: 'Đang tải...',
    faculty: 'Đang tải...'
  };

  passwordData = {
    current: '',
    new: '',
    confirm: ''
  };

  settings = {
    emailAlerts: true,
    smsAlerts: false
  };

  isSaving = false;
  successMsg = '';
  errMsg = '';

  saveChanges() {
    if (this.activeTab === 'password') {
      if (this.passwordData.new !== this.passwordData.confirm) {
        this.errMsg = 'Mật khẩu xác nhận không khớp!';
        setTimeout(() => this.errMsg = '', 3000);
        return;
      }
      this.isSaving = true;
      this.successMsg = '';
      this.errMsg = '';
      this.authService.changePassword(this.passwordData.current, this.passwordData.new).subscribe({
        next: (res: any) => {
          this.isSaving = false;
          this.successMsg = 'Đổi mật khẩu thành công! Bạn sẽ được đăng xuất.';
          this.passwordData = { current: '', new: '', confirm: '' };
          setTimeout(() => {
            this.authService.logout();
          }, 2000);
        },
        error: (err: any) => {
          this.isSaving = false;
          // Use the backend error message if available, else a fallback
          this.errMsg = err.error || 'Đổi mật khẩu thất bại. Vui lòng kiểm tra lại mật khẩu cũ!';
          setTimeout(() => this.errMsg = '', 3000);
        }
      });
    } else {
      this.isSaving = true;
      this.successMsg = '';
      setTimeout(() => {
        this.isSaving = false;
        this.successMsg = 'Cập nhật thành công!';
        setTimeout(() => this.successMsg = '', 3000);
      }, 1000);
    }
  }
}
