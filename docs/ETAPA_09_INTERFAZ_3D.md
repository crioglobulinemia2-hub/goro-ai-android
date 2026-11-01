# Etapa 9/12 — Interfaz humanoide 3D y experiencia visual

## Revisión técnica Nemotron
Run de revisión: https://agent.tinyfish.ai/runs/49a80a08-2721-446e-b4aa-00abecaee871
Recomendación: Filament como motor PBR, GLB/glTF como formato, rig esquelético real, morph targets para visemas/expresiones, estados de avatar y gestos procedurales. No utilizar sprites ni primitivas geométricas para simular el robot aprobado.

## Stack seleccionado
- Google Filament, licencia Apache 2.0.
- GLB/glTF 2.0 para el asset.
- Android Java, minSdk 26, targetSdk 35.
- UI Android Views existente; módulos desacoplados para tema, navegación, avatar, motion y TTS.
- Filament latest verified GitHub release at implementation: v1.79.0.

## Alcance incorporado en esta primera entrega de etapa
- Design tokens modo claro cálido y modo oscuro grafito/cobre/cyan.
- Navegación tipada de ocho secciones.
- Estados de avatar IDLE, LISTENING, THINKING, SPEAKING, CONFIRMING y ALERT.
- Dependencias oficiales Filament 1.79.0: filament-android, gltfio-android y filament-utils-android.
- Contrato de rig GLB con huesos y morph targets obligatorios.
- Estimador inicial de visemas castellano A/E/I/O/U/MBP.
- Planificador procedural con seis gestos y bloqueo de repetición consecutiva; pendiente de conectarlo al rig real.
- Contrato de aceptación del GLB: skeleton completo, morph targets faciales, materiales PBR y clips/rig.
- Se mantiene la UI existente mientras se sustituye por la nueva composición; no se presenta el TextView actual como robot.

## Asset 3D obligatorio aún no incorporado
No hay en el repositorio un modelo GLB rigged que cumpla la referencia. La revisión Nemotron confirma que no se debe fingir que existe uno ni reemplazarlo por un G1 genérico sin rig. Sin un asset 3D real no se puede afirmar boca, ojos y extremidades animadas ni sincronización labial. El GLB debe venir de creación 3D/artefacto autorizado y pasar inspección técnica.

## Criterios de salida
1. GLB real cargado por Filament en Android.
2. Rig de cabeza, cuello, torso, brazos, manos y dedos.
3. Morph targets de visemas/expresiones.
4. Tres o más gestos procedurales con transiciones.
5. Lip-sync asociado a TTS y corte inmediato al interrumpir.
6. Layout de referencia con navegación lateral y tarjetas de datos reales.
7. Modo claro y oscuro completos.
8. Integración con funciones etapa 7/8 y estados de voz.
9. Render y memoria validados en tablet objetivo; no afirmar 30 FPS sin medición.

## Rechazo explícito
Chat genérico, avatar estático, sprites, GIF, robot de cubos/esferas, secuencias repetitivas, fondo plano monocromático, datos falsos o afirmar un GLB no existente.
