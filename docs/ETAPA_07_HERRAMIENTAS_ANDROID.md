# Etapa 7/12 — Herramientas Android

## Funciones implementadas
- Parser determinista en español para alarmas con hora/minutos.
- Parser de temporizadores en segundos, minutos, horas y abreviaturas h/hs.
- Validación de rango 00:00–23:59 y duración de 1 segundo a 24 horas.
- Protección ante números desbordados y entradas inválidas.
- Órdenes por voz para abrir las listas nativas de alarmas y temporizadores.
- Alarmas y temporizadores enviados al reloj Android mediante intents estándar.
- Calculadora aritmética básica con protección de división por cero.
- Mensajes de voz que no afirman que la alarma fue guardada cuando solo se abrió la interfaz del reloj.

## Investigación aplicada
- Permiso SET_ALARM incorporado para ACTION_SET_TIMER.
- AlarmClock intents encapsulados en AlarmClockIntents para construcción y verificación determinista.
- Pruebas Android Intent ejecutadas con Robolectric, no con stubs Android incompletos.
- Reporte comunitario sobre el problema de intents de reloj sin permiso: https://stackoverflow.com/questions/63649575/alarmclock-intent-says-no-apps-can-perform-this-action
- Referencia oficial: https://developer.android.com/reference/android/provider/AlarmClock

## Evidencia automatizada
GitHub Actions **41354060055 — SUCCESS**.
- Static security gate: success.
- Unit tests and build: success.
- Android memory persistence tests (Robolectric): success.
- Manifest and APK integrity validation: success.
- Artifact GoroAI-debug-apk generado (30,579 bytes).

La suite incluye pruebas de parser, rangos, overflow, acciones AlarmClock y construcción de intents de alarma/temporizador/listados.

## Límites conocidos
Android delega el guardado y disparo al proveedor de reloj del dispositivo. La API externa no ofrece confirmación universal de que el usuario guardó una alarma ni de que el proveedor la disparará. GORO informa que abrió el reloj, sin declarar falsamente que la alarma ya está activa.
No se ejecutó una prueba física en teléfono, conforme a la regla del proyecto de no solicitar instalaciones ni pruebas intermedias. La verificación de proveedor de reloj real queda para la validación integral final sin intervención del usuario.

## Estado
**ETAPA 7/12 APROBADA para implementación, compilación, pruebas automatizadas y validación del APK.**
Evidencia: https://github.com/crioglobulinemia2-hub/goro-ai-android/actions/runs/41354060055
