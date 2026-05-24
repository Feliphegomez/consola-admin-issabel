# Guía de usuario — Consola de administración Issabel

Documento orientado al **supervisor o administrador de call center** que usa la aplicación de escritorio *Consola administración Issabel* (versión **0.4.x**).

La consola se conecta al servidor Issabel por **ECCP** (monitoreo en vivo) y, de forma opcional, por **MySQL** (informes históricos, reintentos y datos de campaña).

---

## Índice

1. [Pantalla de conexión (login)](#1-pantalla-de-conexión-login)
2. [Vista general del workspace](#2-vista-general-del-workspace)
3. [Pestaña Dashboard](#3-pestaña-dashboard)
4. [Pestaña Monitoreo](#4-pestaña-monitoreo)
5. [Pestaña Gestión Reintentos](#5-pestaña-gestión-reintentos)
6. [Pestaña Buscar trazabilidad](#6-pestaña-buscar-trazabilidad)
7. [Pestaña Datos campañas](#7-pestaña-datos-campañas)
8. [Pestaña Informes](#8-pestaña-informes)
9. [Pestaña Logs Issabel](#9-pestaña-logs-issabel)
10. [Escuchar llamadas de un agente](#10-escuchar-llamadas-de-un-agente)
11. [Exportar tablas](#11-exportar-tablas)
12. [Qué puede y qué no puede ver la consola](#12-qué-puede-y-qué-no-puede-ver-la-consola)
13. [Requisitos y solución de problemas](#13-requisitos-y-solución-de-problemas)

---

## 1. Pantalla de conexión (login)

Al abrir la aplicación aparece la ventana de conexión, dividida en **dos columnas**.

### Columna izquierda — ECCP y escucha

| Campo / control | Para qué sirve |
|-----------------|----------------|
| **Host ECCP** | IP o nombre del servidor Issabel (misma máquina donde corre el dialer). |
| **Puerto** | Puerto TCP ECCP (por defecto **20005**). |
| **Usuario ECCP** | Usuario supervisor del call center (p. ej. el de `agent_console`). |
| **Clave ECCP** | Contraseña de ese usuario. |
| **Recordar contraseña ECCP** | Guarda la clave en este equipo (Windows). |
| **Modo escucha** | **AMI** — al pulsar *Escuchar* el sistema marca su extensión. **Manual** — solo muestra/copia el código a marcar usted mismo. |
| **Su extensión** | Extensión SIP/PJSIP del supervisor (ej. `8003`). Necesaria en modo AMI. |
| **Escucha automática vía AMI** | Activa el uso de Asterisk Manager al escuchar. |
| **Puerto AMI** | Puerto Manager (por defecto **5038**). |
| **Usuario AMI / Clave AMI** | Credenciales de `manager.conf` con permiso **originate**. |
| **Tecnología canal** | `PJSIP` o `SIP` si la detección automática falla. |
| **Código escucha** | Prefijo de escucha Issabel (por defecto **`555`**, sin asterisco). Se concatena con la extensión del agente (ej. marcar `5558002`). |
| **Conectar** | Establece la sesión ECCP y abre el workspace. |
| **Área de log** | Muestra el progreso de la conexión (TCP, login ECCP). |

### Columna derecha — MySQL (informes)

| Campo / control | Para qué sirve |
|-----------------|----------------|
| **Informes históricos vía MySQL** | Habilita pestañas y funciones que leen la base `call_center`. |
| **Host / Puerto / Base de datos** | Servidor MariaDB/MySQL (puerto **3306**, base **`call_center`**). |
| **Usuario / Clave MySQL** | Usuario con lectura (y escritura solo si usa *Reagendar* en reintentos). |

> **Nota:** Puede conectar solo con ECCP para monitoreo en vivo. Sin MySQL no verá informes históricos, *Gestión Reintentos*, *Datos campañas*, *Fallidas y cortas* ni la tabla *Pendientes* del Dashboard.

---

## 2. Vista general del workspace

Tras conectar, la ventana principal muestra **seis pestañas** (no se pueden cerrar). Pase el ratón sobre cada pestaña para ver una breve descripción.

| Pestaña | Resumen |
|---------|---------|
| **Inicio** | Resumen en vivo: agentes, colas y llamadas. |
| **Monitoreo** | Tablas y paneles de campaña en tiempo real. |
| **Llamadas** | Menú lateral: **Trazabilidad** (por teléfono), **Grabaciones** (audio + historial), **Reintentos** (reprogramar fallidas). |
| **Informes** | Catálogo lateral: informes en vivo (●) e históricos (○), incluido **Uso de canales**. |
| **Campañas** | Buscar llamadas y ver formularios capturados. |
| **Sistema** | Menú lateral: **Salud** del servidor, **PBX / Asterisk**, **Logs**. |

El título de la ventana incluye el host al que está conectado.

**Actualización automática:** Inicio y Monitoreo (y paneles en vivo) se refrescan cada **5 segundos**. Use **Actualizar** para forzar una lectura inmediata.

---

## 3. Pestaña Dashboard

Vista panorámica para supervisión rápida. No tiene subpestañas; está organizada en **secciones**.

### Barra superior

| Componente | Función |
|------------|---------|
| **Actualizar** | Refresca todos los datos del dashboard. |
| **Texto de estado** | Última actualización y cantidad de agentes/colas. |

### Fila de resumen (7 indicadores)

| Indicador | Significado |
|-----------|-------------|
| **En línea** | Agentes conectados y disponibles. |
| **Desconectados** | Agentes sin sesión activa. |
| **En llamada** | Agentes en conversación o sonando. |
| **Colas** | Colas/campañas con actividad reportada. |
| **Entrantes** | Llamadas entrantes activas (conteo). |
| **Salientes** | Llamadas salientes activas (conteo). |
| **Pendientes dialer** | Llamadas salientes en cola del dialer (requiere MySQL). |

### Sección «Extensiones / agentes»

- **Tarjetas por agente**, agrupadas por cola.
- En cada tarjeta: número de agente, nombre, estado (color), extensión, métricas del día y, si aplica, teléfono en curso.
- Botón **Escuchar** en agentes que están en llamada o sonando (si configuró escucha en el login).

### Sección «Colas y campañas»

- Tarjetas con nombre de cola/campaña, tipo (entrante/saliente), llamadas en espera, agentes asignados y contadores del día.

### Sección inferior — tres tablas de llamadas

| Tabla | Contenido | Columnas principales |
|-------|-----------|----------------------|
| **Llamadas de entrada** | Entrantes en curso (ECCP + campañas entrantes), incluye **Colocando** / en cola. | Cola, teléfono, estado, tipo, ID, troncal, campaña. |
| **Llamadas de salida** | Salientes marcando o en curso (ECCP + campañas salientes), estado **Colocando** mientras el dialer origina. | Igual columnas que entrantes. |
| **Pendientes del dialer** | Contactos salientes aún no marcados (MySQL). | Campaña, cola/agente, teléfono, estado, detalle. |

> El Dashboard **no muestra** pasos de IVR, anuncios del PBX ni grabaciones; solo estados del call center y del dialer.

---

## 4. Pestaña Monitoreo

Incluye **barra de herramientas**, **contadores**, **cinco subpestañas** y **barra de estado** inferior.

### Barra superior

| Componente | Función |
|------------|---------|
| **Filtrar cola** | Desplegable para ver solo agentes de una cola (o *Todas las colas*). |
| **Actualizar** | Refresco manual. |
| **Cerrar sesión** | Cierra ECCP y vuelve al login. |

### Contadores (chips)

Disponibles · Desconectados · En llamada · En pausa · Colas activas · Llamadas vivas.

### Subpestaña «Agentes»

Tabla principal de supervisión (equivalente al informe web *rep_agents_monitoring*).

| Columna | Descripción |
|---------|-------------|
| **Escuchar** | Inicia escucha AMI o muestra código manual. |
| **Agente / Nombre** | Identificación del agente. |
| **Estado** | Disponible, en llamada, pausa, desconectado, etc. |
| **Ext. / Canal** | Extensión y canal Asterisk. |
| **Colas** | Colas a las que pertenece. |
| **Nº llamadas / Tiempo hablado / Ent·Sal** | Métricas del día. |
| **Login hoy / Última sesión** | Sesión actual y anterior. |
| **Teléfono / Cola activa / Tipo / ID / Estado llamada / Trunk** | Detalle de la llamada en curso. |
| **Pausa / Desde pausa** | Motivo y hora si está en break. |

Debajo de la tabla: botones **CSV**, **XLSX**, **PDF** para exportar.

### Subpestaña «Colas y campañas»

Estado de colas entrantes y campañas salientes activas.

| Columna | Descripción |
|---------|-------------|
| **Escuchar** | Escucha un agente en llamada dentro de esa cola (si hay uno). |
| **Cola / Tipo / Campaña** | Identificación. |
| **Estado camp. / Llamadas hoy** | Estado de la campaña y volumen. |
| **En espera / Agentes** | Llamadas en cola y resumen de agentes. |
| **Estados llamadas** | Resumen de estados (marcando, sonando, etc.). |

Exportación CSV / XLSX / PDF disponible.

### Subpestaña «Llamadas activas»

Listado global de llamadas en curso reportadas por el dialer (`activecalls`).

Columnas: **Escuchar**, Cola, Campaña, Teléfono, Estado, Tipo, ID, Trunk.

### Subpestaña «Paneles campaña»

Pantalla dividida en **dos mitades** (entrantes | salientes), similares a los paneles web de campaña.

#### Panel entrante (izquierda)

| Componente | Función |
|------------|---------|
| **Desde / Hasta (hora)** | Rango horario del turno (estadísticas del día en ese rango). |
| **Aplicar** | Aplica el turno y guarda la preferencia. |
| **Indicador de turno** | Muestra el rango activo. |
| **Estadísticas** | Total, en cola, exitosas, perdidas, abandonadas, finalizadas, tiempo medio/máximo en cola. |
| **Tabla llamadas activas** | Entrantes en curso en las campañas. |
| **Tabla agentes** | Agentes de la campaña entrante y su estado. |

#### Panel saliente (derecha)

Igual que el entrante, más:

| Componente | Función |
|------------|---------|
| **Llamadas marcando** | Contactos que el dialer está marcando ahora (acordeón). |
| **Pendientes por salir** | Contactos en cola sin marcar (MySQL; acordeón). |
| **Tabla agentes** | Agentes en campañas salientes activas. |

### Subpestaña «Fallidas y cortas»

Requiere **MySQL** configurado en el login.

| Componente | Función |
|------------|---------|
| **Desde / Hasta** | Turno horario del día. |
| **Dirección** | Todas / Salientes / Entrantes. |
| **Aplicar** | Carga el listado. |
| **Tabla superior** | Llamadas fallidas o muy cortas (entrantes y salientes). |
| **Tabla inferior — Trazabilidad** | Pasos de `call_progress_log` de la llamada seleccionada (estado, reintento, troncal, duración, etc.). |

Exportación disponible en ambas tablas.

---

## 5. Pestaña Gestión Reintentos

Gestión de **llamadas salientes** que pueden volver a la cola del dialer.

| Componente | Función |
|------------|---------|
| **Desde / Hasta (hora)** | Ventana horaria del turno. |
| **Aplicar** | Recarga la lista. |
| **Reagendar fallos sin uniqueid (24 h)** | Reintento masivo (máx. 500) de **Fallo** sin `uniqueid` en 24 h. **Omite** números cuya última salida fue **Success**, los que ya tienen fila **pendiente** para el dialer, y los de **no llamar** (`dont_call` / `dnc`). |
| **Tabla superior** | Llamadas elegibles para reintento: ID, campaña, teléfono, estado, fecha, duración, reintentos, código/causa de fallo, troncal, agente, uniqueid. |
| **Acción — Reagendar** | Deja la llamada pendiente para que el dialer la vuelva a marcar (respeta horario y ventana de la campaña). Pide confirmación. |
| **Reagendar \*** | La llamada ya agotó reintentos; el supervisor fuerza un nuevo intento. |
| **Tabla inferior — Trazabilidad** | Historial técnico de la llamada seleccionada. |

**Requisitos:** MySQL activo, servicio `issabeldialer` en ejecución, permisos de escritura en tablas de llamadas si aplica.

---

## 6. Pestaña Buscar trazabilidad

Permite localizar **todas las llamadas** (entrantes y salientes) asociadas a un número de teléfono y ver la **trazabilidad en lenguaje claro**.

| Componente | Función |
|------------|---------|
| **Teléfono** | Dígitos del número (mínimo 4), ej. `6045451116`. Busca coincidencias parciales en la base. |
| **Desde / Hasta** | Rango de fechas (por defecto últimos 30 días). |
| **Tipo** | Todas, solo entrantes o solo salientes. |
| **Buscar** | Ejecuta la consulta en MySQL `call_center`. |
| **Tabla superior** | Llamadas encontradas: tipo, ID, teléfono, campaña/cola, estado, **Diagnóstico** (vista rápida), motivo, fecha, duración, reintentos, agente, troncal. |
| **Detalle** | Resumen de la llamada seleccionada. |
| **Diagnóstico** | Panel destacado: **interno** (Issabel, dialer, cola, agentes) vs **externo** (cliente colgó, troncal, destino no contesta), **dónde** ocurrió y qué revisar. Ej.: entrante **Abandonada** en cola → externo, cliente en cola (no es fallo del dialer saliente). |
| **Motivo** | Explicación del fallo: texto del dialer (`failure_cause_txt`), código SIP (Q.850) o inferencia por estado. |
| **Acción — Reagendar** | Solo **salientes** en fallo, corta o sin respuesta: vuelve a encolar en la misma campaña (igual que Gestión Reintentos). |
| **Tabla inferior — Trazabilidad** | Pasos con **Qué ocurrió**, **Origen** (Interno/Externo por paso) y **Dónde** (cola, troncal, destino, etc.). |
| **Panel derecho — Diagrama (Mermaid)** | Flujo visual: pasos coloreados, motivo del fallo y caja de **diagnóstico** (amarillo = interno, rojo = externo, morado = mixto). |
| **Conclusión del log** | Si el fallo no tiene código SIP en MySQL, busca automáticamente en `dialerd.log` y `asterisk/full` vía SSH (misma config que **Logs Issabel**) y muestra la conclusión + líneas relevantes. |

Exportación **CSV / XLSX / PDF** en la tabla de pasos (no en el diagrama).

**Requisito para búsqueda en logs:** en **Logs Issabel** marque *SSH activo*, usuario/clave root (o con lectura de logs), y *Guardar configuración*.

**Requisitos:** MySQL configurado en el login (solo lectura suficiente para consultar).

---

## 7. Pestaña Datos campañas

Explorador de llamadas con **contexto de campaña** y **formularios** capturados por los agentes.

| Componente | Función |
|------------|---------|
| **Tipo** | Saliente o Entrante. |
| **Campaña** | Lista según el tipo. |
| **Desde / Hasta** | Rango de fechas. |
| **Teléfono** | Filtro opcional por número. |
| **Buscar llamadas** | Ejecuta la búsqueda en MySQL. |
| **Tabla superior** | Llamadas: agente, cola, intentos al mismo número, datos de la llamada. Columna **Datos (✓)** si hay formulario guardado. |
| **Detalle inferior** | Al seleccionar una fila: resumen de la llamada y tabla con **campos del formulario** (nombre del campo → valor). |

Equivalente a los informes web de datos de campaña entrante/saliente.

---

## 8. Pestaña Informes

Lista lateral de informes (etiquetados **[vivo]** o **[BD]**). Botones **Ocultar lista** / **Mostrar lista de informes** para ampliar el contenido.

### Informes en vivo [vivo] — ECCP

| Informe en la lista | Qué hace en esta consola |
|---------------------|-------------------------|
| **Monitoreo de agentes** | Redirige a la pestaña **Monitoreo → Agentes**. |
| **Monitoreo llamadas entrantes** | Colas entrantes del día (tabla en vivo). |
| **Monitoreo llamadas salientes** | Campañas salientes activas (tabla en vivo). |
| **Panel campañas entrantes** | Panel detallado de una campaña entrante (selector de campaña). |
| **Panel campañas salientes** | Panel detallado de campaña saliente. |
| **Monitoreo de campaña** | Detalle de una campaña o cola concreta. |

### Informes históricos [BD] — MySQL

Todos usan filtros **Desde / Hasta** y el botón **Generar informe**. Algunos añaden filtros extra (tipo E/S, estado, entrante/saliente).

| Informe | Contenido habitual |
|---------|-------------------|
| **Información por agente** | Resumen por agente y cola. |
| **Troncales por hora** | Uso de troncales por franja horaria. |
| **Reporte de breaks** | Pausas por agente. |
| **Llamadas por hora** | Histograma horario entrante/saliente. |
| **Llamadas por hora (gráfico)** | Mismos datos en tabla (sin gráfico dibujado). |
| **Llamadas por agente** | Contestadas por agente y cola. |
| **Detalle de llamadas** | Listado CDR del call center (límite ~2000 filas). |
| **Tiempo en espera** | Distribución de `duration_wait`. |
| **Éxito llamadas entrantes** | Terminadas vs abandonadas por cola. |
| **Login / Logout** | Sesiones de agentes. |
| **Detalle de agentes** | Por agente: sesiones, pausas, entrantes y salientes (filtro de agente opcional). |
| **Datos de formularios** | Valores capturados en formularios (tipo, campaña, fechas). |

En cada informe histórico: tabla con exportación **CSV / XLSX / PDF** cuando la vista incluye tabla.

---

## 9. Pestaña Logs Issabel

Consulta de logs sin salir de la aplicación.

### Controles superiores

| Componente | Función |
|------------|---------|
| **Actualizar** | Lee las últimas líneas del log activo. |
| **Guardar configuración** | Persiste host SSH, rutas y opciones. |
| **Auto-actualizar** | Refresco periódico. |
| **Cada (s) / Líneas** | Intervalo y cantidad de líneas a leer. |
| **Filtro (regex)** | Opcional; filtra líneas mostradas. |

### Configuración SSH (opcional)

| Campo | Función |
|-------|---------|
| **SSH activo** | Lee logs remotos en el servidor Issabel. |
| **Host / Puerto / Usuario / Clave** | Acceso SSH al servidor. |
| **Dialer / Asterisk full / Asterisk messages** | Rutas de archivos en el servidor (por defecto rutas típicas de Issabel). |

### Subpestañas de logs

| Subpestaña | Origen |
|------------|--------|
| **Dialer (dialerd.log)** | Log del proceso dialer (`/opt/issabel/dialer/`). |
| **Asterisk (full)** | Log completo de Asterisk. |
| **Asterisk (messages)** | Log de mensajes de Asterisk. |
| **Consola admin (local)** | Log de esta aplicación en su PC (`%LOCALAPPDATA%\demedallo-admin-console\logs\`). |

---

## 10. Escuchar llamadas de un agente

La escucha permite oír la conversación de un agente **sin usar la consola web** ni un softphone integrado en esta app.

### Modo AMI (recomendado)

1. En el login: **Modo escucha → AMI**, indique **su extensión**, active **Escucha automática vía AMI** y complete usuario/clave AMI.
2. Configure **Código escucha** (ej. `555`).
3. En **Dashboard**, **Monitoreo → Agentes**, **Colas** o **Llamadas activas**, pulse **Escuchar** cuando el agente esté en llamada o sonando.
4. Su teléfono (extensión configurada) debe sonar; al contestar escuchará la llamada del agente según la configuración de Issabel (ChanSpy / código de escucha).

### Modo Manual

1. En el login: **Modo escucha → Manual**.
2. Al pulsar **Escuchar** se muestra un diálogo con el número a marcar (ej. `5558002`) y opción de **copiar**.
3. Marque ese número desde su teléfono o softphone externo.

### Si AMI falla

La aplicación puede ofrecer **reintentar**, **modo manual** o **copiar código**, según el error.

---

## 11. Exportar tablas

En la mayoría de tablas del **Monitoreo**, **Informes** y otras vistas con datos tabulares encontrará:

| Botón | Formato |
|-------|---------|
| **CSV** | Hoja de cálculo simple (separado por comas). |
| **XLSX** | Excel. |
| **PDF** | Documento para imprimir o archivar. |

Al pulsar, elija la carpeta y el nombre del archivo.

---

## 12. Qué puede y qué no puede ver la consola

### Sí puede ver (con ECCP y/o MySQL según el caso)

- Estado de agentes, pausas y sesiones.
- Llamadas activas del call center (entrantes, salientes, en cola del dialer).
- Métricas del día por agente y por cola/campaña.
- Paneles de campaña (estadísticas por turno, agentes, llamadas marcando/pendientes).
- Informes históricos equivalentes a los módulos web de Issabel.
- Formularios capturados en llamadas.
- Trazabilidad técnica (`call_progress_log`) en fallidas/cortas y reintentos.
- Logs del dialer y Asterisk (con SSH).

### No puede ver desde esta consola

- Pasos detallados del **IVR** o menús de voz del PBX.
- **Anuncios** o locuciones fuera del módulo call center.
- **Grabaciones** de llamadas (use el módulo de grabaciones de Issabel).
- Teléfono WebRTC integrado (use `native-web-phone` u otro softphone si necesita llamar por SIP desde el PC).

Los estados de llamada reflejan el **dialer y ECCP** (`Placing`, `Ringing`, `OnQueue`, agente `oncall`, etc.), no cada aplicación del dialplan de Asterisk.

---

## 13. Requisitos y solución de problemas

### Requisitos mínimos

| Elemento | Requisito |
|----------|-----------|
| Servidor | Issabel con Call Center y `issabeldialer` activo |
| ECCP | Puerto **20005** accesible desde el PC del supervisor |
| Usuario | Credenciales ECCP de supervisor |
| MySQL (opcional) | Base `call_center`, usuario con lectura; escritura para reintentos |
| AMI (opcional) | Puerto **5038**, usuario con `originate` para escucha automática |
| Escucha | Extensión física o softphone del supervisor registrado en el PBX |

### Problemas frecuentes

| Síntoma | Qué revisar |
|---------|-------------|
| No conecta ECCP | Host, puerto 20005, firewall, usuario/clave, dialer activo |
| Tablas vacías en informes | MySQL no marcado o credenciales incorrectas |
| *Pendientes dialer* vacío | MySQL no configurado |
| Escuchar no marca | AMI desactivado, extensión supervisor vacía, AMI sin `originate` |
| Escuchar manual no audible | Código escucha incorrecto; marque desde su extensión |
| Reagendar no efecto | `issabeldialer` parado; campaña fuera de horario; permisos MySQL |
| Logs remotos vacíos | SSH desactivado o credenciales/rutas incorrectas |

### Log local de la aplicación

```
%LOCALAPPDATA%\demedallo-admin-console\logs\admin-*.log
```

Busque líneas `[listen]`, `[dashboard]`, `[eccp]` para diagnóstico.

---

*Documento generado para la consola nativa de administración Issabel (ECCP). Para instalación y compilación, consulte [README.md](README.md).*
