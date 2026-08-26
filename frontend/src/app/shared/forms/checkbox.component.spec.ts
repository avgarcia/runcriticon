import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { CheckboxComponent } from './checkbox.component';

describe('CheckboxComponent', () => {
  let fixture: ComponentFixture<CheckboxComponent>;
  let component: CheckboxComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [CheckboxComponent] }).compileComponents();
    fixture = TestBed.createComponent(CheckboxComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('inputId', 'test-checkbox');
    fixture.detectChanges();
  });

  it('el label queda asociado al input por for/id', () => {
    const input = fixture.debugElement.query(By.css('input')).nativeElement as HTMLInputElement;
    const label = fixture.debugElement.query(By.css('label')).nativeElement as HTMLLabelElement;

    expect(input.id).toBe('test-checkbox');
    expect(label.getAttribute('for')).toBe('test-checkbox');
  });

  it('no premarcada por defecto', () => {
    const input = fixture.debugElement.query(By.css('input')).nativeElement as HTMLInputElement;

    expect(input.checked).toBe(false);
  });

  it('marcar el checkbox emite true', () => {
    const emitted: boolean[] = [];
    component.checkedChange.subscribe((value: boolean) => emitted.push(value));
    const input = fixture.debugElement.query(By.css('input')).nativeElement as HTMLInputElement;

    input.checked = true;
    input.dispatchEvent(new Event('change'));

    expect(emitted).toEqual([true]);
  });

  it('refleja el estado marcado cuando el input checked cambia desde fuera', () => {
    fixture.componentRef.setInput('checked', true);
    fixture.detectChanges();

    const input = fixture.debugElement.query(By.css('input')).nativeElement as HTMLInputElement;
    expect(input.checked).toBe(true);
  });
});
