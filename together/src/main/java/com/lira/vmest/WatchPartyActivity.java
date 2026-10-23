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
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
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
import org.webrtc.ScreenCapturerAndroid;
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

/** One-to-one screen broadcast, voice and text chat. Media is peer-to-peer; Supabase is signaling only. */
public final class WatchPartyActivity extends Activity {
    public static final String EXTRA_ROOM_ID = "room_id";
    public static final String EXTRA_HOST = "host";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_PLAYER_URL = "player_url";
    private static final int REQUEST_CAPTURE = 7101;
    private static final int REQUEST_MIC = 7102;
    private static final int REQUEST_NOTIFY = 7103;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<IceCandidate> pendingCandidates = new ArrayList<>();
    private final Object offerLock = new Object();
    private SupabaseSync sync;
    private String roomId, title;
    private String playerUrl;
    private boolean host, remoteReady, closed;
    private boolean videoVisible = true, fullscreen = false;
    private long lastSignalId;
    private LinearLayout root;
    private TextView status, roomCode;
    private WebView playerView;
    private SurfaceViewRenderer remoteView;
    private EglBase egl;
    private PeerConnectionFactory factory;
    private PeerConnection peer;
    private AudioSource audioSource;
    private AudioTrack audioTrack;
    private VideoSource videoSource;
    private VideoTrack screenTrack;
    private ScreenCapturerAndroid screenCapturer;
    private SurfaceTextureHelper textureHelper;
    private Intent projectionData;
    private AudioManager audioManager;
    private ScrollView chatScroll;
    private LinearLayout chatLines;
    private boolean uiReady = false;
    private boolean mediaStarted = false;
    private boolean offerReady, offerInProgress, offerPending;

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFY);
            }
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
            } else if (!mediaStarted) {
                mediaStarted = true;
                // Always enter room UI first; host starts screen capture manually via button
                startRtcSafe(null);
            }
        } catch (Exception e) {
            showErrorSafe("Не удалось открыть комнату: " + e.getMessage());
        }
    }

    private void buildUiSafe() {
        try {
            root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(16), dp(12), dp(16), dp(10));
            root.setBackgroundColor(Color.rgb(250, 247, 252)); setContentView(root);
            TextView heading = labelSafe("🎬  " + title, 21, Color.rgb(48, 43, 53), true); root.addView(heading);
            roomCode = labelSafe("Код комнаты: " + roomId, 12, Color.GRAY, false); roomCode.setPadding(0, dp(4), 0, dp(4)); root.addView(roomCode);
            status = labelSafe(host ? "Подготовка трансляции…" : "Ожидаем трансляцию партнёра…", 14, Color.rgb(102, 77, 128), true);
            status.setPadding(0, dp(8), 0, dp(8)); root.addView(status);
            LinearLayout playerControls = new LinearLayout(this); playerControls.setGravity(Gravity.CENTER_VERTICAL);
            playerControls.setPadding(0, dp(4), 0, dp(4));
            EditText playerAddress = new EditText(this); playerAddress.setSingleLine(true);
            playerAddress.setHint("Ссылка на видео для просмотра внутри приложения");
            if (playerUrl != null) playerAddress.setText(playerUrl);
            playerControls.addView(playerAddress, new LinearLayout.LayoutParams(0, dp(48), 1));
            Button play = new Button(this); play.setText("Открыть"); playerControls.addView(play); root.addView(playerControls);
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
            playerView.setWebViewClient(new WebViewClient());
            root.addView(playerView, new LinearLayout.LayoutParams(-1, dp(220)));
            play.setOnClickListener(v -> {
                String link = playerAddress.getText().toString().trim();
                if (!link.startsWith("https://") && !link.startsWith("http://")) {
                    playerAddress.setError("Укажите ссылку http или https"); return;
                }
                playerUrl = link;
                if (playerView != null) playerView.loadUrl(link);
            });
            if (playerUrl != null && !playerUrl.trim().isEmpty() && playerView != null)
                playerView.loadUrl(playerUrl);
            remoteView = new SurfaceViewRenderer(this);
            remoteView.setBackgroundColor(Color.BLACK);
            root.addView(remoteView, new LinearLayout.LayoutParams(-1, 0, 1));
            if (host) remoteView.setVisibility(View.GONE);
            chatScroll = new ScrollView(this); chatScroll.setFillViewport(false);
            chatLines = new LinearLayout(this); chatLines.setOrientation(LinearLayout.VERTICAL); chatScroll.addView(chatLines);
            LinearLayout.LayoutParams chatParams = new LinearLayout.LayoutParams(-1, dp(126)); chatParams.topMargin = dp(8);
            root.addView(chatScroll, chatParams);
            LinearLayout composer = new LinearLayout(this); composer.setGravity(Gravity.CENTER_VERTICAL); root.addView(composer);
            EditText message = new EditText(this); message.setSingleLine(true);
            message.setHint("Сообщение партнёру…");
            composer.addView(message, new LinearLayout.LayoutParams(0, dp(52), 1));
            Button send = new Button(this); send.setText("Отправить"); composer.addView(send);
            send.setOnClickListener(v -> {
                String value = message.getText().toString().trim();
                if (!value.isEmpty()) { sendChatSafe(value); message.setText(""); }
            });
            LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.CENTER); root.addView(actions);
            Button textMode = new Button(this); textMode.setText("Текст ✓"); actions.addView(textMode);
            textMode.setOnClickListener(v -> {
                boolean visible = chatScroll.getVisibility() != View.VISIBLE;
                chatScroll.setVisibility(visible ? View.VISIBLE : View.GONE);
                composer.setVisibility(visible ? View.VISIBLE : View.GONE);
                textMode.setText(visible ? "Текст ✓" : "Текст");
            });
            Button videoMode = new Button(this); videoMode.setText("Видео ✓"); actions.addView(videoMode);
            videoMode.setOnClickListener(v -> {
                videoVisible = !videoVisible;
                if (!host && remoteView != null)
                    remoteView.setVisibility(videoVisible ? View.VISIBLE : View.GONE);
                videoMode.setText(videoVisible ? "Видео ✓" : "Видео");
            });
            Button mic = new Button(this); mic.setText("🎙  Микрофон"); actions.addView(mic);
            mic.setOnClickListener(v -> toggleMicrophoneSafe(mic));
            Button stop = new Button(this); stop.setText("Завершить"); actions.addView(stop);
            stop.setOnClickListener(v -> { finishRoomSafe(); });
            Button fullscreenBtn = new Button(this); fullscreenBtn.setText("На весь экран"); actions.addView(fullscreenBtn);
            fullscreenBtn.setOnClickListener(v -> { toggleFullscreenSafe();
                fullscreenBtn.setText(fullscreen ? "Выйти из полного экрана" : "На весь экран"); });
            if (host) {
                Button share = new Button(this); share.setText("Поделиться кодом"); actions.addView(share);
                share.setOnClickListener(v -> {
                    Intent i = new Intent(Intent.ACTION_SEND); i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_TEXT, "Подключайся к парному просмотру в «Вместе». Код комнаты: " + roomId);
                    startActivity(Intent.createChooser(i, "Пригласить партнёра"));
                });
                Button screenBtn = new Button(this); screenBtn.setText("Начать показ экрана"); actions.addView(screenBtn);
                screenBtn.setOnClickListener(v -> {
                    MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                    if (manager != null) startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
                    else showErrorSafe("Система не поддерживает захват экрана.");
                });
            }
            uiReady = true;
        } catch (Exception e) {
            showErrorSafe("Ошибка интерфейса: " + e.getMessage());
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_MIC && !mediaStarted) {
            boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                mediaStarted = true;
                startRtcSafe(null);
            } else {
                mediaStarted = true;
                main.post(() -> status.setText("Микрофон не разрешён · голосовой чат будет недоступен"));
                startRtcSafe(null);
            }
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAPTURE) {
            if (resultCode != RESULT_OK || data == null) {
                showErrorSafe("Вы не разрешили показ экрана."); return;
            }
            projectionData = data;
            try {
                Intent service = new Intent(this, ScreenShareService.class);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
                else startService(service);
            } catch (Exception ignored) { }
            if (peer != null) startScreenTrackSafe(data);
            else main.post(() -> showErrorSafe("Веб-РТС соединение не готово. Попробуйте снова."));
        }
    }

    private void startRtcSafe(Intent capturePermission) {
        io.execute(() -> {
            try {
                egl = EglBase.create();
                main.post(() -> {
                    if (remoteView != null && !closed)
                        remoteView.init(egl.getEglBaseContext(), null);
                });
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
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    MediaConstraints audioConstraints = new MediaConstraints();
                    audioSource = factory.createAudioSource(audioConstraints);
                    audioTrack = factory.createAudioTrack("vmeste_voice", audioSource);
                    if (peer != null) peer.addTrack(audioTrack, Collections.singletonList("vmeste"));
                } else if (peer != null) {
                    peer.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                        new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY));
                }
                if (host && capturePermission != null) startScreenTrackSafe(capturePermission);
                else if (!host && peer != null)
                    peer.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                        new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY));
                main.post(() -> {
                    if (closed) return;
                    status.setText(host ? "Разрешите показ экрана, чтобы начать трансляцию…"
                        : audioTrack == null ? "Ожидаем трансляцию · микрофон выключен"
                        : "Голос подключён. Ищем трансляцию…");
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
                        status.setText(state == PeerConnection.IceConnectionState.CONNECTED
                            ? "Подключено · голосовой чат активен" : "Связь: " + state.name());
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
                @Override public void onRemoveStream(MediaStream stream) { }
                @Override public void onDataChannel(org.webrtc.DataChannel channel) { }
                @Override public void onRenegotiationNeeded() {
                    if (host) main.post(() -> {
                        if (offerReady && !closed) createOfferSafe();
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

    private void startScreenTrackSafe(Intent permission) {
        main.post(() -> {
            try {
                if (egl == null || factory == null || closed) {
                    showErrorSafe("Веб-РТС соединение не готово. Попробуйте снова.");
                    return;
                }
                screenCapturer = new ScreenCapturerAndroid(permission,
                    new android.media.projection.MediaProjection.Callback() {
                        @Override public void onStop() {
                            main.post(() -> showErrorSafe("Система остановила показ экрана."));
                        }
                    });
                videoSource = factory.createVideoSource(true);
                textureHelper = SurfaceTextureHelper.create("vmeste-screen-capture", egl.getEglBaseContext());
                screenCapturer.initialize(textureHelper, getApplicationContext(), videoSource.getCapturerObserver());
                android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                screenCapturer.startCapture(Math.min(dm.widthPixels, 1280), Math.min(dm.heightPixels, 720), 15);
                screenTrack = factory.createVideoTrack("vmeste_screen", videoSource);
                if (peer != null) peer.addTrack(screenTrack, Collections.singletonList("vmeste"));
            } catch (Exception e) {
                main.post(() -> showErrorSafe("Не удалось запустить захват экрана: " + e.getMessage()));
            }
        });
    }

    private void showRemoteVideoSafe(VideoTrack track) {
        main.post(() -> {
            if (closed || remoteView == null) return;
            remoteView.setVisibility(videoVisible ? View.VISIBLE : View.GONE);
            if (videoVisible) track.addSink(remoteView);
        });
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
                    for (int i = 0; i < batch.length(); i++) {
                        JSONObject item = batch.getJSONObject(i);
                        lastSignalId = Math.max(lastSignalId, item.optLong("id"));
                        if (!sync.userId().equals(item.optString("sender_id"))) processSignalSafe(item);
                    }
                } catch (Exception e) {
                    main.post(() -> status.setText("Ожидание связи… проверьте интернет и доступ к Supabase"));
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
                status.setText("Партнёр подключился · экран и голос передаются");
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
            catch (Exception e) { main.post(() -> status.setText("Не удалось передать сигнал: " + e.getMessage())); }
        }); } catch (Exception ignored) { }
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

    private void toggleMicrophoneSafe(Button button) {
        if (audioTrack == null) return;
        boolean enabled = !audioTrack.enabled();
        audioTrack.setEnabled(enabled);
        button.setText(enabled ? "🎙  Микрофон" : "🔇  Выключен");
    }

    private void toggleFullscreenSafe() {
        fullscreen = !fullscreen;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    if (fullscreen) controller.hide(WindowInsets.Type.systemBars());
                    else controller.show(WindowInsets.Type.systemBars());
                }
            } else {
                int flags = getWindow().getDecorView().getSystemUiVisibility();
                if (fullscreen)
                    flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                else
                    flags &= ~(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
                getWindow().getDecorView().setSystemUiVisibility(flags);
            }
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
        try {
            if (screenCapturer != null) { screenCapturer.stopCapture(); screenCapturer.dispose(); }
        } catch (Exception ignored) { }
        if (peer != null) { peer.close(); peer.dispose(); }
        if (screenTrack != null) screenTrack.dispose();
        if (videoSource != null) videoSource.dispose();
        if (textureHelper != null) textureHelper.dispose();
        if (audioTrack != null) audioTrack.dispose();
        if (audioSource != null) audioSource.dispose();
        if (factory != null) factory.dispose();
        if (remoteView != null) { remoteView.release(); remoteView = null; }
        if (egl != null) egl.release();
        if (audioManager != null) {
            audioManager.setSpeakerphoneOn(false);
            audioManager.setMode(AudioManager.MODE_NORMAL);
        }
        try { stopService(new Intent(this, ScreenShareService.class)); } catch (Exception ignored) { }
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
                if (playerView != null) playerView.setVisibility(View.GONE);
                if (status != null) status.setVisibility(View.GONE);
                if (roomCode != null) roomCode.setVisibility(View.GONE);
                if (remoteView != null) remoteView.setVisibility(View.GONE);
                if (root != null) root.setPadding(dp(4), dp(4), dp(4), dp(4));
            } else {
                if (playerView != null) playerView.setVisibility(View.VISIBLE);
                if (status != null) status.setVisibility(View.VISIBLE);
                if (roomCode != null) roomCode.setVisibility(View.VISIBLE);
                if (!host && remoteView != null)
                    remoteView.setVisibility(videoVisible ? View.VISIBLE : View.GONE);
                if (root != null) root.setPadding(dp(16), dp(12), dp(16), dp(10));
            }
        } catch (Exception ignored) { }
    }

    @Override protected void onDestroy() {
        try {
            if (playerView != null) {
                playerView.stopLoading();
                playerView.loadUrl("about:blank");
                playerView.destroy();
                playerView = null;
            }
        } catch (Exception ignored) { }
        stopRtcSafe();
        super.onDestroy();
    }
}
