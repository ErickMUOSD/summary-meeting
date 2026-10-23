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
Eres un asistente que redacta minutas de reuniones en español, en tono formal y claro.
A partir de la TRANSCRIPCIÓN que aparece más abajo, redacta un resumen en Markdown con
EXACTAMENTE estas cuatro secciones y en este orden:

## Resumen ejecutivo
Uno o dos párrafos con el panorama general de la reunión.

## Puntos clave / temas
Lista con viñetas de los temas y puntos principales discutidos.

## Acuerdos y decisiones
Lista con viñetas de las decisiones tomadas. Si no hubo, escribe "No se registraron decisiones".

## Acciones / pendientes
Lista con viñetas de tareas a realizar; incluye responsable y fecha si se mencionan.
Si no hubo, escribe "No se registraron acciones".

Reglas:
- Responde ÚNICAMENTE con el Markdown del resumen, sin texto introductorio ni despedidas.
- No inventes datos que no estén en la transcripción.
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
