"""Resumen con Claude Opus 4.8 vía Claude Code CLI en modo headless.

Se invoca `claude -p --model opus` y se le pasa el prompt + transcripción por stdin.
El CLI se autentica con la suscripción de dos formas (ver README):
  - CLAUDE_CODE_OAUTH_TOKEN (token de `claude setup-token`), o
  - el login interactivo guardado en ~/.claude.
"""
import os
import subprocess

from ..config import settings

PROMPT_INSTRUCCIONES = """\
Eres un analista que redacta, en español y en tono formal y claro, un documento base para
la redacción de REQUERIMIENTOS TÉCNICOS a partir de la TRANSCRIPCIÓN de una junta de
descubrimiento (reunión con cliente y/o equipo técnico).

A partir de la TRANSCRIPCIÓN que aparece más abajo, redacta un documento en Markdown con
EXACTAMENTE esta estructura, en este orden y con estos encabezados literales. NO incluyas un
título de primer nivel (# ...): el documento empezará directamente con el bloque de cita de
abajo (el título y la fecha de generación se añaden automáticamente).

> Documento base para la redacción de **requerimientos técnicos**.
> Origen: transcripción de junta de descubrimiento.

**Participantes identificados**
- Lista con viñetas: por cada participante, su nombre en **negritas** y su rol/aporte.
  Si un nombre es dudoso por la transcripción, anótalo (p. ej. "transcrito como ...").

**Fecha de la junta:** si no aparece en la transcripción, escribe _[por completar]_.

---

## 1. Contexto del negocio

Uno o dos párrafos con el giro del negocio y cómo opera. Luego incluye estos tres subapartados
en negritas (omite el que no tenga información en la transcripción):

**Funciones principales de operación:**
- Viñetas con las funciones/procesos clave del negocio.

**Sistema actual (dolor de fondo):**
- Viñetas con el sistema o herramientas actuales y los problemas de raíz que se mencionaron.

**Marco de entrega:** una o dos líneas sobre el alcance/enfoque esperado de la entrega (p. ej. MVP).

---

## 2. Requerimientos priorizados (de mayor a menor importancia)

Una tabla Markdown con los requerimientos detectados, ordenados de mayor a menor prioridad.
Usa EXACTAMENTE estas columnas:

| # | Requerimiento | Prioridad | Señal en la junta |
|---|---------------|-----------|-------------------|

- La columna Prioridad usa niveles P0 (alta) a P3 (baja), p. ej. "**ALTA (P0)**".
- "Señal en la junta" es la evidencia textual o el motivo por el que se priorizó así.

---

## 3. Detalle por requerimiento

Un subapartado "### N. <nombre del requerimiento> — **<prioridad>**" por cada fila de la tabla
anterior, en el mismo orden. En cada uno usa viñetas tipo "**Qué:**", "**Objetivo:**",
"**Problema actual:**" y cualquier otro matiz relevante que aparezca en la transcripción.

---

## 4. Consideraciones para la primera salida (MVP)

Viñetas con el alcance sugerido para una primera entrega, candidatos a MVP y dependencias
(integraciones, migraciones, sesiones pendientes) que condicionan el arranque.

---

## 5. Puntos abiertos / por confirmar

Viñetas con las dudas, decisiones pendientes y datos por confirmar que quedaron de la junta.

---

## 6. Glosario rápido (para el equipo técnico)

Viñetas "**Término:** definición breve" de los tecnicismos o términos de negocio que aparecieron
en la transcripción y conviene que el equipo técnico conozca.

Reglas:
- Responde ÚNICAMENTE con el Markdown del documento, sin texto introductorio ni despedidas.
- Respeta los encabezados y la numeración (1 a 6) tal cual se indican arriba.
- No inventes datos que no estén en la transcripción; si falta un dato, usa _[por completar]_.
- Si una sección no aplica por completo, inclúyela igual con una nota breve (p. ej.
  "No se registraron puntos abiertos").
- Usa un estilo sencillo y formal, sin emojis ni adornos.

TRANSCRIPCIÓN:
"""


class SummaryError(Exception):
    pass


def summarize_text(transcript: str) -> str:
    """Devuelve el resumen en Markdown. Lanza SummaryError si el CLI falla."""
    prompt = PROMPT_INSTRUCCIONES + transcript.strip() + "\n"
    # El CLI lee CLAUDE_CODE_OAUTH_TOKEN del entorno; si está configurado lo inyectamos
    # para usar la suscripción sin login interactivo (token de `claude setup-token`).
    env = os.environ.copy()
    if settings.claude_oauth_token:
        env["CLAUDE_CODE_OAUTH_TOKEN"] = settings.claude_oauth_token
    try:
        proc = subprocess.run(
            [settings.claude_bin, "-p", "--model", settings.claude_model],
            input=prompt,
            capture_output=True,
            text=True,
            timeout=settings.claude_timeout,
            env=env,
        )
    except FileNotFoundError:
        raise SummaryError(
            f"No se encontró el binario de Claude ('{settings.claude_bin}'). "
            "¿Está instalado el CLI en el contenedor del worker?"
        )
    except subprocess.TimeoutExpired:
        raise SummaryError(f"La generación del resumen excedió {settings.claude_timeout}s")

    out = (proc.stdout or "").strip()
    err = (proc.stderr or "").strip()

    if proc.returncode != 0 or not out:
        hint = ""
        low = err.lower()
        if "login" in low or "auth" in low or "unauthorized" in low or "credential" in low:
            hint = (" Parece un problema de autenticación: ejecuta "
                    "'docker compose exec worker claude' y haz /login una vez.")
        raise SummaryError(f"Claude CLI falló (code {proc.returncode}): {err[:500]}.{hint}")

    return out
