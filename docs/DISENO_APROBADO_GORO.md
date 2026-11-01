# GORO AI — Diseño visual y de animación aprobado

Referencia: imagen de diseño compartida repetidamente por el propietario el 1 de noviembre de 2026. Esta referencia es obligatoria para la experiencia visual final, no una ilustración optativa. La imagen es un concepto estático: una implementación funcional debe crear el robot como modelo 3D articulado, no usar el collage como interfaz.

## Personaje
Robot humanoide amistoso estilo G1 personalizado, cuerpo blanco perlado/titanio, detalles grafito, ojos grandes azules de aspecto óptico, boca visible y expresiva, articulaciones de cuello, hombros, codos, muñecas, manos y dedos. Mantener proporciones, acabados, expresividad, iluminación y calidad cinematográfica de la referencia. Ningún icono genérico puede sustituirlo.

## Animación obligatoria
- Modelo 3D real con rig esquelético y animación procedural, no GIF, secuencias de PNG o sprites de Falta Envido.
- Ojos: parpadeos y mirada independientes; seguimiento contextual.
- Boca: sincronización labial con sonido por visemas/blendshapes, transiciones suaves y capacidad de interrupción.
- Cabeza, hombros, codos, muñecas y dedos: gestos dinámicos con control por articulaciones, transiciones continuas y no repetitivas.
- Estados: escucha, piensa, habla, confirma una acción, alerta y espera.
- Objetivo de calidad: articulación coherente en tiempo real y medición de FPS/consumo en Android. No declarar rendimiento sin pruebas.
- No hacer playback de animaciones fijas presentadas como inteligencia contextual.

## Interfaces aprobadas
1. Modo principal claro: ambiente cálido con iluminación natural y materiales premium, robot grande visible, menú lateral Inicio / Agenda / Alarmas / Clima / Memoria / Dispositivos / Automatizar / Ajustes, tarjetas meteorológicas y agenda, controles discretos de escucha.
2. Modo oscuro: grafito con cobre y acentos cyan/azul, robot con reflejos y ojos iluminados, panel de eventos y conversación.
3. Modo profesional: superficie clara con acceso a documentos y datos, robot visible y capaz de señalar contenidos.
4. Modo transparente: variación visual del robot con partes translúcidas y estructura aparente, solo si el motor soporta materiales correspondientes.

## Comportamiento
Conversación por voz natural sin pulsar iniciar/finalizar por cada turno; interrupciones, memoria, Google Calendar/calendarios Android, alarmas, temporizadores, clima, automatizaciones. Los datos de pantalla deben ser reales y nunca simulados como conexión exitosa.

## Dispositivos
ZNP Z02 y ZNP N02 deben contemplarse en la arquitectura Bluetooth. El visor del estuche requiere adaptación de interfaz y validación del protocolo; no se declara soportado 3D en el visor sin evidencia. La experiencia Android completa continúa aunque se desconecte el estuche.

## Reglas de entrega
- Prohibido sustituir el diseño aprobado por pantalla de texto y botón EJECUTAR.
- Sin versiones intermedias para que el usuario las instale o pruebe.
- No declarar terminado con solo compilar. Validar funciones, render y conexiones.
- El producto final debe compararse visual y funcionalmente contra la imagen de referencia.
