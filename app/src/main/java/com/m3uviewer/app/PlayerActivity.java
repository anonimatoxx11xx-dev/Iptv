package com.m3uviewer.app;

import android.app.Activity;
import android.os.Bundle;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
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

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        playerView = findViewById(R.id.playerView);
        status = findViewById(R.id.playerStatus);
        TextView title = findViewById(R.id.playerTitle);
        Button back = findViewById(R.id.backButton);

        String url = getIntent().getStringExtra("url");
        String name = getIntent().getStringExtra("name");
        title.setText(name == null || name.isEmpty() ? "IPTV VIEWER" : name);
        back.setOnClickListener(v -> finish());

        if (url == null || url.trim().isEmpty()) {
            status.setText("URL stream non disponibile");
            return;
        }

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("IPTV Viewer/3.0")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(12000)
                .setReadTimeoutMs(20000);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(httpFactory))
                .build();
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) status.setText("Caricamento stream…");
                else if (state == Player.STATE_READY) status.setText("● LIVE");
                else if (state == Player.STATE_ENDED) status.setText("Stream terminato");
            }

            @Override public void onPlayerError(PlaybackException error) {
                String detail = error.getMessage();
                if (detail == null || detail.isEmpty()) detail = "Codice " + error.errorCode;
                status.setText("Errore stream: " + detail);
            }
        });

        try {
            MediaItem item = MediaItem.fromUri(Uri.parse(url));
            player.setMediaItem(item);
            player.prepare();
            player.play();
        } catch (Exception e) {
            status.setText("URL non valido");
        }
    }

    @Override protected void onStop() {
        super.onStop();
        if (player != null) player.pause();
    }

    @Override protected void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}