# Native Admin Console — Change Log

---

## 0.4.0 — en desarrollo

**Versión Maven:** `0.4.0-SNAPSHOT` · **jpackage:** `0.4.0` · **Manifiesto:** `consola-admin-issabel/0.4.0.json`

### Uso de canales

- Pestaña **Uso de canales**: gráfico y tabla de canales activos (estimados desde `asteriskcdrdb.cdr`) con filtros de fecha, hora, intervalo (15/30/60 min) y tecnología (Total, SIP/PJSIP, DAHDI, IAX, Local, H323).
- **Buscar días ≥ umbral**: lista cada día del rango cuyo pico cumple el filtro (ej. Total ≥ 20 canales), con hora del pico y picos por tecnología; gráfico por día; doble clic abre el detalle horario (máx. 90 días).
- Exportación CSV de la tabla; requiere **Base CDR** configurada en el login.

### Barra de navegación superior

- Panel **Ubicación:** con ruta clicable (migas): pestaña › sección › subpestaña (ej. `Sistema › PBX / Asterisk › Colas`).
- Clic en un segmento anterior cambia a esa vista sin perder el workspace.

### Navegación y tablas (UX)

- **6 pestañas** principales: Inicio, Monitoreo, Llamadas, Informes, Campañas, Sistema (con tooltips).
- **Llamadas**: menú lateral — Trazabilidad, Grabaciones, Reintentos.
- **Sistema**: menú lateral — Salud, PBX, Logs.
- **Informes**: catálogo con ● vivo / ○ histórico; **Uso de canales** integrado en el catálogo.
- Tablas de panel: `TableViewUtil.applyStandardColumns` + ancho al viewport (`fillViewportWidth`).
- PBX: etiquetas de subpestañas más cortas (Horarios, Entrantes, MOH, etc.).
- `AGENTS.md` y regla Cursor `.cursor/rules/native-admin-console-ux.mdc` para mantener coherencia.

### Limpieza / correcciones

- Pestaña **Uso de canales** registrada en el `TabPane` del workspace (antes se instanciaba pero no se mostraba).
- Eliminada instrumentación de depuración en **Monitoreo de servicios** (`debug-38d6ea.log`).
- Formato de `CallRecordingService.java` (líneas en blanco duplicadas).
- **Grabaciones / CDR**: consulta CEL compatible con Issabel (`eventextra` en lugar de `extra` inexistente); si CEL falla, trazabilidad desde fila `cdr`.

### Monitoreo de servicios

- Pestaña **Monitoreo de servicios** (segunda en el workspace): salud de dialer, PBX/Asterisk, Issabel, servidor y disco.
- Comprobaciones vía SSH (misma config que **Logs Issabel**): `systemctl`, procesos `dialerd`, canales Asterisk, `fwconsole status`, `df`, `free`, `uptime`, últimas líneas ERR en dialerd y alertas en `asterisk/messages`.
- Probes locales: sesión ECCP y JDBC `call_center` / `asterisk`.
- Tabla con **estado**, **resumen**, **causa/motivo** y panel **acción sugerida** al seleccionar fila.
- Gráficos JavaFX: disco (/), distribución OK/WARN/ERROR, histórico load CPU y RAM %.
- Auto-actualización configurable (10–300 s).

### Grabaciones de llamadas

- Pestaña **Grabaciones**: llamadas con archivo en `call_recording` (MixMonitor Issabel).
- Filtro por teléfono (opcional), fechas y tipo entrante/saliente.
- Por llamada: lista de archivos WAV, **trazabilidad** (`call_progress_log` en campañas; CDR/CEL en fuente CDR Issabel) + diagrama Mermaid.
- **Escuchar** (descarga temporal + reproductor del sistema), **Descargar** (SFTP → archivo local), **Eliminar** (archivo remoto + fila BD).
- Requiere MySQL call_center; SSH activo en Logs Issabel para audio y borrado de archivos.

### Buscar trazabilidad por teléfono

- Pestaña **Buscar trazabilidad**: filtro por número (mín. 4 dígitos), rango de fechas, tipo entrante/saliente.
- Lista hasta 500 llamadas en `calls` / `call_entry`; trazabilidad legible desde `call_progress_log` (columna «Qué ocurrió»).
- Columna **Motivo** (`failure_cause` / `failure_cause_txt` + códigos SIP); botón **Reagendar** en salientes fallidas (misma lógica que Gestión Reintentos).
- Panel **Diagrama (Mermaid)** a la derecha de la trazabilidad (`javafx-web` + mermaid.js empaquetado).
- **Conclusión del log**: si no hay `failure_cause` en BD, grep SSH en dialer/Asterisk y texto legible (misma config SSH que Logs Issabel).
- **Diagnóstico interno/externo**: panel con origen (Issabel/dialer/cola vs cliente/troncal/destino), ubicación, resumen y lista «Revise»; columnas **Origen** / **Dónde** por paso; nodo en diagrama Mermaid.
- Requiere MySQL en login.

### Documentación

- **MANUAL-USUARIO.md**: guía para cliente final (login, 7 pestañas, subpestañas, componentes, escucha AMI, exportación, límites).

`latest.json` sigue en **0.3.0** hasta publicar el instalador 0.4.0.

### Asterisk / PBX

- Pestaña **Asterisk / PBX**: consulta y edición de la base FreePBX/Issabel (`asterisk` por defecto) con las mismas credenciales MySQL del login.
- Subpestañas: grupos de horario (con franjas al seleccionar grupo), condiciones horarias, colas, extensiones, troncales, rutas entrantes y rutas salientes.
- **Franja horaria**: formulario estructurado como la GUI Issabel (hora inicio/fin, días, mes) con generación automática de la regla FreePBX (`HH:mm-HH:mm|día|día-mes|mes`) y vista previa.
- Botones **Nuevo** / **Editar** por entidad; **eliminar no está permitido** (ni en UI ni en DAO).
- Botón **Recargar Asterisk** (naranja cuando hay cambios pendientes): ejecuta `fwconsole reload` vía SSH (misma config que Logs Issabel).
- Rutas salientes: en edición solo se **añaden** patrones/troncales nuevos.
- Extensiones: actualiza `users`/`devices` (SIP avanzado sigue en GUI Issabel).
- Login: campo **Base PBX (Asterisk)** (`pbxDbName` en `admin-db.properties`).
- **IVR**: subpestaña maestro-detalle (`ivr_details` + `ivr_entries`), alta/edición sin eliminar.
- **Música en espera**: listado desde referencias en BD (`users.mohclass`, `incoming`, etc.) y carpetas `/var/lib/asterisk/moh/` vía SSH (Issabel no tiene tabla `music`). **Nuevo** crea carpeta MOH; **Eliminar** borra carpeta vacía (requiere SSH en Logs Issabel).
- **Anuncios** (`announcement`): alta/edición con botón **Eliminar** (confirmación).

### Dashboard — Forzar pendientes del dialer

- Columna **Forzar** en *Pendientes del dialer* (Dashboard) y *Llamadas pendientes por salir* (panel Salientes).
- `PendingDialerService`: ajusta ventana `date_init`/`time_init` a ahora (`queuePendingForImmediateDial`).
- Script SQL: `scripts/fix_campaign_channel_block.sql` (bloqueo por `Success` sin `end_time`).

### Gestión Reintentos

- Pestaña **Gestión Reintentos** (junto a Monitoreo): listado de llamadas salientes en `Failure`, `ShortCall`, `NoAnswer` con trazabilidad `call_progress_log`.
- Botón **Reagendar**: resetea la fila en `calls` y programa ventana hoy/horario de campaña para que el dialer vuelva a marcar (MySQL; sin ECCP `schedulecall` de agente).
- Botón **Reagendar fallos sin uniqueid (24 h)**: reintento masivo (máx. 500) omitiendo último **Success** al número, fila **pendiente** en dialer, y **dont_call** / `dnc`.

### Escucha / monitoreo (AMI)

- Login **Modo escucha**: AMI (marca extensión supervisor vía Originate) o **Manual** (copiar código spy).
- Botón **Escuchar** en Monitoreo (agentes, colas, llamadas activas) y Dashboard (tarjetas en llamada).
- Teléfono WebRTC integrado retirado (WebView sin `getUserMedia` fiable en JavaFX).

### Dashboard (supervisor)

- Nueva pestaña **Dashboard** (primera en el workspace): barra de métricas, extensiones/agentes agrupados por cola, colas y campañas, tres tablas (entrantes, salientes, pendientes dialer).
- `DashboardService` agrega ECCP (`AgentMonitorService`, paneles entrante/saliente) + MySQL (`calls` pendientes).
- Polling cada 5 s; layout con `SplitPane` (62% agentes / 38% colas; tablas inferiores en tercios).
- Tarjetas de agente en `GridPane` (5 columnas, 172px); estilos con texto oscuro sobre fondos pastel.
- Tablas **Llamadas de entrada/salida**: ECCP global + llamadas **Colocando** de paneles de campaña (misma fuente que Monitoreo salientes/entrantes); columnas Cola, Teléfono, Estado, Tipo, ID, Troncal.

**Build:**
```powershell
cd native-admin-console
mvn -q javafx:run
mvn verify "-Pjpackage,jpackage-installer" -DskipTests
```

**Al publicar:** subir `.exe`/`.zip` a `releases.demedallo.com`, rellenar `sha256` en `0.4.0.json` y actualizar `latest.json` a 0.4.0.

---

## 0.3.0 — publicada

**Manifiesto:** `consola-admin-issabel/0.3.0.json`, `latest.json` (hasta release 0.4.0).

- Paneles campaña entrantes y salientes (estadísticas, llamadas marcando, agentes).
- Salientes: acordeón Llamadas marcando / Pendientes por salir (MySQL).
- Pestaña Fallidas y cortas con trazabilidad (`call_progress_log`).
- Exportación de tablas CSV, XLSX, PDF.
- Login en dos columnas (ECCP/AMI | MySQL).
- Datos de campaña: columna Datos (✓) y navegador mejorado.
- Empaquetado jpackage corregido (classpath, `java.sql`, JNA/waffle).

---

## 0.2.0 — histórico

Ver `consola-admin-issabel/0.2.0.json`.
