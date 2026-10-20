package com.lira.vmest;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

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
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<IceCandidate> pendingCandidates = new ArrayList<>();
    private final List<String> lines = new ArrayList<>();
    private SupabaseSync sync;
    private String roomId, title;
    private String playerUrl;
    private boolean host, remoteReady, closed;
    private long lastSignalId;
    private LinearLayout root, chatLines;
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

    public static Intent intent(Context c, String roomId, boolean host, String title) {
        Intent i = new Intent(c, WatchPartyActivity.class);
        i.putExtra(EXTRA_ROOM_ID, roomId); i.putExtra(EXTRA_HOST, host); i.putExtra(EXTRA_TITLE, title);
        return i;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        roomId = getIntent().getStringExtra(EXTRA_ROOM_ID);
        host = getIntent().getBooleanExtra(EXTRA_HOST, false);
        title = getIntent().getStringExtra(EXTRA_TITLE);
        playerUrl = getIntent().getStringExtra(EXTRA_PLAYER_URL);
        if (roomId == null || roomId.isEmpty()) { finish(); return; }
        if (title == null) title = "Совместный просмотр";
        sync = new SupabaseSync(this);
        buildUi();
        if (!sync.configured() || !sync.signedIn() || sync.coupleId().isEmpty()) {
            showError("Сначала подключите Supabase, войдите и создайте общее пространство в разделе «Пригласить партнёра»."); return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
        } else prepareMedia();
    }

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private TextView label(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD); return t;
    }

    private void buildUi() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16), dp(12), dp(16), dp(10));
        root.setBackgroundColor(Color.rgb(250, 247, 252)); setContentView(root);
        TextView heading = label("🎬  " + title, 21, Color.rgb(48, 43, 53), true); root.addView(heading);
        roomCode = label("Код комнаты: " + roomId, 12, Color.GRAY, false); roomCode.setPadding(0, dp(4), 0, dp(4)); root.addView(roomCode);
        status = label(host ? "Подготовка трансляции…" : "Ожидаем трансляцию партнёра…", 14, Color.rgb(102, 77, 128), true);
        status.setPadding(0, dp(8), 0, dp(8)); root.addView(status);
        LinearLayout playerControls=new LinearLayout(this);playerControls.setGravity(Gravity.CENTER_VERTICAL);playerControls.setPadding(0,dp(4),0,dp(4));
        EditText playerAddress=new EditText(this);playerAddress.setSingleLine(true);playerAddress.setHint("Ссылка на видео для просмотра внутри приложения");if(playerUrl!=null)playerAddress.setText(playerUrl);
        playerControls.addView(playerAddress,new LinearLayout.LayoutParams(0,dp(48),1));
        Button play=new Button(this);play.setText("Открыть");playerControls.addView(play);root.addView(playerControls);
        playerView=new WebView(this);playerView.setBackgroundColor(Color.BLACK);WebSettings webSettings=playerView.getSettings();webSettings.setJavaScriptEnabled(true);webSettings.setDomStorageEnabled(true);webSettings.setMediaPlaybackRequiresUserGesture(false);webSettings.setAllowFileAccess(false);webSettings.setAllowContentAccess(false);playerView.setWebChromeClient(new WebChromeClient());playerView.setWebViewClient(new WebViewClient());
        root.addView(playerView,new LinearLayout.LayoutParams(-1,dp(220)));
        play.setOnClickListener(v->{String link=playerAddress.getText().toString().trim();if(!link.startsWith("https://")&&!link.startsWith("http://")){playerAddress.setError("Укажите ссылку http или https");return;}playerUrl=link;playerView.loadUrl(link);});
        if(playerUrl!=null&&!playerUrl.trim().isEmpty())playerView.loadUrl(playerUrl);
        remoteView = new SurfaceViewRenderer(this); remoteView.setBackgroundColor(Color.BLACK);
        root.addView(remoteView, new LinearLayout.LayoutParams(-1, 0, 1));
        if (host) remoteView.setVisibility(View.GONE);
        ScrollView chatScroll = new ScrollView(this); chatScroll.setFillViewport(false);
        chatLines = new LinearLayout(this); chatLines.setOrientation(LinearLayout.VERTICAL); chatScroll.addView(chatLines);
        LinearLayout.LayoutParams chatParams = new LinearLayout.LayoutParams(-1, dp(126)); chatParams.topMargin = dp(8); root.addView(chatScroll, chatParams);
        LinearLayout composer = new LinearLayout(this); composer.setGravity(Gravity.CENTER_VERTICAL); root.addView(composer);
        EditText message = new EditText(this); message.setSingleLine(true); message.setHint("Сообщение партнёру…");
        composer.addView(message, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button send = new Button(this); send.setText("Отправить"); composer.addView(send);
        send.setOnClickListener(v -> { String value = message.getText().toString().trim(); if (!value.isEmpty()) { sendChat(value); message.setText(""); } });
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.CENTER); root.addView(actions);
        Button mic = new Button(this); mic.setText("🎙  Микрофон"); actions.addView(mic);
        mic.setOnClickListener(v -> toggleMicrophone(mic));
        Button stop = new Button(this); stop.setText("Завершить"); actions.addView(stop);
        stop.setOnClickListener(v -> { sendSignal("close", new JSONObject()); finish(); });
        if (host) {
            Button share = new Button(this); share.setText("Поделиться кодом"); actions.addView(share);
            share.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_SEND); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TEXT, "Подключайся к парному просмотру в «Вместе». Код комнаты: " + roomId); startActivity(Intent.createChooser(i, "Пригласить партнёра")); });
        }
    }

    private void prepareMedia() {
        if (host) {
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
        } else startRtc(null);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_MIC) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) prepareMedia();
            else showError("Для голосового чата нужен доступ к микрофону.");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAPTURE) {
            if (resultCode != RESULT_OK || data == null) { showError("Вы не разрешили показ экрана."); return; }
            projectionData = data;
            Intent service = new Intent(this, ScreenShareService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
            startRtc(data);
        }
    }

    private void startRtc(Intent capturePermission) {
        try {
            egl = EglBase.create();
            remoteView.init(egl.getEglBaseContext(), null); remoteView.setEnableHardwareScaler(true);
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(getApplicationContext()).createInitializationOptions());
            factory = PeerConnectionFactory.builder()
                    .setVideoEncoderFactory(new DefaultVideoEncoderFactory(egl.getEglBaseContext(), true, true))
                    .setVideoDecoderFactory(new DefaultVideoDecoderFactory(egl.getEglBaseContext())).createPeerConnectionFactory();
            audioManager = (AudioManager) getSystemService(AUDIO_SERVICE); audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION); audioManager.setSpeakerphoneOn(true);
            createPeer();
            MediaConstraints audioConstraints = new MediaConstraints();
            audioSource = factory.createAudioSource(audioConstraints); audioTrack = factory.createAudioTrack("vmeste_voice", audioSource);
            peer.addTrack(audioTrack, Collections.singletonList("vmeste"));
            if (host && capturePermission != null) startScreenTrack(capturePermission);
            else if (!host) peer.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                    new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY));
            status.setText(host ? "Экран выбран. Ищем партнёра…" : "Голос подключён. Ищем трансляцию…");
            startPolling();
            if (host) createOffer();
        } catch (Exception e) { showError("Не удалось запустить комнату: " + e.getMessage()); }
    }

    private void createPeer() {
        List<PeerConnection.IceServer> ice = Collections.singletonList(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(ice);
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        peer = factory.createPeerConnection(config, new PeerConnection.Observer() {
            @Override public void onSignalingChange(PeerConnection.SignalingState state) { }
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
                main.post(() -> status.setText(state == PeerConnection.IceConnectionState.CONNECTED ? "Подключено · голосовой чат активен" : "Связь: " + state.name()));
            }
            @Override public void onIceConnectionReceivingChange(boolean receiving) { }
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) { }
            @Override public void onIceCandidate(IceCandidate candidate) {
                try { JSONObject p = new JSONObject(); p.put("sdp", candidate.sdp); p.put("mid", candidate.sdpMid); p.put("line", candidate.sdpMLineIndex); sendSignal("ice", p); }
                catch (Exception ignored) { }
            }
            @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) { }
            @Override public void onAddStream(MediaStream stream) { if (!stream.videoTracks.isEmpty()) showRemoteVideo(stream.videoTracks.get(0)); }
            @Override public void onRemoveStream(MediaStream stream) { }
            @Override public void onDataChannel(org.webrtc.DataChannel channel) { }
            @Override public void onRenegotiationNeeded() { }
            @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {
                MediaStreamTrack track = receiver.track(); if (track instanceof VideoTrack) showRemoteVideo((VideoTrack) track);
            }
            @Override public void onTrack(RtpTransceiver transceiver) {
                MediaStreamTrack track = transceiver.getReceiver().track(); if (track instanceof VideoTrack) showRemoteVideo((VideoTrack) track);
            }
        });
        if (peer == null) throw new IllegalStateException("WebRTC peer is unavailable");
    }

    private void startScreenTrack(Intent permission) {
        screenCapturer = new ScreenCapturerAndroid(permission, new android.media.projection.MediaProjection.Callback() {
            @Override public void onStop() { main.post(() -> showError("Система остановила показ экрана.")); }
        });
        videoSource = factory.createVideoSource(true);
        textureHelper = SurfaceTextureHelper.create("vmeste-screen-capture", egl.getEglBaseContext());
        screenCapturer.initialize(textureHelper, getApplicationContext(), videoSource.getCapturerObserver());
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        screenCapturer.startCapture(Math.min(dm.widthPixels, 1280), Math.min(dm.heightPixels, 720), 15);
        screenTrack = factory.createVideoTrack("vmeste_screen", videoSource);
        peer.addTrack(screenTrack, Collections.singletonList("vmeste"));
    }

    private void showRemoteVideo(VideoTrack track) {
        main.post(() -> { if (closed) return; remoteView.setVisibility(View.VISIBLE); track.addSink(remoteView); });
    }

    private void createOffer() {
        peer.createOffer(new SdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) {
                peer.setLocalDescription(new SdpObserver() {
                    @Override public void onSetSuccess() { try { JSONObject p = new JSONObject(); p.put("sdp", sdp.description); sendSignal("offer", p); } catch (Exception e) { showError(e.getMessage()); } }
                    @Override public void onSetFailure(String error) { showError(error); }
                    @Override public void onCreateSuccess(SessionDescription ignored) { }
                    @Override public void onCreateFailure(String error) { }
                }, sdp);
            }
            @Override public void onSetSuccess() { }
            @Override public void onCreateFailure(String error) { showError("Не удалось создать трансляцию: " + error); }
            @Override public void onSetFailure(String error) { showError(error); }
        }, new MediaConstraints());
    }

    private void startPolling() {
        io.execute(new Runnable() {
            @Override public void run() {
                if (closed) return;
                try {
                    JSONArray batch = sync.fetchWatchSignals(roomId, lastSignalId);
                    for (int i = 0; i < batch.length(); i++) {
                        JSONObject item = batch.getJSONObject(i); lastSignalId = Math.max(lastSignalId, item.optLong("id"));
                        if (!sync.userId().equals(item.optString("sender_id"))) processSignal(item);
                    }
                } catch (Exception e) { main.post(() -> status.setText("Ожидание связи… проверьте интернет и доступ к Supabase")); }
                if (!closed) main.postDelayed(() -> io.execute(this), 1300);
            }
        });
    }

    private void processSignal(JSONObject item) {
        String kind = item.optString("kind"); JSONObject payload = item.optJSONObject("payload"); if (payload == null) payload = new JSONObject();
        final JSONObject data = payload;
        if ("chat".equals(kind)) { main.post(() -> addChat("Партнёр", data.optString("text"))); return; }
        if ("close".equals(kind)) { main.post(() -> { status.setText("Партнёр завершил просмотр"); stopRtc(); }); return; }
        if ("ice".equals(kind)) {
            try { IceCandidate candidate = new IceCandidate(data.optString("mid"), data.optInt("line"), data.getString("sdp"));
                if (remoteReady) peer.addIceCandidate(candidate); else pendingCandidates.add(candidate);
            } catch (Exception ignored) { }
            return;
        }
        if (host && "answer".equals(kind) && !remoteReady) {
            peer.setRemoteDescription(sdpObserver(() -> { remoteReady = true; flushCandidates(); status.setText("Партнёр подключился · экран и голос передаются"); }),
                    new SessionDescription(SessionDescription.Type.ANSWER, data.optString("sdp")));
        } else if (!host && "offer".equals(kind) && !remoteReady) {
            peer.setRemoteDescription(sdpObserver(() -> { remoteReady = true; flushCandidates(); createAnswer(); }),
                    new SessionDescription(SessionDescription.Type.OFFER, data.optString("sdp")));
        }
    }

    private SdpObserver sdpObserver(Runnable success) {
        return new SdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) { }
            @Override public void onSetSuccess() { success.run(); }
            @Override public void onCreateFailure(String error) { showError(error); }
            @Override public void onSetFailure(String error) { showError(error); }
        };
    }

    private void createAnswer() {
        peer.createAnswer(new SdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) {
                peer.setLocalDescription(sdpObserver(() -> { try { JSONObject p = new JSONObject(); p.put("sdp", sdp.description); sendSignal("answer", p); } catch (Exception e) { showError(e.getMessage()); } }), sdp);
            }
            @Override public void onSetSuccess() { }
            @Override public void onCreateFailure(String error) { showError(error); }
            @Override public void onSetFailure(String error) { showError(error); }
        }, new MediaConstraints());
    }

    private void flushCandidates() { for (IceCandidate candidate : pendingCandidates) peer.addIceCandidate(candidate); pendingCandidates.clear(); }
    private void sendSignal(String kind, JSONObject payload) { if (closed && !"close".equals(kind)) return; io.execute(() -> { try { sync.sendWatchSignal(roomId, kind, payload); } catch (Exception e) { main.post(() -> status.setText("Не удалось передать сигнал: " + e.getMessage())); } }); }
    private void sendChat(String value) { try { JSONObject data = new JSONObject(); data.put("text", value); addChat("Вы", value); sendSignal("chat", data); } catch (Exception ignored) { } }
    private void addChat(String author, String message) {
        TextView row = label(author + ": " + message, 13, Color.rgb(54, 48, 62), false);
        chatLines.addView(row); if (chatLines.getChildCount() > 80) chatLines.removeViewAt(0);
    }
    private void toggleMicrophone(Button button) { if (audioTrack == null) return; boolean enabled = !audioTrack.enabled(); audioTrack.setEnabled(enabled); button.setText(enabled ? "🎙  Микрофон" : "🔇  Выключен"); }
    private void showError(String message) { main.post(() -> new AlertDialog.Builder(this).setTitle("Кино-комната").setMessage(message).setPositiveButton("Закрыть", (d, w) -> finish()).show()); }

    private void stopRtc() {
        if (closed) return; closed = true; main.removeCallbacksAndMessages(null); io.shutdownNow();
        try { if (screenCapturer != null) { screenCapturer.stopCapture(); screenCapturer.dispose(); } } catch (Exception ignored) { }
        if (peer != null) { peer.close(); peer.dispose(); }
        if (screenTrack != null) screenTrack.dispose(); if (videoSource != null) videoSource.dispose();
        if (textureHelper != null) textureHelper.dispose(); if (audioTrack != null) audioTrack.dispose(); if (audioSource != null) audioSource.dispose();
        if (factory != null) factory.dispose(); if (remoteView != null) { remoteView.release(); remoteView = null; }
        if (egl != null) egl.release(); if (audioManager != null) { audioManager.setSpeakerphoneOn(false); audioManager.setMode(AudioManager.MODE_NORMAL); }
        stopService(new Intent(this, ScreenShareService.class));
    }

    @Override public void onBackPressed() { new AlertDialog.Builder(this).setMessage("Завершить комнату просмотра?")
            .setNegativeButton("Остаться", null).setPositiveButton("Завершить", (d,w) -> { sendSignal("close", new JSONObject()); finish(); }).show(); }
    @Override protected void onDestroy() { if(playerView!=null){playerView.stopLoading();playerView.loadUrl("about:blank");playerView.destroy();playerView=null;}stopRtc(); super.onDestroy(); }
}
