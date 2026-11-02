# Auditoría triple de GORO AI — criterio de producto comercial

Fecha de auditoría: 2026-11-01/02 UTC. Referencia vinculante: imagen aprobada de GORO humanoide.

## Dictamen general
NO APROBADA PARA VENTA. La compilación de desarrollo no equivale a producto final. La imagen de referencia no está implementada como humanoide 3D, no existe conexión real con Nemotron y no se dispone de validación física del hardware ZNP.

## 1. Diseño y experiencia — RECHAZADO
- La aplicación tiene una estructura Android nativa adaptable y tres paletas; no replica la composición de la imagen, la ambientación, el robot, las tarjetas y las expresiones.
- No existe GLB rigged dentro del APK. Inspección del APK de CI: Filament JNI está empaquetado; no hay assets GLB ni directorio de assets del avatar.
- Se agregó feedback de pulsación: RippleDrawable, compresión de escala a 0.965, elevación, retorno animado y llamada a HapticFeedbackConstants.VIRTUAL_KEY.
- Prueba Robolectric GoroDashboardViewTest cubre navegación, controles y estado presionado. No sustituye medición física del motor háptico.
- Falta captura Android real y comparación visual cuantitativa contra la referencia aprobada.

## 2. Concepto y funcionamiento — PARCIAL
- Ocho secciones de navegación.
- Servicios locales: voz continua en primer plano, TTS, alarmas/temporizadores mediante intents, memoria local, recordatorios WorkManager, Calendar Provider y clima por ubicación/Open-Meteo.
- Pruebas JVM/Robolectric del commit 2125d02: 58 pruebas, 0 fallos, 0 omitidas. Incluye 2 pruebas de GoroDashboardView.
- La voz usa reconocimiento de Android y TTS del dispositivo; no prueba por sí sola diálogo generativo contextual.
- NemotronGateway existe como cliente, pero MainActivity no lo instancia ni invoca. La ruta de respuesta desconocida confirma que Nemotron no está conectado.
- La selección de cuenta Google está limitada a calendarios sincronizados vía CalendarContract. No hay integración Gmail API para leer o enviar correo.

## 3. Conexiones y respuesta táctil — PARCIAL / RECHAZADO
- Google Calendar: lectura y operaciones de calendario condicionadas a cuenta Android sincronizada y permisos runtime. Sin evidencia de sincronización de cuenta física.
- Gmail: correo electrónico no integrado; la selección de cuenta Google no equivale a acceso al buzón.
- GPS/clima: ubicación Android y consultas Open-Meteo implementadas; sin lectura GPS física de este equipo.
- ZNP: escaneo BLE voluntario y lectura pasiva de servicios GATT. No escribe, no autentica, no envía órdenes y no confirma visor, audio, táctil ni RCSP.
- Háptica: respuesta visual y llamada al sistema Android incorporadas; no hay prueba de vibración física en Surface/Android.
- No hay proxy NVIDIA autenticado, credencial segura ni prueba de respuesta real de Nemotron.

## Superficie de prueba
El APK es Android. No se puede instalar de forma nativa en Windows 10 Surface como un ejecutable Windows. La validación de interfaz se está intentando en un emulador Android de GitHub Actions. No hay acceso remoto a la pantalla, ADB ni sensores físicos de la Surface desde esta sesión; no se afirma que se haya probado allí.

## Evidencia de CI
- Build 229, commit 2125d02: job build exitoso; APK debug empaquetado y ZIP íntegro.
- Reporte JVM del build 229: 58 tests, 0 failures, 0 errors, 0 skipped. GoroDashboardViewTest: ambos casos PASS.
- APK inspeccionado: 26,789,357 bytes; 10 DEX; 8 bibliotecas nativas Filament; 0 GLB y 0 assets del avatar.
- Emulador Android: ejecución independiente de launch/navigation smoke tests en curso en CI; no se contabiliza como PASS hasta su conclusión.
- Release gate: no listo 12/12; falta GLB, evidencia visual, NVIDIA E2E, ZNP físico, Android físico, concepto E2E, háptica física y APK release firmada.

## Criterio para aprobación comercial
Solo aprobar cuando la referencia visual se reproduzca en la app real, el GLB articulado y el lip-sync se validen en hardware, Nemotron y Gmail/Calendar tengan las integraciones que se anuncien, el ZNP confirme capacidades reales, el feedback háptico se pruebe en hardware, la regresión física y de emulador pase y exista APK release firmada y verificable.

Dictamen: NO APROBADA. No comercializar ni presentar como 12/12.
