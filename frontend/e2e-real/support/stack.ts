import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { APIRequestContext, expect, request } from '@playwright/test';

/**
 * Acceso al entorno real de `infrastructure/e2e` para preparar los datos de un recorrido: una sesión HTTP contra
 * la API (con el handshake CSRF que hace el frontend) y la lectura del token de invitación del log de la app.
 *
 * El alta de cuentas es por invitación (ADR-0003): el token solo viaja por email. Con el perfil `local`,
 * `StubEmailSender` lo escribe en el log en lugar de enviarlo, y de ahí lo lee `invitationToken`.
 */

const COMPOSE_FILE =
  process.env['E2E_REAL_COMPOSE_FILE'] ??
  path.resolve(__dirname, '../../../infrastructure/e2e/docker-compose.yml');

/** Admin de arranque del perfil `local` (`application-local.yml`, `runcriticon.bootstrap.*`). */
export const ADMIN = { email: 'admin@runcriticon.local', password: 'cambia-esta-password-local' };

/**
 * Contraseñas fijas y sin nada del nombre o el email del usuario: el backend rechaza datos personales. Llevan la
 * palabra «password» como las de los tests del backend: gitleaks descarta por ella los valores de prueba.
 */
export const COACH_PASSWORD = 'luna-password-plateada-77';
export const STUDENT_PASSWORD = 'bosque-password-cometa-88';

type Method = 'GET' | 'POST' | 'PUT';

export interface ApiResult<T = unknown> {
  status: number;
  body: T;
}

/** Sesión contra la API: guarda las cookies y reenvía el token CSRF en cada escritura, como la SPA. */
export class ApiSession {
  private constructor(private readonly context: APIRequestContext) {}

  static async open(baseURL: string): Promise<ApiSession> {
    const context = await request.newContext({ baseURL });
    // Sin sesión devuelve 401, pero el handshake deja la cookie XSRF-TOKEN que exigen las escrituras.
    await context.get('/api/sesion/actual');
    return new ApiSession(context);
  }

  async send<T = unknown>(method: Method, url: string, body?: unknown): Promise<ApiResult<T>> {
    const headers: Record<string, string> = {};
    if (method !== 'GET') {
      headers['X-XSRF-TOKEN'] = await this.xsrfToken();
    }
    const response = await this.context.fetch(url, {
      method,
      headers,
      data: body === undefined ? undefined : body,
    });
    const text = await response.text();
    return { status: response.status(), body: (text ? JSON.parse(text) : null) as T };
  }

  /** Como `send`, pero falla con la respuesta completa si el estado no es el esperado. */
  async expect<T = unknown>(
    status: number,
    method: Method,
    url: string,
    body?: unknown,
  ): Promise<T> {
    const result = await this.send<T>(method, url, body);
    expect(result.status, `${method} ${url} → ${JSON.stringify(result.body)}`).toBe(status);
    return result.body;
  }

  async login(email: string, password: string): Promise<void> {
    await this.expect(200, 'POST', '/api/sesion', { email, password });
  }

  async dispose(): Promise<void> {
    await this.context.dispose();
  }

  // Se relee en cada escritura: el login rota la sesión y con ella puede cambiar el token.
  private async xsrfToken(): Promise<string> {
    const { cookies } = await this.context.storageState();
    const xsrf = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN');
    expect(xsrf, 'el handshake no dejó la cookie XSRF-TOKEN').toBeTruthy();
    return xsrf!.value;
  }
}

/**
 * Token de la última invitación enviada a `email`, leído del log de la app. El envío es asíncrono (sale por el
 * outbox), así que espera a que aparezca.
 */
export async function invitationToken(email: string): Promise<string> {
  const line = new RegExp(
    `\\[STUB-EMAIL\\] Invitación para .*? <${escapeRegExp(email)}> — token=(\\S+) `,
    'g',
  );
  let token: string | undefined;
  await expect
    .poll(
      () => {
        const matches = [...appLog().matchAll(line)];
        token = matches.at(-1)?.[1];
        return token;
      },
      { message: `la app no registró la invitación para ${email}`, timeout: 30_000 },
    )
    .toBeTruthy();
  return token!;
}

function appLog(): string {
  return execFileSync('docker', ['compose', '-f', COMPOSE_FILE, 'logs', '--no-color', 'app'], {
    encoding: 'utf8',
    // El perfil local registra a DEBUG: el log crece rápido.
    maxBuffer: 256 * 1024 * 1024,
  });
}

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
