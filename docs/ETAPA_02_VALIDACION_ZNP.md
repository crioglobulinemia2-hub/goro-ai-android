# Etapa 2/12 — ZNP N02 y Bagi Bagi

## Alcance y hallazgos
GORO AI debe identificar y admitir tanto ZNP Z02 como ZNP N02. Cada dispositivo debe mostrar GORO y enviar interacciones táctiles solo si esas capacidades son verificadas en su hardware. La existencia de Bagi Bagi y del SDK JieLi RCSP no demuestra compatibilidad del firmware concreto del N02.

Referencias técnicas:
- https://github.com/Jieli-Tech/Android-JL_Bluetooth
- https://doc.zh-jieli.com/SDKWiki/master/Jieli-Tech/Android-JL_Bluetooth/device-functions/charging-case.html

## Controles de aceptación obligatorios
- [ ] Identificación del chip o fabricante del controlador del N02 mediante documentación fiable o hardware.
- [ ] Identificación de servicios BLE del estuche real con consentimiento.
- [ ] Verificación de si existe protocolo RCSP, incluida autenticación.
- [ ] Medición real de resolución, formatos, memoria y frecuencia de actualización del visor.
- [ ] Verificación de recepción de gestos táctiles en Android.
- [ ] Verificación de reproducción y micrófono por Bluetooth sin interferir con Bagi Bagi.
- [ ] Prueba de desconexión y reconexión con el N02 real.
- [x] Inspector GATT pasivo disponible en código.
- [x] Clasificador de UUIDs con pruebas unitarias y sin inferencias falsas de RCSP.

## Arquitectura dual
- Un solo motor GORO y dos perfiles de dispositivo.
- La detección de nombre anunciado es orientativa; no sustituye validación de identidad ni protocolo.
- El perfil declara capacidades desconocidas como no verificadas, nunca como disponibles.
- El Z02 de Cabezón aporta antecedentes, no evidencia automática para el N02.
- Se agregaron pruebas unitarias de identificación Z02/N02.

## Seguridad
No escribir características BLE, enviar comandos OTA, actualizar firmware ni probar autenticaciones desconocidas. No almacenar MAC o secretos en logs públicos.

## Estado
**ETAPA 2 NO APROBADA** hasta realizar las pruebas físicas del estuche y documentar sus resultados. No sustituir evidencias con afirmaciones sobre otros modelos ZNP.
