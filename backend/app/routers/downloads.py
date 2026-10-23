"""Descarga de entregables: .txt combinado, .md y .pdf."""
import os
import uuid

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session

from ..db import get_db
from ..models import Note

router = APIRouter(prefix="/notes/{note_id}/download", tags=["downloads"])


def _get_note(db: Session, note_id: uuid.UUID) -> Note:
    note = db.get(Note, note_id)
    if not note:
        raise HTTPException(status_code=404, detail="Nota no encontrada")
    return note


def _slug(note: Note) -> str:
    safe = "".join(c if c.isalnum() or c in "-_ " else "_" for c in note.title).strip()
    return (safe or "nota").replace(" ", "_")


@router.get("/txt")
def download_txt(note_id: uuid.UUID, db: Session = Depends(get_db)):
    note = _get_note(db, note_id)
    if not note.combined_txt_path or not os.path.exists(note.combined_txt_path):
        raise HTTPException(status_code=404, detail="Transcripción no disponible")
    return FileResponse(note.combined_txt_path, media_type="text/plain",
                        filename=f"{_slug(note)}.txt")


@router.get("/md")
def download_md(note_id: uuid.UUID, db: Session = Depends(get_db)):
    note = _get_note(db, note_id)
    fn = note.final_note
    if not fn or not fn.md_path or not os.path.exists(fn.md_path):
        raise HTTPException(status_code=404, detail="Resumen .md no disponible")
    return FileResponse(fn.md_path, media_type="text/markdown",
                        filename=f"{_slug(note)}.md")


@router.get("/pdf")
def download_pdf(note_id: uuid.UUID, db: Session = Depends(get_db)):
    note = _get_note(db, note_id)
    fn = note.final_note
    if not fn or not fn.pdf_path or not os.path.exists(fn.pdf_path):
        raise HTTPException(status_code=404, detail="Resumen .pdf no disponible")
    return FileResponse(fn.pdf_path, media_type="application/pdf",
                        filename=f"{_slug(note)}.pdf")
