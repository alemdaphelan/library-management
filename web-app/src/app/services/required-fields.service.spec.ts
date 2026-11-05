import { TestBed } from '@angular/core/testing';
import { RequiredFieldsService } from './required-fields.service';

describe('Required form indicators',()=>{
  let host:HTMLDivElement;
  beforeEach(()=>{TestBed.configureTestingModule({});host=document.createElement('div');document.body.append(host);});
  afterEach(()=>host.remove());
  it('marks wrapped and separate labels once, preserving existing stars and optional fields',()=>{
    host.innerHTML='<label>Required<input required></label><label>Existing<span class="req">*</span><input required></label><label>Optional<input></label><div><label>Password</label><div><input required type="password"></div></div>';
    const s=TestBed.inject(RequiredFieldsService);s.refresh();s.refresh();
    const labels=host.querySelectorAll('label');
    expect(labels[0].querySelectorAll('.required-marker')).toHaveLength(1);
    expect(labels[1].querySelectorAll('.req')).toHaveLength(1);
    expect(labels[1].querySelector('.required-marker')).toBeNull();
    expect(labels[2].querySelector('span')).toBeNull();
    expect(labels[3].querySelector('.required-marker')?.getAttribute('aria-hidden')).toBe('true');
    expect(labels[3].htmlFor).toBe(host.querySelector('input[type=password]')?.id);
    expect(labels[0].querySelector('.field-caption')?.textContent).toBe('*Required');
  });
  it('updates indicators when a conditional field becomes optional',()=>{
    host.innerHTML='<label>Repair fee<input required></label>';
    const s=TestBed.inject(RequiredFieldsService);s.refresh();
    host.querySelector('input')!.removeAttribute('required');s.refresh();
    expect(host.querySelector('.required-marker')).toBeNull();
  });
});
