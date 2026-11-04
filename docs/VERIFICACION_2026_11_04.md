# Estado comprobado de GORO IA, 4 de noviembre de 2026

La aplicación todavía no cumple la entrega final solicitada. Esta revisión continúa la rama `fix/goro-z02-native-audio-bridge`, sin reemplazarla por una aplicación genérica nueva.

## Cambios implementados

- Las respuestas largas se dividen por oraciones para sintetizar todo el texto con Diego y reproducirlo secuencialmente por Z02.
- Al salir de la actividad se cancelan las conexiones HTTP, el reconocimiento gRPC, la captura del estuche y la cola de audio. Los resultados de peticiones anteriores no reactivan el micrófono.
- El reproductor libera el audio antes de iniciar la siguiente escucha.
- La voz se valida por identificador exacto en el catálogo oficial. Una respuesta de Nemotron cortada por el límite de tokens se informa como incompleta.
- Las transcripciones conservan todos los segmentos reconocidos.
- Agenda y Gmail tienen paneles propios; las tarjetas consultan los servicios existentes. El reloj se actualiza durante la ejecución.
- Se permite importar una configuración NVIDIA personal opcional al almacén cifrado del dispositivo. El archivo privado no está en Git ni en los APK públicos de CI.

## Evidencia obtenida

Se utilizaron Gemini y Nemotron desde sus interfaces para revisión técnica. Sus propuestas se verificaron contra el código y la documentación oficial; sus respuestas no se consideran pruebas de ejecución.

El commit `721164145e4f3256f794fea8d0b390983ccdeda3` pasó la compilación, las pruebas JVM y las pruebas de arranque y navegación del emulador en el run `41835318175`. La captura posterior a la reinstalación quedó cubierta por un permiso Android; el script se corrigió para conceder nuevamente los permisos del entorno de prueba antes de capturar.

Con la credencial personal previamente autorizada para reutilizar desde Cabezón se realizaron solicitudes reales, fuera del emulador:

| Servicio | Resultado observado |
| --- | --- |
| Nemotron 3.5 Super VL 120B, endpoint NVIDIA chat/completions | HTTP 200; texto `GORO conectado.`; finalización `stop` |
| Catálogo NVIDIA de voces | HTTP 200; disponible `Magpie-Multilingual.ES-US.Diego` |
| Síntesis Diego | HTTP 200; WAV PCM mono, 22.050 Hz, 16 bits, 5,29 segundos |
| Reconocimiento Parakeet gRPC | No verificado; el proxy del entorno rechazó la conexión con HTTP 403 |

Estas comprobaciones demuestran acceso real al modelo y a la síntesis, pero no prueban la cadena completa micrófono Z02 → reconocimiento → respuesta → reproducción Z02.

## Trabajo que aún impide declarar un APK final

1. La interfaz y el robot renderizado no reproducen todavía la referencia aprobada `GORO IA: Asistente Futurista Conectado.png`. La existencia de un GLB articulado no demuestra esa fidelidad visual.
2. Gmail usa AuthorizationClient, pero no se verificó un cliente OAuth Android registrado para el paquete y certificado reales, ni una autorización y lectura desde esta aplicación.
3. No hay acceso al Z02 físico desde este entorno para verificar identidad, micrófono, altavoz, visor, reconexión y conversación continua.
4. La firma del APK `GORO-IA-1.1.0.apk` recuperado no coincide con el respaldo de firma disponible. No se debe distribuir un reemplazo como actualización compatible ni eliminar datos para ocultar el conflicto.

Certificado SHA-256 del APK recuperado: `81ed6978c3af615fe6f346354d0c3528bbfe4c080c07a229c9227a94110b2b06`.

Certificado SHA-256 del respaldo: `8702426c72ebf740f3055daaf0df591feeddcf0f76918e8b5abf5acc2189ec10`.

Los secretos, tokens, audio privado y archivos de firma no se incluyen en este informe ni en el repositorio. No se modificaron evidencias para forzar una aprobación del control de entrega.
