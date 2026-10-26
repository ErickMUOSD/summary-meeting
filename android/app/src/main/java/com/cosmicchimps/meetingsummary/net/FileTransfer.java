package com.cosmicchimps.meetingsummary.net;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.cosmicchimps.meetingsummary.Prefs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;

/** Subida multipart con barra de progreso y descarga autenticada de entregables. */
public final class FileTransfer {

    public interface ProgressListener {
        void onProgress(int pct);
    }

    // ---------------------------------------------------------------- upload
    public static void uploadFiles(Context ctx, String noteId, List<Uri> uris,
                                   ProgressListener listener) throws IOException {
        Prefs prefs = new Prefs(ctx);
        OkHttpClient client = ApiClient.okHttp(prefs.getToken());

        // Tamaño total para el progreso global
        long total = 0;
        for (Uri u : uris) total += sizeOf(ctx, u);
        if (total <= 0) total = 1;
        final long totalBytes = total;
        final long[] sent = {0};

        MultipartBody.Builder mb = new MultipartBody.Builder().setType(MultipartBody.FORM);
        for (Uri uri : uris) {
            String name = displayName(ctx, uri);
            mb.addFormDataPart("files", name, uriBody(ctx, uri, totalBytes, sent, listener));
        }

        Request req = new Request.Builder()
                .url(prefs.getBaseUrl() + "notes/" + noteId + "/uploads")
                .post(mb.build())
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                String body = resp.body() != null ? resp.body().string() : "";
                throw new IOException("HTTP " + resp.code() + ": " + body);
            }
            if (listener != null) listener.onProgress(100);
        }
    }

    private static RequestBody uriBody(Context ctx, Uri uri, long totalBytes,
                                       long[] sent, ProgressListener listener) {
        return new RequestBody() {
            @Override public MediaType contentType() {
                String t = ctx.getContentResolver().getType(uri);
                return MediaType.parse(t != null ? t : "application/octet-stream");
            }

            @Override public long contentLength() {
                return sizeOf(ctx, uri);
            }

            @Override public void writeTo(BufferedSink sink) throws IOException {
                byte[] buf = new byte[64 * 1024];
                try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new IOException("No se pudo abrir " + uri);
                    int r;
                    while ((r = in.read(buf)) != -1) {
                        sink.write(buf, 0, r);
                        sent[0] += r;
                        if (listener != null) {
                            int pct = (int) Math.min(99, sent[0] * 100 / totalBytes);
                            listener.onProgress(pct);
                        }
                    }
                }
            }
        };
    }

    // -------------------------------------------------------------- download
    /** Descarga un entregable a un archivo local; devuelve el archivo. */
    public static File download(Context ctx, String relativePath, File dest) throws IOException {
        Prefs prefs = new Prefs(ctx);
        OkHttpClient client = ApiClient.okHttp(prefs.getToken());
        Request req = new Request.Builder()
                .url(prefs.getBaseUrl() + relativePath)
                .get()
                .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP " + resp.code());
            }
            ResponseBody body = resp.body();
            if (body == null) throw new IOException("Respuesta vacía");
            try (InputStream in = body.byteStream();
                 FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
            }
        }
        return dest;
    }

    // ---------------------------------------------------------------- utils
    private static final String[] AUDIO_EXTS = {
            ".mp3", ".m4a", ".wav", ".aac", ".ogg", ".oga", ".opus",
            ".flac", ".3gp", ".amr", ".wma", ".caf", ".aiff", ".aif"
    };

    /** true si el archivo apunta a audio (por MIME real o, en su defecto, extensión). */
    public static boolean isSupportedAudio(Context ctx, Uri uri) {
        String mime = ctx.getContentResolver().getType(uri);
        if (mime != null && mime.toLowerCase().startsWith("audio/")) return true;
        String name = displayName(ctx, uri).toLowerCase();
        for (String ext : AUDIO_EXTS) if (name.endsWith(ext)) return true;
        return false;
    }

    public static long sizeOf(Context ctx, Uri uri) {
        try (Cursor c = ctx.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && !c.isNull(idx)) return c.getLong(idx);
            }
        } catch (Exception ignored) { }
        return 0;
    }

    /** true solo si el tamaño es conocido y es 0 bytes (tamaño desconocido no se rechaza). */
    public static boolean isEmptyFile(Context ctx, Uri uri) {
        try (Cursor c = ctx.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && !c.isNull(idx)) return c.getLong(idx) == 0;
            }
        } catch (Exception ignored) { }
        return false;
    }

    public static String displayName(Context ctx, Uri uri) {
        try (Cursor c = ctx.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && !c.isNull(idx)) return c.getString(idx);
            }
        } catch (Exception ignored) { }
        return "audio_" + System.currentTimeMillis();
    }
}
