# Consola administración Issabel (ECCP)

Cliente de escritorio JavaFX para supervisores: monitoreo de agentes en tiempo casi real vía ECCP (puerto TCP 20005), alineado con el módulo web `rep_agents_monitoring`.

**Guía para el usuario final (pestañas, componentes y escucha):** [MANUAL-USUARIO.md](MANUAL-USUARIO.md)

## Requisitos

- JDK 21+
- Maven 3.8+
- Issabel Call Center con `issabeldialer` activo
- Usuario ECCP (p. ej. `agentconsole` / contraseña del módulo agent_console)

## Ejecutar en desarrollo

```bash
cd native-admin-console
mvn -q javafx:run
```

## Compilar .exe (Windows, JDK 21+ con jpackage)

```powershell
cd native-admin-console
mvn verify "-Pjpackage,jpackage-installer" -DskipTests
```

Salidas en `target/` (igual que consola de agentes):

| Artefacto | Ruta |
|-----------|------|
| App portable (carpeta + exe) | `target/jpackage-image/ConsolaAdminIssabel/ConsolaAdminIssabel.exe` |
| ZIP portable | `target/ConsolaAdminIssabel-0.4.0.zip` |
| Instalador Windows (WiX) | `target/jpackage-setup/ConsolaAdminIssabel-0.4.0.exe` |

Solo imagen portable + ZIP (sin WiX): `mvn verify -Pjpackage -DskipTests`

Si `mvn clean` falla al borrar `target/jpackage-setup`, cierre la consola/instalador y vuelva a intentar.

**Importante:** use el instalador de `target/jpackage-setup/`, no copias antiguas en la raíz de `target/`.

## Funcionalidad (v0.4.0 — en desarrollo)

- **Dashboard** (primera pestaña): métricas en tiempo real, agentes por cola (grid), colas/campañas, tablas de llamadas entrantes/salientes y pendientes del dialer (MySQL)
- **Gestión Reintentos**: llamadas salientes fallidas/cortas/sin respuesta; botón *Reagendar* para nuevo intento del dialer (MySQL)
- **Buscar trazabilidad**: por teléfono (ej. `6045451116`), listado entrantes/salientes y pasos legibles de `call_progress_log` (MySQL)
- Actualización automática cada 5 s en Dashboard

## Funcionalidad (v0.3.0)

- **Paneles campaña** (entrantes / salientes): estadísticas por turno, llamadas activas ECCP, agentes
- **Salientes**: acordeón *Llamadas marcando* y *Pendientes por salir* (MySQL `calls.status IS NULL`)
- **Fallidas y cortas**: listado + trazabilidad `call_progress_log`
- **Exportar tablas**: CSV, XLSX, PDF en todas las tablas del monitoreo e informes
- Login en **dos columnas** (ECCP/AMI | MySQL)
- **Datos de campaña**: columna Datos (✓) si hay formulario capturado

## Funcionalidad (v0.2+)

- Login ECCP + opcional **MySQL call_center** para informes históricos
- Pestaña **Informes**: 15 módulos alineados con la consola web Issabel
- Login solo ECCP (sin `loginagent`)
- **Pestaña Agentes**: estado, canal, colas, métricas del día (llamadas, tiempo hablado, entrante/saliente), última sesión, detalle de llamada activa (teléfono, cola, tipo, ID, trunk), pausas
- **Pestaña Colas y campañas**: campañas activas y colas entrantes vía `getcampaignstatus` / `getincomingqueuestatus` (agentes en cola, llamadas en espera, contadores del día)
- **Pestaña Llamadas activas**: llamadas en curso reportadas por el dialer (`activecalls`)
- Actualización automática cada 5 segundos
- Filtro de agentes por cola
- **Escuchar llamada** (agente en llamada/sonando): vía AMI Originate + código de escucha Issabel (p. ej. `555` + extensión, sin `*`) o ChanSpy directo

### Escuchar llamadas activas

Tres modos en login (**Modo escucha**):

| Modo | Uso |
|------|-----|
| **AMI** | Marca su extensión física al pulsar Escuchar (requiere AMI + extensión supervisor) |
| **Teléfono integrado** | WebRTC SipJS en pestaña *Teléfono integrado* (WSS 8089, sin softphone externo) |
| **Manual** | Solo muestra/copia el código spy (ej. `5558002`) |

En el login configure **Código escucha** según su Issabel (por defecto `555`, sin asterisco).

#### Referencia WebRTC (`native-web-phone`, solo lectura)

La pestaña **Teléfono integrado** usa el mismo stack que `native-web-phone` (`WebRtcPhonePane` + SipJS 0.11.6 en `src/main/resources/webphone/webrtc/`). La consola admin añade:

- Audio remoto (`trackAdded` + `<audio>`, patrón `webphone-origin`)
- Precalentado de micrófono (`getUserMedia`)
- Dominio SIP / ruta WSS configurables

Si en `native-web-phone` le funciona el registro, use **los mismos valores** (host, puerto 8089, path `/ws`, usuario/clave SIP). No modifique ese repo; los cambios van solo en `native-admin-console`.

### Informes (pestaña Informes)

| Módulo web | Consola admin | Fuente |
|------------|---------------|--------|
| `rep_agents_monitoring` | Monitoreo → Agentes | ECCP |
| `rep_incoming_calls_monitoring` | Colas entrantes hoy | ECCP |
| `rep_incoming_campaigns_panel` | Panel campañas entrantes | ECCP |
| `rep_outgoing_campaigns_panel` | Panel campañas salientes | ECCP |
| `campaign_monitoring` | Monitoreo de campaña | ECCP |
| `login_logout`, `reports_break`, `calls_per_hour`, `graphic_calls`, `calls_per_agent`, `calls_detail`, `hold_time`, `ingoings_calls_success`, `rep_trunks_used_per_hour`, `rep_agent_information` | Tabla + filtro fechas | MySQL |

Active **Informes históricos vía MySQL** en el login (usuario/clave de `call_center`, p. ej. desde `/etc/issabel.conf` o `default.conf.php` del módulo).

## Logs

Windows: `%LOCALAPPDATA%\demedallo-admin-console\logs\admin-YYYY-MM-DD.log`

## Prueba manual

1. Iniciar dialer: `systemctl status issabeldialer`
2. Abrir la consola y conectar al host Issabel (puerto 20005)
3. Verificar que aparecen agentes activos en Issabel
4. Revisar log:

```bash
grep -E "\[eccp\]|\[monitor\]|\[login\]" "%LOCALAPPDATA%\demedallo-admin-console\logs\admin-"*.log | tail -30
```
