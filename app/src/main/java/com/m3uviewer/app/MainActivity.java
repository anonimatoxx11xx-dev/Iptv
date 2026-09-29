package com.m3uviewer.app;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    private static final int PICK_FILE = 401;
    private final List<Channel> all = new ArrayList<>();
    private final List<Channel> filtered = new ArrayList<>();
    private ChannelAdapter adapter;
    private EditText search, serverInput, usernameInput, passwordInput;
    private Spinner groups;
    private TextView fileName, stats, resultCount, favoritesCount, heroStatus, credentialSummary;
    private LinearLayout filePanel, iptvPanel, credentialsSummary;
    private TextView modeFile, modeIptv;
    private String selected = "Tutti";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        bindViews();
        setupList();
        setupModes();
        loadSavedCredentials();
        setupGroups();
        filter();
    }

    private void bindViews() {
        search = findViewById(R.id.search);
        groups = findViewById(R.id.groupSpinner);
        fileName = findViewById(R.id.fileName);
        stats = findViewById(R.id.stats);
        resultCount = findViewById(R.id.resultCount);
        heroStatus = findViewById(R.id.heroStatus);
        credentialSummary = findViewById(R.id.credentialSummaryText);
        filePanel = findViewById(R.id.filePanel);
        iptvPanel = findViewById(R.id.iptvPanel);
        credentialsSummary = findViewById(R.id.credentialsSummary);
        modeFile = findViewById(R.id.modeFile);
        modeIptv = findViewById(R.id.modeIptv);
        serverInput = findViewById(R.id.serverInput);
        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        findViewById(R.id.btnOpen).setOnClickListener(v -> openFile());
        findViewById(R.id.btnConnect).setOnClickListener(v -> connectIptv());
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a) {}
            public void onTextChanged(CharSequence s,int st,int b,int c) { filter(); }
            public void afterTextChanged(android.text.Editable e) {}
        });
        groups.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> p) {}
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selected = String.valueOf(p.getItemAtPosition(pos)); filter();
            }
        });
    }

    private void setupList() {
        ListView list = findViewById(R.id.channelList);
        adapter = new ChannelAdapter(this, filtered);
        list.setAdapter(adapter);
        list.setOnItemClickListener((p,v,pos,id) -> show(filtered.get(pos)));
    }

    private void setupModes() {
        modeFile.setOnClickListener(v -> showMode(false));
        modeIptv.setOnClickListener(v -> showMode(true));
        showMode(false);
    }

    private void showMode(boolean iptv) {
        filePanel.setVisibility(iptv ? View.GONE : View.VISIBLE);
        iptvPanel.setVisibility(iptv ? View.VISIBLE : View.GONE);
        modeIptv.setBackgroundResource(iptv ? R.drawable.bg_toggle_selected : R.drawable.bg_toggle);
        modeIptv.setTextColor(iptv ? getColor(R.color.primary) : getColor(R.color.muted));
        modeFile.setBackgroundResource(iptv ? R.drawable.bg_toggle : R.drawable.bg_toggle_selected);
        modeFile.setTextColor(iptv ? getColor(R.color.muted) : getColor(R.color.primary));
        if (iptv) loadSavedCredentials();
    }

    private void openFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, PICK_FILE);
    }

    @Override protected void onActivityResult(int r, int code, Intent d) {
        super.onActivityResult(r, code, d);
        if (r != PICK_FILE || code != RESULT_OK || d == null || d.getData() == null) return;
        try {
            Uri uri = d.getData();
            List<Channel> p = parse(uri);
            all.clear(); all.addAll(p);
            String n = name(uri);
            fileName.setText(n);
            heroStatus.setText(n + " • " + all.size() + " canali");
            stats.setText(all.size() + " canali • " + favCount() + " preferiti");
            credentialsSummary.setVisibility(View.GONE);
            setupGroups();
            filter();
            Toast.makeText(this, "Playlist caricata", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Errore lettura playlist: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private List<Channel> parse(Uri u) throws Exception {
        InputStream in = getContentResolver().openInputStream(u);
        if (in == null) throw new IOException("file non accessibile");
        return parseStream(in);
    }

    private List<Channel> parseStream(InputStream input) throws Exception {
        List<Channel> out = new ArrayList<>();
        BufferedReader br = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        String line, n = null, g = "Senza gruppo", id = "", logo = "";
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF")) {
                int c = line.indexOf(',');
                n = c >= 0 ? line.substring(c + 1).trim() : "Canale senza nome";
                g = attr(line, "group-title", "Senza gruppo");
                id = attr(line, "tvg-id", "");
                logo = attr(line, "tvg-logo", "");
            } else if (line.startsWith("#EXTGRP:")) {
                g = line.substring(8).trim();
            } else if (!line.startsWith("#")) {
                if (n == null) n = line;
                out.add(new Channel(n, line, g, id, logo));
                n = null; g = "Senza gruppo"; id = ""; logo = "";
            }
        }
        br.close();
        return out;
    }

    private void connectIptv() {
        String server = serverInput.getText().toString().trim();
        String user = usernameInput.getText().toString().trim();
        String pass = passwordInput.getText().toString();
        if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Inserisci server, nome utente e password", Toast.LENGTH_LONG).show();
            return;
        }

        CheckBox save = findViewById(R.id.saveCredentials);
        if (save.isChecked()) {
            getPreferences(0).edit()
                .putString("server", server).putString("username", user)
                .putString("password", pass).apply();
            credentialsSummary.setVisibility(View.VISIBLE);
            credentialSummary.setText(server + "\nUtente: " + user + " • Password: salvata");
        }

        heroStatus.setText("Connessione in corso…");
        findViewById(R.id.btnConnect).setEnabled(false);

        new Thread(() -> {
            try {
                String base = server.endsWith("/") ? server.substring(0, server.length() - 1) : server;
                String url = base + "/get.php?username=" + URLEncoder.encode(user, "UTF-8")
                        + "&password=" + URLEncoder.encode(pass, "UTF-8")
                        + "&type=m3u_plus&output=ts";
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(12000); c.setReadTimeout(30000);
                c.setRequestProperty("User-Agent", "IPTV Viewer/1.0");
                int code = c.getResponseCode();
                if (code < 200 || code >= 300) throw new IOException("server HTTP " + code);
                InputStream in = c.getInputStream();
                List<Channel> p = parseStream(in);
                c.disconnect();
                runOnUiThread(() -> {
                    all.clear(); all.addAll(p);
                    heroStatus.setText("Account collegato • " + all.size() + " canali");
                    stats.setText(all.size() + " canali • " + favCount() + " preferiti");
                    credentialsSummary.setVisibility(View.VISIBLE);
                    credentialSummary.setText(base + "\nUtente: " + user + " • Password: ••••••••");
                    setupGroups(); filter();
                    Toast.makeText(this, "Playlist IPTV caricata", Toast.LENGTH_SHORT).show();
                    findViewById(R.id.btnConnect).setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    heroStatus.setText("Connessione non riuscita");
                    Toast.makeText(this, "Errore IPTV: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    findViewById(R.id.btnConnect).setEnabled(true);
                });
            }
        }).start();
    }

    private void loadSavedCredentials() {
        String server = getPreferences(0).getString("server", "");
        String user = getPreferences(0).getString("username", "");
        String pass = getPreferences(0).getString("password", "");
        if (!server.isEmpty() || !user.isEmpty()) {
            serverInput.setText(server);
            usernameInput.setText(user);
            passwordInput.setText(pass);
            ((CheckBox)findViewById(R.id.saveCredentials)).setChecked(true);
            credentialsSummary.setVisibility(View.VISIBLE);
            credentialSummary.setText(server + "\nUtente: " + user + " • Password: salvata");
        }
    }

    private String attr(String line, String key, String fallback) {
        Matcher m = Pattern.compile(key + "\\s*=\\s*\\\"([^\\\"]*)\\\"", Pattern.CASE_INSENSITIVE).matcher(line);
        return m.find() ? m.group(1) : fallback;
    }

    private String name(Uri u) {
        try (Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception ignored) {}
        return "Playlist M3U";
    }

    private void setupGroups() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add("Tutti");
        for (Channel c : all) set.add(c.group);
        ArrayAdapter<String> a = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<>(set)) {
            @Override public View getView(int p, View v, ViewGroup parent) {
                TextView t = (TextView) super.getView(p, v, parent);
                t.setTextColor(getColor(R.color.ink)); t.setPadding(12,0,12,0); return t;
            }
        };
        groups.setAdapter(a); selected = "Tutti"; groups.setSelection(0);
    }

    private void filter() {
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (Channel c : all) {
            boolean groupOk = "Tutti".equals(selected) || c.group.equals(selected);
            boolean textOk = q.isEmpty() || c.name.toLowerCase(Locale.ROOT).contains(q)
                    || c.group.toLowerCase(Locale.ROOT).contains(q) || c.id.toLowerCase(Locale.ROOT).contains(q);
            if (groupOk && textOk) filtered.add(c);
        }
        adapter.notifyDataSetChanged();
        resultCount.setText(String.valueOf(filtered.size()));
        stats.setText(all.size() + " canali • " + favCount() + " preferiti");
    }

    private boolean fav(String u) { return getPreferences(0).getBoolean("fav_" + Integer.toHexString(u.hashCode()), false); }
    private int favCount() { int n=0; for(Channel c:all) if(fav(c.url)) n++; return n; }

    private void toggle(Channel c) {
        String k="fav_"+Integer.toHexString(c.url.hashCode());
        getPreferences(0).edit().putBoolean(k,!fav(c.url)).apply();
        filter();
    }

    private void show(Channel c) {
        new android.app.AlertDialog.Builder(this).setTitle(c.name)
            .setMessage("Categoria: " + c.group + (c.id.isEmpty() ? "" : "\nID: " + c.id) + (c.logo.isEmpty() ? "" : "\nLogo: " + c.logo) + "\n\nURL:\n" + c.url)
            .setPositiveButton("Copia URL",(d,w)->{
                ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                cb.setPrimaryClip(ClipData.newPlainText("M3U URL",c.url));
                Toast.makeText(this,"URL copiato",Toast.LENGTH_SHORT).show();
            })
            .setNeutralButton("Apri URL",(d,w)->{
                try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(c.url))); }
                catch(Exception e) { Toast.makeText(this,"Nessuna app compatibile",Toast.LENGTH_SHORT).show(); }
            })
            .setNegativeButton("Chiudi",null).show();
    }

    private class ChannelAdapter extends ArrayAdapter<Channel> {
        ChannelAdapter(Context c,List<Channel>d){super(c,0,d);}
        @Override public View getView(int p,View v,ViewGroup parent){
            if(v==null)v=LayoutInflater.from(MainActivity.this).inflate(R.layout.item_channel,parent,false);
            Channel c=getItem(p);
            ((TextView)v.findViewById(R.id.channelIndex)).setText(String.valueOf(p+1));
            ((TextView)v.findViewById(R.id.channelName)).setText(c.name);
            ((TextView)v.findViewById(R.id.channelGroup)).setText(c.group + (c.id.isEmpty() ? "" : " • " + c.id));
            TextView f=v.findViewById(R.id.favorite);
            f.setText(fav(c.url) ? "★" : "☆");
            f.setOnClickListener(x->toggle(c));
            return v;
        }
    }

    static class Channel {
        final String name,url,group,id,logo;
        Channel(String n,String u,String g,String i,String l){
            name=n==null||n.isEmpty()?"Canale senza nome":n; url=u;
            group=g==null||g.isEmpty()?"Senza gruppo":g; id=i==null?"":i; logo=l==null?"":l;
        }
    }
}