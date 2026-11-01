# Integración ZNP N02 — contrato técnico

## Objetivo
GORO AI Android debe operar de forma autónoma y sincronizar una representación visual con el visor del estuche ZNP N02 cuando se demuestre compatibilidad.

## Evidencia pública
- SDK oficial JieLi Android RCSP: https://github.com/Jieli-Tech/Android-JL_Bluetooth
- Operaciones de estuche: https://doc.zh-jieli.com/SDKWiki/master/Jieli-Tech/Android-JL_Bluetooth/device-functions/charging-case.html
- La existencia del SDK NO confirma que el N02 utilice RCSP ni que acepte animación continua.

## Política de seguridad
1. Descubrimiento BLE únicamente con permiso del usuario.
2. Lectura de servicios/características sin escribir valores.
3. No modificar firmware, no usar OTA, no ejecutar comandos RCSP hasta identificar chipset y autenticación.
4. No almacenar direcciones Bluetooth en logs públicos.
5. Mantener funciones Android independientes del estuche.

## Niveles de compatibilidad
- 0: sin conexión verificada.
- 1: dispositivo identificado y servicios BLE leídos.
- 2: autenticación/protocolo documentados y verificados.
- 3: mensajes/recursos enviados al visor con confirmación.
- 4: interacción táctil bidireccional verificada.
- 5: animación sincronizada evaluada en hardware real.

## Criterios para GORO visual
En Android, render 3D esquelético con control de boca, ojos, cabeza, manos y brazos.
En visor, determinar resolución, profundidad de color, formatos de recursos, tasa de actualización, memoria y limitaciones antes de elegir entre imágenes animadas, sprites o primitivas.
No afirmar que el visor admite 3D.
