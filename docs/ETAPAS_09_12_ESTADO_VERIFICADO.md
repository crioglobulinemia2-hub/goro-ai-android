# GORO AI 9/12 a 12/12 — Estado verificable

Referencia visual obligatoria: collage del humanoide GORO aprobado el 01/11/2026. No sustituir con chat, GIF, esferas, cubos o imagen estática.

## Etapa 9 — Avatar 3D vivo
Existen AvatarRigContract, VisemeEstimator y ProceduralGesturePlanner.
NO COMPLETA: no está el GLB rigged del humanoide aprobado ni la carga/render Filament conectada. La foto de referencia NO constituye un GLB animable. Hace falta modelado original con skin, cabeza/ojos/boca/brazos/manos, morph targets, texturas PBR y validación física. Ruta pactada: app/src/main/assets/goro/goro_rigged.glb.

## Etapa 10 — Interfaz premium
Existe GoroDashboardView con ocho secciones, tres temas (claro, oscuro y profesional), voz y controles enlazados a Gmail, Calendar, clima, GPS y dispositivos. La interfaz se adapta a tablet.
NO COMPLETA: composición, humanoide, iluminación y animaciones no reproducen todavía la imagen. La aceptación requiere comparación de capturas de la aplicación real con la imagen aprobada.

## Etapa 11 — ZNP Z02/N02 y Bagi Bagi
Se agregó ZnpBleDiscovery con escaneo voluntario de Bluetooth LE, permisos Android y lectura de servicios GATT sin comandos al estuche ni escritura de datos. Los perfiles Z02 y N02 no asumen capacidades sin verificar.
NO COMPLETA: no se han verificado protocolo RCSP, pantalla, audio, gestos ni autenticación en ambos dispositivos físicos. Tener el SDK JieLi disponible no demuestra compatibilidad.

## Etapa 12 — Integración, QA y APK
GitHub Actions puede construir APK debug y ejecutar pruebas unitarias. scripts/release_gate.py genera reporte independiente y --strict no aprueba hasta verificar GLB, servidor NVIDIA real, comparación visual, compatibilidad ZNP, pruebas Android físicas y APK release con firma validada.
NO COMPLETA: el APK debug no es el APK definitivo firmado ni prueba que el avatar, NVIDIA o los estuches funcionen.

## Restricciones de aceptación
- NVIDIA Developer: proxy autenticado, respuesta real, control de cuotas y secretos fuera de APK.
- Calendar: cuenta seleccionada, sincronización y altas/bajas/cambios comprobados en Android real.
- GPS y clima: ubicación auténtica y respuesta de Open-Meteo visualizada.
- Voz: conversación continua, interrupción, lipsync sincronizado medido, sin gestos simulados.
- 3D: GLB rigged, morfología de la imagen, FPS y memoria medidos en tablet de destino.
- ZNP: servicios GATT reales, protocolo, autenticación, capacidad visor/táctil/audio.
- Release: firma, instalación, arranque y pruebas de regresión sin crashes.

Regla: no declarar 12/12 ni APK final si el release gate estricto falla o si faltan evidencias auditables. No solicitar al usuario que pruebe prototipos no terminados.
