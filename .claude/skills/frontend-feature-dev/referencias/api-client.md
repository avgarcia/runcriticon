# Cliente API y comunicación con el backend — Runcriticon

Fuente: ADR-0001 D10/D11, ADR-0012 D12-D18, ADR-0003, `frontend/CLAUDE.md`

## Cliente HTTP generado desde OpenAPI (ADR-0012 D12)

**Nunca escribas `HttpClient` directamente**. El cliente TypeScript se genera automáticamente desde la spec OpenAPI del backend con `openapi-generator-cli`.

- Ubicación del código generado: `frontend/src/app/core/api/generated/`
- **No editar a mano** — se sobreescribe en cada `npm run gen:api`.
- Generador: `ng-openapi-gen` (produce servicios inyectables con `HttpClient` nativo que devuelven `Observable<T>`).

```bash
npm run gen:api   # regenera el cliente desde la spec
```

Si necesitas consumir un endpoint nuevo:
1. Actualiza `api/openapi.yaml` (spec contract-first, ADR-0001 D10).
2. Ejecuta `npm run gen:api`.
3. Usa el servicio generado en tu feature.

### Uso del cliente generado

```typescript
import { GruposService } from '../core/api/generated'; // servicio generado

@Injectable({ providedIn: 'root' })
export class GruposFeatureService {
  private readonly api = inject(GruposService);

  readonly #grupos = signal<Grupo[]>([]);
  readonly grupos = this.#grupos.asReadonly();

  cargarGrupos(): void {
    this.api.listarGrupos().subscribe(grupos => this.#grupos.set(grupos));
  }
}
```

## Autenticación — cookie httpOnly (ADR-0003 D10)

- La sesión es una **cookie `httpOnly`, `SameSite=Lax`, `Secure`** gestionada por el backend.
- El frontend **no la lee, no la guarda, no la envía explícitamente** — el navegador la adjunta solo en cada petición al mismo origen.
- **Nunca uses `localStorage`/`sessionStorage`** para auth.
- Login: magic link o contraseña → el backend establece la cookie.

```typescript
// INCORRECTO — nunca:
localStorage.setItem('token', response.token);
headers.set('Authorization', `Bearer ${token}`);

// CORRECTO — el navegador gestiona la cookie automáticamente.
```

## Interceptor CSRF (ADR-0012 D13, ADR-0003 D14)

Configurado en `app.config.ts` de forma nativa — sin interceptor manual:

```typescript
provideHttpClient(
  withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' })
)
```

Angular lee automáticamente la cookie `XSRF-TOKEN` y la reenvía como header en peticiones modificadoras (POST, PUT, DELETE, PATCH). No hay que hacer nada extra en los servicios.

## Interceptor de errores (ADR-0012 D14)

Interceptor global ya configurado en `core/`. Comportamiento por status:

| Status | Comportamiento |
|--------|----------------|
| **400** (validación) | El caller traduce `code` → mensaje + marca campo (ver D19) |
| **401** | Redirige a `/login?returnUrl={ruta-actual}` |
| **403** | Toast: "No tienes permiso para esta acción" |
| **404** | El componente lo maneja (lista vacía, item no encontrado) |
| **409** | El caller traduce a UI |
| **429** | Toast: "Demasiados intentos. Espera unos segundos." |
| **5xx** | Toast: "Algo ha ido mal. Vuelve a intentarlo." + reporte a Sentry |
| **Network/timeout** | Toast: "Sin conexión" |

## Interceptor de autenticación — 401 → login (ADR-0012 D15)

Cualquier respuesta 401 redirige automáticamente a `/login?returnUrl={ruta-actual}`. Tras login exitoso, vuelve a la `returnUrl`. Ya está implementado en `core/`.

## Manejo de errores 4xx estructurados (ADR-0012 D19)

El backend devuelve errores 4xx con body estructurado (ADR-0008 D11):

```json
{
  "code": "EMAIL_INVALIDO",
  "field": "email",
  "message": "Email no válido",
  "details": {}
}
```

El frontend:
- Traduce `code` → mensaje localizado (no muestra `message` del backend directamente).
- Si hay `field`: marca el control del formulario con error.
- Si no hay `field`: error general sobre el formulario.
- Catálogo de códigos: `src/app/core/api/error-codes.ts`.

```typescript
// En el servicio que llama:
this.api.crearAlumno(datos).subscribe({
  next: (alumno) => { /* éxito */ },
  error: (err) => {
    if (err.status === 400 && err.error?.field) {
      this.formulario.controls[err.error.field].setErrors({
        serverError: this.errorCodes.traducir(err.error.code)
      });
    }
  }
});
```

## Autorización en la UI (ADR-0012 D17, ADR-0009 D18)

Al iniciar sesión el frontend pide `GET /me/permissions` y cachea en un servicio singleton.

```typescript
// Directiva hasPermission para ocultar elementos:
<button *hasPermission="'plan.editar'" mat-button>Editar plan</button>
<mat-menu-item *hasPermission="'alumnos.gestionar'">Gestionar alumnos</mat-menu-item>
```

**Regla de oro**: la UI **no es la barrera de seguridad**. El backend autoriza cada petición. Si la UI olvida ocultar algo, el backend devuelve 403 — no es una vulnerabilidad.

## Route guards (ADR-0012 D18)

```typescript
// authGuard — ya implementado en core/auth.guard.ts
// Redirige a /login si no hay sesión activa

export const {FEATURE}_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./{feature}.component').then(m => m.{Feature}Component),
    canActivate: [authGuard],
  },
];

// permissionGuard — para rutas que requieren permiso específico:
canActivate: [authGuard, permissionGuard('plan.ver')],
```

## Proxy en local (desarrollo)

`frontend/proxy.conf.json` proxya `/api` y `/actuator` a `localhost:8080`. El dev server arranca con este proxy. No hay que configurar CORS en local.
