# Etapa 3/12 — Arquitectura y seguridad

## Límites de los módulos
- core/ModelGateway: conexión a modelo vía servidor intermediario, sin secretos en APK.
- core/ToolDispatcher: ejecución de acciones con estado verificable, errores controlados y confirmación de escrituras.
- core/DeviceBridge: transporte Bluetooth aislado del asistente.
- core/AssistantAction y ActionResult: contrato de intención, resultado y confirmación.
- MainActivity: interfaz provisional; no se considera interfaz final.
- Memoria, calendario, clima y render 3D: implementaciones pendientes que deben depender de contratos, no acoplarse a la actividad.

## Modelo de amenazas
1. Clave NVIDIA extraída de APK: mitigación mediante proxy autorizado y almacenamiento de secretos solo en servidor.
2. Prompt injection que provoca escrituras: confirmación explícita y validación de parámetros.
3. Datos personales en registros: evitar logs de transcripciones, credenciales y direcciones BLE.
4. Bluetooth desconocido: solo lectura hasta validación de protocolo y emparejamiento.
5. Respuestas falsas: estado SUCCESS solo cuando la herramienta confirma ejecución.
6. Conectividad perdida: estados UNAVAILABLE/ERROR, sin afirmar ejecución.
7. OAuth Calendar: scopes mínimos y tokens fuera de logs y repositorio.

## Puerta de aceptación
- [x] Contratos de acciones, herramientas, modelo y dispositivo en código.
- [x] Pruebas unitarias de confirmación, indisponibilidad y fallos controlados.
- [x] Estrategia de protección de credenciales y permisos documentada.
- [x] Compilación limpia y pruebas unitarias verificadas en GitHub Actions 41350853529 (success).
- [x] Control estático de secretos NVIDIA, permiso privilegiado y tráfico no cifrado: success en GitHub Actions 41350853529.
- [x] Contratos desacoplados y probados como infraestructura; implementaciones futuras deben someterse nuevamente a regresión.

**Etapa 3 aprobada para arquitectura base y controles estáticos.** Evidencia: GitHub Actions 41350853529, success. No se declara conexión real a NVIDIA ni seguridad de producción. Las revisiones dinámicas de integración siguen pendientes en etapas posteriores. La etapa 2 mantiene sus verificaciones físicas pendientes.
