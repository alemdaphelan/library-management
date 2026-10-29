import { of } from 'rxjs';
import { AdminSuggestions } from './suggestions';

describe('AdminSuggestions', () => {
  const cdr = { detectChanges: vi.fn() };

  it('loads summary lazily and opens its exact original in the unchanged individual drawer', () => {
    const component = new AdminSuggestions({} as any, cdr as any);
    component.proposals = [{ proposalId: 7, title: 'Clean Code', status: 'PENDING' }];
    expect(component.summaryLoaded).toBe(false); component.showSummary(); expect(component.view).toBe('summary');
    component.openSummaryProposal(7); expect(component.view).toBe('individual'); expect(component.selectedSuggestion?.proposalId).toBe(7);
    component.closeDrawer();
  });

  it('opens the exact proposal referenced by an admin notification', () => {
    const proposal = { proposalId: 7, title: 'Clean Architecture', status: 'PENDING' };
    const proposalService = {
      getAllProposalsForAdmin: vi.fn(() => of([proposal])),
    };
    const route = {
      snapshot: { queryParamMap: { get: (key: string) => key === 'referenceId' ? '7' : null } },
    };
    const component = new AdminSuggestions(proposalService as any, cdr as any, route as any);

    component.ngOnInit();

    expect(component.selectedSuggestion?.proposalId).toBe(7);
    expect(component.isDrawerOpen).toBe(true);
    expect(component.activeTab).toBe('ALL');
  });

  it('requires a rejection reason before calling the API', () => {
    const proposalService = {
      updateProposalStatus: vi.fn(() => of({})),
    };
    const component = new AdminSuggestions(proposalService as any, cdr as any);
    component.selectedSuggestion = {
      proposalId: 7,
      title: 'Clean Architecture',
      status: 'PENDING',
    };
    component.adminNoteInput = '   ';

    component.rejectSuggestion();

    expect(proposalService.updateProposalStatus).not.toHaveBeenCalled();
    expect(component.alertMessage?.text).toContain('lý do từ chối');
  });

  it('submits the rejection reason and updates the proposal', () => {
    const proposalService = {
      updateProposalStatus: vi.fn(() => of({
        proposalId: 7,
        title: 'Clean Architecture',
        status: 'REJECTED',
        adminNote: 'Đã có đủ bản tương đương',
      })),
    };
    const component = new AdminSuggestions(proposalService as any, cdr as any);
    component.proposals = [{ proposalId: 7, title: 'Clean Architecture', status: 'PENDING' }];
    component.selectedSuggestion = component.proposals[0];
    component.adminNoteInput = 'Đã có đủ bản tương đương';

    component.rejectSuggestion();

    expect(proposalService.updateProposalStatus)
      .toHaveBeenCalledWith(7, 'REJECTED', 'Đã có đủ bản tương đương');
    expect(component.proposals[0].status).toBe('REJECTED');
  });
});
