import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { DialogService } from './services/dialog.service';
import { RequiredFieldsService } from './services/required-fields.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, CommonModule],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly title = signal('web-app');

  constructor(
    public dialogService: DialogService,
    requiredFields: RequiredFieldsService,
  ) {}
}
