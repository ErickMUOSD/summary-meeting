"""Subida de grabaciones (multipart) hacia /opt/recordings/{note_id}/."""
import os
import shutil
import subprocess
import uuid
from typing import List

from fastapi import APIRouter, Depends, HTTPException, UploadFile, File
from sqlalchemy.orm import Session

from ..config import settings
from ..db import get_db
from ..models import Note, Recording
from ..schemas import RecordingOut

router = APIRouter(tags=["uploads"])

# Extensiones aceptadas (se convierten a 16kHz mono con ffmpeg antes de transcribir)
ALLOWED_EXT = {".wav", ".m4a", ".mp3", ".aac", ".ogg", ".flac", ".opus", ".mp4", ".3gp", ".amr"}


def _probe_duration(path: str):
    """Duración en segundos vía ffprobe; None si falla."""
    try:
        out = subprocess.run(
            ["ffprobe", "-v", "error", "-show_entries", "format=duration",
             "-of", "default=noprint_wrappers=1:nokey=1", path],
            capture_output=True, text=True, timeout=60,
        )
        return float(out.stdout.strip())
    except Exception:
        return None


@router.post("/notes/{note_id}/uploads", response_model=List[RecordingOut], status_code=201)
async def upload_recordings(
    note_id: uuid.UUID,
    files: List[UploadFile] = File(...),
    db: Session = Depends(get_db),
):
    note = db.get(Note, note_id)
    if not note:
        raise HTTPException(status_code=404, detail="Nota no encontrada")
    if not files:
        raise HTTPException(status_code=422, detail="No se enviaron archivos")

    note_dir = os.path.join(settings.recordings_dir, str(note_id))
    os.makedirs(note_dir, exist_ok=True)

    created: List[Recording] = []
    for up in files:
        original = os.path.basename(up.filename or "audio")
        ext = os.path.splitext(original)[1].lower()
        if ext not in ALLOWED_EXT:
            raise HTTPException(
                status_code=415,
                detail=f"Formato no soportado: {original} ({ext or 'sin extensión'})",
            )
        # Nombre único en disco; conserva el nombre original en la BD
        stored_name = f"{uuid.uuid4().hex}{ext}"
        stored_path = os.path.join(note_dir, stored_name)
        with open(stored_path, "wb") as fh:
            shutil.copyfileobj(up.file, fh)  # streaming a disco
        await up.close()

        rec = Recording(
            note_id=note_id,
            original_filename=original,
            stored_path=stored_path,
            mime=up.content_type,
            duration_seconds=_probe_duration(stored_path),
            status="uploaded",
        )
        db.add(rec)
        created.append(rec)

    # El estado de la nota avanza a 'uploaded' (si aún no está más adelante)
    if note.status == "created":
        note.status = "uploaded"
    db.commit()
    for rec in created:
        db.refresh(rec)
    return created
