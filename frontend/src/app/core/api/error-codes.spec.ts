import { HttpErrorResponse } from '@angular/common/http';
import { fieldOf, messageForError } from './error-codes';

describe('error-codes', () => {
  describe('messageForError', () => {
    it('traduce un código conocido al mensaje del catálogo', () => {
      const err = new HttpErrorResponse({ status: 400, error: { code: 'INVALID_INPUT' } });
      expect(messageForError(err)).toBe('Revisa los datos introducidos.');
    });

    it('nunca devuelve el message crudo del backend', () => {
      const err = new HttpErrorResponse({
        status: 400,
        error: { code: 'INVALID_INPUT', message: 'texto interno del backend' },
      });
      expect(messageForError(err)).not.toBe('texto interno del backend');
    });

    it('cae al mensaje de fallback ante un código desconocido', () => {
      const err = new HttpErrorResponse({ status: 418, error: { code: 'SOY_UNA_TETERA' } });
      expect(messageForError(err)).toBe('No se ha podido completar la operación. Inténtalo de nuevo.');
    });

    it('cae al mensaje de fallback si el error no es un HttpErrorResponse', () => {
      expect(messageForError(new Error('boom'))).toBe(
        'No se ha podido completar la operación. Inténtalo de nuevo.',
      );
    });
  });

  describe('fieldOf', () => {
    it('devuelve el campo cuando el backend lo indica', () => {
      const err = new HttpErrorResponse({
        status: 400,
        error: { code: 'INVALID_INPUT', field: 'email' },
      });
      expect(fieldOf(err)).toBe('email');
    });

    it('devuelve null cuando no hay campo', () => {
      const err = new HttpErrorResponse({ status: 403, error: { code: 'FORBIDDEN' } });
      expect(fieldOf(err)).toBeNull();
    });
  });
});
