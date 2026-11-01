# Etapa 8 — Calendario nativo del dispositivo

La integración usa CalendarContract. No necesita OAuth propio ni backend. Accede a las cuentas/calendarios que Android ya sincroniza en el dispositivo, si el usuario concede READ_CALENDAR y WRITE_CALENDAR.

Seguridad:
- Solo calendarios visibles, sincronizados y con nivel de acceso contributor o superior para crear.
- Listados excluyen eventos recurrentes (RRULE) para evitar tratar series como eventos únicos.
- Update/delete vuelven a leer el evento y rechazan series recurrentes.
- Cada operación de escritura exige confirmación verbal previa en MainActivity.
- La API solo reporta éxito si ContentResolver devuelve URI/filas afectadas.

Fuentes: https://developer.android.com/reference/android/provider/CalendarContract ; https://developer.android.com/guide/topics/providers/calendar-provider

No se solicita OAuth ni se guarda ningún token de Google en la APK.
