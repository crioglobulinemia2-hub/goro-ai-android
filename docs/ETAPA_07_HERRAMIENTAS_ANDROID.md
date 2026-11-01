# Etapa 7/12 — Herramientas Android

## Alcance implementado
- Parser determinista en español para alarmas con hora/minutos.
- Parser de temporizadores en segundos, minutos y horas.
- Validación de rangos: hora 0–23, minutos 0–59 y temporizadores 1 segundo–24 horas.
- Rechazo de entradas ambiguas o fuera de rango.
- Pruebas unitarias para formatos válidos e inválidos.
- Integración con acciones estándar Android AlarmClock desde MainActivity.
- Calculadora aritmética básica con protección de división por cero.

## Alcance de ejecución
Alarmas y temporizadores se delegan a la aplicación de reloj del dispositivo mediante intents ACTION_SET_ALARM/ACTION_SET_TIMER. Esto permite al sistema gestionar sonido y persistencia; GORO informa que abrió el reloj, no afirma que el usuario confirmó el guardado si el sistema no devuelve tal confirmación.

## Pendiente para aceptación
- [ ] Compilación y suite de regresión de esta etapa.
- [ ] Prueba en dispositivo real con app de reloj instalada.
- [ ] Verificación de creación, edición, cancelación y disparo de alarma.
- [ ] Verificación de temporizador en primer plano y con pantalla bloqueada.
- [ ] Pruebas de accesibilidad y manejo de ausencia de app reloj.

No se implementan alarmas propias duplicadas ni notificaciones exactas en esta etapa; se usa el reloj nativo Android para evitar permisos innecesarios y problemas de ejecución en segundo plano.
