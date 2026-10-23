# ============================================================================
# meeting-summary backend (CPU) :: imagen compartida por 'api' y 'worker'
# ============================================================================
FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    DEBIAN_FRONTEND=noninteractive

# --- Dependencias de sistema ------------------------------------------------
#  ffmpeg        -> conversión de audio a 16kHz mono
#  lib(pango|cairo|gdk-pixbuf|ffi) + fonts -> WeasyPrint (PDF)
#  nodejs/npm    -> Claude Code CLI (resumen con Opus vía suscripción)
#  curl/ca-certs -> instalación de node y salida HTTPS a la API de Claude
RUN apt-get update && apt-get install -y --no-install-recommends \
        ffmpeg \
        curl ca-certificates gnupg \
        libpango-1.0-0 libpangocairo-1.0-0 libgdk-pixbuf-2.0-0 \
        libcairo2 libffi8 shared-mime-info \
        fonts-dejavu-core fonts-liberation \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && npm install -g @anthropic-ai/claude-code \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /srv

COPY requirements.txt .
RUN pip install -r requirements.txt

COPY app ./app
COPY worker.py ./worker.py

EXPOSE 8080
