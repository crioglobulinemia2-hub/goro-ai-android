# Etapa 8/12 — Cuenta Gmail vinculada, GPS, clima y automatizaciones

## Diseño funcional confirmado
- La persona vincula desde GORO la cuenta Google/Gmail que quiere usar mediante el selector nativo de cuentas Android.
- GORO guarda localmente el correo seleccionado y limita las operaciones CalendarContract a los calendarios de esa cuenta, no a todas las cuentas del dispositivo.
- El usuario concede READ_CALENDAR y WRITE_CALENDAR mediante permisos Android.
- El clima usa ubicación actual del dispositivo: GPS_PROVIDER con permiso preciso; NETWORK_PROVIDER como alternativa cuando solo se concede ubicación aproximada o GPS no está disponible.
- La ubicación se solicita en primer plano al pedir el clima; no se pide permiso de ubicación en segundo plano.
- Open-Meteo obtiene clima actual y pronóstico de tres días sin API key.
- WorkManager entrega recordatorios únicos y recurrentes no exactos y persistentes.

## Implementación incorporada
- AccountManager.newChooseAccountIntent limitado a accountType com.google.
- Cuenta seleccionada guardada en SharedPreferences de GORO, con control para cambiarla y desvincularla sin borrar eventos.
- Botón separado para volver a conceder permisos de calendario si se rechazaron anteriormente.
- CalendarContract.Calendars filtrado por ACCOUNT_NAME y ACCOUNT_TYPE.
- Lecturas por CalendarContract.Instances para mostrar las ocurrencias de eventos dentro del rango.
- Escrituras únicamente en el calendario principal editable de la cuenta elegida.
- Edición y eliminación validan que el evento pertenezca a esa cuenta; series recurrentes se rechazan para evitar borrar o alterar toda la serie.
- DeviceLocationProvider usa LocationManager.getCurrentLocation desde API 30 y requestSingleUpdate en Android 8/9; GPS primero, red como fallback.
- Botones y comandos de voz para clima actual por GPS y pronóstico de tres días; las órdenes “clima” y “pronóstico” usan la ubicación del dispositivo sin pedir ciudad.
- Recordatorios recurrentes por voz cada N minutos, horas o días, con cancelación por texto del recordatorio.
- No se requiere OAuth client ID, servidor propio ni client_secret para CalendarContract.

## Permisos
- READ_CALENDAR y WRITE_CALENDAR: autorización runtime para leer y modificar la cuenta elegida.
- ACCESS_FINE_LOCATION y ACCESS_COARSE_LOCATION: autorización runtime al solicitar clima local.
- No se solicita GET_ACCOUNTS: el selector AccountManager devuelve la cuenta elegida y la hace visible para la app.
- No se solicita ACCESS_BACKGROUND_LOCATION: el GPS solo se usa cuando la persona pide clima en primer plano.

## Limitaciones operativas
- La cuenta Google debe estar agregada al dispositivo y Calendar sincronizado. Si no tiene un calendario visible/editable o la sincronización está desactivada, GORO informa el motivo y no crea eventos en otra cuenta.
- El proveedor de ubicación requiere que los servicios de ubicación del dispositivo estén activados; GPS es preferido y la ubicación de red es alternativa.
- WorkManager no garantiza una hora exacta de notificación.

## Evidencia técnica
- Android AccountManager account picker: https://developer.android.com/reference/android/accounts/AccountManager
- Android Calendar Provider: https://developer.android.com/guide/topics/providers/calendar-provider
- Android LocationManager current location: https://developer.android.com/reference/android/location/LocationManager
- Open-Meteo: https://open-meteo.com/en/docs
- NVIDIA Nemotron review: https://agent.tinyfish.ai/runs/bdb3db10-45c2-4a29-86c9-51dbf90972bd

## Estado de aceptación — ETAPA 8 COMPLETADA
**Aprobada para implementación de software y validación automatizada.**

Evidencia CI: GitHub Actions run `41359622615` — `SUCCESS`, commit `e00c48712f69f2d023ec8ef6ef2758fad46c6aea`.
- Static security gate: SUCCESS.
- Unit tests and Android build: SUCCESS.
- Android memory persistence tests (Robolectric): SUCCESS.
- Manifest and APK validation: SUCCESS.
- Artifact `GoroAI-debug-apk` generado por el run: 2,698,292 bytes.

La suite automatizada valida persistencia de la cuenta Gmail seleccionada y su desvinculación, rechazo de permisos de ubicación ausentes, validación de coordenadas y rango de pronóstico, y programación de recordatorios únicos/recurrentes. La compilación e integridad del APK pasan.

**Límites no ocultos:** CI no puede demostrar sincronización real de una cuenta Google en un teléfono ni recepción GPS real, porque no se ejecutó un dispositivo físico. La función queda implementada y condicionada a que el teléfono tenga la cuenta Google agregada, Calendar sincronizado y permisos concedidos; el clima requiere ubicación del dispositivo habilitada y permiso de ubicación. No se solicitó ninguna prueba ni instalación al usuario.

La referencia visual 3D aprobada permanece como requisito obligatorio de la etapa de interfaz y APK final; la pantalla de depuración actual no se presenta como interfaz final.
