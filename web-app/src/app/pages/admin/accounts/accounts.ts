import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-accounts',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './accounts.html',
  styleUrls: ['./accounts.css']
})
export class Accounts {
  isImportModalOpen = false;

  students = [
    {
      id: '20012111',
      name: 'Nguyễn Văn Anh',
      class: '10DHPM1',
      email: '20012111@student.huit.edu.vn',
      status: 'active',
      borrowed: 2,
      joined: '15/09/2023'
    },
    {
      id: '20012122',
      name: 'Trần Thị Bảo',
      class: '11DHKTPM2',
      email: '20012122@student.huit.edu.vn',
      status: 'active',
      borrowed: 0,
      joined: '10/08/2024'
    },
    {
      id: '20012133',
      name: 'Lê Minh Chiến',
      class: '10DHHTTT1',
      email: '20012133@student.huit.edu.vn',
      status: 'locked',
      borrowed: 5,
      joined: '05/09/2023'
    },
    {
      id: '20012144',
      name: 'Phạm Văn Duy',
      class: '12DHAT1',
      email: '20012144@student.huit.edu.vn',
      status: 'expired',
      borrowed: 1,
      joined: '20/08/2022'
    },
    {
      id: '20012155',
      name: 'Vũ Thị Yến',
      class: '13DHKT2',
      email: '20012155@student.huit.edu.vn',
      status: 'active',
      borrowed: 0,
      joined: '12/09/2025'
    }
  ];

  openImportModal() {
    this.isImportModalOpen = true;
  }

  closeImportModal() {
    this.isImportModalOpen = false;
  }
}
