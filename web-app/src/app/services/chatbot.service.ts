import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface RagSource {
  sourceKey: string;
  title: string;
  sourceUri: string;
  type: 'POLICY' | 'CATALOG' | string;
  snippet: string;
  score: number;
}

export interface RagChatResponse {
  requestId: string;
  answer: string;
  sources: RagSource[];
  suggestions: string[];
  grounded: boolean;
  fallbackUsed: boolean;
  model: string;
}

@Injectable({ providedIn: 'root' })
export class ChatbotService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/api/v1/chatbot`;

  ask(message: string): Observable<RagChatResponse> {
    return this.http.post<RagChatResponse>(`${this.apiUrl}/query`, { message });
  }
}
