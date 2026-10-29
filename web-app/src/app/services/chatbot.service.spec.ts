import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ChatbotService, RagChatResponse } from './chatbot.service';

describe('ChatbotService', () => {
  let service: ChatbotService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ChatbotService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ChatbotService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends the question only to the backend RAG endpoint', () => {
    const response: RagChatResponse = {
      requestId: 'request-1',
      answer: 'Mỗi tài liệu được gia hạn tối đa một lần [1].',
      sources: [{
        sourceKey: 'circulation-policy',
        title: 'Quy định mượn, trả, gia hạn và đặt giữ',
        sourceUri: 'classpath:rag/circulation-policy.md',
        type: 'POLICY',
        snippet: 'Mỗi tài liệu được gia hạn tối đa một lần.',
        score: 0.91
      }],
      suggestions: ['Khi nào không được gia hạn?'],
      grounded: true,
      fallbackUsed: false,
      model: 'gemini-3.6-flash'
    };

    service.ask('Tôi được gia hạn mấy lần?').subscribe(result => {
      expect(result.grounded).toBe(true);
      expect(result.sources[0].sourceKey).toBe('circulation-policy');
    });

    const request = http.expectOne(req => req.url.endsWith('/api/v1/chatbot/query'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ message: 'Tôi được gia hạn mấy lần?' });
    expect(request.request.url).not.toContain('generativelanguage.googleapis.com');
    request.flush(response);
  });
});
