# Native Admin Console — TODO

## 0.4.0 (en curso)

- [ ] Validar barra **Ubicación** y migas en las 6 pestañas (clic atrás en Llamadas, Informes, Sistema › PBX)
- [ ] Validar **Grabaciones** CDR: trazabilidad + Mermaid (CEL `eventextra`)
- [ ] Validar **Informes › Uso de canales** (gráfico + buscar días ≥ umbral)
- [ ] Tablas: revisar anchos en Trazabilidad, Reintentos, Monitoreo, Campañas (aplicar `applyStandardColumns` donde falte)
- [ ] Actualizar resto de **MANUAL-USUARIO.md** (capítulos 5–9 alineados con Llamadas / Sistema)
- [ ] Validar Gestión Reintentos en producción (reagendar + dialer coloca llamada)
- [ ] Completar Dashboard: validar en producción con MySQL + dialer activo
- [ ] Export CSV en tablas del Dashboard (opcional)
- [ ] Extensiones PBX / troncales / parqueo vía AMI (futuro; hoy solo agentes ECCP)
- [ ] Publicar: `mvn verify "-Pjpackage,jpackage-installer" -DskipTests`, subir artefactos, `sha256` en `0.4.0.json`, `latest.json` → 0.4.0

## Backlog

- [ ] Tests automatizados (no hay suite hoy)
- [ ] Linux jpackage (si se requiere)
