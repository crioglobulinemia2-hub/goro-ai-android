# Etapa 4/12 — NVIDIA Nemotron

## Implementado
- Cliente Android HTTPS asíncrono detrás de interfaz ModelGateway.
- JSON de solicitud: conversationId, text.
- JSON de respuesta esperada: reply.
- Límite de longitud, timeout, control de respuesta y errores sin revelar detalles.
- No se incorporan claves NVIDIA en la APK.
- El modelo NVIDIA se ejecutará detrás de un proxy autenticado que deberá gestionar claves, cuotas y autorización.

## Pendiente para cerrar etapa
- [ ] Proxy de servidor desplegado y autenticado, con clave NVIDIA configurada fuera de APK.
- [ ] Confirmar modelo NIM disponible y acceso real a la cuenta NVIDIA Developer.
- [ ] Prueba extremo a extremo de respuesta auténtica, manejo de errores y límites.
- [ ] Conectar ModelGateway al motor de conversación y llamadas a herramientas.
- [ ] Pruebas de seguridad del proxy y de acceso no autorizado.

## Bloqueos
No se encontró ni se verificó la credencial NVIDIA de Cabezón en formato reutilizable. El repositorio privado no es un almacén seguro de claves y no se incluirán secretos en commits.

**Estado: NO APROBADA.** La compilación del cliente no demuestra conectividad con NVIDIA.
