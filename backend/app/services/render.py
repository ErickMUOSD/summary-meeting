"""Genera los entregables .md y .pdf con estilo simple y formal."""
import os
from datetime import datetime

import markdown as md_lib
from weasyprint import HTML

from ..config import settings

# CSS mínimo y formal: sin colores ni adornos, solo tipografía y márgenes legibles.
_CSS = """
@page { size: A4; margin: 2.2cm; }
body { font-family: 'Liberation Serif', 'DejaVu Serif', serif;
       font-size: 11.5pt; line-height: 1.45; color: #000; }
h1 { font-size: 18pt; margin: 0 0 4pt 0; }
h2 { font-size: 13pt; margin: 16pt 0 4pt 0; border-bottom: 1px solid #000;
     padding-bottom: 2pt; }
ul { margin: 4pt 0 4pt 18pt; }
li { margin: 2pt 0; }
p { margin: 4pt 0; }
.meta { font-size: 9.5pt; color: #333; margin-bottom: 12pt; }
"""


def _out_dir(note_id) -> str:
    d = os.path.join(settings.recordings_dir, str(note_id), "out")
    os.makedirs(d, exist_ok=True)
    return d


def slugify(text: str, fallback: str = "nota") -> str:
    """Convierte un texto en un fragmento de nombre de archivo seguro."""
    safe = "".join(c if c.isalnum() or c in "-_ " else "_" for c in (text or "")).strip()
    return (safe or fallback).replace(" ", "_")


def deliverable_basename(project_name: str, title: str,
                         when: datetime | None = None) -> str:
    """Nombre base de los entregables: <proyecto>_<nota>_<fecha> (sin extensión)."""
    when = when or datetime.now()
    proyecto = slugify(project_name, fallback="proyecto")
    nota = slugify(title, fallback="nota")
    return f"{proyecto}_{nota}_{when.strftime('%Y-%m-%d')}"


def render_deliverables(note_id, title: str, summary_md: str, project_name: str = ""):
    """Escribe los entregables .md y .pdf nombrados <proyecto>_<nota>_<fecha>.ext.

    Devuelve (md_path, pdf_path).
    """
    out_dir = _out_dir(note_id)
    ahora = datetime.now()
    fecha = ahora.strftime("%Y-%m-%d %H:%M")
    base = deliverable_basename(project_name, title, ahora)

    # Al regenerar, elimina entregables previos para no dejar archivos huérfanos
    # (p. ej. de una fecha o título anteriores).
    for name in os.listdir(out_dir):
        if name.endswith((".md", ".pdf")):
            try:
                os.remove(os.path.join(out_dir, name))
            except OSError:
                pass

    # --- Markdown (encabezado + resumen) ---
    md_full = f"# {title}\n\n_Generado: {fecha}_\n\n{summary_md.strip()}\n"
    md_path = os.path.join(out_dir, f"{base}.md")
    with open(md_path, "w", encoding="utf-8") as fh:
        fh.write(md_full)

    # --- PDF (md -> html -> pdf) ---
    body_html = md_lib.markdown(summary_md, extensions=["extra", "sane_lists"])
    html_doc = f"""<!doctype html><html lang="es"><head><meta charset="utf-8">
<style>{_CSS}</style></head><body>
<h1>{_escape(title)}</h1>
<div class="meta">Generado: {fecha}</div>
{body_html}
</body></html>"""
    pdf_path = os.path.join(out_dir, f"{base}.pdf")
    HTML(string=html_doc).write_pdf(pdf_path)

    return md_path, pdf_path


def _escape(s: str) -> str:
    return (s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
