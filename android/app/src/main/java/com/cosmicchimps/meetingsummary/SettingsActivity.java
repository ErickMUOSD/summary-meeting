package com.cosmicchimps.meetingsummary;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cosmicchimps.meetingsummary.net.ApiClient;
import com.google.android.material.textfield.TextInputLayout;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SettingsActivity extends AppCompatActivity {

    private static final int TOKEN_MIN_LEN = 8;

    private EditText url, token;
    private TextInputLayout tilUrl, tilToken;
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = new Prefs(this);

        url = findViewById(R.id.url);
        token = findViewById(R.id.token);
        tilUrl = findViewById(R.id.til_url);
        tilToken = findViewById(R.id.til_token);
        Button save = findViewById(R.id.btnSave);
        Button test = findViewById(R.id.btnTest);

        url.setText(prefs.getBaseUrl());
        token.setText(prefs.getToken());

        // Limpia el error al escribir
        url.addTextChangedListener(new SimpleWatcher(() -> tilUrl.setError(null)));
        token.addTextChangedListener(new SimpleWatcher(() -> tilToken.setError(null)));

        save.setOnClickListener(v -> {
            boolean okUrl = validateUrl();
            boolean okToken = validateToken();
            if (!okUrl || !okToken) return;
            prefs.save(url.getText().toString(), token.getText().toString());
            url.setText(prefs.getBaseUrl());
            toast("Guardado");
            finish();
        });

        test.setOnClickListener(v -> {
            if (!validateUrl()) return;
            String base = Prefs.normalize(url.getText().toString());
            String t = token.getText().toString().trim();
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

    /** URL: obligatoria, sin espacios y con formato http/https + host válido. */
    private boolean validateUrl() {
        String raw = url.getText().toString().trim();
        if (raw.isEmpty()) { tilUrl.setError("Ingresa la URL del servidor"); return false; }
        if (containsWhitespace(raw)) { tilUrl.setError("La URL no debe contener espacios"); return false; }
        Uri u = Uri.parse(Prefs.normalize(raw));
        String scheme = u.getScheme();
        boolean okScheme = "http".equals(scheme) || "https".equals(scheme);
        if (!okScheme || u.getHost() == null || u.getHost().isEmpty()) {
            tilUrl.setError("URL inválida (ej. http://192.168.1.50:8080)");
            return false;
        }
        tilUrl.setError(null);
        return true;
    }

    /** Token: obligatorio, sin espacios y de al menos 8 caracteres. */
    private boolean validateToken() {
        String t = token.getText().toString();
        if (t.trim().isEmpty()) { tilToken.setError("Ingresa el token"); return false; }
        if (containsWhitespace(t)) { tilToken.setError("El token no debe contener espacios"); return false; }
        if (t.length() < TOKEN_MIN_LEN) {
            tilToken.setError("El token debe tener al menos " + TOKEN_MIN_LEN + " caracteres");
            return false;
        }
        tilToken.setError(null);
        return true;
    }

    private static boolean containsWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) return true;
        }
        return false;
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_LONG).show();
    }

    /** TextWatcher mínimo que solo reacciona a los cambios de texto. */
    private static final class SimpleWatcher implements android.text.TextWatcher {
        private final Runnable onChange;
        SimpleWatcher(Runnable onChange) { this.onChange = onChange; }
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) { onChange.run(); }
        @Override public void afterTextChanged(android.text.Editable s) { }
    }
}
