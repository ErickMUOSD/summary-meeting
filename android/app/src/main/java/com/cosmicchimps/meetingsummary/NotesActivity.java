package com.cosmicchimps.meetingsummary;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cosmicchimps.meetingsummary.adapters.SimpleAdapter;
import com.cosmicchimps.meetingsummary.net.ApiClient;
import com.cosmicchimps.meetingsummary.net.models.CreateBody;
import com.cosmicchimps.meetingsummary.net.models.Note;

import java.util.List;

import retrofit2.Call;

public class NotesActivity extends AppCompatActivity {

    private String projectId;
    private SimpleAdapter<Note> adapter;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);

        projectId = getIntent().getStringExtra("project_id");
        String projectName = getIntent().getStringExtra("project_name");
        ((TextView) findViewById(R.id.projectName)).setText(projectName);

        empty = findViewById(R.id.empty);
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SimpleAdapter<>(
                n -> n.title,
                this::statusLabel,
                this::openNote);
        list.setAdapter(adapter);

        findViewById(R.id.btnAdd).setOnClickListener(v -> addNoteDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private String statusLabel(Note n) {
        switch (n.status == null ? "" : n.status) {
            case "created":     return "Nueva";
            case "uploaded":    return "Grabaciones subidas";
            case "transcribed": return "Transcrita";
            case "summarized":  return "Resumen listo ✓";
            default:            return n.status;
        }
    }

    private void load() {
        Call<List<Note>> call = ApiClient.api(this).listNotes(projectId);
        Bg.run(call::execute, resp -> {
            if (resp.isSuccessful() && resp.body() != null) {
                adapter.setItems(resp.body());
                empty.setVisibility(resp.body().isEmpty() ? View.VISIBLE : View.GONE);
            } else {
                Toast.makeText(this, "Error HTTP " + resp.code(), Toast.LENGTH_LONG).show();
            }
        }, e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void openNote(Note n) {
        Intent i = new Intent(this, NoteWizardActivity.class);
        i.putExtra("note_id", n.id);
        i.putExtra("note_title", n.title);
        startActivity(i);
    }

    private void addNoteDialog() {
        EditText input = new EditText(this);
        input.setHint("Título de la nota");
        new AlertDialog.Builder(this)
                .setTitle("Nueva nota")
                .setView(input)
                .setPositiveButton("Crear", (d, w) -> {
                    String title = input.getText().toString().trim();
                    if (title.isEmpty()) return;
                    Call<Note> call = ApiClient.api(this).createNote(projectId, CreateBody.note(title));
                    Bg.run(call::execute, resp -> {
                        if (resp.isSuccessful() && resp.body() != null) openNote(resp.body());
                        else Toast.makeText(this, "Error HTTP " + resp.code(), Toast.LENGTH_LONG).show();
                    }, e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
