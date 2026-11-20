# Firma y actualización de GORO

Comprobación del 4 de noviembre de 2026. No se modificó ninguna instalación ni se borraron datos.

| APK comprobada | Paquete | Código de versión | Certificado SHA-256 |
| --- | --- | --- | --- |
| GORO-IA-1.1.0.apk | ar.goro.ai.personal | 3 | 81ed6978c3af615fe6f346354d0c3528bbfe4c080c07a229c9227a94110b2b06 |
| GORO-IA-Recuperacion.apk | ar.goro.ai.recovery | 3 | 8702426c72ebf740f3055daaf0df591feeddcf0f76918e8b5abf5acc2189ec10 |
| GORO-IA-1.2.0-integrada-personal.apk, interna | ar.goro.ai.personal | 4 | 8702426c72ebf740f3055daaf0df591feeddcf0f76918e8b5abf5acc2189ec10 |

La clave privada disponible corresponde al último certificado. Su SHA-1 es `1C:64:B2:E6:DA:A8:18:52:D1:63:C7:AF:46:A1:E5:44:B6:DD:DC:12`. Es la identidad que debe coincidir con el cliente OAuth Android del paquete `ar.goro.ai.personal`.

La APK 1.1.0 está firmada como `C=US, O=Android, CN=Android Debug`. No se encontró su clave entre los archivos locales de GORO, la clave debug local actual ni el respaldo de firma catalogado. El [workflow histórico 41730617209](https://github.com/crioglobulinemia2-hub/goro-ai-android/actions/runs/41730617209) compila `assembleDebug` en un runner de GitHub y publica APK y reportes; no publica el almacén de firma. Esa revisión no demuestra que la clave no exista en otra copia desconocida.

La APK Recuperación tiene la clave disponible, pero otro paquete. Por eso la coincidencia de certificado por sí sola no demuestra que GORO personal pueda actualizarla. ADB no mostró dispositivos conectados durante esta comprobación y sigue sin conocerse cuál de estas instalaciones existe en el equipo del usuario.

## Firma reproducible y control previo

`scripts/sign_owner_apk.py` exige una APK de referencia, compara paquete, código de versión y certificado, y rechaza cualquier incompatibilidad antes de publicar el archivo de salida. No cambia paquetes, instala, desinstala, rota claves ni toca datos. Las contraseñas se leen desde archivos privados; no se pasan como valores de argumentos ni se imprimen.

Ejemplo con rutas privadas externas al repositorio:

```bash
python3 scripts/sign_owner_apk.py \
  --unsigned-apk /private/candidate-unsigned.apk \
  --baseline-apk /private/baseline.apk \
  --build-tools /android-sdk/build-tools/36.0.0 \
  --keystore /private/signing.p12 \
  --store-password-file /private/store-password \
  --key-alias-file /private/key-alias \
  --output /private/candidate-signed.apk
```

`JAVA_HOME` debe apuntar a un JDK. Si la clave tiene contraseña distinta, agregar `--key-password-file`. Omitir `--output` ejecuta solo el control previo. La salida tiene permisos `0600`; se rechaza sobrescribir un archivo existente. Después de firmar se verifican certificado, integridad ZIP, manifiesto y alineación de 16 KiB.

Se ejecutaron cuatro casos sobre APK reales: rechazo de la 1.1.0 por clave distinta, rechazo de Recuperación por paquete distinto, firma correcta contra la APK estable guardada y firma correcta con archivo explícito de contraseña de clave. Los dos rechazos no generaron APK de salida. Las dos firmas produjeron el mismo SHA-256.

El control de entrega incorpora `12 Actualización compatible`. Este control exige evidencia del paquete, versión y certificado instalados y los compara con la APK real. Una compilación firmada o una referencia guardada no cambia `installed_device_certificate_verified` a verdadero.
