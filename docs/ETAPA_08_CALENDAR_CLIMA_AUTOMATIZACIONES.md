# Etapa 8/12 — Cuenta Gmail vinculada, GPS, clima y automatizaciones

## Diseño funcional confirmado
- La persona vincula desde GORO la cuenta Google/Gmail que quiere usar mediante el selector nativo de cuentas Android.
- GORO guarda localmente el correo seleccionado y limita las operaciones CalendarContract a los calendarios de esa cuenta, no a todas las cuentas del dispositivo.
- El usuario concede READ_CALENDAR y WRITE_CALENDAR mediante permisos Android.
- El clima usa ubicación actual del dispositivo: GPS_PROVIDER con permiso preciso; NETWORK_PROVIDER como alternativa cuando solo se concede ubicación aproximada o GPS no está disponible.
- La ubicación se solicita en primer plano al pedir el clima; no se pide permiso de ubicación en segundo plano.
- Open-Meteo obtiene clima actual y pronóstico de tres días sin API key.
- WorkManager entrega recordatorios no exactos y persistentes.

## Implementación incorporada
- AccountManager.newChooseAccountIntent limitado a accountType com.google.
- Cuenta seleccionada guardada en SharedPreferences de GORO.
- CalendarContract.Calendars filtrado por ACCOUNT_NAME y ACCOUNT_TYPE.
- Lecturas por CalendarContract.Instances para mostrar las ocurrencias de eventos dentro del rango.
- Escrituras únicamente en el calendario principal editable de la cuenta elegida.
- Edición y eliminación validan que el evento pertenezca a esa cuenta; series recurrentes se rechazan para evitar borrar o alterar toda la serie.
- DeviceLocationProvider usa LocationManager.getCurrentLocation desde API 30 y requestSingleUpdate en Android 8/9; GPS primero, red como fallback.
- Botones y comandos de voz para clima actual por GPS y pronóstico de tres días.
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

## Estado
La vinculación de cuenta Gmail y el clima GPS ya están conectados al flujo de la aplicación. Falta que CI valide esta revisión y completar la integración visual con la interfaz 3D aprobada antes de la APK final. No se requiere instalación ni prueba manual intermedia.
