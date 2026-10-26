package com.cosmicchimps.meetingsummary;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.cosmicchimps.meetingsummary.net.ApiClient;
import com.cosmicchimps.meetingsummary.net.FileTransfer;
import com.cosmicchimps.meetingsummary.net.models.Job;
import com.cosmicchimps.meetingsummary.net.models.Note;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;

public class NoteWizardActivity extends AppCompatActivity {

    private String noteId, noteTitle;
    private final List<Uri> picked = new ArrayList<>();

    private final Handler poll = new Handler(Looper.getMainLooper());

    // vistas
    private View step1, step2, step3;
    private TextView stepIndicator, selectedFiles, uploadStatus, transcribeStatus, summaryStatus;
    private Button btnPick, btnUpload, btnNext1, btnTranscribe, btnNext2, btnDownloadTxt,
            btnSummarize, btnDownloadMd, btnDownloadPdf;
    private ProgressBar uploadProgress, transcribeProgress, summaryProgress;

    private ActivityResultLauncher<Intent> picker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_wizard);

        noteId = getIntent().getStringExtra("note_id");
        noteTitle = getIntent().getStringExtra("note_title");

        bindViews();
        ((TextView) findViewById(R.id.noteTitle)).setText(noteTitle);

        picker = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        collectPicked(result.getData());
                    }
                });

        btnPick.setOnClickListener(v -> openPicker());
        btnUpload.setOnClickListener(v -> doUpload());
        btnNext1.setOnClickListener(v -> showStep(2));
        btnTranscribe.setOnClickListener(v -> doTranscribe());
        btnNext2.setOnClickListener(v -> showStep(3));
        btnSummarize.setOnClickListener(v -> doSummarize());
        btnDownloadTxt.setOnClickListener(v -> download("txt", "text/plain"));
        btnDownloadMd.setOnClickListener(v -> download("md", "text/markdown"));
        btnDownloadPdf.setOnClickListener(v -> download("pdf", "application/pdf"));

        showStep(1);
        refreshNote();
    }

    private void bindViews() {
        step1 = findViewById(R.id.step1);
        step2 = findViewById(R.id.step2);
        step3 = findViewById(R.id.step3);
        stepIndicator = findViewById(R.id.stepIndicator);
        selectedFiles = findViewById(R.id.selectedFiles);
        uploadStatus = findViewById(R.id.uploadStatus);
        transcribeStatus = findViewById(R.id.transcribeStatus);
        summaryStatus = findViewById(R.id.summaryStatus);
        btnPick = findViewById(R.id.btnPick);
        btnUpload = findViewById(R.id.btnUpload);
        btnNext1 = findViewById(R.id.btnNext1);
        btnTranscribe = findViewById(R.id.btnTranscribe);
        btnNext2 = findViewById(R.id.btnNext2);
        btnDownloadTxt = findViewById(R.id.btnDownloadTxt);
        btnSummarize = findViewById(R.id.btnSummarize);
        btnDownloadMd = findViewById(R.id.btnDownloadMd);
        btnDownloadPdf = findViewById(R.id.btnDownloadPdf);
        uploadProgress = findViewById(R.id.uploadProgress);
        transcribeProgress = findViewById(R.id.transcribeProgress);
        summaryProgress = findViewById(R.id.summaryProgress);
    }

    // ------------------------------------------------------------- navegación
    private void showStep(int step) {
        step1.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        step2.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
        step3.setVisibility(step == 3 ? View.VISIBLE : View.GONE);
        String[] labels = {"", "Paso 1 de 3: Subir grabaciones",
                "Paso 2 de 3: Transcribir", "Paso 3 de 3: Resumen con IA"};
        stepIndicator.setText(labels[step]);
    }

    /** Ajusta botones habilitados según el estado del servidor (permite reanudar). */
    private void refreshNote() {
        Call<Note> call = ApiClient.api(this).getNote(noteId);
        Bg.run(call::execute, resp -> {
            if (!resp.isSuccessful() || resp.body() == null) return;
            Note n = resp.body();
            boolean hasRecordings = n.recordings != null && !n.recordings.isEmpty();
            if (hasRecordings) {
                btnNext1.setEnabled(true);
                uploadStatus.setText(n.recordings.size() + " grabación(es) en el servidor");
                uploadProgress.setProgress(100);
            }
            if (n.isTranscribed()) {
                btnNext2.setEnabled(true);
                btnDownloadTxt.setEnabled(true);
                transcribeProgress.setProgress(100);
                transcribeStatus.setText("Transcripción lista");
            }
            boolean deliverables = Boolean.TRUE.equals(n.has_deliverables);
            if (n.isSummarized() || deliverables) {
                btnDownloadMd.setEnabled(true);
                btnDownloadPdf.setEnabled(true);
                summaryProgress.setProgress(100);
                summaryStatus.setText("Resumen listo");
            }
        }, e -> { /* silencioso: es solo para reanudar */ });
    }

    // --------------------------------------------------------------- paso 1
    private void openPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/*"});
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        picker.launch(i);
    }

    private void collectPicked(Intent data) {
        picked.clear();
        List<Uri> raw = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int k = 0; k < data.getClipData().getItemCount(); k++) {
                raw.add(data.getClipData().getItemAt(k).getUri());
            }
        } else if (data.getData() != null) {
            raw.add(data.getData());
        }

        // Validación: solo audio real y sin archivos vacíos.
        int rejNoAudio = 0, rejEmpty = 0;
        for (Uri u : raw) {
            if (!FileTransfer.isSupportedAudio(this, u)) { rejNoAudio++; continue; }
            if (FileTransfer.isEmptyFile(this, u)) { rejEmpty++; continue; }
            picked.add(u);
        }

        StringBuilder sb = new StringBuilder();
        for (Uri u : picked) sb.append("• ").append(FileTransfer.displayName(this, u)).append("\n");
        selectedFiles.setText(picked.isEmpty() ? "Ningún archivo seleccionado" : sb.toString().trim());
        btnUpload.setEnabled(!picked.isEmpty());

        if (rejNoAudio > 0 || rejEmpty > 0) {
            StringBuilder msg = new StringBuilder("Se omitieron ");
            if (rejNoAudio > 0) msg.append(rejNoAudio).append(" no-audio");
            if (rejNoAudio > 0 && rejEmpty > 0) msg.append(" y ");
            if (rejEmpty > 0) msg.append(rejEmpty).append(" vacío(s)");
            Toast.makeText(this, msg.toString(), Toast.LENGTH_LONG).show();
        }
    }

    private void doUpload() {
        if (picked.isEmpty()) return;
        setEnabled(btnUpload, false);
        setEnabled(btnPick, false);
        uploadStatus.setText("Subiendo...");
        List<Uri> uris = new ArrayList<>(picked);
        Bg.run(() -> {
            FileTransfer.uploadFiles(this, noteId, uris,
                    pct -> Bg.onMain(() -> uploadProgress.setProgress(pct)));
            return true;
        }, ok -> {
            uploadProgress.setProgress(100);
            uploadStatus.setText("Subida completa (" + uris.size() + " archivo(s))");
            btnNext1.setEnabled(true);
            setEnabled(btnPick, true);
            Toast.makeText(this, "Grabaciones subidas", Toast.LENGTH_SHORT).show();
        }, e -> {
            uploadStatus.setText("Error al subir: " + e.getMessage());
            setEnabled(btnUpload, true);
            setEnabled(btnPick, true);
        });
    }

    // --------------------------------------------------------------- paso 2
    private void doTranscribe() {
        setEnabled(btnTranscribe, false);
        transcribeStatus.setText("Encolando...");
        transcribeProgress.setProgress(0);
        Call<Job> call = ApiClient.api(this).transcribe(noteId);
        Bg.run(call::execute, resp -> {
            if (resp.isSuccessful() && resp.body() != null) {
                pollJob(resp.body().id, transcribeProgress, transcribeStatus, () -> {
                    btnNext2.setEnabled(true);
                    btnDownloadTxt.setEnabled(true);
                    setEnabled(btnTranscribe, true);
                });
            } else {
                transcribeStatus.setText("Error HTTP " + resp.code());
                setEnabled(btnTranscribe, true);
            }
        }, e -> {
            transcribeStatus.setText("Error: " + e.getMessage());
            setEnabled(btnTranscribe, true);
        });
    }

    // --------------------------------------------------------------- paso 3
    private void doSummarize() {
        setEnabled(btnSummarize, false);
        summaryStatus.setText("Encolando...");
        summaryProgress.setProgress(0);
        Call<Job> call = ApiClient.api(this).summarize(noteId);
        Bg.run(call::execute, resp -> {
            if (resp.isSuccessful() && resp.body() != null) {
                pollJob(resp.body().id, summaryProgress, summaryStatus, () -> {
                    btnDownloadMd.setEnabled(true);
                    btnDownloadPdf.setEnabled(true);
                    setEnabled(btnSummarize, true);
                });
            } else {
                summaryStatus.setText("Error HTTP " + resp.code());
                setEnabled(btnSummarize, true);
            }
        }, e -> {
            summaryStatus.setText("Error: " + e.getMessage());
            setEnabled(btnSummarize, true);
        });
    }

    // --------------------------------------------------------------- polling
    private void pollJob(String jobId, ProgressBar bar, TextView status, Runnable onDone) {
        Runnable r = new Runnable() {
            @Override public void run() {
                Call<Job> call = ApiClient.api(NoteWizardActivity.this).getJob(jobId);
                Bg.run(call::execute, resp -> {
                    if (resp.isSuccessful() && resp.body() != null) {
                        Job j = resp.body();
                        bar.setProgress(j.progress_pct);
                        status.setText((j.stage != null ? j.stage : j.status)
                                + " (" + j.progress_pct + "%)");
                        if ("done".equals(j.status)) {
                            bar.setProgress(100);
                            status.setText("Completado");
                            onDone.run();
                        } else if ("error".equals(j.status)) {
                            status.setText("Error: " + (j.error != null ? j.error : "desconocido"));
                        } else {
                            poll.postDelayed(this, 1500);
                        }
                    } else {
                        poll.postDelayed(this, 2500);
                    }
                }, e -> poll.postDelayed(this, 2500));
            }
        };
        poll.post(r);
    }

    // -------------------------------------------------------------- descargas
    private void download(String kind, String mime) {
        File dir = new File(getExternalFilesDir(null), "downloads");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File dest = new File(dir, safe(noteTitle) + "." + kind);
        String rel = "notes/" + noteId + "/download/" + kind;
        Toast.makeText(this, "Descargando...", Toast.LENGTH_SHORT).show();
        Bg.run(() -> FileTransfer.download(this, rel, dest), file -> {
            Toast.makeText(this, "Guardado: " + file.getName(), Toast.LENGTH_LONG).show();
            openFile(file, mime);
        }, e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void openFile(File file, String mime) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Abrir con"));
        } catch (Exception e) {
            Toast.makeText(this, "Guardado en: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------------- utils
    private void setEnabled(Button b, boolean v) { b.setEnabled(v); }

    private static String safe(String s) {
        if (s == null) return "nota";
        String r = s.replaceAll("[^a-zA-Z0-9-_ ]", "_").trim().replace(' ', '_');
        return r.isEmpty() ? "nota" : r;
    }

    @Override
    protected void onDestroy() {
        poll.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
