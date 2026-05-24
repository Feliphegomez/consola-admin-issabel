# AGENTS.md — Native Admin Console

JavaFX supervisor console for Issabel Call Center. Target users are **supervisors**, not developers.

## Navigation model (do not break)

**Top bar:** `NavigationBarPane` + `WorkspaceNavigation` show trail `Pestaña › Sección › Subpestaña`. Wire new screens via `bindNavigation()` / `setMainAndSub()` / `setMainSectionSub()`.

| Top tab | Content |
|---------|---------|
| **Inicio** | Live dashboard |
| **Monitoreo** | ECCP tables and campaign panels |
| **Llamadas** | Side menu: Trazabilidad, Grabaciones, Reintentos (`CallsWorkspacePane`) |
| **Informes** | Side catalog: live ● / historical ○ reports + Uso de canales (`ReportsBrowserPane`) |
| **Campañas** | Campaign calls and forms |
| **Sistema** | Side menu: Salud, PBX / Asterisk, Logs (`SystemWorkspacePane`) |

- **Never** add more than 6–7 top-level tabs; group new features under Llamadas, Informes or Sistema.
- Sub-navigation uses `NavSectionPane` (sidebar + content), same UX as Informes.
- Tab labels: **short Spanish**, tooltips with one-line purpose (`Tooltip` on `Tab`).

## UI copy (end user)

- Buttons: verb first — «Buscar», «Generar gráfico», «Actualizar».
- Avoid technical jargon in titles: prefer «Salud» over «Monitoreo de servicios», «PBX» over «Asterisk / PBX» in menus.
- Status lines: one sentence, include counts when useful.
- English for **code** (classes, methods, comments); Spanish for **visible UI**.

## Tables

- Use `TableViewUtil.applyStandardColumns(table, fillViewportWidth)` after defining columns.
- `fillViewportWidth=true` for panel tables (≤ ~10 columns); `false` for wide CDR/historical exports.
- Wrap with `TableViewUtil.wrapInScrollPane(table, exportName, fillViewportWidth)`.
- Do not use `CONSTRAINED_RESIZE_POLICY` without `applyStandardColumns` — columns squash.

## Data sources

- **ECCP**: live monitor, dashboard.
- **call_center** MySQL: campaigns, trace, retries, historical reports.
- **asteriskcdrdb**: CDR, recordings (CDR source), channel usage, CEL trace.
- **SSH** (Logs Issabel host): MOH, recordings play/delete, service health, log grep.

## Files

| Area | Package / path |
|------|----------------|
| Workspace | `ui/AdminWorkspacePane.java` |
| Section nav | `ui/NavSectionPane.java` |
| Reports enum | `report/ReportId.java` |
| Styles | `src/main/resources/css/issabel-admin.css` |
| User manual | `MANUAL-USUARIO.md` |
| Changelog | `CHANGES.md` |

## Build

```bash
mvn -q compile -DskipTests
```
