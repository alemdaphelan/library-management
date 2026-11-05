import { Injectable, afterEveryRender, inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';

/** Adds one accessible visual convention to native required controls, including dynamic forms. */
@Injectable({providedIn:'root'})
export class RequiredFieldsService {
  private readonly document=inject(DOCUMENT);
  private sequence=0;
  constructor(){afterEveryRender(()=>this.refresh());}
  refresh():void {
    const needed=new Set<HTMLLabelElement>();
    for(const control of this.document.querySelectorAll<HTMLInputElement|HTMLSelectElement|HTMLTextAreaElement>('input[required],select[required],textarea[required],[aria-required="true"]')){
      const labels=Array.from(control.labels||[]);
      let element:Element|null=control;
      for(let depth=0;!labels.length && element && depth<3;depth++,element=element.parentElement){
        const sibling=element.previousElementSibling;
        if(sibling?.tagName==='LABEL')labels.push(sibling as HTMLLabelElement);
      }
      for(const label of labels){
        if(!control.id)control.id=`required-field-${++this.sequence}`;
        if(!label.contains(control))label.htmlFor=control.id;
        needed.add(label);
        let caption=label.querySelector<HTMLElement>(':scope > .field-caption');
        if(!caption){
          // Keep the star beside its caption even when the label uses CSS grid.
          const parts=Array.from(label.childNodes).filter(node=>
            node.nodeType===3 && !!node.textContent?.trim() || node instanceof Element &&
            (node.matches('.required,.req') || node.tagName==='SPAN' && node.textContent?.trim()==='*'));
          if(parts.length){caption=this.document.createElement('span');caption.className='field-caption';label.prepend(caption);for(const part of parts)caption.append(part);}
        }
        if(!label.querySelector('.required-marker,.required,.req') && !Array.from(label.querySelectorAll('span')).some(s=>s.textContent?.trim()==='*')){
          const marker=this.document.createElement('span');marker.className='required-marker';marker.setAttribute('aria-hidden','true');marker.textContent='*';(caption||label).prepend(marker);
        }
        label.classList.add('has-required-field');
      }
    }
    for(const label of this.document.querySelectorAll<HTMLLabelElement>('label.has-required-field')){
      if(!needed.has(label)){label.querySelector('.required-marker')?.remove();label.classList.remove('has-required-field');}
    }
  }
}
