# meeting-summary

Sistema **self-hosted** para transcribir reuniones y generar un resumen con IA.

- **Entrada:** app **Android** (Java) desde la que subes los audios de la reunión.
- **Backend:** **Python** (FastAPI + faster-whisper) en tu servidor local, accesible por **LAN**.
- **IA:** resumen con **Claude Opus 4.8** vía el CLI de **Claude Code** (usa tu suscripción).
- **Datos:** **Postgres** en contenedor.
- **Entregables:** transcripción `.txt`, resumen `.md` y `.pdf` (estilo simple y formal).

> **Hardware objetivo:** Intel i7-4700MQ (8 hilos) + Quadro K1100M. La GPU **no** es
> compatible con faster-whisper (Kepler CC 3.0, 2 GB), por lo que **la transcripción
> corre en CPU** con el modelo `medium`. Una reunión de ~30 min tarda ~45-75 min.

---

## Flujo

```
Proyecto → Nota → [1] Subir .wav → [2] Transcribir (.txt) → [3] Resumen IA (.md + .pdf)
```
Cada nota puede tener **1 o varias** grabaciones; se transcriben y se combinan en un solo `.txt`.

---

## 1. Backend (servidor local)

### Prerrequisitos
- Docker + Docker Compose.
- Salida a internet (solo el backend) para que el CLI de Claude llegue a Anthropic.
- Carpeta de datos: `sudo mkdir -p /opt/recordings && sudo chown $USER /opt/recordings`

### Puesta en marcha
```bash
cd meeting-summary
cp .env.example .env
# edita .env: APP_TOKEN (openssl rand -hex 32), POSTGRES_PASSWORD, DATABASE_URL
docker compose up -d --build
```
Servicios: `db` (Postgres), `api` (`:8080`), `worker` (transcribe + resume, 1 job a la vez).

### Modelo de transcripción (primer arranque)
Con `HF_HUB_OFFLINE=0` (valor por defecto del `.env.example`), el modelo `medium`
de Whisper se **descarga** desde Hugging Face en el primer job de transcripción
(~1.5 GB) y queda en el volumen `whisper-models`. Tras esa primera descarga puedes
poner `HF_HUB_OFFLINE=1` en `.env` para que use solo la caché y nunca se cuelgue si
HF limita las descargas. Si la descarga se bloquea por rate-limit, puedes precargar
la caché copiando un `~/.cache/huggingface/hub/models--Systran--faster-whisper-medium`
existente dentro del volumen y arrancar con `HF_HUB_OFFLINE=1`.

### Autenticación de Claude Code (en el worker)  ← **requerido para el paso de resumen**
El resumen usa tu suscripción vía el CLI. Hay dos formas de autenticarlo; elige una.

**Opción A — token de la suscripción (recomendada para Docker).**
Genera el token **una vez** en tu máquina (donde ya tengas sesión de Claude Code):
```bash
claude setup-token   # imprime un token; sigue el flujo del navegador
```
Pégalo en `.env` como `CLAUDE_CODE_OAUTH_TOKEN=...` y levanta los contenedores.
El worker lo inyecta al CLI por variable de entorno: no hace falta login interactivo
ni el volumen de credenciales. Si el token expira, vuelve a generarlo.

**Opción B — login interactivo** (si prefieres no manejar el token):
```bash
docker compose exec worker claude
# dentro del CLI: /login  (sigue el flujo del navegador)
# prueba:         claude -p --model opus "responde: ok"
```
Las credenciales (`/root/.claude/.credentials.json`) quedan en el volumen
`claude-config` y se refrescan solas; si expiran, repite el `/login`.

Sin ninguna de las dos, subir y transcribir (pasos 1 y 2) funcionan, pero el paso 3
(resumen) fallará con un mensaje pidiendo autenticación.

### Comprobación rápida (sin la app)
```bash
TOKEN=<tu APP_TOKEN>
H="Authorization: Bearer $TOKEN"
BASE=http://localhost:8080

curl -s $BASE/health
PID=$(curl -s -X POST $BASE/projects -H "$H" -H 'Content-Type: application/json' -d '{"name":"Demo"}' | jq -r .id)
NID=$(curl -s -X POST $BASE/projects/$PID/notes -H "$H" -H 'Content-Type: application/json' -d '{"title":"Reunion 1"}' | jq -r .id)
curl -s -X POST $BASE/notes/$NID/uploads -H "$H" -F "files=@/ruta/a/recorder.wav"
JID=$(curl -s -X POST $BASE/notes/$NID/transcribe -H "$H" | jq -r .id)
watch -n2 "curl -s $BASE/jobs/$JID -H '$H' | jq '{status,progress_pct,stage}'"
# cuando status=done:
curl -s -X POST $BASE/notes/$NID/summarize -H "$H"   # repite el watch
curl -s $BASE/notes/$NID/download/pdf -H "$H" -o resumen.pdf
```

---

## 2. App Android

### Build
1. Abre la carpeta `android/` en **Android Studio** (deja que sincronice Gradle;
   genera el wrapper automáticamente). Alternativa por consola, con Gradle 8.9+:
   `cd android && gradle wrapper && ./gradlew assembleDebug`
2. El APK queda en `android/app/build/outputs/apk/debug/app-debug.apk`.
3. **Sideload** al celular (Android 13+): habilita "Instalar apps desconocidas" y
   copia/instala el APK.

### Configuración (en la app)
Abre **Configuración** e introduce:
- **URL interna**: `http://<IP-LAN-del-servidor>:8080`
- **Token**: el mismo `APP_TOKEN` del `.env`.

Pulsa **Probar conexión** (espera "Conexión OK").

### Uso
`Nuevo proyecto` → abrir proyecto → `Nueva nota` → asistente de 3 pasos:
1. Seleccionar audios → **Subir** (barra de progreso).
2. **Transcribir** (barra por polling) → opcional descargar `.txt`.
3. **Generar resumen con IA** (barra) → descargar `.md` y `.pdf`.

---

## Arquitectura / datos

- Audios y entregables: `/opt/recordings/{note_id}/` (+ `out/` para `.txt/.md/.pdf`).
  Los audios **se conservan siempre**.
- Tablas Postgres: `projects`, `notes`, `recordings` (nombre + texto por archivo),
  `summaries` (resumen de la nota), `final_notes` (rutas `.md`/`.pdf`), `jobs` (progreso).
- Auth: token **Bearer estático** (un usuario) sobre **HTTP** en LAN.

## Configuración (variables .env relevantes)
- `WHISPER_MODEL` (`tiny|base|small|medium|large-v3`, por defecto `medium`).
- `WHISPER_CPU_THREADS` (por defecto 8), `WHISPER_LANGUAGE` (`es`).
- `CLAUDE_MODEL` (`opus`), `CLAUDE_TIMEOUT` (segundos).

## Notas
- **HTTP plano**: úsalo solo en una LAN de confianza.
- **Rendimiento**: si una reunión es muy larga, considera `WHISPER_MODEL=small`.
- **API key en vez de suscripción**: si tu organización habilita una API key de
  Anthropic, se puede cambiar `services/summarize.py` a la Messages API con el mismo prompt.
