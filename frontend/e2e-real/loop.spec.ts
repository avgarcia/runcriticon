import { expect, Page, test } from '@playwright/test';
import {
  ADMIN,
  ApiSession,
  COACH_PASSWORD,
  STUDENT_PASSWORD,
  invitationToken,
} from './support/stack';

/**
 * El bucle central del producto contra el backend real: el entrenador publica el plan de la semana a su grupo y el
 * alumno lo ve en su vista. Ningún otro test lo recorre entero: los e2e del job `frontend` mockean la red, y los del
 * backend no pasan por la SPA. Aquí la SPA, la API, Postgres y los eventos del outbox son los de verdad.
 *
 * El alta de cuentas es por invitación, así que los datos se preparan por API con el admin de arranque y el alumno
 * activa su cuenta por la interfaz: es lo que ejercita que el frontend y el backend coinciden en la versión del texto
 * de consentimiento.
 */

const NOTA = 'Rodaje suave de e2e, sin forzar';

test('el plan que publica el entrenador a su grupo lo ve el alumno', async ({
  browser,
  baseURL,
}) => {
  const club = await prepararClub(baseURL!);

  // Entrenador: entra, crea el plan de esta semana, le añade la sesión de hoy y lo publica al grupo.
  const coachContext = await browser.newContext();
  const coach = await coachContext.newPage();
  await iniciarSesion(coach, club.coach.email, COACH_PASSWORD);

  await coach.goto(`/planificacion/grupos/${club.groupId}/planes`);
  const planLink = coach.getByRole('link', { name: new RegExp(lunesDeEstaSemana()) });
  // El entrenador solo puede crear planes de un grupo cuando su asignación ha llegado a la proyección de
  // planificación, que se alimenta por eventos: hasta entonces el alta falla.
  await expect(async () => {
    await coach.getByRole('button', { name: '+ Nuevo plan (esta semana)' }).click();
    await expect(planLink).toBeVisible({ timeout: 3_000 });
  }).toPass({ timeout: 30_000 });
  await planLink.click();

  await expect(coach.getByRole('heading', { name: 'Plan semanal' })).toBeVisible();
  await coach
    .locator('article', { hasText: hoyMesDia() })
    .getByRole('button', { name: '+ Añadir sesión' })
    .click();
  await coach.getByRole('button', { name: 'Rodaje', exact: true }).click();
  await coach.getByRole('button', { name: 'Distancia', exact: true }).click();
  await coach.getByLabel('Metros').fill('8000');
  await coach.getByLabel('Notas para el alumno').fill(NOTA);
  await coach.getByRole('button', { name: 'Guardar sesión' }).click();
  await expect(coach.getByText(NOTA)).toBeVisible();

  await coach.getByRole('button', { name: 'Publicar al grupo' }).click();
  await expect(coach.getByText(/[1-9]\d* alumnos en el grupo ahora mismo/)).toBeVisible();
  await coach.getByRole('button', { name: 'Publicar plan' }).click();
  await expect(coach.getByRole('button', { name: 'Publicar al grupo' })).toBeHidden();

  // Alumno: activa la cuenta con su invitación (con el consentimiento de datos de salud) y ve el plan.
  const studentContext = await browser.newContext();
  const student = await studentContext.newPage();
  // La pantalla pinta la invitación con esta consulta. El backend la limita por IP (429), y ese límite no se reinicia
  // entre ejecuciones: si falla aquí tras muchas ejecuciones seguidas, hay que recrear el entorno.
  const invitation = student.waitForResponse(
    (response) =>
      response.url().includes('/api/activacion') && response.request().method() === 'GET',
  );
  await student.goto(`/activar?token=${club.student.token}`);
  expect(
    (await invitation).status(),
    'consulta de la invitación en la pantalla de activación',
  ).toBe(200);
  await student.getByLabel('Contraseña', { exact: true }).fill(STUDENT_PASSWORD);
  await student.getByLabel('Repite la contraseña').fill(STUDENT_PASSWORD);
  await student.getByLabel(/consentimiento explícito/).check();
  await student.getByRole('button', { name: 'Activar mi cuenta' }).click();

  // Activar inicia sesión y lleva al alumno a su semana: navegar antes cortaría la activación en curso.
  await expect(student).toHaveURL(/\/mi-plan/);
  // La publicación llega a la vista del alumno por eventos: hasta entonces la semana sale vacía.
  await expect(async () => {
    await student.reload();
    await expect(student.getByText(NOTA)).toBeVisible({ timeout: 3_000 });
  }).toPass({ timeout: 30_000 });
  await expect(student.getByText('Tu plan de esta semana')).toBeVisible();
  await expect(student.getByText('Rodaje', { exact: true }).first()).toBeVisible();
  await expect(student.getByText('8000 m')).toBeVisible();

  await coachContext.close();
  await studentContext.close();
});

interface Club {
  coach: { email: string };
  student: { email: string; token: string };
  groupId: string;
}

/**
 * Un club con un entrenador activo, un grupo suyo y un alumno invitado dentro del grupo. Todo por la API, con el
 * admin de arranque. Cada ejecución usa emails propios para poder repetirse contra la misma base.
 */
async function prepararClub(baseURL: string): Promise<Club> {
  const run = Date.now().toString(36);
  const coachEmail = `entrenadora-${run}@club.test`;
  const studentEmail = `alumno-${run}@club.test`;

  const admin = await ApiSession.open(baseURL);
  await admin.login(ADMIN.email, ADMIN.password);

  const coach = await admin.expect<{ id: string }>(201, 'POST', '/api/entrenadores', {
    nombre: 'Entrenadora Prueba',
    email: coachEmail,
  });
  const coachSession = await ApiSession.open(baseURL);
  await coachSession.expect(200, 'POST', '/api/activacion', {
    token: await invitationToken(coachEmail),
    password: COACH_PASSWORD,
  });
  await coachSession.dispose();

  const taxonomia = await admin.expect<{
    tags: { nombre: string; valores: { id: string; valor: string }[] }[];
  }>(200, 'GET', '/api/taxonomia');
  const nivelIniciacion = taxonomia.tags
    .find((tag) => tag.nombre === 'nivel')
    ?.valores.find((valor) => valor.valor === 'iniciación');
  expect(nivelIniciacion, 'la taxonomía por defecto no trae nivel: iniciación').toBeTruthy();

  const student = await admin.expect<{ id: string }>(201, 'POST', '/api/alumnos', {
    nombre: 'Alumno Prueba',
    email: studentEmail,
  });
  await admin.expect(200, 'POST', `/api/alumnos/${student.id}/tags`, {
    valorId: nivelIniciacion!.id,
  });

  const group = await admin.expect<{ id: string }>(201, 'POST', '/api/grupos', {
    nombre: `Grupo e2e ${run}`,
    valores: [nivelIniciacion!.id],
  });
  await admin.expect(200, 'PUT', `/api/grupos/${group.id}/entrenadores/${coach.id}`);
  await admin.dispose();

  return {
    coach: { email: coachEmail },
    student: { email: studentEmail, token: await invitationToken(studentEmail) },
    groupId: group.id,
  };
}

async function iniciarSesion(page: Page, email: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Contraseña').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page).not.toHaveURL(/\/login/);
}

/** Lunes de esta semana (hora local) como `AAAA-MM-DD`, el que calcula el botón «Nuevo plan (esta semana)». */
function lunesDeEstaSemana(): string {
  const hoy = new Date();
  const desdeElLunes = (hoy.getDay() + 6) % 7;
  return fechaIso(new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate() - desdeElLunes));
}

/** Hoy como `MM-DD`, que es lo que rotula cada día en el editor semanal. */
function hoyMesDia(): string {
  return fechaIso(new Date()).slice(5);
}

function fechaIso(fecha: Date): string {
  const dos = (n: number) => String(n).padStart(2, '0');
  return `${fecha.getFullYear()}-${dos(fecha.getMonth() + 1)}-${dos(fecha.getDate())}`;
}
