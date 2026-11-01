# Etapa 5/12 — Motor de conversación por voz

## Implementado en código
- Sesión continua de reconocimiento español es-AR con resultados parciales.
- Reinicio con backoff tras final de turno y errores transitorios.
- Máquina de estados IDLE/LISTENING/PROCESSING/SPEAKING/INTERRUPTED/ERROR.
- Señal de interrupción cuando comienza el habla del usuario para detener TTS desde la capa de integración.
- Pruebas unitarias de transiciones y capacidad de interrupción.

## Límites explícitos
- La sesión se ejecuta mientras la actividad Android está en primer plano.
- No se promete escucha permanente con pantalla apagada: eso requiere servicio foreground de micrófono, notificación persistente y cumplir restricciones Android por versión.
- El controlador aún debe integrarse con NemotronGateway y TTS de MainActivity.
- La calidad de ASR español depende del proveedor de reconocimiento disponible en el dispositivo y requiere prueba física.
- No se declara audio Bluetooth verificado en Z02/N02.

## Criterios de salida
- [x] Controlador de sesión y máquina de estados implementados.
- [x] Pruebas unitarias de turnos e interrupción.
- [ ] Permiso RECORD_AUDIO y flujo runtime integrados en Activity.
- [ ] Integración de callback con Nemotron y TTS.
- [ ] Pruebas físicas en Android de conversación sostenida, ruido, interrupciones y cambio de salida Bluetooth.
- [ ] Evidencia de rendimiento y estabilidad.

**Estado: etapa 5 no aprobada todavía.**
