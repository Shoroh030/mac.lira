package com.lira.vmest;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One-to-one synchronized playback with opt-in camera, voice and text chat. */
public final class WatchPartyActivity extends Activity {
    public static final String EXTRA_ROOM_ID = "room_id";
    public static final String EXTRA_HOST = "host";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_PLAYER_URL = "player_url";
    private static final int REQUEST_MIC = 7102;
    private static final int REQUEST_CAMERA = 7104;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService media = Executors.newSingleThreadExecutor();
    private final List<IceCandidate> pendingCandidates = new ArrayList<>();
    private final Object offerLock = new Object();
    private SupabaseSync sync;
    private String roomId, title;
    private String playerUrl;
    private boolean host, remoteReady;
    private volatile boolean closed;
    private boolean applyingRemotePlayback;
    private boolean remoteVideoAvailable, fullscreen = false, rtcReady;
    private long lastSignalId, suppressPlaybackSyncUntil, lastPlaybackSignalAt;
    private long lastYoutubePositionMs, lastYoutubeSampleAt;
    private boolean youtubeStateKnown, lastYoutubePlaying, playbackRequestSent;
    private boolean playerReady;
    private JSONObject pendingPlaybackState;
    private LinearLayout root;
    private LinearLayout topPanel;
    private ScrollView controlsScroll;
    private TextView status, roomCode;
    private WebView playerView;
    private FrameLayout playerContainer;
    private Button fullscreenButton;
    private PlayerView directPlayerView;
    private EditText playerAddress;
    private LinearLayout videoRow;
    private TextView privacyStatus;
    private Button cameraButton, micButton;
    private ExoPlayer directPlayer;
    private WatchPlayerSource playerSource;
    private SurfaceViewRenderer remoteView;
    private SurfaceViewRenderer localCameraView;
    private VideoTrack remoteVideoTrack;
    private VideoTrack cameraTrack;
    private EglBase egl;
    private PeerConnectionFactory factory;
    private PeerConnection peer;
    private AudioSource audioSource;
    private AudioTrack audioTrack;
    private RtpTransceiver audioTransceiver;
    private RtpTransceiver videoTransceiver;
    private VideoSource videoSource;
    private CameraVideoCapturer cameraCapturer;
    private SurfaceTextureHelper textureHelper;
    private volatile boolean cameraStarting, cameraStopPending;
    private boolean cameraFrontFacing;
    private AudioManager audioManager;
    private ScrollView chatScroll;
    private LinearLayout chatLines;
    private boolean uiReady = false;
    private boolean mediaStarted = false;
    private boolean offerReady, offerInProgress, offerPending;
    private final Runnable playbackPoll = new Runnable() {
        @Override public void run() {
            if (closed || playerSource == null) return;
            if (playerSource.kind == WatchPlayerSource.Kind.YOUTUBE && playerView != null) {
                playerView.evaluateJavascript(
                    "(function(){if(!window.vmestePlayer||!window.vmestePlayer.getPlayerState)return '';"
                        + "return window.vmestePlayer.getPlayerState()+'|'+window.vmestePlayer.getCurrentTime();})()",
                    WatchPartyActivity.this::onYouTubeSampleSafe);
            }
            if (host && isLocalPlayerPlaying()
                    && SystemClock.elapsedRealtime() - lastPlaybackSignalAt >= 15_000) {
                publishPlaybackSnapshotSafe();
            }
            if (!closed) main.postDelayed(this, 1_000);
        }
    };

    public static Intent intent(Context c, String roomId, boolean host, String title) {
        Intent i = new Intent(c, WatchPartyActivity.class);
        i.putExtra(EXTRA_ROOM_ID, roomId); i.putExtra(EXTRA_HOST, host); i.putExtra(EXTRA_TITLE, title);
        return i;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            roomId = getIntent().getStringExtra(EXTRA_ROOM_ID);
            host = getIntent().getBooleanExtra(EXTRA_HOST, false);
            title = getIntent().getStringExtra(EXTRA_TITLE);
            playerUrl = getIntent().getStringExtra(EXTRA_PLAYER_URL);
            if (roomId == null || roomId.isEmpty()) { finish(); return; }
            if (title == null) title = "Совместный просмотр";
            sync = new SupabaseSync(this);
            buildUiSafe();
            if (!sync.configured() || !sync.signedIn() || sync.coupleId().isEmpty()) {
                showErrorSafe("Сначала подключите Supabase, войдите и создайте общее пространство в разделе «Пригласить партнёра».");
                return;
            }
            startRtcSafe();
        } catch (Exception e) {
            showErrorSafe("Не удалось открыть комнату: " + e.getMessage());
        }
    }

    private void buildUiSafe() {
        try {
            root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(12), dp(8), dp(12), dp(8));
            root.setBackgroundColor(Color.rgb(250, 247, 252)); setContentView(root);
            topPanel = new LinearLayout(this); topPanel.setOrientation(LinearLayout.VERTICAL);
            root.addView(topPanel, new LinearLayout.LayoutParams(-1, -2));
            TextView heading = labelSafe("🎬  " + title, 20, Color.rgb(48, 43, 53), true);
            topPanel.addView(heading);
            roomCode = labelSafe("Код комнаты: " + roomId, 12, Color.GRAY, false);
            roomCode.setPadding(0, dp(3), 0, dp(2)); topPanel.addView(roomCode);
            status = labelSafe("Плеер и чат доступны · камера и микрофон выключены", 14,
                Color.rgb(102, 77, 128), true);
            status.setPadding(0, dp(5), 0, dp(5)); topPanel.addView(status);
            LinearLayout playerControls = new LinearLayout(this); playerControls.setGravity(Gravity.CENTER_VERTICAL);
            playerControls.setPadding(0, dp(2), 0, dp(6));
            playerAddress = new EditText(this); playerAddress.setSingleLine(true);
            playerAddress.setHint("YouTube или HTTPS-ссылка на MP4/HLS");
            if (playerUrl != null) playerAddress.setText(playerUrl);
            playerControls.addView(playerAddress, new LinearLayout.LayoutParams(0, dp(48), 1));
            Button play = buttonSafe("Открыть", Color.rgb(129, 88, 165), Color.WHITE);
            playerControls.addView(play); topPanel.addView(playerControls);
            playerView = new WebView(this); playerView.setBackgroundColor(Color.BLACK);
            WebSettings webSettings = playerView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webSettings.setDomStorageEnabled(true);
            webSettings.setMediaPlaybackRequiresUserGesture(false);
            webSettings.setAllowFileAccess(false);
            webSettings.setAllowContentAccess(false);
            webSettings.setLoadWithOverviewMode(true);
            webSettings.setUseWideViewPort(true);
            playerView.setWebChromeClient(new WebChromeClient());
            playerView.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return !isHttpsUrl(url);
                }

                @Override public boolean shouldOverrideUrlLoading(
                        WebView view, android.webkit.WebResourceRequest request) {
                    return !isHttpsUrl(request.getUrl().toString());
                }
            });
            directPlayerView = new PlayerView(this);
            directPlayerView.setUseController(true);
            directPlayerView.setBackgroundColor(Color.BLACK);
            playerContainer = new FrameLayout(this);
            playerContainer.setBackgroundColor(Color.BLACK);
            playerContainer.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
            playerContainer.addView(directPlayerView, new FrameLayout.LayoutParams(-1, -1));
            playerView.setVisibility(View.GONE);
            directPlayerView.setVisibility(View.GONE);
            fullscreenButton = buttonSafe("⛶", Color.rgb(48, 43, 53), Color.WHITE);
            fullscreenButton.setMinWidth(dp(48));
            fullscreenButton.setContentDescription("На весь экран");
            fullscreenButton.setEnabled(false);
            FrameLayout.LayoutParams fullscreenParams = new FrameLayout.LayoutParams(
                    dp(48), dp(44), Gravity.END | Gravity.TOP);
            fullscreenParams.setMargins(0, dp(8), dp(8), 0);
            playerContainer.addView(fullscreenButton, fullscreenParams);
            fullscreenButton.setOnClickListener(v -> toggleFullscreenSafe());
            LinearLayout.LayoutParams playerParams = new LinearLayout.LayoutParams(-1, 0, 3f);
            playerParams.topMargin = dp(4);
            playerParams.bottomMargin = dp(8);
            root.addView(playerContainer, playerParams);
            play.setOnClickListener(v -> {
                String link = playerAddress.getText().toString().trim();
                openPlayerSafe(link, true);
            });
            if (playerUrl != null && !playerUrl.trim().isEmpty()) openPlayerSafe(playerUrl, host);

            controlsScroll = new ScrollView(this);
            controlsScroll.setFillViewport(false);
            LinearLayout controlPanel = new LinearLayout(this);
            controlPanel.setOrientation(LinearLayout.VERTICAL);
            controlsScroll.addView(controlPanel, new ScrollView.LayoutParams(-1, -2));
            root.addView(controlsScroll, new LinearLayout.LayoutParams(-1, 0, 2f));

            TextView callHeading = labelSafe("Камера и голос", 15, Color.rgb(48, 43, 53), true);
            callHeading.setPadding(0, dp(2), 0, dp(5));
            controlPanel.addView(callHeading);
            videoRow = new LinearLayout(this);
            videoRow.setGravity(Gravity.CENTER);
            LinearLayout remoteTile = new LinearLayout(this);
            remoteTile.setOrientation(LinearLayout.VERTICAL);
            TextView remoteLabel = labelSafe("Партнёр", 12, Color.rgb(102, 77, 128), true);
            remoteTile.addView(remoteLabel);
            remoteView = new SurfaceViewRenderer(this);
            remoteView.setBackgroundColor(Color.BLACK);
            remoteTile.addView(remoteView, new LinearLayout.LayoutParams(-1, dp(96)));
            LinearLayout localTile = new LinearLayout(this);
            localTile.setOrientation(LinearLayout.VERTICAL);
            TextView localLabel = labelSafe("Вы", 12, Color.rgb(102, 77, 128), true);
            localTile.addView(localLabel);
            localCameraView = new SurfaceViewRenderer(this);
            localCameraView.setBackgroundColor(Color.BLACK);
            localTile.addView(localCameraView, new LinearLayout.LayoutParams(-1, dp(96)));
            videoRow.addView(remoteTile, new LinearLayout.LayoutParams(0, dp(118), 1));
            videoRow.addView(localTile, new LinearLayout.LayoutParams(0, dp(118), 1));
            videoRow.setVisibility(View.GONE);
            controlPanel.addView(videoRow);
            LinearLayout mediaControls = new LinearLayout(this);
            mediaControls.setGravity(Gravity.CENTER);
            cameraButton = buttonSafe("📷 Камера выключена", Color.rgb(239, 229, 246), Color.rgb(102, 68, 132));
            cameraButton.setEnabled(false);
            LinearLayout.LayoutParams mediaButtonParams = new LinearLayout.LayoutParams(0, dp(46), 1);
            mediaButtonParams.setMargins(0, 0, dp(6), 0);
            mediaControls.addView(cameraButton, mediaButtonParams);
            micButton = buttonSafe("🔇 Микрофон выключен", Color.rgb(239, 229, 246), Color.rgb(102, 68, 132));
            micButton.setEnabled(false);
            LinearLayout.LayoutParams micButtonParams = new LinearLayout.LayoutParams(0, dp(46), 1);
            micButtonParams.setMargins(dp(6), 0, 0, 0);
            mediaControls.addView(micButton, micButtonParams);
            controlPanel.addView(mediaControls);
            privacyStatus = labelSafe("Видео и звук никому не передаются", 12,
                Color.GRAY, false);
            privacyStatus.setPadding(0, dp(3), 0, dp(8));
            controlPanel.addView(privacyStatus);
            cameraButton.setOnClickListener(v -> toggleCameraSafe());
            micButton.setOnClickListener(v -> toggleMicrophoneSafe());

            LinearLayout chatHeading = new LinearLayout(this);
            chatHeading.setGravity(Gravity.CENTER_VERTICAL);
            TextView chatLabel = labelSafe("Текстовый чат", 15, Color.rgb(48, 43, 53), true);
            chatHeading.addView(chatLabel, new LinearLayout.LayoutParams(0, -2, 1));
            Button chatToggle = buttonSafe("Свернуть", Color.rgb(239, 229, 246), Color.rgb(102, 68, 132));
            chatHeading.addView(chatToggle);
            controlPanel.addView(chatHeading);
            chatScroll = new ScrollView(this); chatScroll.setFillViewport(false);
            chatLines = new LinearLayout(this); chatLines.setOrientation(LinearLayout.VERTICAL); chatScroll.addView(chatLines);
            LinearLayout.LayoutParams chatParams = new LinearLayout.LayoutParams(-1, dp(112));
            chatParams.topMargin = dp(4);
            controlPanel.addView(chatScroll, chatParams);
            LinearLayout composer = new LinearLayout(this); composer.setGravity(Gravity.CENTER_VERTICAL);
            EditText message = new EditText(this); message.setSingleLine(true);
            message.setHint("Сообщение партнёру…");
            composer.addView(message, new LinearLayout.LayoutParams(0, dp(48), 1));
            Button send = buttonSafe("Отправить", Color.rgb(129, 88, 165), Color.WHITE);
            composer.addView(send);
            controlPanel.addView(composer);
            send.setOnClickListener(v -> {
                String value = message.getText().toString().trim();
                if (!value.isEmpty()) { sendChatSafe(value); message.setText(""); }
            });
            chatToggle.setOnClickListener(v -> {
                boolean visible = chatScroll.getVisibility() != View.VISIBLE;
                chatScroll.setVisibility(visible ? View.VISIBLE : View.GONE);
                composer.setVisibility(visible ? View.VISIBLE : View.GONE);
                chatToggle.setText(visible ? "Свернуть" : "Показать");
            });

            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, -2);
            actionParams.topMargin = dp(8);
            controlPanel.addView(actions, actionParams);
            Button stop = buttonSafe("Завершить просмотр", Color.rgb(250, 236, 239), Color.rgb(154, 56, 72));
            actions.addView(stop, new LinearLayout.LayoutParams(0, dp(46), host ? 1 : 2));
            stop.setOnClickListener(v -> { finishRoomSafe(); });
            if (host) {
                Button share = buttonSafe("Поделиться кодом", Color.rgb(239, 229, 246), Color.rgb(102, 68, 132));
                LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(0, dp(46), 1);
                shareParams.setMargins(dp(6), 0, 0, 0);
                actions.addView(share, shareParams);
                share.setOnClickListener(v -> {
                    Intent i = new Intent(Intent.ACTION_SEND); i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_TEXT, "Подключайся к парному просмотру в «Вместе». Код комнаты: " + roomId);
                    startActivity(Intent.createChooser(i, "Пригласить партнёра"));
                });
            }
            uiReady = true;
        } catch (Exception e) {
            showErrorSafe("Ошибка интерфейса: " + e.getMessage());
        }
    }

    private Button buttonSafe(String text, int background, int foreground) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(foreground);
        button.setMinHeight(dp(44));
        button.setMinimumHeight(dp(44));
        button.setMinimumWidth(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(background));
        return button;
    }

    private boolean isHttpsUrl(String value) {
        Uri uri = Uri.parse(value);
        return "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null && uri.getUserInfo() == null;
    }

    private void openPlayerSafe(String rawUrl) {
        openPlayerSafe(rawUrl, true);
    }

    private void openPlayerSafe(String rawUrl, boolean notifyPeer) {
        final WatchPlayerSource source;
        try {
            source = WatchPlayerSource.parse(rawUrl);
        } catch (IllegalArgumentException e) {
            if (playerAddress != null) playerAddress.setError(e.getMessage());
            status.setText(e.getMessage());
            return;
        }

        if (playerSource != null && playerSource.kind == WatchPlayerSource.Kind.YOUTUBE
                && playerView != null) {
            playerView.evaluateJavascript(
                    "if(window.vmestePlayer)window.vmestePlayer.pauseVideo()", null);
        }
        playerSource = source;
        if (fullscreenButton != null) fullscreenButton.setEnabled(true);
        playerUrl = source.url;
        playbackRequestSent = false;
        playerReady = false;
        youtubeStateKnown = false;
        pendingPlaybackState = null;
        lastPlaybackSignalAt = 0;
        main.removeCallbacks(playbackPoll);
        releaseDirectPlayerSafe();
        if (playerView != null) playerView.onResume();

        if (source.kind == WatchPlayerSource.Kind.DIRECT_VIDEO) {
            playerView.onPause();
            playerView.stopLoading();
            playerView.setVisibility(View.GONE);
            directPlayerView.setVisibility(View.VISIBLE);
            directPlayer = new ExoPlayer.Builder(this).build();
            directPlayerView.setPlayer(directPlayer);
            directPlayer.addListener(new Player.Listener() {
                @Override public void onPlayerError(androidx.media3.common.PlaybackException error) {
                    if (!closed && status != null)
                        status.setText("Плеер не загрузил видео · проверьте ссылку и подключение к интернету");
                }

                @Override public void onEvents(Player player, Player.Events events) {
                    if (closed) return;
                    if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
                            && player.getPlaybackState() == Player.STATE_READY) {
                        playerReady = true;
                        applyPendingPlaybackSafe();
                        requestInitialPlaybackStateSafe();
                    }
                    if (!isApplyingRemotePlayback()
                            && player.getPlaybackState() == Player.STATE_READY
                            && (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)
                            || events.contains(Player.EVENT_POSITION_DISCONTINUITY))) {
                        publishPlaybackSnapshotSafe();
                    }
                }
            });
            directPlayer.setMediaItem(MediaItem.fromUri(source.url));
            directPlayer.prepare();
            status.setText("Синхронный плеер · прямое MP4/HLS/WebM-видео");
        } else {
            directPlayerView.setVisibility(View.GONE);
            playerView.setVisibility(View.VISIBLE);
            if (source.kind == WatchPlayerSource.Kind.YOUTUBE) {
                playerView.loadDataWithBaseURL("https://www.youtube-nocookie.com",
                        youtubePlayerPage(source.youtubeVideoId), "text/html", "UTF-8", null);
                status.setText("YouTube · воспроизведение синхронизируется между участниками");
            } else {
                playerView.loadUrl(source.url);
                status.setText("У этого сайта нет доступной синхронизации. DRM и закрытые плееры не обходятся.");
            }
        }
        if (notifyPeer && source.kind != WatchPlayerSource.Kind.WEBSITE) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("url", source.url);
                sendSignalSafe("player_source", payload);
            } catch (Exception e) {
                status.setText("Не удалось отправить ссылку партнёру: " + e.getMessage());
            }
        }
        if (source.kind != WatchPlayerSource.Kind.WEBSITE) main.post(playbackPoll);
    }

    private String youtubePlayerPage(String videoId) {
        return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>html,body,#player{margin:0;width:100%;height:100%;background:#000;overflow:hidden}"
                + "</style></head><body><div id=\"player\"></div><script>"
                + "var tag=document.createElement('script');tag.src='https://www.youtube.com/iframe_api';"
                + "document.head.appendChild(tag);"
                + "function onYouTubeIframeAPIReady(){window.vmestePlayer=new YT.Player('player',{"
                + "width:'100%',height:'100%',videoId:'" + videoId + "',"
                + "playerVars:{playsinline:1,controls:1,fs:0,enablejsapi:1,origin:'https://www.youtube-nocookie.com'}});}"
                + "</script></body></html>";
    }

    private void onYouTubeSampleSafe(String result) {
        if (closed || playerSource == null || playerSource.kind != WatchPlayerSource.Kind.YOUTUBE) return;
        try {
            Object decoded = new JSONTokener(result).nextValue();
            if (!(decoded instanceof String)) return;
            String[] values = ((String) decoded).split("\\|", 2);
            if (values.length != 2) return;
            int state = Integer.parseInt(values[0]);
            long positionMs = (long) (Double.parseDouble(values[1]) * 1_000);
            if (positionMs < 0) return;

            playerReady = true;
            if (!host) requestInitialPlaybackStateSafe();
            applyPendingPlaybackSafe();

            boolean playing = state == 1 || state == 3;
            long now = SystemClock.elapsedRealtime();
            if (youtubeStateKnown && !isApplyingRemotePlayback()) {
                long positionDelta = positionMs - lastYoutubePositionMs;
                long elapsed = Math.max(0, now - lastYoutubeSampleAt);
                boolean seeked = Math.abs(positionDelta - (lastYoutubePlaying ? elapsed : 0)) > 2_500
                        && Math.abs(positionDelta) > 2_500;
                if (playing != lastYoutubePlaying || seeked) publishPlaybackSnapshotSafe();
            }
            lastYoutubePlaying = playing;
            lastYoutubePositionMs = positionMs;
            lastYoutubeSampleAt = now;
            youtubeStateKnown = true;
        } catch (Exception ignored) {
            // The player API is not ready until its iframe has finished loading.
        }
    }

    private void requestInitialPlaybackStateSafe() {
        if (host || playbackRequestSent || playerSource == null
                || playerSource.kind == WatchPlayerSource.Kind.WEBSITE) return;
        playbackRequestSent = true;
        sendSignalSafe("playback_request", new JSONObject());
    }

    private boolean isLocalPlayerPlaying() {
        if (playerSource == null) return false;
        if (playerSource.kind == WatchPlayerSource.Kind.DIRECT_VIDEO)
            return directPlayer != null && directPlayer.getPlayWhenReady();
        return playerSource.kind == WatchPlayerSource.Kind.YOUTUBE && lastYoutubePlaying;
    }

    private long localPlayerPositionMs() {
        if (playerSource == null) return 0;
        if (playerSource.kind == WatchPlayerSource.Kind.DIRECT_VIDEO)
            return directPlayer == null ? 0 : Math.max(0, directPlayer.getCurrentPosition());
        return playerSource.kind == WatchPlayerSource.Kind.YOUTUBE
                ? Math.max(0, lastYoutubePositionMs) : 0;
    }

    private void publishPlaybackSnapshotSafe() {
        if (closed || playerSource == null || playerSource.kind == WatchPlayerSource.Kind.WEBSITE) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("source", playerSource.kind.name());
            payload.put("position_ms", localPlayerPositionMs());
            payload.put("playing", isLocalPlayerPlaying());
            payload.put("sent_at", System.currentTimeMillis());
            lastPlaybackSignalAt = SystemClock.elapsedRealtime();
            sendSignalSafe("playback", payload);
        } catch (Exception e) {
            status.setText("Не удалось синхронизировать плеер: " + e.getMessage());
        }
    }

    private boolean isApplyingRemotePlayback() {
        return applyingRemotePlayback || SystemClock.elapsedRealtime() < suppressPlaybackSyncUntil;
    }

    private void applyPendingPlaybackSafe() {
        if (pendingPlaybackState == null) return;
        JSONObject pending = pendingPlaybackState;
        pendingPlaybackState = null;
        applyRemotePlaybackSafe(pending);
    }

    private void applyRemotePlaybackSafe(JSONObject payload) {
        if (closed || playerSource == null || playerSource.kind == WatchPlayerSource.Kind.WEBSITE) return;
        if (!playerSource.kind.name().equals(payload.optString("source"))) return;
        long targetPositionMs = payload.optLong("position_ms", -1);
        if (targetPositionMs < 0 || targetPositionMs > 7L * 24 * 60 * 60 * 1_000) return;
        boolean playing = payload.optBoolean("playing", false);
        long sentAt = payload.optLong("sent_at", 0);
        if (playing && sentAt > 0) {
            long transitMs = Math.min(1_500, Math.max(0, System.currentTimeMillis() - sentAt));
            targetPositionMs += transitMs;
        }

        if (playerSource.kind == WatchPlayerSource.Kind.DIRECT_VIDEO) {
            if (directPlayer == null || directPlayer.getPlaybackState() != Player.STATE_READY) {
                pendingPlaybackState = payload;
                return;
            }
            applyingRemotePlayback = true;
            suppressPlaybackSyncUntil = SystemClock.elapsedRealtime() + 2_500;
            if (Math.abs(directPlayer.getCurrentPosition() - targetPositionMs) > 1_500)
                directPlayer.seekTo(targetPositionMs);
            directPlayer.setPlayWhenReady(playing);
            applyingRemotePlayback = false;
            return;
        }

        if (!playerReady || playerView == null) {
            pendingPlaybackState = payload;
            return;
        }
        applyingRemotePlayback = true;
        suppressPlaybackSyncUntil = SystemClock.elapsedRealtime() + 2_500;
        double targetSeconds = targetPositionMs / 1_000.0;
        String action = playing ? "playVideo()" : "pauseVideo()";
        playerView.evaluateJavascript("(function(){var p=window.vmestePlayer;if(!p)return;"
                + "if(Math.abs(p.getCurrentTime()-" + targetSeconds + ")>1.5)p.seekTo("
                + targetSeconds + ",true);p." + action + "})()", ignored -> {
                    applyingRemotePlayback = false;
                });
        main.postDelayed(() -> applyingRemotePlayback = false, 2_500);
    }

    private void releaseDirectPlayerSafe() {
        if (directPlayerView != null) directPlayerView.setPlayer(null);
        if (directPlayer != null) {
            directPlayer.release();
            directPlayer = null;
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_MIC) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                startMicrophoneSafe();
            else {
                main.post(() -> {
                    if (status != null) status.setText("Микрофон не разрешён · плеер и чат продолжают работать");
                    updatePrivacyStatusSafe();
                });
            }
        } else if (requestCode == REQUEST_CAMERA) {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                startCameraSafe();
            else {
                main.post(() -> {
                    if (status != null) status.setText("Камера не разрешена · плеер и чат продолжают работать");
                    updatePrivacyStatusSafe();
                });
            }
        }
    }

    private void startRtcSafe() {
        if (mediaStarted) return;
        mediaStarted = true;
        io.execute(() -> {
            try {
                egl = EglBase.create();
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(getApplicationContext()).createInitializationOptions());
                factory = PeerConnectionFactory.builder()
                        .setVideoEncoderFactory(new DefaultVideoEncoderFactory(egl.getEglBaseContext(), true, true))
                        .setVideoDecoderFactory(new DefaultVideoDecoderFactory(egl.getEglBaseContext()))
                        .createPeerConnectionFactory();
                audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
                if (audioManager != null) {
                    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                    audioManager.setSpeakerphoneOn(true);
                }
                createPeerSafe();
                if (peer == null) throw new IllegalStateException("WebRTC peer is unavailable");
                RtpTransceiver.RtpTransceiverInit sendReceive = new RtpTransceiver.RtpTransceiverInit(
                    RtpTransceiver.RtpTransceiverDirection.SEND_RECV);
                audioTransceiver = peer.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, sendReceive);
                videoTransceiver = peer.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                    new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV));
                if (audioTransceiver == null || videoTransceiver == null)
                    throw new IllegalStateException("WebRTC media transceivers are unavailable");
                main.post(() -> {
                    if (closed) return;
                    try {
                        remoteView.init(egl.getEglBaseContext(), null);
                        localCameraView.init(egl.getEglBaseContext(), null);
                        rtcReady = true;
                        offerReady = true;
                        status.setText("Комната готова · плеер и текстовый чат доступны");
                        updateMediaControlsSafe();
                    } catch (Exception e) {
                        showErrorSafe("Не удалось подготовить видео: " + e.getMessage());
                    }
                });
                startPollingSafe();
                if (host) {
                    offerReady = true;
                    createOfferSafe();
                }
            } catch (Exception e) {
                main.post(() -> showErrorSafe("Не удалось запустить комнату: " + e.getMessage()));
            }
        });
    }

    private void createPeerSafe() {
        try {
            List<PeerConnection.IceServer> ice = Collections.singletonList(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
            PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(ice);
            config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
            peer = factory.createPeerConnection(config, new PeerConnection.Observer() {
                @Override public void onSignalingChange(PeerConnection.SignalingState state) { }
                @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
                    main.post(() -> {
                        if (closed) return;
                        if (state == PeerConnection.IceConnectionState.CONNECTED
                                || state == PeerConnection.IceConnectionState.COMPLETED) {
                            status.setText("Связь с партнёром установлена · камера и звук доступны при включении");
                        } else if (state == PeerConnection.IceConnectionState.FAILED) {
                            status.setText("Не удалось установить прямую связь · проверьте сеть и TURN-настройки");
                        } else if (state == PeerConnection.IceConnectionState.DISCONNECTED) {
                            status.setText("Связь с партнёром потеряна · пытаемся восстановить");
                        } else {
                            status.setText("Связь: " + state.name());
                        }
                    });
                }
                @Override public void onIceConnectionReceivingChange(boolean receiving) { }
                @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) { }
                @Override public void onIceCandidate(IceCandidate candidate) {
                    try {
                        JSONObject p = new JSONObject();
                        p.put("sdp", candidate.sdp); p.put("mid", candidate.sdpMid);
                        p.put("line", candidate.sdpMLineIndex); sendSignalSafe("ice", p);
                    } catch (Exception ignored) { }
                }
                @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) { }
                @Override public void onAddStream(MediaStream stream) {
                    if (!stream.videoTracks.isEmpty()) showRemoteVideoSafe(stream.videoTracks.get(0));
                }
                @Override public void onRemoveStream(MediaStream stream) {
                    if (!stream.videoTracks.isEmpty()) main.post(WatchPartyActivity.this::clearRemoteVideoSafe);
                }
                @Override public void onDataChannel(org.webrtc.DataChannel channel) { }
                @Override public void onRenegotiationNeeded() {
                    main.post(() -> {
                        if (closed || !rtcReady) return;
                        if (host) {
                            if (offerReady) createOfferSafe();
                        } else {
                            try {
                                JSONObject request = new JSONObject();
                                request.put("renegotiation_request", true);
                                sendSignalSafe("offer", request);
                            } catch (Exception e) {
                                showErrorSafe("Не удалось запросить подключение камеры или микрофона: " + e.getMessage());
                            }
                        }
                    });
                }
                @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {
                    MediaStreamTrack track = receiver.track();
                    if (track instanceof VideoTrack) showRemoteVideoSafe((VideoTrack) track);
                }
                @Override public void onTrack(RtpTransceiver transceiver) {
                    MediaStreamTrack track = transceiver.getReceiver().track();
                    if (track instanceof VideoTrack) showRemoteVideoSafe((VideoTrack) track);
                }
            });
            if (peer == null) throw new IllegalStateException("WebRTC peer is unavailable");
        } catch (Exception e) {
            main.post(() -> showErrorSafe("Ошибка WebRTC: " + e.getMessage()));
        }
    }

    private void showRemoteVideoSafe(VideoTrack track) {
        main.post(() -> {
            if (closed || remoteView == null || track == null) return;
            if (remoteVideoTrack == track) return;
            if (remoteVideoTrack != null) remoteVideoTrack.removeSink(remoteView);
            remoteVideoTrack = track;
            remoteVideoAvailable = true;
            track.addSink(remoteView);
            updateVideoRowSafe();
        });
    }

    private void clearRemoteVideoSafe() {
        if (remoteVideoTrack != null && remoteView != null) remoteVideoTrack.removeSink(remoteView);
        remoteVideoTrack = null;
        remoteVideoAvailable = false;
        updateVideoRowSafe();
    }

    private void updateVideoRowSafe() {
        if (remoteView != null) remoteView.setVisibility(remoteVideoAvailable ? View.VISIBLE : View.GONE);
        if (localCameraView != null) localCameraView.setVisibility(cameraTrack != null ? View.VISIBLE : View.GONE);
        if (videoRow != null)
            videoRow.setVisibility(remoteVideoAvailable || cameraTrack != null ? View.VISIBLE : View.GONE);
    }

    private void createOfferSafe() {
        if (!host || peer == null || closed) return;
        synchronized (offerLock) {
            if (offerInProgress) {
                offerPending = true;
                return;
            }
            if (peer.signalingState() != PeerConnection.SignalingState.STABLE) {
                offerPending = true;
                return;
            }
            offerInProgress = true;
            offerPending = false;
        }
        peer.createOffer(new SdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) {
                peer.setLocalDescription(new SdpObserver() {
                    @Override public void onSetSuccess() {
                        try { JSONObject p = new JSONObject(); p.put("sdp", sdp.description); sendSignalSafe("offer", p); }
                        catch (Exception e) { showErrorSafe(e.getMessage()); }
                        finally { completeOfferSafe(); }
                    }
                    @Override public void onSetFailure(String error) {
                        showErrorSafe(error);
                        completeOfferSafe();
                    }
                    @Override public void onCreateSuccess(SessionDescription ignored) { }
                    @Override public void onCreateFailure(String error) { }
                }, sdp);
            }
            @Override public void onSetSuccess() { }
            @Override public void onCreateFailure(String error) {
                showErrorSafe("Не удалось создать трансляцию: " + error);
                completeOfferSafe();
            }
            @Override public void onSetFailure(String error) {
                showErrorSafe(error);
                completeOfferSafe();
            }
        }, new MediaConstraints());
    }

    private void completeOfferSafe() {
        synchronized (offerLock) {
            offerInProgress = false;
        }
        maybeCreatePendingOfferSafe();
    }

    private void maybeCreatePendingOfferSafe() {
        if (peer == null || closed || peer.signalingState() != PeerConnection.SignalingState.STABLE) return;
        synchronized (offerLock) {
            if (!offerPending || offerInProgress) return;
            offerPending = false;
        }
        createOfferSafe();
    }

    private void startPollingSafe() {
        io.execute(new Runnable() {
            @Override public void run() {
                if (closed) return;
                try {
                    JSONArray batch = sync.fetchWatchSignals(roomId, lastSignalId);
                    List<JSONObject> incoming = new ArrayList<>();
                    for (int i = 0; i < batch.length(); i++) {
                        JSONObject item = batch.getJSONObject(i);
                        lastSignalId = Math.max(lastSignalId, item.optLong("id"));
                        if (!sync.userId().equals(item.optString("sender_id"))) incoming.add(item);
                    }
                    for (int phase = 0; phase < 3 && !closed; phase++) {
                        for (JSONObject item : incoming) {
                            String kind = item.optString("kind");
                            boolean negotiation = "offer".equals(kind) || "answer".equals(kind);
                            boolean ice = "ice".equals(kind);
                            if ((phase == 0 && negotiation)
                                    || (phase == 1 && ice)
                                    || (phase == 2 && !negotiation && !ice)) {
                                processSignalSafe(item);
                            }
                        }
                    }
                } catch (Exception e) {
                    String reason = signalErrorLabel(e);
                    main.post(() -> {
                        if (!closed && status != null)
                            status.setText("Сигналы комнаты недоступны (" + reason + ") · проверьте доступ Supabase и миграции");
                    });
                }
                if (!closed && !io.isShutdown()) {
                    try { main.postDelayed(() -> io.execute(this), 1300); }
                    catch (Exception ignored) { }
                }
            }
        });
    }

    private void processSignalSafe(JSONObject item) {
        String kind = item.optString("kind");
        JSONObject payload = item.optJSONObject("payload");
        if (payload == null) payload = new JSONObject();
        final JSONObject data = payload;
        if ("chat".equals(kind)) {
            main.post(() -> addChatSafe("Партнёр", data.optString("text"))); return;
        }
        if ("playback_request".equals(kind)) {
            if (host) main.post(this::publishPlaybackSnapshotSafe);
            return;
        }
        if ("renegotiate".equals(kind)) {
            if (host) main.post(() -> {
                if (offerReady && !closed) createOfferSafe();
            });
            return;
        }
        if ("offer".equals(kind) && host && data.optBoolean("renegotiation_request")) {
            main.post(() -> {
                if (offerReady && !closed) createOfferSafe();
            });
            return;
        }
        if ("player_source".equals(kind)) {
            String remoteUrl = data.optString("url", "");
            try {
                if (WatchPlayerSource.parse(remoteUrl).kind != WatchPlayerSource.Kind.WEBSITE)
                    main.post(() -> openPlayerSafe(remoteUrl, false));
            } catch (IllegalArgumentException ignored) { }
            return;
        }
        if ("playback".equals(kind)) {
            main.post(() -> applyRemotePlaybackSafe(data));
            return;
        }
        if ("close".equals(kind)) {
            main.post(() -> { status.setText("Партнёр завершил просмотр"); stopRtcSafe(); }); return;
        }
        if ("ice".equals(kind)) {
            try {
                IceCandidate candidate = new IceCandidate(data.optString("mid"), data.optInt("line"), data.getString("sdp"));
                if (remoteReady) peer.addIceCandidate(candidate);
                else pendingCandidates.add(candidate);
            } catch (Exception ignored) { }
            return;
        }
        if (host && "answer".equals(kind)) {
            remoteReady = false;
            peer.setRemoteDescription(sdpObserverSafe(() -> {
                remoteReady = true; flushCandidatesSafe();
                status.setText("Партнёр подключён · общий плеер готов");
                maybeCreatePendingOfferSafe();
            }), new SessionDescription(SessionDescription.Type.ANSWER, data.optString("sdp")));
        } else if (!host && "offer".equals(kind)) {
            remoteReady = false;
            peer.setRemoteDescription(sdpObserverSafe(() -> {
                remoteReady = true; flushCandidatesSafe(); createAnswerSafe();
            }), new SessionDescription(SessionDescription.Type.OFFER, data.optString("sdp")));
        }
    }

    private SdpObserver sdpObserverSafe(Runnable success) {
        return new SdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) { }
            @Override public void onSetSuccess() { success.run(); }
            @Override public void onCreateFailure(String error) { showErrorSafe(error); }
            @Override public void onSetFailure(String error) { showErrorSafe(error); }
        };
    }

    private void createAnswerSafe() {
        if (peer == null) return;
        peer.createAnswer(new SdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) {
                peer.setLocalDescription(sdpObserverSafe(() -> {
                    try { JSONObject p = new JSONObject(); p.put("sdp", sdp.description); sendSignalSafe("answer", p); }
                    catch (Exception e) { showErrorSafe(e.getMessage()); }
                }), sdp);
            }
            @Override public void onSetSuccess() { }
            @Override public void onCreateFailure(String error) { showErrorSafe(error); }
            @Override public void onSetFailure(String error) { showErrorSafe(error); }
        }, new MediaConstraints());
    }

    private void flushCandidatesSafe() {
        for (IceCandidate candidate : pendingCandidates) {
            if (peer != null) peer.addIceCandidate(candidate);
        }
        pendingCandidates.clear();
    }

    private void sendSignalSafe(String kind, JSONObject payload) {
        if (closed && !"close".equals(kind)) return;
        if (io.isShutdown()) return;
        try { io.execute(() -> {
            try { sync.sendWatchSignal(roomId, kind, payload); }
            catch (Exception e) {
                String reason = signalErrorLabel(e);
                main.post(() -> {
                    if (!closed && status != null)
                        status.setText("Не удалось передать сигнал " + kind + " (" + reason + ") · проверьте миграции и доступ Supabase");
                });
            }
        }); } catch (Exception ignored) { }
    }

    private static String signalErrorLabel(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isEmpty()) return error.getClass().getSimpleName();
        int separator = message.indexOf(':');
        return message.startsWith("HTTP ") && separator > 0
                ? message.substring(0, separator)
                : error.getClass().getSimpleName();
    }

    private void sendChatSafe(String value) {
        try { JSONObject data = new JSONObject(); data.put("text", value);
            addChatSafe("Вы", value); sendSignalSafe("chat", data); }
        catch (Exception ignored) { }
    }

    private void addChatSafe(String author, String message) {
        if (chatLines == null || closed) return;
        TextView row = labelSafe(author + ": " + message, 13, Color.rgb(54, 48, 62), false);
        chatLines.addView(row);
        if (chatLines.getChildCount() > 80) chatLines.removeViewAt(0);
        if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void toggleMicrophoneSafe() {
        if (!rtcReady || closed) return;
        if (audioTrack != null) {
            stopMicrophoneSafe();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
            return;
        }
        startMicrophoneSafe();
    }

    private void startMicrophoneSafe() {
        if (closed || !rtcReady || audioTrack != null) return;
        try {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                throw new SecurityException("Разрешение на микрофон не выдано");
            audioSource = factory.createAudioSource(new MediaConstraints());
            audioTrack = factory.createAudioTrack("vmeste_voice", audioSource);
            if (audioTransceiver == null || !audioTransceiver.getSender().setTrack(audioTrack, false))
                throw new IllegalStateException("Не удалось включить микрофон в WebRTC");
            audioTrack.setEnabled(true);
            updatePrivacyStatusSafe();
            updateMediaControlsSafe();
        } catch (Exception e) {
            stopMicrophoneSafe();
            if (status != null) status.setText("Не удалось включить микрофон: " + e.getMessage());
        }
    }

    private void stopMicrophoneSafe() {
        AudioTrack track = audioTrack;
        AudioSource source = audioSource;
        audioTrack = null;
        audioSource = null;
        if (track != null) {
            if (audioTransceiver != null) audioTransceiver.getSender().setTrack(null, false);
            track.dispose();
        }
        if (source != null) source.dispose();
        updatePrivacyStatusSafe();
        updateMediaControlsSafe();
    }

    private void toggleCameraSafe() {
        if (!rtcReady || closed) return;
        if (cameraTrack != null) {
            stopCameraSafe();
            return;
        }
        if (cameraStarting) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
            return;
        }
        startCameraSafe();
    }

    private void startCameraSafe() {
        if (closed || !rtcReady || cameraTrack != null || cameraStarting) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
            return;
        }
        cameraStarting = true;
        cameraStopPending = false;
        updateMediaControlsSafe();
        media.execute(() -> {
            CameraVideoCapturer capturer = null;
            SurfaceTextureHelper helper = null;
            VideoSource source = null;
            VideoTrack track = null;
            try {
                if (closed) return;
                capturer = createCameraCapturerSafe();
                source = factory.createVideoSource(false);
                helper = SurfaceTextureHelper.create("vmeste-camera-capture", egl.getEglBaseContext());
                capturer.initialize(helper, getApplicationContext(), source.getCapturerObserver());
                capturer.startCapture(640, 480, 24);
                track = factory.createVideoTrack("vmeste_camera", source);
                if (videoTransceiver == null || !videoTransceiver.getSender().setTrack(track, false))
                    throw new IllegalStateException("Не удалось включить камеру в WebRTC");
                if (closed || cameraStopPending) {
                    videoTransceiver.getSender().setTrack(null, false);
                    releaseCameraResourcesSafe(capturer, helper, source, track);
                    return;
                }
                cameraCapturer = capturer;
                textureHelper = helper;
                videoSource = source;
                cameraTrack = track;
                main.post(() -> {
                    cameraStarting = false;
                    if (closed) {
                        stopCameraSafe();
                        return;
                    }
                    localCameraView.setMirror(cameraFrontFacing);
                    cameraTrack.addSink(localCameraView);
                    updateVideoRowSafe();
                    updatePrivacyStatusSafe();
                    updateMediaControlsSafe();
                });
            } catch (Exception e) {
                if (capturer != null || helper != null || source != null || track != null)
                    releaseCameraResourcesSafe(capturer, helper, source, track);
                main.post(() -> {
                    cameraStarting = false;
                    cameraStopPending = false;
                    if (!closed && status != null)
                        status.setText("Не удалось включить камеру: " + e.getMessage());
                    updatePrivacyStatusSafe();
                    updateMediaControlsSafe();
                });
            }
        });
    }

    private CameraVideoCapturer createCameraCapturerSafe() {
        CameraEnumerator enumerator = Camera2Enumerator.isSupported(this)
            ? new Camera2Enumerator(this) : new Camera1Enumerator(true);
        String[] deviceNames = enumerator.getDeviceNames();
        for (String name : deviceNames) {
            if (enumerator.isFrontFacing(name)) {
                CameraVideoCapturer capturer = enumerator.createCapturer(name, null);
                if (capturer != null) {
                    cameraFrontFacing = true;
                    return capturer;
                }
            }
        }
        for (String name : deviceNames) {
            CameraVideoCapturer capturer = enumerator.createCapturer(name, null);
            if (capturer != null) {
                cameraFrontFacing = false;
                return capturer;
            }
        }
        throw new IllegalStateException("На устройстве не найдена доступная камера");
    }

    private void stopCameraSafe() {
        if (cameraStarting && cameraTrack == null) {
            cameraStopPending = true;
            return;
        }
        cameraStarting = false;
        CameraVideoCapturer capturer = cameraCapturer;
        SurfaceTextureHelper helper = textureHelper;
        VideoSource source = videoSource;
        VideoTrack track = cameraTrack;
        cameraCapturer = null;
        textureHelper = null;
        videoSource = null;
        cameraTrack = null;
        if (track != null) {
            if (videoTransceiver != null) videoTransceiver.getSender().setTrack(null, false);
            if (localCameraView != null) track.removeSink(localCameraView);
        }
        releaseCameraResourcesSafe(capturer, helper, source, track);
        updateVideoRowSafe();
        updatePrivacyStatusSafe();
        updateMediaControlsSafe();
    }

    private void releaseCameraResourcesSafe(CameraVideoCapturer capturer, SurfaceTextureHelper helper,
            VideoSource source, VideoTrack track) {
        if (capturer != null) {
            try { capturer.stopCapture(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            catch (Exception ignored) { }
            capturer.dispose();
        }
        if (track != null) track.dispose();
        if (source != null) source.dispose();
        if (helper != null) helper.dispose();
    }

    private void updateMediaControlsSafe() {
        if (cameraButton != null) {
            cameraButton.setText(cameraStarting ? "📷 Камера запускается…"
                : cameraTrack != null ? "📷 Камера включена" : "📷 Камера выключена");
            cameraButton.setEnabled(rtcReady && !closed && !cameraStarting);
            cameraButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    cameraTrack != null ? Color.rgb(225, 241, 229) : Color.rgb(239, 229, 246)));
        }
        if (micButton != null) {
            micButton.setText(audioTrack != null ? "🎙 Микрофон включён" : "🔇 Микрофон выключен");
            micButton.setEnabled(rtcReady && !closed);
            micButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    audioTrack != null ? Color.rgb(225, 241, 229) : Color.rgb(239, 229, 246)));
        }
    }

    private void updatePrivacyStatusSafe() {
        if (privacyStatus == null) return;
        boolean cameraOn = cameraTrack != null;
        boolean microphoneOn = audioTrack != null;
        String text;
        if (!cameraOn && !microphoneOn) text = "Видео и звук никому не передаются";
        else if (cameraOn && microphoneOn) text = "Камера и микрофон включены · передаются партнёру";
        else if (cameraOn) text = "Камера включена · видео передаётся партнёру";
        else text = "Микрофон включён · звук передаётся партнёру";
        privacyStatus.setText(text);
        privacyStatus.setTextColor(cameraOn || microphoneOn
            ? Color.rgb(73, 115, 83) : Color.GRAY);
    }

    private void toggleFullscreenSafe() {
        fullscreen = !fullscreen;
        if (topPanel != null) topPanel.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        if (controlsScroll != null) controlsScroll.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        if (root != null) root.setPadding(fullscreen ? 0 : dp(12), fullscreen ? 0 : dp(8),
                fullscreen ? 0 : dp(12), fullscreen ? 0 : dp(8));
        if (playerContainer != null) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) playerContainer.getLayoutParams();
            params.height = 0;
            params.weight = fullscreen ? 1f : 3f;
            playerContainer.setLayoutParams(params);
        }
        if (fullscreenButton != null) {
            fullscreenButton.setText(fullscreen ? "×" : "⛶");
            fullscreenButton.setContentDescription(fullscreen ? "Выйти из полноэкранного режима" : "На весь экран");
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    if (fullscreen) controller.hide(WindowInsets.Type.systemBars());
                    else controller.show(WindowInsets.Type.systemBars());
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                int flags = getWindow().getDecorView().getSystemUiVisibility();
                if (fullscreen)
                    flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                else
                    flags &= ~(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
                getWindow().getDecorView().setSystemUiVisibility(flags);
            }
            if (root != null) root.requestLayout();
        } catch (Exception ignored) { }
    }

    private void finishRoomSafe() {
        if (closed) return;
        new Thread(() -> {
            try { sync.sendWatchSignal(roomId, "close", new JSONObject()); }
            catch (Exception ignored) {}
            if (host) {
                try { sync.closeWatchRoom(roomId); } catch (Exception ignored) { }
            }
        }).start();
        finish();
    }

    private void showErrorSafe(String message) {
        main.post(() -> {
            if (closed || isFinishing()) return;
            new AlertDialog.Builder(this)
                .setTitle("Кино-комната")
                .setMessage(message)
                .setPositiveButton("Закрыть", (d, w) -> finish())
                .show();
        });
    }

    private void stopRtcSafe() {
        if (closed) return;
        closed = true;
        main.removeCallbacksAndMessages(null);
        io.shutdownNow();
        stopMicrophoneSafe();
        stopCameraSafe();
        media.shutdown();
        try { media.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        releaseDirectPlayerSafe();
        if (playerView != null) {
            playerView.stopLoading();
            playerView.loadUrl("about:blank");
        }
        clearRemoteVideoSafe();
        if (peer != null) { peer.close(); peer.dispose(); }
        if (factory != null) factory.dispose();
        if (remoteView != null) { remoteView.release(); remoteView = null; }
        if (localCameraView != null) { localCameraView.release(); localCameraView = null; }
        if (egl != null) egl.release();
        if (audioManager != null) {
            audioManager.setSpeakerphoneOn(false);
            audioManager.setMode(AudioManager.MODE_NORMAL);
        }
    }

    private int dp(float n) {
        return (int) (n * getResources().getDisplayMetrics().density + .5f);
    }

    private TextView labelSafe(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    @Override public void onBackPressed() {
        if (fullscreen) {
            toggleFullscreenSafe();
            return;
        }
        new AlertDialog.Builder(this)
            .setMessage("Завершить комнату просмотра?")
            .setNegativeButton("Остаться", null)
            .setPositiveButton("Завершить", (d, w) -> finishRoomSafe())
            .show();
    }

    @Override public void onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && uiReady) {
            try {
                PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(new android.util.Rational(16, 9))
                    .build();
                enterPictureInPictureMode(params);
            } catch (Exception ignored) { }
        }
    }

    @Override public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        if (!uiReady) return;
        try {
            if (isInPictureInPictureMode) {
                if (playerContainer != null) playerContainer.setVisibility(View.VISIBLE);
                if (topPanel != null) topPanel.setVisibility(View.GONE);
                if (controlsScroll != null) controlsScroll.setVisibility(View.GONE);
                if (fullscreenButton != null) fullscreenButton.setVisibility(View.GONE);
                if (root != null) root.setPadding(dp(4), dp(4), dp(4), dp(4));
            } else {
                boolean onlyPlayer = fullscreen;
                if (topPanel != null) topPanel.setVisibility(onlyPlayer ? View.GONE : View.VISIBLE);
                if (controlsScroll != null) controlsScroll.setVisibility(onlyPlayer ? View.GONE : View.VISIBLE);
                if (fullscreenButton != null) fullscreenButton.setVisibility(View.VISIBLE);
                if (root != null) root.setPadding(onlyPlayer ? 0 : dp(12), onlyPlayer ? 0 : dp(8),
                        onlyPlayer ? 0 : dp(12), onlyPlayer ? 0 : dp(8));
                updateVideoRowSafe();
            }
        } catch (Exception ignored) { }
    }

    @Override protected void onDestroy() {
        stopRtcSafe();
        try {
            if (playerView != null) {
                playerView.stopLoading();
                playerView.loadUrl("about:blank");
                playerView.destroy();
                playerView = null;
            }
        } catch (Exception ignored) { }
        super.onDestroy();
    }
}
