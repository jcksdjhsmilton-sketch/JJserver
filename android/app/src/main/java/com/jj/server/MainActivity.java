package com.jj.server;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    interface Job { void run() throws Exception; }

    SharedPreferences sp;
    EditText token, repo, url;
    TextView log;
    ExecutorService ex = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("jj", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 48, 24, 24);

        token = field("GitHub token (permissao Codespaces)", "token");
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        repo = field("Repositorio (ex: usuario/JJserver) - opcional", "repo");
        url = field("https://seu-dominio.ngrok-free.app/SEGREDO/mcp", "url");

        LinearLayout r1 = row(), r2 = row();
        r1.addView(btn("Status", v -> bg(this::refresh)));
        r1.addView(btn("Ligar", v -> bg(() -> action("start"))));
        r1.addView(btn("Desligar", v -> bg(() -> action("stop"))));
        r2.addView(btn("Testar MCP", v -> bg(this::testMcp)));
        r2.addView(btn("Copiar URL", v -> copy()));

        log = new TextView(this);
        log.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(this);
        sv.addView(log);

        root.addView(token);
        root.addView(repo);
        root.addView(url);
        root.addView(r1);
        root.addView(r2);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    EditText field(String hint, String key) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setText(sp.getString(key, ""));
        return e;
    }

    LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    Button btn(String t, View.OnClickListener c) {
        Button x = new Button(this);
        x.setText(t);
        x.setOnClickListener(c);
        x.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        return x;
    }

    void say(String s) { runOnUiThread(() -> log.setText(s)); }

    void bg(Job j) {
        sp.edit().putString("token", token.getText().toString().trim())
                .putString("repo", repo.getText().toString().trim())
                .putString("url", url.getText().toString().trim()).apply();
        say("...");
        ex.execute(() -> {
            try { j.run(); } catch (Exception e) { say("Erro: " + e.getMessage()); }
        });
    }

    String http(String method, String u, String bearer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("ngrok-skip-browser-warning", "1");
        if (bearer != null) {
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("Authorization", "Bearer " + bearer);
            c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        }
        if (method.equals("POST")) {
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(0);
            c.getOutputStream().close();
        }
        int code = c.getResponseCode();
        InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        if (is != null) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        }
        String body = bo.toString("UTF-8");
        if (code >= 400) throw new IOException("HTTP " + code + ": " + body);
        return body;
    }

    String gh(String method, String path) throws Exception {
        return http(method, "https://api.github.com" + path, token.getText().toString().trim());
    }

    JSONObject findCs() throws Exception {
        JSONArray a = new JSONObject(gh("GET", "/user/codespaces")).getJSONArray("codespaces");
        if (a.length() == 0) throw new IOException("Nenhum codespace encontrado");
        String want = repo.getText().toString().trim().toLowerCase();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            String full = o.getJSONObject("repository").getString("full_name").toLowerCase();
            if (want.isEmpty() || full.contains(want)) return o;
        }
        throw new IOException("Nenhum codespace do repo '" + want + "'");
    }

    void refresh() throws Exception {
        JSONObject o = findCs();
        say(o.getString("display_name") + "\nrepo: " + o.getJSONObject("repository").getString("full_name")
                + "\nestado: " + o.getString("state"));
    }

    void action(String a) throws Exception {
        JSONObject o = findCs();
        gh("POST", "/user/codespaces/" + o.getString("name") + "/" + a);
        say("Comando '" + a + "' enviado. Aguardando...");
        Thread.sleep(5000);
        refresh();
    }

    void testMcp() throws Exception {
        Uri p = Uri.parse(url.getText().toString().trim());
        say(http("GET", p.getScheme() + "://" + p.getAuthority() + "/status", null));
    }

    void copy() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("mcp", url.getText().toString().trim()));
        Toast.makeText(this, "Copiado!", Toast.LENGTH_SHORT).show();
    }
}
