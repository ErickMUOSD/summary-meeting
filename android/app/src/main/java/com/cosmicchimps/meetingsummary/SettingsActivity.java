package com.cosmicchimps.meetingsummary;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cosmicchimps.meetingsummary.net.ApiClient;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SettingsActivity extends AppCompatActivity {

    private EditText url, token;
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = new Prefs(this);

        url = findViewById(R.id.url);
        token = findViewById(R.id.token);
        Button save = findViewById(R.id.btnSave);
        Button test = findViewById(R.id.btnTest);

        url.setText(prefs.getBaseUrl());
        token.setText(prefs.getToken());

        save.setOnClickListener(v -> {
            String u = url.getText().toString();
            String t = token.getText().toString();
            if (u.trim().isEmpty() || t.trim().isEmpty()) {
                toast("Completa URL y token");
                return;
            }
            prefs.save(u, t);
            url.setText(prefs.getBaseUrl());
            toast("Guardado");
            finish();
        });

        test.setOnClickListener(v -> {
            String base = Prefs.normalize(url.getText().toString());
            String t = token.getText().toString().trim();
            if (base.isEmpty()) { toast("Escribe la URL"); return; }
            Bg.run(() -> {
                OkHttpClient c = ApiClient.okHttp(t);
                Request req = new Request.Builder().url(base + "projects").get().build();
                try (Response r = c.newCall(req).execute()) {
                    return r.code();
                }
            }, code -> {
                if (code == 200) toast("Conexión OK (200)");
                else if (code == 401) toast("URL OK, pero token inválido (401)");
                else toast("Respuesta HTTP " + code);
            }, e -> toast("Error: " + e.getMessage()));
        });
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_LONG).show();
    }
}
