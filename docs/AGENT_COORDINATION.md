# GORO IA — protocolo de coordinación de agentes

## Propósito
Gemini, Nemotron y el integrador no comparten memoria, archivos ni decisiones automáticamente. La coordinación debe ocurrir mediante un contrato común y una fuente de verdad versionada. No se permiten tres conversaciones independientes que produzcan cambios incompatibles.

## Fuente de verdad
- Repositorio: `crioglobulinemia2-hub/goro-ai-android`
- Rama de integración: `work/goro-repair-20261105`
- PR de integración: #5
- Cada revisión debe declarar el SHA exacto que inspeccionó.
- Ningún agente escribe directamente en `main`; entrega hallazgos o un patch en su propia rama.
- El integrador es responsable de resolver conflictos, aplicar cambios y ejecutar CI.

## Responsabilidades

### Gemini — auditoría visual, Android y avatar
Revisar:
1. Capturas Android de teléfono/tablet contra la referencia GORO aprobada.
2. GLB, jerarquía de huesos, morph targets y nombres/duración de los seis clips.
3. Ciclo de vida de Filament, TextureView, Vulkan y render loop.
4. Sincronización de gestos, expresiones, parpadeo y boca con el audio.
5. Layout y visibilidad del avatar en portrait, landscape y ventanas divididas.

No declarar aprobado el robot procedural. El GLB actualmente empaquetado fue identificado como desarrollo procedural; no equivale al modelo humanoide G1 aprobado.

### Nemotron — auditoría de voz, red y dispositivos
Revisar:
1. Estado de credenciales y conexión NVIDIA NIM.
2. Nemotron streaming, Magpie Diego WAV y Parakeet RNNT.
3. Fallback de salida por Android cuando A2DP del ZNP no está disponible.
4. Estados independientes BLE, RCSP, micrófono, audio y pantalla.
5. Detección y conexión de Z01, Z02 y N02 sin asumir que los tres usan el mismo chipset.

No enviar comandos Bluetooth de escritura, OTA o pantalla a un dispositivo sin identidad, chipset y protocolo confirmados. No usar credenciales personales en prompts, issues o logs.

### Integrador — ChatGPT / mantenedor
1. Entregar a ambos agentes el mismo SHA y el mismo contrato de aceptación.
2. Recibir los informes en el formato común.
3. Contrastar los hallazgos con código, logs, APK y pruebas reproducibles.
4. Aplicar cambios en la rama de integración.
5. Ejecutar pruebas JVM, validación de GIF/GLB, build y emulator smoke.
6. No marcar una función como operativa solo por compilar.

## Formato obligatorio de revisión
Cada agente debe entregar un archivo `docs/agent-reviews/<agent>-<sha-corto>.json` con esta estructura:

```json
{
  "agent": "gemini | nemotron",
  "commit_sha": "SHA exacto revisado",
  "summary": "resultado breve",
  "findings": [
    {
      "severity": "blocker | high | medium | low",
      "file": "ruta del archivo",
      "lines": "líneas exactas",
      "evidence": "log, captura o comportamiento observado",
      "reproduction": "pasos reproducibles",
      "proposed_fix": "cambio concreto",
      "test": "prueba que debe pasar"
    }
  ],
  "not_verified": ["pruebas que no se pudieron realizar"]
}
```

Un hallazgo sin archivo/líneas o sin evidencia se trata como hipótesis, no como corrección confirmada. No se copian secretos, tokens, APK personales ni datos de cuenta a los informes.

## Arquitectura de coordinación recomendada
1. El integrador crea un snapshot de revisión (SHA + capturas + logs + lista de fallos).
2. Gemini y Nemotron reciben ese mismo snapshot, pero tareas distintas.
3. Ambos devuelven informes estructurados; no se pasan instrucciones contradictorias directamente entre modelos.
4. El integrador compara los informes y solicita aclaración solo sobre contradicciones concretas.
5. El integrador aplica una única solución, añade una prueba de regresión y ejecuta CI.
6. Si la CI falla, se devuelve el log exacto al agente responsable; no se vuelve a pedir una revisión genérica.

## Restricción de ejecución y coste
La coordinación por API requiere credenciales separadas de cada proveedor y un orquestador que envíe el contexto y ejecute el ciclo de herramientas. Este repositorio no debe llamar automáticamente a modelos externos en cada build: evita costes, exposición de secretos y resultados no reproducibles. La CI valida el código sin consumir cuotas de Gemini o NVIDIA.

## Criterios de aceptación GORO
- Avatar visible y con movimiento perceptible en Android real; GLB coincidente con el diseño aprobado.
- Audio de respuesta audible por altavoz Android aunque el ZNP no tenga A2DP listo.
- Diego solo se declara listo después de probar WAV y dispositivo de salida.
- Conversación Nemotron y reconocimiento Parakeet autenticados con la clave personal importada de forma cifrada.
- Z01, Z02 y N02 verificados por separado en hardware físico.
- Animación del visor confirmada después de leer la resolución real.
- Gmail OAuth y lectura real comprobados.
- Firma y paquete de instalación verificados sin borrar datos.
- CI verde y evidencia física adjunta antes de llamar a la APK «final».

## Documentación oficial de APIs
- NVIDIA NIM LLM API: https://docs.api.nvidia.com/nim/reference/llm-apis
- NVIDIA NIM function calling: https://docs.nvidia.com/nim/large-language-models/latest/function-calling.html
- Gemini function calling: https://ai.google.dev/gemini-api/docs/function-calling
- Gemini tools: https://ai.google.dev/gemini-api/docs/tools
