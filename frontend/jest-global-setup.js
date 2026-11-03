// Fija la zona horaria de los tests a la del club (España). Los bugs de fecha dependen de ella: con `TZ=UTC`, que es la
// del CI, `toISOString()` y la fecha local coinciden y un test que debería fallar pasa sin enterarse. Se hace aquí, en
// el proceso padre, porque `process.env` dentro de un test es una copia y asignarlo no cambia el reloj real; los
// workers de Jest heredan este entorno al arrancar.
module.exports = async () => {
  process.env.TZ = 'Europe/Madrid';
};
