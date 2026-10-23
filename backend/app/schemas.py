"""Esquemas Pydantic de entrada/salida."""
import uuid
from datetime import datetime
from typing import Optional, List

from pydantic import BaseModel, ConfigDict


# --- Projects ---------------------------------------------------------------
class ProjectCreate(BaseModel):
    name: str


class ProjectOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    name: str
    created_at: datetime


# --- Notes ------------------------------------------------------------------
class NoteCreate(BaseModel):
    title: str


class RecordingOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    original_filename: str
    mime: Optional[str] = None
    duration_seconds: Optional[float] = None
    status: str
    created_at: datetime


class NoteOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    project_id: uuid.UUID
    title: str
    status: str
    combined_txt_path: Optional[str] = None
    created_at: datetime
    updated_at: datetime


class NoteDetail(NoteOut):
    recordings: List[RecordingOut] = []
    has_summary: bool = False
    has_deliverables: bool = False


# --- Jobs -------------------------------------------------------------------
class JobOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    note_id: uuid.UUID
    type: str
    status: str
    progress_pct: int
    stage: Optional[str] = None
    error: Optional[str] = None
    updated_at: datetime
