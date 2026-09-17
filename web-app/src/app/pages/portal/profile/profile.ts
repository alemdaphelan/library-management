import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './profile.html',
  styleUrls: ['./profile.css']
})
export class Profile {
  activeTab: 'info' | 'password' | 'settings' = 'info';

  constructor(private route: ActivatedRoute) {
    this.route.queryParams.subscribe(params => {
      if (params['tab']) {
        this.activeTab = params['tab'];
      }
    });
  }

  userInfo = {
    fullName: 'Nguyễn Văn A',
    studentId: '2001190001',
    email: 'nva@sinhvien.huit.edu.vn',
    phone: '0901234567',
    faculty: 'Công nghệ thông tin'
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

  saveChanges() {
    this.isSaving = true;
    this.successMsg = '';
    setTimeout(() => {
      this.isSaving = false;
      this.successMsg = 'Cập nhật thành công!';
      setTimeout(() => this.successMsg = '', 3000);
    }, 1000);
  }
}
