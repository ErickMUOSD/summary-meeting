"""Punto de entrada de la API FastAPI."""
import os

from fastapi import FastAPI, Depends

from .auth import verify_token
from .config import settings
from .routers import projects, notes, uploads, jobs, downloads

# Asegura que exista el directorio de grabaciones
os.makedirs(settings.recordings_dir, exist_ok=True)

app = FastAPI(title="meeting-summary API", version="1.0.0")


@app.get("/health", tags=["health"])
def health():
    """Sin auth: para comprobaciones de disponibilidad en LAN."""
    return {"status": "ok"}


# Todos los routers protegidos por token Bearer
protected = [Depends(verify_token)]
app.include_router(projects.router, dependencies=protected)
app.include_router(notes.router, dependencies=protected)
app.include_router(uploads.router, dependencies=protected)
app.include_router(jobs.router, dependencies=protected)
app.include_router(downloads.router, dependencies=protected)
