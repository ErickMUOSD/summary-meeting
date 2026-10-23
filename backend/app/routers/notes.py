"""CRUD de notas + disparadores de transcripción/resumen."""
import uuid
from typing import List

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from ..db import get_db
from ..models import Project, Note, Recording, Job, Summary, FinalNote
from ..schemas import NoteCreate, NoteOut, NoteDetail, RecordingOut, JobOut

router = APIRouter(tags=["notes"])


def _get_note_or_404(db: Session, note_id: uuid.UUID) -> Note:
    note = db.get(Note, note_id)
    if not note:
        raise HTTPException(status_code=404, detail="Nota no encontrada")
    return note


@router.get("/projects/{project_id}/notes", response_model=List[NoteOut])
def list_notes(project_id: uuid.UUID, db: Session = Depends(get_db)):
    if not db.get(Project, project_id):
        raise HTTPException(status_code=404, detail="Proyecto no encontrado")
    return (
        db.query(Note)
        .filter(Note.project_id == project_id)
        .order_by(Note.created_at.desc())
        .all()
    )


@router.post("/projects/{project_id}/notes", response_model=NoteOut, status_code=201)
def create_note(project_id: uuid.UUID, body: NoteCreate, db: Session = Depends(get_db)):
    if not db.get(Project, project_id):
        raise HTTPException(status_code=404, detail="Proyecto no encontrado")
    title = body.title.strip()
    if not title:
        raise HTTPException(status_code=422, detail="El título no puede estar vacío")
    note = Note(project_id=project_id, title=title, status="created")
    db.add(note)
    db.commit()
    db.refresh(note)
    return note


@router.get("/notes/{note_id}", response_model=NoteDetail)
def get_note(note_id: uuid.UUID, db: Session = Depends(get_db)):
    note = _get_note_or_404(db, note_id)
    detail = NoteDetail.model_validate(note)
    detail.recordings = [RecordingOut.model_validate(r) for r in note.recordings]
    detail.has_summary = note.summary is not None
    detail.has_deliverables = note.final_note is not None and bool(note.final_note.pdf_path)
    return detail


@router.delete("/notes/{note_id}", status_code=204)
def delete_note(note_id: uuid.UUID, db: Session = Depends(get_db)):
    note = _get_note_or_404(db, note_id)
    db.delete(note)
    db.commit()


def _active_job(db: Session, note_id: uuid.UUID):
    return (
        db.query(Job)
        .filter(Job.note_id == note_id, Job.status.in_(["pending", "running"]))
        .first()
    )


@router.post("/notes/{note_id}/transcribe", response_model=JobOut, status_code=202)
def start_transcription(note_id: uuid.UUID, db: Session = Depends(get_db)):
    note = _get_note_or_404(db, note_id)
    if not note.recordings:
        raise HTTPException(status_code=409, detail="La nota no tiene grabaciones subidas")
    if _active_job(db, note_id):
        raise HTTPException(status_code=409, detail="Ya hay un trabajo en curso para esta nota")
    job = Job(note_id=note_id, type="transcribe", status="pending", stage="En cola")
    db.add(job)
    db.commit()
    db.refresh(job)
    return job


@router.post("/notes/{note_id}/summarize", response_model=JobOut, status_code=202)
def start_summary(note_id: uuid.UUID, db: Session = Depends(get_db)):
    note = _get_note_or_404(db, note_id)
    # Paso 3 bloqueado hasta que exista transcripción combinada
    if note.status not in ("transcribed", "summarized") or not note.combined_txt_path:
        raise HTTPException(status_code=409, detail="La nota aún no está transcrita")
    if _active_job(db, note_id):
        raise HTTPException(status_code=409, detail="Ya hay un trabajo en curso para esta nota")
    job = Job(note_id=note_id, type="summarize", status="pending", stage="En cola")
    db.add(job)
    db.commit()
    db.refresh(job)
    return job
