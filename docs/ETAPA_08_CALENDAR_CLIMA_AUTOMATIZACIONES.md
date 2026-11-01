# Etapa 8/12 — Google Calendar, clima y automatizaciones

## Implementado
- Modelo de eventos con zona horaria.
- Contrato CalendarGateway para consultar, crear, modificar y eliminar eventos.
- Adaptador HTTPS SecureCalendarProxy. No incluye claves ni refresh tokens en la APK.
- Servicio meteorológico Open-Meteo por HTTPS, sin clave API, con validación de coordenadas y respuesta actual (temperatura, viento, código y hora).
- Pruebas unitarias del modelo de eventos y validación de zona horaria.

## Fuentes y decisión
- Google recomienda Credential Manager para iniciar sesión Android y prohíbe usar WebView embebido para OAuth: https://developers.google.com/identity/siwg/best-practices
- Google Calendar API events list/insert/update/delete: https://developers.google.com/workspace/calendar/api/v3/reference/events
- Open-Meteo ofrece endpoint HTTPS sin API key para pronóstico, con uso gratuito no comercial y atribución CC BY 4.0: https://open-meteo.com/en/docs
- El proxy debe validar identidad, OAuth scope calendar.events y ownership de eventos; no aceptar user IDs arbitrarios del cliente.

## Contrato requerido para el proxy
- GET /calendar/events?timeMin=&timeMax=&q= -> {items:[Google Calendar event objects]}
- POST /calendar/events -> Google Calendar event object
- PATCH /calendar/events/{id} -> Google Calendar event object
- DELETE /calendar/events/{id} -> 204
- OAuth tokens/refresh tokens se guardan exclusivamente en el servidor.

## Automatizaciones
Las acciones de calendario requieren confirmación explícita antes de crear, modificar o borrar. Las tareas locales recurrentes deben persistir con WorkManager y mostrar notificación; implementación de scheduler aún pendiente.

## Pendiente de aceptación
- [ ] Implementar proxy OAuth real y desplegarlo.
- [ ] Integrar Google Credential Manager y consentimiento del usuario.
- [ ] Conectar calendario a la sesión Nemotron y dispatcher.
- [ ] Implementar WorkManager para recordatorios/automatizaciones.
- [ ] Pruebas de integración contra un calendario de prueba y mock server.
- [ ] Compilar y ejecutar regresión CI.

**Estado: etapa 8 en desarrollo, no aprobada.**
