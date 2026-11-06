# GORO AI Android

Asistente personal Android con NVIDIA Nemotron, NVIDIA Magpie Diego / Parakeet, Google Calendar, Gmail API, alarmas, temporizadores, calculadora, búsqueda web y memoria.

## Estado de integración

La rama de reparación work/goro-repair-20261105 incluye selección explícita de ZNP Z01, Z02 y N02, búsqueda BLE amplia, clasificación de anuncios, intento de enlace RCSP, diagnóstico BLE pasivo, control de desconexión y verificación de la salida de audio Bluetooth antes de habilitar la conversación manos libres.

La compatibilidad de cada dispositivo se informa por etapas. Un nombre BLE no acredita identidad; un enlace BLE no acredita autenticación RCSP; y la autenticación RCSP no acredita micrófono, pantalla, audio ni gestos. Las funciones de pantalla se habilitan únicamente después de comprobar el chipset JieLi compatible y leer la resolución física.

## Animaciones de visor

- 320×172: animación horizontal GORO ensamblada desde fragmentos validados.
- 240×296: animación vertical GORO validada.
- La aplicación elige el recurso según la resolución que informa el hardware; no convierte ni envía una animación a una resolución desconocida.

La animación que se muestra dentro de Android y el recurso que admite el visor físico son capacidades distintas.

## Pendientes de validación física

- Conexión, autenticación, micrófono, salida de audio y visor en cada ZNP Z01, Z02 y N02.
- Gmail OAuth con consentimiento real y lectura de mensajes desde la instalación Android.
- Certificado de firma del propietario para actualizar la instalación existente sin borrar datos.
- GLB humanoide autorado y comparación visual contra el diseño aprobado; el robot procedural de CI no es una entrega final.

No marcar una función como operativa por una prueba unitaria, un emulador o un nombre anunciado por BLE.
