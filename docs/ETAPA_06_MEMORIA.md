# Etapa 6/12 — Memoria persistente y contextual

## Implementado
- Modelo de memoria con categoría, contenido e identificadores.
- Almacenamiento local app-private SQLite.
- Crear, listar recientes, actualizar, borrar individual y borrar todo.
- Límites de tamaño y validación de campos.
- Índice por categoría y pruebas unitarias del modelo.

## Pendiente
- [ ] Migrar comandos provisionales SharedPreferences a MemoryStore.
- [ ] Interfaz de revisión, edición, exportación y borrado.
- [ ] Recuperación semántica/contextual conectada a Nemotron.
- [ ] Consentimiento explícito antes de guardar recuerdos sensibles.
- [ ] Pruebas instrumentadas de persistencia, migración y borrado en dispositivo.
- [ ] Cifrado de datos si se habilita almacenamiento de información sensible.

## Privacidad
La base de datos es privada para la aplicación, pero no está cifrada. No guardar contraseñas, claves API ni información médica/financiera sensible. Los recuerdos se guardan únicamente cuando el usuario lo solicita.

**Estado: etapa 6 no aprobada hasta integración y pruebas instrumentadas.**
