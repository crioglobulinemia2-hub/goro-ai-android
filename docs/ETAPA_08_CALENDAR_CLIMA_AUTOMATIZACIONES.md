# Etapa 8/12 — Google Calendar, clima y automatizaciones

## Revisión técnica externa
Revisión ejecutada en NVIDIA Nemotron-3.5-super-vl-120b-a12b Playground:
https://agent.tinyfish.ai/runs/45b928cd-70cd-4635-9e5c-a195a3e72bcd

Decisión de arquitectura: cliente Android público con OAuth Authorization Code + PKCE y REST directo a Google Calendar; sin servidor propio, sin client_secret embebido y sin backend proxy. Open-Meteo se usa como proveedor meteorológico sin API key. WorkManager se reserva para recordatorios no exactos.

## Implementado
- AppAuth 0.11.1 para autorización Google mediante navegador/Custom Tabs y PKCE S256.
- GoogleTokenStore con EncryptedSharedPreferences para access/refresh tokens.
- GoogleCalendarTokenProvider con refresh token grant y renovación antes de expirar.
- GoogleCalendarRestGateway con OkHttp y endpoints Calendar API v3 directos.
- Calendario: consulta por día/semana, creación con confirmación explícita, eliminación con confirmación explícita.
- Zona horaria local IANA + offset RFC3339 al construir eventos.
- WeatherService: geocoding Open-Meteo + condiciones actuales por coordenadas.
- Recordatorios relativos y recurrentes con WorkManager y NotificationChannel.
- Solicitud de permiso POST_NOTIFICATIONS disponible en UI.
- MainActivity conserva say/runCommand; clima y operaciones de calendario están en el dispatcher de voz.
- Sin client_secret ni API key hardcodeados. No hay proxy remoto.

## Configuración externa necesaria para habilitar Google Calendar real
Es registro OAuth externo que no puede inferirse ni inventarse.
1. En Google Cloud Console, crear/seleccionar proyecto y habilitar Google Calendar API.
2. Configurar OAuth consent screen y añadir cuenta de prueba si la app permanece en Testing.
3. Crear OAuth Client ID tipo Android con package ar.goro.ai y SHA-1 del certificado de firma que se usará en la APK final.
4. Pasar el client ID público al build como propiedad Gradle GOOGLE_OAUTH_CLIENT_ID; el esquema redirect se deriva del prefijo com.googleusercontent.apps.<client-prefix>.
5. Autorizar scope mínimo https://www.googleapis.com/auth/calendar.events.
No se necesita client_secret en Android. No se debe reutilizar un OAuth client ID de tipo Web.

## Límites y honestidad de integración
- Hasta configurar client ID Android + SHA-1 en Google Console, el botón de conexión informa que Calendar requiere configuración y no se puede completar consentimiento real.
- No se afirma conexión real con Google Calendar: no se ha concedido consentimiento OAuth a esta app.
- Las pruebas CI no invocan cuentas reales ni Open-Meteo; verifican compilación, modelos, parser, seguridad y contratos locales.
- WorkManager no garantiza ejecución exacta al minuto. Para alarmas exactas usar AlarmManager con permisos/políticas Android; etapa 7 delega alarmas al reloj nativo.
- Android 13+ requiere POST_NOTIFICATIONS habilitado por la persona; GORO muestra la acción de activación.
- Las operaciones de calendario que modifican datos requieren confirmación verbal "confirmar"; "cancelar" descarta la operación pendiente.

## Fuentes
- AppAuth AuthorizationService: https://github.com/openid/AppAuth-Android/blob/master/library/java/net/openid/appauth/AuthorizationService.java
- AppAuth RedirectUriReceiverActivity: https://github.com/openid/AppAuth-Android/blob/master/library/java/net/openid/appauth/RedirectUriReceiverActivity.java
- AppAuth Google setup: https://github.com/openid/AppAuth-Android/blob/master/app/README-Google.md
- Google Calendar API events: https://developers.google.com/workspace/calendar/api/v3/reference/events
- Open-Meteo: https://open-meteo.com/en/docs
- Android WorkManager: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started
- Android notification permission: https://developer.android.com/develop/ui/views/notifications/notification-permission

## Estado de aceptación
Implementación de código incorporada. La conexión de Google Calendar queda bloqueada por registro externo de OAuth (client ID + SHA-1 + consentimiento), no por falta de backend ni por pruebas del usuario. CI requerido para aceptar el último commit. No se requiere instalación ni prueba manual intermedia.
