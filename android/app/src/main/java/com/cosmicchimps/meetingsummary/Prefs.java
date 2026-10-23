package com.cosmicchimps.meetingsummary;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/** Almacenamiento cifrado de la URL del servidor y el token Bearer. */
public final class Prefs {
    private static final String FILE = "ms_secure_prefs";
    private static final String K_URL = "base_url";
    private static final String K_TOKEN = "token";

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        try {
            MasterKey key = new MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            sp = EncryptedSharedPreferences.create(
                    ctx, FILE, key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo abrir el almacén seguro", e);
        }
    }

    public String getBaseUrl() { return sp.getString(K_URL, ""); }
    public String getToken()   { return sp.getString(K_TOKEN, ""); }

    public void save(String baseUrl, String token) {
        sp.edit().putString(K_URL, normalize(baseUrl)).putString(K_TOKEN, token.trim()).apply();
    }

    public boolean isConfigured() {
        return !getBaseUrl().isEmpty() && !getToken().isEmpty();
    }

    /** Garantiza esquema http:// y barra final para Retrofit. */
    public static String normalize(String url) {
        String u = url.trim();
        if (u.isEmpty()) return u;
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "http://" + u;
        }
        if (!u.endsWith("/")) u = u + "/";
        return u;
    }
}
