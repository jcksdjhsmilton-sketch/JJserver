package com.jj.server;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
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
    static final String DEF_DOMAIN = "subpanel-unreached-skinning.ngrok-free.dev";
    static final String DEF_SECRET = "JJsecreto2026xyz";
    static final String DEF_REPO = "jcksdjhsmilton-sketch/JJserver";

    interface Job { void run() throws Exception; }

    SharedPreferences sp;
    EditText token, repo, domain, secret;
    TextView urlView, log;
    ExecutorService ex = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("jj2", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 48, 24, 24);

        token = field("GitHub token (permissao codespace)", sp.getString("token", ""));
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        repo = field("Repositorio (usuario/JJserver)", sp.getString("repo", DEF_REPO));
        domain = field("Dominio ngrok", sp.getString("domain", DEF_DOMAIN));
        secret = field("Segredo (JJ_SECRET)", sp.getString("secret", DEF_SECRET));

        TextView label = new TextView(this);
        label.setText("URL do MCP (cole no Claude):");
        urlView = new TextView(this);
        urlView.setTextIsSelectable(true);
        urlView.setTextSize(15f);
        urlView.setText(mcpUrl());

        TextWatcher tw = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            public void onTextChanged(CharSequence s, int a, int c, int d) {}
            public void afterTextChanged(Editable s) { urlView.setText(mcpUrl()); }
        };
        domain.addTextChangedListener(tw);
        secret.addTextChangedListener(tw);

        LinearLayout r1 = row(), r2 = row();
        r1.addView(btn("Status", v -> bg(this::refresh)));
        r1.addView(btn("Ligar", v -> bg(() -> action("start"))));
        r1.addView(btn("Desligar", v -> bg(() -> action("stop"))));
        r2.addView(btn("Testar MCP", v -> bg(this::testMcp)));
        r2.addView(btn("Copiar URL", v -> copy()));

        log = new TextView(this);
        log.setTextIsSelectable(true);
        log.setPadding(0, 24, 0, 0);

        root.addView(token);
        root.addView(repo);
        root.addView(domain);
        root.addView(secret);
        root.addView(label);
        root.addView(urlView);
        root.addView(r1);
        root.addView(r2);
        root.addView(log);
        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setText(value);
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

    String host() {
        String d = domain.getText().toString().trim().replace("https://", "").replace("http://", "");
        while (d.endsWith("/")) d = d.substring(0, d.length() - 1);
        return d;
    }

    String baseUrl() { return "https://" + host(); }

    String mcpUrl() { return baseUrl() + "/" + secret.getText().toString().trim() + "/mcp"; }

    void say(String s) { runOnUiThread(() -> log.setText(s)); }

    void bg(Job j) {
        sp.edit().putString("token", token.getText().toString().trim())
                .putString("repo", repo.getText().toString().trim())
                .putString("domain", domain.getText().toString().trim())
                .putString("secret", secret.getText().toString().trim()).apply();
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
        say(o.getString("display_name") + "\nestado: " + o.getString("state")
                + "\n\nURL MCP:\n" + mcpUrl());
    }

    void action(String a) throws Exception {
        JSONObject o = findCs();
        gh("POST", "/user/codespaces/" + o.getString("name") + "/" + a);
        if (a.equals("start")) {
            say("Ligando...");
            waitUp();
        } else {
            say("Desligando...");
            Thread.sleep(5000);
            refresh();
        }
    }

    void waitUp() throws Exception {
        for (int i = 0; i < 30; i++) {
            Thread.sleep(5000);
            String st = findCs().getString("state");
            if (st.equals("Available")) {
                try {
                    String r = http("GET", baseUrl() + "/status", null);
                    if (r.contains("\"ok\"")) {
                        say("Servidor no ar!\n\nURL MCP:\n" + mcpUrl());
                        return;
                    }
                } catch (Exception ignore) { }
                say("Codespace ligado, esperando o servidor subir... (" + (i * 5) + "s)");
            } else {
                say("Estado: " + st + " (" + (i * 5) + "s)");
            }
        }
        say("Demorou. Toque em Testar MCP. Se nao subir, rode no terminal: python server/server.py");
    }

    void testMcp() throws Exception {
        say(http("GET", baseUrl() + "/status", null) + "\n\nURL MCP:\n" + mcpUrl());
    }

    void copy() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("mcp", mcpUrl()));
        Toast.makeText(this, "URL copiada!", Toast.LENGTH_SHORT).show();
    }
}
