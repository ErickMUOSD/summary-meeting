package com.cosmicchimps.meetingsummary;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
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
import com.cosmicchimps.meetingsummary.net.models.Project;

import java.util.List;

import retrofit2.Call;

public class ProjectsActivity extends AppCompatActivity {

    private SimpleAdapter<Project> adapter;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_projects);

        empty = findViewById(R.id.empty);
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SimpleAdapter<>(
                p -> p.name,
                p -> null,
                this::openProject);
        list.setAdapter(adapter);

        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnAdd).setOnClickListener(v -> addProjectDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!new Prefs(this).isConfigured()) {
            Toast.makeText(this, "Configura el servidor primero", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        load();
    }

    private void load() {
        Call<List<Project>> call = ApiClient.api(this).listProjects();
        Bg.run(call::execute, resp -> {
            if (resp.isSuccessful() && resp.body() != null) {
                adapter.setItems(resp.body());
                empty.setVisibility(resp.body().isEmpty() ? View.VISIBLE : View.GONE);
            } else {
                Toast.makeText(this, "Error HTTP " + resp.code(), Toast.LENGTH_LONG).show();
            }
        }, e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void openProject(Project p) {
        Intent i = new Intent(this, NotesActivity.class);
        i.putExtra("project_id", p.id);
        i.putExtra("project_name", p.name);
        startActivity(i);
    }

    private void addProjectDialog() {
        EditText input = new EditText(this);
        input.setHint("Nombre del proyecto");
        new AlertDialog.Builder(this)
                .setTitle("Nuevo proyecto")
                .setView(input)
                .setPositiveButton("Crear", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    Call<Project> call = ApiClient.api(this).createProject(CreateBody.project(name));
                    Bg.run(call::execute, resp -> {
                        if (resp.isSuccessful()) load();
                        else Toast.makeText(this, "Error HTTP " + resp.code(), Toast.LENGTH_LONG).show();
                    }, e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
