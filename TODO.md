# Native Admin Console — TODO

## 0.4.0 (en curso)

- [ ] Validar Gestión Reintentos en producción (reagendar + dialer coloca llamada)
- [ ] Completar Dashboard: validar en producción con MySQL + dialer activo
- [ ] Export CSV en tablas del Dashboard (opcional)
- [ ] Extensiones PBX / troncales / parqueo vía AMI (futuro; hoy solo agentes ECCP)
- [ ] Publicar: `mvn verify "-Pjpackage,jpackage-installer" -DskipTests`, subir artefactos, `sha256` en `0.4.0.json`, `latest.json` → 0.4.0

## Backlog

- [ ] Tests automatizados (no hay suite hoy)
- [ ] Linux jpackage (si se requiere)
