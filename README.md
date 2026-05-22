# Consola administración Issabel (ECCP)

Cliente de escritorio JavaFX para supervisores: monitoreo de agentes en tiempo casi real vía ECCP (puerto TCP 20005), alineado con el módulo web `rep_agents_monitoring`.

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
mvn verify "-Pjpackage,jpackage-installer"
```

Salidas en `target/`:

| Artefacto | Ruta |
|-----------|------|
| App portable (carpeta + exe) | `target/jpackage-image/ConsolaAdminIssabel/ConsolaAdminIssabel.exe` |
| ZIP portable | `target/ConsolaAdminIssabel-0.2.0.zip` |
| Instalador Windows | `target/jpackage-setup/ConsolaAdminIssabel-0.2.0.exe` |

Solo imagen portable (sin WiX): `mvn verify -Pjpackage`

## Funcionalidad (v0.2)

- Login solo ECCP (sin `loginagent`)
- **Pestaña Agentes**: estado, canal, colas, métricas del día (llamadas, tiempo hablado, entrante/saliente), última sesión, detalle de llamada activa (teléfono, cola, tipo, ID, trunk), pausas
- **Pestaña Colas y campañas**: campañas activas y colas entrantes vía `getcampaignstatus` / `getincomingqueuestatus` (agentes en cola, llamadas en espera, contadores del día)
- **Pestaña Llamadas activas**: llamadas en curso reportadas por el dialer (`activecalls`)
- Actualización automática cada 5 segundos
- Filtro de agentes por cola
- **Escuchar llamada** (agente en llamada/sonando): vía AMI Originate + código de escucha Issabel (p. ej. `555` + extensión, sin `*`) o ChanSpy directo

### Escuchar llamadas activas

1. En el login indique **su extensión** (softphone o teléfono del supervisor).
2. Active **AMI** y configure usuario/clave de `/etc/asterisk/manager.conf` (permiso `originate`).
3. En la pestaña Agentes, pulse **Escuchar** en un agente con llamada activa; su teléfono sonará y entrará en modo escucha (p. ej. marcar `5558002` = prefijo `555` + extensión `8002`).

En el login configure **Código escucha** según su Issabel (por defecto `555`, sin asterisco). Si AMI no está configurado, la consola muestra el número a marcar manualmente.

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
