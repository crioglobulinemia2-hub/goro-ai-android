# Investigación técnica aplicada — voz y herramientas Android

## Fuentes primarias y reportes de desarrolladores
- Android SpeechRecognizer API: https://developer.android.com/reference/android/speech/SpeechRecognizer
  Android advierte expresamente que SpeechRecognizer no está diseñado para reconocimiento continuo prolongado por consumo de batería y ancho de banda; exige RECORD_AUDIO y destroy().
- Android AlarmClock API: https://developer.android.com/reference/android/provider/AlarmClock
  ACTION_SET_ALARM y ACTION_SET_TIMER son intents estándar. ACTION_SET_TIMER requiere com.android.alarm.permission.SET_ALARM. Los intents abren el proveedor de reloj y no constituyen confirmación de que la persona guardó la alarma si se muestra UI.
- AOSP RecognitionService: https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/speech/RecognitionService.java
  El servicio rechaza sesiones concurrentes con ERROR_RECOGNIZER_BUSY.
- Reporte reciente de STT bloqueado en Android: https://github.com/csdcorp/speech_to_text/issues/651
  Describe bucles de recuperación y errores permanentes ERROR_SPEECH_TIMEOUT / ERROR_RECOGNIZER_BUSY.
- Reporte de reinicio de sesión SpeechRecognizer: https://github.com/capacitor-community/speech-recognition/issues/127
  Documenta fallos al reiniciar una sesión con resultados parciales y necesidad de limpiar correctamente el reconocedor.
- Alternativa streaming Vosk documentada en proyecto Android: https://github.com/oierreaemme/notari/blob/main/docs/decisions/0018-continuous-streaming-asr-vosk.md
  Explica por qué el modelo por segmentos de SpeechRecognizer pierde palabras al intentar simular streaming.

## Cambios aplicados
1. Se agregó permiso com.android.alarm.permission.SET_ALARM para el intent de temporizador.
2. Se corrigió el parser de duración: reconoce h y hs, evita desbordamiento numérico y rechaza duraciones superiores a 24 horas.
3. Se mantiene la distinción de confirmación: abrir el reloj no equivale a confirmar el guardado por parte del usuario.

## Decisión de arquitectura de voz
No vender SpeechRecognizer como streaming real. Para la experiencia conversacional final, evaluar Vosk u otro motor streaming local/online con detección VAD y audio PCM; SpeechRecognizer queda como fallback de comandos cortos. Las sesiones deben serializar start/stop/cancel/destroy, no reiniciarse cuando el motor sigue ocupado. El TTS debe suspender el ASR y reanudarlo una sola vez al completar la locución.

## Estado
Hallazgos documentados y dos correcciones Android aplicadas. Requiere nueva compilación y pruebas de regresión. No se declara resuelta la conversación continua ni la etapa 7.
