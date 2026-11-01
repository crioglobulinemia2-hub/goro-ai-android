# Etapa 7/12 — Herramientas Android

## Funciones implementadas
- Parser determinista en español para alarmas con hora/minutos.
- Parser de temporizadores en segundos, minutos, horas y abreviaturas h/hs.
- Validación de rango 00:00–23:59 y duración de 1 segundo a 24 horas.
- Protección ante números desbordados y entradas inválidas.
- Órdenes por voz para abrir las listas nativas de alarmas y temporizadores.
- Alarmas y temporizadores enviados al reloj Android mediante intents estándar.
- Calculadora aritmética básica con protección de división por cero.

## Hallazgos de documentación y comunidad aplicados
1. Android AlarmClock API exige `com.android.alarm.permission.SET_ALARM` para `ACTION_SET_TIMER`; permiso incorporado al manifest.
   Fuente: https://developer.android.com/reference/android/provider/AlarmClock
2. Un error habitual de `ACTION_SET_ALARM` / `ACTION_SET_TIMER` es que el sistema no encuentre una app receptora cuando falta SET_ALARM.
   Reporte: https://stackoverflow.com/questions/63649575/alarmclock-intent-says-no-apps-can-perform-this-action
3. Se usa `Context.startActivity()` para que el usuario pueda elegir el reloj instalado, conforme a la recomendación oficial.
4. Se añadió manejo de ausencia de aplicación receptora y se evita decir que quedó guardado si solo se abrió la interfaz.

## Pruebas automáticas
- Parser de alarmas: hora/minutos, límites y entradas inválidas.
- Parser de temporizadores: segundos/minutos/horas, h/hs, límites y overflow.
- Compilación y validación de manifiesto en GitHub Actions.

## Límite explícito
La API pública abre la aplicación de reloj; la app externa no recibe una confirmación universal de que el usuario guardó o de que la alarma sonará. Por eso GORO informa que abrió la interfaz y pide confirmar el guardado. La prueba de disparo físico y comportamiento con pantalla bloqueada requiere un teléfono real y la aplicación de reloj concreta.

## Referencias
- Android AlarmClock: https://developer.android.com/reference/android/provider/AlarmClock
- Android alarms guide: https://developer.android.com/develop/background-work/services/alarms
- Stack Overflow — missing SET_ALARM permission: https://stackoverflow.com/questions/63649575/alarmclock-intent-says-no-apps-can-perform-this-action

**Estado:** implementación y regresión automática pendientes de resultado del run 41353665109. La verificación física queda documentada como requisito de aceptación en dispositivo.
