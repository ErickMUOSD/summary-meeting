package com.cosmicchimps.meetingsummary.ui;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.cosmicchimps.meetingsummary.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Collection;

/**
 * Diálogo de entrada de texto con estilo Material y validación inline:
 * obligatorio, mínimo 2 y máximo 80 caracteres, y sin duplicados.
 */
public final class InputDialog {

    public static final int MIN_LEN = 2;
    public static final int MAX_LEN = 80;

    public interface OnConfirm { void run(String value); }

    private InputDialog() { }

    public static void show(Context ctx, String title, String hint, String positive,
                            @Nullable Collection<String> existingNames, OnConfirm onConfirm) {
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_input, null);
        TextInputLayout til = view.findViewById(R.id.til_input);
        TextInputEditText et = view.findViewById(R.id.et_input);
        til.setHint(hint);
        et.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(MAX_LEN) });

        AlertDialog dialog = new MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setView(view)
                .setPositiveButton(positive, null)   // se sobrescribe para no auto-cerrar
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(d -> {
            et.requestFocus();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            }
            et.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                    til.setError(null);
                }
                @Override public void afterTextChanged(Editable s) { }
            });

            Button pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            pos.setOnClickListener(v -> {
                String value = et.getText() == null ? "" : et.getText().toString().trim();
                String error = validate(value, existingNames);
                if (error != null) {
                    til.setError(error);
                    return;
                }
                dialog.dismiss();
                onConfirm.run(value);
            });
        });

        dialog.show();
    }

    private static String validate(String value, @Nullable Collection<String> existing) {
        if (value.isEmpty()) return "Ingresa un nombre";
        if (value.length() < MIN_LEN) return "Mínimo " + MIN_LEN + " caracteres";
        if (value.length() > MAX_LEN) return "Máximo " + MAX_LEN + " caracteres";
        if (existing != null) {
            for (String n : existing) {
                if (n != null && n.trim().equalsIgnoreCase(value)) {
                    return "Ya existe un elemento con ese nombre";
                }
            }
        }
        return null;
    }
}
