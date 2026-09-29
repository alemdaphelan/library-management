import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-auth',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './auth.html',
  styleUrls: ['./auth.css']
})
export class Auth implements OnInit {
  mode: 'login' | 'forgot' = 'login';

  loginData = { username: '', password: '' };
  forgotStep: 'email' | 'otp' | 'reset' = 'email';
  forgotData = { email: '', otp: '', newPassword: '' };

  isSubmitting = false;
  successMessage = '';
  errorMessage = '';

  constructor(private router: Router, private authService: AuthService) { }

  ngOnInit() {
    if (window.location.pathname.includes('forgot-password')) {
      this.mode = 'forgot';
    }
  }

  toggleMode(newMode: 'login' | 'forgot', event: Event) {
    event.preventDefault();
    this.mode = newMode;
    this.successMessage = '';
    this.errorMessage = '';
    this.forgotStep = 'email';
    this.forgotData = { email: '', otp: '', newPassword: '' };

    const newUrl = newMode === 'login' ? '/login' : '/forgot-password';
    window.history.pushState({}, '', newUrl);
  }

  onLogin() {
    this.isSubmitting = true;
    this.errorMessage = '';

    this.authService.login(this.loginData.username, this.loginData.password).subscribe({
      next: (response) => {
        this.isSubmitting = false;
        if (response.role === 'ADMIN' || response.role === 'LIBRARIAN' || response.role === 'ACCOUNTANT' || response.role === 'TREASURER') {
          this.router.navigate(['/admin']);
        } else {
          this.router.navigate(['/']); 
        }
      },
      error: (err) => {
        console.log(err);
        this.isSubmitting = false;
        this.errorMessage = 'Sai tên đăng nhập hoặc mật khẩu. Vui lòng thử lại!';
        alert(this.errorMessage);
      }
    });
  }

  onForgotPassword() {
    this.isSubmitting = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.authService.forgotPassword(this.forgotData.email).subscribe({
      next: (res: any) => {
        this.isSubmitting = false;
        this.successMessage = 'Mã OTP đã được gửi đến email của bạn.';
        this.forgotStep = 'otp';
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: (err: any) => {
        this.isSubmitting = false;
        this.errorMessage = err.error || 'Đã có lỗi xảy ra.';
      }
    });
  }

  onVerifyOtp() {
    this.isSubmitting = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.authService.verifyOtp(this.forgotData.email, this.forgotData.otp).subscribe({
      next: (res: any) => {
        this.isSubmitting = false;
        this.successMessage = 'Xác thực thành công. Vui lòng nhập mật khẩu mới.';
        this.forgotStep = 'reset';
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: (err: any) => {
        this.isSubmitting = false;
        this.errorMessage = err.error || 'Mã OTP không hợp lệ hoặc đã hết hạn.';
      }
    });
  }

  onResetPassword() {
    this.isSubmitting = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.authService.resetPassword(this.forgotData.email, this.forgotData.otp, this.forgotData.newPassword).subscribe({
      next: (res: any) => {
        this.isSubmitting = false;
        this.successMessage = 'Đặt lại mật khẩu thành công! Vui lòng đăng nhập lại.';
        setTimeout(() => {
          this.toggleMode('login', new Event('click'));
        }, 3000);
      },
      error: (err: any) => {
        this.isSubmitting = false;
        this.errorMessage = err.error || 'Đặt lại mật khẩu thất bại.';
      }
    });
  }
}
