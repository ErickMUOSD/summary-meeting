"""Configuración leída desde variables de entorno."""
from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # Auth
    app_token: str = "change-me"

    # Base de datos
    database_url: str = "postgresql+psycopg2://meeting:meeting@db:5432/meeting_summary"

    # Almacenamiento
    recordings_dir: str = "/opt/recordings"

    # Transcripción (faster-whisper, CPU)
    whisper_model: str = "medium"
    whisper_device: str = "cpu"
    whisper_compute_type: str = "int8"
    whisper_cpu_threads: int = 8
    whisper_language: str = "es"

    # Resumen (Claude Code CLI headless)
    claude_bin: str = "claude"
    claude_model: str = "opus"
    claude_timeout: int = 600
    # Token de la suscripción generado con `claude setup-token` (CLAUDE_CODE_OAUTH_TOKEN).
    # Si está definido, el CLI se autentica con él y no hace falta el /login interactivo.
    # Vacío = se usa el login guardado en el volumen ~/.claude (ver README).
    claude_oauth_token: str = Field(default="", alias="CLAUDE_CODE_OAUTH_TOKEN")


settings = Settings()
