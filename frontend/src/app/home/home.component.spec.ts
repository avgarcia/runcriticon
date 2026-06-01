import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HomeComponent } from './home.component';

/**
 * Test de muestra (Jest) que demuestra que el stack de testing del frontend funciona
 * end-to-end en el build (ADR-0012 D21). Los tests reales por componente llegan con
 * las features de Fase 1.
 */
describe('HomeComponent', () => {
  let fixture: ComponentFixture<HomeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
  });

  it('se crea', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('muestra el título Runcriticon', () => {
    const titulo = fixture.nativeElement.querySelector('mat-card-title');
    expect(titulo?.textContent).toContain('Runcriticon');
  });
});
