import { MarkDistance } from '../../core/my-marks.service';

/** Las cuatro distancias estándar, en el orden fijo del wireframe (`mis-marcas.html`) y del contrato:
 * 5K, 10K, 21K, 42K. `full` es el nombre coloquial que solo llevan las dos últimas. */
export const MARK_DISTANCES: { value: MarkDistance; short: string; full: string | null }[] = [
  { value: '5K', short: '5K', full: null },
  { value: '10K', short: '10K', full: null },
  { value: '21K', short: '21K', full: $localize`media maratón` },
  { value: '42K', short: '42K', full: $localize`maratón` },
];
