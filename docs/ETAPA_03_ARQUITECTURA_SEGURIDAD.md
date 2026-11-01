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
- [ ] Compilación y suite de regresión de este commit verificadas.
- [ ] Auditoría automatizada de secretos y permisos de este commit.
- [ ] Revisión de contratos contra implementaciones posteriores.

No se declara conexión real a NVIDIA ni seguridad de producción. La etapa 2 mantiene sus verificaciones físicas pendientes.
