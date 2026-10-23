"""Worker de procesamiento.

Loop FIFO que toma un job 'pending' a la vez (protege la CPU), lo ejecuta y
reporta progreso en la tabla jobs para que el celular haga polling.

Tipos de job:
  - transcribe: convierte+transcribe todas las grabaciones -> .txt combinado
  - summarize : pasa el .txt a Claude -> resumen -> .md + .pdf
"""
import time
import traceback
from datetime import datetime, timezone

from sqlalchemy import select

from app.db import SessionLocal
from app.models import Job, Note, Recording, Summary, FinalNote
from app.services import transcribe as tsvc
from app.services.summarize import summarize_text, SummaryError
from app.services.render import render_deliverables

POLL_INTERVAL = 2.0  # segundos entre consultas cuando no hay trabajo


def _now():
    return datetime.now(timezone.utc)


def claim_next_job(db):
    """Toma el job pendiente más antiguo y lo marca 'running' (1 a la vez)."""
    job = (
        db.query(Job)
        .filter(Job.status == "pending")
        .order_by(Job.created_at.asc())
        .with_for_update(skip_locked=True)
        .first()
    )
    if job:
        job.status = "running"
        job.stage = "Iniciando"
        job.progress_pct = 0
        db.commit()
    return job


def set_progress(db, job, pct=None, stage=None):
    if pct is not None:
        job.progress_pct = pct
    if stage is not None:
        job.stage = stage
    job.updated_at = _now()
    db.commit()


# ---------------------------------------------------------------------------
# Transcripción
# ---------------------------------------------------------------------------
def run_transcribe(db, job):
    note = db.get(Note, job.note_id)
    recordings = (
        db.query(Recording)
        .filter(Recording.note_id == note.id)
        .order_by(Recording.created_at.asc())
        .all()
    )
    if not recordings:
        raise RuntimeError("La nota no tiene grabaciones")

    # Duración total para ponderar el progreso global
    total = 0.0
    for r in recordings:
        total += r.duration_seconds or tsvc._duration(r.stored_path)
    total = total or 1.0

    set_progress(db, job, 0, "Transcribiendo")
    processed = 0.0
    for idx, rec in enumerate(recordings, start=1):
        rec.status = "transcribing"
        db.commit()

        def cb(pct):
            set_progress(db, job, pct, f"Transcribiendo {idx}/{len(recordings)}")

        text, dur = tsvc.transcribe_recording(rec.stored_path, processed, total, cb)
        rec.transcript_text = text
        rec.status = "done"
        if not rec.duration_seconds:
            rec.duration_seconds = dur
        processed += dur
        db.commit()

    # Combinar en un único .txt (orden de subida, con encabezado por archivo)
    set_progress(db, job, 99, "Combinando transcripción")
    out_path = tsvc.combined_txt_path(note.id)
    with open(out_path, "w", encoding="utf-8") as fh:
        for rec in recordings:
            fh.write(f"### {rec.original_filename}\n\n")
            fh.write((rec.transcript_text or "").strip() + "\n\n")

    note.combined_txt_path = out_path
    note.status = "transcribed"
    db.commit()
    set_progress(db, job, 100, "Completado")


# ---------------------------------------------------------------------------
# Resumen
# ---------------------------------------------------------------------------
def run_summarize(db, job):
    note = db.get(Note, job.note_id)
    if not note.combined_txt_path:
        raise RuntimeError("La nota no está transcrita")

    with open(note.combined_txt_path, "r", encoding="utf-8") as fh:
        transcript = fh.read()

    set_progress(db, job, 20, "Generando resumen con Claude")
    summary_md = summarize_text(transcript)

    # Guardar/actualizar summary
    set_progress(db, job, 70, "Guardando resumen")
    existing = db.query(Summary).filter(Summary.note_id == note.id).first()
    if existing:
        existing.content_md = summary_md
    else:
        db.add(Summary(note_id=note.id, content_md=summary_md))
    db.commit()

    # Entregables .md + .pdf
    set_progress(db, job, 85, "Generando .md y .pdf")
    md_path, pdf_path = render_deliverables(note.id, note.title, summary_md)

    fn = db.query(FinalNote).filter(FinalNote.note_id == note.id).first()
    if fn:
        fn.md_path, fn.pdf_path = md_path, pdf_path
    else:
        db.add(FinalNote(note_id=note.id, md_path=md_path, pdf_path=pdf_path))

    note.status = "summarized"
    db.commit()
    set_progress(db, job, 100, "Completado")


def process(db, job):
    try:
        if job.type == "transcribe":
            run_transcribe(db, job)
        elif job.type == "summarize":
            run_summarize(db, job)
        else:
            raise RuntimeError(f"Tipo de job desconocido: {job.type}")
        job.status = "done"
        job.updated_at = _now()
        db.commit()
    except SummaryError as e:
        db.rollback()
        job.status = "error"
        job.error = str(e)
        job.updated_at = _now()
        db.commit()
    except Exception as e:  # noqa: BLE001
        db.rollback()
        traceback.print_exc()
        job.status = "error"
        job.error = f"{type(e).__name__}: {e}"
        job.updated_at = _now()
        db.commit()


def main():
    print("[worker] iniciado; esperando jobs...", flush=True)
    while True:
        db = SessionLocal()
        try:
            job = claim_next_job(db)
            if job is None:
                time.sleep(POLL_INTERVAL)
                continue
            print(f"[worker] procesando job {job.id} ({job.type})", flush=True)
            process(db, job)
            print(f"[worker] job {job.id} -> {job.status}", flush=True)
        except Exception:  # noqa: BLE001
            traceback.print_exc()
            time.sleep(POLL_INTERVAL)
        finally:
            db.close()


if __name__ == "__main__":
    main()
