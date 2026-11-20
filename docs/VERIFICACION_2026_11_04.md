# Estado comprobado de GORO IA, 4 de noviembre de 2026

La aplicación todavía no cumple la entrega final solicitada. Esta revisión continúa la rama `fix/goro-z02-native-audio-bridge`, sin reemplazarla por una aplicación genérica nueva.

## Continuación del 4 de noviembre, 22:51 UTC

La última versión de código comprobada es `95bba1e2f2c76f1ec6db1be5f6ea7aa6cd623add`, árbol `6c35a3eab440903188e5e5f8edb25b8c1347b318`. Los dos trabajos de [CI 41928049938](https://github.com/crioglobulinemia2-hub/goro-ai-android/actions/runs/41928049938) terminaron correctamente: 170 pruebas JVM/Robolectric, 7 pruebas Python del avatar y 3 pruebas instrumentales en Android API 36. Se compilaron debug y release sin firma; pasaron integridad ZIP, manifiesto, control de credenciales y firma debug.

- Gmail vuelve a la escucha tras resultados, errores o cancelación de lectura solicitada por voz. La lectura consentida ya no recorta el mensaje a 1.450 caracteres.
- Repetir «Conectar» no desarma una sesión Bluetooth activa, y un reintento tardío no interrumpe una captura existente.
- «Agenda reunión mañana a las 15:30» prepara creación y confirmación, conservando «agenda mañana» como consulta.
- El inicio muestra ambiente y tarjetas de clima, agenda y dispositivo con datos de los servicios. El robot sigue siendo procedural y no equivale al personaje aprobado.
- El render se pausa cuando queda oculto, mantiene una sola cadena de cuadros y reutiliza dos buffers faciales. El fork mínimo de ModelViewer 1.70 conserva licencia/procedencia y pasa los flags de transparencia que la implementación original omitía. Java y Kotlin usan JVM 17.
- Una prueba instrumental compara píxeles con el avatar oculto/visible para comprobar transparencia y dibujo del robot. El artefacto `GoroAI-emulator-test-reports` incluye capturas reales en teléfono y tablet. Los tiempos del emulador por software no certifican fluidez ni consumo en el dispositivo físico.
- Gemini y Nemotron respondieron consultas técnicas generales sin código privado ni credenciales. Las indicaciones relevantes se contrastaron con la fuente y el bytecode de Filament.

El control de entrega continúa en `false`. No hay Android físico conectado. Continúan sin verificar el reconocimiento Parakeet y la conversación completa Z02, la autorización y lectura Gmail desde la aplicación, la firma instalada y el personaje definitivo de TRELLIS. Las pruebas guardadas de Nemotron y Diego se conservan sin presentarlas como una conversación física completa. No se entrega la APK de CI como versión personal final.

## Continuación del 4 de noviembre, 18:50 ART

- Se recuperó el árbol de PR #3 (`006da605`) y se verificó que las correcciones del micrófono Z02 y la energía labial están integradas. Los dos trabajos de CI de ese commit terminaron correctamente en el run `41896996796`.
- Se corrigió un error visible: `showVoiceStatus` convertía tanto un fallo de Parakeet como una comprobación exitosa en «Comprobando voz Diego». Ahora muestra el resultado recibido y mantiene el texto completo accesible.
- El inicio o cierre de captura del micrófono ya no borra el diagnóstico de los servicios de voz en Ajustes. Dos pruebas de regresión cubren fallo, éxito, navegación y cambios de captura.
- Validación local del cambio: 151 pruebas JVM/Robolectric aprobadas, 0 fallos; compilación release e integridad ZIP correctas. Las 7 pruebas Python del avatar también aprobaron. No se repitió la instrumentación Android después de este último cambio de estado visual.
- Se conservaron en `evidence/` los hechos comprobables de las respuestas guardadas de Nemotron y Diego, el hash del WAV y la incompatibilidad entre certificados. Los archivos no incluyen claves ni audio privado. La síntesis guardada se inspeccionó como PCM mono de 16 bits a 22.050 Hz.
- TRELLIS mostraba un enlace a un GLB exportado. Dos intentos con los controles documentados del navegador agotaron el tiempo, y la recuperación HTTP devolvió 403. No se obtuvo ni se validó ese GLB localmente; tampoco se verificaron su esqueleto, expresiones o fidelidad visual.
- ADB no muestra dispositivos conectados. No se verificaron la conversación física Z02, el consentimiento/lectura Gmail dentro de la aplicación ni el certificado de la aplicación instalada. La configuración OAuth realizada en la sesión anterior no equivale a esas pruebas.

El control de entrega continúa en `release_ready_12_of_12: false`. La APK compilada en este tramo es interna, sin firma y sin configuración NVIDIA personal; no se presenta como entrega final.

## Cambios implementados

- Las respuestas largas se dividen por oraciones para sintetizar todo el texto con Diego y reproducirlo secuencialmente por Z02.
- Al salir de la actividad se cancelan las conexiones HTTP, el reconocimiento gRPC, la captura del estuche y la cola de audio. Los resultados de peticiones anteriores no reactivan el micrófono.
- El reproductor libera el audio antes de iniciar la siguiente escucha.
- La voz se valida por identificador exacto en el catálogo oficial. Una respuesta de Nemotron cortada por el límite de tokens se informa como incompleta.
- Las transcripciones conservan todos los segmentos reconocidos.
- La captura Android permitió detectar que todos los triángulos del robot estaban invertidos. Se corrigió la orientación de las superficies y se agregó una comprobación geométrica que rechaza normales opuestas al frente de cada triángulo.
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
