# GORO AI — Referencia visual aprobada y criterios de aceptación

## Referencia vinculante confirmada el 4 de noviembre de 2026

El propietario volvió a adjuntar **la imagen adjunta por el propietario (image.png)** y pidió: «ESTE ES EL DISEÑO ACORDADO QUIERO 100X100 IGUAL».

Prevalece esta referencia sobre las variaciones posteriores de teléfonos con visor negro y ojos dibujados como sonrisa. El personaje aprobado tiene cara blanca perlada, grandes ojos ópticos azules redondos, boca mecánica visible, paneles grafito en la cabeza y el cuello, manos articuladas y emblema azul hexagonal en el pecho.

La vista principal muestra al personaje de cintura hacia arriba sobre un escritorio, con ambiente cálido y luz diurna, tarjetas compactas translúcidas a la derecha, burbuja clara junto al rostro y controles inferiores discretos. No debe tener una pared de botones ni tarjetas azules opacas sobredimensionadas. Los datos y estados de conexión de la referencia son ilustrativos: se deben sustituir por datos reales sin simular conexiones.

La compilación del commit 52a81ee46fb4dc1733ce8baaa42584cd9915bc17 pasó sus pruebas, pero **NO cumple la identidad del robot ni la composición aprobada**. No es una entrega final. La imagen reconstruida para modelado tampoco acredita un modelo 3D funcional ni aceptación visual. La conversión de esa imagen mediante Microsoft TRELLIS en Hugging Face quedó bloqueada por la revisión automática: requiere autorización para enviar el recurso visual a ese servicio externo. No se debe reintentar la transferencia sin esa autorización.

La imagen aportada por el usuario el 1 de noviembre de 2026 es la referencia visual vinculante para la interfaz y la animación de GORO. No debe sustituirse por un chat genérico, un formulario simple ni una pantalla monocromática oscura.

## Composición de inicio
- Robot humanoide 3D de cuerpo visible, proporciones y articulaciones mecánicas tipo G1, con ojos y boca expresivos.
- El robot ocupa el centro visual de la pantalla, con ambiente realista cálido y profundidad.
- Panel de navegación lateral: Inicio, Agenda, Alarmas, Clima, Memoria, Dispositivos, Automatizar y Ajustes.
- Tarjetas de clima local, agenda del día y dispositivos conectados.
- Burbuja de diálogo contextual del robot.
- Micrófono y forma de onda visibles; conversación continua, sin botón separado para iniciar y otro para terminar cada frase.
- Accesos rápidos de teclado, cámara, brillo/tema y audio.
- Módulo de próximos eventos y documentos recientes en vistas complementarias.

## Robot y animación
- No usar secuencias rígidas repetitivas ni sprites básicos.
- Expresiones faciales diferenciadas: neutral, hablando, sonriendo, sorprendido y pensando.
- Ojos con mirada orientable y parpadeo natural.
- Boca sincronizada con fonemas/visemas del audio TTS, no una animación aleatoria sin relación con el habla.
- Brazos, manos, cabeza y torso con gestos articulados, con transiciones suaves y variación contextual.
- El robot mira hacia el elemento o zona de la interfaz que está explicando.
- Debe poder señalar agenda, clima, mapa o documentos al mencionarlos.

## Modos de interfaz
- Modo claro cálido y profesional, como la pantalla principal de referencia.
- Modo oscuro tecnológico, con contraste y jerarquía visual; no un fondo negro uniforme ni un solo color.
- Los dos modos conservan composición, robot, tarjetas y jerarquía de navegación.
- La adaptación a tablet debe aprovechar el ancho disponible y no estirar una UI móvil.

## Criterios de rechazo
- Chat genérico con avatar estático.
- Un solo botón para hablar que obliga a iniciar y finalizar cada turno manualmente.
- Robot reducido a una imagen inmóvil.
- Animación repetitiva sin sincronía con la voz.
- Fondo plano monocromático.
- Tarjetas sin datos funcionales reales.
- Mostrar un estado "conectado" si no existe conexión real.

## Separación de etapas
La etapa 8 implementa servicios de cuenta Google seleccionada, Calendar Provider, GPS/clima y recordatorios. La reproducción visual de esta referencia y la animación 3D articulada son criterios de la interfaz final y no deben declararse implementados por el mero hecho de que compile el backend funcional.
