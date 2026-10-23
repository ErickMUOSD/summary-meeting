"""Estado de jobs para polling de barras de progreso."""
import uuid
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from ..db import get_db
from ..models import Job
from ..schemas import JobOut

router = APIRouter(prefix="/jobs", tags=["jobs"])


@router.get("/{job_id}", response_model=JobOut)
def get_job(job_id: uuid.UUID, db: Session = Depends(get_db)):
    job = db.get(Job, job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Job no encontrado")
    return job


@router.get("", response_model=Optional[JobOut])
def latest_job_for_note(note_id: uuid.UUID, db: Session = Depends(get_db)):
    """Último job de una nota (útil para reanudar el polling tras reabrir la app)."""
    return (
        db.query(Job)
        .filter(Job.note_id == note_id)
        .order_by(Job.created_at.desc())
        .first()
    )
