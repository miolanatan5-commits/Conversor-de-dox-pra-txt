package com.conversor.docxtxt;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private static final int REQ_PICK = 1;
    private static final int REQ_SAVE = 2;
    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final int PREVIEW_LIMIT = 3000;

    private TextView status;
    private TextView preview;
    private Button btnPick;
    private Button btnSave;

    private String pendingText;
    private String pendingName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        status = findViewById(R.id.status);
        preview = findViewById(R.id.preview);
        btnPick = findViewById(R.id.btn_pick);
        btnSave = findViewById(R.id.btn_save);

        btnPick.setOnClickListener(v -> pickFile());
        btnSave.setOnClickListener(v -> askWhereToSave());
        btnSave.setEnabled(false);

        if (savedInstanceState == null) {
            handleIncoming(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncoming(intent);
    }

    private void setStatus(String s) {
        status.setText(s);
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{DOCX_MIME, "application/octet-stream"});
        startActivityForResult(i, REQ_PICK);
    }

    @SuppressWarnings("deprecation")
    private void handleIncoming(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Uri uri = null;
        if (Intent.ACTION_SEND.equals(action)) {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        } else if (Intent.ACTION_VIEW.equals(action)) {
            uri = intent.getData();
        }
        if (uri != null) {
            convert(uri);
        }
    }

    private void convert(final Uri uri) {
        setStatus(getString(R.string.status_converting));
        btnPick.setEnabled(false);
        btnSave.setEnabled(false);

        new Thread(() -> {
            try {
                String baseName = displayBaseName(uri);
                InputStream in = getContentResolver().openInputStream(uri);
                String text = DocxReader.extractText(in);
                runOnUiThread(() -> onConverted(baseName, text));
            } catch (Exception e) {
                String msg = "INVALID".equals(e.getMessage())
                        ? getString(R.string.error_invalid)
                        : (e.getMessage() != null ? e.getMessage() : e.toString());
                runOnUiThread(() -> onError(msg));
            }
        }).start();
    }

    private void onConverted(String baseName, String text) {
        btnPick.setEnabled(true);
        if (text.trim().isEmpty()) {
            pendingText = null;
            preview.setText("");
            setStatus(getString(R.string.status_empty));
            return;
        }
        pendingText = text;
        pendingName = baseName + ".txt";
        preview.setText(text.length() > PREVIEW_LIMIT
                ? text.substring(0, PREVIEW_LIMIT) + "\n…"
                : text);
        btnSave.setEnabled(true);
        int words = text.trim().split("\\s+").length;
        setStatus(getString(R.string.status_converted, words));
        askWhereToSave();
    }

    private void onError(String msg) {
        btnPick.setEnabled(true);
        setStatus(getString(R.string.status_error, msg));
    }

    private void askWhereToSave() {
        if (pendingText == null) return;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TITLE, pendingName);
        startActivityForResult(i, REQ_SAVE);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        boolean ok = resultCode == RESULT_OK && data != null && data.getData() != null;
        if (requestCode == REQ_PICK) {
            if (ok) {
                convert(data.getData());
            } else {
                setStatus(getString(R.string.status_cancelled));
            }
        } else if (requestCode == REQ_SAVE) {
            if (ok) {
                writeFile(data.getData());
            } else {
                setStatus(getString(R.string.status_save_cancelled));
            }
        }
    }

    private void writeFile(final Uri uri) {
        final String text = pendingText;
        final String name = pendingName;
        if (text == null) return;
        setStatus(getString(R.string.status_saving));

        new Thread(() -> {
            try (OutputStream os = getContentResolver().openOutputStream(uri, "wt")) {
                if (os == null) throw new IOException("Não foi possível gravar o arquivo.");
                os.write(text.getBytes(StandardCharsets.UTF_8));
                os.flush();
                runOnUiThread(() -> setStatus(getString(R.string.status_saved, name)));
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                runOnUiThread(() -> setStatus(getString(R.string.status_save_error, msg)));
            }
        }).start();
    }

    private String displayBaseName(Uri uri) {
        String name = null;
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception ignored) {
        }
        if (name == null || name.trim().isEmpty()) {
            return getString(R.string.default_name);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name;
    }
}
