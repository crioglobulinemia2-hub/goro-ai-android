# Etapa 1/12 — Auditoría técnica (2026-11-01)

## Inventario comprobado
- Aplicación Android Java, paquete `ar.goro.ai`, minSdk 26, targetSdk 35, compileSdk 35.
- Android Gradle Plugin 8.7.3 y Gradle 8.9 en GitHub Actions.
- Archivos fuente: `MainActivity.java` y `ZnpBleInspector.java`.
- Manifest: solo permiso INTERNET; actividad principal exportada.
- No hay pruebas automatizadas, emulador ni test instrumentado configurados.
- No hay integración NVIDIA, Google Calendar, render 3D ni comunicación BLE activa.
- La interfaz actual no cumple la referencia visual aprobada.

## Evidencia de compilación
- Run 41349374996: success (voz y alarmas).
- Run 41349563254: success (memoria local).
- Run 41349766623: success (documentación ZNP).
- Run 41349772151: success (inspector GATT pasivo).
- Los resultados confirman compilación, no ejecución funcional ni compatibilidad con el estuche.

## Riesgos detectados
1. Falta cobertura de pruebas unitarias y de integración.
2. La alarma y el temporizador dependen de actividades de reloj externas; no hay confirmación transaccional de creación.
3. Voz por RecognizerIntent no equivale a conversación continua.
4. Memoria SharedPreferences sin modelo contextual, controles de escala ni cifrado.
5. El inspector GATT es pasivo y no está conectado a un flujo de descubrimiento autorizado.
6. No hay render 3D ni motor de animación.
7. La API NVIDIA no está integrada ni existe almacenamiento seguro de credenciales.
8. No hay autorización OAuth para Calendar.
9. No hay evidencia de pruebas físicas.

## Puerta de salida de etapa 1
- [x] Inventario de configuración y código existente.
- [x] Compilación limpia en GitHub Actions.
- [x] Riesgos y funciones ausentes documentados.
- [x] Pruebas unitarias automatizadas y verificadas (MathEngineTest; GitHub Actions 41349971714).
- [x] Validación automatizada de manifest y permisos mínimos (run 41349971714).
- [x] Regresión reproducible: clean testDebugUnitTest assembleDebug, validación de APK ZIP y artifact (run 41349971714).

**Estado: ETAPA 1 APROBADA para auditoría de código y compilación.** GitHub Actions 41349971714 terminó success. Esto no valida comportamiento en dispositivo ni integra NVIDIA, robot 3D o ZNP; esas verificaciones pertenecen a etapas posteriores.
