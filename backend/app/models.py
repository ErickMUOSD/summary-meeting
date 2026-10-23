"""Modelos ORM (coinciden con migrations/init.sql)."""
import uuid
from datetime import datetime

from sqlalchemy import (
    Column, String, Text, Integer, Float, DateTime, ForeignKey, func,
)
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import relationship

from .db import Base


def _uuid():
    return uuid.uuid4()


class Project(Base):
    __tablename__ = "projects"

    id = Column(UUID(as_uuid=True), primary_key=True, default=_uuid)
    name = Column(Text, nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    notes = relationship("Note", back_populates="project", cascade="all, delete-orphan")


class Note(Base):
    __tablename__ = "notes"

    id = Column(UUID(as_uuid=True), primary_key=True, default=_uuid)
    project_id = Column(UUID(as_uuid=True), ForeignKey("projects.id", ondelete="CASCADE"), nullable=False)
    title = Column(Text, nullable=False)
    # created -> uploaded -> transcribed -> summarized
    status = Column(String, nullable=False, default="created")
    combined_txt_path = Column(Text)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())

    project = relationship("Project", back_populates="notes")
    recordings = relationship("Recording", back_populates="note", cascade="all, delete-orphan")
    summary = relationship("Summary", back_populates="note", uselist=False, cascade="all, delete-orphan")
    final_note = relationship("FinalNote", back_populates="note", uselist=False, cascade="all, delete-orphan")
    jobs = relationship("Job", back_populates="note", cascade="all, delete-orphan")


class Recording(Base):
    __tablename__ = "recordings"

    id = Column(UUID(as_uuid=True), primary_key=True, default=_uuid)
    note_id = Column(UUID(as_uuid=True), ForeignKey("notes.id", ondelete="CASCADE"), nullable=False)
    original_filename = Column(Text, nullable=False)
    stored_path = Column(Text, nullable=False)
    mime = Column(Text)
    duration_seconds = Column(Float)
    transcript_text = Column(Text)
    status = Column(String, nullable=False, default="uploaded")  # uploaded|transcribing|done|error
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    note = relationship("Note", back_populates="recordings")


class Summary(Base):
    __tablename__ = "summaries"

    id = Column(UUID(as_uuid=True), primary_key=True, default=_uuid)
    note_id = Column(UUID(as_uuid=True), ForeignKey("notes.id", ondelete="CASCADE"), nullable=False)
    content_md = Column(Text, nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    note = relationship("Note", back_populates="summary")


class FinalNote(Base):
    __tablename__ = "final_notes"

    id = Column(UUID(as_uuid=True), primary_key=True, default=_uuid)
    note_id = Column(UUID(as_uuid=True), ForeignKey("notes.id", ondelete="CASCADE"), nullable=False)
    md_path = Column(Text)
    pdf_path = Column(Text)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    note = relationship("Note", back_populates="final_note")


class Job(Base):
    __tablename__ = "jobs"

    id = Column(UUID(as_uuid=True), primary_key=True, default=_uuid)
    note_id = Column(UUID(as_uuid=True), ForeignKey("notes.id", ondelete="CASCADE"), nullable=False)
    type = Column(String, nullable=False)   # transcribe | summarize
    status = Column(String, nullable=False, default="pending")  # pending|running|done|error
    progress_pct = Column(Integer, nullable=False, default=0)
    stage = Column(Text)
    error = Column(Text)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())

    note = relationship("Note", back_populates="jobs")
