# Integración ZNP Z01 / Z02 / N02 — contrato técnico

## Objetivo
GORO debe permitir seleccionar cada modelo, intentar el transporte compatible, mostrar la animación apropiada y habilitar micrófono/audio/visor solamente cuando cada capacidad esté confirmada.

## Flujo implementado en la rama de reparación
1. El usuario elige Z01, Z02 o N02 en Dispositivos.
2. El escáner JieLi usa descubrimiento amplio y no descarta anuncios solo porque Android todavía no tenga un nombre en caché.
3. La aplicación compara el nombre local completo o abreviado del anuncio con el modelo seleccionado. Esto identifica un candidato, no prueba su identidad.
4. Se intenta abrir el enlace BLE/RCSP y se espera la autenticación del SDK con tiempo límite.
5. Tras autenticar, se leen el mensaje de identificación/chipset, la información de pantalla, la memoria y el brillo. Estas operaciones de identificación son de lectura.
6. Android solicita el perfil de audio y la aplicación espera hasta 45 segundos a que la salida Bluetooth exacta del dispositivo aparezca en AudioManager. La conversación manos libres no se habilita antes de esa confirmación.
7. El micrófono RCSP, la reproducción Bluetooth y el visor se reportan como estados independientes.
8. El usuario puede desconectar el dispositivo desde GORO. Cambiar de modelo no reutiliza silenciosamente una sesión anterior.

## SDK y límites
- SDK JieLi Android RCSP fijado en CI: https://github.com/Jieli-Tech/Android-JL_Bluetooth
- Operaciones de estuche: https://doc.zh-jieli.com/SDKWiki/master/Jieli-Tech/Android-JL_Bluetooth/device-functions/charging-case.html
- La presencia del SDK no demuestra que cada variante Z01/Z02/N02 utilice el mismo chipset o protocolo.
- No se envían órdenes de pantalla, luz ni transferencia de recursos antes de confirmar autenticación y un chipset soportado (JieLi 701N/707N).
- El inventario GATT alternativo es pasivo: no escribe características ni ejecuta OTA.
- Una lectura de UUID o el nombre anunciado no demuestran que un servicio sea micrófono, pantalla o táctil.

## Animación de visor
- 320×172: GIF horizontal construido desde los fragmentos znp_faces_00...17; el validador CI comprueba cabecera, resolución, frames y trailer.
- 240×296: GIF vertical znp_faces_240x296.b64; el validador CI comprueba la misma cantidad de frames que el horizontal.
- En ejecución, ZnpScreenAsset elige por resolución real reportada por el firmware. No fuerza una resolución desconocida ni envía el GIF horizontal a una pantalla vertical.
- La animación del visor es 2D/GIF. El avatar humanoide Filament de la pantalla Android es independiente.

## Niveles de compatibilidad por modelo
- 0: no detectado.
- 1: candidato BLE detectado y servicios GATT leídos.
- 2: RCSP autenticado y chipset identificado.
- 3: audio de entrada (micrófono) probado y salida Bluetooth exacta confirmada.
- 4: transferencia de recurso y activación del GIF confirmadas en el visor.
- 5: gestos/táctil y animación sincronizada comprobados en hardware real.

## Criterio de aceptación
Registrar por separado Z01, Z02 y N02. No copiar la aprobación de un modelo a otro. La CI y el emulador validan código y recursos, pero no reemplazan una prueba física de cada dispositivo.
