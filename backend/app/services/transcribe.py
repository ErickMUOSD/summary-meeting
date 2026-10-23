"""Transcripción con faster-whisper (CPU).

Reutiliza el enfoque validado manualmente:
  - ffmpeg convierte cualquier formato a WAV 16kHz mono
  - se lee el WAV con numpy (evita el bug PyAV<->faster-whisper) y se pasa el array
  - faster-whisper 'medium' int8 en CPU, idioma español, con filtro VAD
  - el progreso se calcula por (audio procesado / duración total de la nota)
"""
import os
import wave
import tempfile
import subprocess

import numpy as np
from faster_whisper import WhisperModel

from ..config import settings

_model = None


def get_model() -> WhisperModel:
    """Carga perezosa y cacheada del modelo (una vez por proceso worker)."""
    global _model
    if _model is None:
        _model = WhisperModel(
            settings.whisper_model,
            device=settings.whisper_device,
            compute_type=settings.whisper_compute_type,
            cpu_threads=settings.whisper_cpu_threads,
        )
    return _model


def _to_mono16k(src_path: str) -> str:
    """Convierte cualquier audio a WAV 16kHz mono en un archivo temporal."""
    tmp = tempfile.NamedTemporaryFile(suffix=".wav", delete=False)
    tmp.close()
    subprocess.run(
        ["ffmpeg", "-nostdin", "-v", "error", "-y",
         "-i", src_path, "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le", tmp.name],
        check=True,
    )
    return tmp.name


def _read_wav(path: str) -> np.ndarray:
    with wave.open(path, "rb") as w:
        raw = w.readframes(w.getnframes())
    return np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0


def _duration(path: str) -> float:
    try:
        out = subprocess.run(
            ["ffprobe", "-v", "error", "-show_entries", "format=duration",
             "-of", "default=noprint_wrappers=1:nokey=1", path],
            capture_output=True, text=True, timeout=60,
        )
        return float(out.stdout.strip())
    except Exception:
        return 0.0


def transcribe_recording(stored_path: str, processed_before: float, total: float, progress_cb):
    """Transcribe un archivo y devuelve (texto, duración).

    processed_before/total permiten reportar progreso global (0-100) de toda la nota.
    """
    wav16 = _to_mono16k(stored_path)
    try:
        audio = _read_wav(wav16)
        model = get_model()
        segments, info = model.transcribe(
            audio,
            language=settings.whisper_language,
            beam_size=5,
            vad_filter=True,
            vad_parameters=dict(min_silence_duration_ms=500),
            condition_on_previous_text=True,
        )
        dur = info.duration or (len(audio) / 16000.0)
        parts = []
        for seg in segments:
            parts.append(seg.text.strip())
            if total > 0 and progress_cb:
                done = processed_before + min(seg.end, dur)
                pct = int(max(0, min(99, round(100 * done / total))))
                progress_cb(pct)
        return " ".join(parts).strip(), dur
    finally:
        try:
            os.remove(wav16)
        except OSError:
            pass


def combined_txt_path(note_id) -> str:
    out_dir = os.path.join(settings.recordings_dir, str(note_id), "out")
    os.makedirs(out_dir, exist_ok=True)
    return os.path.join(out_dir, "transcript.txt")
