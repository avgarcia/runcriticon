import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PasswordStrengthComponent } from './password-strength.component';

describe('PasswordStrengthComponent', () => {
  let fixture: ComponentFixture<PasswordStrengthComponent>;
  let component: PasswordStrengthComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [PasswordStrengthComponent] }).compileComponents();
    fixture = TestBed.createComponent(PasswordStrengthComponent);
    component = fixture.componentInstance;
  });

  it('no renderiza nada si la contraseña está vacía', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.password-strength')).toBeNull();
  });

  it('marca la regla de longitud como cumplida a partir de 12 caracteres', () => {
    fixture.componentRef.setInput('password', 'doce-caracteres!');
    fixture.detectChanges();
    expect(component.lengthOk()).toBe(true);
  });

  it('marca la regla de coincidencia solo cuando confirm iguala a password', () => {
    fixture.componentRef.setInput('password', 'clave-clave-clave');
    fixture.componentRef.setInput('confirm', 'otra-cosa');
    fixture.detectChanges();
    expect(component.matchOk()).toBe(false);

    fixture.componentRef.setInput('confirm', 'clave-clave-clave');
    fixture.detectChanges();
    expect(component.matchOk()).toBe(true);
  });

  it('sube de fortaleza con longitud y variedad de caracteres', () => {
    fixture.componentRef.setInput('password', 'abc');
    fixture.detectChanges();
    const weak = component.strength();

    fixture.componentRef.setInput('password', 'Abc12345!@#$xyz');
    fixture.detectChanges();
    const strong = component.strength();

    expect(strong).toBeGreaterThan(weak);
    expect(component.label()).toBe('Excelente');
  });
});
