import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TagKey } from '../../../core/taxonomy.service';
import { GroupCondition, GroupConditionRowComponent } from './group-condition-row.component';

describe('GroupConditionRowComponent', () => {
  const nivel: TagKey = {
    id: 'nivel',
    nombre: 'nivel',
    archivadoEn: null,
    valores: [
      { id: 'medio', valor: 'medio', archivadoEn: null },
      { id: 'alto', valor: 'alto', archivadoEn: null },
    ],
  } as TagKey;
  const objetivo: TagKey = {
    id: 'objetivo',
    nombre: 'objetivo',
    archivadoEn: null,
    valores: [{ id: 'maraton', valor: 'Maratón Valencia', archivadoEn: null }],
  } as TagKey;

  let fixture: ComponentFixture<GroupConditionRowComponent>;
  let component: GroupConditionRowComponent;

  async function crear(condition: GroupCondition, takenTagIds: string[] = []): Promise<void> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({ imports: [GroupConditionRowComponent] }).compileComponents();
    fixture = TestBed.createComponent(GroupConditionRowComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('condition', condition);
    fixture.componentRef.setInput('axes', [nivel, objetivo]);
    fixture.componentRef.setInput('takenTagIds', takenTagIds);
    fixture.detectChanges();
  }

  it('ofrece los valores del eje elegido', async () => {
    await crear({ tagId: 'nivel', valueId: null });

    expect(component.assignableValues().map((value) => value.id)).toEqual(['medio', 'alto']);
  });

  it('no ofrece un eje que ya usa otra condición', async () => {
    await crear({ tagId: 'nivel', valueId: null }, ['objetivo']);

    expect(component.selectableAxes().map((axis) => axis.id)).toEqual(['nivel']);
  });

  it('sí ofrece el eje que usa esta misma condición', async () => {
    await crear({ tagId: 'nivel', valueId: 'medio' }, ['nivel']);

    expect(component.selectableAxes().map((axis) => axis.id)).toContain('nivel');
  });

  it('cambiar de eje deja la condición sin valor', async () => {
    await crear({ tagId: 'nivel', valueId: 'medio' });
    const emitted: GroupCondition[] = [];
    component.conditionChange.subscribe((condition) => emitted.push(condition));

    component.changeAxis('objetivo');

    expect(emitted).toEqual([{ tagId: 'objetivo', valueId: null }]);
  });

  // El filtro solo sabe hacer «y»: dos valores del mismo eje exigirían tenerlos los dos a la vez.
  it('elegir un segundo valor del mismo eje sustituye al primero', async () => {
    await crear({ tagId: 'nivel', valueId: 'medio' });
    const emitted: GroupCondition[] = [];
    component.conditionChange.subscribe((condition) => emitted.push(condition));

    component.changeValue('alto');

    expect(emitted).toEqual([{ tagId: 'nivel', valueId: 'alto' }]);
  });

  it('quitar el chip deja la condición sin valor', async () => {
    await crear({ tagId: 'nivel', valueId: 'medio' });
    const emitted: GroupCondition[] = [];
    component.conditionChange.subscribe((condition) => emitted.push(condition));

    component.clearValue();

    expect(emitted).toEqual([{ tagId: 'nivel', valueId: null }]);
  });

  it('avisa de que una condición sin valor no cuenta', async () => {
    await crear({ tagId: 'nivel', valueId: null });

    expect(fixture.nativeElement.textContent).toContain('esta condición no cuenta');
  });
});
