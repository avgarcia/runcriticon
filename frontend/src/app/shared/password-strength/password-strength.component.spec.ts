import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PasswordStrengthComponent } from './password-strength.component';

// zxcvbn se carga con import() dinámico; en Jest los mocks interceptan también esa vía.
jest.mock('@zxcvbn-ts/core', () => ({
  ZxcvbnFactory: jest.fn().mockImplementation(() => ({
    check: (password: string) => ({ score: password.length >= 16 ? 4 : 1 }),
  })),
}));
jest.mock('@zxcvbn-ts/language-common', () => ({ adjacencyGraphs: {}, dictionary: {} }));
jest.mock('@zxcvbn-ts/language-es-es', () => ({ translations: {}, dictionary: {} }));

describe('PasswordStrengthComponent', () => {
  let fixture: ComponentFixture<PasswordStrengthComponent>;
  let component: PasswordStrengthComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PasswordStrengthComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(PasswordStrengthComponent);
    component = fixture.componentInstance;
  });

  const flushZxcvbn = async () => {
    // Deja resolver la carga dinámica y la evaluación asíncrona.
    await new Promise((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();
  };

  it('no se muestra con contraseña vacía', () => {
    fixture.componentRef.setInput('password', '');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent.trim()).toBe('');
  });

  it('evalúa la contraseña con zxcvbn y muestra la etiqueta', async () => {
    fixture.componentRef.setInput('password', 'x'.repeat(16));
    fixture.detectChanges();
    await flushZxcvbn();
    expect(component.score()).toBe(4);
    expect(fixture.nativeElement.textContent).toContain('Excelente');
  });

  it('marca el check de longitud a partir de 12 caracteres', () => {
    fixture.componentRef.setInput('password', 'x'.repeat(12));
    fixture.detectChanges();
    expect(component.lengthOk()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Al menos 12 caracteres');
  });

  it('marca la coincidencia solo cuando ambas contraseñas son iguales y no vacías', () => {
    fixture.componentRef.setInput('password', 'contraseña-larga');
    fixture.componentRef.setInput('confirm', 'otra-distinta');
    fixture.detectChanges();
    expect(component.matchOk()).toBe(false);

    fixture.componentRef.setInput('confirm', 'contraseña-larga');
    fixture.detectChanges();
    expect(component.matchOk()).toBe(true);
  });

  it('descarta la puntuación si la contraseña cambió durante la evaluación', async () => {
    fixture.componentRef.setInput('password', 'x'.repeat(16));
    fixture.detectChanges();
    fixture.componentRef.setInput('password', 'y');
    fixture.detectChanges();
    await flushZxcvbn();
    // La última evaluación corresponde a 'y' (score 1), no a la contraseña anterior.
    expect(component.score()).toBe(1);
  });
});
