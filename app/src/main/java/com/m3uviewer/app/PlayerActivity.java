package com.m3uviewer.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Map;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

public class PlayerActivity extends Activity {
    private ExoPlayer player;
    private PlayerView playerView;
    private TextView status;
    private String originalUrl;
    private String fallbackUrl;
    private boolean fallbackTried = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        playerView = findViewById(R.id.playerView);
        status = findViewById(R.id.playerStatus);
        TextView title = findViewById(R.id.playerTitle);
        Button back = findViewById(R.id.backButton);
        Button retry = findViewById(R.id.retryButton);
        Button external = findViewById(R.id.externalButton);

        originalUrl = getIntent().getStringExtra("url");
        String name = getIntent().getStringExtra("name");
        String referer = getIntent().getStringExtra("referer");

        title.setText(name == null || name.isEmpty() ? "IPTV VIEWER" : name);
        back.setOnClickListener(v -> finish());
        retry.setOnClickListener(v -> {
            fallbackTried = false;
            releasePlayer();
            startPlayback(originalUrl, referer);
        });
        external.setOnClickListener(v -> {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(originalUrl));
                startActivity(i);
            } catch (Exception e) {
                status.setText("Nessun player esterno disponibile");
            }
        });

        if (originalUrl == null || originalUrl.trim().isEmpty()) {
            status.setText("URL stream non disponibile");
            return;
        }
        fallbackUrl = buildFallback(originalUrl);
        startPlayback(originalUrl, referer);
    }

    private void startPlayback(String url, String referer) {
        if (url == null || url.trim().isEmpty()) {
            status.setText("URL stream non disponibile");
            return;
        }
        status.setText("Connessione allo stream…");

        Map<String,String> headers = new HashMap<>();
        headers.put("User-Agent", "IPTV Viewer/5.6");
        headers.put("Accept", "*/*");
        if (referer != null && !referer.isEmpty()) headers.put("Referer", referer);

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(headers)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(30000);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(httpFactory))
                .build();
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) status.setText("Caricamento stream…");
                else if (state == Player.STATE_READY) status.setText("●  LIVE / RIPRODUZIONE");
                else if (state == Player.STATE_ENDED) status.setText("Stream terminato");
            }

            @Override public void onPlayerError(PlaybackException error) {
                if (!fallbackTried && fallbackUrl != null && !fallbackUrl.equals(url)) {
                    fallbackTried = true;
                    status.setText("Provo formato alternativo…");
                    releasePlayer();
                    startPlayback(fallbackUrl, referer);
                    return;
                }
                String detail = error.getMessage();
                if (detail == null || detail.isEmpty()) detail = "codice " + error.errorCode;
                status.setText("Stream non riproducibile: " + detail);
            }
        });

        try {
            MediaItem.Builder builder = new MediaItem.Builder().setUri(Uri.parse(url));
            String lower = url.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains(".m3u8")) builder.setMimeType(MimeTypes.APPLICATION_M3U8);
            else if (lower.contains(".ts")) builder.setMimeType(MimeTypes.VIDEO_MP2T);
            player.setMediaItem(builder.build());
            player.prepare();
            player.play();
        } catch (Exception e) {
            status.setText("URL non valido: " + e.getMessage());
        }
    }

    private String buildFallback(String url) {
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains(".m3u8")) return url.replaceAll("(?i)\\.m3u8(\\?.*)?$", ".ts$1");
        if (lower.contains(".ts")) return url.replaceAll("(?i)\\.ts(\\?.*)?$", ".m3u8$1");
        return null;
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override protected void onStop() {
        super.onStop();
        if (player != null) player.pause();
    }

    @Override protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
}