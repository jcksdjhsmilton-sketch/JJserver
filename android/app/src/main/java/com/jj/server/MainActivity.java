package com.jj.server;

import android.app.Activity;
import android.app.AlertDialog;
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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
    ExecutorService ex = Executors.newCachedThreadPool();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("jj2", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 48, 24, 24);

        token = field("GitHub token (classic, com permissao codespace)", sp.getString("token", ""));
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

        LinearLayout r1 = row(), r2 = row(), r3 = row();
        r1.addView(btn("Status", v -> bg(this::refresh)));
        r1.addView(btn("Ligar", v -> bg(this::ligar)));
        r1.addView(btn("Desligar", v -> bg(this::desligar)));
        r2.addView(btn("Testar MCP", v -> bg(this::testMcp)));
        r2.addView(btn("Copiar URL", v -> copy()));
        r3.addView(btn("Recriar Codespace (so se o Ligar nao funcionar)", v -> confirmarRecriar()));

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
        root.addView(r3);
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

    String http(String method, String u, String bearer, String body) throws Exception {
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
            byte[] data = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            if (body != null) c.setRequestProperty("Content-Type", "application/json");
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(data.length);
            OutputStream os = c.getOutputStream();
            os.write(data);
            os.close();
        }
        int code = c.getResponseCode();
        InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        if (is != null) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        }
        String resp = bo.toString("UTF-8");
        if (code >= 400) throw new IOException("HTTP " + code + ": " + resp);
        return resp;
    }

    String tok() throws Exception {
        String t = token.getText().toString().trim();
        if (t.isEmpty()) throw new IOException("Cole o token do GitHub no primeiro campo");
        return t;
    }

    String repoName() throws Exception {
        String r = repo.getText().toString().trim();
        if (!r.contains("/")) throw new IOException("Preencha o repositorio como usuario/JJserver");
        return r;
    }

    String gh(String method, String path, String body) throws Exception {
        return http(method, "https://api.github.com" + path, tok(), body);
    }

    List<JSONObject> findAll() throws Exception {
        JSONArray a = new JSONObject(gh("GET", "/user/codespaces", null)).getJSONArray("codespaces");
        String want = repoName().toLowerCase();
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            String full = o.getJSONObject("repository").getString("full_name").toLowerCase();
            if (full.equals(want)) out.add(o);
        }
        return out;
    }

    JSONObject findCs() throws Exception {
        List<JSONObject> l = findAll();
        return l.isEmpty() ? null : l.get(0);
    }

    boolean serverUp() {
        try {
            return http("GET", baseUrl() + "/status", null, null).contains("\"ok\"");
        } catch (Exception e) {
            return false;
        }
    }

    void refresh() throws Exception {
        JSONObject o = findCs();
        if (o == null) {
            say("Nenhum Codespace deste repositorio.\nToque em Ligar para criar um (5 a 10 min).");
            return;
        }
        say(o.getString("display_name") + "\nestado: " + o.getString("state")
                + "\nservidor: " + (serverUp() ? "NO AR" : "offline")
                + "\n\nURL MCP:\n" + mcpUrl());
    }

    void ligar() throws Exception {
        JSONObject o = findCs();
        if (o == null) {
            criar();
            return;
        }
        if (serverUp()) {
            say("Servidor ja esta no ar!\n\nURL MCP:\n" + mcpUrl());
            return;
        }
        say("Ligando...");
        waitUp(900);
    }

    void desligar() throws Exception {
        JSONObject o = findCs();
        if (o == null) {
            say("Nenhum Codespace.");
            return;
        }
        gh("POST", "/user/codespaces/" + o.getString("name") + "/stop", null);
        say("Desligando...");
        Thread.sleep(5000);
        refresh();
    }

    void criar() throws Exception {
        say("Criando Codespace novo... (5 a 10 min na primeira vez)");
        gh("POST", "/repos/" + repoName() + "/codespaces", "{\"ref\":\"main\",\"idle_timeout_minutes\":240}");
        waitUp(1500);
    }

    void recriar() throws Exception {
        for (JSONObject o : findAll()) {
            say("Apagando " + o.getString("display_name") + "...");
            gh("DELETE", "/user/codespaces/" + o.getString("name"), null);
        }
        Thread.sleep(10000);
        criar();
    }

    void waitUp(int maxSec) throws Exception {
        boolean started = false;
        for (int t = 0; t < maxSec; t += 5) {
            JSONObject o = findCs();
            if (o == null) throw new IOException("O Codespace sumiu");
            String st = o.getString("state");
            if (st.equals("Shutdown") || st.equals("Archived")) {
                if (!started) {
                    gh("POST", "/user/codespaces/" + o.getString("name") + "/start", null);
                    started = true;
                }
            } else if (st.equals("Failed") || st.equals("Deleted")) {
                throw new IOException("Codespace em estado " + st);
            } else if (st.equals("Available") && serverUp()) {
                say("Servidor no ar!\n\nURL MCP:\n" + mcpUrl());
                return;
            }
            String dica = t >= 300 ? "\n\nSe for um Codespace antigo, toque em Recriar Codespace." : "";
            say("Estado: " + st + " | servidor ainda nao respondeu (" + t + "s)\nA primeira vez demora 5 a 10 min. Pode deixar o app aberto." + dica);
            Thread.sleep(5000);
        }
        say("Demorou demais. Toque em Status daqui a pouco.");
    }

    void confirmarRecriar() {
        new AlertDialog.Builder(this)
                .setTitle("Recriar Codespace")
                .setMessage("Apaga o Codespace atual (arquivos dentro dele sao perdidos) e cria um novo que sobe o servidor sozinho. Continuar?")
                .setPositiveButton("Recriar", (d, w) -> bg(this::recriar))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    void testMcp() throws Exception {
        say(http("GET", baseUrl() + "/status", null, null) + "\n\nURL MCP:\n" + mcpUrl());
    }

    void copy() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("mcp", mcpUrl()));
        Toast.makeText(this, "URL copiada!", Toast.LENGTH_SHORT).show();
    }
}
