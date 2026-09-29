package com.m3uviewer.app;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int PICK_FILE = 401;
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 45000;
    private static final int DISCOVERY_CONNECT_TIMEOUT_MS = 2500;
    private static final int DISCOVERY_READ_TIMEOUT_MS = 5000;
    private static final int DISPLAY_LIMIT = 500;
    private int matchingCount = 0;
    private final android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingFilter;
    private final List<Channel> all = new ArrayList<>();
    private final List<Channel> filtered = new ArrayList<>();
    private ChannelAdapter adapter;
    private EditText search, serverInput, portInput, usernameInput, passwordInput;
    private Spinner groups;
    private TextView fileName, stats, resultCount, heroStatus, credentialSummary;
    private LinearLayout filePanel, iptvPanel, credentialsSummary;
    private TextView modeFile, modeIptv;
    private ProgressBar loading;
    private Button connectButton;
    private String selected = "Tutti";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        applySystemInsets();
        bindViews();
        setupList();
        setupModes();
        loadSavedCredentials();
        setupGroups();
        filter();
    }

    private void applySystemInsets() {
        final View root = findViewById(android.R.id.content);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(
                    android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
                v.setPadding(v.getPaddingLeft(), dp(14) + i.top, v.getPaddingRight(), dp(28) + i.bottom);
            }
            return insets;
        });
        root.requestApplyInsets();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
        loading = findViewById(R.id.loading);
        connectButton = findViewById(R.id.btnConnect);
        serverInput = findViewById(R.id.serverInput);
        portInput = findViewById(R.id.portInput);
        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);

        findViewById(R.id.btnOpen).setOnClickListener(v -> openFile());
        connectButton.setOnClickListener(v -> connectIptv());

        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a) {}
            public void onTextChanged(CharSequence s,int st,int b,int c) {
                if (pendingFilter != null) searchHandler.removeCallbacks(pendingFilter);
                pendingFilter = MainActivity.this::filter;
                searchHandler.postDelayed(pendingFilter, 180);
            }
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
        list.setOnItemClickListener((p,v,pos,id) -> playChannel(filtered.get(pos)));
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
            setupGroups(); filter();
            Toast.makeText(this, p.isEmpty() ? "File M3U vuoto" : "Playlist caricata", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Errore M3U: " + cleanError(e), Toast.LENGTH_LONG).show();
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
            line = line.replace("\uFEFF","").trim();
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
        final String server = serverInput.getText().toString().trim();
        final String port = portInput.getText().toString().trim();
        final String user = usernameInput.getText().toString().trim();
        final String pass = passwordInput.getText().toString();

        if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Inserisci server, nome utente e password", Toast.LENGTH_LONG).show();
            return;
        }
        if (!port.isEmpty()) {
            try {
                int p = Integer.parseInt(port);
                if (p < 1 || p > 65535) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "La porta deve essere tra 1 e 65535", Toast.LENGTH_LONG).show();
                return;
            }
        }

        final CheckBox save = findViewById(R.id.saveCredentials);
        if (save.isChecked()) saveCredentials(server, port, user, pass);
        else getPreferences(0).edit().clear().apply();

        setLoading(true);
        heroStatus.setText("Connessione in corso…");
        new Thread(() -> performIptvConnection(server, port, user, pass), "iptv-connect").start();
    }

    private void performIptvConnection(String server, String port, String user, String pass) {
        // Known provider endpoint for the configured server IP.
        // Username/password are taken only from the current form fields.
        if (server.trim().equals("185.160.192.91") && port.trim().isEmpty()) {
            String directM3u = "http://netherland.warstarlive.com:6923/get.php?username=" + enc(user)
                    + "&password=" + enc(pass) + "&type=m3u_plus&output=mpegts";
            runOnUiThread(() -> heroStatus.setText("Server IPTV trovato • caricamento playlist…"));
            loadM3uFromUrl(directM3u, user, pass);
            return;
        }

        // If the user pastes a complete Xtream/M3U URL, use it directly.
        if (server.contains("?") && (server.contains("/get.php") || server.contains("/player_api.php"))) {
            try {
                String directUrl = server.trim();
                Uri parsed = Uri.parse(directUrl);
                String directUser = parsed.getQueryParameter("username");
                String directPass = parsed.getQueryParameter("password");
                if (directUser != null && directPass != null) {
                    String directBase = parsed.getScheme() + "://" + parsed.getAuthority();
                    String m3u = directUrl;
                    final String shownBase = directBase;
                    runOnUiThread(() -> heroStatus.setText("Server trovato: " + shownBase + " • caricamento playlist…"));
                    loadM3uFromUrl(m3u, directUser, directPass);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        String base;
        Exception first = null;
        try {
            base = discoverOrNormalizeServer(server, port, user, pass);
            final String foundBase = base;
            runOnUiThread(() -> heroStatus.setText("Server trovato: " + foundBase + " • accesso Xtream…"));
        } catch (Exception discoveryError) {
            runOnUiThread(() -> {
                setLoading(false);
                heroStatus.setText("Server IPTV non trovato");
                new android.app.AlertDialog.Builder(this)
                    .setTitle("Server IPTV non trovato")
                    .setMessage("Non è stato trovato automaticamente un servizio Xtream sul server indicato.\n\n" + cleanError(discoveryError) + "\n\nL’app ha provato automaticamente le configurazioni Xtream supportate.")
                    .setPositiveButton("OK", null)
                    .show();
            });
            return;
        }
        try {
            PlayerResult api = tryXtreamApi(base, user, pass);
            if (!api.channels.isEmpty()) {
                finishConnection(api.channels, base, user, "Xtream API");
                return;
            }
            first = new IOException("Il server ha risposto ma non ha restituito canali live");
        } catch (Exception e) {
            first = e;
        }

        try {
            String m3uUrl = base + "/get.php?username=" + enc(user) + "&password=" + enc(pass) + "&type=m3u_plus&output=mpegts";
            HttpURLConnection c = open(m3uUrl);
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
            List<Channel> channels = parseStream(c.getInputStream());
            c.disconnect();
            if (channels.isEmpty()) throw new IOException("playlist M3U vuota");
            finishConnection(channels, base, user, "M3U");
        } catch (Exception e) {
            final Exception apiError = first;
            runOnUiThread(() -> {
                setLoading(false);
                heroStatus.setText("Connessione non riuscita");
                connectButton.setEnabled(true);
                String detail = describeConnectionError(apiError, e, base);
                new android.app.AlertDialog.Builder(this)
                    .setTitle("Connessione IPTV non riuscita")
                    .setMessage(detail)
                    .setPositiveButton("OK", null)
                    .show();
            });
        }
    }

    private String discoverOrNormalizeServer(String server, String port, String user, String pass) throws Exception {
        String raw = server.trim();
        if (!port.isEmpty() || hasExplicitPort(raw)) return normalizeServer(raw, port);

        String prepared = raw.matches("(?i)^https?://.*") ? raw : "http://" + raw;
        Uri u = Uri.parse(prepared);
        String host = u.getHost();
        if (host == null || host.isEmpty()) throw new IllegalArgumentException("server non valido");
        String dnsHost = knownProviderHost(host);
        if (dnsHost == null) {
            try {
                String canonical = InetAddress.getByName(host).getCanonicalHostName();
                if (canonical != null && !canonical.equalsIgnoreCase(host)
                        && !canonical.matches("^\\d+(?:\\.\\d+){3}$")) dnsHost = canonical;
            } catch (Exception ignored) {}
        }

        String path = u.getPath();
        if (path == null) path = "";
        while (path.endsWith("/") && !path.isEmpty()) path = path.substring(0, path.length() - 1);

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (knownProviderHost(host) != null) {
            addCandidates(candidates, "http", knownProviderHost(host), 6923, path);
        }
        int[] httpPorts = {80, 8080, 8000, 8880, 6923, 25461, 25460, 25462, 2052, 2053, 2082, 2083, 2086, 2087, 2095, 2096, 8001, 8081};
        int[] httpsPorts = {443, 8443, 25463, 4433};
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
        if (dnsHost != null) {
            if ("https".equals(scheme)) {
                for (int p : httpsPorts) addCandidates(candidates, "https", dnsHost, p, path);
            } else {
                for (int p : httpPorts) addCandidates(candidates, "http", dnsHost, p, path);
                for (int p : httpsPorts) addCandidates(candidates, "https", dnsHost, p, path);
            }
        }

        if ("https".equals(scheme)) {
            for (int p : httpsPorts) addCandidates(candidates, "https", host, p, path);
        } else {
            for (int p : httpPorts) addCandidates(candidates, "http", host, p, path);
            for (int p : httpsPorts) addCandidates(candidates, "https", host, p, path);
        }

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(10, Math.max(1, candidates.size())));
        CompletionService<String> completion = new ExecutorCompletionService<>(executor);
        int submitted = 0;
        for (String candidate : candidates) {
            final String base = candidate;
            completion.submit(() -> probeXtream(base, user, pass) ? base : null);
            submitted++;
        }

        try {
            for (int i = 0; i < submitted; i++) {
                Future<String> future = completion.take();
                String found = future.get();
                if (found != null) {
                    executor.shutdownNow();
                    return stripXtreamEndpoint(found);
                }
            }
        } catch (ExecutionException e) {
            // Continue until all candidates have been checked.
        } finally {
            executor.shutdownNow();
        }
        throw new IOException("nessun endpoint Xtream trovato automaticamente");
    }

    private String stripXtreamEndpoint(String url) {
        if (url == null) return null;
        String s = url.trim();
        String lower = s.toLowerCase(Locale.ROOT);
        String[] suffixes = {"/player_api.php", "/get.php"};
        for (String suffix : suffixes) {
            if (lower.endsWith(suffix)) {
                return s.substring(0, s.length() - suffix.length());
            }
        }
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private String knownProviderHost(String host) {
        if ("185.160.192.91".equals(host)) return "netherland.warstarlive.com";
        return null;
    }

    private void addCandidates(Set<String> set, String scheme, String host, int port, String path) {
        String h = host;
        if (h.contains(":") && !h.startsWith("[")) h = "[" + h + "]";
        String[] paths = {"", "/player_api.php", "/c", "/iptv", "/api"};
        for (String suffix : paths) {
            String p = path + suffix;
            if (p.endsWith("/player_api.php")) {
                set.add(scheme + "://" + h + ":" + port + p);
            } else {
                set.add(scheme + "://" + h + ":" + port + p);
            }
        }
    }

    private boolean hasExplicitPort(String server) {
        try {
            String s = server.matches("(?i)^https?://.*") ? server : "http://" + server;
            Uri u = Uri.parse(s);
            return u.getPort() != -1;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean probeXtream(String base, String user, String pass) throws Exception {
        String cleanBase = base;
        String apiBase = cleanBase;
        if (cleanBase.endsWith("/player_api.php")) {
            apiBase = cleanBase.substring(0, cleanBase.length() - "/player_api.php".length());
        }
        String apiUrl = apiBase + "/player_api.php?username=" + enc(user) + "&password=" + enc(pass);
        HttpURLConnection c = null;
        try {
            c = open(apiUrl, DISCOVERY_CONNECT_TIMEOUT_MS, DISCOVERY_READ_TIMEOUT_MS);
            int code = c.getResponseCode();
            if (code >= 200 && code < 300) {
                String body = readLimited(c.getInputStream(), 512_000).trim();
                if (body.startsWith("{")) {
                    JSONObject root = new JSONObject(body);
                    JSONObject info = root.optJSONObject("user_info");
                    if (root.has("server_info") || info != null) {
                        if (info == null || info.optString("auth", "1").equals("1") || info.optString("status", "").equalsIgnoreCase("Active")) return true;
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.disconnect();
        }

        String m3uUrl = apiBase + "/get.php?username=" + enc(user) + "&password=" + enc(pass) + "&type=m3u_plus&output=mpegts";
        c = null;
        try {
            c = open(m3uUrl, DISCOVERY_CONNECT_TIMEOUT_MS, DISCOVERY_READ_TIMEOUT_MS);
            int code = c.getResponseCode();
            if (code >= 200 && code < 300) {
                String body = readPrefix(c.getInputStream(), 65536).trim();
                return body.contains("#EXTM3U") || body.contains("#EXTINF");
            }
        } finally {
            if (c != null) c.disconnect();
        }
        return false;
    }
    private void loadM3uFromUrl(String url, String user, String pass) {
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                c = open(url);
                int code = c.getResponseCode();
                if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
                List<Channel> parsed = parseStream(c.getInputStream());
                if (parsed.isEmpty()) throw new IOException("playlist M3U vuota");
                Uri u = Uri.parse(url);
                String base = u.getScheme() + "://" + u.getAuthority();
                finishConnection(parsed, base, user, "M3U Xtream");
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    connectButton.setEnabled(true);
                    heroStatus.setText("Errore caricamento playlist");
                    new android.app.AlertDialog.Builder(this)
                        .setTitle("Errore playlist")
                        .setMessage(cleanError(e))
                        .setPositiveButton("OK", null).show();
                });
            } finally {
                if (c != null) c.disconnect();
            }
        }, "iptv-m3u").start();
    }

    private PlayerResult tryXtreamApi(String base, String user, String pass) throws Exception {
        String authUrl = base + "/player_api.php?username=" + enc(user) + "&password=" + enc(pass);
        HttpURLConnection auth = open(authUrl);
        int code = auth.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
        String body = readLimited(auth.getInputStream(), 2_000_000);
        auth.disconnect();
        JSONObject root = new JSONObject(body);
        if (root.has("user_info")) {
            JSONObject ui = root.optJSONObject("user_info");
            String status = ui == null ? "" : ui.optString("status", "");
            if ("Disabled".equalsIgnoreCase(status) || "Expired".equalsIgnoreCase(status))
                throw new IOException("account IPTV non attivo");
        }

        JSONArray cats = fetchJsonArray(base + "/player_api.php?username=" + enc(user) + "&password=" + enc(pass) + "&action=get_live_categories");
        Map<String,String> categoryMap = new HashMap<>();
        for(int i=0;i<cats.length();i++){
            JSONObject o=cats.optJSONObject(i);
            if(o!=null) categoryMap.put(o.optString("category_id",""), o.optString("category_name","Senza gruppo"));
        }

        JSONArray streams = fetchJsonArray(base + "/player_api.php?username=" + enc(user) + "&password=" + enc(pass) + "&action=get_live_streams");
        List<Channel> channels = new ArrayList<>();
        for(int i=0;i<streams.length();i++){
            JSONObject o=streams.optJSONObject(i);
            if(o==null) continue;
            String id=o.optString("stream_id","");
            if(id.isEmpty()) continue;
            String n=o.optString("name","Canale senza nome");
            String cat=categoryMap.get(o.optString("category_id",""));
            if(cat==null||cat.isEmpty()) cat="Senza gruppo";
            String logo=o.optString("stream_icon","");
            String streamUrl=base+"/live/"+enc(user)+"/"+enc(pass)+"/"+id+".ts";
            channels.add(new Channel(n,streamUrl,cat,id,logo));
        }
        return new PlayerResult(channels);
    }

    private JSONArray fetchJsonArray(String url) throws Exception {
        HttpURLConnection c=open(url);
        int code=c.getResponseCode();
        if(code<200||code>=300) throw new IOException("HTTP "+code);
        String body=readLimited(c.getInputStream(), 20_000_000);
        c.disconnect();
        return new JSONArray(body);
    }

    private HttpURLConnection open(String url) throws Exception {
        return open(url, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }

    private HttpURLConnection open(String url, int connectTimeout, int readTimeout) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(connectTimeout);
        c.setReadTimeout(readTimeout);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent","IPTV Viewer/2.0");
        c.setRequestProperty("Accept","*/*");
        c.setRequestProperty("Connection","close");
        return c;
    }

    private String normalizeServer(String server, String port) {
        String s = server.trim();
        if (!s.matches("(?i)^https?://.*")) s = "http://" + s;
        Uri u = Uri.parse(s);
        String scheme = u.getScheme() == null ? "http" : u.getScheme();
        String host = u.getHost();
        if (host == null || host.isEmpty()) throw new IllegalArgumentException("server non valido");
        int p = u.getPort();
        if (!port.trim().isEmpty()) p = Integer.parseInt(port.trim());
        String path = u.getPath() == null ? "" : u.getPath();
        if (p > 0) return scheme + "://" + host + ":" + p + path;
        return scheme + "://" + host + path;
    }
    private String enc(String s) { try{return URLEncoder.encode(s,"UTF-8");}catch(Exception e){return s;} }

    private String readPrefix(InputStream in, int maxBytes) throws Exception {
        try (InputStream raw = in; BufferedInputStream bin = new BufferedInputStream(raw);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int total = 0;
            while (total < maxBytes) {
                int want = Math.min(buf.length, maxBytes - total);
                int n = bin.read(buf, 0, want);
                if (n == -1) break;
                out.write(buf, 0, n);
                total += n;
                String s = out.toString(StandardCharsets.UTF_8.name());
                if (s.contains("#EXTM3U") || s.contains("#EXTINF")) return s;
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private String readLimited(InputStream in, int maxBytes) throws Exception {
        try(InputStream raw=in; BufferedInputStream bin=new BufferedInputStream(raw);
            ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buf=new byte[8192]; int n,total=0;
            while((n=bin.read(buf))!=-1){
                total+=n;
                if(total>maxBytes) throw new IOException("risposta del server troppo grande");
                out.write(buf,0,n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private void finishConnection(List<Channel> channels, String base, String user, String source) {
        runOnUiThread(() -> {
            setLoading(false);
            all.clear(); all.addAll(channels);
            heroStatus.setText("Account collegato • " + all.size() + " canali • " + source);
            stats.setText(all.size() + " canali • " + favCount() + " preferiti");
            credentialsSummary.setVisibility(View.VISIBLE);
            credentialSummary.setText(base + "\nUtente: " + user + " • Password: ••••••••");
            setupGroups(); filter();
            connectButton.setEnabled(true);
            Toast.makeText(this, "Playlist caricata", Toast.LENGTH_SHORT).show();
        });
    }

    private void setLoading(boolean value) {
        loading.setVisibility(value ? View.VISIBLE : View.GONE);
        connectButton.setEnabled(!value);
    }

    private void saveCredentials(String server,String port,String user,String pass) {
        getPreferences(0).edit()
            .putString("server",server).putString("port",port)
            .putString("username",user).putString("password",pass).apply();
        credentialsSummary.setVisibility(View.VISIBLE);
        credentialSummary.setText(server + (port.isEmpty()?"":":"+port) + "\nUtente: " + user + " • Password: salvata");
    }

    private void loadSavedCredentials() {
        String server=getPreferences(0).getString("server","");
        String port=getPreferences(0).getString("port","");
        String user=getPreferences(0).getString("username","");
        String pass=getPreferences(0).getString("password","");
        if(!server.isEmpty()||!user.isEmpty()) {
            serverInput.setText(server); portInput.setText(port);
            usernameInput.setText(user); passwordInput.setText(pass);
            ((CheckBox)findViewById(R.id.saveCredentials)).setChecked(true);
            credentialsSummary.setVisibility(View.VISIBLE);
            credentialSummary.setText(server+(port.isEmpty()?"":":"+port)+"\nUtente: "+user+" • Password: salvata");
        }
    }

    private String describeConnectionError(Exception api, Exception m3u, String base) {
        String msg="Server: "+base+"\n\n";
        if(api!=null) msg+="Accesso API: "+cleanError(api)+"\n";
        msg+="Playlist M3U: "+cleanError(m3u)+"\n\n";
        msg+="Controlla server e porta del tuo servizio IPTV. Esempio: http://server:8080";
        return msg;
    }

    private String cleanError(Throwable e) {
        if(e==null) return "errore sconosciuto";
        String m=e.getMessage();
        return (m==null||m.trim().isEmpty()) ? e.getClass().getSimpleName() : m;
    }

    private String attr(String line,String key,String fallback) {
        Matcher m=Pattern.compile(key+"\\s*=\\s*\\\"([^\\\"]*)\\\"",Pattern.CASE_INSENSITIVE).matcher(line);
        return m.find()?m.group(1):fallback;
    }

    private String name(Uri u) {
        try(Cursor c=getContentResolver().query(u,null,null,null,null)) {
            if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0)return c.getString(i);}
        } catch(Exception ignored){}
        return "Playlist M3U";
    }

    private void setupGroups() {
        LinkedHashSet<String> set=new LinkedHashSet<>(); set.add("Tutti");
        for(Channel c:all) set.add(c.group);
        ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new ArrayList<>(set)){
            @Override public View getView(int p,View v,ViewGroup parent){
                TextView t=(TextView)super.getView(p,v,parent);
                t.setTextColor(getColor(R.color.ink)); t.setPadding(12,0,12,0); return t;
            }
        };
        groups.setAdapter(a); selected="Tutti"; groups.setSelection(0);
    }

    private void filter() {
        String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        matchingCount=0;
        for(Channel c:all){
            boolean groupOk="Tutti".equals(selected)||c.group.equals(selected);
            boolean textOk=q.isEmpty()||c.name.toLowerCase(Locale.ROOT).contains(q)
                ||c.group.toLowerCase(Locale.ROOT).contains(q)||c.id.toLowerCase(Locale.ROOT).contains(q);
            if(groupOk&&textOk){
                matchingCount++;
                if(filtered.size()<DISPLAY_LIMIT) filtered.add(c);
            }
        }
        adapter.notifyDataSetChanged();
        resultCount.setText(matchingCount>DISPLAY_LIMIT ? DISPLAY_LIMIT+"+" : String.valueOf(matchingCount));
        stats.setText(all.size()+" canali • "+favCount()+" preferiti");
    }

    private boolean fav(String u){return getPreferences(0).getBoolean("fav_"+Integer.toHexString(u.hashCode()),false);}
    private int favCount(){int n=0;for(Channel c:all)if(fav(c.url))n++;return n;}
    private void toggle(Channel c){
        getPreferences(0).edit().putBoolean("fav_"+Integer.toHexString(c.url.hashCode()),!fav(c.url)).apply(); filter();
    }

    private void playChannel(Channel c) {
        if (c == null || c.url == null || c.url.trim().isEmpty()) {
            Toast.makeText(this, "Stream non disponibile", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("url", c.url);
        i.putExtra("name", c.name);
        startActivity(i);
    }

    private void show(Channel c) {
        new android.app.AlertDialog.Builder(this).setTitle(c.name)
            .setMessage("Categoria: "+c.group+(c.id.isEmpty()?"":"\nID: "+c.id)+(c.logo.isEmpty()?"":"\nLogo: "+c.logo)+"\n\nURL:\n"+c.url)
            .setPositiveButton("Copia URL",(d,w)->{
                ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                cb.setPrimaryClip(ClipData.newPlainText("M3U URL",c.url));
                Toast.makeText(this,"URL copiato",Toast.LENGTH_SHORT).show();
            })
            .setNeutralButton("Apri URL",(d,w)->{
                try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(c.url)));}catch(Exception e){
                    Toast.makeText(this,"Nessuna app compatibile con questo stream",Toast.LENGTH_SHORT).show();
                }
            }).setNegativeButton("Chiudi",null).show();
    }

    private class ChannelAdapter extends ArrayAdapter<Channel>{
        ChannelAdapter(Context c,List<Channel>d){super(c,0,d);}
        @Override public View getView(int p,View v,ViewGroup parent){
            if(v==null)v=LayoutInflater.from(MainActivity.this).inflate(R.layout.item_channel,parent,false);
            Channel c=getItem(p);
            ((TextView)v.findViewById(R.id.channelIndex)).setText(String.valueOf(p+1));
            ((TextView)v.findViewById(R.id.channelName)).setText(c.name);
            ((TextView)v.findViewById(R.id.channelGroup)).setText(c.group+(c.id.isEmpty()?"":" • "+c.id));
            TextView f=v.findViewById(R.id.favorite); f.setText(fav(c.url)?"★":"☆"); f.setOnClickListener(x->toggle(c));
            return v;
        }
    }

    static class PlayerResult { final List<Channel> channels; PlayerResult(List<Channel> c){channels=c;} }

    static class Channel {
        final String name,url,group,id,logo;
        Channel(String n,String u,String g,String i,String l){
            name=n==null||n.isEmpty()?"Canale senza nome":n; url=u;
            group=g==null||g.isEmpty()?"Senza gruppo":g; id=i==null?"":i; logo=l==null?"":l;
        }
    }
}