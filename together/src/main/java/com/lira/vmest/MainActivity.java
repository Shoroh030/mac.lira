package com.lira.vmest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.integration.android.IntentResult;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.integration.android.IntentIntegrator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Random;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/** A standalone, offline-first shared-life companion prototype. */
public class MainActivity extends Activity {
    private int BG = 0xFFFBF8FC;
    private int PAPER = 0xFFFFFFFF;
    private int INK = 0xFF302B35;
    private int MUTED = 0xFF6E6575;
    private int PURPLE = 0xFF8158A5;
    private int PALE = 0xFFF0E8F6;
    private int LINE = 0xFFE9E1ED;
    private boolean softDark;
    private boolean firstRunWelcomeVisible;
    private boolean deferGameNotificationPermission;
    private android.content.SharedPreferences prefs;
    private LinearLayout root, body, nav;
    private ScrollView contentScroll;
    private String tab = "Главная";
    private String feature = null;
    private View chatComposer;
    private android.media.MediaRecorder chatRecorder;
    private java.io.File chatRecording;
    private boolean movieRoomFetchInFlight;
    private boolean pendingLoyaltyScan;
    private boolean pendingWatchRoomScan;
    private JSONArray activeWatchRoomsSnapshot = new JSONArray();
    private boolean activeWatchRoomsFetchInFlight, activeWatchRoomsUnavailable;
    private boolean watchRoomListInFlight, watchRoomCreationInFlight;
    private String activeWatchRoomsFailureLabel = "";
    private String pendingWatchRoomInviteId = "";
    private long lastActiveWatchRoomsFetchAt;
    private boolean coupleRestorePending, coupleRestoreFailed;
    private String pendingPhotoKey = "", pendingPhotoItemId = "";
    private String pendingStoryCaption = "";
    private String storiesFilter = "";
    private String capsuleAnimationId = "";
    private String pendingFileDownloadPath = "", pendingFileDownloadName = "";
    private String pendingSharedText = "";
    private Uri pendingSharedUri;
    private final ChatScrollPolicy chatScrollPolicy = new ChatScrollPolicy();
    private int chatScrollY;
    private Runnable chatRefreshRunnable;
    private boolean activityResumed;
    private Runnable sharedTimerTick, sharedTimerPoll;
    private Calendar visibleMonth = Calendar.getInstance();
    private Calendar selectedDay = Calendar.getInstance();
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private SupabaseSync sync;
    private PairGameSync pairGames;
    private int pendingCameraRequestCode = 0;
    private final Handler gameHandler = new Handler(Looper.getMainLooper());
    private Runnable gameInvitePoll;
    private Runnable sharedSyncPoll;
    private long lastProfileRefreshAt;

    private static final String[][] MORE_GROUPS = {
            {"Общение", "💬  Чат для двоих", "📷  Истории для двоих", "💌  Пригласить партнёра"},
            {"Планы и заметки", "🗓  Календарь", "💜  Хотим вместе", "🧳  Общие планы", "📝  Заметки", "🍲  Рецепты", "⏳  Капсула времени", "📎  Файлы"},
            {"Совместное время", "🎲  Совместные игры", "🎬  Комната просмотра", "🗳  Голосование", "🎁  Подарочный радар"},
            {"Полезное", "💳  Карты лояльности", "💗  Карта партнёра"}
    };
    private static final String[] TUNES={"Ода к радости","Вальс цветов","Маленькая ночная серенада","Щелкунчик: Танец феи Драже"};
    private static final int MAX_CHAT_ATTACHMENT_BYTES=12*1024*1024;
    private static final String[] LOYALTY_FORMATS = {
            "CODE_128", "CODE_39", "EAN_13", "EAN_8", "UPC_A", "UPC_E",
            "ITF", "CODABAR", "QR_CODE", "DATA_MATRIX"
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        pendingLoyaltyScan = state != null && state.getBoolean("pending_loyalty_scan", false);
        pendingWatchRoomScan = state != null && state.getBoolean("pending_watch_room_scan", false);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        prefs = getSharedPreferences("vmeste_data", MODE_PRIVATE);
        softDark = prefs.getBoolean("soft_dark_theme", false); applyPalette();
        sync = new SupabaseSync(this);
        pairGames = new PairGameSync(this);
        coupleRestorePending = sync.signedIn() && sync.coupleId().isEmpty();
        showApp();
        boolean incomingShare = Intent.ACTION_SEND.equals(getIntent().getAction())
                || Intent.ACTION_SEND_MULTIPLE.equals(getIntent().getAction());
        boolean incomingWatchRoomInvite = watchRoomInviteFromIntent(getIntent()) != null;
        boolean firstRunWelcome = FirstRunOnboarding.shouldShow(sync.signedIn(),
                prefs.getBoolean("first_run_auth_prompt_shown", false), incomingShare || incomingWatchRoomInvite);
        if (firstRunWelcome) {
            deferGameNotificationPermission = true;
            showFirstRunWelcome();
        }

        if (coupleRestorePending) root.post(this::restoreCoupleBinding);
        handleIncomingShare(getIntent());
        if(sync.projectChanged())toast("Supabase-проект изменён. Войдите заново и подключите пару.");
        if (!firstRunWelcome && !sync.signedIn()
                && !prefs.getBoolean("first_run_auth_prompt_shown", false) && !incomingShare) {
            prefs.edit().putBoolean("first_run_auth_prompt_shown", true).apply();
            root.postDelayed(this::accountDialog, 500);
        }
        refreshProfiles();
        if (!firstRunWelcome) requestGameNotificationPermission();
        startGameInvitePolling();
        gameHandler.postDelayed(() -> checkGitHubUpdate(false), 2500);
    }

    private void showFirstRunWelcome() {
        firstRunWelcomeVisible = true;
        FrameLayout welcome = new FrameLayout(this);
        welcome.setBackgroundColor(BG);
        GalaxyBackdropView galaxy = new GalaxyBackdropView(this);
        welcome.addView(galaxy, new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = column();
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(28), dp(24), dp(28), dp(24));
        TextView title = text("Ближе, даже когда далеко", 29, INK, true);
        title.setGravity(Gravity.CENTER);
        content.addView(title);
        TextView description = text(
                "Общие планы, разговоры и маленькие поводы быть рядом — в одном месте.",
                16, MUTED, false);
        description.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.topMargin = dp(12);
        content.addView(description, descriptionParams);

        Button continueButton = new Button(this);
        continueButton.setText("Продолжить");
        LinearLayout.LayoutParams continueParams = new LinearLayout.LayoutParams(-1, dp(52));
        continueParams.topMargin = dp(28);
        content.addView(continueButton, continueParams);
        TextView skip = text("Пропустить", 15, PURPLE, true);
        skip.setGravity(Gravity.CENTER);
        skip.setMinHeight(dp(48));
        LinearLayout.LayoutParams skipParams = new LinearLayout.LayoutParams(-1, dp(48));
        skipParams.topMargin = dp(4);
        content.addView(skip, skipParams);
        skip.setOnClickListener(v -> completeFirstRunWelcome());
        continueButton.setOnClickListener(v -> completeFirstRunWelcome());

        scroll.addView(content, new ScrollView.LayoutParams(-1, -1));
        welcome.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(welcome);
    }

    private void completeFirstRunWelcome() {
        if (!firstRunWelcomeVisible) return;
        firstRunWelcomeVisible = false;
        prefs.edit().putBoolean("first_run_auth_prompt_shown", true).apply();
        showApp();
        root.postDelayed(this::accountDialog, 180);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingShare(intent);
    }

    private void handleIncomingShare(Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            String roomId = watchRoomInviteFromIntent(intent);
            clearExternalIntent();
            if (roomId == null) toast("Ссылка на комнату недействительна");
            else handleWatchRoomInvite(roomId);
            return;
        }
        if (!Intent.ACTION_SEND.equals(intent.getAction())
                && !Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) return;
        CharSequence sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        String inviteId = WatchRoomQrCode.decodeInvitation(
                sharedText == null ? "" : sharedText.toString());
        if (inviteId != null) {
            clearExternalIntent();
            handleWatchRoomInvite(inviteId);
            return;
        }
        pendingSharedText = sharedText == null ? "" : sharedText.toString().trim();
        Uri sharedUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (sharedUri == null && Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            ArrayList<Uri> streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (streams != null && !streams.isEmpty()) sharedUri = streams.get(0);
        }
        pendingSharedUri = sharedUri;
        if (pendingSharedUri == null && pendingSharedText.isEmpty()) {
            toast("В отправленном материале нет текста или изображения");
            return;
        }
        if (pendingSharedUri != null) {
            try {
                getContentResolver().takePersistableUriPermission(
                        pendingSharedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) { }
        }
        clearExternalIntent();
        tab = "Чат";
        feature = null;
        chatScrollPolicy.jumpToLatest();
        render();
        toast(sync.ready()
                ? "Материал готов к отправке партнёру в чате"
                : "Материал добавлен в черновик чата. Подключите пару перед отправкой");
    }

    private String watchRoomInviteFromIntent(Intent intent) {
        if (intent == null) return null;
        if (Intent.ACTION_VIEW.equals(intent.getAction()))
            return WatchRoomQrCode.decodeInvitation(intent.getDataString());
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            return WatchRoomQrCode.decodeInvitation(text == null ? "" : text.toString());
        }
        return null;
    }

    private void clearExternalIntent() {
        setIntent(new Intent(this, MainActivity.class));
    }

    private void handleWatchRoomInvite(String roomId) {
        pendingWatchRoomInviteId = roomId;
        if (!sync.configured()) {
            new AlertDialog.Builder(this)
                    .setTitle("Настройте подключение")
                    .setMessage("Чтобы открыть приглашение, сначала подключите Supabase в разделе «Пригласить партнёра».")
                    .setPositiveButton("Открыть настройки", (dialog, which) -> {
                        feature=null;tab=NavigationModel.SETTINGS_TAB;render();
                    })
                    .setNegativeButton("Позже", null)
                    .show();
            return;
        }
        if (!sync.signedIn()) {
            new AlertDialog.Builder(this)
                    .setTitle("Войдите, чтобы подключиться")
                    .setMessage("Приглашение получено. Войдите в свой аккаунт, затем подключитесь к той же паре, что и ведущий.")
                    .setPositiveButton("Войти", (dialog, which) -> accountDialog())
                    .setNegativeButton("Позже", null)
                    .show();
            return;
        }
        if (sync.coupleId().isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Подключите свою пару")
                    .setMessage("В разделе «Пригласить партнёра» подключитесь к существующей паре ведущего. Не создавайте новую пару для этого приглашения.")
                    .setPositiveButton("Открыть раздел", (dialog, which) -> {
                        tab = "Пригласить";
                        feature = null;
                        render();
                    })
                    .setNegativeButton("Позже", null)
                    .show();
            return;
        }
        resumePendingWatchRoomInvite();
    }

    private void resumePendingWatchRoomInvite() {
        if (pendingWatchRoomInviteId.isEmpty() || !sync.ready()) return;
        String roomId = pendingWatchRoomInviteId;
        pendingWatchRoomInviteId = "";
        openJoinableWatchRoom(roomId);
    }

    private void requestGameNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 903);
    }

    private void startGameInvitePolling() {
        gameInvitePoll = new Runnable() {
            @Override public void run() {
                if (pairGames != null && pairGames.ready()) network.execute(() -> {
                    try {
                        JSONArray invites=pairGames.waitingInvites();
                        for(int i=invites.length()-1;i>=0;i--) {
                            JSONObject invite=invites.optJSONObject(i); if(invite==null)continue;
                            JSONObject state=invite.optJSONObject("state");
                            if(state==null||!state.optString("p2").isEmpty()||!isRecentGameInvite(invite.optString("updated_at")))continue;
                            String id=invite.optString("id");
                            if(id.isEmpty()||hasSeenGameInvite(id))continue;
                            rememberGameInvite(id);
                            runOnUiThread(()->notifyGameInvite(invite.optString("game_type")));
                        }
                    } catch(Exception e) { android.util.Log.w("VmesteGame","Game invite check failed",e); }
                });
                gameHandler.postDelayed(this,15000);
            }
        };
        gameHandler.postDelayed(gameInvitePoll,5000);
    }

    private boolean isRecentGameInvite(String value) {
        try { if(value==null||value.length()<19)return false;java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.US);f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));return System.currentTimeMillis()-f.parse(value.substring(0,19)).getTime()<90000; } catch(Exception ignored) { return false; }
    }
    private boolean hasSeenGameInvite(String id) { JSONArray ids=read("seen_game_invites"); for(int i=0;i<ids.length();i++)if(id.equals(ids.optString(i)))return true;return false; }
    private void rememberGameInvite(String id) { JSONArray ids=read("seen_game_invites");ids.put(id);while(ids.length()>100)ids.remove(0);save("seen_game_invites",ids); }
    private void notifyGameInvite(String type) {
        if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        String title="Партнёр ждёт вас в игре", name="парной игре";
        if("durak".equals(type))name="Дураке";else if("sea_battle".equals(type))name="Морском бое";else if("tic_tac_toe".equals(type))name="Крестиках-ноликах";
        String channel="vmeste_game_invites";
        if(android.os.Build.VERSION.SDK_INT>=26){android.app.NotificationChannel c=new android.app.NotificationChannel(channel,"Игровые приглашения",android.app.NotificationManager.IMPORTANCE_DEFAULT);((android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);}
        android.app.Notification.Builder b=android.os.Build.VERSION.SDK_INT>=26?new android.app.Notification.Builder(this,channel):new android.app.Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText("Вас ждут в "+name+" · откройте раздел «Игры»").setAutoCancel(true);
        ((android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(type.hashCode(),b.build());
    }

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private android.graphics.Bitmap cropAvatar(android.graphics.Bitmap source,int size){
        if(source==null)return null;int side=Math.min(source.getWidth(),source.getHeight());if(side<1)return null;
        android.graphics.Bitmap square=android.graphics.Bitmap.createBitmap(source,(source.getWidth()-side)/2,(source.getHeight()-side)/2,side,side);
        android.graphics.Bitmap circle=android.graphics.Bitmap.createBitmap(size,size,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(circle);
        android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG|android.graphics.Paint.FILTER_BITMAP_FLAG);paint.setShader(new android.graphics.BitmapShader(square,android.graphics.Shader.TileMode.CLAMP,android.graphics.Shader.TileMode.CLAMP));canvas.drawCircle(size/2f,size/2f,size/2f,paint);return circle;
    }
    private boolean setAvatarPicture(ImageView view,String uri,int size){
        view.setBackground(shape(PALE,Math.max(1,size/2)));view.setClipToOutline(true);
        if(uri==null||uri.isEmpty()){view.setImageBitmap(null);return false;}try{android.graphics.Bitmap bitmap;
            if(uri.startsWith("data:image/")){byte[] bytes=android.util.Base64.decode(uri.substring(uri.indexOf(',')+1),android.util.Base64.DEFAULT);android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();opts.inSampleSize=2;bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);}
            else if(uri.startsWith("file://")){java.io.File f=new java.io.File(Uri.parse(uri).getPath());if(!f.exists()){view.setImageBitmap(null);return false;}android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;try(java.io.InputStream in=new java.io.FileInputStream(f)){android.graphics.BitmapFactory.decodeStream(in,null,bounds);}int sample=1;while(bounds.outWidth>0&&bounds.outHeight>0&&(bounds.outWidth/sample>1024||bounds.outHeight/sample>1024))sample*=2;android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();opts.inSampleSize=sample;try(java.io.InputStream in=new java.io.FileInputStream(f)){bitmap=android.graphics.BitmapFactory.decodeStream(in,null,opts);}}
            else{android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;try(java.io.InputStream in=getContentResolver().openInputStream(Uri.parse(uri))){android.graphics.BitmapFactory.decodeStream(in,null,bounds);}int sample=1;while(bounds.outWidth>0&&bounds.outHeight>0&&(bounds.outWidth/sample>1024||bounds.outHeight/sample>1024))sample*=2;android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();opts.inSampleSize=sample;try(java.io.InputStream in=getContentResolver().openInputStream(Uri.parse(uri))){bitmap=android.graphics.BitmapFactory.decodeStream(in,null,opts);}}
            bitmap=orientAvatar(bitmap,uri);
            android.graphics.Bitmap rounded=cropAvatar(bitmap,dp(size));if(rounded!=null){view.setImageBitmap(rounded);return true;}view.setImageBitmap(null);return false;
        }catch(Exception ignored){view.setImageBitmap(null);return false;}}
    private android.graphics.Bitmap orientAvatar(android.graphics.Bitmap source,String uri){
        if(source==null||uri==null||uri.isEmpty())return source;
        int orientation=android.media.ExifInterface.ORIENTATION_NORMAL;
        try(java.io.InputStream input=avatarInputStream(uri)){
            if(input!=null)orientation=new android.media.ExifInterface(input).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,android.media.ExifInterface.ORIENTATION_NORMAL);
        }catch(Exception e){android.util.Log.i("VmesteProfile","Photo orientation metadata is unavailable",e);}
        AvatarImageTransform transform=AvatarImageTransform.fromExifOrientation(orientation);
        if(transform.rotationDegrees==0&&!transform.flipHorizontal)return source;
        android.graphics.Matrix matrix=new android.graphics.Matrix();
        matrix.setRotate(transform.rotationDegrees);
        if(transform.flipHorizontal)matrix.postScale(-1f,1f);
        android.graphics.Bitmap oriented=android.graphics.Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),matrix,true);
        if(oriented!=source)source.recycle();
        return oriented;
    }
    private java.io.InputStream avatarInputStream(String uri)throws Exception{
        if(uri.startsWith("data:image/"))return new java.io.ByteArrayInputStream(android.util.Base64.decode(uri.substring(uri.indexOf(',')+1),android.util.Base64.DEFAULT));
        if(uri.startsWith("file://"))return new java.io.FileInputStream(new File(Uri.parse(uri).getPath()));
        return getContentResolver().openInputStream(Uri.parse(uri));
    }
    private String avatarData(Uri uri)throws Exception{
        android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        try(java.io.InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new Exception("Фото не найдено");android.graphics.BitmapFactory.decodeStream(in,null,bounds);}
        if(bounds.outWidth<1||bounds.outHeight<1)throw new Exception("Не удалось прочитать изображение");
        int sample=1;while(bounds.outWidth/sample>1200||bounds.outHeight/sample>1200)sample*=2;
        android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();opts.inSampleSize=sample;
        android.graphics.Bitmap source;
        try(java.io.InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new Exception("Фото больше недоступно");source=android.graphics.BitmapFactory.decodeStream(in,null,opts);}
        if(source==null)throw new Exception("Формат изображения не поддерживается");
        source=orientAvatar(source,uri.toString());
        int side=Math.min(source.getWidth(),source.getHeight());
        android.graphics.Bitmap square=android.graphics.Bitmap.createBitmap(source,(source.getWidth()-side)/2,(source.getHeight()-side)/2,side,side);
        android.graphics.Bitmap scaled=android.graphics.Bitmap.createScaledBitmap(square,256,256,true);
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        if(!scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG,82,out))throw new Exception("Не удалось подготовить фото");
        if(scaled!=square)scaled.recycle();if(square!=source)square.recycle();source.recycle();
        return "data:image/jpeg;base64,"+android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP);
    }
    private void applyPalette() {
        if (softDark) { BG=0xFF211D25; PAPER=0xFF302A35; INK=0xFFF2EDF5; MUTED=0xFFB8ACBF; PURPLE=0xFFD0B3E9; PALE=0xFF403648; LINE=0xFF504657; getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG); getWindow().getDecorView().setSystemUiVisibility(0); }
        else { BG=0xFFFBF8FC; PAPER=0xFFFFFFFF; INK=0xFF302B35; MUTED=0xFF6E6575; PURPLE=0xFF8158A5; PALE=0xFFF0E8F6; LINE=0xFFE9E1ED; getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG); int visibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; if(android.os.Build.VERSION.SDK_INT>=26)visibility|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR; getWindow().getDecorView().setSystemUiVisibility(visibility); }
    }
    private int themedFill(int c) { if(!softDark)return c; if(c==0xFFFBF8FC||c==0xFFFBF9FC||c==0xFFFFFFFF)return PAPER; if(c==0xFFF0E8F6||c==0xFFEFE5F6||c==0xFFEDE4F1||c==0xFFE7D9F0)return PALE; if(c==0xFFF3F4F6||c==0xFFE9E1ED)return 0xFF39333E; return c; }
    private GradientDrawable shape(int fill, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(themedFill(fill)); d.setCornerRadius(dp(radius)); return d;
    }
    private GradientDrawable gameDisc(int fill, int outline) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        d.setStroke(dp(2), outline);
        return d;
    }
    private TextView text(String s, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }

    private void showApp() {
        root = column(); root.setBackgroundColor(BG); setContentView(root);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(13), dp(20), dp(12));
        LinearLayout brand = column();
        brand.addView(text("вместе", 23, INK, true));
        brand.addView(text("ваша маленькая вселенная", 11, MUTED, false));
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        String profileName = prefs.getString("profile_name", "").trim();
        String avatarEmoji = prefs.getString("profile_avatar", "♡");
        String avatarUri = localAvatarUri();
        LinearLayout avatars = new LinearLayout(this); avatars.setOrientation(LinearLayout.HORIZONTAL); avatars.setGravity(Gravity.CENTER); avatars.setPadding(dp(4),0,dp(4),0);
        ImageView avatarImg = new ImageView(this); avatarImg.setScaleType(ImageView.ScaleType.CENTER_CROP); avatarImg.setBackgroundColor(0xFFE8E0EC); avatarImg.setClipToOutline(true);
        boolean hasProfilePhoto=setAvatarPicture(avatarImg,avatarUri,24);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(dp(24), dp(24)); imgParams.rightMargin=dp(6); avatars.addView(avatarImg, imgParams);
        String visibleProfileName = hasProfilePhoto
                ? (profileName.isEmpty() ? "Профиль" : profileName)
                : profileName.isEmpty() ? avatarEmoji + "  Профиль" : avatarEmoji + "  " + profileName;
        TextView avatarText = text(visibleProfileName, 13, PURPLE, true);
        avatarText.setGravity(Gravity.CENTER);
        avatarText.setMaxLines(1);
        avatarText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        avatars.addView(avatarText);
        avatars.setBackground(glassSurface(PALE, 22));
        avatars.setContentDescription("Открыть настройки профиля" + (profileName.isEmpty() ? "" : ": " + profileName));
        header.addView(avatars, new LinearLayout.LayoutParams(dp(120), dp(48)));
        avatars.setOnClickListener(v -> { feature=null;tab=NavigationModel.SETTINGS_TAB;render(); });
        root.addView(header);

        ScrollView scroll = new ScrollView(this); contentScroll = scroll; scroll.setFillViewport(false); scroll.setClipToPadding(false);
        body = column(); body.setPadding(dp(18), dp(6), dp(18), dp(20)); scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        nav = new LinearLayout(this); nav.setGravity(Gravity.CENTER); nav.setPadding(dp(6), dp(6), dp(6), dp(8));
        nav.setBackground(navigationSurface());
        nav.setElevation(dp(10));
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(68)));
        render();
    }

    private void render() {
        if (root != null) root.setBackgroundColor(BG);
        if (chatComposer != null && chatComposer.getParent() == root) root.removeView(chatComposer);
        chatComposer = null;
        if(contentScroll!=null)contentScroll.setFillViewport(false);
        body.removeAllViews(); nav.removeAllViews();
        renderWatchRoomInvitation();
        if (feature != null && feature.startsWith("list:")) renderSharedList();
        else if (feature != null) renderFeature();
        else if (tab.equals("Главная")) renderHome();
        else if (tab.equals("Дела")) renderTasks();
        else if (tab.equals("Календарь")) renderCalendar();
        else if (tab.equals("Хотим")) renderCollection("Хочется вместе", "Маленькие и большие мечты", "wishes", "Добавить хотелку");
        else if (tab.equals("Чат")) renderChat();
        else if (tab.equals("Ещё")) renderMore();
        else if (tab.equals("Игры")) renderGamesTab();
        else if (tab.equals(NavigationModel.SETTINGS_TAB)) renderSettingsTab();
        else if (tab.equals("Просмотр")) renderMovieRoomTab();
        else if (tab.equals("Пригласить")) renderInvitePartnerTab();
        else if (tab.equals("Подарочный радар")) renderGiftRadarTab();
        else if (tab.equals("Истории")) renderStoriesTab();
        else if (tab.equals("Капсула")) renderTimeCapsuleTab();
        else if (tab.equals("Карты")) renderLoyaltyCardsTab();
        else if (tab.equals("Голосование")) renderVotingTab();
        else if (tab.equals("Файлы")) renderFilesTab();
        for (String item : NavigationModel.bottomTabs(navigationTabs(), hiddenNavigationTabs())) {
            final String dest = item;
            boolean selected = item.equals(tab) && feature == null;
            TextView b = text(iconFor(item) + "\n" + item, 10, selected ? (softDark ? 0xFFF2E8FA : PURPLE) : (softDark ? 0xFFB8AFBF : MUTED), selected);
            b.setGravity(Gravity.CENTER); b.setPadding(dp(2), dp(3), dp(2), dp(3));
            b.setMinHeight(dp(48));
            b.setContentDescription(item);
            b.setSelected(selected);
            b.setBackground(navigationItem(selected));
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(0, -1, 1);
            itemParams.setMargins(dp(2), 0, dp(2), 0);
            nav.addView(b, itemParams);
            b.setTag(dest);
            b.setOnClickListener(v -> { feature = null; tab = dest; if ("Чат".equals(dest)) chatScrollPolicy.jumpToLatest(); render(); });
        }
        if ((feature != null && feature.contains("Чат")) || "Чат".equals(tab)) {
            chatComposer = createChatComposer();
            root.addView(chatComposer, root.indexOfChild(nav));
        }
    }
    private ArrayList<String> navigationTabs(){
        List<String> defaults = NavigationModel.defaultOrder();
        ArrayList<String> order = new ArrayList<>();
        try {
            JSONArray saved = new JSONArray(prefs.getString("navigation_tab_order", "[]"));
            for (int i = 0; i < saved.length(); i++) {
                String item = saved.optString(i);
                if (defaults.contains(item) && !order.contains(item)) order.add(item);
            }
        } catch (Exception ignored) { }
        for (String item : defaults) if (!order.contains(item)) order.add(item);
        order.remove(NavigationModel.MORE_TAB);
        order.add(NavigationModel.MORE_TAB);
        return order;
    }
    private HashSet<String> hiddenNavigationTabs(){
        HashSet<String> hidden = new HashSet<>();
        if (!prefs.contains("navigation_tab_hidden")) hidden.addAll(NavigationModel.defaultHidden());
        else {
            try {
                JSONArray saved = new JSONArray(prefs.getString("navigation_tab_hidden", "[]"));
                List<String> knownTabs = NavigationModel.defaultOrder();
                for (int i = 0; i < saved.length(); i++) {
                    String item = saved.optString(i);
                    if (knownTabs.contains(item)) hidden.add(item);
                }
            } catch (Exception ignored) { }
        }
        hidden.remove(NavigationModel.MORE_TAB);
        hidden.remove(NavigationModel.SETTINGS_TAB);
        return hidden;
    }
    private void navigationSettings(){
        try{
            ArrayList<String> order=navigationTabs();
            HashSet<String> hidden=hiddenNavigationTabs();
            LinearLayout list=column();
            list.setPadding(dp(16),dp(10),dp(16),dp(10));
            TextView help=text("На панели закреплены «Настройки» и «Ещё». Выберите до трёх основных разделов и задайте их порядок.",13,MUTED,false);
            list.addView(help);
            ScrollView scroll=new ScrollView(this);
            scroll.setFillViewport(false);
            scroll.addView(list);
            scroll.setBackgroundColor(PAPER);
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Разделы на панели")
                    .setView(scroll).setNegativeButton("Отмена",null)
                    .setPositiveButton("Сохранить",null).create();
            Runnable[] draw={null};
            draw[0]=()->{
                list.removeAllViews();
                list.addView(help);
                for(int i=0;i<order.size();i++){
                    final int at=i;
                    String item=order.get(i);
                    boolean pinned=NavigationModel.MORE_TAB.equals(item)
                            ||NavigationModel.SETTINGS_TAB.equals(item);
                    LinearLayout row=new LinearLayout(this);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(8),dp(3),dp(8),dp(3));
                    row.setBackground(shape(softDark?0xFF39333E:0xFFFBF9FC,14));
                    LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(54));
                    rp.topMargin=dp(6);
                    list.addView(row,rp);
                    android.widget.CheckBox visible=new android.widget.CheckBox(this);
                    visible.setText(pinned?item+" · закреплено":item);
                    visible.setTextColor(INK);
                    visible.setChecked(pinned||!hidden.contains(item));
                    visible.setEnabled(!pinned);
                    visible.setButtonTintList(android.content.res.ColorStateList.valueOf(PURPLE));
                    row.addView(visible,new LinearLayout.LayoutParams(0,-1,1));
                    visible.setOnCheckedChangeListener((button,checked)->{
                        int shown=0;
                        for(String route:order){
                            if(!NavigationModel.MORE_TAB.equals(route)
                                    &&!NavigationModel.SETTINGS_TAB.equals(route)
                                    &&!hidden.contains(route))shown++;
                        }
                        if(checked){
                            if(shown>=3){button.setChecked(false);toast("Оставьте на панели не больше трёх основных разделов");}
                            else hidden.remove(item);
                        }else if(shown>1)hidden.add(item);
                        else{button.setChecked(true);toast("Оставьте хотя бы один основной раздел");}
                    });
                    TextView up=text("↑",20,PURPLE,true);
                    up.setGravity(Gravity.CENTER);
                    up.setContentDescription("Переместить "+item+" выше");
                    up.setMinWidth(dp(48));
                    row.addView(up,new LinearLayout.LayoutParams(dp(48),-1));
                    up.setEnabled(at>0&&!pinned);
                    up.setOnClickListener(v->{java.util.Collections.swap(order,at,at-1);draw[0].run();});
                    TextView down=text("↓",20,PURPLE,true);
                    down.setGravity(Gravity.CENTER);
                    down.setContentDescription("Переместить "+item+" ниже");
                    down.setMinWidth(dp(48));
                    row.addView(down,new LinearLayout.LayoutParams(dp(48),-1));
                    down.setEnabled(at<order.size()-1&&!pinned);
                    down.setOnClickListener(v->{java.util.Collections.swap(order,at,at+1);draw[0].run();});
                }
            };
            draw[0].run();
            dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{
                JSONArray sorted=new JSONArray(),hiddenRows=new JSONArray();
                for(String item:order)sorted.put(item);
                for(String item:order)if(hidden.contains(item))hiddenRows.put(item);
                prefs.edit().putString("navigation_tab_order",sorted.toString())
                        .putString("navigation_tab_hidden",hiddenRows.toString()).apply();
                boolean selectedVisible=NavigationModel.SETTINGS_TAB.equals(tab);
                for(String item:order)if(item.equals(tab)&&!hidden.contains(item))selectedVisible=true;
                if(!selectedVisible)for(String item:order)if(!hidden.contains(item)){tab=item;break;}
                dialog.dismiss();
                render();
            }));
            dialog.show();
        }catch(Exception e){toast("Не удалось открыть настройки вкладок: "+e.getMessage());}
    }
    private GradientDrawable navigationSurface() {
        GradientDrawable surface = new GradientDrawable();
        surface.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        surface.setColors(softDark
                ? new int[]{0xF74A414F,0xEB39333E,0xF7302A35}
                : new int[]{0xF8FFFFFF,0xEAFBF8FD,0xF4F3EDF8});
        surface.setCornerRadii(new float[]{dp(18),dp(18),dp(18),dp(18),0,0,0,0});
        surface.setStroke(dp(1), softDark ? 0x88988BA2 : 0xD6FFFFFF);
        return surface;
    }
    private android.graphics.drawable.Drawable navigationItem(boolean selected) {
        GradientDrawable item = new GradientDrawable();
        if (selected) {
            item.setColor(softDark ? 0xFF493D53 : PALE);
            item.setCornerRadius(dp(15));
            item.setStroke(dp(1), softDark ? 0xFF695779 : 0xFFE3D5ED);
        } else {
            item.setColor(0x00000000);
            item.setCornerRadius(dp(15));
        }
        int rippleColor = softDark ? 0x557A6889 : 0x557F58A4;
        return new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(rippleColor), item, null);
    }
    private String iconFor(String s) {
        switch(s) { case "Главная": return "⌂"; case "Дела": return "✓"; case "Чат": return "✉"; case "Календарь": return "▦"; case "Хотим": return "♡"; case "Ещё": return "⋯"; case "Настройки": return "⚙"; case "Игры": return "🎮"; case "Просмотр": return "🎬"; case "Пригласить": return "🤝"; case "Подарочный радар": return "🎁"; case "Истории": return "📖"; case "Капсула": return "⏳"; case "Карты": return "💳"; case "Голосование": return "🗳"; case "Файлы": return "📎"; default: return "•••"; }
    }

    private void renderHome() {
        LinearLayout welcome = card(); welcome.setBackground(shape(PALE, 24));
        String ownName = prefs.getString("profile_name", "").trim();
        String partnerName = prefs.getString("partner_name", "").trim();
        String greeting = !ownName.isEmpty() && !partnerName.isEmpty()
                ? "Привет, " + ownName + " и " + partnerName + " 💛"
                : !ownName.isEmpty() ? "Привет, " + ownName + " 💛" : "Привет вам двоим 💛";
        addGap(welcome, 7); welcome.addView(text(greeting, 22, INK, true));
        addGap(welcome, 4); welcome.addView(text("Сегодня — хороший повод побыть рядом", 13, MUTED, false));
        addGap(welcome, 16);
        TextView question = pill("Вопрос дня  ·  Ответить друг другу", PURPLE);
        welcome.addView(question); question.setOnClickListener(v -> open("💭  Вопрос дня для двоих"));

        renderHomePairingShortcut();
        heading("На сегодня", "Ваши общие дела");
        LinearLayout taskCard = card();
        JSONArray tasks = read("tasks");
        int shown = 0;
        for (int i = 0; i < tasks.length() && shown < 3; i++) {
            JSONObject item = tasks.optJSONObject(i);
            if (item != null && !item.optBoolean("done")) { addTask(taskCard, tasks, i); shown++; }
        }
        if (shown == 0) {
            taskCard.addView(text("Все общие дела закрыты", 15, INK, true));
            TextView emptyHint = text("Добавьте новое дело во вкладке «Дела»", 13, MUTED, false);
            LinearLayout.LayoutParams emptyHintParams = new LinearLayout.LayoutParams(-1, -2);
            emptyHintParams.topMargin = dp(4);
            taskCard.addView(emptyHint, emptyHintParams);
        }
        TextView link = text("Все дела  →", 13, PURPLE, true); link.setPadding(0, dp(8), 0, 0);
        taskCard.addView(link); link.setOnClickListener(v -> { tab = "Дела"; render(); });

        heading("Скоро", "Чтобы ничего не забыть");
        JSONArray events = read("events");
        JSONObject nextEvent = null; long now = System.currentTimeMillis(), best = Long.MAX_VALUE;
        for (int i=0;i<events.length();i++) { JSONObject event=events.optJSONObject(i); if(event==null)continue; long at=event.optLong("reminder_at",0); if(at>=now&&at<best){best=at;nextEvent=event;} }
        if (nextEvent == null) simpleCard("🗓", "Ближайших событий нет", "Добавьте дату в календаре", () -> { tab = "Календарь"; render(); });
        else simpleCard("🗓", nextEvent.optString("title"), new SimpleDateFormat("d MMMM · HH:mm",new Locale("ru")).format(new java.util.Date(best)), () -> { tab = "Календарь"; render(); });

        heading("Выберите занятие", "Общайтесь, играйте или смотрите вместе");
        LinearLayout watchRoomCard = card();
        watchRoomCard.addView(text("🎬  Комната просмотра", 16, INK, true));
        boolean canUseWatchRoom = sync.ready();
        TextView watchRoomDescription = text(
                canUseWatchRoom
                        ? "Создайте комнату и отправьте партнёру QR-код, ссылку или код подключения."
                        : "Сначала войдите и подключите пару. После этого можно будет создать комнату или присоединиться к партнёру.",
                13, MUTED, false);
        LinearLayout.LayoutParams watchRoomDescriptionParams =
                new LinearLayout.LayoutParams(-1, -2);
        watchRoomDescriptionParams.topMargin = dp(4);
        watchRoomDescriptionParams.bottomMargin = dp(12);
        watchRoomCard.addView(watchRoomDescription, watchRoomDescriptionParams);
        TextView createWatchRoom = pill("Создать комнату", PURPLE);
        createWatchRoom.setMinHeight(dp(48));
        createWatchRoom.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        createWatchRoom.setEnabled(canUseWatchRoom);
        createWatchRoom.setAlpha(canUseWatchRoom ? 1f : 0.55f);
        createWatchRoom.setContentDescription("Создать новую комнату просмотра");
        watchRoomCard.addView(createWatchRoom);
        createWatchRoom.setOnClickListener(v -> prepareLiveRoomCreation());
        TextView joinWatchRoom = pill("Подключиться к комнате партнёра", PURPLE);
        joinWatchRoom.setMinHeight(dp(48));
        joinWatchRoom.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        joinWatchRoom.setEnabled(canUseWatchRoom);
        joinWatchRoom.setAlpha(canUseWatchRoom ? 1f : 0.55f);
        joinWatchRoom.setContentDescription("Подключиться к комнате партнёра");
        watchRoomCard.addView(joinWatchRoom);
        joinWatchRoom.setOnClickListener(v -> showWatchRoomJoinOptions());
        simpleCard("🎲", "Игры для двоих", "Вопросы и онлайн-игры с партнёром",
                () -> { tab = "Игры"; feature = null; render(); });
        simpleCard("🗳", "Выбрать, чем заняться", "Отдельное голосование за совместные занятия", () -> open("🗳 Голосование: чем заняться"));
        simpleCard("🎁", "Подарочный радар", "Сохраняйте идеи подарков", () -> open("🎁  Подарочный радар"));
    }

    private void renderHomePairingShortcut() {
        if (!sync.coupleId().isEmpty()) return;

        heading("Подключить партнёра", "Пара открывает общие функции и синхронизацию");
        if (!sync.configured()) {
            simpleCard("☁", "Настроить связь пары", "Сначала подключите Supabase.", this::configureServer);
            return;
        }
        if (!sync.signedIn()) {
            simpleCard("🤝", "Войти и подключить партнёра",
                    "После входа выберите сканирование QR-кода или ручной ввод приглашения.",
                    () -> { tab = "Пригласить"; render(); });
            return;
        }
        if (coupleRestorePending) {
            simpleCard("🤝", "Проверяем существующую пару",
                    "Подождите, пока проверка аккаунта завершится.", null);
            return;
        }
        if (coupleRestoreFailed) {
            simpleCard("!", "Не удалось проверить пару",
                    "Повторите проверку, прежде чем создавать новое приглашение.",
                    this::restoreCoupleBinding);
            return;
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        TextView scan = pill("⌗  Сканировать QR", PURPLE);
        TextView manual = pill("Ввести код", PURPLE);
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(0, -2, 1);
        scanParams.setMargins(0, 0, dp(6), 0);
        LinearLayout.LayoutParams manualParams = new LinearLayout.LayoutParams(0, -2, 1);
        manualParams.setMargins(dp(6), 0, 0, 0);
        scan.setLayoutParams(scanParams);
        manual.setLayoutParams(manualParams);
        actions.addView(scan);
        actions.addView(manual);
        body.addView(actions);
        scan.setOnClickListener(v -> startInviteScanner());
        manual.setOnClickListener(v -> joinCoupleDialog());
    }

    private void renderTasks() {
        heading("Общие дела", "Распределяйте заботы и отмечайте готовое");
        TextView add = pill("＋  Добавить общее дело", PURPLE); body.addView(add); add.setOnClickListener(v -> addDialog("tasks", "Новое дело"));
        JSONArray list = read("tasks");
        LinearLayout box = card();
        if (list.length() == 0) simpleCard("＋", "Пока дел нет", "Добавьте дело для вас двоих", null);
        else for (int i=0;i<list.length();i++) addTask(box, list, i);
        heading("Списки на двоих", "Покупки, поездки, домашние дела");
        renderListLink("🛒", "Покупки", "list_shopping");
        renderListLink("🧳", "В поездку", "list_trip");
        renderListLink("🏡", "Для дома", "list_home");
        TextView createList = pill("＋ Создать свой список", PURPLE); body.addView(createList);
        createList.setOnClickListener(v -> createSharedList());
        JSONArray customLists = read("list_catalog");
        for (int i=0;i<customLists.length();i++) { JSONObject entry=customLists.optJSONObject(i); if(entry!=null) renderCustomList(entry); }
    }

    private void renderListLink(String icon,String title,String key) {
        simpleCard(icon,title,"Открыть общий список · нажмите и удерживайте для управления",()->open("list:"+key));
        View last=body.getChildAt(body.getChildCount()-1);last.setOnLongClickListener(v->{listActions(key,title,null);return true;});
    }

    private void renderCustomList(JSONObject entry) {
        String key=entry.optString("list_key"),title=entry.optString("title");
        if(key.isEmpty()||title.isEmpty())return;
        simpleCard("📋",title,"Общий список · нажмите и удерживайте для управления",()->open("list:"+key));
        View last=body.getChildAt(body.getChildCount()-1);last.setOnLongClickListener(v->{listActions(key,title,entry);return true;});
    }

    private void createSharedList() {
        EditText title=new EditText(this);title.setSingleLine(true);title.setHint("Например, идеи для поездки");
        new AlertDialog.Builder(this).setTitle("Новый список для двоих").setView(title).setNegativeButton("Отмена",null)
                .setPositiveButton("Создать",(d,w)->{String value=title.getText().toString().trim();if(value.isEmpty())return;JSONObject row=new JSONObject();String id=UUID.randomUUID().toString();try{row.put("id",id);row.put("list_key","list_"+id);row.put("title",value);}catch(Exception ignored){}append("list_catalog",value,"");JSONArray rows=read("list_catalog");JSONObject saved=rows.optJSONObject(rows.length()-1);if(saved!=null)try{saved.put("list_key","list_"+id);save("list_catalog",rows);syncItem("list_catalog",saved);}catch(Exception ignored){}render();}).show();
    }

    private void listActions(String key,String title,JSONObject custom) {
        String[] choices=custom==null?new String[]{"Открыть"}:new String[]{"Открыть", "Переименовать", "Удалить список"};
        new AlertDialog.Builder(this).setTitle(title).setItems(choices,(d,which)->{
            if(which==0){open("list:"+key);return;}
            if(which==1){EditText input=new EditText(this);input.setSingleLine(true);input.setText(title);new AlertDialog.Builder(this).setTitle("Название списка").setView(input).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(x,y)->{if(custom==null){Toast.makeText(this,"Встроенный список нельзя переименовать",Toast.LENGTH_SHORT).show();return;}try{custom.put("title",input.getText().toString().trim());replaceItem("list_catalog",custom);render();}catch(Exception ignored){}}).show();return;}
            if(custom!=null)new AlertDialog.Builder(this).setMessage("Удалить список «"+title+"» вместе со всеми пунктами?").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(x,y)->{removeItem("list_catalog",custom.optString("id"));JSONArray items=read(key);for(int i=items.length()-1;i>=0;i--){JSONObject item=items.optJSONObject(i);if(item!=null)removeRemoteItem(item.optString("id"));}prefs.edit().remove(key).apply();render();}).show();
        }).show();
    }

    private void renderSharedList() {
        String key=feature.substring("list:".length());String title=listTitle(key);
        TextView back=text("‹  Назад к делам",14,PURPLE,true);body.addView(back);back.setOnClickListener(v->{feature=null;tab="Дела";render();});
        heading(title,"Общий список — изменения видны у вас обоих");
        TextView add=pill("＋ Добавить пункт",PURPLE);body.addView(add);add.setOnClickListener(v->addDialog(key,"Новый пункт"));
        JSONArray items=read(key);if(items.length()==0)simpleCard("＋","Список пока пуст","Добавьте первый пункт",null);
        else {LinearLayout box=card();for(int i=0;i<items.length();i++)addTask(box,items,i,key);}
    }

    private String listTitle(String key) {
        if("list_shopping".equals(key))return "Покупки";if("list_trip".equals(key))return "В поездку";if("list_home".equals(key))return "Для дома";
        JSONArray rows=read("list_catalog");for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&key.equals(row.optString("list_key")))return row.optString("title");}return "Общий список";
    }

    private void renderCalendar() {
        boolean personalCalendar=prefs.getBoolean("personal_calendar_view",false);
        String eventKey=personalCalendar?"personal_events":"events";
        String calendarName=personalCalendar?"Мой календарь":"Наш календарь";
        heading(calendarName,personalCalendar?"Личные дела и даты · видны только на этом устройстве":"Общие события и дела для вас двоих");
        LinearLayout calendarModes=new LinearLayout(this);
        calendarModes.setGravity(Gravity.CENTER);
        TextView sharedMode=pill("Наш календарь",personalCalendar?MUTED:PURPLE);
        TextView personalMode=pill("Мой календарь",personalCalendar?PURPLE:MUTED);
        sharedMode.setGravity(Gravity.CENTER);
        personalMode.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams modeParams=new LinearLayout.LayoutParams(0,-2,1);
        modeParams.setMargins(dp(3),0,dp(3),0);
        calendarModes.addView(sharedMode,modeParams);
        calendarModes.addView(personalMode,new LinearLayout.LayoutParams(0,-2,1));
        body.addView(calendarModes);
        sharedMode.setOnClickListener(v->{prefs.edit().putBoolean("personal_calendar_view",false).apply();render();});
        personalMode.setOnClickListener(v->{prefs.edit().putBoolean("personal_calendar_view",true).apply();render();});
        LinearLayout monthCard = card();
        LinearLayout monthBar = new LinearLayout(this); monthBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView prev = text("‹", 28, PURPLE, true); prev.setGravity(Gravity.CENTER);
        monthBar.addView(prev, new LinearLayout.LayoutParams(dp(48), dp(48)));
        String monthName = new SimpleDateFormat("LLLL yyyy", new Locale("ru")).format(visibleMonth.getTime());
        TextView month = text(monthName.substring(0,1).toUpperCase(new Locale("ru")) + monthName.substring(1), 17, INK, true);
        month.setGravity(Gravity.CENTER); monthBar.addView(month, new LinearLayout.LayoutParams(0, -2, 1));
        TextView next = text("›", 28, PURPLE, true); next.setGravity(Gravity.CENTER);
        monthBar.addView(next, new LinearLayout.LayoutParams(dp(48), dp(48))); monthCard.addView(monthBar);
        prev.setOnClickListener(v -> moveMonth(-1));
        next.setOnClickListener(v -> moveMonth(1));

        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        grid.setColumnCount(7); grid.setRowCount(7);
        grid.setUseDefaultMargins(false);
        grid.setAlignmentMode(android.widget.GridLayout.ALIGN_BOUNDS);
        String[] weekdays={"ПН","ВТ","СР","ЧТ","ПТ","СБ","ВС"};
        for(int i=0;i<weekdays.length;i++){
            TextView d=text(weekdays[i],10,MUTED,true); d.setGravity(Gravity.CENTER);
            android.widget.GridLayout.LayoutParams p=new android.widget.GridLayout.LayoutParams();
            p.width=0; p.height=dp(32);
            p.columnSpec=android.widget.GridLayout.spec(i,1,android.widget.GridLayout.FILL,1f);
            p.rowSpec=android.widget.GridLayout.spec(0,1,android.widget.GridLayout.FILL,0f);
            grid.addView(d,p);
        }
        Calendar first=(Calendar)visibleMonth.clone(); first.set(Calendar.DAY_OF_MONTH,1);
        int offset=(first.get(Calendar.DAY_OF_WEEK)+5)%7; first.add(Calendar.DAY_OF_MONTH,-offset);
        String selectedKey=dateKey(selectedDay);
        for(int i=0;i<42;i++){
            Calendar date=(Calendar)first.clone(); date.add(Calendar.DAY_OF_MONTH,i);
            String key=dateKey(date); boolean thisMonth=date.get(Calendar.MONTH)==visibleMonth.get(Calendar.MONTH);
            boolean isSelected=key.equals(selectedKey); boolean isToday=key.equals(dateKey(Calendar.getInstance()));
            String dayKey=(personalCalendar?"personal_day_":"day_")+key;
            boolean hasTasks=read(dayKey).length()>0 || hasCalendarEventOn(date,eventKey);
            TextView day=text(String.valueOf(date.get(Calendar.DAY_OF_MONTH)),14,
                    isSelected?PAPER:thisMonth?INK:0xFFB9B2BE,isSelected);
            day.setGravity(Gravity.CENTER); day.setIncludeFontPadding(false);
            day.setBackground(shape(isSelected?PURPLE:(hasTasks?PALE:BG),14));
            if(isToday&&!isSelected) day.setBackground(shape(0xFFE7D9F0,14));
            day.setContentDescription(new SimpleDateFormat("EEEE, d MMMM",new Locale("ru")).format(date.getTime())+(hasTasks?", есть записи":""));
            android.widget.GridLayout.LayoutParams p=new android.widget.GridLayout.LayoutParams();
            p.width=0; p.height=dp(46);
            p.columnSpec=android.widget.GridLayout.spec(i%7,1,android.widget.GridLayout.FILL,1f);
            p.rowSpec=android.widget.GridLayout.spec(1+i/7,1,android.widget.GridLayout.FILL,0f);
            p.setMargins(dp(2),dp(2),dp(2),dp(2)); grid.addView(day,p);
            day.setOnClickListener(v->{selectedDay.setTime(date.getTime());visibleMonth.set(Calendar.YEAR,date.get(Calendar.YEAR));visibleMonth.set(Calendar.MONTH,date.get(Calendar.MONTH));render();});
        }
        monthCard.addView(grid,new LinearLayout.LayoutParams(-1,-2));
        String humanDate=new SimpleDateFormat("EEEE, d MMMM",new Locale("ru")).format(selectedDay.getTime());
        heading(humanDate.substring(0,1).toUpperCase(new Locale("ru"))+humanDate.substring(1),personalCalendar?"Личные планы на выбранную дату":"Общие планы на выбранную дату");
        String key=(personalCalendar?"personal_day_":"day_")+selectedKey;
        TextView add=pill("＋  Добавить дело на эту дату",PURPLE);body.addView(add);
        add.setOnClickListener(v->addDialog(key,"Дело на "+new SimpleDateFormat("d MMMM",new Locale("ru")).format(selectedDay.getTime())));
        JSONArray dayTasks=read(key);
        if(dayTasks.length()==0) simpleCard("＋","Пока свободно","Запланируйте что-нибудь вдвоём",null);
        else { LinearLayout list=card(); for(int i=0;i<dayTasks.length();i++)addTask(list,dayTasks,i,key); }
        heading(personalCalendar?"Мои события":"Наши события",null);
        TextView addEvent=pill("＋  Добавить событие с датой и временем",PURPLE);body.addView(addEvent);
        addEvent.setOnClickListener(v->addCalendarEvent(eventKey));
        JSONArray events=read(eventKey);
        if(events.length()==0)simpleCard("♡",personalCalendar?"Личное событие":"Общее событие","Выберите дату и точное время",()->addCalendarEvent(eventKey));
        else for(int i=0;i<events.length();i++){
            JSONObject event=events.optJSONObject(i);if(event==null)continue;
            LinearLayout entry=card();
            entry.addView(text(event.optString("title"),15,INK,true));
            long at=event.optLong("reminder_at",0);
            if(at>0)entry.addView(text(new SimpleDateFormat("d MMMM · HH:mm",new Locale("ru")).format(new java.util.Date(at)),12,PURPLE,true));
            if(!event.optString("detail").isEmpty())entry.addView(text(event.optString("detail"),13,MUTED,false));
            entry.setOnClickListener(v->editItemDialog(eventKey,event));
            entry.setOnLongClickListener(v->{itemActions(eventKey,event);return true;});
        }
    }

    private boolean hasCalendarEventOn(Calendar date,String eventKey) {
        JSONArray events=read(eventKey);
        String key=dateKey(date);
        for(int i=0;i<events.length();i++) {
            JSONObject event=events.optJSONObject(i);
            if(event==null) continue;
            long at=event.optLong("reminder_at",0);
            if(at>0) {
                Calendar eventDate=Calendar.getInstance(); eventDate.setTimeInMillis(at);
                if(key.equals(dateKey(eventDate))) return true;
            }
        }
        return false;
    }

    private void addCalendarEvent(String eventKey){
        Calendar when=(Calendar)selectedDay.clone();
        new android.app.DatePickerDialog(this,(picker,year,month,day)->{
            when.set(Calendar.YEAR,year);when.set(Calendar.MONTH,month);when.set(Calendar.DAY_OF_MONTH,day);
            new android.app.TimePickerDialog(this,(time,hour,minute)->{
                when.set(Calendar.HOUR_OF_DAY,hour);when.set(Calendar.MINUTE,minute);
                when.set(Calendar.SECOND,0);when.set(Calendar.MILLISECOND,0);
                LinearLayout form=column();form.setPadding(dp(18),dp(4),dp(18),dp(4));
                EditText title=new EditText(this);title.setSingleLine(true);title.setHint("Название события");form.addView(title);
                EditText detail=new EditText(this);detail.setMinLines(2);detail.setHint("Заметка (необязательно)");form.addView(detail);
                TextView selectedTime=text(new SimpleDateFormat("EEEE, d MMMM · HH:mm",new Locale("ru")).format(when.getTime()),13,PURPLE,true);
                selectedTime.setPadding(0,dp(8),0,dp(8));form.addView(selectedTime);
                AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Новое событие")
                        .setView(form).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
                dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
                    String name=title.getText().toString().trim();
                    if(name.isEmpty()){title.setError("Введите название события");return;}
                    try{
                        JSONObject event=new JSONObject();event.put("id",UUID.randomUUID().toString());
                        event.put("title",name);event.put("detail",detail.getText().toString().trim());
                        event.put("reminder_at",when.getTimeInMillis());event.put("created_at",System.currentTimeMillis());
                        JSONArray rows=read(eventKey);rows.put(event);save(eventKey,rows);
                        syncItem(eventKey,event);dialog.dismiss();render();
                    }catch(Exception e){toast("Не удалось сохранить событие: "+e.getMessage());}
                }));
                dialog.show();
            },9,0,true).show();
        },when.get(Calendar.YEAR),when.get(Calendar.MONTH),when.get(Calendar.DAY_OF_MONTH)).show();
    }

    private String dateKey(Calendar date){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(date.getTime());}
    private void moveMonth(int delta){
        visibleMonth.add(Calendar.MONTH,delta); selectedDay.set(Calendar.YEAR,visibleMonth.get(Calendar.YEAR));
        selectedDay.set(Calendar.MONTH,visibleMonth.get(Calendar.MONTH));
        selectedDay.set(Calendar.DAY_OF_MONTH,Math.min(selectedDay.get(Calendar.DAY_OF_MONTH),visibleMonth.getActualMaximum(Calendar.DAY_OF_MONTH)));
        render();
    }

    private void renderCollection(String title, String subtitle, String key, String action) {
        heading(title, subtitle);
        TextView add = pill("＋  " + action, PURPLE); body.addView(add);
        add.setOnClickListener(v -> {
            if("wishes".equals(key))editWishPlanDialog(null);
            else addDialog(key, action);
        });
        JSONArray list = read(key);
        if (list.length() == 0) {
            simpleCard("＋", "Пока записей нет", "Добавьте вашу первую общую запись", null);
        } else for (int i=0;i<list.length();i++) {
            JSONObject item = list.optJSONObject(i); if (item == null) continue;
            if("wishes".equals(key))renderWishPlan(item);
            else renderEditableEntry(key, list, i, "🗓");
        }
        if (key.equals("wishes")) simpleCard("🗳", "Голосование за хотелки", "Выберите мечту, которую исполните вместе", () -> open("🗳 Голосование за хотелки"));
    }

    private void renderWishPlan(JSONObject item){
        LinearLayout plan=card();
        plan.addView(text("✨  "+item.optString("title"),16,INK,true));
        String category=item.optString("category","");if(!category.isEmpty())plan.addView(text(category,12,PURPLE,true));
        String detail=item.optString("detail","");if(!detail.isEmpty())plan.addView(text(detail,13,MUTED,false));
        JSONArray steps=item.optJSONArray("plan_steps");
        if(steps!=null&&steps.length()>0){
            int done=0;for(int i=0;i<steps.length();i++){JSONObject step=steps.optJSONObject(i);if(step!=null&&step.optBoolean("done"))done++;}
            int progress=PlanProgress.percent(done,steps.length());
            TextView progressLabel=text("Шаги · "+done+" из "+steps.length()+" · "+progress+"%",12,PURPLE,true);
            progressLabel.setPadding(0,dp(8),0,dp(2));plan.addView(progressLabel);
            android.widget.ProgressBar bar=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);bar.setProgress(progress);
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(PURPLE));
            LinearLayout.LayoutParams barParams=new LinearLayout.LayoutParams(-1,dp(8));barParams.bottomMargin=dp(6);
            plan.addView(bar,barParams);
            for(int i=0;i<steps.length();i++){
                JSONObject step=steps.optJSONObject(i);if(step==null)continue;
                android.widget.CheckBox check=new android.widget.CheckBox(this);check.setText(step.optString("title"));
                check.setChecked(step.optBoolean("done"));
                check.setButtonTintList(android.content.res.ColorStateList.valueOf(PURPLE));
                plan.addView(check);
                check.setOnCheckedChangeListener((button,checked)->{
                    try{step.put("done",checked);item.put("plan_steps",steps);replaceItem("wishes",item);render();}
                    catch(Exception e){toast("Не удалось обновить шаг плана: "+e.getMessage());}
                });
            }
        }else plan.addView(text("Добавьте шаги, чтобы отслеживать продвижение",12,MUTED,false));
        plan.setOnClickListener(v->editWishPlanDialog(item));
        plan.setOnLongClickListener(v->{itemActions("wishes",item);return true;});
    }

    private void editWishPlanDialog(JSONObject existing){
        LinearLayout form=column();form.setPadding(dp(18),dp(4),dp(18),dp(4));
        EditText title=new EditText(this);title.setSingleLine(true);title.setHint("Мечта или план");
        if(existing!=null)title.setText(existing.optString("title"));form.addView(title);
        EditText category=new EditText(this);category.setSingleLine(true);category.setHint("Раздел / категория");
        if(existing!=null)category.setText(existing.optString("category"));form.addView(category);
        EditText detail=new EditText(this);detail.setMinLines(2);detail.setHint("Описание или заметка");
        if(existing!=null)detail.setText(existing.optString("detail"));form.addView(detail);
        EditText stepsInput=new EditText(this);stepsInput.setGravity(Gravity.TOP|Gravity.START);stepsInput.setMinLines(4);
        stepsInput.setHint("Шаги плана — каждый с новой строки");
        JSONArray oldSteps=existing==null?null:existing.optJSONArray("plan_steps");
        if(oldSteps!=null){StringBuilder textValue=new StringBuilder();for(int i=0;i<oldSteps.length();i++){JSONObject step=oldSteps.optJSONObject(i);if(step!=null){if(textValue.length()>0)textValue.append('\n');textValue.append(step.optString("title"));}}stepsInput.setText(textValue.toString());}
        form.addView(stepsInput);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(existing==null?"Новый план":"Изменить план")
                .setView(scroll).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
            String name=title.getText().toString().trim();
            if(name.isEmpty()){title.setError("Введите мечту или план");return;}
            try{
                JSONObject plan=existing==null?new JSONObject():new JSONObject(existing.toString());
                if(!plan.has("id"))plan.put("id",UUID.randomUUID().toString());
                plan.put("title",name);plan.put("category",category.getText().toString().trim());
                plan.put("detail",detail.getText().toString().trim());
                JSONArray parsed=new JSONArray();String[] lines=stepsInput.getText().toString().split("\\r?\\n");
                for(int i=0;i<lines.length;i++){
                    String stepTitle=lines[i].trim();if(stepTitle.isEmpty())continue;
                    JSONObject old=oldSteps!=null&&i<oldSteps.length()?oldSteps.optJSONObject(i):null;
                    JSONObject step=new JSONObject();step.put("title",stepTitle);
                    step.put("done",old!=null&&stepTitle.equals(old.optString("title"))&&old.optBoolean("done"));
                    parsed.put(step);
                }
                plan.put("plan_steps",parsed);
                if(existing==null){JSONArray rows=read("wishes");rows.put(plan);save("wishes",rows);syncItem("wishes",plan);}
                else replaceItem("wishes",plan);
                dialog.dismiss();render();
            }catch(Exception e){toast("Не удалось сохранить план: "+e.getMessage());}
        }));
        dialog.show();
    }

    private void renderMore() {
        heading("Все разделы", "Общение, планы и занятия для вас двоих");
        for (String[] group : MORE_GROUPS) {
            heading(group[0], null);
            for (int i = 1; i < group.length; i++) {
                String item = group[i];
                String icon = item.substring(0, 2);
                String title = item.contains("Карта партнёра")
                        ? "Карта " + prefs.getString("partner_name", "партнёра")
                        : item.substring(4);
                simpleCard(icon, title, description(item), () -> open(item));
            }
        }
        heading("Инструменты", null);
        simpleCard("⏱", "Общий таймер", "Запустите отсчёт и следите за ним вместе", () -> open("shared_timer"));
        simpleCard("🔔", "Напомнить партнёру", "Общая дата и уведомление на телефонах пары", () -> open("shared_reminder"));
        heading("Приложение", null);
        simpleCard("⚙", "Настройки", "Профиль, внешний вид, подключение и обновления", () -> {
            feature=null;
            tab=NavigationModel.SETTINGS_TAB;
            render();
        });
    }
    private void renderSettingsTab(){
        heading("Настройки","Профиль и параметры приложения собраны здесь");

        LinearLayout profile=settingsGroup("Профиль");
        String name=prefs.getString("profile_name","").trim();
        String emoji=prefs.getString("profile_avatar","♡");
        addSettingRow(profile,emoji,
                name.isEmpty()?"Заполнить профиль":name,
                "Имя, фото, описание и эмодзи для партнёра",
                this::profileDialog);

        LinearLayout appearance=settingsGroup("Внешний вид и навигация");
        addSettingRow(appearance,"◐",softDark?"Светлая тема":"Тёмная тема",
                "Переключить оформление приложения",()->{
                    softDark=!softDark;
                    prefs.edit().putBoolean("soft_dark_theme",softDark).apply();
                    applyPalette();
                    render();
                });
        addSettingRow(appearance,"☷","Разделы на панели",
                "Выбрать основные вкладки и их порядок",this::navigationSettings);

        LinearLayout connection=settingsGroup("Подключение");
        addSettingRow(connection,"☁","Supabase",
                sync.url().isEmpty()?"Настроить сервер синхронизации":"Параметры сервера синхронизации",
                this::configureServer);
        addSettingRow(connection,"♡","Аккаунт и пара",
                sync.signedIn()?"Войти в другой аккаунт или зарегистрироваться":"Войти или создать аккаунт",
                this::accountDialog);

        LinearLayout updates=settingsGroup("Обновления");
        addSettingRow(updates,"↻","Проверить обновления",
                "Проверить доступную версию приложения на GitHub",
                ()->checkGitHubUpdate(true));

        LinearLayout account=settingsGroup("Аккаунт");
        addSettingRow(account,"!", "Удалить аккаунт…",
                "Удаление необратимо; сначала будет показано подтверждение",
                this::confirmAccountDeletion);
    }
    private LinearLayout settingsGroup(String title){
        heading(title,null);
        LinearLayout group=card();
        group.setPadding(dp(8),dp(4),dp(8),dp(4));
        return group;
    }
    private void addSettingRow(LinearLayout group,String icon,String title,String summary,Runnable action){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8),dp(6),dp(8),dp(6));
        row.setMinimumHeight(dp(64));
        row.setClickable(true);
        row.setFocusable(true);
        android.util.TypedValue background=new android.util.TypedValue();
        if(getTheme().resolveAttribute(android.R.attr.selectableItemBackground,background,true))
            row.setBackgroundResource(background.resourceId);
        TextView glyph=text(icon,20,PURPLE,true);
        glyph.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams glyphParams=new LinearLayout.LayoutParams(dp(40),dp(48));
        glyphParams.rightMargin=dp(10);
        row.addView(glyph,glyphParams);
        LinearLayout labels=column();
        labels.addView(text(title,15,INK,true));
        TextView detail=text(summary,12,MUTED,false);
        detail.setMaxLines(2);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(detail);
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=text("›",22,MUTED,false);
        arrow.setGravity(Gravity.CENTER);
        arrow.setMinWidth(dp(32));
        row.addView(arrow,new LinearLayout.LayoutParams(dp(32),dp(48)));
        row.setContentDescription(title+". "+summary);
        row.setOnClickListener(v->action.run());
        group.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }
    private void renderGamesTab(){
        renderGameRating();
        gameCard("Крестики-нолики", "Сетевая партия с партнёром на другом устройстве", this::onlineTicTacToe);
        gameCard("Морской бой", "Каждый расставляет свой флот на своём телефоне", this::onlineSeaBattle);
        gameCard("Дурак", "Карточная партия с партнёром на его телефоне", this::onlineDurak);
        gameCard("Шашки", "Классическая игра для двоих · каждый ход синхронизируется", this::onlineCheckers);
        gameCard("Четыре в ряд", "Бросайте фишки в колонку и соберите четыре подряд", this::onlineConnectFour);
        gameCard("Точки и квадратики", "Соединяйте точки и замыкайте квадраты", this::onlineDotsAndBoxes);
        gameCard("Вопрос для двоих", "Отвечайте на один и тот же вопрос и сравнивайте ответы", () -> onlineCoupleQuiz("love","Что вам больше всего нравится в ваших отношениях?"));
        gameCard("Угадай мелодию", "Напойте мелодию, пусть партнёр угадает", this::onlineGuessTune);
    }
    private void renderMovieRoomTab(){
        renderMovieRoom();
    }
    private void renderInvitePartnerTab(){
        renderCoupleSetup();
    }
    private void renderGiftRadarTab(){
        renderGiftRadar();
    }
    private void renderStoriesTab(){
        renderStories();
    }
    private void renderTimeCapsuleTab(){
        renderTimeCapsules();
    }
    private void renderLoyaltyCardsTab(){
        renderLoyaltyCards();
    }
    private void renderVotingTab(){
        renderVoting("votes_activities","Чем заняться","Выберите занятие для ближайшего совместного времени");
    }
    private void renderFilesTab(){
        simpleCard("📎", "Общие файлы", "Передавайте документы, фото и APK партнёру. APK хранится как обычный файл и никогда не запускается автоматически.", null);
        TextView attach=pill("＋  Выбрать файл",PURPLE);body.addView(attach);
        attach.setOnClickListener(v->{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("*/*");pick.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(pick,402);});
        JSONArray rows=read("files");
        for(int i=rows.length()-1;i>=0;i--){
            JSONObject item=rows.optJSONObject(i);if(item==null)continue;
            LinearLayout entry=card();
            String mime=item.optString("mime","application/octet-stream");
            if(mime.isEmpty())mime="application/octet-stream";
            boolean apk=mime.equals("application/vnd.android.package-archive")
                    ||item.optString("title").toLowerCase(Locale.ROOT).endsWith(".apk");
            entry.addView(text((apk?"📦  ":"📎  ")+item.optString("title","Файл"),16,INK,true));
            String detail=item.optString("detail","");
            if(!detail.isEmpty())entry.addView(text(detail,12,MUTED,false));
            entry.addView(text(apk?"APK · недоверенный файл · не запускается здесь"
                    :"Общий файл · "+mime,11,PURPLE,true));
            TextView saveCopy=pill("Сохранить копию",PURPLE);saveCopy.setMinHeight(dp(48));entry.addView(saveCopy);
            saveCopy.setOnClickListener(v->requestFileDownload(item));
            entry.setOnClickListener(v->openSharedFilePreview(item));
            if(mime.startsWith("image/")&&!item.optString("path").isEmpty()){
                ImageView preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                preview.setBackground(shape(PALE,14));preview.setContentDescription("Предпросмотр "+item.optString("title"));
                LinearLayout.LayoutParams previewParams=new LinearLayout.LayoutParams(-1,dp(180));
                previewParams.topMargin=dp(8);entry.addView(preview,previewParams);
                loadSharedImagePreview(item.optString("path"),preview);
            }
            entry.setOnLongClickListener(v->{itemActions("files",item);return true;});
        }
        if(rows.length()==0)simpleCard("🗂","Пока нет файлов","Выберите файл, чтобы передать его в защищённое хранилище пары.",null);
    }

    private void loadSharedImagePreview(String path,ImageView target){
        if(!sync.ready())return;
        network.execute(()->{try{
            byte[] bytes=sync.downloadAttachment(path);
            android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();
            options.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
            int sample=1;
            while(options.outWidth/sample>900||options.outHeight/sample>900)sample*=2;
            options.inJustDecodeBounds=false;options.inSampleSize=sample;
            android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
            if(bitmap==null)throw new Exception("Формат изображения не поддерживается");
            runOnUiThread(()->{if(target.getParent()!=null)target.setImageBitmap(bitmap);});
        }catch(Exception e){android.util.Log.w("VmesteFiles","Could not load shared image preview",e);}});
    }

    private void openSharedFilePreview(JSONObject item){
        String path=item.optString("path","");
        String mime=item.optString("mime","application/octet-stream");
        String title=item.optString("title","Файл");
        if(path.isEmpty()){toast("Файл ещё не загружен в общее хранилище");return;}
        if(mime.startsWith("image/")){
            toast("Загружаю предпросмотр…");
            network.execute(()->{try{
                byte[] bytes=sync.downloadAttachment(path);
                android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();
                options.inJustDecodeBounds=true;
                android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                int sample=1;
                while(options.outWidth/sample>1600||options.outHeight/sample>1600)sample*=2;
                options.inJustDecodeBounds=false;options.inSampleSize=sample;
                android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(bitmap==null)throw new Exception("Формат изображения не поддерживается");
                runOnUiThread(()->{ImageView image=new ImageView(this);image.setImageBitmap(bitmap);
                    image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    new AlertDialog.Builder(this).setTitle(title).setView(image)
                            .setPositiveButton("Закрыть",null).show();});
            }catch(Exception e){runOnUiThread(()->toast("Не удалось открыть предпросмотр: "+e.getMessage()));}});
            return;
        }
        if(mime.startsWith("text/")||mime.equals("application/json")||mime.equals("application/xml")){
            network.execute(()->{try{
                byte[] bytes=sync.downloadAttachment(path);
                int maxPreview=256*1024;
                String content=new String(bytes,0,Math.min(bytes.length,maxPreview),
                        java.nio.charset.StandardCharsets.UTF_8);
                runOnUiThread(()->{TextView preview=text(content,14,INK,false);
                    preview.setTextIsSelectable(true);
                    ScrollView scroll=new ScrollView(this);scroll.setPadding(dp(16),dp(12),dp(16),dp(12));
                    scroll.addView(preview);
                    new AlertDialog.Builder(this).setTitle(title).setView(scroll)
                            .setPositiveButton("Закрыть",null).show();});
            }catch(Exception e){runOnUiThread(()->toast("Не удалось открыть предпросмотр: "+e.getMessage()));}});
            return;
        }
        boolean apk=mime.equals("application/vnd.android.package-archive")
                ||title.toLowerCase(Locale.ROOT).endsWith(".apk");
        String message=(apk?"APK · недоверенный установочный файл. Он не будет запущен или установлен автоматически.\n\n":"")
                +item.optString("detail","Формат: "+mime)
                +"\n\nДля просмотра или передачи сохраните копию на устройство.";
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setNegativeButton("Закрыть",null)
                .setPositiveButton("Сохранить копию",(d,w)->requestFileDownload(item)).show();
    }

    private void requestFileDownload(JSONObject item){
        String path=item.optString("path","");
        if(path.isEmpty()){toast("У этого файла нет загруженной копии");return;}
        pendingFileDownloadPath=path;
        String title=item.optString("title","vmeste-file");
        pendingFileDownloadName=safeFileName(title);
        String mime=item.optString("mime","application/octet-stream");
        if(mime.equals("application/vnd.android.package-archive")
                ||title.toLowerCase(Locale.ROOT).endsWith(".apk")){
            mime="application/vnd.android.package-archive";
            final String apkMime=mime;
            new AlertDialog.Builder(this).setTitle("Сохранить APK")
                    .setMessage("APK будет только сохранён как недоверенный файл. Приложение не запускает и не устанавливает его автоматически.")
                    .setNegativeButton("Отмена",(d,w)->clearPendingFileDownload())
                    .setPositiveButton("Продолжить",(d,w)->startFileSavePicker(apkMime)).show();
        }else startFileSavePicker(mime);
    }

    private void startFileSavePicker(String mime){
        Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        save.addCategory(Intent.CATEGORY_OPENABLE);save.setType(mime);
        save.putExtra(Intent.EXTRA_TITLE,pendingFileDownloadName);
        try{startActivityForResult(save,409);}
        catch(Exception e){clearPendingFileDownload();toast("Не удалось открыть выбор места сохранения: "+e.getMessage());}
    }

    private String safeFileName(String value){
        String name=value==null?"":value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").trim();
        if(name.isEmpty()||".".equals(name)||"..".equals(name))return "vmeste-file";
        return name.length()>120?name.substring(name.length()-120):name;
    }

    private void clearPendingFileDownload(){pendingFileDownloadPath="";pendingFileDownloadName="";}

    private void saveSharedFileCopy(Uri destination){
        String path=pendingFileDownloadPath;
        clearPendingFileDownload();
        if(path.isEmpty()||destination==null){toast("Выбор сохранения отменён");return;}
        toast("Скачиваю файл…");
        network.execute(()->{try{
            byte[] bytes=sync.downloadAttachment(path);
            try(OutputStream out=getContentResolver().openOutputStream(destination,"wt")){
                if(out==null)throw new Exception("Не удалось открыть файл для записи");
                out.write(bytes);out.flush();
            }
            runOnUiThread(()->toast("Файл сохранён. Для APK установка остаётся ручным действием Android."));
        }catch(Exception e){runOnUiThread(()->toast("Не удалось сохранить файл: "+e.getMessage()));}});
    }
    private String description(String title) {
        if (title.contains("Чат")) return "Сообщения, которые остаются между вами";
        if (title.contains("Заметки")) return "Мысли, рецепты и маленькие напоминания";
        if (title.contains("Рецепты")) return "Ингредиенты, граммовки и шаги приготовления";
        if (title.contains("Файлы")) return "Билеты, фотографии и важные документы";
        if (title.contains("Карты")) return "Сохраняйте штрихкоды карт лояльности";
        if (title.contains("Подар")) return "Идеи подарков и даты-сюрпризы";
        if (title.contains("Капсула")) return "Послание, которое откроете в будущем";
        if (title.contains("Голос")) return "Решайте вместе, что делать дальше";
        if (title.contains("игры")) return "Небольшие игры для двоих";
        if (title.contains("просмотра")) return "Настройте совместный кино-вечер";
        return "Мечты и планы, которые вы строите вместе";
    }

    private void renderFeature() {
        TextView back = text("‹  Назад", 14, PURPLE, true); back.setPadding(0, dp(5), 0, dp(14)); body.addView(back);
        back.setOnClickListener(v -> { feature = null; render(); });
        if (feature.contains("Карты")) {
            renderLoyaltyCards();
            return;
        }
        if (feature.contains("Календарь")) {
            renderCalendar();
            return;
        }
        if (feature.contains("Хотим вместе")) {
            renderCollection("Хочется вместе", "Маленькие и большие мечты",
                    "wishes", "Добавить хотелку");
            return;
        }
        heading(feature, description(feature));
        if ("shared_timer".equals(feature)) {
            renderSharedTimer();
        } else if ("shared_reminder".equals(feature)) {
            renderSharedReminder();
        } else if (feature.contains("Голосование")) {
            boolean wishesVote = feature.contains("хотелки");
            renderVoting(wishesVote ? "votes_wishes" : "votes_activities",
                    wishesVote ? "Голосование за хотелки" : "Чем заняться",
                    wishesVote ? "Выберите мечту, которую исполните вместе" : "Выберите занятие для ближайшего совместного времени");
        } else if (feature.contains("Вопрос дня")) {
            simpleCard("💭", "Что из наших общих воспоминаний хочется пережить ещё раз?", "Вопрос для вас обоих", null);
            TextView answer = pill("Написать ответ", PURPLE); body.addView(answer); answer.setOnClickListener(v -> addDialog("answers", "Ваш ответ"));
            renderEntries("answers");
        } else if (feature.contains("Пригласить партнёра")) {
            renderCoupleSetup();
        } else if (feature.contains("Подарочный радар")) {
            renderGiftRadar();
        } else if (feature.contains("Истории для двоих")) {
            renderStories();
        } else if (feature.contains("Капсула времени")) {
            renderTimeCapsules();
        } else if (feature.contains("Карта партнёра")) {
            renderPartnerCard();
        } else if (feature.contains("Заметки")) {
            renderNotes();
        } else if (feature.contains("Рецепты")) {
            renderRecipes();
        } else if (feature.contains("просмотра")) {
            renderMovieRoom();
        } else if (feature.contains("игры")) {
            try{renderGameRating();}catch(Exception ignored){}
            try{gameCard("Крестики-нолики", "Сетевая партия с партнёром на другом устройстве", this::onlineTicTacToe);}catch(Exception ignored){}
            try{gameCard("Морской бой", "Каждый расставляет свой флот на своём телефоне", this::onlineSeaBattle);}catch(Exception ignored){}
            try{gameCard("Дурак", "Карточная партия с партнёром на его телефоне", this::onlineDurak);}catch(Exception ignored){}
            try{gameCard("Шашки", "Классическая игра для двоих · каждый ход синхронизируется", this::onlineCheckers);}catch(Exception ignored){}
            try{gameCard("Четыре в ряд", "Бросайте фишки в колонку и соберите четыре подряд", this::onlineConnectFour);}catch(Exception ignored){}
            try{gameCard("Точки и квадратики", "Соединяйте точки и замыкайте квадраты", this::onlineDotsAndBoxes);}catch(Exception ignored){}
        } else if (feature.contains("Файлы")) {
            renderFilesTab();
        } else if (feature.contains("Чат")) {
            renderChat();
        } else {
            if (feature.contains("Капсула")) simpleCard("⏳", "Послание для будущих вас", "Добавьте текст — откройте его в особенный день.", null);
            TextView add = pill(feature.contains("Капсула") ? "＋  Написать послание" : feature.contains("Голосование") ? "＋  Предложить вариант" : "＋  Добавить", PURPLE);
            body.addView(add); add.setOnClickListener(v -> addDialog(keyFor(feature), feature.contains("Капсула") ? "Послание в будущее" : feature.contains("Голосование") ? "Вариант для голосования" : feature));
            renderEntries(keyFor(feature));
        }

    }

    private void renderLoyaltyCards() {
            heading("Карты лояльности", "Сканируйте штрихкод или QR либо введите номер вручную.");
            TextView privacy = text("Камера считывает только код и не сохраняет фото. Поддерживаются EAN/UPC, Code 128/39, ITF/Codabar, QR и Data Matrix; незнакомый формат можно ввести вручную. Данные синхронизируются с подключённой парой. Не сканируйте платёжные карты и не вводите CVV.", 13, MUTED, false);
            privacy.setPadding(0, 0, 0, dp(12));
            body.addView(privacy);
            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            TextView scan = pill("⌗  Сканировать", PURPLE);
            TextView manual = pill("＋  Ввести номер", PURPLE);
            scan.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            manual.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            actions.addView(scan);
            actions.addView(manual);
            body.addView(actions);
            scan.setOnClickListener(v -> startLoyaltyScan());
            manual.setOnClickListener(v -> showManualLoyaltyCardDialog());

            JSONArray cards = read("loyalty");
            if (cards.length() == 0) {
                TextView empty = text("Добавьте первую карту — тогда любой из вас сможет открыть её и показать код на кассе.", 14, MUTED, false);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(dp(18), dp(24), dp(18), dp(24));
                body.addView(empty);
                return;
            }
            for (int i = cards.length() - 1; i >= 0; i--) {
                JSONObject item = cards.optJSONObject(i);
                if (item != null) renderLoyaltyCard(item);
            }
        }

        private void renderLoyaltyCard(JSONObject item) {
            String code = item.optString("detail", "");
            String format = loyaltyFormat(item);
            LinearLayout pass = card();
            pass.setBackground(shape(softDark ? 0xFF39333E : 0xFFFFFFFF, 20));
            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            pass.addView(top);
            LinearLayout details = column();
            details.addView(text(item.optString("title", "Карта лояльности"), 17, INK, true));
            TextView kind = text(loyaltyFormatLabel(format), 11, MUTED, false);
            LinearLayout.LayoutParams kindParams = new LinearLayout.LayoutParams(-1, -2);
            kindParams.topMargin = dp(3);
            details.addView(kind, kindParams);
            top.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
            TextView show = text("Показать  ›", 13, PURPLE, true);
            top.addView(show);
            try {
                ImageView preview = new ImageView(this);
                preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                preview.setBackground(shape(0xFFFFFFFF, 10));
                preview.setPadding(dp(8), dp(6), dp(8), dp(6));
                preview.setContentDescription("Предпросмотр кода карты " + item.optString("title"));
                preview.setImageBitmap(loyaltyBarcodeBitmap(code, format, dp(340), dp(90)));
                LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, dp(78));
                previewParams.topMargin = dp(12);
                pass.addView(preview, previewParams);
            } catch (Exception e) {
                TextView unavailable = text("Код не удалось построить · нажмите, чтобы исправить", 12, MUTED, false);
                unavailable.setPadding(0, dp(10), 0, 0);
                pass.addView(unavailable);
            }
            pass.setOnClickListener(v -> showLoyaltyPass(item));
            pass.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setItems(new String[]{"Редактировать карту", "Удалить карту"}, (dialog, which) -> {
                    if (which == 0) showLoyaltyEditor(item);
                    else new AlertDialog.Builder(this).setMessage("Удалить карту «" + item.optString("title") + "» у вас обоих?")
                            .setNegativeButton("Отмена", null)
                            .setPositiveButton("Удалить", (confirm, choice) -> {
                                removeItem("loyalty", item.optString("id"));
                                render();
                            }).show();
                }).show();
                return true;
            });
        }

        private String loyaltyFormat(JSONObject item) {
            String format = item.optString("barcode_format", "");
            return LoyaltyBarcode.supports(format) ? format : BarcodeFormat.CODE_128.name();
        }

        private String loyaltyFormatLabel(String format) {
            if (BarcodeFormat.QR_CODE.name().equals(format)) return "QR-код";
            if (BarcodeFormat.DATA_MATRIX.name().equals(format)) return "Data Matrix";
            return "Штрихкод · " + format.replace('_', ' ');
        }

        private void startLoyaltyScan() {
            pendingLoyaltyScan = true;
            ArrayList<String> formats = new ArrayList<>();
            for (BarcodeFormat format : LoyaltyBarcode.formats()) formats.add(format.name());
            try {
                new IntentIntegrator(this)
                        .setDesiredBarcodeFormats(formats)
                        .setPrompt("Только код карты лояльности — не сканируйте банковские карты")
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                        .initiateScan();
            } catch (RuntimeException e) {
                pendingLoyaltyScan = false;
                toast("Не удалось открыть сканер: " + e.getMessage());
            }
        }

        private void showManualLoyaltyCardDialog() {
            showLoyaltyEditor(null);
        }

        private void showLoyaltyEditor(JSONObject editing) {
            LinearLayout form = column();
            form.setPadding(dp(22), dp(8), dp(22), dp(4));
            EditText title = new EditText(this);
            title.setSingleLine(true);
            title.setHint("Название программы, например «Пеликан»");
            if (editing != null) title.setText(editing.optString("title", ""));
            form.addView(title);
            EditText code = new EditText(this);
            code.setSingleLine(true);
            code.setHint("Номер или содержимое штрихкода");
            code.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            if (editing != null) code.setText(editing.optString("detail", ""));
            form.addView(code);
            String[] labels = new String[LOYALTY_FORMATS.length];
            for (int i = 0; i < LOYALTY_FORMATS.length; i++) labels[i] = loyaltyFormatLabel(LOYALTY_FORMATS[i]);
            Spinner format = new Spinner(this);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            format.setAdapter(adapter);
            String selectedFormat = editing == null ? BarcodeFormat.CODE_128.name() : loyaltyFormat(editing);
            for (int i = 0; i < LOYALTY_FORMATS.length; i++) {
                if (LOYALTY_FORMATS[i].equals(selectedFormat)) {
                    format.setSelection(i);
                    break;
                }
            }
            form.addView(format);
            TextView warning = text("Только карты лояльности. Не вводите номер банковской карты, срок действия или CVV. Код будет доступен обоим участникам пары.", 12, MUTED, false);
            warning.setPadding(0, dp(8), 0, 0);
            form.addView(warning);
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle(editing == null ? "Добавить карту" : "Изменить карту")
                    .setView(form).setNegativeButton("Отмена", null).setPositiveButton("Сохранить", null).create();
            dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                String name = title.getText().toString().trim();
                if (name.isEmpty()) {
                    title.setError("Введите название программы");
                    return;
                }
                try {
                    String chosenFormat = LOYALTY_FORMATS[format.getSelectedItemPosition()];
                    if (editing == null) {
                        saveLoyaltyCard(name, code.getText().toString(), chosenFormat);
                    } else {
                        String normalizedCode = LoyaltyBarcode.validate(code.getText().toString(), chosenFormat);
                        LoyaltyBarcode.encode(normalizedCode, chosenFormat, 720, BarcodeFormat.QR_CODE.name().equals(chosenFormat) ? 720 : 220);
                        JSONObject updated = new JSONObject(editing.toString());
                        updated.put("title", name);
                        updated.put("detail", normalizedCode);
                        updated.put("barcode_format", chosenFormat);
                        replaceItem("loyalty", updated);
                        render();
                        toast("Изменения карты сохранены");
                    }
                    dialog.dismiss();
                } catch (Exception e) {
                    code.setError(e.getMessage());
                }
            }));
            dialog.show();
        }

        private void promptScannedLoyaltyCard(String contents, String formatName) {
            if (!LoyaltyBarcode.supports(formatName)) {
                toast("Этот формат кода пока не поддерживается. Попробуйте ввести номер вручную.");
                return;
            }
            EditText title = new EditText(this);
            title.setSingleLine(true);
            title.setHint("Название программы лояльности");
            TextView explanation = text("Распознан: " + loyaltyFormatLabel(formatName) + ". Сохранятся название, код и формат; доступ будет у вас обоих.", 13, MUTED, false);
            LinearLayout form = column();
            form.setPadding(dp(22), dp(8), dp(22), dp(4));
            form.addView(explanation);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
            titleParams.topMargin = dp(8);
            form.addView(title, titleParams);
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Сохранить карту")
                    .setView(form).setNegativeButton("Отмена", null).setPositiveButton("Сохранить", null).create();
            dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                String name = title.getText().toString().trim();
                if (name.isEmpty()) {
                    title.setError("Введите название программы");
                    return;
                }
                try {
                    saveLoyaltyCard(name, contents, formatName);
                    dialog.dismiss();
                } catch (Exception e) {
                    toast("Не удалось сохранить код: " + e.getMessage());
                }
            }));
            dialog.show();
        }

        private void saveLoyaltyCard(String title, String rawCode, String formatName) throws Exception {
            if (title == null || title.trim().isEmpty()) throw new IllegalArgumentException("Введите название программы");
            String code = LoyaltyBarcode.validate(rawCode, formatName);
            LoyaltyBarcode.encode(code, formatName, 720, BarcodeFormat.QR_CODE.name().equals(formatName) ? 720 : 220);
            JSONObject item = new JSONObject();
            item.put("id", UUID.randomUUID().toString());
            item.put("title", title.trim());
            item.put("detail", code);
            item.put("barcode_format", formatName);
            JSONArray rows = read("loyalty");
            rows.put(item);
            save("loyalty", rows);
            syncItem("loyalty", item);
            render();
            toast(sync.ready() ? "Карта сохранена; синхронизация с парой выполняется" : "Карта сохранена на устройстве; синхронизируется после подключения пары");
        }

        private android.graphics.Bitmap loyaltyBarcodeBitmap(String code, String format, int width, int height) throws WriterException {
            return loyaltyBarcodeBitmap(code, format, width, height, android.graphics.Color.BLACK, android.graphics.Color.WHITE);
        }

        private android.graphics.Bitmap loyaltyBarcodeBitmap(String code, String format, int width, int height,
                                                              int foregroundColor, int backgroundColor) throws WriterException {
            BitMatrix matrix = LoyaltyBarcode.encode(code, format, Math.max(1, width), Math.max(1, height));
            int[] pixels = new int[matrix.getWidth() * matrix.getHeight()];
            for (int y = 0; y < matrix.getHeight(); y++) {
                int row = y * matrix.getWidth();
                for (int x = 0; x < matrix.getWidth(); x++) pixels[row + x] = matrix.get(x, y) ? foregroundColor : backgroundColor;
            }
            return android.graphics.Bitmap.createBitmap(pixels, matrix.getWidth(), matrix.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        }

        private void showLoyaltyPass(JSONObject item) {
            String code = item.optString("detail", "");
            String format = loyaltyFormat(item);
            boolean qr = BarcodeFormat.QR_CODE.name().equals(format) || BarcodeFormat.DATA_MATRIX.name().equals(format);
            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            int maxWidth = Math.max(dp(160), Math.min(dp(380), metrics.widthPixels - dp(64)));
            int initialWidth = Math.min(dp(300), maxWidth);
            ScrollView scroller = new ScrollView(this);
            LinearLayout panel = column();
            panel.setPadding(dp(14), dp(4), dp(14), dp(8));
            scroller.addView(panel);
            TextView name = text(item.optString("title", "Карта лояльности"), 19, INK, true);
            name.setGravity(Gravity.CENTER);
            panel.addView(name);
            TextView badge = text(loyaltyFormatLabel(format) + " · готово для предъявления", 12, MUTED, false);
            badge.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(-1, -2);
            badgeParams.topMargin = dp(4);
            panel.addView(badge, badgeParams);
            ImageView codeImage = new ImageView(this);
            codeImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
            codeImage.setBackgroundColor(0xFFFFFFFF);
            codeImage.setContentDescription("Код карты " + item.optString("title"));
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(initialWidth, qr ? initialWidth : dp(120));
            imageParams.gravity = Gravity.CENTER_HORIZONTAL;
            imageParams.topMargin = dp(12);
            panel.addView(codeImage, imageParams);
            TextView value = text(code, 12, INK, false);
            value.setTextIsSelectable(true);
            value.setGravity(Gravity.CENTER);
            value.setPadding(0, dp(8), 0, 0);
            panel.addView(value);
            TextView sizeLabel = text("Размер кода", 12, MUTED, false);
            sizeLabel.setPadding(0, dp(14), 0, 0);
            panel.addView(sizeLabel);
            SeekBar size = new SeekBar(this);
            size.setMax(Math.max(0, maxWidth - dp(160)));
            size.setProgress(Math.max(0, initialWidth - dp(160)));
            panel.addView(size);
            TextView contrastLabel = text("Контраст · максимум по умолчанию", 12, MUTED, false);
            contrastLabel.setPadding(0, dp(8), 0, 0);
            panel.addView(contrastLabel);
            SeekBar contrast = new SeekBar(this);
            contrast.setMax(30);
            contrast.setProgress(30);
            panel.addView(contrast);
            TextView brightnessLabel = text("Яркость экрана", 12, MUTED, false);
            panel.addView(brightnessLabel);
            SeekBar brightness = new SeekBar(this);
            brightness.setMax(40);
            brightness.setProgress(25);
            panel.addView(brightness);
            TextView lightButton = text("☀  Максимальная яркость", 13, PURPLE, true);
            lightButton.setGravity(Gravity.CENTER);
            lightButton.setMinHeight(dp(48));
            panel.addView(lightButton);

            Runnable updateCode = () -> {
                int width = dp(160) + size.getProgress();
                int height = qr ? width : Math.max(dp(88), width / 3);
                int gray = (contrast.getMax() - contrast.getProgress()) * 3;
                try {
                    int foreground = android.graphics.Color.rgb(gray, gray, gray);
                    codeImage.setImageBitmap(loyaltyBarcodeBitmap(code, format, width * 2, height * 2, foreground, android.graphics.Color.WHITE));
                    ViewGroup.LayoutParams params = codeImage.getLayoutParams();
                    params.width = width;
                    params.height = height;
                    codeImage.setLayoutParams(params);
                } catch (Exception e) {
                    toast("Не удалось построить код: " + e.getMessage());
                }
            };
            updateCode.run();
            size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { updateCode.run(); }
                @Override public void onStartTrackingTouch(SeekBar seekBar) { }
                @Override public void onStopTrackingTouch(SeekBar seekBar) { }
            });
            contrast.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { updateCode.run(); }
                @Override public void onStartTrackingTouch(SeekBar seekBar) { }
                @Override public void onStopTrackingTouch(SeekBar seekBar) { }
            });
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Показать на кассе")
                    .setView(scroller).setPositiveButton("Готово", null).create();
            final float originalBrightness = getWindow().getAttributes().screenBrightness;
            Runnable setBrightness = () -> {
                android.view.WindowManager.LayoutParams params = getWindow().getAttributes();
                params.screenBrightness = 0.60f + (brightness.getProgress() / 100f);
                getWindow().setAttributes(params);
            };
            brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { setBrightness.run(); }
                @Override public void onStartTrackingTouch(SeekBar seekBar) { }
                @Override public void onStopTrackingTouch(SeekBar seekBar) { }
            });
            lightButton.setOnClickListener(v -> {
                brightness.setProgress(40);
                setBrightness.run();
            });
            dialog.setOnDismissListener(d -> {
                android.view.WindowManager.LayoutParams params = getWindow().getAttributes();
                params.screenBrightness = originalBrightness;
                getWindow().setAttributes(params);
            });
            dialog.show();
        }

    private void onLoyaltyScanResult(Intent data) {
            pendingLoyaltyScan = false;
            IntentResult result = IntentIntegrator.parseActivityResult(IntentIntegrator.REQUEST_CODE, RESULT_OK, data);
            if (result == null || result.getContents() == null) {
                toast("Код не распознан или сканирование отменено. Неизвестный формат можно ввести вручную.");
                return;
            }
            String format = result.getFormatName();
            if (!LoyaltyBarcode.supports(format)) {
                toast("Формат " + (format == null ? "" : format) + " пока не поддерживается. Попробуйте ввести номер вручную.");
                return;
            }
            promptScannedLoyaltyCard(result.getContents(), format);
    }

    private void renderVoting(String storeKey,String title,String subtitle) {
        heading(title, subtitle+" · голосование хранится отдельно от других разделов");
        JSONArray all=read(storeKey); ArrayList<JSONObject> options=new ArrayList<>(); JSONObject result=null;
        for(int i=0;i<all.length();i++){JSONObject item=all.optJSONObject(i);if(item==null)continue;if(item.optBoolean("coin_result"))result=item;else options.add(item);}
        TextView add=pill("＋ Добавить вариант",PURPLE);body.addView(add);add.setOnClickListener(v->addDialog(storeKey,"Новый вариант"));
        for(JSONObject option:options){LinearLayout c=card();c.addView(text("🗳  "+option.optString("title"),16,INK,true));String detail=option.optString("detail");if(!detail.isEmpty())c.addView(text(detail,12,MUTED,false));c.setOnLongClickListener(v->{itemActions(storeKey,option);return true;});}
        if(options.size()==2){
            JSONObject state=result==null?new JSONObject():result;
            boolean sameOptions=result==null
                    ||(options.get(0).optString("title").equals(state.optString("option_first"))
                    &&options.get(1).optString("title").equals(state.optString("option_second")));
            if(!sameOptions){
                state=coinState(storeKey,options,state);
                try{state.put("wins_first",0);state.put("wins_second",0);state.put("series_finished",false);state.put("series_winner","");state.put("title","");saveCoinState(storeKey,state);}
                catch(Exception e){android.util.Log.w("VmesteCoin","Could not reset score after options changed",e);}
            }
            final JSONObject seriesState=state;
            int winsA=seriesState.optInt("wins_first",0),winsB=seriesState.optInt("wins_second",0);
            int target=Math.max(1,seriesState.optInt("target_wins",1));
            boolean finished=seriesState.optBoolean("series_finished",false);
            TextView targetButton=pill("🏁 До "+target+" "+(target==1?"победы":"побед"),PURPLE);
            TextView score=text(options.get(0).optString("title")+"  "+winsA+" : "+winsB+"  "+options.get(1).optString("title"),16,INK,true);
            score.setGravity(Gravity.CENTER);score.setPadding(dp(8),dp(8),dp(8),dp(8));
            TextView outcome=text(finished?"Серия завершена · победитель: "+seriesState.optString("series_winner"):
                    result==null?"Орёл — первый вариант · решка — второй":"Последний бросок: "+seriesState.optString("title"),13,MUTED,true);
            outcome.setGravity(Gravity.CENTER);
            body.addView(targetButton);body.addView(score);body.addView(outcome);
            targetButton.setOnClickListener(v->chooseCoinTarget(storeKey,options,seriesState,winsA,winsB));
            if(finished){
                TextView restart=pill("Начать новую серию",PURPLE);body.addView(restart);
                restart.setOnClickListener(v->{JSONObject next=coinState(storeKey,options,seriesState);try{next.put("wins_first",0);next.put("wins_second",0);next.put("series_finished",false);next.put("series_winner","");next.put("title","");saveCoinState(storeKey,next);render();}catch(Exception e){toast("Не удалось начать новую серию: "+e.getMessage());}});
            }else{
                TextView flip=pill("🪙 Подбросить монетку",PURPLE);body.addView(flip);
                flip.setOnClickListener(v->flipCoin(storeKey,options,outcome,flip,score,seriesState));
            }
        }
        else if(options.size()>2)simpleCard("🎲","Можно выбрать любой из "+options.size()+" вариантов","Монетка доступна, когда останутся ровно два варианта.",null);
        else simpleCard("📝","Нужно два варианта","Добавьте ещё один вариант, чтобы подбросить монетку.",null);
    }
    private void renderGiftRadar(){
        boolean partnerView=prefs.getBoolean("gift_view_partner",false);String owner=partnerView?prefs.getString("partner_user_id",""):sync.userId();
        TextView toggle=pill(partnerView?"Показать мои идеи":"Посмотреть идеи партнёра",PURPLE);body.addView(toggle);toggle.setOnClickListener(v->{prefs.edit().putBoolean("gift_view_partner",!partnerView).apply();render();});
        TextView add=pill("＋ Добавить подарок",PURPLE);body.addView(add);add.setOnClickListener(v->addDialog("gifts","Идея подарка · название или ссылка"));
        JSONArray rows=read("gifts");int visible=0;for(int i=0;i<rows.length();i++){JSONObject item=rows.optJSONObject(i);if(item==null)continue;String itemOwner=item.optString("owner_id",item.optString("created_by",sync.userId()));if(!owner.equals(itemOwner))continue;visible++;LinearLayout c=card();String imageUrl=item.optString("preview_image");if(!GiftPreviewUrl.normalizePublicHttpUrl(imageUrl).isEmpty()){ImageView preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.CENTER_CROP);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(150));ip.bottomMargin=dp(8);c.addView(preview,ip);network.execute(()->{android.graphics.Bitmap bitmap=loadGiftBitmap(imageUrl);if(bitmap!=null)runOnUiThread(()->preview.setImageBitmap(bitmap));});}c.addView(text("🎁  "+item.optString("title"),16,INK,true));String detail=item.optString("detail");if(!detail.isEmpty())c.addView(text(detail,12,MUTED,false));String rawGiftLink=item.optString("url");String giftLink=GiftPreviewUrl.normalizePublicHttpUrl(rawGiftLink);if(!giftLink.isEmpty())c.addView(text("Предпросмотр ссылки  ›",12,PURPLE,true));c.setOnClickListener(v->{if(!giftLink.isEmpty())showGiftPreview(giftLink);else if(!rawGiftLink.trim().isEmpty())toast("Можно просматривать только публичные ссылки http/https");else editItemDialog("gifts",item);});c.setOnLongClickListener(v->{itemActions("gifts",item);return true;});}
        if(visible==0)simpleCard("🎀",partnerView?"Пока нет идей от партнёра":"Сохраните идею подарка","Добавьте название, ссылку на магазин или штрихкод карты лояльности.",null);
    }
    private void renderTimeCapsules(){
        TextView intro=pill("Письмо, которое откроется в выбранный день",PURPLE);body.addView(intro);TextView add=pill("＋ Запечатать послание",PURPLE);body.addView(add);add.setOnClickListener(v->createCapsule());
        JSONArray rows=read("capsule");long now=System.currentTimeMillis();
        for(int i=0;i<rows.length();i++){
            JSONObject item=rows.optJSONObject(i);if(item==null)continue;
            long unlock=item.optLong("unlock_at",0);LinearLayout c=card();
            if(unlock>now){
                LinearLayout seal=new LinearLayout(this);seal.setGravity(Gravity.CENTER_VERTICAL);
                TextView lock=text(item.optString("id").equals(capsuleAnimationId)?"🔓":"🔒",22,PURPLE,true);
                lock.setGravity(Gravity.CENTER);seal.addView(lock,new LinearLayout.LayoutParams(dp(42),dp(44)));
                seal.addView(text("Послание запечатано",16,INK,true),new LinearLayout.LayoutParams(0,-2,1));
                c.addView(seal);
                if(item.optString("id").equals(capsuleAnimationId))animateCapsuleSeal(lock);
                c.addView(text("Откроется "+new SimpleDateFormat("d MMMM yyyy · HH:mm",new Locale("ru")).format(new java.util.Date(unlock)),13,MUTED,false));
            }else{
                c.addView(text("💌  Послание открыто",15,PURPLE,true));
                c.addView(text(item.optString("title"),15,INK,false));
            }
            c.setOnLongClickListener(v->{itemActions("capsule",item);return true;});
            if(unlock>now)scheduleReminder(item.optString("id"),unlock,"Капсула времени открыта","Ваше общее послание ждёт вас");
        }
        capsuleAnimationId="";
    }
    private void animateCapsuleSeal(TextView lock){
        float durationScale;
        try{durationScale=android.provider.Settings.Global.getFloat(getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f);}
        catch(Exception e){durationScale=1f;}
        if(durationScale<=0){lock.setText("🔒");return;}
        lock.setRotation(36);lock.setScaleX(.78f);lock.setScaleY(.78f);
        lock.animate().rotation(0).scaleX(1f).scaleY(1f).setDuration(280)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(()->lock.setText("🔒")).start();
    }
    private void createCapsule(){EditText message=new EditText(this);message.setHint("Ваше послание будущим вам двоим");message.setMinLines(4);message.setGravity(Gravity.TOP|Gravity.START);Calendar date=Calendar.getInstance();date.add(Calendar.DAY_OF_YEAR,1);new android.app.DatePickerDialog(this,(p,y,m,d)->{date.set(y,m,d);new android.app.TimePickerDialog(this,(tp,h,min)->{date.set(Calendar.HOUR_OF_DAY,h);date.set(Calendar.MINUTE,min);date.set(Calendar.SECOND,0);new AlertDialog.Builder(this).setTitle("Запечатать послание").setView(message).setNegativeButton("Отмена",null).setPositiveButton("Запечатать капсулу",(dialog,w)->{String value=message.getText().toString().trim();if(value.isEmpty())return;JSONObject item=new JSONObject();try{String id=UUID.randomUUID().toString();item.put("id",id);item.put("title",value);item.put("detail","Послание времени");item.put("unlock_at",date.getTimeInMillis());item.put("done",false);capsuleAnimationId=id;}catch(Exception e){toast("Не удалось подготовить капсулу: "+e.getMessage());return;}JSONArray rows=read("capsule");rows.put(item);save("capsule",rows);syncItem("capsule",item);render();}).show();},date.get(Calendar.HOUR_OF_DAY),date.get(Calendar.MINUTE),true).show();},date.get(Calendar.YEAR),date.get(Calendar.MONTH),date.get(Calendar.DAY_OF_MONTH)).show();}
    private void renderStories(){
        heading("Истории для двоих","Фото и короткие моменты хранятся сутки");
        JSONArray rows=read("stories");long now=System.currentTimeMillis();
        ArrayList<JSONObject> visible=new ArrayList<>();
        java.util.LinkedHashMap<String,JSONObject> authors=new java.util.LinkedHashMap<>();
        for(int i=rows.length()-1;i>=0;i--){
            JSONObject story=rows.optJSONObject(i);if(story==null)continue;
            if(story.optLong("expires_at",Long.MAX_VALUE)<=now){removeItem("stories",story.optString("id"));continue;}
            visible.add(story);
            String authorId=story.optString("author_id","");
            if(!authorId.isEmpty()&&!authors.containsKey(authorId))authors.put(authorId,story);
        }
        if(!storiesFilter.isEmpty()&&!authors.containsKey(storiesFilter))storiesFilter="";
        renderStoryAuthors(authors,visible.size());
        if(visible.isEmpty()){
            storiesFilter="";
            simpleCard("📷","Ваша первая история начнётся здесь","Добавьте фото или короткий текст — партнёр увидит его в общей ленте.",null);
            return;
        }
        for(JSONObject story:visible){
            String authorId=story.optString("author_id","");
            if(!storiesFilter.isEmpty()&&!storiesFilter.equals(authorId))continue;
            renderStoryPost(story,now);
        }
    }

    private void renderStoryAuthors(java.util.LinkedHashMap<String,JSONObject> authors,int total){
        android.widget.HorizontalScrollView scroll=new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout strip=new LinearLayout(this);strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(dp(3),dp(4),dp(3),dp(8));
        scroll.addView(strip);
        FrameLayout addBubble=new FrameLayout(this);
        TextView addCircle=text("+",23,PURPLE,true);addCircle.setGravity(Gravity.CENTER);
        addCircle.setBackground(shape(softDark?0xFF39333E:PALE,32));
        FrameLayout.LayoutParams circleParams=new FrameLayout.LayoutParams(dp(54),dp(54),Gravity.CENTER);
        addBubble.addView(addCircle,circleParams);
        TextView addLabel=text("Ваша",11,INK,true);addLabel.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams labelParams=new FrameLayout.LayoutParams(dp(70),dp(24),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        addBubble.addView(addLabel,labelParams);
        LinearLayout.LayoutParams addParams=new LinearLayout.LayoutParams(dp(70),dp(82));addParams.rightMargin=dp(6);
        strip.addView(addBubble,addParams);
        addBubble.setFocusable(true);addBubble.setContentDescription("Добавить историю");
        addBubble.setOnClickListener(v->createStoryDialog());
        for(String authorId:authors.keySet()){
            JSONObject latest=authors.get(authorId);
            boolean mine=authorId.equals(sync.userId());
            String name=mine?"Вы":prefs.getString("partner_name","Партнёр");
            FrameLayout bubble=new FrameLayout(this);
            int ring=authorId.equals(storiesFilter)?PURPLE:(softDark?0xFF766681:0xFFD8C7E6);
            android.graphics.drawable.GradientDrawable ringShape=shape(softDark?0xFF211D25:PAPER,36);
            ringShape.setStroke(dp(2),ring);bubble.setBackground(ringShape);
            TextView initials=text(mine?firstCharacter(prefs.getString("profile_name","В")):
                    firstCharacter(prefs.getString("partner_name","П")),18,PURPLE,true);
            initials.setGravity(Gravity.CENTER);
            bubble.addView(initials,new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.CENTER));
            TextView nameLabel=text(name,11,INK,true);nameLabel.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams nameParams=new FrameLayout.LayoutParams(dp(70),dp(24),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
            bubble.addView(nameLabel,nameParams);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(70),dp(82));params.rightMargin=dp(6);
            strip.addView(bubble,params);
            bubble.setFocusable(true);bubble.setContentDescription("Показать истории: "+name);
            bubble.setOnClickListener(v->{storiesFilter=storiesFilter.equals(authorId)?"":authorId;render();});
        }
        body.addView(scroll,new LinearLayout.LayoutParams(-1,dp(88)));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text(storiesFilter.isEmpty()?"Лента · "+total: (authors.get(storiesFilter).optString("author_id").equals(sync.userId())?"Ваши истории":"Истории партнёра"),16,INK,true);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        if(!storiesFilter.isEmpty()){
            TextView all=pill("Все",PURPLE);all.setContentDescription("Показать все истории");
            header.addView(all);all.setOnClickListener(v->{storiesFilter="";render();});
        }
        body.addView(header);
    }

    private String firstCharacter(String value){
        String trimmed=value==null?"":value.trim();
        return trimmed.isEmpty()?"♡":trimmed.substring(0,1).toUpperCase(new Locale("ru"));
    }

    private void renderStoryPost(JSONObject story,long now){
        String authorId=story.optString("author_id","");
        boolean mine=authorId.equals(sync.userId());
        String author=mine?prefs.getString("profile_name","Вы"):prefs.getString("partner_name","Партнёр");
        LinearLayout entry=card();
        LinearLayout byline=new LinearLayout(this);byline.setGravity(Gravity.CENTER_VERTICAL);
        TextView avatar=text(firstCharacter(author),16,PURPLE,true);avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(shape(softDark?0xFF49394F:PALE,24));
        byline.addView(avatar,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout authorInfo=column();authorInfo.setPadding(dp(10),0,0,0);
        authorInfo.addView(text(author,14,INK,true));
        long created=story.optLong("created_at",0);
        String posted=created>0?new SimpleDateFormat("d MMM · HH:mm",new Locale("ru")).format(new java.util.Date(created)):"Недавно";
        authorInfo.addView(text(posted,11,MUTED,false));
        byline.addView(authorInfo,new LinearLayout.LayoutParams(0,-2,1));
        entry.addView(byline);
        String mediaPath=story.optString("media_path","");
        String localPath=story.optString("local_path","");
        if(!mediaPath.isEmpty()||!localPath.isEmpty()){
            ImageView photo=new ImageView(this);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photo.setBackground(shape(softDark?0xFF39333E:PALE,16));photo.setClipToOutline(true);
            LinearLayout.LayoutParams photoParams=new LinearLayout.LayoutParams(-1,dp(340));
            photoParams.topMargin=dp(10);entry.addView(photo,photoParams);
            photo.setContentDescription("Фото в истории от "+author);
            network.execute(()->{try{
                android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();opts.inSampleSize=2;
                android.graphics.Bitmap bitmap;
                if(!localPath.isEmpty()){
                    Uri local=Uri.parse(localPath);
                    if("file".equals(local.getScheme()))bitmap=android.graphics.BitmapFactory.decodeFile(local.getPath(),opts);
                    else try(InputStream input=getContentResolver().openInputStream(local)){
                        bitmap=input==null?null:android.graphics.BitmapFactory.decodeStream(input,null,opts);
                    }
                }else{
                    byte[] bytes=sync.downloadAttachment(mediaPath);
                    bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
                }
                if(bitmap==null)throw new Exception("Формат изображения не поддерживается");
                runOnUiThread(()->{if(bitmap!=null&&photo.getParent()!=null)photo.setImageBitmap(bitmap);});
            }catch(Exception e){android.util.Log.w("VmesteStories","Could not load story photo",e);runOnUiThread(()->{if(photo.getParent()!=null)photo.setContentDescription("Фото истории недоступно");});}});
            photo.setOnClickListener(v->showStoryImage(mediaPath,localPath));
            if(story.optBoolean("sync_pending")){
                TextView retry=pill("↻  Повторить передачу партнёру",PURPLE);entry.addView(retry);
                retry.setOnClickListener(v->retryStoryUpload(story));
            }
        }
        String caption=story.optString("title","");
        if(!caption.isEmpty()){
            if(mediaPath.isEmpty()){
                TextView textStory=text(caption,20,INK,true);textStory.setGravity(Gravity.CENTER);
                textStory.setPadding(dp(18),dp(28),dp(18),dp(28));
                textStory.setBackground(shape(softDark?0xFF403648:0xFFF0E8F6,18));
                LinearLayout.LayoutParams textParams=new LinearLayout.LayoutParams(-1,-2);
                textParams.topMargin=dp(10);entry.addView(textStory,textParams);
            }else{
                TextView captionView=text(caption,15,INK,false);
                captionView.setPadding(dp(2),dp(10),dp(2),dp(4));entry.addView(captionView);
            }
        }
        long left=Math.max(0,story.optLong("expires_at")-now);
        long hours=left/3600000L,minutes=(left/60000L)%60;
        entry.addView(text(hours>0?"История исчезнет через "+hours+" ч "+minutes+" мин":
                "История исчезнет менее чем через час",11,MUTED,false));
        entry.setOnLongClickListener(v->{itemActions("stories",story);return true;});
    }

    private void createStoryDialog(){
        EditText caption=new EditText(this);caption.setHint("Подпись к фото или короткая история");caption.setMinLines(3);caption.setGravity(Gravity.TOP|Gravity.START);
        new AlertDialog.Builder(this).setTitle("Новая история").setMessage("История сохранится на этом устройстве и синхронизируется с парой, когда подключение будет доступно. Через 24 часа она исчезнет.")
                .setView(caption).setNegativeButton("Отмена",null)
                .setNeutralButton("Добавить фото",(d,w)->{pendingStoryCaption=caption.getText().toString().trim();Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("image/*");pick.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(pick,407);})
                .setPositiveButton("Опубликовать текст",(d,w)->publishStory(null,caption.getText().toString().trim())).show();
    }

    private void publishStory(Uri photo,String caption){
        String value=caption==null?"":caption.trim();
        if(value.isEmpty()&&photo==null){toast("Напишите текст или добавьте фото");return;}
        JSONObject story=new JSONObject();String id=UUID.randomUUID().toString();
        try{story.put("id",id);story.put("title",value);story.put("author_id",sync.userId());story.put("expires_at",System.currentTimeMillis()+86400000L);story.put("created_at",System.currentTimeMillis());}
        catch(Exception e){toast("Не удалось подготовить историю");return;}
        if(photo==null){
            JSONArray rows=read("stories");rows.put(story);save("stories",rows);syncItem("stories",story);render();
            if(!sync.ready())toast("История сохранена на устройстве и отправится партнёру после подключения");
            return;
        }
        toast("Сохраняю фото на устройстве…");
        network.execute(()->{try{
            Uri local=archiveChatAttachment(photo,id,"Фото","image/jpeg");
            story.put("local_path",local.toString());story.put("sync_pending",true);
            JSONArray rows=read("stories");rows.put(story);save("stories",rows);
            runOnUiThread(()->{render();toast(sync.ready()?"Фото сохранено. Передаю партнёру…":"Фото сохранено на устройстве. Подключите пару, чтобы отправить его.");});
            if(!sync.ready())return;
            uploadStoryPhoto(story,local);
        }catch(Exception e){runOnUiThread(()->toast("Не удалось сохранить фото истории: "+e.getMessage()));}});
    }

    private void retryStoryUpload(JSONObject story){
        if(!sync.ready()){
            toast("Подключите пару, чтобы передать сохранённое фото");
            return;
        }
        String localPath=story.optString("local_path","");
        Uri local=Uri.parse(localPath);
        if(!"file".equals(local.getScheme())||!new File(local.getPath()).isFile()){
            toast("Локальная копия фото недоступна");
            return;
        }
        try{story.put("sync_pending",true);}
        catch(Exception e){toast("Не удалось подготовить повторную отправку истории");return;}
        toast("Повторяю передачу истории…");
        network.execute(()->uploadStoryPhoto(story,local));
    }

    private void uploadStoryPhoto(JSONObject story,Uri local){
        try{
            String path=sync.uploadAttachment(local,story.optString("id"),this,"image/jpeg");
            JSONObject saved=findItem(read("stories"),story.optString("id"));
            if(saved==null)saved=story;
            saved.put("media_path",path);saved.put("sync_pending",false);
            JSONArray rows=read("stories");save("stories",replaceById(rows,saved));
            syncItem("stories",saved);
            runOnUiThread(()->{render();toast("История опубликована для вашей пары");});
        }catch(Exception e){
            android.util.Log.w("VmesteStories","Could not sync story photo",e);
            runOnUiThread(()->{render();toast("Фото осталось на устройстве. Нажмите «Повторить передачу» после восстановления связи.");});
        }
    }

    private void showStoryImage(String path,String localPath){
        toast("Открываю историю…");
        network.execute(()->{try{
            android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();opts.inSampleSize=2;
            android.graphics.Bitmap bitmap;
            if(!localPath.isEmpty()){
                Uri local=Uri.parse(localPath);
                if("file".equals(local.getScheme()))bitmap=android.graphics.BitmapFactory.decodeFile(local.getPath(),opts);
                else try(InputStream input=getContentResolver().openInputStream(local)){
                    bitmap=input==null?null:android.graphics.BitmapFactory.decodeStream(input,null,opts);
                }
            }else{
                byte[] bytes=sync.downloadAttachment(path);
                bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
            }
            if(bitmap==null)throw new Exception("Формат изображения не поддерживается");
            runOnUiThread(()->{ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setAdjustViewBounds(true);new AlertDialog.Builder(this).setTitle("История для двоих").setView(image).setPositiveButton("Закрыть",null).show();});}
            catch(Exception e){runOnUiThread(()->toast("Не удалось открыть фото истории: "+e.getMessage()));}});
    }
    private void renderPartnerCard(){
        JSONArray rows=read("partner_card");JSONObject card=rows.length()>0?rows.optJSONObject(0):new JSONObject();String name=prefs.getString("partner_name","Партнёр");LinearLayout hero=card();hero.setBackground(shape(softDark?0xFF49394F:0xFFEFE5F6,24));LinearLayout identity=new LinearLayout(this);identity.setGravity(Gravity.CENTER_VERTICAL);hero.addView(identity);ImageView photo=new ImageView(this);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);photo.setBackground(shape(PALE,32));        String partnerAvatar=prefs.getString("partner_avatar_uri","");if(partnerAvatar.isEmpty())partnerAvatar=prefs.getString("partner_avatar_data","");
        boolean hasPhoto=setAvatarPicture(photo,partnerAvatar,64);identity.addView(photo,new LinearLayout.LayoutParams(dp(64),dp(64)));TextView fallback=text(prefs.getString("partner_avatar","♡"),24,PURPLE,true);fallback.setGravity(Gravity.CENTER);fallback.setBackground(shape(PALE,32));fallback.setVisibility(hasPhoto?View.GONE:View.VISIBLE);identity.addView(fallback,new LinearLayout.LayoutParams(dp(64),dp(64)));LinearLayout details=column();details.setPadding(dp(12),0,0,0);details.addView(text("💗  Карта партнёра",12,PURPLE,true));details.addView(text(name,24,INK,true));identity.addView(details,new LinearLayout.LayoutParams(0,-2,1));String bio=prefs.getString("partner_bio","");if(!bio.isEmpty())hero.addView(text(bio,14,MUTED,false));
        String[][] sections={{"Увлечения и хобби","hobbies","🎨"},{"Что любит","loves","♡"},{"Что не любит","dislikes","☁"},{"Как поддержать","comfort","🤍"},{"Важные даты","dates","🗓"},{"Мечты и идеи подарков","dreams","✨"}};
        for(String[] section:sections){LinearLayout block=card();block.addView(text(section[2]+"  "+section[0],15,PURPLE,true));String value=card.optString(section[1],"");block.addView(text(value.isEmpty()?"Пока не заполнено · нажмите, чтобы добавить":value,14,value.isEmpty()?MUTED:INK,false));block.setOnClickListener(v->editPartnerFact(card,section[0],section[1]));}
        TextView add=pill("＋ Добавить ещё о партнёре",PURPLE);body.addView(add);add.setOnClickListener(v->editPartnerFact(card,"Новая заметка о партнёре","notes_about"));
    }
    private void renderNotes(){
        LinearLayout cover=card();cover.setBackground(shape(softDark?0xFF403648:0xFFF0E8F6,22));
        cover.addView(text("📖  Ваш блокнот",18,PURPLE,true));
        cover.addView(text("Свободный текст, планы, последовательности и карта мыслей. Заметки остаются на этом устройстве.",13,MUTED,false));
        TextView add=pill("＋  Новая заметка",PURPLE);body.addView(add);
        add.setOnClickListener(v->editNoteDialog(null));
        JSONArray rows=read("notes");
        for(int i=rows.length()-1;i>=0;i--){
            JSONObject note=rows.optJSONObject(i);if(note==null)continue;
            LinearLayout entry=card();entry.setBackground(shape(softDark?0xFF302A35:0xFFFFFEFC,20));
            entry.addView(text(note.optString("title"),17,INK,true));
            entry.addView(text(noteTypeLabel(note.optString("note_type","free")),11,PURPLE,true));
            renderNoteContent(entry,note.optString("detail"),note.optString("note_type","free"));
            entry.addView(text("Личная заметка · только вы",10,MUTED,true));
            entry.setOnClickListener(v->editNoteDialog(note));
            entry.setOnLongClickListener(v->{itemActions("notes",note);return true;});
        }
    }

    private String noteTypeLabel(String type){
        if("outline".equals(type))return "Структурированный список";
        if("steps".equals(type))return "Последовательность шагов";
        if("map".equals(type))return "Карта мыслей";
        return "Свободная запись";
    }

    private void renderNoteContent(LinearLayout parent,String raw,String type){
        if(raw==null||raw.trim().isEmpty())return;
        if("map".equals(type)){
            String[] lines=raw.split("\\r?\\n");
            TextView rootIdea=text(lines[0].trim(),16,INK,true);rootIdea.setGravity(Gravity.CENTER);
            rootIdea.setPadding(dp(12),dp(10),dp(12),dp(10));rootIdea.setBackground(shape(PALE,16));
            parent.addView(rootIdea);
            if(lines.length>1){
                View stem=new View(this);stem.setBackgroundColor(PURPLE);
                LinearLayout.LayoutParams stemParams=new LinearLayout.LayoutParams(dp(2),dp(12));
                stemParams.gravity=Gravity.CENTER;parent.addView(stem,stemParams);
                for(int i=1;i<lines.length;i++){
                    String branch=lines[i].trim();if(branch.isEmpty())continue;
                    TextView node=text("↳  "+branch,14,INK,false);
                    node.setPadding(dp(12),dp(8),dp(12),dp(8));node.setBackground(shape(themedFill(PALE),14));
                    LinearLayout.LayoutParams nodeParams=new LinearLayout.LayoutParams(-1,-2);
                    nodeParams.bottomMargin=dp(4);parent.addView(node,nodeParams);
                }
            }
            return;
        }
        String[] lines=raw.split("\\r?\\n");
        for(int i=0;i<lines.length;i++){
            if(lines[i].isEmpty())continue;
            String line=lines[i];
            if("steps".equals(type))line=(i+1)+".  "+line.replaceFirst("^\\d+[.)]?\\s*","");
            else if("outline".equals(type)&&!line.matches("^\\s*[•–-].*"))line="•  "+line;
            TextView item=text(line,14,INK,false);
            item.setPadding(0,dp(3),0,dp(3));parent.addView(item);
        }
    }

    private void editNoteDialog(JSONObject existing){
        LinearLayout form=column();form.setPadding(dp(18),dp(4),dp(18),dp(4));
        EditText title=new EditText(this);title.setSingleLine(true);title.setHint("Заголовок");
        if(existing!=null)title.setText(existing.optString("title"));form.addView(title);
        Spinner type=new Spinner(this);
        String[] types={"Свободный текст","Список / план","Последовательность шагов","Карта мыслей"};
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,types);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);type.setAdapter(adapter);
        String[] keys={"free","outline","steps","map"};
        if(existing!=null)for(int i=0;i<keys.length;i++)
            if(keys[i].equals(existing.optString("note_type","free")))type.setSelection(i);
        form.addView(type,new LinearLayout.LayoutParams(-1,dp(52)));
        EditText content=new EditText(this);content.setHint("Пишите как в обычном блокноте");
        content.setGravity(Gravity.TOP|Gravity.START);content.setMinLines(7);
        content.setText(existing==null?"":existing.optString("detail"));form.addView(content);
        TextView helper=text("Для карты мыслей: первая строка — тема, каждая следующая — отдельная ветка.",12,MUTED,false);
        helper.setPadding(0,dp(5),0,dp(6));form.addView(helper);
        TextView insert=pill("＋  Вставить пункт",PURPLE);form.addView(insert);
        insert.setOnClickListener(v->{
            int selection=Math.max(0,content.getSelectionStart());
            String value=content.getText().toString();
            int start=Math.min(selection,value.length());
            String prefix=type.getSelectedItemPosition()==2
                    ?(value.isEmpty()||value.endsWith("\n")?"":"\n")+(value.split("\\n",-1).length)+". "
                    :(value.isEmpty()||value.endsWith("\n")?"":"\n")
                            +(type.getSelectedItemPosition()==3?"Ветка: ":"• ");
            content.getText().insert(start,prefix);content.setSelection(start+prefix.length());
        });
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle(existing==null?"Новая заметка":"Изменить заметку").setView(scroll)
                .setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
            String name=title.getText().toString().trim();
            if(name.isEmpty()){title.setError("Введите заголовок");return;}
            try{
                JSONObject note=existing==null?new JSONObject():new JSONObject(existing.toString());
                if(!note.has("id"))note.put("id",UUID.randomUUID().toString());
                note.put("title",name);note.put("detail",content.getText().toString());
                note.put("note_type",keys[type.getSelectedItemPosition()]);
                note.put("updated_at",System.currentTimeMillis());
                if(existing==null){JSONArray rows=read("notes");rows.put(note);save("notes",rows);}
                else replaceItem("notes",note);
                dialog.dismiss();render();
            }catch(Exception e){toast("Не удалось сохранить заметку: "+e.getMessage());}
        }));
        dialog.show();
    }

    private void renderRecipes(){
        TextView intro=text("Ваши рецепты хранятся на устройстве и синхронизируются с подключённой парой.",13,MUTED,false);
        intro.setPadding(0,0,0,dp(8));body.addView(intro);
        TextView add=pill("＋  Добавить рецепт",PURPLE);body.addView(add);
        add.setOnClickListener(v->editRecipeDialog(null));
        JSONArray recipes=read("recipes");
        for(int i=recipes.length()-1;i>=0;i--){
            JSONObject recipe=recipes.optJSONObject(i);if(recipe==null)continue;
            LinearLayout entry=card();entry.addView(text("🍲  "+recipe.optString("title"),17,INK,true));
            String servings=recipe.optString("servings","");
            if(!servings.isEmpty())entry.addView(text("Порции: "+servings,12,PURPLE,true));
            String ingredients=recipe.optString("ingredients","");
            if(!ingredients.isEmpty())entry.addView(text("Ингредиенты · "+firstNonEmptyLine(ingredients),13,MUTED,false));
            entry.addView(text(recipe.optString("steps").isEmpty()?"Добавьте шаги приготовления":"Шагов: "+countNonEmptyLines(recipe.optString("steps")),
                    12,MUTED,false));
            entry.setOnClickListener(v->showRecipe(recipe));
            entry.setOnLongClickListener(v->{itemActions("recipes",recipe);return true;});
        }
        if(recipes.length()==0)simpleCard("🥣","Первый рецепт","Запишите ингредиенты с граммовками и порядок приготовления.",null);
    }

    private String firstNonEmptyLine(String value){
        for(String line:value.split("\\r?\\n"))if(!line.trim().isEmpty())return line.trim();
        return "";
    }

    private int countNonEmptyLines(String value){
        int count=0;for(String line:value.split("\\r?\\n"))if(!line.trim().isEmpty())count++;
        return count;
    }

    private void showRecipe(JSONObject recipe){
        LinearLayout details=column();details.setPadding(dp(18),dp(8),dp(18),dp(8));
        String servings=recipe.optString("servings","");
        if(!servings.isEmpty())details.addView(text("Порции: "+servings,13,PURPLE,true));
        details.addView(text("Ингредиенты",16,INK,true));
        TextView ingredients=text(recipe.optString("ingredients",""),14,INK,false);
        ingredients.setPadding(0,dp(5),0,dp(12));details.addView(ingredients);
        details.addView(text("Приготовление",16,INK,true));
        renderNoteContent(details,recipe.optString("steps"),"steps");
        ScrollView scroll=new ScrollView(this);scroll.addView(details);
        new AlertDialog.Builder(this).setTitle(recipe.optString("title","Рецепт"))
                .setView(scroll).setPositiveButton("Закрыть",null).show();
    }

    private void editRecipeDialog(JSONObject existing){
        LinearLayout form=column();form.setPadding(dp(18),dp(4),dp(18),dp(4));
        EditText title=new EditText(this);title.setSingleLine(true);title.setHint("Название блюда");
        if(existing!=null)title.setText(existing.optString("title"));form.addView(title);
        EditText servings=new EditText(this);servings.setSingleLine(true);
        servings.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        servings.setHint("Количество порций");
        if(existing!=null)servings.setText(existing.optString("servings"));form.addView(servings);
        EditText ingredients=new EditText(this);ingredients.setHint("Ингредиенты и граммовки\nНапример: мука — 200 г");
        ingredients.setGravity(Gravity.TOP|Gravity.START);ingredients.setMinLines(4);
        if(existing!=null)ingredients.setText(existing.optString("ingredients"));form.addView(ingredients);
        EditText steps=new EditText(this);steps.setHint("Шаги приготовления — каждый с новой строки");
        steps.setGravity(Gravity.TOP|Gravity.START);steps.setMinLines(5);
        if(existing!=null)steps.setText(existing.optString("steps"));form.addView(steps);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(existing==null?"Новый рецепт":"Изменить рецепт")
                .setView(scroll).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
            String name=title.getText().toString().trim();
            if(name.isEmpty()){title.setError("Введите название блюда");return;}
            if(ingredients.getText().toString().trim().isEmpty()){ingredients.setError("Добавьте ингредиенты");return;}
            if(steps.getText().toString().trim().isEmpty()){steps.setError("Добавьте шаги приготовления");return;}
            try{
                JSONObject recipe=existing==null?new JSONObject():new JSONObject(existing.toString());
                if(!recipe.has("id"))recipe.put("id",UUID.randomUUID().toString());
                recipe.put("title",name);recipe.put("servings",servings.getText().toString().trim());
                recipe.put("ingredients",ingredients.getText().toString().trim());
                recipe.put("steps",steps.getText().toString().trim());
                recipe.put("detail",firstNonEmptyLine(ingredients.getText().toString()));
                recipe.put("updated_at",System.currentTimeMillis());
                if(existing==null){JSONArray rows=read("recipes");rows.put(recipe);save("recipes",rows);syncItem("recipes",recipe);}
                else replaceItem("recipes",recipe);
                dialog.dismiss();render();
            }catch(Exception e){toast("Не удалось сохранить рецепт: "+e.getMessage());}
        }));
        dialog.show();
    }
    private void editPartnerFact(JSONObject current,String title,String key){EditText value=new EditText(this);value.setHint("Напишите по-доброму и конкретно");value.setMinLines(3);value.setGravity(Gravity.TOP|Gravity.START);value.setText(current.optString(key,""));new AlertDialog.Builder(this).setTitle(title).setView(value).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,w)->{try{current.put(key,value.getText().toString().trim());if(!current.has("id"))current.put("id",UUID.randomUUID().toString());JSONArray rows=new JSONArray();rows.put(current);save("partner_card",rows);syncItem("partner_card",current);render();}catch(Exception ignored){}}).show();}
    private JSONObject coinState(String storeKey,ArrayList<JSONObject> options,JSONObject previous){
        JSONObject state;
        try{state=previous==null?new JSONObject():new JSONObject(previous.toString());}catch(Exception e){state=new JSONObject();}
        try{
            if(!state.has("id"))state.put("id",UUID.nameUUIDFromBytes(("coin_result:"+storeKey+":"+sync.coupleId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
            state.put("coin_result",true);
            state.put("option_first",options.get(0).optString("title"));
            state.put("option_second",options.get(1).optString("title"));
            if(!state.has("target_wins"))state.put("target_wins",1);
        }catch(Exception e){android.util.Log.w("VmesteCoin","Could not prepare shared coin state",e);}
        return state;
    }

    private void saveCoinState(String storeKey,JSONObject state){
        try{
            state.put("updated_at",System.currentTimeMillis());
            JSONArray rows=read(storeKey);boolean found=false;
            for(int i=0;i<rows.length();i++){JSONObject item=rows.optJSONObject(i);if(item!=null&&item.optBoolean("coin_result")){rows.put(i,state);found=true;break;}}
            if(!found)rows.put(state);
            save(storeKey,rows);syncItem(storeKey,state);
        }catch(Exception e){toast("Не удалось сохранить счёт: "+e.getMessage());}
    }

    private void chooseCoinTarget(String storeKey,ArrayList<JSONObject> options,JSONObject state,int winsA,int winsB){
        String[] labels={"1 победа","3 победы","6 побед","10 побед"};
        int[] targets={1,3,6,10};int selected=0;
        for(int i=0;i<targets.length;i++)if(targets[i]==state.optInt("target_wins",1))selected=i;
        new AlertDialog.Builder(this).setTitle("Сколько побед нужно для серии?")
                .setSingleChoiceItems(labels,selected,(dialog,which)->{
                    Runnable apply=()->{
                        JSONObject next=coinState(storeKey,options,state);
                        try{next.put("target_wins",targets[which]);next.put("wins_first",0);next.put("wins_second",0);next.put("series_finished",false);next.put("series_winner","");next.put("title","");saveCoinState(storeKey,next);render();}
                        catch(Exception e){toast("Не удалось настроить серию: "+e.getMessage());}
                    };
                    if(winsA+ winsB>0)new AlertDialog.Builder(this).setTitle("Начать счёт заново?")
                            .setMessage("Смена длины серии сбросит текущий счёт.")
                            .setNegativeButton("Оставить как есть",(d,w)->{}).setPositiveButton("Сбросить и изменить",(d,w)->apply.run()).show();
                    else apply.run();
                    dialog.dismiss();
                }).setNegativeButton("Отмена",null).show();
    }

    private void flipCoin(String storeKey,ArrayList<JSONObject> options,TextView outcome,TextView button,TextView score,JSONObject previous){
        button.setEnabled(false);button.setText("Монетка крутится…");final int[] ticks={0};final Handler h=new Handler(Looper.getMainLooper());Runnable[] animation={null};
        animation[0]=()->{
            ticks[0]++;outcome.setText(ticks[0]%2==0?"🪙  Орёл":"🪙  Решка");
            if(ticks[0]<12){h.postDelayed(animation[0],85);return;}
            if(new Random().nextInt(300)==0){outcome.setText("Монетка встала на ребро · перебрасываем");h.postDelayed(()->flipCoin(storeKey,options,outcome,button,score,previous),850);return;}
            int pick=new Random().nextInt(2);String label=options.get(pick).optString("title");
            boolean sameOptions=previous!=null
                    &&options.get(0).optString("title").equals(previous.optString("option_first"))
                    &&options.get(1).optString("title").equals(previous.optString("option_second"));
            JSONObject saved=coinState(storeKey,options,previous);
            int winsA=sameOptions?saved.optInt("wins_first",0):0;
            int winsB=sameOptions?saved.optInt("wins_second",0):0;
            int target=Math.max(1,saved.optInt("target_wins",1));
            CoinSeriesRules.Score nextScore=CoinSeriesRules.advance(winsA,winsB,target,pick);
            winsA=nextScore.firstWins;winsB=nextScore.secondWins;boolean finished=!nextScore.winner.isEmpty();
            try{saved.put("wins_first",winsA);saved.put("wins_second",winsB);saved.put("title",label);saved.put("series_finished",finished);saved.put("series_winner",finished?label:"");}
            catch(Exception e){toast("Не удалось обновить счёт: "+e.getMessage());}
            score.setText(options.get(0).optString("title")+"  "+winsA+" : "+winsB+"  "+options.get(1).optString("title"));
            outcome.setText(finished?"Серия завершена · победитель: "+label:"Выпало: "+label);
            button.setEnabled(true);button.setText("🪙 Бросить ещё раз");
            saveCoinState(storeKey,saved);
            if(finished)render();
        };
        h.post(animation[0]);
    }

    private void renderSharedTimer() {
        heading("Общий таймер","Отсчёт хранится в общем пространстве пары");
        JSONArray current=read("shared_timer");JSONObject timer=current.length()==0?null:current.optJSONObject(0);
        TextView counter=text("Таймер не запущен",24,PURPLE,true);counter.setGravity(Gravity.CENTER);LinearLayout timerCard=card();timerCard.addView(counter);
        TextView currentMode=text(timer==null?"Выберите, для кого запустить таймер":
                timer.optString("mode_label","Для нас"),13,MUTED,false);
        currentMode.setGravity(Gravity.CENTER);timerCard.addView(currentMode);
        TextView modeLabel=text("Кому поставить таймер",14,INK,true);
        LinearLayout.LayoutParams modeLabelParams=new LinearLayout.LayoutParams(-1,-2);
        modeLabelParams.topMargin=dp(8);body.addView(modeLabel,modeLabelParams);
        Spinner mode=new Spinner(this);
        String[] modes={"Для меня","Для партнёра","Для нас"};
        ArrayAdapter<String> modeAdapter=new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,modes);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mode.setAdapter(modeAdapter);
        if(timer!=null){
            String savedMode=timer.optString("mode_label","Для нас");
            for(int i=0;i<modes.length;i++)if(modes[i].equals(savedMode))mode.setSelection(i);
        }
        mode.setContentDescription("Для кого запустить таймер");
        body.addView(mode,new LinearLayout.LayoutParams(-1,dp(52)));
        EditText minutes=new EditText(this);minutes.setSingleLine(true);minutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);minutes.setHint("Минуты");body.addView(minutes);
        TextView start=pill("Запустить таймер",PURPLE);body.addView(start);start.setOnClickListener(v->{int value;try{value=Integer.parseInt(minutes.getText().toString());}catch(Exception e){minutes.setError("Введите минуты");return;}if(value<1||value>1440){minutes.setError("От 1 минуты до суток");return;}String target=modes[mode.getSelectedItemPosition()];JSONObject next=new JSONObject();try{String timerId=timer==null||timer.optString("id").isEmpty()?UUID.randomUUID().toString():timer.optString("id");next.put("id",timerId);next.put("title","Таймер · "+target);next.put("mode_label",target);next.put("minutes",value);next.put("timer_end",System.currentTimeMillis()+value*60000L);next.put("started_by",sync.userId());}catch(Exception ignored){}JSONArray rows=new JSONArray();rows.put(next);save("shared_timer",rows);syncItem("shared_timer",next);scheduleReminder(next.optString("id"),next.optLong("timer_end"),"Таймер завершён",target+" · отсчёт завершён");render();});
        if(timer!=null){TextView stop=pill("Остановить таймер",0xFFB44F69);body.addView(stop);stop.setOnClickListener(v->{removeRemoteItem(timer.optString("id"));prefs.edit().remove("shared_timer").apply();render();});}
        if(sharedTimerTick!=null)gameHandler.removeCallbacks(sharedTimerTick);if(sharedTimerPoll!=null)gameHandler.removeCallbacks(sharedTimerPoll);
        sharedTimerTick=new Runnable(){public void run(){if(!"shared_timer".equals(feature))return;JSONArray rows=read("shared_timer");JSONObject active=rows.length()==0?null:rows.optJSONObject(0);if(active==null)counter.setText("Таймер не запущен");else{long left=Math.max(0,active.optLong("timer_end")-System.currentTimeMillis());counter.setText(String.format(Locale.US,"%02d:%02d:%02d",left/3600000,(left/60000)%60,(left/1000)%60));if(left==0)counter.append("\nВремя вышло");}gameHandler.postDelayed(this,1000);}};gameHandler.post(sharedTimerTick);
        sharedTimerPoll=new Runnable(){public void run(){if(!"shared_timer".equals(feature))return;if(sync.ready())network.execute(()->{try{JSONArray rows=sync.fetchItems();boolean found=false;for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);JSONObject payload=row.optJSONObject("payload");if(payload!=null&&"shared_timer".equals(payload.optString("app_key")))found=true;sync.storeRemote(row,prefs);}if(!found)prefs.edit().remove("shared_timer").apply();runOnUiThread(()->{if("shared_timer".equals(feature)){if(sharedTimerTick!=null){gameHandler.removeCallbacks(sharedTimerTick);gameHandler.post(sharedTimerTick);}if(body!=null)render();}});}catch(Exception ignored){}});gameHandler.postDelayed(this,5000);}};gameHandler.postDelayed(sharedTimerPoll,5000);
    }

    private void renderSharedReminder() {
        heading("Напоминание для партнёра","Напоминание синхронизируется в общем календаре");
        TextView create=pill("＋ Выбрать дату и время",PURPLE);body.addView(create);create.setOnClickListener(v->{Calendar when=Calendar.getInstance();new android.app.DatePickerDialog(this,(picker,year,month,day)->{when.set(Calendar.YEAR,year);when.set(Calendar.MONTH,month);when.set(Calendar.DAY_OF_MONTH,day);new android.app.TimePickerDialog(this,(time,hour,minute)->{when.set(Calendar.HOUR_OF_DAY,hour);when.set(Calendar.MINUTE,minute);when.set(Calendar.SECOND,0);if(when.getTimeInMillis()<=System.currentTimeMillis()){toast("Выберите время в будущем");return;}EditText note=new EditText(this);note.setHint("Что напомнить партнёру?");new AlertDialog.Builder(this).setTitle("Общее напоминание").setView(note).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,w)->{String value=note.getText().toString().trim();if(value.isEmpty())value="Пора выполнить ваш общий план";append("events",value,"Напоминание партнёру");JSONArray rows=read("events");JSONObject item=rows.optJSONObject(rows.length()-1);if(item!=null)try{item.put("reminder_at",when.getTimeInMillis());item.put("remind_partner",true);save("events",rows);syncItem("events",item);scheduleReminder(item.optString("id"),when.getTimeInMillis(),"Вместе",value);}catch(Exception ignored){}render();}).show();},when.get(Calendar.HOUR_OF_DAY),when.get(Calendar.MINUTE),true).show();},when.get(Calendar.YEAR),when.get(Calendar.MONTH),when.get(Calendar.DAY_OF_MONTH)).show();});
        JSONArray events=read("events");for(int i=0;i<events.length();i++){JSONObject item=events.optJSONObject(i);if(item!=null&&item.optBoolean("remind_partner"))renderEditableEntry("events",events,i,"🔔");}
    }

    private void scheduleReminder(String id,long at,String title,String message) {
        if("Вместе".equals(title)&&prefs.getBoolean("owned_partner_reminder_"+id,false))return;
        if(id.isEmpty()||at<=System.currentTimeMillis())return;
        if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},8101);}
        android.content.Intent intent=new android.content.Intent(this,ReminderReceiver.class);intent.putExtra("title",title);intent.putExtra("body",message);intent.putExtra("notification_id",id.hashCode());
        android.app.PendingIntent pending=android.app.PendingIntent.getBroadcast(this,id.hashCode(),intent,android.app.PendingIntent.FLAG_UPDATE_CURRENT|android.app.PendingIntent.FLAG_IMMUTABLE);
        android.app.AlarmManager alarm=(android.app.AlarmManager)getSystemService(ALARM_SERVICE);if(alarm!=null)alarm.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP,at,pending);
    }

    private void syncQuietly() {
        flushPendingSync();
        if(!sync.ready())return;network.execute(()->{try{String before=prefs.getAll().toString();JSONArray rows=sync.fetchItems();boolean hasTimer=false;for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);sync.storeRemote(row,prefs);JSONObject payload=row.optJSONObject("payload");if(payload!=null){if("shared_timer".equals(payload.optString("app_key"))){hasTimer=true;scheduleReminder(row.optString("id"),payload.optLong("timer_end"),"Таймер завершён","Ваш общий таймер закончился");}if("capsule".equals(payload.optString("app_key")))scheduleReminder(row.optString("id"),payload.optLong("unlock_at"),"Капсула времени открыта","Ваше общее послание ждёт вас");if(payload.optBoolean("remind_partner")&&!sync.userId().equals(row.optString("created_by")))scheduleReminder(row.optString("id"),payload.optLong("reminder_at"),"Вместе",payload.optString("title","Напоминание"));}}if(!hasTimer)prefs.edit().remove("shared_timer").apply();String after=prefs.getAll().toString();if(!before.equals(after))runOnUiThread(()->{if(!(getCurrentFocus() instanceof EditText)&&!isFinishing())render();});}catch(Exception ignored){}});
    }
    private void flushPendingSync(){if(!sync.ready())return;JSONArray pending;try{pending=new JSONArray(prefs.getString("pending_shared_sync","[]"));}catch(Exception e){pending=new JSONArray();}if(pending.length()==0)return;final JSONArray batch=pending;network.execute(()->{for(int i=0;i<batch.length();i++){JSONObject entry=batch.optJSONObject(i);if(entry==null)continue;try{String k=entry.optString("key","");JSONObject it=entry.optJSONObject("item");if(k.isEmpty()||it==null)continue;sync.upsert(k,it);String id=it.optString("id");synchronized(MainActivity.this){JSONArray latest;try{latest=new JSONArray(prefs.getString("pending_shared_sync","[]"));}catch(Exception e){latest=new JSONArray();}for(int j=latest.length()-1;j>=0;j--){JSONObject q=latest.optJSONObject(j);if(q!=null){JSONObject itemObj=q.optJSONObject("item");if(itemObj!=null&&id.equals(itemObj.optString("id")))latest.remove(j);}}prefs.edit().putString("pending_shared_sync",latest.toString()).apply();}}catch(Exception ignored){}}});}

    @Override protected void onResume(){super.onResume();activityResumed=true;resumePendingWatchRoomInvite();syncQuietly();refreshActiveWatchRooms(true);refreshProfiles();checkGitHubUpdate(false);if(isChatOpen())startChatRefresh();String pending=prefs.getString("pending_update_url","");if(!pending.isEmpty()&&android.os.Build.VERSION.SDK_INT>=26&&getPackageManager().canRequestPackageInstalls()){String digest=prefs.getString("pending_update_digest","");String version=prefs.getString("pending_update_version","");prefs.edit().remove("pending_update_url").remove("pending_update_digest").remove("pending_update_version").apply();downloadGitHubUpdate(pending,digest,version);}if(sharedSyncPoll==null)sharedSyncPoll=new Runnable(){public void run(){if(!isFinishing()){syncQuietly();refreshActiveWatchRooms(false);refreshProfiles();gameHandler.postDelayed(this,15000);}}};gameHandler.removeCallbacks(sharedSyncPoll);gameHandler.postDelayed(sharedSyncPoll,15000);}
    @Override protected void onPause(){activityResumed=false;if(sharedSyncPoll!=null)gameHandler.removeCallbacks(sharedSyncPoll);if(chatRefreshRunnable!=null)gameHandler.removeCallbacks(chatRefreshRunnable);super.onPause();}

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("pending_loyalty_scan", pendingLoyaltyScan);
        state.putBoolean("pending_watch_room_scan", pendingWatchRoomScan);
        super.onSaveInstanceState(state);
    }

    private float swipeStartX, swipeStartY;
    private boolean swipeCandidate, swipeConsumed;
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        int action = event.getActionMasked();
        if (action == android.view.MotionEvent.ACTION_DOWN) {
            swipeStartX = event.getRawX(); swipeStartY = event.getRawY(); swipeConsumed = false;
            swipeCandidate = feature == null && contentScroll != null && !(getCurrentFocus() instanceof EditText);
            if (swipeCandidate) { int[] location = new int[2]; contentScroll.getLocationOnScreen(location); swipeCandidate = swipeStartY >= location[1] && swipeStartY <= location[1] + contentScroll.getHeight(); }
        } else if (action == android.view.MotionEvent.ACTION_MOVE && swipeCandidate) {
            float dx = event.getRawX() - swipeStartX, dy = event.getRawY() - swipeStartY;
            if (Math.abs(dy) > dp(36) && Math.abs(dy) > Math.abs(dx)) swipeCandidate = false;
            else if (Math.abs(dx) > dp(72) && Math.abs(dx) > Math.abs(dy) * 1.4f) {
                android.view.MotionEvent cancel = android.view.MotionEvent.obtain(event); cancel.setAction(android.view.MotionEvent.ACTION_CANCEL); super.dispatchTouchEvent(cancel); cancel.recycle();
                swipeCandidate = false; swipeConsumed = true; navigateBySwipe(dx < 0); return true;
            }
        } else if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
            boolean consumed = swipeConsumed; swipeCandidate = false; swipeConsumed = false; if (consumed) return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private void navigateBySwipe(boolean forward) {
        if (nav == null || feature != null) return;
        int current = -1; for (int i = 0; i < nav.getChildCount(); i++) if (tab.equals(nav.getChildAt(i).getTag())) { current = i; break; }
        int next = current + (forward ? 1 : -1); if (current < 0 || next < 0 || next >= nav.getChildCount()) return;
        Object destination = nav.getChildAt(next).getTag();
        if (!(destination instanceof String)) return;
        String nextTab=(String)destination;
        if("Чат".equals(nextTab))chatScrollPolicy.jumpToLatest();
        boolean reduceMotion=false;
        try{reduceMotion=android.provider.Settings.Global.getFloat(getContentResolver(),android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f;}catch(Exception ignored){}
        if(reduceMotion||contentScroll==null){tab=nextTab;feature=null;render();if(contentScroll!=null)contentScroll.scrollTo(0,0);return;}
        View page=contentScroll;float direction=forward?-1f:1f;
        page.animate().cancel();
        page.animate().alpha(0f).translationX(direction*dp(24)).setDuration(90).withEndAction(()->{
            tab=nextTab;feature=null;render();page.setTranslationX(-direction*dp(12));
            page.animate().alpha(1f).translationX(0).setDuration(150).start();
            page.scrollTo(0,0);
        }).start();
    }

    private void renderCoupleSetup() {
        if (!sync.configured()) {
            simpleCard("☁", "Общее пространство", "Добавьте адрес и публичный ключ Supabase, чтобы связывать данные двух телефонов.", null);
            TextView configure = pill("Настроить подключение", PURPLE); body.addView(configure);
            configure.setOnClickListener(v -> {
                feature=null;tab=NavigationModel.SETTINGS_TAB;render();
            });
            return;
        }
        if (!sync.signedIn()) {
            simpleCard("♡", "Войдите или создайте аккаунт", "Используйте один аккаунт на своём телефоне и пригласите партнёра кодом.", null);
            TextView account = pill("Войти / зарегистрироваться", PURPLE); body.addView(account);
            account.setOnClickListener(v -> accountDialog());
            return;
        }
        if (sync.coupleId().isEmpty() && coupleRestorePending) {
            simpleCard("♡", "Проверяем вашу пару", "Восстанавливаем приглашение, связанное с этим аккаунтом.", null);
            return;
        }
        if (sync.coupleId().isEmpty() && coupleRestoreFailed) {
            simpleCard("!", "Не удалось проверить пару", "Не создавайте новую пару, пока не удалось проверить существующее приглашение.", null);
            TextView retry = pill("Повторить проверку", PURPLE); body.addView(retry);
            retry.setOnClickListener(v -> restoreCoupleBinding());
            return;
        }
        if (sync.coupleId().isEmpty()) {
            simpleCard("♡", "Создайте пространство для двоих", "Один из вас создаёт пару и отправляет код приглашения второму.", null);
            TextView create = pill("Создать приглашение", PURPLE); body.addView(create);
            create.setOnClickListener(v -> createCoupleDialog());
            TextView scan = pill("Сканировать QR-код партнёра", PURPLE); body.addView(scan);
            scan.setOnClickListener(v -> startInviteScanner());
            TextView join = pill("Ввести код вручную", PURPLE); body.addView(join);
            join.setOnClickListener(v -> joinCoupleDialog());
        } else {
            String partnerName=prefs.getString("partner_name","").trim();
            simpleCard("✓", partnerName.isEmpty()?"Пара подключена":"Вы в паре с "+partnerName,
                    "Общее пространство доступно только двум аккаунтам по коду: " + sync.inviteCode(), null);
            TextView showQr = pill("Показать QR-код приглашения", PURPLE); body.addView(showQr);
            showQr.setOnClickListener(v -> showInviteCodeDialog());
            TextView share = pill("Отправить код партнёру", PURPLE); body.addView(share);
            share.setOnClickListener(v -> shareText("Присоединяйся к нашему пространству «Вместе». Код: " + sync.inviteCode()));
            TextView refresh = pill("Синхронизировать данные", PURPLE); body.addView(refresh);
            refresh.setOnClickListener(v -> syncItems());
        }
    }

    private void configureServer() {
        LinearLayout form = column(); form.setPadding(dp(18), 0, dp(18), 0);
        EditText url = new EditText(this); url.setSingleLine(true); url.setHint("Project URL (https://…supabase.co)");
        EditText key = new EditText(this); key.setSingleLine(true); key.setHint("Publishable / anon key");
        url.setText(sync.url()); key.setText(sync.key());
        form.addView(url); form.addView(key);
        new AlertDialog.Builder(this).setTitle("Подключение Supabase").setMessage("Сначала создайте проект и примените SQL-инструкцию из папки supabase. Секретный service_role key сюда вводить нельзя.")
                .setView(form).setNegativeButton("Отмена", null).setPositiveButton("Сохранить", (d, w) -> {
                    String projectUrl = url.getText().toString().trim(), publicKey = key.getText().toString().trim();
                    if (!projectUrl.startsWith("https://") || publicKey.isEmpty()) { toast("Проверьте URL и публичный ключ"); return; }
                    sync.saveProject(projectUrl, publicKey); render();
                }).show();
    }

    private void accountDialog() {
        LinearLayout form = column(); form.setPadding(dp(18), 0, dp(18), 0);
        EditText email = new EditText(this); email.setSingleLine(true); email.setHint("Электронная почта"); email.setInputType(33);
        EditText password = new EditText(this); password.setSingleLine(true); password.setHint("Пароль (от 6 символов)"); password.setInputType(129);
        form.addView(email); form.addView(password);
        new AlertDialog.Builder(this).setTitle("Аккаунт Вместе").setView(form).setNegativeButton("Отмена", null)
                .setNeutralButton("Регистрация", (d, w) -> authenticate(email.getText().toString(), password.getText().toString(), true))
                .setPositiveButton("Войти", (d, w) -> authenticate(email.getText().toString(), password.getText().toString(), false)).show();
    }

    private void authenticate(String email, String password, boolean signUp) {
        if (email.trim().isEmpty() || password.length() < 6) { toast("Введите почту и пароль не короче 6 символов"); return; }
        runNetwork(() -> sync.authenticate(email.trim(), password, signUp), result -> {
            if (deferGameNotificationPermission) {
                deferGameNotificationPermission = false;
                requestGameNotificationPermission();
            }
            if ("EMAIL_CONFIRMATION_REQUIRED".equals(result)) toast("Подтвердите почту по ссылке из письма, затем войдите");
            else { toast(signUp ? "Аккаунт создан" : "Вы вошли"); restoreCoupleBinding(); }
        });
    }

    private void restoreCoupleBinding() {
        if (!sync.signedIn()) return;
        coupleRestorePending = true;
        coupleRestoreFailed = false;
        render();
        runNetwork(sync::restoreCoupleMembership, restored -> {
            coupleRestorePending = false;
            coupleRestoreFailed = false;
            if (Boolean.TRUE.equals(restored)) {
                persistCurrentProfile();
                refreshProfiles();
            }
            render();
            resumePendingWatchRoomInvite();
        }, error -> {
            coupleRestorePending = false;
            coupleRestoreFailed = true;
            render();
            toast("Вход выполнен, но пару не удалось проверить: " + error.getMessage());
        });
    }

    private void createCoupleDialog() {
        EditText name = new EditText(this); name.setSingleLine(true); name.setHint("Название пары, например: Мы");
        new AlertDialog.Builder(this).setTitle("Создать приглашение")
                .setMessage("Создайте пространство, затем покажите QR-код партнёру. У каждого должен быть свой аккаунт.")
                .setView(name).setNegativeButton("Отмена", null)
                .setPositiveButton("Создать", (d, w) -> runNetwork(() -> sync.createCouple(name.getText().toString()), result -> {
                    persistCurrentProfile(); render(); showInviteCodeDialog();
                })).show();
    }

    private void joinCoupleDialog() {
        EditText code = new EditText(this);
        code.setSingleLine(true);
        code.setHint("8 символов или VMESTE:XXXXXXXX");
        code.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        code.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(15)});
        new AlertDialog.Builder(this).setTitle("Ввести код приглашения")
                .setMessage("Или отсканируйте QR-код на главном экране или во вкладке «Пригласить».")
                .setView(code).setNegativeButton("Отмена", null)
                .setPositiveButton("Присоединиться", (d, w) -> joinCoupleWithCode(code.getText().toString())).show();
    }

    private void joinCoupleWithCode(String rawCode) {
        String code = InviteCode.normalize(rawCode);
        if (code.isEmpty()) { toast("Код приглашения должен содержать 8 букв или цифр"); return; }
        runNetwork(() -> sync.joinCouple(code), result -> {
            persistCurrentProfile(); render(); refreshProfiles(); toast("Вы теперь в общем пространстве");
            resumePendingWatchRoomInvite();
        });
    }

    private void persistCurrentProfile(){
        if(!sync.ready())return;
        try{
            JSONObject profile=new JSONObject();
            profile.put("display_name",prefs.getString("profile_name",""));
            profile.put("avatar",prefs.getString("profile_avatar","♡"));
            profile.put("avatar_local_uri",localAvatarUri());
            profile.put("bio",prefs.getString("profile_bio",""));
            persistProfile(profile);
        }catch(Exception e){android.util.Log.w("VmesteProfile","Could not prepare profile after pairing",e);toast("Не удалось подготовить профиль для синхронизации");}
    }

    private void startInviteScanner() {
        if (!sync.configured()) { toast("Сначала настройте подключение к общему пространству"); return; }
        if (!sync.signedIn()) { accountDialog(); return; }
        try {
            new IntentIntegrator(this)
                    .setDesiredBarcodeFormats(java.util.Collections.singletonList(BarcodeFormat.QR_CODE.toString()))
                    .setPrompt("Наведите камеру на QR-код приглашения Вместе")
                    .setBeepEnabled(false)
                    .setOrientationLocked(false)
                    .initiateScan();
        } catch (RuntimeException e) {
            android.util.Log.w("VmesteInvite", "Could not launch the QR scanner", e);
            toast("Не удалось открыть сканер. Введите код приглашения вручную.");
        }
    }

    private void showInviteCodeDialog() {
        String code = InviteCode.normalize(sync.inviteCode());
        if (code.isEmpty()) { toast("Сначала создайте приглашение для пары"); return; }
        LinearLayout content = column(); content.setGravity(Gravity.CENTER); content.setPadding(dp(20), dp(8), dp(20), dp(8));
        ImageView qrImage = new ImageView(this); qrImage.setContentDescription("QR-код приглашения в пару");
        try {
            BitMatrix matrix = InviteQrCode.encode(code, dp(232));
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(matrix.getWidth(), matrix.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
            for (int y=0;y<matrix.getHeight();y++) for (int x=0;x<matrix.getWidth();x++)
                bitmap.setPixel(x,y,matrix.get(x,y)?android.graphics.Color.BLACK:android.graphics.Color.WHITE);
            qrImage.setImageBitmap(bitmap);
            content.addView(qrImage, new LinearLayout.LayoutParams(dp(232), dp(232)));
        } catch (WriterException e) {
            android.util.Log.e("VmesteInvite", "Could not create invite QR code", e);
            content.addView(text("QR-код не удалось создать. Отправьте код вручную.", 14, MUTED, false));
        }
        TextView invite = text(code, 22, INK, true); invite.setGravity(Gravity.CENTER); invite.setPadding(0,dp(12),0,dp(4)); content.addView(invite);
        TextView hint = text("Партнёр может отсканировать этот код в разделе «Пригласить».", 13, MUTED, false);
        hint.setGravity(Gravity.CENTER); content.addView(hint);
        new AlertDialog.Builder(this).setTitle("Приглашение для пары").setView(content)
                .setNeutralButton("Поделиться кодом", (d,w) -> shareText("Присоединяйся к нашему пространству «Вместе». Код: " + code))
                .setPositiveButton("Готово", null).show();
    }

    private void renderMovieRoom() {
        syncMovieRoomMetadata();
        JSONObject room = readMovieRoom();
        if (room == null) {
            simpleCard("🍿", "Выберите фильм или видео", "YouTube и публичные MP4/HLS-ссылки синхронизируются в комнате. Закрытые плееры могут не поддерживать управление.", null);
            TextView create = pill("＋  Создать кино-комнату", PURPLE); body.addView(create);
            create.setOnClickListener(v -> editMovieRoom(null));
            TextView youtube = pill("Найти видео на YouTube", PURPLE); body.addView(youtube);
            youtube.setOnClickListener(v -> openUrl("https://www.youtube.com"));
            TextView syncRoom = pill("Обновить комнату партнёра", PURPLE); body.addView(syncRoom);
            syncRoom.setOnClickListener(v -> fetchMovieRoom());
            addWatchRoomRoleAction();
            return;
        }
        String title = room.optString("title", "Вечер кино"), url = room.optString("url", "");
        String status = room.optString("status", "ready");
        simpleCard("🎬", title, statusText(status) + (sync.signedIn() && !sync.coupleId().isEmpty() ? " · общее пространство подключено" : " · только на этом телефоне"), null);
        if (!url.isEmpty()) {
            TextView open = pill("▶  Открыть ссылку отдельно", PURPLE); body.addView(open); open.setOnClickListener(v -> openUrl(url));
        }
        TextView share = pill("Поделиться комнатой", PURPLE); body.addView(share);
        share.setOnClickListener(v -> shareText("Смотрим вместе: " + title + (url.isEmpty() ? "" : "\n" + url)));
        TextView state = pill("▶  Мы смотрим", PURPLE); body.addView(state);
        state.setOnClickListener(v -> updateMovieRoom("playing"));
        TextView pause = pill("Ⅱ  Пауза", PURPLE); body.addView(pause);
        pause.setOnClickListener(v -> updateMovieRoom("paused"));
        TextView done = pill("Закончили просмотр", PURPLE); body.addView(done);
        done.setOnClickListener(v -> updateMovieRoom("finished"));
        TextView change = pill("Изменить фильм / ссылку", PURPLE); body.addView(change);
        change.setOnClickListener(v -> editMovieRoom(room));
        TextView refresh = pill("Обновить с телефона партнёра", PURPLE); body.addView(refresh);
        refresh.setOnClickListener(v -> fetchMovieRoom());
        addWatchRoomRoleAction();
    }

    private void addWatchRoomRoleAction() {
        heading("Комната просмотра", "Один телефон создаёт комнату и становится ведущим. Второй подключается по приглашению.");
        TextView create = pill("＋  Создать комнату", PURPLE);
        body.addView(create);
        create.setOnClickListener(v -> prepareLiveRoomCreation());
        TextView join = pill("Подключиться к комнате партнёра", PURPLE);
        body.addView(join);
        join.setOnClickListener(v -> showWatchRoomJoinOptions());
        TextView hint = text("После создания появятся QR-код, ссылка и код для отправки партнёру.", 12, MUTED, false);
        hint.setPadding(dp(4), 0, dp(4), dp(8));
        body.addView(hint);
    }

    private void showWatchRoomJoinOptions() {
        if (!ensureWatchRoomAccess()) return;
        new AlertDialog.Builder(this)
                .setTitle("Подключиться к комнате")
                .setItems(new String[]{"Выбрать активную комнату", "Ввести код или ссылку", "Сканировать QR-код"}, (dialog, choice) -> {
                    if (choice == 0) joinLiveRoom();
                    else if (choice == 1) showWatchRoomCodeEntry();
                    else startWatchRoomScanner();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showWatchRoomCodeEntry() {
        EditText code = new EditText(this);
        code.setSingleLine(true);
        code.setHint("Код VMESTE:ROOM:… или ссылка vmeste://room/…");
        code.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Ввести приглашение")
                .setMessage("Вставьте код или ссылку, полученные от ведущего.")
                .setView(code)
                .setPositiveButton("Подключиться", null)
                .setNegativeButton("Отмена", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String roomId = WatchRoomQrCode.decodeInvitation(code.getText().toString());
            if (roomId == null) {
                code.setError("Проверьте код или ссылку приглашения");
                return;
            }
            dialog.dismiss();
            handleWatchRoomInvite(roomId);
        }));
        dialog.show();
    }

    private void renderWatchRoomInvitation() {
        if (!sync.ready()) return;
        refreshActiveWatchRooms(false);
        JSONArray partnerRooms = new JSONArray();
        JSONObject ownRoom = null;
        String userId = sync.userId();
        for (int i = 0; i < activeWatchRoomsSnapshot.length(); i++) {
            JSONObject room = activeWatchRoomsSnapshot.optJSONObject(i);
            if (room == null || !"active".equals(room.optString("state"))) continue;
            if (userId.equals(room.optString("host_id"))) {
                if (ownRoom == null) ownRoom = room;
            } else {
                partnerRooms.put(room);
            }
        }
        if (partnerRooms.length() > 0) {
            heading("Партнёр приглашает вас в комнату",
                    "Подключайтесь, если партнёр сейчас находится в комнате");
            TextView connect = pill("Подключиться", PURPLE);
            body.addView(connect);
            JSONArray invitations = partnerRooms;
            connect.setOnClickListener(v -> {
                if (invitations.length() == 1) confirmJoinWatchRoom(invitations.optJSONObject(0));
                else showActiveWatchRooms(invitations);
            });
        } else if (ownRoom != null) {
            heading("Ваша комната просмотра активна", "После сбоя сохранённая запись может быть устаревшей");
            String roomId = ownRoom.optString("id", "");
            TextView reopen = pill("Продолжить или заменить комнату", PURPLE);
            body.addView(reopen);
            reopen.setOnClickListener(v -> confirmOpenHostedWatchRoom(roomId));
        } else if (activeWatchRoomsUnavailable) {
            simpleCard("!", "Не удалось проверить приглашение",
                    activeWatchRoomsFailureLabel + " Нажмите, чтобы повторить проверку.",
                    () -> refreshActiveWatchRooms(true));
        }
    }

    private boolean isWatchRoomDiscoveryVisible() {
        return activityResumed && !isFinishing();
    }

    private void refreshActiveWatchRooms(boolean force) {
        if (!isWatchRoomDiscoveryVisible()) return;
        if (!sync.ready()) {
            activeWatchRoomsSnapshot = new JSONArray();
            activeWatchRoomsUnavailable = false;
            activeWatchRoomsFailureLabel = "";
            return;
        }
        long now = System.currentTimeMillis();
        if (activeWatchRoomsFetchInFlight
                || (!force && now - lastActiveWatchRoomsFetchAt < 15_000)) return;
        activeWatchRoomsFetchInFlight = true;
        network.execute(() -> {
            JSONArray rooms = null;
            Exception failure = null;
            try {
                rooms = sync.activeWatchRooms();
            } catch (Exception e) {
                failure = e;
            }
            final JSONArray result = rooms;
            final boolean failed = failure != null;
            final String failureLabel = failed
                    ? WatchRoomError.label(failure.getMessage()) : "";
            runOnUiThread(() -> {
                activeWatchRoomsFetchInFlight = false;
                lastActiveWatchRoomsFetchAt = System.currentTimeMillis();
                String previous = activeWatchRoomsSnapshot.toString();
                boolean previousUnavailable = activeWatchRoomsUnavailable;
                activeWatchRoomsSnapshot = failed || result == null ? new JSONArray() : result;
                activeWatchRoomsUnavailable = failed;
                activeWatchRoomsFailureLabel = failureLabel;
                boolean changed = !previous.equals(activeWatchRoomsSnapshot.toString())
                        || previousUnavailable != activeWatchRoomsUnavailable;
                if (changed && !isFinishing() && isWatchRoomDiscoveryVisible()) render();
            });
        });
    }

    private void syncMovieRoomMetadata(){
        if(movieRoomFetchInFlight||!sync.ready())return;
        movieRoomFetchInFlight=true;
        network.execute(()->{try{JSONObject shared=sync.fetchMovieRoom();runOnUiThread(()->{movieRoomFetchInFlight=false;if(shared==null)return;JSONObject local=readMovieRoom();if(shared.optLong("updated",0)> (local==null?0:local.optLong("updated",0))){prefs.edit().putString("movie_room",shared.toString()).apply();if(feature!=null&&feature.contains("просмотра"))render();}});}catch(Exception e){runOnUiThread(()->movieRoomFetchInFlight=false);}});
    }

    private boolean ensureWatchRoomAccess() {
        if (!sync.configured()) {
            tab = "Пригласить";
            feature = null;
            render();
            toast("Сначала настройте Supabase в разделе «Пригласить партнёра».");
            return false;
        }
        if (!sync.signedIn()) {
            accountDialog();
            return false;
        }
        if (sync.coupleId().isEmpty()) {
            tab = "Пригласить";
            feature = null;
            render();
            toast(coupleRestorePending
                    ? "Проверяем существующую пару. Повторите действие через мгновение."
                    : "Подключитесь к той же паре, что и партнёр, прежде чем открывать комнату.");
            return false;
        }
        return true;
    }

    private void createLiveRoom() {
        if (!ensureWatchRoomAccess() || watchRoomCreationInFlight) return;
        watchRoomCreationInFlight = true;
        JSONObject room = readMovieRoom();
        String roomTitle = room == null ? "Совместный просмотр" : room.optString("title", "Совместный просмотр");
        this.<JSONObject>runNetwork(() -> {
            String id=sync.startWatchRoom(roomTitle);
            JSONObject active=new JSONObject();active.put("id",id);active.put("host_id",sync.userId());active.put("title",roomTitle);active.put("state","active");
            String playerUrl=room==null?"":room.optString("url","");
            try {
                JSONObject sharedMovie=sync.fetchMovieRoom();
                if(sharedMovie!=null)playerUrl=sharedMovie.optString("url",playerUrl);
            } catch(Exception e) {
                active.put("movie_url_sync_failed",true);
                android.util.Log.w("VmesteWatch","Could not sync the optional movie link",e);
            }
            active.put("player_url",playerUrl);
            return active;
        }, active -> {
            watchRoomCreationInFlight = false;
            boolean host=sync.userId().equals(active.optString("host_id"));
            startActivity(WatchPartyActivity.intent(this,active.optString("id"),host,active.optString("title","Совместный просмотр"))
                    .putExtra(WatchPartyActivity.EXTRA_PLAYER_URL,active.optString("player_url",""))
                    .putExtra(WatchPartyActivity.EXTRA_SHOW_INVITE, host));
            if(active.optBoolean("movie_url_sync_failed"))toast("Комната открыта, но не удалось обновить ссылку на видео");
        }, error -> {
            watchRoomCreationInFlight = false;
            new AlertDialog.Builder(this)
                    .setTitle("Комната не создана")
                    .setMessage(WatchRoomError.label(error.getMessage()))
                    .setPositiveButton("Понятно", null)
                    .show();
        });
    }

    private void prepareLiveRoomCreation() {
        if (!ensureWatchRoomAccess() || watchRoomListInFlight || watchRoomCreationInFlight) return;
        watchRoomListInFlight = true;
        this.<JSONArray>runNetwork(sync::activeWatchRooms, rooms -> {
            watchRoomListInFlight = false;
            if (rooms.length() == 0) {
                createLiveRoom();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Уже есть активная комната")
                    .setMessage("Сначала выберите комнату, где партнёр уже ждёт. Создание новой комнаты завершит все предыдущие активные комнаты.")
                    .setPositiveButton("Выбрать активную", (dialog, which) -> showActiveWatchRooms(rooms))
                    .setNeutralButton("Создать новую и завершить старые", (dialog, which) -> createLiveRoom())
                    .setNegativeButton("Отмена", null)
                    .show();
        }, error -> {
            watchRoomListInFlight = false;
            showWatchRoomError(error);
        });
    }

    private void joinLiveRoom() {
        if (!ensureWatchRoomAccess()) return;
        this.<JSONArray>runNetwork(sync::activeWatchRooms, this::showActiveWatchRooms, this::showWatchRoomError);
    }

    private void showActiveWatchRooms(JSONArray rooms) {
        if (rooms.length() == 0) {
            new AlertDialog.Builder(this)
                    .setTitle("Активных комнат нет")
                    .setMessage("Попросите партнёра создать комнату и пригласить вас, затем обновите список. Новая комната не будет создана автоматически.")
                    .setPositiveButton("Обновить", (dialog, which) -> joinLiveRoom())
                    .setNeutralButton("Сканировать QR-код", (dialog, which) -> startWatchRoomScanner())
                    .setNegativeButton("Отмена", null)
                    .show();
            return;
        }
        String[] labels = new String[rooms.length()];
        for (int i = 0; i < rooms.length(); i++) {
            JSONObject room = rooms.optJSONObject(i);
            String hostLabel = room != null && sync.userId().equals(room.optString("host_id"))
                    ? "этот аккаунт" : "партнёр";
            String createdAt = room == null ? "" : room.optString("created_at", "");
            String timestamp = createdAt.length() >= 16
                    ? " · " + createdAt.substring(0, 16).replace('T', ' ')
                            + (createdAt.endsWith("Z") || createdAt.endsWith("+00:00") ? " UTC" : "")
                    : "";
            labels[i] = "Комната " + (i + 1) + " · ведущий: " + hostLabel + timestamp;
        }
        new AlertDialog.Builder(this)
                .setTitle("Выберите ведущего")
                .setMessage("Комнаты отсортированы от новых к старым. После аварийного закрытия запись может быть устаревшей — выбирайте только комнату, где ведущий сейчас ждёт.")
                .setItems(labels, (dialog, which) -> openSelectedWatchRoom(rooms.optJSONObject(which)))
                .setNeutralButton("Сканировать QR-код", (dialog, which) -> startWatchRoomScanner())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void openSelectedWatchRoom(JSONObject selected) {
        if (selected == null) {
            showWatchRoomError(new Exception("Не удалось прочитать выбранную комнату"));
            return;
        }
        String roomId = selected.optString("id", "");
        if (sync.userId().equals(selected.optString("host_id"))) confirmOpenHostedWatchRoom(roomId);
        else confirmJoinWatchRoom(selected);
    }

    private void confirmOpenHostedWatchRoom(String roomId) {
        new AlertDialog.Builder(this)
                .setTitle("Комната отмечена как активная")
                .setMessage("После аварийного завершения SDP/ICE могли устареть. Открывайте эту комнату только если хотите продолжить её; создание новой завершит старые активные комнаты.")
                .setPositiveButton("Продолжить эту", (dialog, which) -> openHostedWatchRoom(roomId))
                .setNeutralButton("Создать новую", (dialog, which) -> confirmCreateFreshWatchRoom())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void confirmCreateFreshWatchRoom() {
        new AlertDialog.Builder(this)
                .setTitle("Создать новую комнату?")
                .setMessage("Все старые активные комнаты этой пары будут завершены. Продолжить?")
                .setPositiveButton("Создать новую", (dialog, which) -> createLiveRoom())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void openHostedWatchRoom(String roomId) {
        if (roomId.isEmpty()) {
            showWatchRoomError(new Exception("У выбранной комнаты отсутствует код"));
            return;
        }
        this.<JSONObject>runNetwork(() -> {
            JSONObject room = sync.getWatchRoom(roomId);
            String hostError = WatchRoomJoinPolicy.error(
                    room == null ? "" : room.optString("state", ""),
                    room == null ? "" : room.optString("couple_id", ""),
                    room == null ? "" : room.optString("host_id", ""),
                    sync.coupleId(), sync.userId(), true);
            if (hostError != null) throw new Exception(hostError);
            JSONObject movie = sync.fetchMovieRoom();
            if (movie != null) room.put("player_url", movie.optString("url", ""));
            return room;
        }, room -> startActivity(WatchPartyActivity.intent(this, roomId, true,
                room.optString("title", "Совместный просмотр"))
                .putExtra(WatchPartyActivity.EXTRA_PLAYER_URL, room.optString("player_url", ""))),
                this::showWatchRoomError);
    }

    private void startWatchRoomScanner() {
        if (!ensureWatchRoomAccess()) return;
        pendingWatchRoomScan = true;
        try {
            new IntentIntegrator(this)
                    .setDesiredBarcodeFormats(java.util.Collections.singletonList(BarcodeFormat.QR_CODE.toString()))
                    .setPrompt("Наведите камеру на QR-код комнаты «Вместе»")
                    .setBeepEnabled(false)
                    .setOrientationLocked(false)
                    .initiateScan();
        } catch (RuntimeException e) {
            pendingWatchRoomScan = false;
            android.util.Log.w("VmesteWatch", "Could not launch the room QR scanner", e);
            toast("Не удалось открыть сканер QR-кода");
        }
    }

    private void openSelectedWatchRoomFromQr(String payload) {
        String roomId = WatchRoomQrCode.decode(payload);
        if (roomId == null) {
            toast("Это не QR-приглашение в комнату просмотра");
            return;
        }
        openJoinableWatchRoom(roomId);
    }

    private void confirmJoinWatchRoom(JSONObject selected) {
        if (selected == null) {
            showWatchRoomError(new Exception("Не удалось прочитать выбранную комнату"));
            return;
        }
        String roomId = selected.optString("id", "");
        if (roomId.isEmpty()) {
            showWatchRoomError(new Exception("У выбранной комнаты отсутствует код"));
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Подключиться к комнате?")
                .setMessage("Запись комнаты отмечена как активная, но это не подтверждает, что ведущий сейчас в сети. Если ведущий закрыл приложение аварийно, попросите его создать новую комнату.")
                .setPositiveButton("Подключиться", (dialog, which) -> openJoinableWatchRoom(roomId))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void openJoinableWatchRoom(String roomId) {
        this.<JSONObject>runNetwork(() -> {
            JSONObject room = sync.getWatchRoom(roomId);
            String joinError = WatchRoomJoinPolicy.error(
                    room == null ? "" : room.optString("state", ""),
                    room == null ? "" : room.optString("couple_id", ""),
                    room == null ? "" : room.optString("host_id", ""),
                    sync.coupleId(), sync.userId());
            if (joinError != null) throw new Exception(joinError);
            JSONObject movie = sync.fetchMovieRoom();
            if (movie != null) room.put("player_url", movie.optString("url", ""));
            return room;
        }, room -> startActivity(WatchPartyActivity.intent(this, roomId, false,
                room.optString("title", "Совместный просмотр"))
                .putExtra(WatchPartyActivity.EXTRA_PLAYER_URL, room.optString("player_url", ""))),
                this::showWatchRoomError);
    }

    private void showWatchRoomError(Exception error) {
        String message = WatchRoomError.label(error == null ? null : error.getMessage());
        new AlertDialog.Builder(this)
                .setTitle("Не удалось выполнить действие с комнатой")
                .setMessage(message)
                .setPositiveButton("Понятно", null)
                .show();
    }

    private void profileDialog() {
        LinearLayout form = column(); form.setPadding(dp(20), dp(8), dp(20), dp(10)); form.setBackgroundColor(PAPER);
        EditText name = new EditText(this); name.setSingleLine(true); name.setHint("Ваше имя"); name.setText(prefs.getString("profile_name", ""));
        EditText avatar = new EditText(this); avatar.setSingleLine(true); avatar.setHint("Эмодзи профиля, например 🌿"); avatar.setText(prefs.getString("profile_avatar", "♡"));
        EditText bio = new EditText(this); bio.setHint("О себе или короткое пожелание"); bio.setText(prefs.getString("profile_bio", "")); bio.setMinLines(3); bio.setGravity(Gravity.TOP | Gravity.START);
        TextView avatarLabel = text("Ваше фото профиля", 12, MUTED, true); avatarLabel.setPadding(0, dp(8), 0, 0); form.addView(avatarLabel);
        ImageView avatarPreview = new ImageView(this); avatarPreview.setScaleType(ImageView.ScaleType.CENTER_CROP); avatarPreview.setBackgroundColor(0xFFE8E0EC);
        String avatarUri=localAvatarUri();
        boolean hasPhoto=setAvatarPicture(avatarPreview,avatarUri,48);
        avatarPreview.setVisibility(hasPhoto?View.VISIBLE:View.GONE);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(72), dp(72)); avatarParams.gravity=Gravity.CENTER_HORIZONTAL; avatarParams.topMargin=dp(4); avatarParams.bottomMargin=dp(8); form.addView(avatarPreview, avatarParams);
        TextView avatarEmoji = text(prefs.getString("profile_avatar","♡"), 24, PURPLE, true); avatarEmoji.setGravity(Gravity.CENTER); avatarEmoji.setVisibility(hasPhoto?View.GONE:View.VISIBLE);
        LinearLayout.LayoutParams emojiParams = new LinearLayout.LayoutParams(dp(72), dp(72)); emojiParams.gravity=Gravity.CENTER_HORIZONTAL; emojiParams.topMargin=dp(4); emojiParams.bottomMargin=dp(8); form.addView(avatarEmoji, emojiParams);
        TextView intro = text("Ваш уютный образ для партнёра", 12, MUTED, false); intro.setPadding(0, 0, 0, dp(10)); form.addView(intro);
        for (EditText field : new EditText[]{name, avatar, bio}) { field.setTextColor(INK); field.setHintTextColor(MUTED); field.setPadding(dp(14), dp(11), dp(14), dp(11)); field.setBackground(shape(softDark ? 0xFF3A3440 : 0xFFFBF9FC, 14)); }
        TextView nameLabel = text("Имя", 12, MUTED, true); form.addView(nameLabel); form.addView(name);
        TextView avatarFieldLabel = text("Эмодзи профиля", 12, MUTED, true); avatarFieldLabel.setPadding(0,dp(8),0,0); form.addView(avatarFieldLabel); form.addView(avatar);
        TextView emojiPicker=pill("Выбрать эмодзи профиля",PURPLE);form.addView(emojiPicker);
        emojiPicker.setOnClickListener(v->showProfileEmojiPicker(avatar));
        TextView bioLabel = text("Пара слов о себе", 12, MUTED, true); bioLabel.setPadding(0, dp(8), 0, 0); form.addView(bioLabel); form.addView(bio);
        TextView previewName = text(name.getText().toString().trim().isEmpty() ? "Как вас будут видеть" : name.getText().toString().trim(), 12, MUTED, false); previewName.setGravity(Gravity.CENTER); previewName.setPadding(0,dp(4),0,0); form.addView(previewName);
        TextView chooseAvatar = pill("📷 Выбрать фото", PURPLE); form.addView(chooseAvatar);
        chooseAvatar.setOnClickListener(v -> { Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT); pick.setType("image/*"); pick.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(pick, 406); });
        TextView takeAvatar = pill("Сделать фото в приложении", PURPLE); form.addView(takeAvatar); takeAvatar.setOnClickListener(v -> startCameraCapture(406));
        TextView clearAvatar = pill("Убрать фото профиля", MUTED); form.addView(clearAvatar);
        clearAvatar.setOnClickListener(v -> {
            prefs.edit().remove("profile_avatar_uri").remove("profile_avatar_data")
                    .remove("profile_avatar_remote_path").putBoolean("profile_avatar_pending",true).apply();
            avatarPreview.setVisibility(View.GONE);
            avatarEmoji.setVisibility(View.VISIBLE);
            try {
                JSONObject profile=new JSONObject();
                profile.put("display_name",prefs.getString("profile_name",""));
                profile.put("avatar",prefs.getString("profile_avatar","♡"));
                profile.put("avatar_local_uri","");
                profile.put("bio",prefs.getString("profile_bio",""));
                persistProfile(profile);
            } catch(Exception e){android.util.Log.w("VmesteProfile","Could not prepare profile photo removal",e);}
            showApp();
        });
        name.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { previewName.setText(s.length() == 0 ? "Как вас будут видеть" : s); }
            public void afterTextChanged(android.text.Editable e) { }
        });
        avatar.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { avatarEmoji.setText(s.length() == 0 ? "♡" : s); }
            public void afterTextChanged(android.text.Editable e) { }
        });
        ScrollView profileScroll=new ScrollView(this);profileScroll.setBackgroundColor(PAPER);profileScroll.setFillViewport(true);profileScroll.addView(form);
        AlertDialog profileDialog = new AlertDialog.Builder(this).setTitle("Мой профиль").setView(profileScroll).setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (d,w) -> {
                    String n = name.getText().toString().trim(), a = avatar.getText().toString().trim(), b = bio.getText().toString().trim();
                    if (n.isEmpty()) { toast("Введите имя для профиля"); return; }
                    if (a.isEmpty()) a = "♡";
                    prefs.edit().putString("profile_name", n).putString("profile_avatar", a).putString("profile_bio", b).apply();
                    JSONObject profile = new JSONObject();
                    try { profile.put("display_name", n); profile.put("avatar", a); profile.put("avatar_local_uri", localAvatarUri()); profile.put("bio", b); } catch (Exception ignored) { }
                    persistProfile(profile); showApp();
                }).create();
        profileDialog.setOnShowListener(v -> { if (profileDialog.getWindow() != null) profileDialog.getWindow().setBackgroundDrawable(shape(PAPER, 24)); int titleId=getResources().getIdentifier("alertTitle", "id", "android"); TextView titleView=profileDialog.findViewById(titleId); if(titleView!=null)titleView.setTextColor(INK); profileDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(PURPLE); profileDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(MUTED); });
        profileDialog.show();
    }

    private void showProfileEmojiPicker(EditText avatar){
        String[] choices={"♡","🌿","🌙","☀️","✨","🌸","🌻","🍀","🪻","🦋","🐈","🐇","🦊","🐻","🐼","🐧","🐢","🐬","🍓","🍒","🍑","🍉","🍋","🍄","☕","🎧","🎨","📚","🧸","💌","💜","🩵"};
        android.widget.GridLayout grid=new android.widget.GridLayout(this);grid.setColumnCount(4);
        grid.setPadding(dp(8),dp(8),dp(8),dp(8));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Эмодзи профиля")
                .setView(grid).setNegativeButton("Закрыть",null).create();
        for(String choice:choices){
            TextView item=text(choice,25,INK,false);item.setGravity(Gravity.CENTER);
            item.setContentDescription("Выбрать "+choice);
            android.widget.GridLayout.LayoutParams params=new android.widget.GridLayout.LayoutParams();
            params.width=dp(58);params.height=dp(54);params.setMargins(dp(2),dp(2),dp(2),dp(2));
            grid.addView(item,params);
            item.setOnClickListener(v->{avatar.setText(choice);dialog.dismiss();});
        }
        dialog.show();
    }

    private void checkGitHubUpdate(boolean userInitiated) {
        long now = System.currentTimeMillis();
        if (!userInitiated && now - prefs.getLong("last_update_check", 0) < 6L * 60 * 60 * 1000) return;
        prefs.edit().putLong("last_update_check", now).apply();
        network.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL("https://api.github.com/repos/Shoroh030/mac.lira/releases/latest").openConnection();
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "Vmeste-Android");
                connection.setConnectTimeout(10000); connection.setReadTimeout(12000);
                if (connection.getResponseCode() != 200) throw new Exception("GitHub не вернул последнюю опубликованную версию");
                StringBuilder response = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(connection.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) { String line; while ((line = reader.readLine()) != null) response.append(line); }
                JSONObject release = new JSONObject(response.toString());
                String tag = release.optString("tag_name", "").replaceFirst("^[vV]", "");
                if (tag.isEmpty()) throw new Exception("В релизе GitHub отсутствует версия");
                String current = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                int versionOrder = ReleaseVersion.compare(tag, current);
                String assetUrl = "", assetDigest = "";
                JSONArray assets = release.optJSONArray("assets");
                String expectedAssetName = "together-" + tag + ".apk";
                if (assets != null) for (int i = 0; i < assets.length(); i++) { JSONObject asset = assets.optJSONObject(i); if (asset == null) continue; if (expectedAssetName.equals(asset.optString("name", ""))) { assetUrl = asset.optString("browser_download_url", ""); assetDigest = asset.optString("digest", ""); break; } }
                final String download = assetUrl, digest = assetDigest, version = tag;
                final boolean newer = versionOrder > 0;
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    if (newer && !download.isEmpty() && isSha256Digest(digest) && isTrustedGitHubAssetUrl(download)) showGitHubUpdate(version, download, digest);
                    else if (newer && !download.isEmpty()) { if (userInitiated) toast("Релиз GitHub не прошёл проверку APK"); }
                    else if (userInitiated) toast(newer ? "В релизе GitHub пока нет APK приложения" : "У вас установлена последняя версия");
                });
            } catch (Exception e) { if (userInitiated) runOnUiThread(() -> toast("Не удалось проверить GitHub: " + e.getMessage())); }
            finally { if (connection != null) connection.disconnect(); }
        });
    }

    private boolean isTrustedGitHubAssetUrl(String value) {
        try {
            URL url = new URL(value);
            String host = url.getHost().toLowerCase(Locale.US);
            return "https".equalsIgnoreCase(url.getProtocol()) && url.getUserInfo() == null &&
                    (host.equals("github.com") || host.equals("release-assets.githubusercontent.com") || host.equals("objects.githubusercontent.com"));
        } catch (Exception e) { return false; }
    }

    private boolean isSha256Digest(String value) { return value != null && value.matches("(?i)^sha256:[0-9a-f]{64}$"); }

    private void showGitHubUpdate(String version, String url, String digest) {
        AlertDialog updateDialog = new AlertDialog.Builder(this).setTitle("Доступно обновление " + version).setMessage("Новая версия «Вместе» опубликована в GitHub. Скачать APK и установить?")
                .setNegativeButton("Позже", null).setPositiveButton("Обновить", (d, w) -> {
                    if (android.os.Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                        prefs.edit().putString("pending_update_url", url).putString("pending_update_digest", digest).putString("pending_update_version", version).apply();
                        startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
                    } else downloadGitHubUpdate(url, digest, version);
                }).create();
        updateDialog.setOnShowListener(v -> {
            if (updateDialog.getWindow() != null) updateDialog.getWindow().setBackgroundDrawable(shape(PAPER, 24));
            int titleId = getResources().getIdentifier("alertTitle", "id", "android");
            TextView titleView = updateDialog.findViewById(titleId); if (titleView != null) titleView.setTextColor(INK);
            updateDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(PURPLE);
            updateDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(MUTED);
        });
        updateDialog.show();
    }

    private void downloadGitHubUpdate(String url, String digest, String expectedVersion) {
        toast("Скачиваем обновление из GitHub…");
        network.execute(() -> {
            HttpURLConnection connection = null;
            File apk = new File(getCacheDir(), "vmeste-update.apk");
            try {
                if (!isTrustedGitHubAssetUrl(url) || !isSha256Digest(digest)) throw new Exception("Ссылка или контрольная сумма релиза недействительна");
                connection = (HttpURLConnection) new URL(url).openConnection(); connection.setInstanceFollowRedirects(true); connection.setRequestProperty("User-Agent", "Vmeste-Android"); connection.setConnectTimeout(15000); connection.setReadTimeout(30000);
                if (connection.getResponseCode() != 200 || !isTrustedGitHubAssetUrl(connection.getURL().toString())) throw new Exception("Не удалось скачать APK из доверенного источника");
                long expectedLength = connection.getContentLengthLong();
                if (expectedLength > 150L * 1024 * 1024) throw new Exception("Размер APK превышает допустимый предел");
                long downloaded = 0;
                try (InputStream input = connection.getInputStream(); OutputStream output = new FileOutputStream(apk)) { byte[] buffer = new byte[16384]; int count; while ((count = input.read(buffer)) != -1) { downloaded += count; if (downloaded > 150L * 1024 * 1024) throw new Exception("Размер APK превышает допустимый предел"); output.write(buffer, 0, count); } }
                if (apk.length() < 100000) throw new Exception("Файл обновления слишком маленький");
                if (expectedLength >= 0 && downloaded != expectedLength) throw new Exception("Загрузка APK завершилась не полностью");
                MessageDigest sha = MessageDigest.getInstance("SHA-256"); try (InputStream input = new FileInputStream(apk)) { byte[] buffer = new byte[16384]; int count; while ((count = input.read(buffer)) != -1) sha.update(buffer, 0, count); } StringBuilder hex = new StringBuilder(); for (byte value : sha.digest()) hex.append(String.format(Locale.US, "%02x", value)); if (!digest.substring(7).equalsIgnoreCase(hex.toString())) throw new Exception("Проверка целостности APK не прошла");
                int signingFlags = android.os.Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
                PackageManager pm = getPackageManager(); PackageInfo info = pm.getPackageArchiveInfo(apk.getAbsolutePath(), signingFlags);
                if (info == null || !getPackageName().equals(info.packageName)) throw new Exception("Этот APK предназначен для другого приложения");
                PackageInfo installed = pm.getPackageInfo(getPackageName(), signingFlags);
                if (!ReleaseVersion.isValid(expectedVersion) || !expectedVersion.equals(info.versionName) || ReleaseVersion.compare(info.versionName, installed.versionName) <= 0) throw new Exception("Версия APK не совпадает с опубликованным релизом или не новее установленной");
                long installedCode = android.os.Build.VERSION.SDK_INT >= 28 ? installed.getLongVersionCode() : installed.versionCode;
                long updateCode = android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
                if (updateCode <= installedCode) throw new Exception("Номер сборки APK не новее установленной версии");
                if (!certificateDigests(installed).equals(certificateDigests(info))) throw new Exception("Подпись APK не совпадает с установленной версией");
                runOnUiThread(() -> installGitHubApk(apk));
            } catch (Exception e) { apk.delete(); runOnUiThread(() -> toast("Обновление не установлено: " + e.getMessage())); }
            finally { if (connection != null) connection.disconnect(); }
        });
    }

    private HashSet<String> certificateDigests(PackageInfo info) throws Exception {
        Signature[] signatures = android.os.Build.VERSION.SDK_INT >= 28
                ? info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners()
                : info.signatures;
        if (signatures == null || signatures.length == 0) throw new Exception("У APK отсутствует сертификат подписи");
        HashSet<String> result = new HashSet<>();
        for (Signature signature : signatures) {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
            StringBuilder hex = new StringBuilder();
            for (byte value : bytes) hex.append(String.format(Locale.US, "%02x", value));
            result.add(hex.toString());
        }
        return result;
    }

    private void installGitHubApk(File apk) {
        try {
            PackageInstaller installer = getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL); params.setAppPackageName(getPackageName());
            int sessionId = installer.createSession(params);
            try (PackageInstaller.Session session = installer.openSession(sessionId); InputStream input = new FileInputStream(apk); OutputStream output = session.openWrite("vmeste.apk", 0, apk.length())) {
                byte[] buffer = new byte[16384]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); session.fsync(output);
                Intent status = new Intent(this, InstallResultReceiver.class);
                int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT | (android.os.Build.VERSION.SDK_INT >= 23 ? android.app.PendingIntent.FLAG_IMMUTABLE : 0);
                session.commit(android.app.PendingIntent.getBroadcast(this, sessionId, status, flags).getIntentSender());
            }
            apk.delete();
        } catch (Exception e) { toast("Не удалось открыть установщик: " + e.getMessage()); }
    }

    private void persistProfile(JSONObject profile) {
        if (!sync.configured() || !sync.signedIn() || sync.coupleId().isEmpty()) { toast("Профиль сохранён на этом телефоне. Подключите пару для синхронизации."); return; }
        network.execute(() -> {
            try {
                JSONObject remote = new JSONObject(profile.toString());
                String localUri = remote.optString("avatar_local_uri", "");
                String avatar = remote.optString("avatar", "♡");
                String remotePath = prefs.getString("profile_avatar_remote_path", "");
                if (!localUri.isEmpty() && (prefs.getBoolean("profile_avatar_pending", false) || remotePath.isEmpty())) {
                    Uri uri = Uri.parse(localUri);
                    if (!"file".equals(uri.getScheme()) || !new File(uri.getPath()).isFile())
                        throw new Exception("Локальный файл фото недоступен");
                    remotePath = sync.uploadAttachment(uri, "profile-avatar-" + sync.userId(), this, "image/jpeg");
                    prefs.edit().putString("profile_avatar_remote_path", remotePath)
                            .putBoolean("profile_avatar_pending", false).apply();
                }
                remote.put("avatar", remotePath.isEmpty() ? avatar : remotePath);
                remote.remove("avatar_local_uri");
                sync.saveProfile(remote);
                prefs.edit().remove("profile_avatar_pending").apply();
                runOnUiThread(() -> {
                    lastProfileRefreshAt = 0;
                    refreshProfiles();
                    toast("Профиль синхронизирован с партнёром");
                });
            } catch (Exception e) {
                android.util.Log.w("VmesteProfile", "Could not sync profile", e);
                runOnUiThread(() -> toast("Профиль сохранён на телефоне, но синхронизация не удалась: " + e.getMessage()));
            }
        });
    }

    private void refreshProfiles() {
        if (!sync.configured() || !sync.signedIn() || sync.coupleId().isEmpty()) return;
        if(prefs.getBoolean("profile_avatar_pending",false)){
            try{
                JSONObject profile=new JSONObject();
                profile.put("display_name",prefs.getString("profile_name",""));
                profile.put("avatar",prefs.getString("profile_avatar","♡"));
                profile.put("avatar_local_uri",localAvatarUri());
                profile.put("bio",prefs.getString("profile_bio",""));
                persistProfile(profile);
            }catch(Exception e){android.util.Log.w("VmesteProfile","Could not retry pending profile photo sync",e);}
        }
        long now=System.currentTimeMillis();if(now-lastProfileRefreshAt<45000L)return;lastProfileRefreshAt=now;
        network.execute(()->{
            try{
                JSONArray rows=sync.fetchProfiles();String ownId=sync.userId();boolean changed=false;
                for(int i=0;i<rows.length();i++){
                    JSONObject row=rows.optJSONObject(i);if(row==null)continue;
                    String avatar=row.optString("avatar","♡");boolean hasPhoto=avatar.startsWith("data:image/");
                    if(ownId.equals(row.optString("user_id"))){
                        changed|=putProfileValue("profile_name",row.optString("display_name","").trim());
                        if(!prefs.getBoolean("profile_avatar_pending",false)){
                            if(isRemoteAvatarPath(avatar)){
                                try{changed|=cacheRemoteAvatar(avatar,true);}catch(Exception e){android.util.Log.w("VmesteProfile","Could not load own profile photo",e);}
                            }else if(hasPhoto){
                                changed|=putProfileValue("profile_avatar","♡");
                                changed|=putProfileValue("profile_avatar_data",avatar);
                                changed|=putProfileValue("profile_avatar_uri","");
                            }else{
                                changed|=putProfileValue("profile_avatar",avatar);
                                changed|=putProfileValue("profile_avatar_data","");
                                changed|=putProfileValue("profile_avatar_uri","");
                                changed|=putProfileValue("profile_avatar_remote_path","");
                            }
                        }
                        changed|=putProfileValue("profile_bio",row.optString("bio",""));
                    }else{
                        changed|=putProfileValue("partner_name",row.optString("display_name","").trim());
                        if(isRemoteAvatarPath(avatar)){
                            try{changed|=cacheRemoteAvatar(avatar,false);}catch(Exception e){android.util.Log.w("VmesteProfile","Could not load partner profile photo",e);}
                        }else{
                            changed|=putProfileValue("partner_avatar",hasPhoto?"♡":avatar);
                            changed|=putProfileValue("partner_avatar_data",hasPhoto?avatar:"");
                            changed|=putProfileValue("partner_avatar_remote_path","");
                        }
                        changed|=putProfileValue("partner_user_id",row.optString("user_id"));
                        changed|=putProfileValue("partner_bio",row.optString("bio",""));
                    }
                }
                if(changed)runOnUiThread(()->{if(body!=null&&!isFinishing())showApp();});
            }catch(Exception e){android.util.Log.w("VmesteProfile","Could not refresh couple profiles",e);}
        });
    }

    private boolean putProfileValue(String key,String value){
        String next=value==null?"":value;if(next.equals(prefs.getString(key,"")))return false;
        prefs.edit().putString(key,next).apply();return true;
    }

    private String localAvatarUri(){
        String uri=prefs.getString("profile_avatar_uri","");
        if(!uri.isEmpty())return uri;
        String legacy=prefs.getString("profile_avatar_data","");
        if(!legacy.startsWith("data:image/"))return "";
        try{
            byte[] bytes=android.util.Base64.decode(legacy.substring(legacy.indexOf(',')+1),android.util.Base64.DEFAULT);
            File file=new File(getFilesDir(),"profile-avatar.jpg");
            try(FileOutputStream output=new FileOutputStream(file)){output.write(bytes);}
            uri=Uri.fromFile(file).toString();
            prefs.edit().putString("profile_avatar_uri",uri).remove("profile_avatar_data").apply();
            return uri;
        }catch(Exception e){android.util.Log.w("VmesteProfile","Could not migrate local profile photo",e);return "";}
    }

    private boolean isRemoteAvatarPath(String value){
        return value!=null&&!value.isEmpty()&&!value.startsWith("data:image/")&&!value.startsWith("file:")
                &&value.startsWith(sync.coupleId()+"/");
    }

    private boolean cacheRemoteAvatar(String remotePath,boolean own)throws Exception{
        String pathKey=own?"profile_avatar_remote_path":"partner_avatar_remote_path";
        String uriKey=own?"profile_avatar_uri":"partner_avatar_uri";
        String dataKey=own?"profile_avatar_data":"partner_avatar_data";
        String fileName=own?"profile-avatar.jpg":"partner-avatar.jpg";
        File file=new File(getFilesDir(),fileName);
        boolean same=remotePath.equals(prefs.getString(pathKey,""));
        boolean fileExists=file.isFile();
        long fileLength=fileExists?file.length():0;
        boolean decodable=fileExists&&fileLength>0&&isAvatarFileDecodable(file);
        if(AvatarCachePolicy.shouldDownload(same,fileExists,fileLength,decodable)){
            byte[] bytes=sync.downloadAttachment(remotePath);
            if(!isAvatarImageDecodable(bytes))throw new Exception("Сервер вернул повреждённый файл фото");
            File temporary=new File(getFilesDir(),fileName+".tmp");
            try(FileOutputStream output=new FileOutputStream(temporary)){output.write(bytes);output.flush();}
            if(!isAvatarFileDecodable(temporary)){temporary.delete();throw new Exception("Не удалось проверить миниатюру профиля");}
            if(file.exists()&&!file.delete())throw new Exception("Не удалось заменить локальную миниатюру");
            if(!temporary.renameTo(file)){temporary.delete();throw new Exception("Не удалось сохранить миниатюру профиля");}
        }
        boolean changed=putProfileValue(pathKey,remotePath);
        changed|=putProfileValue(uriKey,Uri.fromFile(file).toString());
        changed|=putProfileValue(dataKey,"");
        if(own&&prefs.getString("profile_avatar","").isEmpty())changed|=putProfileValue("profile_avatar","♡");
        else if(!own&&prefs.getString("partner_avatar","").isEmpty())changed|=putProfileValue("partner_avatar","♡");
        return changed;
    }

    private boolean isAvatarFileDecodable(File file){
        if(file==null||!file.isFile()||file.length()==0)return false;
        android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);
        if(bounds.outWidth<1||bounds.outHeight<1)return false;
        android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();
        options.inSampleSize=avatarSampleSize(bounds.outWidth,bounds.outHeight);
        android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(),options);
        if(bitmap==null)return false;
        bitmap.recycle();
        return true;
    }

    private boolean isAvatarImageDecodable(byte[] bytes){
        if(bytes==null||bytes.length==0)return false;
        android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(bounds.outWidth<1||bounds.outHeight<1)return false;
        android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();
        options.inSampleSize=avatarSampleSize(bounds.outWidth,bounds.outHeight);
        android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(bitmap==null)return false;
        bitmap.recycle();
        return true;
    }

    private int avatarSampleSize(int width,int height){
        int sample=1;
        while(width/sample>512||height/sample>512)sample*=2;
        return sample;
    }

    private JSONObject readMovieRoom() { try { String raw = prefs.getString("movie_room", ""); return raw.isEmpty() ? null : new JSONObject(raw); } catch (Exception e) { return null; } }
    private void editMovieRoom(JSONObject existing) {
        LinearLayout form = column(); form.setPadding(dp(18), 0, dp(18), 0);
        EditText title = new EditText(this); title.setSingleLine(true); title.setHint("Название фильма");
        EditText url = new EditText(this); url.setSingleLine(true); url.setHint("YouTube или публичное видео MP4/HLS"); url.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        if (existing != null) { title.setText(existing.optString("title")); url.setText(existing.optString("url")); }
        form.addView(title); form.addView(url);
        new AlertDialog.Builder(this).setTitle("Кино-комната").setMessage("В общей комнате синхронизируются YouTube и публичные HTTPS-видео MP4/HLS. Закрытые и DRM-плееры не обходятся и могут не поддерживать синхронизацию.")
                .setView(form).setNegativeButton("Отмена", null).setPositiveButton("Сохранить", (d, w) -> {
                    String name = title.getText().toString().trim(), link = url.getText().toString().trim();
                    if (name.isEmpty()) name = "Вечер кино";
                    if (!link.isEmpty() && !link.startsWith("https://")) { toast("Используйте безопасную HTTPS-ссылку"); return; }
                    JSONObject room = existing == null ? new JSONObject() : existing;
                    try { if (!room.has("id")) room.put("id", UUID.randomUUID().toString()); room.put("title", name); room.put("url", link); room.put("status", "ready"); room.put("updated", System.currentTimeMillis()); prefs.edit().putString("movie_room", room.toString()).apply(); } catch (Exception ignored) { }
                    persistMovieRoom(room); render();
                }).show();
    }

    private void updateMovieRoom(String status) {
        JSONObject room = readMovieRoom(); if (room == null) return;
        try { room.put("status", status); room.put("updated", System.currentTimeMillis()); prefs.edit().putString("movie_room", room.toString()).apply(); } catch (Exception ignored) { }
        persistMovieRoom(room); render();
    }

    private void persistMovieRoom(JSONObject room) {
        if (!sync.configured() || !sync.signedIn() || sync.coupleId().isEmpty()) { toast("Комната сохранена на телефоне. Подключите партнёра в разделе «Пригласить партнёра» для синхронизации."); return; }
        runNetwork(() -> { sync.upsertMovieRoom(room); return "ok"; }, result -> toast("Кино-комната обновлена для вас двоих"));
    }

    private void fetchMovieRoom() {
        if (!sync.configured() || !sync.signedIn() || sync.coupleId().isEmpty()) { toast("Сначала подключите общее пространство в разделе «Пригласить партнёра»"); return; }
        runNetwork(() -> sync.fetchMovieRoom(), room -> {
            if (room == null) toast("Общей кино-комнаты пока нет — создайте её на этом телефоне");
            else { prefs.edit().putString("movie_room", room.toString()).apply(); render(); toast("Комната обновлена"); }
        });
    }

    private String statusText(String status) {
        if ("playing".equals(status)) return "Сейчас смотрите вместе";
        if ("paused".equals(status)) return "Пауза";
        if ("finished".equals(status)) return "Просмотр завершён";
        return "Готовы к просмотру";
    }

    private void openUrl(String value) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(value))); }
        catch (Exception e) { toast("Не удалось открыть ссылку"); }
    }

    private void openSharedLink(String link) {
        Uri uri=Uri.parse(link);
        String scheme=uri.getScheme();
        if(uri.getHost()==null||!("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme))){
            toast("Можно открыть только обычные web-ссылки");return;
        }
        android.webkit.WebView web=new android.webkit.WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setAllowFileAccessFromFileURLs(false);
        web.getSettings().setAllowUniversalAccessFromFileURLs(false);
        if(android.os.Build.VERSION.SDK_INT>=26)web.getSettings().setSafeBrowsingEnabled(true);
        web.setWebViewClient(new android.webkit.WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(android.webkit.WebView view,String url){
                Uri target=Uri.parse(url);String targetScheme=target.getScheme();
                return target.getHost()==null||!("https".equalsIgnoreCase(targetScheme)||"http".equalsIgnoreCase(targetScheme));
            }
        });
        new AlertDialog.Builder(this).setTitle("Публикация партнёра").setView(web)
                .setNegativeButton("Закрыть",null)
                .setPositiveButton("Открыть в браузере",(d,w)->openUrl(link)).show();
        web.loadUrl(link);
    }

    private void showGiftPreview(String link) {
        String safeUrl = GiftPreviewUrl.normalizePublicHttpUrl(link);
        if (safeUrl.isEmpty()) {
            toast("Можно просматривать только публичные ссылки http/https");
            return;
        }

        LinearLayout content = column();
        TextView notice = text("Предпросмотр откроется внутри приложения. Некоторые сайты требуют входа или могут ограничивать показ контента; мы не обходим такие ограничения.", 12, MUTED, false);
        notice.setPadding(dp(16), dp(10), dp(16), dp(10));
        content.addView(notice);
        TextView status = text(Uri.parse(safeUrl).getHost(), 12, MUTED, false);
        status.setPadding(dp(16), 0, dp(16), dp(8));
        content.addView(status);

        android.webkit.WebView web = new android.webkit.WebView(this);
        android.webkit.WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (android.os.Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        int previewHeight = Math.max(dp(200), Math.min(dp(560), getResources().getDisplayMetrics().heightPixels * 2 / 3 - dp(130)));
        content.addView(web, new LinearLayout.LayoutParams(-1, previewHeight));
        web.setWebViewClient(new android.webkit.WebViewClient() {
            private final java.util.concurrent.atomic.AtomicBoolean blockedNoticeShown =
                    new java.util.concurrent.atomic.AtomicBoolean();

            private boolean isAllowed(String url) {
                return !GiftPreviewUrl.normalizePublicHttpUrl(url).isEmpty();
            }

            private void blockUrl(String url) {
                android.util.Log.i("VmesteGift", "Blocked non-public preview URL: " + Uri.parse(url).getScheme());
                toast("Эта ссылка не поддерживается в предпросмотре");
            }

            @Override public boolean shouldOverrideUrlLoading(android.webkit.WebView view, String url) {
                if (isAllowed(url)) return false;
                blockUrl(url);
                return true;
            }

            @Override public boolean shouldOverrideUrlLoading(android.webkit.WebView view, android.webkit.WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (isAllowed(url)) return false;
                blockUrl(url);
                return true;
            }

            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(android.webkit.WebView view, String url) {
                if (GiftPreviewUrl.resolvesToPublicAddresses(url)) return null;
                return blockedResponse();
            }

            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(android.webkit.WebView view, android.webkit.WebResourceRequest request) {
                if (GiftPreviewUrl.resolvesToPublicAddresses(request.getUrl().toString())) return null;
                return blockedResponse();
            }

            @Override public void onPageStarted(android.webkit.WebView view, String url, android.graphics.Bitmap favicon) {
                String host = Uri.parse(url).getHost();
                status.setText(host == null ? "Предпросмотр" : host);
            }

            @Override public void onReceivedError(android.webkit.WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) status.setText("Страница недоступна. Проверьте подключение или откройте другую ссылку.");
            }

            @Override public void onReceivedHttpError(android.webkit.WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceResponse errorResponse) {
                if (request.isForMainFrame() && errorResponse.getStatusCode() >= 400) {
                    status.setText("Сайт ограничил доступ или страница недоступна (HTTP "
                            + errorResponse.getStatusCode() + "). Ограничения не обходятся.");
                }
            }

            private android.webkit.WebResourceResponse blockedResponse() {
                if (blockedNoticeShown.compareAndSet(false, true)) {
                    runOnUiThread(() -> status.setText("Запрос на локальный или неподдерживаемый адрес заблокирован."));
                }
                return new android.webkit.WebResourceResponse("text/plain", "UTF-8",
                        new java.io.ByteArrayInputStream(new byte[0]));
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Предпросмотр подарка")
                .setView(content)
                .setNegativeButton("Закрыть", null)
                .setNeutralButton("Обновить", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> web.reload()));
        dialog.setOnDismissListener(ignored -> {
            web.stopLoading();
            web.loadUrl("about:blank");
            web.destroy();
        });
        dialog.show();
        web.loadUrl(safeUrl);
    }

    private void shareText(String value) {
        Intent send = new Intent(Intent.ACTION_SEND); send.setType("text/plain"); send.putExtra(Intent.EXTRA_TEXT, value);
        startActivity(Intent.createChooser(send, "Поделиться"));
    }

    private interface NetworkWork { Object run() throws Exception; }
    private interface NetworkDone<T> { void done(T result); }
    private interface NetworkError { void failed(Exception error); }
    @SuppressWarnings("unchecked") private <T> void runNetwork(NetworkWork work, NetworkDone<T> done) {
        runNetwork(work, done, error -> toast("Не удалось выполнить действие: " + error.getMessage()));
    }
    @SuppressWarnings("unchecked") private <T> void runNetwork(NetworkWork work, NetworkDone<T> done, NetworkError onError) {
        Toast.makeText(this, "Подключаемся…", Toast.LENGTH_SHORT).show();
        network.execute(() -> {
            try { Object value = work.run(); runOnUiThread(() -> done.done((T) value)); }
            catch (Exception e) { runOnUiThread(() -> onError.failed(e)); }
        });
    }

    private void syncItems() {
        runNetwork(() -> { org.json.JSONArray rows = sync.fetchItems(); for (int i=0;i<rows.length();i++) sync.storeRemote(rows.getJSONObject(i), prefs); return rows.length(); }, count -> { render(); toast("Получено записей: " + count); });
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private void safeUi(Runnable r){if(isFinishing()||isDestroyed())return;runOnUiThread(()->{try{r.run();}catch(Exception e){if(!isFinishing())toast("Ошибка: "+e.getMessage());}});}

    private void open(String page) { feature = page; render(); }

    private void gameCard(String title, String q, Runnable action) {
        simpleCard("🎲", title, q, action);
    }
    private JSONObject openOrJoinGame(String type) throws Exception { return openOrJoinGame(type, null); }
    private JSONObject openOrJoinGame(String type,String question) throws Exception {
        if (!pairGames.ready()) throw new Exception("Войдите в свой аккаунт и сначала подключитесь к паре по коду приглашения");
        JSONObject game=pairGames.latest(type);
        if(game==null){
            JSONObject state=new JSONObject();state.put("p1",pairGames.userId());state.put("p2","");
            if("tic_tac_toe".equals(type)){state.put("board",".........");state.put("turn",pairGames.userId());state.put("winner","");}
            if("connect_four".equals(type)){state.put("board",emptyBoard(42));state.put("turn",pairGames.userId());state.put("winner","");}
            if("dots_boxes".equals(type)){state.put("edges",emptyBoard(40));state.put("boxes",emptyBoard(16));state.put("score1",0);state.put("score2",0);state.put("turn",pairGames.userId());state.put("winner","");}
            if("checkers".equals(type)){state.put("board",initialCheckersBoard());state.put("turn",pairGames.userId());state.put("winner","");state.put("chain",-1);}
            if("sea_battle".equals(type)){state.put("p1Fleet",randomFleet());state.put("p2Fleet",new JSONArray());state.put("p1Ready",false);state.put("p2Ready",false);state.put("p1Shots",new JSONArray());state.put("p2Shots",new JSONArray());state.put("turn","");state.put("winner","");}
            if("durak".equals(type)){JSONArray deck=new JSONArray(),h1=new JSONArray(),h2=new JSONArray();ArrayList<Integer> cards=new ArrayList<>();for(int i=0;i<36;i++)cards.add(i);java.util.Collections.shuffle(cards);for(int i=0;i<36;i++){int card=cards.get(i);if(i<6)h1.put(card);else if(i<12)h2.put(card);else deck.put(card);}state.put("p1Hand",h1);state.put("p2Hand",h2);state.put("deck",deck);state.put("trump",cards.get(35)%4);state.put("attacker",pairGames.userId());state.put("attack",-1);state.put("defense",-1);state.put("winner","");}
            if("guess_tune".equals(type)){String tune=TUNES[new Random().nextInt(TUNES.length)];state.put("prompt","Напойте мелодию: "+tune);state.put("correct",tune);state.put("p1Ready",false);state.put("p2Answer","");state.put("winner","");}
            if("couple_quiz".equals(type)){state.put("prompt","Вопрос для двоих");state.put("question",question==null?"Какой мой идеальный выходной?":question);state.put("p1Answer","");state.put("p2Answer","");}
            String id=UUID.randomUUID().toString();
            try{pairGames.create(id,type,state);}catch(Exception race){game=pairGames.latest(type);if(game==null)throw race;}
            if(game==null)game=pairGames.fetch(id);
        }
        if(game==null)throw new Exception("Не удалось создать игровую комнату");
        JSONObject state=game.optJSONObject("state");if(state==null)state=new JSONObject();
        String me=pairGames.userId(),p1=state.optString("p1"),p2=state.optString("p2");
        if(!me.equals(p1)&&!me.equals(p2)){
            if(("waiting".equals(game.optString("status"))||"active".equals(game.optString("status")))&&p2.isEmpty()){
                state.put("p2",me);
                if("sea_battle".equals(type))state.put("p2Fleet",randomFleet());
                else if(isRandomTurnGame(type))setRandomFirstTurn(state,type);
                String next="sea_battle".equals(type)?"waiting":"active";
                game=pairGames.update(game.getString("id"),game.optLong("version"),state,next);
            }else throw new Exception("Идёт другая партия; её можно открыть только в аккаунте вашего партнёра");
        }
        if(!"sea_battle".equals(type)&&!state.optString("p2").isEmpty()
                &&isRandomTurnGame(type)&&state.optString("first_player").isEmpty()){
            if(isGameAtInitialPosition(type,state,p1))setRandomFirstTurn(state,type);
            else state.put("first_player",p1);
            try{game=pairGames.update(game.getString("id"),game.optLong("version"),state,game.optString("status"));}
            catch(Exception race){
                JSONObject latest=pairGames.fetch(game.getString("id"));
                JSONObject latestState=latest==null?null:latest.optJSONObject("state");
                if(latestState==null||latestState.optString("first_player").isEmpty())throw race;
                game=latest;
            }
        }
        return game;
    }
    private boolean isGameAtInitialPosition(String type,JSONObject state,String playerOne){
        if("tic_tac_toe".equals(type))return ".........".equals(state.optString("board"));
        if("connect_four".equals(type))return emptyBoard(42).equals(state.optString("board"));
        if("checkers".equals(type))return initialCheckersBoard().equals(state.optString("board"));
        if("dots_boxes".equals(type))return emptyBoard(40).equals(state.optString("edges"));
        if("durak".equals(type))return state.optInt("attack",-1)<0
                &&playerOne.equals(state.optString("attacker"));
        return false;
    }
    private boolean isRandomTurnGame(String type){
        return "tic_tac_toe".equals(type)||"connect_four".equals(type)||"checkers".equals(type)
                ||"dots_boxes".equals(type)||"durak".equals(type);
    }
    private void setRandomFirstTurn(JSONObject state,String type)throws Exception{
        boolean previouslySelected=!state.optString("first_player").isEmpty();
        String first=GameStartRules.chooseOrKeepFirstPlayer(
                state.optString("p1"),state.optString("p2"),
                state.optString("first_player"),new Random());
        state.put("first_player",first);
        if(state.has("turn"))state.put("turn",first);
        if("durak".equals(type))state.put("attacker",first);
        if("checkers".equals(type)&&!previouslySelected){
            String playerOne=state.optString("p1");
            if(!first.equals(playerOne))
                state.put("board",BoardGameRules.swapCheckersColors(state.optString("board")));
        }
    }
    private void startNextGame(String type,JSONObject[] current,Runnable draw){
        network.execute(()->{
            try{
                JSONObject previous=pairGames.fetch(current[0].getString("id"));
                if(previous==null||!"finished".equals(previous.optString("status")))
                    throw new Exception("Новая партия доступна после завершения текущей");
                JSONObject state=previous.optJSONObject("state");
                String me=pairGames.userId();
                if(state==null||(!me.equals(state.optString("p1"))&&!me.equals(state.optString("p2"))))
                    throw new Exception("Вы не участник этой партии");
                if(state.optString("p2").isEmpty())
                    throw new Exception("Для новой партии дождитесь подключения партнёра");
                JSONObject next=openOrJoinGame(type);
                runOnUiThread(()->{current[0]=next;draw.run();});
            }catch(Exception e){runOnUiThread(()->toast("Не удалось начать новую партию: "+e.getMessage()));}
        });
    }
    private JSONArray randomFleet(){
        JSONArray fleet=new JSONArray();java.util.HashSet<Integer> occupied=new java.util.HashSet<>();Random random=new Random();int[] lengths={4,3,3,2,1,1,1,1,1,1,1,1};
        for(int len:lengths){boolean placed=false;for(int tries=0;tries<2000&&!placed;tries++){boolean horizontal=random.nextBoolean();int row=random.nextInt(10),col=random.nextInt(10);if(horizontal&&col+len>10||!horizontal&&row+len>10)continue;ArrayList<Integer> cells=new ArrayList<>();boolean clear=true;for(int k=0;k<len;k++){int r=row+(horizontal?0:k),c=col+(horizontal?k:0),p=r*10+c;cells.add(p);for(int dr=-1;dr<=1;dr++)for(int dc=-1;dc<=1;dc++){int nr=r+dr,nc=c+dc;if(nr>=0&&nr<10&&nc>=0&&nc<10&&occupied.contains(nr*10+nc))clear=false;}}if(clear){JSONArray ship=new JSONArray();for(int p:cells){occupied.add(p);ship.put(p);}fleet.put(ship);placed=true;}}
        if(!placed)return randomFleet();
        }return fleet;
    }
    private String emptyBoard(int size) { char[] cells=new char[size];java.util.Arrays.fill(cells,'.');return new String(cells); }
    private String initialCheckersBoard() { char[] cells=emptyBoard(64).toCharArray();for(int r=0;r<3;r++)for(int c=0;c<8;c++)if((r+c)%2==1)cells[r*8+c]='b';for(int r=5;r<8;r++)for(int c=0;c<8;c++)if((r+c)%2==1)cells[r*8+c]='r';return new String(cells); }
    private void renderGameRating() {
        LinearLayout rating=card();rating.setBackground(shape(softDark?0xFF403648:PALE,20));rating.addView(text("🏆  Счёт ваших партий",16,INK,true));
        TextView score=text("Считаем победы…",13,MUTED,false);score.setPadding(0,dp(5),0,0);rating.addView(score);body.addView(rating);
        if(!pairGames.ready()){score.setText("Подключите пару, чтобы вести общий счёт");return;}
        score.setOnClickListener(v->render());
        network.execute(()->{
            try{
                JSONArray games=pairGames.history();
                ArrayList<GameScoreRules.Outcome> outcomes=new ArrayList<>();
                for(int i=0;i<games.length();i++){
                    JSONObject row=games.optJSONObject(i);
                    JSONObject state=row==null?null:row.optJSONObject("state");
                    if(state==null)continue;
                    outcomes.add(new GameScoreRules.Outcome(state.optString("winner"),
                            state.optString("p1"),state.optString("p2")));
                }
                GameScoreRules.Summary summary=GameScoreRules.summarize(outcomes,
                        pairGames.userId(),prefs.getString("partner_user_id",""));
                String result=prefs.getString("profile_name","Вы")+"  "+summary.playerWins
                        +"  ·  "+prefs.getString("partner_name","Партнёр")+"  "+summary.partnerWins
                        +"  ·  ничьи "+summary.draws;
                runOnUiThread(()->{if(!isFinishing())score.setText(result);});
            }catch(Exception e){
                runOnUiThread(()->{
                    if(isFinishing())return;
                    score.setText("Не удалось обновить общий счёт · нажмите, чтобы повторить");
                    score.setOnClickListener(v->render());
                });
            }
        });
    }

    private void onlineTicTacToe(){
        if(!pairGames.ready()){toast("Войдите в аккаунт и подключитесь к партнёру по коду приглашения");return;}
        toast("Подключаем партию…");network.execute(()->{try{JSONObject g=openOrJoinGame("tic_tac_toe");safeUi(()->showOnlineTicTacToe(g));}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});
    }
    private void showOnlineTicTacToe(JSONObject initial){
        final JSONObject[] game={initial};final boolean[] closed={false};final Runnable[] poll={null};
        LinearLayout panel=column();panel.setPadding(dp(14),dp(8),dp(14),dp(8));
        TextView status=text("",14,INK,true);status.setGravity(Gravity.CENTER);status.setPadding(dp(10),dp(10),dp(10),dp(10));status.setBackground(shape(PALE,15));panel.addView(status);
        LinearLayout board=column();board.setGravity(Gravity.CENTER);board.setPadding(dp(8),dp(8),dp(8),dp(8));board.setBackground(shape(0xFFEDE4F1,20));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(10);panel.addView(board,bp);
        TextView restart=pill("Новая партия",PURPLE);restart.setGravity(Gravity.CENTER);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(10);panel.addView(restart,rp);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Крестики-нолики · онлайн").setView(panel).setNegativeButton("Закрыть",null).create();
        Runnable[] draw={null};draw[0]=()->{JSONObject g=game[0],s=g.optJSONObject("state");if(s==null)return;String me=pairGames.userId(),p1=s.optString("p1"),p2=s.optString("p2"),boardState=s.optString("board","........."),turn=s.optString("turn"),winner=s.optString("winner");boolean active="active".equals(g.optString("status"));char myMark=GameStartRules.ticTacToeMarkForPlayer(me,s.optString("first_player"),p1),partnerMark=myMark=='X'?'O':'X';
            if(p2.isEmpty())status.setText("Комната создана. Ждём, пока партнёр откроет эту игру на своём телефоне.");else if(!winner.isEmpty())status.setText(winner.equals(me)?"Вы победили! ✨":"Партнёр победил. Сыграем ещё? 💛");else if(!active)status.setText("Партия завершена");else status.setText(turn.equals(me)?"Ваш ход · "+myMark:"Ход партнёра · "+partnerMark);
            restart.setVisibility(active||p2.isEmpty()?View.GONE:View.VISIBLE);
            board.removeAllViews();for(int row=0;row<3;row++){LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER);board.addView(line);for(int col=0;col<3;col++){int i=row*3+col;char mark=i<boardState.length()?boardState.charAt(i):'.';TextView cell=text(mark=='.'?"":String.valueOf(mark),31,mark=='X'?0xFF71529B:0xFFD66D92,true);cell.setGravity(Gravity.CENTER);cell.setBackground(shape(0xFFFBF9FC,14));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(70),1);cp.setMargins(dp(3),dp(3),dp(3),dp(3));line.addView(cell,cp);cell.setEnabled(active&&turn.equals(me)&&mark=='.'&&winner.isEmpty());cell.setOnClickListener(v->{network.execute(()->{try{JSONObject fresh=pairGames.fetch(g.getString("id"));if(fresh==null)return;JSONObject ns=new JSONObject(fresh.getJSONObject("state").toString());if(!pairGames.userId().equals(ns.optString("turn")))return;String b=ns.optString("board",".........");if(b.charAt(i)!='.')return;char player=GameStartRules.ticTacToeMarkForPlayer(me,ns.optString("first_player"),ns.optString("p1"));StringBuilder sb=new StringBuilder(b);sb.setCharAt(i,player);ns.put("board",sb.toString());boolean won=hasWinner(sb.toString(),player);boolean full=sb.indexOf(".")<0;if(won)ns.put("winner",me);else if(!full)ns.put("turn",me.equals(ns.optString("p1"))?ns.optString("p2"):ns.optString("p1"));String newStatus=won||full?"finished":"active";JSONObject saved=pairGames.update(fresh.getString("id"),fresh.optLong("version"),ns,newStatus);runOnUiThread(()->{try{game[0]=saved;draw[0].run();}catch(Exception e){toast("Ошибка игры: "+e.getMessage());}});}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});});}}
        };
        restart.setOnClickListener(v->startNextGame("tic_tac_toe",game,draw[0]));
        draw[0].run();dialog.setOnDismissListener(d->{closed[0]=true;});dialog.show();poll[0]=()->{if(closed[0])return;network.execute(()->{try{JSONObject fresh=pairGames.fetch(game[0].getString("id"));if(fresh!=null)runOnUiThread(()->{try{game[0]=fresh;draw[0].run();}catch(Exception ignored){}});}catch(Exception ignored){}if(!closed[0])gameHandler.postDelayed(poll[0],1400);});};gameHandler.postDelayed(poll[0],1400);
    }
    private boolean hasWinner(String board,char p){int[][] lines={{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};for(int[] l:lines)if(board.charAt(l[0])==p&&board.charAt(l[1])==p&&board.charAt(l[2])==p)return true;return false;}
    private void onlineSeaBattle(){
        if(!pairGames.ready()){toast("Войдите в аккаунт и подключитесь к партнёру по коду приглашения");return;}
        toast("Подключаем морской бой…");network.execute(()->{try{JSONObject g=openOrJoinGame("sea_battle");safeUi(()->showOnlineSeaBattle(g));}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});
    }
    private void showOnlineSeaBattle(JSONObject initial){
        final JSONObject[] game={initial};final boolean[] closed={false};final Runnable[] poll={null};
        final boolean[] ownBoardSelected={true},manualMode={false},horizontal={true};
        final JSONArray[] manualFleet={new JSONArray()};
        LinearLayout panel=column();panel.setPadding(dp(8),dp(6),dp(8),dp(6));
        TextView status=text("",13,INK,true);status.setGravity(Gravity.CENTER);status.setPadding(dp(8),dp(8),dp(8),dp(8));status.setBackground(shape(PALE,14));panel.addView(status);
        LinearLayout boardTabs=new LinearLayout(this);boardTabs.setGravity(Gravity.CENTER);panel.addView(boardTabs);
        TextView myFleetTab=pill("Мой флот",PURPLE);myFleetTab.setGravity(Gravity.CENTER);boardTabs.addView(myFleetTab,new LinearLayout.LayoutParams(0,dp(42),1));
        TextView enemyTab=pill("Поле партнёра",PURPLE);enemyTab.setGravity(Gravity.CENTER);boardTabs.addView(enemyTab,new LinearLayout.LayoutParams(0,dp(42),1));
        LinearLayout board=column();board.setPadding(dp(3),dp(3),dp(3),dp(3));board.setBackground(shape(0xFFDCECF1,16));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(7);panel.addView(board,bp);
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=dp(6);panel.addView(actions,ap);
        TextView shuffle=pill("↻  Переставить",PURPLE);shuffle.setGravity(Gravity.CENTER);actions.addView(shuffle,new LinearLayout.LayoutParams(0,dp(44),1));
        TextView manual=pill("✎ Вручную",PURPLE);manual.setGravity(Gravity.CENTER);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,dp(44),1);mp.leftMargin=dp(5);actions.addView(manual,mp);
        TextView rotate=pill("↻",PURPLE);rotate.setGravity(Gravity.CENTER);actions.addView(rotate,new LinearLayout.LayoutParams(dp(42),dp(44)));
        TextView undo=pill("↶",PURPLE);undo.setGravity(Gravity.CENTER);actions.addView(undo,new LinearLayout.LayoutParams(dp(42),dp(44)));
        TextView ready=pill("Готов",PURPLE);ready.setGravity(Gravity.CENTER);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(44),1);rp.leftMargin=dp(5);actions.addView(ready,rp);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Морской бой · онлайн").setView(panel).setNegativeButton("Закрыть",null).create();
        Runnable[] draw={null};draw[0]=()->{JSONObject g=game[0],s=g.optJSONObject("state");if(s==null)return;String me=pairGames.userId(),p1=s.optString("p1"),p2=s.optString("p2"),winner=s.optString("winner"),turn=s.optString("turn");boolean first=me.equals(p1);String fleetKey=first?"p1Fleet":"p2Fleet",shotsKey=first?"p1Shots":"p2Shots",readyKey=first?"p1Ready":"p2Ready";JSONArray own=s.optJSONArray(fleetKey);if(own==null)own=new JSONArray();JSONArray shots=s.optJSONArray(shotsKey);if(shots==null)shots=new JSONArray();boolean bothReady=s.optBoolean("p1Ready")&&s.optBoolean("p2Ready"),active="active".equals(g.optString("status"));
            if(p2.isEmpty())status.setText("Расставьте флот. Ждём, когда партнёр войдёт в игру со своего телефона.");else if(!winner.isEmpty())status.setText(winner.equals(me)?"Вы победили! ⚓":"Партнёр нашёл ваш флот. Сыграем ещё? 💛");else if(!bothReady)status.setText("Поставьте флот и нажмите «Готов». Игра начнётся, когда будут готовы оба.");else if(turn.equals(me))status.setText("Ваш ход · выберите клетку на поле партнёра");else status.setText("Ход партнёра · ваш флот пока скрыт");
            boolean setup=!bothReady&&winner.isEmpty();shuffle.setVisibility(setup?View.VISIBLE:View.GONE);manual.setVisibility(setup?View.VISIBLE:View.GONE);rotate.setVisibility(setup&&manualMode[0]?View.VISIBLE:View.GONE);undo.setVisibility(setup&&manualMode[0]?View.VISIBLE:View.GONE);ready.setVisibility(setup?View.VISIBLE:View.GONE);ready.setText(s.optBoolean(readyKey)?"Вы готовы ✓":"Готов");
            boardTabs.setVisibility(setup?View.GONE:View.VISIBLE);enemyTab.setEnabled(bothReady);myFleetTab.setBackground(shape(ownBoardSelected[0]?0xFFDCC9E9:PALE,18));enemyTab.setBackground(shape(!ownBoardSelected[0]?0xFFDCC9E9:PALE,18));
            board.removeAllViews();JSONArray shownOwn=manualMode[0]&&setup?manualFleet[0]:own;HashSet<Integer> ownCells=new HashSet<>();for(int j=0;j<shownOwn.length();j++){JSONArray ship=shownOwn.optJSONArray(j);if(ship!=null)for(int k=0;k<ship.length();k++)ownCells.add(ship.optInt(k,-1));}JSONArray enemyFleet=s.optJSONArray(first?"p2Fleet":"p1Fleet");if(enemyFleet==null)enemyFleet=new JSONArray();HashSet<Integer> enemyCells=new HashSet<>();for(int j=0;j<enemyFleet.length();j++){JSONArray ship=enemyFleet.optJSONArray(j);if(ship!=null)for(int k=0;k<ship.length();k++)enemyCells.add(ship.optInt(k,-1));}
            boolean showOwn=setup||ownBoardSelected[0];JSONArray shownShots=showOwn?s.optJSONArray(first?"p2Shots":"p1Shots"):shots;if(shownShots==null)shownShots=new JSONArray();HashSet<Integer> displayedShips=showOwn?ownCells:enemyCells;
            LinearLayout coordinates=new LinearLayout(this);coordinates.setGravity(Gravity.CENTER_VERTICAL);board.addView(coordinates);
            TextView corner=text("",10,MUTED,true);coordinates.addView(corner,new LinearLayout.LayoutParams(dp(22),dp(22)));
            for(int col=0;col<10;col++){TextView label=text(String.valueOf((char)('A'+col)),10,INK,true);label.setGravity(Gravity.CENTER);coordinates.addView(label,new LinearLayout.LayoutParams(0,dp(22),1));}
            for(int row=0;row<10;row++){
                LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);board.addView(line);
                TextView label=text(String.valueOf(row+1),10,INK,true);label.setGravity(Gravity.CENTER);line.addView(label,new LinearLayout.LayoutParams(dp(22),dp(29)));
                for(int col=0;col<10;col++){
                    int index=row*10+col;boolean wasShot=contains(shownShots,index);
                    TextView cell=text("",11,INK,true);cell.setGravity(Gravity.CENTER);
                    int bg=0xFFB9D9E4;
                    if(showOwn&&ownCells.contains(index)){
                        cell.setText(wasShot?"✦":shipCellSymbol(shownOwn,index));
                        cell.setTextColor(wasShot?0xFF8F3444:PAPER);
                        bg=wasShot?0xFFF0C8CC:0xFF597F91;
                        cell.setContentDescription(wasShot?"Попадание по кораблю":
                                "Корабль · "+shipLengthAt(shownOwn,index)+" клеток");
                    }else if(wasShot){
                        boolean hit=displayedShips.contains(index);cell.setText(hit?"✦":"·");
                        bg=hit?0xFFF0C8CC:0xFFEAF4F6;
                    }
                    cell.setBackground(shape(bg,4));
                    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(29),1);
                    cp.setMargins(dp(1),dp(1),dp(1),dp(1));line.addView(cell,cp);
                    cell.setEnabled(setup||active&&turn.equals(me)&&!wasShot&&winner.isEmpty());
                    if(setup&&manualMode[0])cell.setOnClickListener(v->{if(placeManualShip(manualFleet[0],index,horizontal[0]))draw[0].run();else toast("Корабль не помещается или касается другого");});
                    else if(!setup&&!showOwn)cell.setOnClickListener(v->fireSeaShot(index,game,draw));
                }
            }
        };
        myFleetTab.setOnClickListener(v->{ownBoardSelected[0]=true;draw[0].run();});enemyTab.setOnClickListener(v->{JSONObject state=game[0].optJSONObject("state");if(state==null||!state.optBoolean("p1Ready")||!state.optBoolean("p2Ready"))return;ownBoardSelected[0]=false;draw[0].run();});
        manual.setOnClickListener(v->{manualMode[0]=!manualMode[0];manualFleet[0]=new JSONArray();draw[0].run();status.setText(manualMode[0]?"Расставьте корабли по одному: выберите клетку, затем поверните следующий.":"Автоматическая расстановка. Можно перетасовать.");});
        rotate.setOnClickListener(v->{horizontal[0]=!horizontal[0];rotate.setText(horizontal[0]?"↔":"↕");});
        undo.setOnClickListener(v->{if(manualFleet[0].length()>0)manualFleet[0].remove(manualFleet[0].length()-1);draw[0].run();});
        shuffle.setOnClickListener(v->{manualMode[0]=false;network.execute(()->{try{JSONObject fresh=pairGames.fetch(game[0].getString("id"));JSONObject s=new JSONObject(fresh.getJSONObject("state").toString());s.put(meets(s,"p1")?"p1Fleet":"p2Fleet",randomFleet());s.put(meets(s,"p1")?"p1Ready":"p2Ready",false);JSONObject saved=pairGames.update(fresh.getString("id"),fresh.optLong("version"),s,"waiting");runOnUiThread(()->{try{game[0]=saved;draw[0].run();}catch(Exception e){toast("Ошибка игры: "+e.getMessage());}});}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});});
        ready.setOnClickListener(v->{if(manualMode[0]&&manualFleet[0].length()!=12){toast("Расставьте все 12 кораблей");return;}network.execute(()->{try{JSONObject fresh=pairGames.fetch(game[0].getString("id"));JSONObject s=new JSONObject(fresh.getJSONObject("state").toString());boolean first=pairGames.userId().equals(s.optString("p1"));String key=first?"p1Ready":"p2Ready";if(manualMode[0])s.put(first?"p1Fleet":"p2Fleet",manualFleet[0]);s.put(key,true);boolean start=s.optBoolean(first?"p2Ready":"p1Ready")&&!s.optString("p2").isEmpty();if(start&&s.optString("first_player").isEmpty())setRandomFirstTurn(s,"sea_battle");JSONObject saved=pairGames.update(fresh.getString("id"),fresh.optLong("version"),s,start?"active":"waiting");runOnUiThread(()->{try{game[0]=saved;draw[0].run();}catch(Exception e){toast("Ошибка игры: "+e.getMessage());}});}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});});
        draw[0].run();dialog.setOnDismissListener(d->{closed[0]=true;});dialog.show();poll[0]=()->{if(closed[0])return;network.execute(()->{try{JSONObject fresh=pairGames.fetch(game[0].getString("id"));if(fresh!=null)runOnUiThread(()->{try{game[0]=fresh;draw[0].run();}catch(Exception ignored){}});}catch(Exception ignored){}if(!closed[0])gameHandler.postDelayed(poll[0],1400);});};gameHandler.postDelayed(poll[0],1400);
    }
    private boolean placeManualShip(JSONArray fleet,int start,boolean horizontal){
        int[] lengths={4,3,3,2,1,1,1,1,1,1,1,1};int shipIndex=fleet.length();if(shipIndex>=lengths.length)return false;
        int row=start/10,col=start%10,len=lengths[shipIndex];if(horizontal&&col+len>10||!horizontal&&row+len>10)return false;
        HashSet<Integer> occupied=new HashSet<>();for(int i=0;i<fleet.length();i++){JSONArray ship=fleet.optJSONArray(i);if(ship!=null)for(int j=0;j<ship.length();j++)occupied.add(ship.optInt(j,-1));}
        JSONArray next=new JSONArray();for(int k=0;k<len;k++){int r=row+(horizontal?0:k),c=col+(horizontal?k:0),cell=r*10+c;
            for(int dr=-1;dr<=1;dr++)for(int dc=-1;dc<=1;dc++){int nr=r+dr,nc=c+dc;if(nr>=0&&nr<10&&nc>=0&&nc<10&&occupied.contains(nr*10+nc))return false;}
            next.put(cell);
        }
        fleet.put(next);return true;
    }
    private boolean meets(JSONObject s,String key){return pairGames.userId().equals(s.optString(key));}
    private boolean contains(JSONArray values,int n){for(int i=0;i<values.length();i++)if(values.optInt(i,-1)==n)return true;return false;}
    private int shipLengthAt(JSONArray fleet,int cell){
        for(int i=0;i<fleet.length();i++){
            JSONArray ship=fleet.optJSONArray(i);if(ship==null)continue;
            for(int j=0;j<ship.length();j++)if(ship.optInt(j,-1)==cell)return ship.length();
        }
        return 1;
    }
    private String shipCellSymbol(JSONArray fleet,int cell){
        for(int i=0;i<fleet.length();i++){
            JSONArray ship=fleet.optJSONArray(i);if(ship==null)continue;
            for(int j=0;j<ship.length();j++){
                if(ship.optInt(j,-1)!=cell)continue;
                if(ship.length()==1)return "●";
                boolean horizontal=ship.optInt(1)-ship.optInt(0)==1;
                if(j==0)return horizontal?"◀":"▲";
                if(j==ship.length()-1)return horizontal?"▶":"▼";
                return horizontal?"━":"┃";
            }
        }
        return "●";
    }
    private void fireSeaShot(int index,JSONObject[] game,Runnable[] draw){network.execute(()->{try{JSONObject fresh=pairGames.fetch(game[0].getString("id"));JSONObject s=new JSONObject(fresh.getJSONObject("state").toString());String me=pairGames.userId();if(!"active".equals(fresh.optString("status"))||!me.equals(s.optString("turn")))return;boolean first=me.equals(s.optString("p1"));String shotsKey=first?"p1Shots":"p2Shots",targetFleetKey=first?"p2Fleet":"p1Fleet";JSONArray shots=s.optJSONArray(shotsKey);if(shots==null)shots=new JSONArray();if(contains(shots,index))return;shots.put(index);s.put(shotsKey,shots);JSONArray fleet=s.optJSONArray(targetFleetKey);boolean hit=false;int totalHits=0;if(fleet!=null)for(int j=0;j<fleet.length();j++){JSONArray ship=fleet.optJSONArray(j);if(ship!=null)for(int k=0;k<ship.length();k++){int cell=ship.optInt(k,-1);if(cell==index)hit=true;if(contains(shots,cell))totalHits++;}}String winner=s.optString("winner");String status="active";if(totalHits>=20){winner=me;s.put("winner",me);status="finished";}else if(!hit)s.put("turn",first?s.optString("p2"):s.optString("p1"));JSONObject saved=pairGames.update(fresh.getString("id"),fresh.optLong("version"),s,status);runOnUiThread(()->{try{game[0]=saved;draw[0].run();}catch(Exception e){toast("Ошибка игры: "+e.getMessage());}});}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});}
    private void onlineDurak(){
        if(!pairGames.ready()){toast("Войдите в аккаунт и подключитесь к партнёру по коду приглашения");return;}
        network.execute(()->{try{JSONObject g=openOrJoinGame("durak");safeUi(()->showOnlineDurak(g));}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});
    }
    private void showOnlineDurak(JSONObject initial){
        final JSONObject[] game={initial};final boolean[] closed={false};final Runnable[] poll={null};
        LinearLayout panel=column();panel.setPadding(dp(12),dp(8),dp(12),dp(8));TextView status=text("",14,INK,true);status.setGravity(Gravity.CENTER);status.setPadding(dp(10),dp(9),dp(10),dp(9));status.setBackground(shape(PALE,14));panel.addView(status);
        TextView table=text("Стол пуст",17,0xFFFFFFFF,true);table.setGravity(Gravity.CENTER);table.setPadding(dp(10),dp(18),dp(10),dp(18));table.setBackground(shape(0xFF2D765D,16));LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(9);panel.addView(table,tp);
        TextView deckInfo=text("",12,MUTED,false);deckInfo.setGravity(Gravity.CENTER);panel.addView(deckInfo);
        TextView take=pill("Забрать карту",PURPLE);take.setGravity(Gravity.CENTER);panel.addView(take);
        LinearLayout hand= new LinearLayout(this);hand.setGravity(Gravity.CENTER);hand.setPadding(0,dp(8),0,dp(4));panel.addView(hand);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Дурак · онлайн").setView(panel).setNegativeButton("Закрыть",null).create();
        Runnable[] draw={null};draw[0]=()->{JSONObject g=game[0],s=g.optJSONObject("state");if(s==null)return;String me=pairGames.userId(),p1=s.optString("p1"),p2=s.optString("p2"),attacker=s.optString("attacker"),winner=s.optString("winner");boolean first=me.equals(p1),active="active".equals(g.optString("status"));JSONArray own=s.optJSONArray(first?"p1Hand":"p2Hand");if(own==null)own=new JSONArray();int attack=s.optInt("attack",-1),defense=s.optInt("defense",-1);int deck=s.optJSONArray("deck")==null?0:s.optJSONArray("deck").length();
            if(p2.isEmpty())status.setText("Колода готова. Ждём второго игрока.");else if(!winner.isEmpty())status.setText(winner.equals("draw")?"Ничья!":winner.equals(me)?"Вы вышли первым — победа! 🎉":"Партнёр вышел первым. Вы — дурак 😄");else status.setText(attacker.equals(me)?"Ваш ход атаки":"Ход партнёра · "+(attack>=0?(defense>=0?"карта отбита":"выберите карту выше или заберите") : "ожидайте атаку"));
            table.setText(attack<0?"Стол пуст":"Атака  "+showDurakCard(attack)+(defense>=0?"     ·     Отбой  "+showDurakCard(defense):""));int trump=s.optInt("trump",0);String[] suits={"♣ треф","♦ бубен","♥ червей","♠ пик"};deckInfo.setText("Карт у партнёра: "+s.optJSONArray(first?"p2Hand":"p1Hand").length()+"  ·  колода: "+deck+"  ·  КОЗЫРЬ: "+suits[Math.max(0,Math.min(3,trump))]);boolean mayTake=active&&p2.length()>0&&attacker.equals(me)?false:active&&p2.length()>0&&!attacker.equals(me)&&attack>=0&&defense<0;take.setVisibility(mayTake?View.VISIBLE:View.GONE);
            hand.removeAllViews();for(int k=0;k<own.length();k++){int code=own.optInt(k,-1);if(code<0||code>=36)continue;Card card=new Card(6+code/4,code%4);TextView button=text(card.show(),17,card.suit==1||card.suit==2?0xFFD84B5E:INK,true);button.setGravity(Gravity.CENTER);button.setBackground(shape(PAPER,11));button.setElevation(dp(2));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(62),1);cp.setMargins(dp(2),dp(2),dp(2),dp(2));hand.addView(button,cp);boolean canPlay=active&&p2.length()>0&&(attacker.equals(me)?attack<0:attack>=0&&defense<0&&beats(card,new Card(6+attack/4,attack%4),s.optInt("trump")));button.setEnabled(canPlay);button.setOnClickListener(v->playDurakCard(code,game,draw));}
        };
        take.setOnClickListener(v->takeDurakCard(game,draw));draw[0].run();dialog.setOnDismissListener(d->{closed[0]=true;});dialog.show();poll[0]=()->{if(closed[0])return;network.execute(()->{try{JSONObject fresh=pairGames.fetch(game[0].getString("id"));if(fresh!=null)runOnUiThread(()->{try{game[0]=fresh;draw[0].run();}catch(Exception ignored){}});}catch(Exception ignored){}if(!closed[0])gameHandler.postDelayed(poll[0],1400);});};gameHandler.postDelayed(poll[0],1400);
    }
    private String showDurakCard(int code){return new Card(6+code/4,code%4).show();}
    private void playDurakCard(int code,JSONObject[] game,Runnable[] draw){network.execute(()->{try{JSONObject fresh=pairGames.fetch(game[0].getString("id"));JSONObject s=new JSONObject(fresh.getJSONObject("state").toString());String me=pairGames.userId(),attacker=s.optString("attacker");boolean first=me.equals(s.optString("p1"));String handKey=first?"p1Hand":"p2Hand";JSONArray hand=s.optJSONArray(handKey);int attack=s.optInt("attack",-1),defense=s.optInt("defense",-1);int cardIndex=-1;for(int i=0;i<hand.length();i++)if(hand.optInt(i)==code)cardIndex=i;if(cardIndex<0)throw new Exception("Этой карты уже нет в вашей руке");if(me.equals(attacker)){if(attack>=0)throw new Exception("Сначала завершите текущую пару карт");s.put("attack",code);hand.remove(cardIndex);}else{if(attack<0||defense>=0)throw new Exception("Сейчас нельзя отбить карту");Card c=new Card(6+code/4,code%4),a=new Card(6+attack/4,attack%4);if(!beats(c,a,s.optInt("trump")))throw new Exception("Подходит только карта выше по масти или козырь");s.put("defense",code);hand.remove(cardIndex);settleDurak(s,true);}
            JSONObject saved=pairGames.update(fresh.getString("id"),fresh.optLong("version"),s,s.optString("winner").isEmpty()?"active":"finished");runOnUiThread(()->{try{game[0]=saved;draw[0].run();}catch(Exception e){toast("Ошибка игры: "+e.getMessage());}});}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});}
    private void takeDurakCard(JSONObject[] game,Runnable[] draw){network.execute(()->{try{JSONObject fresh=pairGames.fetch(game[0].getString("id"));JSONObject s=new JSONObject(fresh.getJSONObject("state").toString());if(s.optInt("attack",-1)<0||s.optInt("defense",-1)>=0)throw new Exception("Сейчас нечего забирать");settleDurak(s,false);JSONObject saved=pairGames.update(fresh.getString("id"),fresh.optLong("version"),s,s.optString("winner").isEmpty()?"active":"finished");runOnUiThread(()->{try{game[0]=saved;draw[0].run();}catch(Exception e){toast("Ошибка игры: "+e.getMessage());}});}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});}
    private void settleDurak(JSONObject s,boolean defended)throws Exception{String attacker=s.optString("attacker"),defender=attacker.equals(s.optString("p1"))?s.optString("p2"):s.optString("p1");JSONArray h1=s.optJSONArray("p1Hand"),h2=s.optJSONArray("p2Hand"),deck=s.optJSONArray("deck");if(!defended){JSONArray dh=defender.equals(s.optString("p1"))?h1:h2;dh.put(s.optInt("attack"));}s.put("attacker",defended?defender:attacker);s.put("attack",-1);s.put("defense",-1);for(String player:new String[]{attacker,defender}){JSONArray h=player.equals(s.optString("p1"))?h1:h2;while(h.length()<6&&deck.length()>0)h.put(deck.remove(deck.length()-1));}s.put("p1Hand",h1);s.put("p2Hand",h2);s.put("deck",deck);if(deck.length()==0){boolean one=h1.length()==0,two=h2.length()==0;if(one&&two)s.put("winner","draw");else if(one)s.put("winner",s.optString("p1"));else if(two)s.put("winner",s.optString("p2"));}}
    private void onlineCoupleQuiz(String mode,String question){if(!pairGames.ready()){toast("Сначала войдите в аккаунт и подключитесь к партнёру");return;}network.execute(()->{try{JSONObject g=openOrJoinGame("couple_quiz",question);runOnUiThread(()->showOnlineCoupleQuiz(g));}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});}
    private void showOnlineCoupleQuiz(JSONObject initial){
        final JSONObject[] game={initial};final boolean[] closed={false};final Runnable[] poll={null};LinearLayout panel=column();panel.setPadding(dp(18),dp(8),dp(18),dp(8));TextView prompt=text("",16,INK,true);panel.addView(prompt);TextView status=text("",13,PURPLE,true);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(10);panel.addView(status,sp);EditText answer=new EditText(this);answer.setHint("Ваш ответ — он пока скрыт от партнёра");answer.setMinLines(2);LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.topMargin=dp(9);panel.addView(answer,ep);TextView submit=pill("Отправить ответ",PURPLE);submit.setGravity(Gravity.CENTER);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(46));bp.topMargin=dp(8);panel.addView(submit,bp);TextView reveal=text("",14,INK,false);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(10);panel.addView(reveal,rp);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Вопрос для двоих · онлайн").setView(panel).setNegativeButton("Закрыть",null).create();Runnable[] draw={null};draw[0]=()->{JSONObject g=game[0],s=g.optJSONObject("state");if(s==null)return;String me=pairGames.userId(),p2=s.optString("p2"),ownKey=me.equals(s.optString("p1"))?"p1Answer":"p2Answer",a=s.optString("p1Answer"),b=s.optString("p2Answer");prompt.setText(s.optString("question"));boolean ready="active".equals(g.optString("status")),done=!a.isEmpty()&&!b.isEmpty();status.setText(p2.isEmpty()?"Ждём, когда партнёр присоединится со своего телефона.":done?"Оба ответа открыты 💛":!ready?"Ожидаем партнёра…":s.optString(ownKey).isEmpty()?"Напишите свой ответ — партнёр увидит его, когда ответит тоже.":"Ваш ответ сохранён. Ждём ответ партнёра…");answer.setVisibility(ready&&!done&&s.optString(ownKey).isEmpty()?View.VISIBLE:View.GONE);submit.setVisibility(answer.getVisibility());reveal.setText(done?"Ваш ответ: "+a+"\nОтвет партнёра: "+b:"");};submit.setOnClickListener(v->{String value=answer.getText().toString().trim();if(value.isEmpty())return;network.execute(()->{try{JSONObject fresh=pairGames.fetch(game[0].getString("id"));JSONObject s=new JSONObject(fresh.getJSONObject("state").toString());String key=pairGames.userId().equals(s.optString("p1"))?"p1Answer":"p2Answer";if(!s.optString(key).isEmpty())return;s.put(key,value);boolean done=!s.optString("p1Answer").isEmpty()&&!s.optString("p2Answer").isEmpty();JSONObject saved=pairGames.update(fresh.getString("id"),fresh.optLong("version"),s,done?"finished":"active");runOnUiThread(()->{try{game[0]=saved;answer.setText("");draw[0].run();}catch(Exception e){toast("Ошибка игры: "+e.getMessage());}});}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});});draw[0].run();dialog.setOnDismissListener(d->closed[0]=true);dialog.show();poll[0]=()->{if(closed[0])return;network.execute(()->{try{JSONObject g=pairGames.fetch(game[0].getString("id"));if(g!=null)runOnUiThread(()->{try{game[0]=g;draw[0].run();}catch(Exception ignored){}});}catch(Exception ignored){}if(!closed[0])gameHandler.postDelayed(poll[0],1600);});};gameHandler.postDelayed(poll[0],1600);
    }
    private void onlineGuessTune(){if(!pairGames.ready()){toast("Сначала войдите в аккаунт и подключитесь к партнёру");return;}network.execute(()->{try{JSONObject g=openOrJoinGame("guess_tune");runOnUiThread(()->showOnlineGuessTune(g));}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});}
    private void showOnlineGuessTune(JSONObject initial){
        final JSONObject[] game={initial};final boolean[] closed={false};final Runnable[] poll={null};LinearLayout panel=column();panel.setPadding(dp(18),dp(8),dp(18),dp(8));TextView prompt=text("",16,INK,true);panel.addView(prompt);TextView status=text("",13,PURPLE,true);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(9);panel.addView(status,sp);EditText answer=new EditText(this);answer.setSingleLine(true);answer.setHint("Введите название мелодии");LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.topMargin=dp(9);panel.addView(answer,ep);TextView action=pill("Я напел — угадывай",PURPLE);action.setGravity(Gravity.CENTER);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(46));ap.topMargin=dp(8);panel.addView(action,ap);TextView result=text("",14,INK,true);panel.addView(result);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Угадай мелодию · онлайн").setView(panel).setNegativeButton("Закрыть",null).create();Runnable[] draw={null};draw[0]=()->{JSONObject s=game[0].optJSONObject("state");if(s==null)return;String me=pairGames.userId();boolean host=me.equals(s.optString("p1"));String guess=s.optString("p2Answer"),winner=s.optString("winner");if(host){prompt.setText("Напойте партнёру: «"+s.optString("correct")+"».");answer.setVisibility(View.GONE);action.setVisibility("finished".equals(game[0].optString("status"))?View.GONE:View.VISIBLE);status.setText(s.optBoolean("p1Ready")?"Партнёр угадывает…":"Найдите эту мелодию и спойте её вслух.");result.setText(!guess.isEmpty()?"Ответ: "+guess+" · "+(winner.equals(me)?"Угадано! 🎉":"Попробуйте ещё раз в следующей партии") : "");}else{prompt.setText(s.optBoolean("p1Ready")?"Партнёр напел мелодию. Что это было?":"Ждём, пока партнёр напоёт мелодию…");answer.setVisibility(s.optBoolean("p1Ready")&&guess.isEmpty()?View.VISIBLE:View.GONE);action.setVisibility(answer.getVisibility());action.setText("Отправить ответ");status.setText(guess.isEmpty()?"Введите название, затем отправьте ответ.":"Ответ отправлен. Ждём результат…");result.setText(!guess.isEmpty()?"Ответ: "+guess+" · "+(winner.equals(me)?"Верно! 🎉":"Не угадано — следующая песня будет вашей") : "");}};
        action.setOnClickListener(v->{boolean host=pairGames.userId().equals(game[0].optJSONObject("state").optString("p1"));String value=answer.getText().toString().trim();if(!host&&value.isEmpty())return;network.execute(()->{try{JSONObject fresh=pairGames.fetch(game[0].getString("id"));JSONObject s=new JSONObject(fresh.getJSONObject("state").toString());String next="active";if(host){s.put("p1Ready",true);}else{if(!s.optBoolean("p1Ready"))throw new Exception("Сначала дождитесь мелодию");s.put("p2Answer",value);boolean correct=value.equalsIgnoreCase(s.optString("correct"));if(correct)s.put("winner",pairGames.userId());else s.put("winner","wrong");next="finished";}JSONObject saved=pairGames.update(fresh.getString("id"),fresh.optLong("version"),s,next);runOnUiThread(()->{try{game[0]=saved;answer.setText("");draw[0].run();}catch(Exception e){toast("Ошибка игры: "+e.getMessage());}});}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});});draw[0].run();dialog.setOnDismissListener(d->closed[0]=true);dialog.show();poll[0]=()->{if(closed[0])return;network.execute(()->{try{JSONObject g=pairGames.fetch(game[0].getString("id"));if(g!=null)runOnUiThread(()->{try{game[0]=g;draw[0].run();}catch(Exception ignored){}});}catch(Exception ignored){}if(!closed[0])gameHandler.postDelayed(poll[0],1600);});};gameHandler.postDelayed(poll[0],1600);
    }
    private void promptGame(String q) {
        new AlertDialog.Builder(this).setTitle("Вопрос для двоих").setMessage(q+"\n\nПо очереди назовите ответ и сравните, насколько хорошо вы друг друга знаете!")
                .setPositiveButton("Дальше",null).show();
    }
    private void ticTacToe() {
        final char[] cells=new char[9]; final char[] turn={'X'}; final boolean[] finished={false};
        LinearLayout panel=column(); panel.setPadding(dp(20),dp(8),dp(20),dp(8));
        TextView status=text("Ход: Игрок 1 · X",15,INK,true);status.setGravity(Gravity.CENTER);status.setPadding(dp(12),dp(10),dp(12),dp(10));status.setBackground(shape(PALE,16));
        LinearLayout.LayoutParams statusParams=new LinearLayout.LayoutParams(-1,-2);statusParams.bottomMargin=dp(12);panel.addView(status,statusParams);
        LinearLayout board=column();board.setGravity(Gravity.CENTER);board.setPadding(dp(10),dp(10),dp(10),dp(10));board.setBackground(shape(0xFFEDE4F1,22));panel.addView(board);
        TextView[] buttons=new TextView[9];
        Runnable[] reset={null}; reset[0]=()->{for(int i=0;i<9;i++){cells[i]=' ';buttons[i].setText("");buttons[i].setTextColor(INK);buttons[i].setBackground(shape(0xFFFBF9FC,15));buttons[i].setEnabled(true);}turn[0]='X';finished[0]=false;status.setText("Ход: Игрок 1 · X");};
        for(int row=0;row<3;row++){
            LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER);board.addView(line);
            for(int col=0;col<3;col++){
                int i=row*3+col;TextView b=text("",32,INK,true);buttons[i]=b;b.setGravity(Gravity.CENTER);b.setBackground(shape(0xFFFBF9FC,15));b.setElevation(dp(1));
                LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(dp(78),dp(76));cell.setMargins(dp(4),dp(4),dp(4),dp(4));line.addView(b,cell);
                b.setOnClickListener(v->{if(cells[i]!=' '||finished[0])return;char mark=turn[0];cells[i]=mark;b.setText(String.valueOf(mark));b.setTextColor(mark=='X'?0xFF71529B:0xFFD66D92);
                    if(hasWinner(cells,mark)){finished[0]=true;status.setText("Победил игрок "+(mark=='X'?"1":"2")+"! ✨");for(TextView x:buttons){x.setEnabled(false);if(!x.getText().toString().isEmpty()&&x.getText().toString().charAt(0)==mark)x.setBackground(shape(0xFFE8F4EA,15));}return;}
                    boolean full=true;for(char x:cells)if(x==' ')full=false;if(full){finished[0]=true;status.setText("Ничья — хорошая партия 💛");return;}
                    turn[0]=mark=='X'?'O':'X';status.setText("Ход: Игрок "+(turn[0]=='X'?"1 · X":"2 · O"));});
            }
        }
        TextView resetButton=pill("Новая партия",PURPLE);resetButton.setGravity(Gravity.CENTER);LinearLayout.LayoutParams resetParams=new LinearLayout.LayoutParams(-1,-2);resetParams.topMargin=dp(12);panel.addView(resetButton,resetParams);resetButton.setOnClickListener(v->reset[0].run());
        new AlertDialog.Builder(this).setTitle("Крестики-нолики").setView(panel).setNegativeButton("Закрыть",null).show();
    }
    private boolean hasWinner(char[] b,char p){
        int[][] lines={{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};
        for(int[] l:lines)if(b[l[0]]==p&&b[l[1]]==p&&b[l[2]]==p)return true;return false;
    }
    private void seaBattle() {
        final int size=8; final int[] shipAt=new int[size*size],shipLengths={3,2,1},hitsPerShip={0,0,0},hits={0},shots={0};final boolean[] fired=new boolean[size*size],finished={false};Random random=new Random();
        for(int ship=0;ship<shipLengths.length;ship++){int len=shipLengths[ship],tries=0;boolean done=false;while(!done&&tries++<500){boolean horizontal=random.nextBoolean();int row=random.nextInt(size),col=random.nextInt(size);if(horizontal&&col+len>size||!horizontal&&row+len>size)continue;boolean clear=true;for(int k=0;k<len;k++){int rr=row+(horizontal?0:k),cc=col+(horizontal?k:0);for(int dr=-1;dr<=1;dr++)for(int dc=-1;dc<=1;dc++){int nr=rr+dr,nc=cc+dc;if(nr>=0&&nr<size&&nc>=0&&nc<size&&shipAt[nr*size+nc]!=0)clear=false;}}if(!clear)continue;for(int k=0;k<len;k++){int rr=row+(horizontal?0:k),cc=col+(horizontal?k:0);shipAt[rr*size+cc]=ship+1;}done=true;}}
        LinearLayout panel=column();panel.setPadding(dp(12),dp(8),dp(12),dp(8));
        TextView status=text("Флот: эсминец · катер · шлюпка\nПопаданий: 0 из 6",13,INK,true);status.setGravity(Gravity.CENTER);status.setPadding(dp(10),dp(9),dp(10),dp(9));status.setBackground(shape(PALE,15));
        LinearLayout.LayoutParams statusParams=new LinearLayout.LayoutParams(-1,-2);statusParams.bottomMargin=dp(10);panel.addView(status,statusParams);
        LinearLayout board=column();board.setPadding(dp(5),dp(5),dp(5),dp(5));board.setBackground(shape(0xFFDCECF1,18));panel.addView(board);TextView[] cells=new TextView[size*size];
        for(int row=0;row<size;row++){LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER);board.addView(line);for(int col=0;col<size;col++){int i=row*size+col;TextView cell=text("",16,INK,true);cells[i]=cell;cell.setGravity(Gravity.CENTER);cell.setBackground(shape(0xFFB9D9E4,7));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(36),1);cp.setMargins(dp(1),dp(1),dp(1),dp(1));line.addView(cell,cp);cell.setOnClickListener(v->{if(fired[i]||finished[0])return;fired[i]=true;shots[0]++;int ship=shipAt[i];if(ship>0){hits[0]++;hitsPerShip[ship-1]++;cell.setText("✦");cell.setTextColor(0xFFC64D61);cell.setBackground(shape(0xFFF5D8D9,7));boolean sunk=hitsPerShip[ship-1]>=shipLengths[ship-1];status.setText((sunk?new String[]{"","Эсминец потоплен!","Катер потоплен!","Шлюпка потоплена!"}[ship]:"Попадание!")+"\nПопаданий: "+hits[0]+" из 6 · выстрелов: "+shots[0]);}else{cell.setText("·");cell.setTextColor(0xFF5F8EA4);cell.setBackground(shape(0xFFEAF4F6,7));status.setText("Мимо. Попаданий: "+hits[0]+" из 6 · выстрелов: "+shots[0]);}if(hits[0]==6){finished[0]=true;status.setText("Флот найден! Отличная стрельба ⚓\nВсего выстрелов: "+shots[0]);}});}}
        new AlertDialog.Builder(this).setTitle("Морской бой · поиск флота").setView(panel).setNegativeButton("Закрыть",null).show();
    }
    private void guessTune() {
        String[] tunes={"Ода к радости","Вальс цветов","Маленькая ночная серенада","Щелкунчик: Танец феи Драже"};
        int answer=new Random().nextInt(tunes.length);int wrong=(answer+1+new Random().nextInt(tunes.length-1))%tunes.length;
        String hint=tunes[answer];String[] options={hint,tunes[wrong]};if(new Random().nextBoolean()){String t=options[0];options[0]=options[1];options[1]=t;}
        new AlertDialog.Builder(this).setTitle("Угадай мелодию")
                .setMessage("Один из вас напевает мелодию, второй выбирает название. После ответа поменяйтесь!")
                .setSingleChoiceItems(options,-1,(dialog,which)->{String result=options[which].equals(hint)?"Верно! Отличная музыкальная память 💛":"Правильный ответ: "+hint;
                    Toast.makeText(this,result,Toast.LENGTH_LONG).show();dialog.dismiss();}).setNegativeButton("Отмена",null).show();
    }
    private static class Card {int rank,suit;Card(int r,int s){rank=r;suit=s;}String show(){String[] ranks={"6","7","8","9","10","В","Д","К","Т"};String[] suits={"♣","♦","♥","♠"};return ranks[rank-6]+suits[suit];}}
    private boolean beats(Card defense,Card attack,int trump){return defense.suit==attack.suit&&defense.rank>attack.rank||defense.suit==trump&&attack.suit!=trump;}
    private void durak() {
        java.util.ArrayList<Card> deck=new java.util.ArrayList<>(),hand=new java.util.ArrayList<>(),cpuHand=new java.util.ArrayList<>();
        for(int s=0;s<4;s++)for(int n=6;n<=14;n++)deck.add(new Card(n,s));java.util.Collections.shuffle(deck);
        for(int i=0;i<6;i++){hand.add(deck.remove(deck.size()-1));cpuHand.add(deck.remove(deck.size()-1));}final int trump=deck.get(deck.size()-1).suit;
        final int[] attacker={0};final Card[] cpuAttack={null};final boolean[] finished={false};
        LinearLayout panel=column();panel.setPadding(dp(15),dp(8),dp(15),dp(8));
        TextView status=text("",14,0xFFFFFFFF,true);status.setGravity(Gravity.CENTER);status.setPadding(dp(12),dp(10),dp(12),dp(10));status.setBackground(shape(0xFF245B49,16));panel.addView(status);
        LinearLayout table=column();table.setPadding(dp(12),dp(10),dp(12),dp(10));table.setBackground(shape(0xFF2D765D,18));LinearLayout.LayoutParams tableParams=new LinearLayout.LayoutParams(-1,-2);tableParams.topMargin=dp(10);panel.addView(table,tableParams);
        TextView rival=text("Соперник · карт: "+cpuHand.size()+"   ·   колода: "+deck.size(),12,0xFFE8F3ED,false);rival.setGravity(Gravity.CENTER);table.addView(rival);
        TextView trumpBadge=text("Козырь  "+new Card(6,trump).show().substring(1),18,INK,true);trumpBadge.setGravity(Gravity.CENTER);trumpBadge.setBackground(shape(PAPER,14));LinearLayout.LayoutParams trumpParams=new LinearLayout.LayoutParams(dp(84),dp(56));trumpParams.gravity=Gravity.CENTER;trumpParams.topMargin=dp(10);table.addView(trumpBadge,trumpParams);
        TextView tableCards=text("Стол пуст",15,0xFFFFFFFF,true);tableCards.setGravity(Gravity.CENTER);tableCards.setPadding(dp(8),dp(16),dp(8),dp(16));table.addView(tableCards);
        TextView take=pill("Забрать карту",PURPLE);take.setGravity(Gravity.CENTER);take.setVisibility(View.GONE);LinearLayout.LayoutParams takeParams=new LinearLayout.LayoutParams(-2,-2);takeParams.gravity=Gravity.CENTER;table.addView(take,takeParams);
        TextView handTitle=text("Ваши карты",13,MUTED,true);handTitle.setPadding(0,dp(13),0,dp(6));panel.addView(handTitle);LinearLayout cards=new LinearLayout(this);cards.setGravity(Gravity.CENTER);panel.addView(cards);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Дурак · карточная партия").setView(panel).setNegativeButton("Завершить",null).create();
        Runnable[] draw={null};draw[0]=()->{
            cards.removeAllViews();rival.setText("Соперник · карт: "+cpuHand.size()+"   ·   колода: "+deck.size());
            if(finished[0])return;
            if(attacker[0]==1&&cpuAttack[0]==null&&!cpuHand.isEmpty())cpuAttack[0]=cpuHand.remove(0);
            boolean cpuTurn=attacker[0]==1;
            status.setText(cpuTurn?"Ваш ход защиты · отбейте карту соперника":"Ваш ход атаки · выберите карту");
            tableCards.setText(cpuAttack[0]==null?"Стол пуст":"Соперник атакует картой  "+cpuAttack[0].show());take.setVisibility(cpuAttack[0]==null?View.GONE:View.VISIBLE);
            if(cpuAttack[0]==null)tableCards.setBackground(shape(0xFF2D765D,12));else tableCards.setBackground(shape(0xFF387E64,12));
            for(Card card:hand){TextView b=text(card.show(),19,card.suit==1||card.suit==2?0xFFD84B5E:0xFF28232C,true);b.setGravity(Gravity.CENTER);b.setBackground(shape(PAPER,12));b.setElevation(dp(2));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(46),dp(66));bp.setMargins(dp(2),dp(2),dp(2),dp(2));cards.addView(b,bp);
                b.setOnClickListener(v->{if(finished[0])return;
                    if(attacker[0]==0&&cpuAttack[0]==null){Card attack=card;hand.remove(attack);Card defend=null;for(Card c:cpuHand)if(beats(c,attack,trump)&&(defend==null||c.rank<defend.rank))defend=c;
                        if(defend!=null){cpuHand.remove(defend);tableCards.setText("Атака  "+attack.show()+"     ·     Отбой  "+defend.show());cpuAttack[0]=null;attacker[0]=1;}else{cpuHand.add(attack);tableCards.setText("Соперник забрал карту  "+attack.show());attacker[0]=0;}
                    }else if(attacker[0]==1&&cpuAttack[0]!=null){if(!beats(card,cpuAttack[0],trump)){toast("Этой картой не отбиться — выберите карту выше или козырь");return;}hand.remove(card);tableCards.setText("Атака  "+cpuAttack[0].show()+"     ·     Отбой  "+card.show());cpuAttack[0]=null;attacker[0]=0;}
                    if(deck.isEmpty()){if(hand.isEmpty()&&cpuHand.isEmpty()){finished[0]=true;status.setText("Ничья — карты закончились одновременно");}else if(hand.isEmpty()){finished[0]=true;status.setText("Вы вышли из игры первым — победа! 🎉");}else if(cpuHand.isEmpty()&&cpuAttack[0]==null){finished[0]=true;status.setText("Соперник вышел первым — вы подкинули ему дурака 😄");}}
                    if(!finished[0]){java.util.ArrayList<Card> first=attacker[0]==0?hand:cpuHand,second=attacker[0]==0?cpuHand:hand;while(first.size()<6&&!deck.isEmpty())first.add(deck.remove(deck.size()-1));while(second.size()<6&&!deck.isEmpty())second.add(deck.remove(deck.size()-1));draw[0].run();}
                });}
        };
        take.setOnClickListener(v->{if(cpuAttack[0]==null||finished[0])return;hand.add(cpuAttack[0]);cpuAttack[0]=null;attacker[0]=1;if(deck.isEmpty()&&cpuHand.isEmpty()){finished[0]=true;status.setText("Соперник вышел первым — вы подкинули ему дурака 😄");}else{while(cpuHand.size()<6&&!deck.isEmpty())cpuHand.add(deck.remove(deck.size()-1));while(hand.size()<6&&!deck.isEmpty())hand.add(deck.remove(deck.size()-1));draw[0].run();}});
        draw[0].run();dialog.show();
    }
    private String keyFor(String s) {
        if (s.contains("Заметки")) return "notes"; if (s.contains("Рецепты")) return "recipes"; if (s.contains("Файлы")) return "files";
        if (s.contains("Карты")) return "loyalty"; if (s.contains("Подар")) return "gifts";
        if (s.contains("Капсула")) return "capsule"; if (s.contains("Голос")) return "votes";
        if (s.contains("планы")) return "plans"; return "items_" + s;
    }

    private void renderEntries(String key) {
        JSONArray rows = read(key);
        for (int i=0;i<rows.length();i++) {
            JSONObject row=rows.optJSONObject(i); if (row == null) continue;
            renderEditableEntry(key, rows, i, "✦");
        }
    }

    private void renderChat() {
        if(contentScroll!=null)contentScroll.setFillViewport(true);
        JSONArray messages=read("messages");
        TextView latest=text(chatScrollPolicy.pendingIncomingCount()>0?"↓  Новые сообщения ("+chatScrollPolicy.pendingIncomingCount()+")":"↓  К последнему сообщению",13,PURPLE,true);
        latest.setGravity(Gravity.CENTER);
        latest.setPadding(dp(12),dp(10),dp(12),dp(10));
        latest.setBackground(shape(PALE,18));
        latest.setVisibility(!chatScrollPolicy.isFollowingLatest()&&chatScrollPolicy.pendingIncomingCount()>0?View.VISIBLE:View.GONE);
        LinearLayout.LayoutParams latestParams=new LinearLayout.LayoutParams(-1,-2);
        latestParams.bottomMargin=dp(6);
        body.addView(latest,latestParams);
        final ScrollView[] messageScroll={null};
        latest.setOnClickListener(v->{
            chatScrollPolicy.jumpToLatest();
            chatScrollY=Integer.MAX_VALUE;
            latest.setVisibility(View.GONE);
            ScrollView list=messageScroll[0];
            if(list!=null)list.post(()->list.fullScroll(ScrollView.FOCUS_DOWN));
        });
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(8),dp(8),dp(8),dp(8));box.setBackgroundColor(0x00000000);
        ScrollView scroll=new ScrollView(this);messageScroll[0]=scroll;scroll.setFillViewport(true);
        scroll.addView(box,new ScrollView.LayoutParams(-1,-2));
        body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        scroll.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
            if(bottom-top==oldBottom-oldTop||!chatScrollPolicy.isFollowingLatest())return;
            scroll.post(()->{
                if(!chatScrollPolicy.isFollowingLatest())return;
                scroll.fullScroll(ScrollView.FOCUS_DOWN);
                chatScrollPolicy.onScroll(scroll.getScrollY(),scroll.getHeight(),box.getHeight(),dp(48));
            });
        });
        final boolean[] restoringScroll = {true};
        scroll.setOnScrollChangeListener((View view,int x,int y,int oldX,int oldY)->{
            if(restoringScroll[0])return;
            chatScrollY=y;
            boolean followingLatest=chatScrollPolicy.onScroll(y,scroll.getHeight(),box.getHeight(),dp(48));
            if(followingLatest)latest.setVisibility(View.GONE);
            else if(chatScrollPolicy.pendingIncomingCount()>0)latest.setVisibility(View.VISIBLE);
        });
        if(messages.length()==0){TextView empty=text("Напишите первое сообщение — чат только для вас двоих",13,MUTED,false);empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(30),0,dp(30));box.addView(empty);}
        String lastSender=null;
        for(int i=0;i<messages.length();i++){
            JSONObject row=messages.optJSONObject(i);if(row==null)continue;
            boolean mine=row.optString("sender_id",sync.userId()).equals(sync.userId());
            String sender=mine?prefs.getString("profile_name","Вы"):prefs.getString("partner_name","Партнёр");
            if(lastSender==null||!lastSender.equals(sender)){
                TextView daySep=text("Сегодня",11,MUTED,false);daySep.setGravity(Gravity.CENTER);daySep.setPadding(0,dp(6),0,dp(6));box.addView(daySep);
                lastSender=sender;
            }
            LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.VERTICAL);line.setPadding(dp(4),dp(2),dp(4),dp(2));
            LinearLayout rowWrap=new LinearLayout(this);rowWrap.setGravity(mine?Gravity.RIGHT:Gravity.LEFT);rowWrap.setPadding(dp(2),dp(2),dp(2),dp(2));
            TextView avatar=text(mine?prefs.getString("profile_avatar","♡"):prefs.getString("partner_avatar","♡"),14,PURPLE,true);avatar.setGravity(Gravity.CENTER);avatar.setBackground(shape(PALE,16));
            String avatarSrc=mine?localAvatarUri():prefs.getString("partner_avatar_uri","");
            if(!mine&&avatarSrc.isEmpty())avatarSrc=prefs.getString("partner_avatar_data","");
            boolean hasPhoto=!avatarSrc.isEmpty();
            if(hasPhoto){ImageView avatarImg=new ImageView(this);avatarImg.setScaleType(ImageView.ScaleType.CENTER_CROP);avatarImg.setBackgroundColor(0xFFE8E0EC);setAvatarPicture(avatarImg,avatarSrc,22);rowWrap.addView(avatarImg,new LinearLayout.LayoutParams(dp(22),dp(22)));} else rowWrap.addView(avatar,new LinearLayout.LayoutParams(dp(28),dp(28)));
            String messageBody=row.optString("title");JSONObject media=null;
            try{JSONObject parsed=new JSONObject(messageBody);if(parsed.optBoolean("vmeste_media"))media=parsed;}catch(Exception ignored){}
            String displayText=media==null?messageBody:("Кружок".equals(media.optString("type"))?"◉ Видеокружок":("Голосовое".equals(media.optString("type"))?"▶ Голосовое":"GIF".equals(media.optString("type"))?"GIF · посмотреть":"Фото".equals(media.optString("type"))?"📷 Фото":("Видео".equals(media.optString("type"))?"▶ Видео":"♫ Аудио")));
            TextView bubble=text(displayText,14,INK,false);bubble.setPadding(dp(12),dp(9),dp(12),dp(9));bubble.setMaxWidth(dp(260));bubble.setBackground(shape(mine?0xFFEFE5F6:0xFFF3F4F6,16));
            if(mine&&media==null){
                bubble.setOnLongClickListener(v->{showMessageActions(row);return true;});
                bubble.setContentDescription("Ваше сообщение. Нажмите и удерживайте, чтобы изменить");
            }
            if(media!=null){String type=media.optString("type"),path=media.optString("local_path",media.optString("path"));bubble.setOnClickListener(v->playChatAttachment(path,type));}
            else {
                String link=SharedLink.firstHttpUrl(displayText);
                if(!link.isEmpty()){bubble.setOnClickListener(v->openSharedLink(link));bubble.setContentDescription("Открыть ссылку во внутреннем просмотре");}
            }
            long ts=row.optLong("ts",0);String timeStr=ts>0?android.text.format.DateFormat.format("HH:mm",ts).toString():"";
            if(row.has("edited_at"))timeStr += (timeStr.isEmpty()?"":" · ")+"изменено";
            TextView time=text(timeStr,9,MUTED,false);
            LinearLayout bubbleWrap=new LinearLayout(this);bubbleWrap.setOrientation(LinearLayout.VERTICAL);bubbleWrap.addView(bubble);bubbleWrap.addView(time);
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2);bp.gravity=mine?Gravity.RIGHT:Gravity.LEFT;bp.setMargins(mine?dp(50):dp(8),0,mine?dp(8):dp(50),0);rowWrap.addView(bubbleWrap,bp);
            line.addView(rowWrap);
            box.addView(line);
        }
        startChatRefresh();
        box.post(()->{
            if(chatScrollPolicy.isFollowingLatest())scroll.fullScroll(ScrollView.FOCUS_DOWN);
            else scroll.scrollTo(0,Math.min(chatScrollY,Math.max(0,box.getHeight()-scroll.getHeight())));
            restoringScroll[0]=false;
        });
    }

    private void showMessageActions(JSONObject message){
        if(!sync.ready()){toast("Чтобы изменить сообщение, подключитесь к паре");return;}
        new AlertDialog.Builder(this).setItems(new String[]{"Изменить сообщение"},(dialog,which)->editChatMessage(message)).show();
    }

    private void editChatMessage(JSONObject message){
        EditText input=new EditText(this);
        input.setText(message.optString("title",""));
        input.setMinLines(2);
        input.setGravity(Gravity.TOP|Gravity.START);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4000)});
        new AlertDialog.Builder(this).setTitle("Изменить сообщение")
                .setMessage("Партнёр увидит обновлённый текст.")
                .setView(input).setNegativeButton("Отмена",null)
                .setPositiveButton("Сохранить",(dialog,which)->{
                    String value=input.getText().toString().trim();
                    if(value.isEmpty()){toast("Пустое сообщение сохранить нельзя");return;}
                    network.execute(()->{
                        try{
                            sync.updateMessage(message.optString("id"),value);
                            JSONArray rows=read("messages");
                            JSONObject local=findItem(rows,message.optString("id"));
                            if(local!=null){
                                local.put("title",value);
                                local.put("edited_at",System.currentTimeMillis());
                                save("messages",replaceById(rows,local));
                            }
                            runOnUiThread(()->{if(isChatOpen())render();toast("Сообщение изменено");});
                        }catch(Exception e){
                            android.util.Log.w("VmesteChat","Could not edit message",e);
                            runOnUiThread(()->toast("Изменение не сохранено: "+e.getMessage()
                                    +". Проверьте, что миграция редактирования сообщений применена."));
                        }
                    });
                }).show();
    }

    private void startChatRefresh(){
        if(chatRefreshRunnable!=null)gameHandler.removeCallbacks(chatRefreshRunnable);
        if(!sync.ready()||!isChatOpen()||!activityResumed||isFinishing())return;
        chatRefreshRunnable=new Runnable(){
            @Override public void run(){
                if(!sync.ready()||!isChatOpen()||!activityResumed||isFinishing())return;
                network.execute(()->{
                    try{
                        String before=prefs.getString("messages","[]");
                        JSONArray previous=new JSONArray(before),remote=sync.fetchItems();
                        int unread=0;
                        for(int i=0;i<remote.length();i++){
                            JSONObject row=remote.optJSONObject(i);if(row==null)continue;
                            boolean incoming=row.has("body")&&!sync.userId().equals(row.optString("sender_id"));
                            if(incoming&&!containsMessage(previous,row.optString("id")))unread++;
                            sync.storeRemote(row,prefs);
                            if(incoming)archiveIncomingMessage(row);
                        }
                        boolean changed=!before.equals(prefs.getString("messages","[]"));
                        final int newCount=unread;
                        runOnUiThread(()->{
                            if(!isChatOpen()||!activityResumed)return;
                            chatScrollPolicy.onMessagesAdded(newCount,false);
                            if(changed)render();
                            else if(chatRefreshRunnable==this)gameHandler.postDelayed(this,3000);
                        });
                    }catch(Exception e){
                        android.util.Log.w("VmesteChat","Could not refresh messages",e);
                        if(isChatOpen()&&activityResumed&&chatRefreshRunnable==this)gameHandler.postDelayed(this,5000);
                    }
                });
            }
        };
        gameHandler.postDelayed(chatRefreshRunnable,1200);
    }

    private boolean containsMessage(JSONArray rows,String id){
        for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&id.equals(row.optString("id")))return true;}
        return false;
    }

    private void archiveIncomingMessage(JSONObject remote)throws Exception{
        String id=remote.optString("id"),body=remote.optString("body");
        try{
            JSONObject media=new JSONObject(body);
            if(media.optBoolean("vmeste_media")){
                String cloudPath=media.optString("path"),type=media.optString("type");
                JSONObject saved=findItem(read("messages"),id);
                JSONObject localMedia=saved==null?null:jsonObject(saved.optString("title",""));
                String existing=localMedia==null?"":localMedia.optString("local_path","");
                File existingFile=existing.startsWith("file:")?new File(Uri.parse(existing).getPath()):null;
                boolean downloaded=false;
                if(existingFile==null||!existingFile.isFile()){
                    byte[] bytes=sync.downloadAttachment(cloudPath);
                    File folder=new File(getFilesDir(),"chat_archive");
                    if(!folder.exists()&&!folder.mkdirs())throw new Exception("Не удалось создать архив чата");
                    File file=new File(folder,id+"."+attachmentExtension(type,media.optString("mime","")));
                    try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}
                    String local=Uri.fromFile(file).toString();
                    media.put("local_path",local);
                    if(saved!=null){saved.put("title",media.toString());JSONArray rows=read("messages");save("messages",replaceById(rows,saved));}
                    downloaded=true;
                }
                if(downloaded)sync.deleteAttachment(cloudPath);
            }
        }catch(org.json.JSONException notMedia){ }
        sync.acknowledgeMessage(id);
    }

    private JSONObject jsonObject(String raw){
        try{return new JSONObject(raw);}catch(Exception ignored){return null;}
    }

    private JSONArray replaceById(JSONArray rows,JSONObject replacement){
        for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&replacement.optString("id").equals(row.optString("id"))){try{rows.put(i,replacement);}catch(org.json.JSONException e){android.util.Log.w("VmesteChat","Could not update the local message cache",e);}break;}}
        return rows;
    }

    private View createChatComposer(){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(5),dp(8),dp(5));row.setBackground(glassSurface(PAPER,18));row.setElevation(dp(2));
        TextView attach=text("＋",24,PURPLE,true);attach.setGravity(Gravity.CENTER);row.addView(attach,new LinearLayout.LayoutParams(dp(48),dp(48)));
        attach.setContentDescription("Добавить фото, файл, GIF или видеокружок");
        attach.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Вложение").setItems(new String[]{"Фото, видео или аудио из телефона","GIF из телефона","Записать видеокружок (до минуты)"},(d,which)->{try{if(which==0){Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("*/*");pick.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(pick,404);}else if(which==1){Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("image/gif");pick.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(pick,408);}else startCameraCapture(405);}catch(Exception e){toast("Не удалось открыть камеру или галерею: "+e.getMessage());}}).show());
        TextView emoji=text("☺",20,PURPLE,true);emoji.setGravity(Gravity.CENTER);emoji.setContentDescription("Выбрать эмодзи");row.addView(emoji,new LinearLayout.LayoutParams(dp(48),dp(48)));
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("Сообщение партнёру…");row.addView(input,new LinearLayout.LayoutParams(0,dp(48),1));
        emoji.setOnClickListener(v->showChatEmojiPicker(input));
        input.setText(pendingSharedText);
        if(pendingSharedUri!=null){
            TextView sharedPhoto=text("📎",18,PURPLE,true);sharedPhoto.setGravity(Gravity.CENTER);
            sharedPhoto.setContentDescription("Фото из другого приложения готово к отправке");
            row.addView(sharedPhoto,new LinearLayout.LayoutParams(dp(34),dp(48)));
        }
        TextView voice=text(chatRecorder==null?"🎙":"■",22,PURPLE,true);voice.setGravity(Gravity.CENTER);voice.setContentDescription(chatRecorder==null?"Записать голосовое сообщение":"Остановить запись голосового сообщения");row.addView(voice,new LinearLayout.LayoutParams(dp(48),dp(48)));
        voice.setOnClickListener(v->{if(chatRecorder==null)startChatVoiceRecording(voice);else stopChatVoiceRecording(voice);});
        TextView send=text("➤",23,PURPLE,true);send.setGravity(Gravity.CENTER);send.setContentDescription("Отправить сообщение");row.addView(send,new LinearLayout.LayoutParams(dp(48),dp(48)));
        send.setOnClickListener(v->{
            String value=input.getText().toString().trim();
            if(pendingSharedUri!=null){
                if(sync.ready()){
                    Uri shared=pendingSharedUri;
                    String mime=getContentResolver().getType(shared);
                    String type="image/gif".equalsIgnoreCase(mime)?"GIF":"Фото";
                    confirmChatAttachment(shared,type,mime,()->{
                        pendingSharedUri=null;
                        if(isChatOpen())render();
                    });
                }
                else toast("Чтобы отправить фото партнёру, сначала подключите пару");
            }
            if(!value.isEmpty()){chatScrollPolicy.onMessagesAdded(0,true);chatScrollY=Integer.MAX_VALUE;append("messages",value,"Сейчас");}
            pendingSharedText="";
            if(!value.isEmpty()||pendingSharedUri==null)render();
        });
        return row;
    }

    private void showChatEmojiPicker(EditText input){
        String[] emojis={"😊","😂","🥰","😍","😘","❤️","💛","✨","👍","🥺","🤗","🎉","🌿","☕","🐈","💌"};
        android.widget.GridLayout grid=new android.widget.GridLayout(this);
        grid.setColumnCount(4);
        grid.setPadding(dp(12),dp(8),dp(12),dp(8));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Эмодзи").setView(grid)
                .setNegativeButton("Закрыть",null).create();
        for(String value:emojis){
            TextView button=text(value,25,INK,false);
            button.setGravity(Gravity.CENTER);
            button.setContentDescription(value);
            android.widget.GridLayout.LayoutParams params=new android.widget.GridLayout.LayoutParams();
            params.width=dp(54);params.height=dp(52);params.setMargins(dp(3),dp(3),dp(3),dp(3));
            grid.addView(button,params);
            button.setOnClickListener(v->{
                int start=Math.max(0,input.getSelectionStart()),end=Math.max(start,input.getSelectionEnd());
                input.getText().replace(start,end,value);
                dialog.dismiss();
            });
        }
        TextView keyboard=text("⌨  Другие эмодзи — на клавиатуре",13,PURPLE,true);
        keyboard.setGravity(Gravity.CENTER);
        android.widget.GridLayout.LayoutParams keyboardParams=new android.widget.GridLayout.LayoutParams();
        keyboardParams.width=dp(234);keyboardParams.height=dp(48);keyboardParams.columnSpec=android.widget.GridLayout.spec(0,4);
        grid.addView(keyboard,keyboardParams);
        keyboard.setOnClickListener(v->{
            dialog.dismiss();input.requestFocus();
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);
        });
        dialog.show();
    }

    private void confirmChatAttachment(Uri uri,String type,String mime,Runnable accepted){
        if(!sync.ready()){toast("Сначала подключите общее пространство пары");return;}
        long size=attachmentSize(uri);
        if(size>MAX_CHAT_ATTACHMENT_BYTES){toast("Файл больше 12 МБ. Выберите или сожмите файл на телефоне.");return;}
        String sizeText=size>0?formatBytes(size):"размер проверится при чтении";
        String message="Файл: "+attachmentName(uri)+" · "+sizeText
                +"\n\nПеред отправкой он останется в архиве этого телефона и загрузится в закрытое хранилище пары. После получения файла партнёром облачная копия удаляется; локальная копия остаётся. Автоматическое сжатие не выполняется.";
        new AlertDialog.Builder(this).setTitle("Отправить "+type.toLowerCase(Locale.ROOT)+"?")
                .setMessage(message).setNegativeButton("Не отправлять",(dialog,which)->{})
                .setPositiveButton("Отправить",(dialog,which)->{
                    if(accepted!=null)accepted.run();
                    uploadChatAttachment(uri,type,mime);
                }).show();
    }

    private long attachmentSize(Uri uri){
        if("file".equals(uri.getScheme())){File file=new File(uri.getPath());return file.isFile()?file.length():-1;}
        try(android.database.Cursor cursor=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.SIZE},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()){int index=cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);if(index>=0)return cursor.getLong(index);}
        }catch(Exception e){android.util.Log.i("VmesteChat","Could not inspect attachment size",e);}
        return -1;
    }

    private String attachmentName(Uri uri){
        if("file".equals(uri.getScheme()))return new File(uri.getPath()).getName();
        try(android.database.Cursor cursor=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()){int index=cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);if(index>=0)return cursor.getString(index);}
        }catch(Exception e){android.util.Log.i("VmesteChat","Could not read attachment name",e);}
        return uri.getLastPathSegment()==null?"Вложение":uri.getLastPathSegment();
    }

    private String formatBytes(long bytes){
        if(bytes<1024)return bytes+" Б";
        if(bytes<1024L*1024)return String.format(Locale.getDefault(),"%.1f КБ",bytes/1024.0);
        return String.format(Locale.getDefault(),"%.1f МБ",bytes/(1024.0*1024));
    }
    private boolean isChatOpen(){return "Чат".equals(tab)||(feature!=null&&feature.contains("Чат"));}
    private void startCameraCapture(int requestCode) {
        pendingCameraRequestCode = requestCode;
        ArrayList<String> missing=new ArrayList<>();
        if(checkSelfPermission(android.Manifest.permission.CAMERA)!=android.content.pm.PackageManager.PERMISSION_GRANTED)missing.add(android.Manifest.permission.CAMERA);
        if(requestCode==405&&checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED)missing.add(android.Manifest.permission.RECORD_AUDIO);
        if(!missing.isEmpty()){requestPermissions(missing.toArray(new String[0]),905);toast("Разрешите камеру и микрофон, затем нажмите кнопку ещё раз");return;}
        startActivityForResult(new Intent(this,CameraCaptureActivity.class).putExtra("photo_only",requestCode==406),requestCode);
    }
    private void saveAvatar(Uri uri){
        try{
            String encoded=avatarData(uri);
            byte[] image=android.util.Base64.decode(encoded.substring(encoded.indexOf(',')+1),android.util.Base64.DEFAULT);
            File target=new File(getFilesDir(),"profile-avatar.jpg");
            try(FileOutputStream output=new FileOutputStream(target)){output.write(image);output.flush();}
            String localUri=Uri.fromFile(target).toString();
            prefs.edit().putString("profile_avatar_uri",localUri).remove("profile_avatar_data")
                    .remove("profile_avatar_remote_path").putBoolean("profile_avatar_pending",true).apply();
            JSONObject profile=new JSONObject();
            profile.put("display_name",prefs.getString("profile_name",""));
            profile.put("avatar",prefs.getString("profile_avatar","♡"));
            profile.put("avatar_local_uri",localUri);
            profile.put("bio",prefs.getString("profile_bio",""));
            showApp();
            if(sync.ready())persistProfile(profile);
            else toast("Фото сохранено на телефоне; подключите пару, чтобы синхронизировать его");
        }catch(Exception e){toast("Не удалось сохранить фото профиля: "+e.getMessage());}
    }
    private void confirmAccountDeletion() {
        new AlertDialog.Builder(this).setTitle("Удалить аккаунт?")
                .setMessage("Аккаунт Supabase и доступ к общему пространству будут удалены. Это действие нельзя отменить.")
                .setNegativeButton("Отмена",null)
                .setPositiveButton("Удалить",(d,w)->runNetwork(()->{sync.deleteAccount();return true;},ok->{prefs.edit().clear().apply();toast("Аккаунт удалён");render();}))
                .show();
    }
    private void startChatVoiceRecording(TextView button){
        if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},904);toast("Разрешите микрофон, затем нажмите 🎙 ещё раз");return;}
        try{chatRecording=new java.io.File(getCacheDir(),"voice-"+System.currentTimeMillis()+".3gp");chatRecorder=new android.media.MediaRecorder();chatRecorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);chatRecorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP);chatRecorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AMR_NB);chatRecorder.setOutputFile(chatRecording.getAbsolutePath());chatRecorder.prepare();chatRecorder.start();button.setText("■");button.setContentDescription("Остановить запись голосового сообщения");toast("Запись началась · после остановки проверьте размер и подтвердите отправку");}catch(Exception e){if(chatRecorder!=null){chatRecorder.release();chatRecorder=null;}button.setContentDescription("Записать голосовое сообщение");toast("Не удалось записать голосовое: "+e.getMessage());}
    }
    private void stopChatVoiceRecording(TextView button){
        try{
            chatRecorder.stop();chatRecorder.release();chatRecorder=null;button.setText("🎙");button.setContentDescription("Записать голосовое сообщение");
            if(chatRecording!=null){
                File recording=chatRecording;
                confirmChatAttachment(Uri.fromFile(recording),"Голосовое","audio/3gpp",()->{});
                chatRecording=null;
            }
        }catch(Exception e){
            toast("Запись слишком короткая, повторите ещё раз");
            if(chatRecorder!=null){chatRecorder.release();chatRecorder=null;}
            button.setText("🎙");button.setContentDescription("Записать голосовое сообщение");
        }
    }
    private void uploadChatAttachment(Uri uri,String type){uploadChatAttachment(uri,type,getContentResolver().getType(uri));}
    private void uploadChatAttachment(Uri uri,String type,String mime){
        if(!sync.ready()){toast("Сначала подключите общее пространство пары");return;}
        if(mime==null||mime.isEmpty())mime=defaultChatMime(type);
        final String uploadMime=mime;
        String id=UUID.randomUUID().toString();toast("Отправляю "+type.toLowerCase(Locale.ROOT)+"…");network.execute(()->{try{Uri archive=archiveChatAttachment(uri,id,type,uploadMime);String path=sync.uploadAttachment(archive,id,this,uploadMime);JSONObject media=new JSONObject();media.put("vmeste_media",true);media.put("type",type);media.put("mime",uploadMime);media.put("path",path);media.put("local_path",archive.toString());JSONObject item=new JSONObject();item.put("id",id);item.put("title",media.toString());item.put("detail","Сейчас");item.put("ts",System.currentTimeMillis());item.put("done",false);item.put("sender_id",sync.userId());JSONArray rows=read("messages");rows.put(item);save("messages",rows);syncItem("messages",item);runOnUiThread(()->{if(isChatOpen())render();});}catch(Exception e){runOnUiThread(()->toast("Не удалось отправить вложение: "+e.getMessage()));}});
    }
    private String defaultChatMime(String type){
        if("GIF".equals(type))return "image/gif";
        if("Голосовое".equals(type))return "audio/3gpp";
        if("Кружок".equals(type))return "video/webm";
        if("Фото".equals(type))return "image/jpeg";
        return "video/mp4";
    }
    private String attachmentExtension(String type,String mime){
        if("GIF".equals(type)||"image/gif".equalsIgnoreCase(mime))return "gif";
        if("Голосовое".equals(type)||"audio/3gpp".equalsIgnoreCase(mime))return "3gp";
        if("Кружок".equals(type)||"video/webm".equalsIgnoreCase(mime))return "webm";
        if("Фото".equals(type))return "jpg";
        String extension=android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        return extension==null||extension.isEmpty()?"bin":extension.replaceAll("[^A-Za-z0-9]","");
    }
    private Uri archiveChatAttachment(Uri source,String id,String type,String mime)throws Exception{
        File folder=new File(getFilesDir(),"chat_archive");if(!folder.exists()&&!folder.mkdirs())throw new Exception("Не удалось создать архив чата");
        File target=new File(folder,id+"."+attachmentExtension(type,mime));
        try(InputStream in=getContentResolver().openInputStream(source);FileOutputStream out=new FileOutputStream(target)){
            if(in==null)throw new Exception("Файл не найден");
            byte[] buf=new byte[8192];int n,total=0;
            while((n=in.read(buf))>0){total+=n;if(total>MAX_CHAT_ATTACHMENT_BYTES)throw new Exception("Файл больше 12 МБ");out.write(buf,0,n);}
            if(total==0)throw new Exception("Выбранный файл пуст");
        }
        return Uri.fromFile(target);
    }
    private void playChatAttachment(String path,String type){
        try{Uri uri=Uri.parse(path);if("file".equals(uri.getScheme())){showChatAttachment(uri,type);return;}}catch(Exception ignored){}network.execute(()->{try{byte[] bytes=sync.downloadAttachment(path);File folder=new File(getFilesDir(),"chat_archive");if(!folder.exists()&&!folder.mkdirs())throw new Exception("Не удалось создать архив чата");File file=new File(folder,"received-"+UUID.randomUUID()+"."+attachmentExtension(type,""));try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}runOnUiThread(()->showChatAttachment(Uri.fromFile(file),type));}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});
    }
    private void showChatAttachment(Uri uri,String type){try{if("Голосовое".equals(type)){android.media.MediaPlayer player=new android.media.MediaPlayer();player.setDataSource(this,uri);player.setOnCompletionListener(android.media.MediaPlayer::release);player.prepare();player.start();toast("Воспроизводится голосовое сообщение");}else if("Фото".equals(type)){ImageView image=new ImageView(this);image.setImageURI(uri);image.setAdjustViewBounds(true);new AlertDialog.Builder(this).setTitle("Фото от партнёра").setView(image).setPositiveButton("Закрыть",null).show();}else if("GIF".equals(type)){showChatGif(uri);}else if("Кружок".equals(type)){android.webkit.WebView player=new android.webkit.WebView(this);player.getSettings().setJavaScriptEnabled(true);player.getSettings().setAllowFileAccess(true);String page="<meta name='viewport' content='width=device-width,initial-scale=1'><style>body{margin:0;background:#211d25;display:grid;place-items:center;height:100vh}video{width:88vw;height:88vw;max-width:440px;max-height:440px;border-radius:50%;object-fit:cover}</style><video controls autoplay playsinline src='"+uri.toString()+"'></video>";player.loadDataWithBaseURL("file:///",page,"text/html","UTF-8",null);new AlertDialog.Builder(this).setTitle("Видеокружок").setView(player).setPositiveButton("Закрыть",null).show();}else if("Видео".equals(type)){android.widget.VideoView video=new android.widget.VideoView(this);video.setVideoURI(uri);new AlertDialog.Builder(this).setTitle("Видео от партнёра").setView(video).setPositiveButton("Закрыть",null).show();video.start();}else{android.media.MediaPlayer player=new android.media.MediaPlayer();player.setDataSource(this,uri);player.setOnCompletionListener(android.media.MediaPlayer::release);player.prepare();player.start();toast("Воспроизводится аудио");}}catch(Exception e){toast("Не удалось воспроизвести вложение: "+e.getMessage());}}

    private void showChatGif(Uri uri)throws Exception{
        View content;
        android.graphics.drawable.AnimatedImageDrawable animated=null;
        if(android.os.Build.VERSION.SDK_INT>=28){
            android.graphics.ImageDecoder.Source source=android.graphics.ImageDecoder.createSource(getContentResolver(),uri);
            android.graphics.drawable.Drawable drawable=android.graphics.ImageDecoder.decodeDrawable(source);
            ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setImageDrawable(drawable);content=image;
            if(drawable instanceof android.graphics.drawable.AnimatedImageDrawable)animated=(android.graphics.drawable.AnimatedImageDrawable)drawable;
        }else{
            android.graphics.Movie movie;
            try(InputStream input=getContentResolver().openInputStream(uri)){movie=android.graphics.Movie.decodeStream(input);}
            if(movie==null){ImageView image=new ImageView(this);image.setImageURI(uri);image.setAdjustViewBounds(true);content=image;}
            else{
                android.graphics.Movie gif=movie;
                content=new View(this){
                    private final long started=android.os.SystemClock.uptimeMillis();
                    @Override protected void onDraw(android.graphics.Canvas canvas){
                        super.onDraw(canvas);
                        int duration=Math.max(1,gif.duration()),time=(int)((android.os.SystemClock.uptimeMillis()-started)%duration);
                        gif.setTime(time);
                        float scale=Math.min((float)getWidth()/gif.width(),(float)getHeight()/gif.height());
                        canvas.save();canvas.scale(scale,scale);gif.draw(canvas,0,0);canvas.restore();
                        postInvalidateDelayed(33);
                    }
                };
                content.setMinimumHeight(dp(220));
            }
        }
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("GIF")
                .setView(content).setPositiveButton("Закрыть",null).create();
        android.graphics.drawable.AnimatedImageDrawable playback=animated;
        dialog.setOnShowListener(v->{if(android.os.Build.VERSION.SDK_INT>=28&&playback!=null)playback.start();});
        dialog.setOnDismissListener(v->{if(android.os.Build.VERSION.SDK_INT>=28&&playback!=null)playback.stop();});
        dialog.show();
    }

    private void renderEditableEntry(String key,JSONArray list,int index,String icon) {
        JSONObject item=list.optJSONObject(index);if(item==null)return;
        LinearLayout row=card();
        if("plans".equals(key)){
            android.widget.CheckBox done=new android.widget.CheckBox(this);done.setText(item.optBoolean("done")?"Выполнено":"Отметить выполнение");done.setChecked(item.optBoolean("done"));done.setButtonTintList(android.content.res.ColorStateList.valueOf(PURPLE));row.addView(done);
            done.setOnCheckedChangeListener((b,on)->{try{item.put("done",on);item.put("completed_at",on?System.currentTimeMillis():0);replaceItem(key,item);render();}catch(Exception ignored){}});
        }
        TextView title=text(icon+"  "+item.optString("title"),15,INK,true);row.addView(title);
        String category=item.optString("category","");String detail=item.optString("detail","");
        if(!category.isEmpty())row.addView(text("Раздел: "+category,12,PURPLE,true));
        if(!detail.isEmpty())row.addView(text(detail,12,MUTED,false));
        if(item.has("photo_path")){
            row.addView(text("📷 Фото-отчёт",12,PURPLE,true));ImageView photo=new ImageView(this);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);photo.setBackground(shape(PALE,16));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(170));pp.topMargin=dp(8);row.addView(photo,pp);
            if(sync.ready()){String path=item.optString("photo_path");network.execute(()->{try{byte[] bytes=sync.downloadAttachment(path);android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length);runOnUiThread(()->{if(bitmap!=null)photo.setImageBitmap(bitmap);});}catch(Exception e){android.util.Log.w("VmesteSync","Could not load plan photo",e);}});}
        }
        row.setOnClickListener(v->editItemDialog(key,item));
        row.setOnLongClickListener(v->{itemActions(key,item);return true;});
    }
    private JSONArray read(String key) { try { return new JSONArray(prefs.getString(key, "[]")); } catch(Exception e) { return new JSONArray(); } }
    private void save(String key, JSONArray items) { prefs.edit().putString(key, items.toString()).apply(); }
    private boolean isPersonalCalendarKey(String key){return key.startsWith("personal_");}
    private void syncItem(String key,JSONObject item){if("notes".equals(key)||isPersonalCalendarKey(key)||sync==null||item==null)return;if("events".equals(key)&&item.optBoolean("remind_partner"))prefs.edit().putBoolean("owned_partner_reminder_"+item.optString("id"),true).apply();try{JSONArray pending=new JSONArray(prefs.getString("pending_shared_sync","[]"));JSONObject queued=new JSONObject();queued.put("key",key);queued.put("item",new JSONObject(item.toString()));boolean found=false;for(int i=0;i<pending.length();i++){JSONObject e=pending.optJSONObject(i);if(e==null)continue;JSONObject ei=e.optJSONObject("item");if(ei==null)continue;if(item.optString("id").equals(ei.optString("id"))){pending.put(i,queued);found=true;break;}}if(!found)pending.put(queued);prefs.edit().putString("pending_shared_sync",pending.toString()).apply();}catch(Exception ignored){}flushPendingSync();}
    private void replaceItem(String key,JSONObject item){JSONArray rows=read(key);for(int i=0;i<rows.length();i++){JSONObject old=rows.optJSONObject(i);if(old!=null&&old.optString("id").equals(item.optString("id"))){try{rows.put(i,item);save(key,rows);syncItem(key,item);}catch(Exception ignored){}return;}}}
    private void removeRemoteItem(String id){if(sync!=null&&sync.ready())network.execute(()->{try{sync.deleteItem(id);}catch(Exception e){android.util.Log.w("VmesteSync","Could not delete shared item",e);}});}
    private void removeItem(String key,String id){JSONArray rows=read(key);for(int i=rows.length()-1;i>=0;i--){JSONObject item=rows.optJSONObject(i);if(item!=null&&id.equals(item.optString("id"))){rows.remove(i);break;}}save(key,rows);if(!isPersonalCalendarKey(key))removeRemoteItem(id);}
    private void append(String key, String title, String detail) {
        JSONArray list=read(key); JSONObject item=new JSONObject();
        try { item.put("id", UUID.randomUUID().toString()); item.put("title", title); item.put("detail", detail); item.put("done", false); item.put("ts", System.currentTimeMillis()); if ("messages".equals(key)) item.put("sender_id", sync.userId()); if("gifts".equals(key))item.put("owner_id",sync.userId()); list.put(item); } catch(Exception ignored) { }
        save(key,list);
        if (!"notes".equals(key)) {
            JSONObject row = list.optJSONObject(list.length() - 1);
            if (row != null) syncItem(key,row);
        }
    }

    private void itemActions(String key,JSONObject item){
        ArrayList<String> options=new ArrayList<>();options.add("Редактировать");if("plans".equals(key))options.add("Добавить фото-отчёт");options.add("Удалить");
        String[] choices=options.toArray(new String[0]);new AlertDialog.Builder(this).setItems(choices,(d,which)->{
            String choice=choices[which];if(choice.equals("Редактировать")){
                if("notes".equals(key))editNoteDialog(item);
                else if("recipes".equals(key))editRecipeDialog(item);
                else editItemDialog(key,item);
                return;
            }
            if(choice.equals("Добавить фото-отчёт")){pendingPhotoKey=key;pendingPhotoItemId=item.optString("id");Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("image/*");pick.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(pick,403);return;}
            new AlertDialog.Builder(this).setMessage("Удалить «"+item.optString("title")+"»?").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(x,y)->{removeItem(key,item.optString("id"));render();}).show();
        }).show();
    }

    private void editItemDialog(String key,JSONObject item){
        if("wishes".equals(key)){editWishPlanDialog(item);return;}
        LinearLayout form=column();form.setPadding(dp(18),0,dp(18),0);
        EditText title=new EditText(this);title.setSingleLine(true);title.setHint("Название");title.setText(item.optString("title"));form.addView(title);
        EditText detail=new EditText(this);detail.setHint("Описание или заметка");detail.setText(item.optString("detail"));form.addView(detail);
        EditText category=new EditText(this);category.setSingleLine(true);category.setHint("Раздел / категория");category.setText(item.optString("category"));if("wishes".equals(key))form.addView(category);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Изменить запись").setView(form).setNegativeButton("Отмена",null).setNeutralButton("Удалить",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(v->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{String value=title.getText().toString().trim();if(value.isEmpty()){title.setError("Введите название");return;}try{item.put("title",value);item.put("detail",detail.getText().toString().trim());if("wishes".equals(key))item.put("category",category.getText().toString().trim());replaceItem(key,item);dialog.dismiss();render();}catch(Exception ignored){}});dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(x->{removeItem(key,item.optString("id"));dialog.dismiss();render();});});dialog.show();
    }
    private void addDialog(String key, String title) {
        LinearLayout form = column(); form.setPadding(dp(22), dp(8), dp(22), dp(2));
        EditText name = new EditText(this); name.setHint("loyalty".equals(key) ? "Название карты, например Пеликан" : "gifts".equals(key)?"Название подарка или ссылка":"Что хотите сохранить?"); name.setSingleLine(true); form.addView(name);
        EditText detail = new EditText(this); detail.setHint("loyalty".equals(key) ? "Номер карты или штрихкод" : "gifts".equals(key)?"Ссылка на магазин или заметка":"Заметка или дата (необязательно)"); form.addView(detail);
        EditText category = new EditText(this); category.setSingleLine(true); category.setHint("Раздел / категория"); if ("wishes".equals(key)) form.addView(category);
        new AlertDialog.Builder(this).setTitle(title).setView(form).setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (d,w) -> { String t=name.getText().toString().trim(), extra=detail.getText().toString().trim(); if (!t.isEmpty()) { append(key,t,extra); JSONArray rows=read(key);JSONObject item=rows.optJSONObject(rows.length()-1);if(item!=null)try{if("wishes".equals(key))item.put("category",category.getText().toString().trim());if("gifts".equals(key)){String url=giftUrl(extra,t);if(!url.isEmpty()){item.put("url",url);item.put("detail",url);if(giftUrl("",t).equals(url)){item.put("title_from_url",true);try{item.put("title",new java.net.URL(url).getHost().replaceFirst("^www\\.",""));}catch(Exception ignored){}};}save(key,rows);syncItem(key,item);if(!url.isEmpty())enrichGiftLink(item,url);}else if("wishes".equals(key)){save(key,rows);syncItem(key,item);}}catch(Exception ignored){}render(); } }).show();
    }
    private String giftUrl(String detail,String title){
        for(String raw:new String[]{detail,title}){if(raw==null)continue;java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)(https?://[^\\s]+|www\\.[^\\s]+|(?:[a-z0-9-]+\\.)+[a-z]{2,}(?:/[^\\s]*)?)").matcher(raw.trim());if(!m.find())continue;String candidate=m.group(1).replaceAll("[),.!?;]+$","");if(!candidate.matches("(?i)^https?://.*"))candidate="https://"+candidate;String safeUrl=GiftPreviewUrl.normalizePublicHttpUrl(candidate);if(!safeUrl.isEmpty())return safeUrl;}
        return "";
    }
    private android.graphics.Bitmap loadGiftBitmap(String imageUrl){
        java.net.HttpURLConnection connection=null;try{connection=openPublicGiftConnection(imageUrl);int status=connection.getResponseCode();String contentType=connection.getContentType();if(status<200||status>=300||contentType==null||!contentType.toLowerCase(Locale.ROOT).startsWith("image/"))return null;int length=connection.getContentLength();if(length>8*1024*1024)return null;java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();try(java.io.InputStream input=connection.getInputStream()){byte[] buffer=new byte[8192];int count,total=0;while((count=input.read(buffer))!=-1&&total<8*1024*1024){out.write(buffer,0,count);total+=count;}}byte[] bytes=out.toByteArray();android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);if(bounds.outWidth<=0||bounds.outHeight<=0)return null;int sample=1;while(bounds.outWidth/sample>1200||bounds.outHeight/sample>1200)sample*=2;android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();opts.inSampleSize=sample;return android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);}catch(Exception e){android.util.Log.i("VmesteGift","Could not load gift image",e);return null;}finally{if(connection!=null)connection.disconnect();}
    }
    private void enrichGiftLink(JSONObject gift,String url){
        network.execute(()->{java.net.HttpURLConnection connection=null;try{connection=openPublicGiftConnection(url);int status=connection.getResponseCode();if(status<200||status>=300)throw new java.io.IOException("Ссылка вернула HTTP "+status);String contentType=connection.getContentType();if(contentType==null||!(contentType.toLowerCase(Locale.ROOT).contains("text/html")||contentType.toLowerCase(Locale.ROOT).contains("application/xhtml+xml")))throw new java.io.IOException("Ссылка не ведёт на HTML-страницу");String charset="UTF-8";java.util.regex.Matcher cm=java.util.regex.Pattern.compile("(?i)charset=([^; ]+)").matcher(contentType);if(cm.find())charset=cm.group(1).replace("\\\"","");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();try(java.io.InputStream input=connection.getInputStream()){byte[] buffer=new byte[8192];int count,total=0;while((count=input.read(buffer))!=-1&&total<512*1024){out.write(buffer,0,count);total+=count;}}String html=new String(out.toByteArray(),java.nio.charset.Charset.forName(charset));String pageUrl=connection.getURL().toString();String title=meta(html,"og:title");if(title.isEmpty())title=meta(html,"twitter:title");if(title.isEmpty()){java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?is)<title[^>]*>(.*?)</title>").matcher(html);if(m.find())title=m.group(1).replaceAll("(?is)<[^>]+>"," ").replaceAll("\\s+"," ").trim();}title=android.text.Html.fromHtml(title,android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim();String image=meta(html,"og:image:secure_url");if(image.isEmpty())image=meta(html,"og:image");if(image.isEmpty())image=meta(html,"twitter:image");if(!image.isEmpty()){String imageUrl=new java.net.URL(new java.net.URL(pageUrl),android.text.Html.fromHtml(image,android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()).toString();image=GiftPreviewUrl.normalizePublicHttpUrl(imageUrl);}if(!title.isEmpty()&&gift.optBoolean("title_from_url")){gift.put("title",title);gift.put("title_from_url",false);}if(!image.isEmpty())gift.put("preview_image",image);replaceItem("gifts",gift);runOnUiThread(()->{if(feature!=null&&feature.contains("Подарочный радар"))render();});}catch(Exception e){android.util.Log.i("VmesteGift","Could not load product preview",e);try{if(gift.optBoolean("title_from_url")){gift.put("title",new java.net.URL(url).getHost().replaceFirst("^www\\.",""));gift.put("title_from_url",false);replaceItem("gifts",gift);runOnUiThread(()->{if(feature!=null)render();});}}catch(Exception ignored){}}finally{if(connection!=null)connection.disconnect();}});
    }
    private java.net.HttpURLConnection openPublicGiftConnection(String rawUrl) throws java.io.IOException {
        String safeUrl=GiftPreviewUrl.normalizePublicHttpUrl(rawUrl);
        if(safeUrl.isEmpty())throw new java.io.IOException("Можно загружать только публичные ссылки http/https");
        java.net.URL target=new java.net.URL(safeUrl);
        for(int redirects=0;redirects<=5;redirects++){
            if(!GiftPreviewUrl.resolvesToPublicAddresses(target.toString()))throw new java.io.IOException("Адрес не является публичным");
            java.net.HttpURLConnection connection=(java.net.HttpURLConnection)target.openConnection();
            connection.setConnectTimeout(9000);connection.setReadTimeout(11000);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent","Mozilla/5.0 VmesteApp");
            int status;
            try{status=connection.getResponseCode();}catch(java.io.IOException e){connection.disconnect();throw e;}
            if(status==301||status==302||status==303||status==307||status==308){
                String location=connection.getHeaderField("Location");connection.disconnect();
                if(location==null||redirects==5)throw new java.io.IOException("Слишком много перенаправлений");
                String redirected=GiftPreviewUrl.normalizePublicHttpUrl(new java.net.URL(target,location).toString());
                if(redirected.isEmpty())throw new java.io.IOException("Сайт перенаправил на непубличный адрес");
                target=new java.net.URL(redirected);continue;
            }
            return connection;
        }
        throw new java.io.IOException("Слишком много перенаправлений");
    }
    private String meta(String html,String property){java.util.regex.Matcher tags=java.util.regex.Pattern.compile("(?is)<meta\\b[^>]*>").matcher(html);while(tags.find()){String tag=tags.group();String key=attribute(tag,"property");if(key.isEmpty())key=attribute(tag,"name");if(property.equalsIgnoreCase(key))return attribute(tag,"content");}return "";}
    private String attribute(String tag,String name){java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?is)(?:^|\\s)"+java.util.regex.Pattern.quote(name)+"\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))").matcher(tag);if(!m.find())return "";for(int i=1;i<=3;i++)if(m.group(i)!=null)return m.group(i).trim();return "";}
    private void addTask(LinearLayout box, JSONArray list, int index) {
        addTask(box,list,index,"tasks");
    }
    private void addTask(LinearLayout box, JSONArray list, int index, String storeKey) {
        JSONObject item=list.optJSONObject(index); if(item==null)return;
        android.widget.CheckBox check=new android.widget.CheckBox(this);
        check.setText(item.optString("title")); check.setTextSize(14); check.setTextColor(INK);
        check.setButtonTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        check.setChecked(item.optBoolean("done")); box.addView(check);
        check.setOnCheckedChangeListener((b,on)->{JSONArray current=read(storeKey);JSONObject o=findItem(current,item.optString("id"));if(o!=null){try{o.put("done",on);o.put("completed_at",on?System.currentTimeMillis():0);replaceItem(storeKey,o);if("tasks".equals(storeKey))render();}catch(Exception ignored){}}});
        check.setOnLongClickListener(v->{itemActions(storeKey,item);return true;});
    }
    private JSONObject findItem(JSONArray rows,String id){for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&id.equals(row.optString("id")))return row;}return null;}
    private void addCheck(LinearLayout box, String value, boolean checked, int index) {
        android.widget.CheckBox check=new android.widget.CheckBox(this); check.setText(value); check.setTextSize(14); check.setTextColor(INK);
        check.setButtonTintList(android.content.res.ColorStateList.valueOf(PURPLE)); check.setChecked(checked); box.addView(check);
        if (index > 0 || read("tasks").length() > 0) check.setOnCheckedChangeListener((b, on) -> {
            JSONArray list=read("tasks"); JSONObject o=list.optJSONObject(index);
            if(o!=null) { try { o.put("done",on); save("tasks",list); } catch(Exception ignored) { } }
        });
    }
    private void simpleCard(String icon, String title, String sub, Runnable click) {
        LinearLayout c=card(); c.setOrientation(LinearLayout.HORIZONTAL); c.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge=text(icon,20,PURPLE,true); badge.setGravity(Gravity.CENTER); badge.setBackground(shape(PALE,15)); c.addView(badge,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout copy=column(); copy.setPadding(dp(12),0,0,0); copy.addView(text(title,15,INK,true)); if(sub!=null&&!sub.isEmpty()){TextView desc=text(sub,12,MUTED,false); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(3);copy.addView(desc,p);}
        c.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        if(click!=null) {
            c.setContentDescription(title + (sub == null || sub.isEmpty() ? "" : ". " + sub) + ". Открыть");
            c.setFocusable(true);
            c.setClickable(true);
            c.setOnClickListener(v->click.run());
        }
    }
    private LinearLayout card() {
        LinearLayout c=column(); c.setPadding(dp(17),dp(16),dp(17),dp(16));         c.setBackground(glassSurface(PAPER,22)); c.setElevation(dp(2));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(9);body.addView(c,p);return c;
    }
    private void heading(String title,String sub) {
        TextView h=text(title,19,INK,true); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(13);p.bottomMargin=dp(3);body.addView(h,p);
        if(sub!=null){TextView s=text(sub,12,MUTED,false);LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(-1,-2);q.bottomMargin=dp(11);body.addView(s,q);}
    }
    private TextView pill(String value,int color) { TextView b=text(value,13,color,true);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(48));b.setPadding(dp(16),dp(11),dp(16),dp(11));b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(softDark?0x667F688E:0x557F58A4),glassSurface(PALE,22),null));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.bottomMargin=dp(10);b.setLayoutParams(p);return b; }
    private GradientDrawable glassSurface(int fill,int radius) {
        GradientDrawable surface=new GradientDrawable();
        surface.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        surface.setColors(softDark
                ? new int[]{0xF74A414F,0xE83A3340,0xF4332D39}
                : new int[]{0xF8FFFFFF,0xEAFBF8FD,0xF4F4EFF8});
        surface.setCornerRadius(dp(radius));
        surface.setStroke(dp(1),softDark?0x88988BA2:0xD6FFFFFF);
        return surface;
    }
    private void addGap(LinearLayout parent,int h) { View gap=new View(this);parent.addView(gap,new LinearLayout.LayoutParams(1,dp(h))); }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==IntentIntegrator.REQUEST_CODE){
            if(pendingWatchRoomScan) {
                pendingWatchRoomScan=false;
                IntentResult roomResult=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);
                if(resultCode==RESULT_OK&&roomResult!=null&&roomResult.getContents()!=null)
                    openSelectedWatchRoomFromQr(roomResult.getContents());
                else toast("Сканирование QR-кода комнаты отменено");
                return;
            }
            if(pendingLoyaltyScan) {
                if(resultCode==RESULT_OK&&data!=null) onLoyaltyScanResult(data);
                else {
                    pendingLoyaltyScan=false;
                    toast("Сканирование карты отменено");
                }
                return;
            }
            IntentResult result=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);
            if(result==null||result.getContents()==null){toast("Сканирование QR-кода отменено");return;}
            joinCoupleWithCode(result.getContents());return;
        }
        if(requestCode==405&&resultCode==RESULT_OK&&data!=null&&data.hasExtra("path")){Uri uri=Uri.fromFile(new File(data.getStringExtra("path")));confirmChatAttachment(uri,"Кружок","video/webm",null);return;}
        if(requestCode==406&&resultCode==RESULT_OK&&data!=null){if(data.hasExtra("path")){saveAvatar(Uri.fromFile(new java.io.File(data.getStringExtra("path"))));return;}if(data.getData()!=null){Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}saveAvatar(uri);return;}}
        if((requestCode==404||requestCode==408)&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();String mime=getContentResolver().getType(uri);
            String type=requestCode==408||"image/gif".equalsIgnoreCase(mime)?"GIF"
                    :mime!=null&&mime.startsWith("image/")?"Фото"
                    :mime!=null&&mime.startsWith("video/")?"Видео":"Голосовое";
            confirmChatAttachment(uri,type,mime,null);return;
        }
        if(requestCode==403&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();String key=pendingPhotoKey,id=pendingPhotoItemId;
            if(!sync.ready()){toast("Фото-отчёт можно синхронизировать после подключения пары");return;}
            toast("Загружаю фото-отчёт…");network.execute(()->{try{String path=sync.uploadAttachment(uri,id,this);runOnUiThread(()->{JSONObject item=findItem(read(key),id);if(item!=null)try{item.put("photo_path",path);item.put("reported_at",System.currentTimeMillis());replaceItem(key,item);render();toast("Фото прикреплено к плану для вас двоих");}catch(Exception e){toast(e.getMessage());}});}catch(Exception e){runOnUiThread(()->toast("Не удалось загрузить фото: "+e.getMessage()));}});return;
        }
        if(requestCode==407&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            String caption=pendingStoryCaption;pendingStoryCaption="";publishStory(uri,caption);return;
        }
        if(requestCode==402&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null) {
            Uri uri=data.getData();
            try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
            catch(Exception ignored){}
            publishSharedFile(uri);return;
        }
        if(requestCode==409){
            if(resultCode==RESULT_OK&&data!=null&&data.getData()!=null)saveSharedFileCopy(data.getData());
            else clearPendingFileDownload();
        }
    }

    private void publishSharedFile(Uri uri){
        if(!sync.ready()){toast("Чтобы передать файл партнёру, сначала войдите и подключите пару");return;}
        String name=uri.getLastPathSegment();
        long size=-1;
        try(android.database.Cursor cursor=getContentResolver().query(uri,
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME,
                        android.provider.OpenableColumns.SIZE},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()){
                int nameColumn=cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                int sizeColumn=cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if(nameColumn>=0)name=cursor.getString(nameColumn);
                if(sizeColumn>=0&&!cursor.isNull(sizeColumn))size=cursor.getLong(sizeColumn);
            }
        }catch(Exception e){android.util.Log.w("VmesteFiles","Could not read selected file metadata",e);}
        name=safeFileName(name);
        if(size>12L*1024*1024){toast("Файлы больше 12 МиБ пока нельзя передать");return;}
        String mime=getContentResolver().getType(uri);
        if(mime==null||mime.isEmpty()){
            String extension=android.webkit.MimeTypeMap.getFileExtensionFromUrl(name);
            mime=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        if(mime==null||mime.isEmpty())mime="application/octet-stream";
        final String selectedName=name,selectedMime=mime;
        final long selectedSize=size;
        String id=UUID.randomUUID().toString();
        toast("Загружаю файл для вашей пары…");
        network.execute(()->{try{
            String path=sync.uploadAttachment(uri,id,this,selectedMime);
            JSONObject item=new JSONObject();
            item.put("id",id);item.put("title",selectedName);item.put("mime",selectedMime);
            item.put("path",path);item.put("size_bytes",selectedSize);
            item.put("detail",selectedSize>=0?android.text.format.Formatter.formatShortFileSize(this,selectedSize)
                    :"Размер уточняется при загрузке");
            item.put("created_at",System.currentTimeMillis());
            JSONArray rows=read("files");rows.put(item);save("files",rows);syncItem("files",item);
            runOnUiThread(()->{render();toast("Файл передан в общее хранилище");});
        }catch(Exception e){runOnUiThread(()->toast("Файл не передан: "+e.getMessage()));}});
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        boolean granted=results.length>0&&results[0]==android.content.pm.PackageManager.PERMISSION_GRANTED;
        if(requestCode==905){
            if(granted)startCameraCapture(pendingCameraRequestCode);
            else toast("Разрешите камеру и микрофон, чтобы использовать фото/видео.");
            pendingCameraRequestCode=0;
            return;
        }
        if(requestCode==904){
            if(!granted)toast("Разрешите микрофон, чтобы записывать голосовые.");
            return;
        }
        if(requestCode==903||requestCode==8101){
            if(!granted)toast("Уведомления отключены — приглашения и напоминания могут не приходить.");
        }
    }
    @Override public void onBackPressed() {
        if(firstRunWelcomeVisible){completeFirstRunWelcome();return;}
        if(feature!=null){feature=null;render();} else if(!tab.equals("Главная")){tab="Главная";render();} else super.onBackPressed();
    }

    private void onlineConnectFour(){
        if(!pairGames.ready()){toast("Войдите в аккаунт и подключитесь к партнёру по коду приглашения");return;}
        toast("Подключаем «Четыре в ряд»…");network.execute(()->{try{JSONObject g=openOrJoinGame("connect_four");safeUi(()->showOnlineConnectFour(g));}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});
    }
    private void showOnlineConnectFour(JSONObject initial){
        final JSONObject[] game={initial};final boolean[] closed={false};final Runnable[] poll={null};
        final boolean[] moveBusy={false};
        final int ROWS=6,COLS=7;
        LinearLayout panel=column();panel.setPadding(dp(10),dp(6),dp(10),dp(6));
        TextView status=text("",14,INK,true);status.setGravity(Gravity.CENTER);status.setPadding(dp(10),dp(10),dp(10),dp(10));status.setBackground(shape(PALE,14));panel.addView(status);
        LinearLayout board=column();board.setGravity(Gravity.CENTER);board.setPadding(dp(6),dp(6),dp(6),dp(6));board.setBackground(shape(0xFF214D6B,20));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(8);panel.addView(board,bp);
        TextView restart=pill("Новая партия",PURPLE);restart.setGravity(Gravity.CENTER);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(10);panel.addView(restart,rp);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Четыре в ряд · онлайн").setView(panel).setNegativeButton("Закрыть",null).create();
        Runnable[] draw={null};
        draw[0]=()->{
            JSONObject g=game[0];
            JSONObject s=g.optJSONObject("state");
            if(s==null)return;
            final String me=pairGames.userId();
            final String p1=s.optString("p1");
            final String p2=s.optString("p2");
            final String boardState=s.optString("board",emptyBoard(42));
            final String turn=s.optString("turn");
            final String winner=s.optString("winner");
            final String firstPlayer=GameStartRules.firstPlayerOrPlayerOne(s.optString("first_player"),p1);
            final char myToken=GameStartRules.connectFourTokenForPlayer(me,firstPlayer,p1);
            final char partnerToken=myToken=='R'?'B':'R';
            final boolean active="active".equals(g.optString("status"));
            final boolean participant=me.equals(p1)||me.equals(p2);
            if(p2.isEmpty())status.setText("Игра создана. Ждём партнёра.");
            else if("draw".equals(winner))status.setText("Ничья!");
            else if(!winner.isEmpty())status.setText(winner.equals(me)?"Вы победили! 🎉":"Партнёр победил.");
            else if(!active)status.setText("Партия завершена");
            else if(!participant)status.setText("Вы не участник этой партии");
            else status.setText((moveBusy[0]?"Сохраняю ход…":turn.equals(me)
                    ?"Ваш ход — выберите колонку":"Ход партнёра")
                    +" · вы "+(myToken=='R'?"красные":"синие")
                    +" / партнёр "+(partnerToken=='R'?"красные":"синие"));
            restart.setVisibility(participant&&!p2.isEmpty()&&"finished".equals(g.optString("status"))?View.VISIBLE:View.GONE);
            board.removeAllViews();
            int cellSize=Math.max(dp(34),Math.min(dp(48),
                    (getResources().getDisplayMetrics().widthPixels-dp(80))/COLS));
            for(int r=0;r<ROWS;r++){
                LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);board.addView(row);
                for(int c=0;c<COLS;c++){
                    final int col=c;
                    int i=r*COLS+c;
                    char mark=i<boardState.length()?boardState.charAt(i):'.';
                    FrameLayout cell=new FrameLayout(this);
                    cell.setFocusable(true);
                    int holeSize=(int)(cellSize*.78f);
                    View hole=new View(this);
                    hole.setBackground(gameDisc(0xFF12364F,0xFF54748A));
                    cell.addView(hole,new FrameLayout.LayoutParams(holeSize,holeSize,Gravity.CENTER));
                    if(mark!='.'){
                        View token=new View(this);
                        token.setBackground(gameDisc(mark=='R'?0xFFD84B5E:0xFF4B8BD8,0xFFF5ECDD));
                        token.setElevation(dp(2));
                        int tokenSize=(int)(holeSize*.88f);
                        cell.addView(token,new FrameLayout.LayoutParams(tokenSize,tokenSize,Gravity.CENTER));
                    }
                    cell.setContentDescription("Колонка "+(c+1)+", "
                            +(mark=='.'?"пустая ячейка":mark=='R'?"красная фишка":"синяя фишка"));
                    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(cellSize,cellSize);
                    cp.setMargins(dp(1),dp(1),dp(1),dp(1));
                    row.addView(cell,cp);
                    cell.setEnabled(active&&participant&&!p2.isEmpty()&&turn.equals(me)&&winner.isEmpty()&&!moveBusy[0]);
                    cell.setOnClickListener(v->{
                        if(moveBusy[0])return;
                        moveBusy[0]=true;draw[0].run();
                        network.execute(()->{
                            try{
                                JSONObject fresh=pairGames.fetch(g.getString("id"));
                                if(fresh==null)throw new Exception("Партия не найдена");
                                JSONObject ns=new JSONObject(fresh.getJSONObject("state").toString());
                                String b=ns.optString("board",emptyBoard(42));
                                String p1Fresh=ns.optString("p1"),p2Fresh=ns.optString("p2");
                                if(!"active".equals(fresh.optString("status"))||p2Fresh.isEmpty()
                                        ||(!me.equals(p1Fresh)&&!me.equals(p2Fresh))||!me.equals(ns.optString("turn")))
                                    throw new Exception("Сейчас ход партнёра или партия ещё не началась");
                                int drop=BoardGameRules.connectFourLandingRow(b,col);
                                if(drop<0)throw new Exception("Эта колонка заполнена");
                                char player=GameStartRules.connectFourTokenForPlayer(
                                        me,ns.optString("first_player"),p1Fresh);
                                StringBuilder sb=new StringBuilder(b);sb.setCharAt(drop,player);
                                ns.put("board",sb.toString());
                                boolean won=BoardGameRules.hasConnectFour(sb.toString(),player);
                                boolean full=sb.indexOf(".")<0;
                                if(won)ns.put("winner",me);
                                else if(full)ns.put("winner","draw");
                                else ns.put("turn",me.equals(p1Fresh)?p2Fresh:p1Fresh);
                                String st=won||full?"finished":"active";
                                JSONObject saved=pairGames.update(fresh.getString("id"),fresh.optLong("version"),ns,st);
                                runOnUiThread(()->{try{moveBusy[0]=false;game[0]=saved;draw[0].run();}catch(Exception e){toast("Ошибка игры: "+e.getMessage());}});
                            }catch(Exception ex){runOnUiThread(()->{moveBusy[0]=false;draw[0].run();toast("Ход не сохранён: "+ex.getMessage());});}
                        });
                    });
                }
            }
        };
        restart.setOnClickListener(v->startNextGame("connect_four",game,draw[0]));
        draw[0].run();dialog.setOnDismissListener(d->{closed[0]=true;});dialog.show();poll[0]=()->{if(closed[0])return;network.execute(()->{try{JSONObject fresh=pairGames.fetch(game[0].getString("id"));if(fresh!=null)runOnUiThread(()->{try{game[0]=fresh;draw[0].run();}catch(Exception ignored){}});}catch(Exception ignored){}if(!closed[0])gameHandler.postDelayed(poll[0],1400);});};gameHandler.postDelayed(poll[0],1400);
    }
    private boolean hasConnectFour(String b,char p){int ROWS=6,COLS=7;for(int r=0;r<ROWS;r++)for(int c=0;c<COLS;c++){int i=r*COLS+c;if(c+3<COLS&&b.charAt(i)==p&&b.charAt(i+1)==p&&b.charAt(i+2)==p&&b.charAt(i+3)==p)return true;if(r+3<ROWS&&b.charAt(i)==p&&b.charAt(i+COLS)==p&&b.charAt(i+2*COLS)==p&&b.charAt(i+3*COLS)==p)return true;if(c+3<COLS&&r+3<ROWS&&b.charAt(i)==p&&b.charAt(i+COLS+1)==p&&b.charAt(i+2*(COLS+1))==p&&b.charAt(i+3*(COLS+1))==p)return true;if(c-3>=0&&r+3<ROWS&&b.charAt(i)==p&&b.charAt(i+COLS-1)==p&&b.charAt(i+2*(COLS-1))==p&&b.charAt(i+3*(COLS-1))==p)return true;}return false;}

    private void onlineCheckers(){
        if(!pairGames.ready()){toast("Войдите в аккаунт и подключитесь к партнёру по коду приглашения");return;}
        toast("Подключаем шашки…");network.execute(()->{try{JSONObject g=openOrJoinGame("checkers");safeUi(()->showOnlineCheckers(g));}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});
    }
    private void showOnlineCheckers(JSONObject initial){
        final JSONObject[] game={initial};final boolean[] closed={false};final Runnable[] poll={null};
        final int[] selected={-1};final boolean[] moveBusy={false};
        LinearLayout panel=column();panel.setPadding(dp(10),dp(6),dp(10),dp(6));
        TextView status=text("",14,INK,true);status.setGravity(Gravity.CENTER);status.setPadding(dp(10),dp(10),dp(10),dp(10));status.setBackground(shape(PALE,14));panel.addView(status);
        LinearLayout board=column();board.setGravity(Gravity.CENTER);board.setPadding(dp(6),dp(6),dp(6),dp(6));board.setBackground(shape(0xFF4B3628,18));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(8);panel.addView(board,bp);
        TextView restart=pill("Новая партия",PURPLE);restart.setGravity(Gravity.CENTER);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(10);panel.addView(restart,rp);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Шашки · онлайн").setView(panel).setNegativeButton("Закрыть",null).create();
        Runnable[] draw={null};draw[0]=()->{JSONObject g=game[0],s=g.optJSONObject("state");if(s==null)return;String me=pairGames.userId(),p1=s.optString("p1"),p2=s.optString("p2"),boardState=s.optString("board",initialCheckersBoard()),turn=s.optString("turn"),winner=s.optString("winner"),whitePlayer=GameStartRules.checkersWhitePlayer(s.optString("first_player"),p1);int chain=s.optInt("chain",-1);boolean active="active".equals(g.optString("status")),participant=me.equals(p1)||me.equals(p2),whiteMovesUp=GameStartRules.checkersWhiteMovesUp(whitePlayer,p1);char mine=me.equals(whitePlayer)?'r':'b';boolean rotated=!me.equals(p1);
            String sideLabel="Ваши шашки "+(mine=='r'?"белые":"чёрные")+" · ваша сторона внизу";
            if(p2.isEmpty())status.setText("Игра создана. Ждём партнёра.");else if(!winner.isEmpty())status.setText((winner.equals(me)?"Вы победили! 🎉":"Партнёр победил.")+"\n"+sideLabel);else if(!active)status.setText("Партия завершена\n"+sideLabel);else status.setText((moveBusy[0]?"Сохраняю ход…":turn.equals(me)?"Ваш ход · выберите шашку":"Ход партнёра")+"\n"+sideLabel);
            java.util.Map<Integer,java.util.List<Integer>> legal=active&&participant&&!p2.isEmpty()&&turn.equals(me)?BoardGameRules.legalCheckersMoves(boardState,me,p1,whitePlayer,chain,whiteMovesUp):java.util.Collections.emptyMap();
            java.util.List<Integer> destinations=selected[0]<0?java.util.Collections.emptyList():legal.getOrDefault(selected[0],java.util.Collections.emptyList());
            restart.setVisibility(!p2.isEmpty()&&"finished".equals(g.optString("status"))?View.VISIBLE:View.GONE);
            board.removeAllViews();
            int cellSize=Math.max(dp(28),Math.min(dp(44),
                    (getResources().getDisplayMetrics().widthPixels-dp(92))/8));
            for(int r=0;r<8;r++){
                LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);board.addView(row);
                for(int c=0;c<8;c++){
                    int i=BoardGameRules.checkersBoardIndexForView(r,c,rotated);
                    char mark=i<boardState.length()?boardState.charAt(i):'.';
                    boolean dark=(i/8+i%8)%2==1;
                    boolean own=mark=='r'&&mine=='r'||mark=='R'&&mine=='r'
                            ||mark=='b'&&mine=='b'||mark=='B'&&mine=='b';
                    boolean target=destinations.contains(i);
                    FrameLayout cell=new FrameLayout(this);
                    int bg=dark?0xFF79553A:0xFFE7D2AF;
                    if(target)bg=0xFF6D9A6E;
                    else if(i==selected[0])bg=0xFFB98A35;
                    else if(own&&active&&turn.equals(me)&&!moveBusy[0])bg=dark?0xFF916C42:0xFFF0D9AA;
                    cell.setBackground(shape(bg,4));
                    if(mark!='.'){
                        int pieceColor=mark=='r'||mark=='R'?0xFFF4F0E7:0xFF30313A;
                        int pieceOutline=mark=='r'||mark=='R'?0xFFFFFFFF:0xFF878895;
                        int pieceSize=(int)(cellSize*.68f);
                        View piece=new View(this);
                        piece.setBackground(gameDisc(pieceColor,pieceOutline));
                        piece.setElevation(dp(2));
                        cell.addView(piece,new FrameLayout.LayoutParams(pieceSize,pieceSize,Gravity.CENTER));
                        if(Character.isUpperCase(mark)){
                            int crownSize=Math.max(12,(int)(cellSize
                                    /getResources().getDisplayMetrics().density/2f));
                            TextView crown=text("♛",crownSize,0xFFFFD66B,true);
                            crown.setGravity(Gravity.CENTER);
                            cell.addView(crown,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
                        }
                    }
                    String description=mark=='.'?(target?"доступный ход":"пустая клетка")
                            :own?"ваша шашка":"шашка партнёра";
                    cell.setContentDescription("Ряд "+(8-r)+", колонка "+(c+1)+": "+description);
                    cell.setFocusable(true);
                    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(cellSize,cellSize);
                    cp.setMargins(dp(1),dp(1),dp(1),dp(1));
                    row.addView(cell,cp);
                    cell.setEnabled(!moveBusy[0]&&active&&participant&&!p2.isEmpty()
                            &&turn.equals(me)&&winner.isEmpty());
                    cell.setOnClickListener(v->{
                        if(moveBusy[0])return;
                        if(destinations.contains(i)){
                            int from=selected[0];selected[0]=-1;moveBusy[0]=true;
                            draw[0].run();submitCheckersMove(game,from,i,moveBusy,draw);return;
                        }
                        if(own&&legal.containsKey(i))selected[0]=i;
                        else selected[0]=-1;
                        draw[0].run();
                    });
                }
            }
        };
        restart.setOnClickListener(v->startNextGame("checkers",game,draw[0]));
        draw[0].run();dialog.setOnDismissListener(d->{closed[0]=true;});dialog.show();poll[0]=()->{if(closed[0])return;network.execute(()->{try{JSONObject fresh=pairGames.fetch(game[0].getString("id"));if(fresh!=null)runOnUiThread(()->{try{game[0]=fresh;draw[0].run();}catch(Exception ignored){}});}catch(Exception ignored){}if(!closed[0])gameHandler.postDelayed(poll[0],1400);});};gameHandler.postDelayed(poll[0],1400);
    }

    private void submitCheckersMove(JSONObject[] game,int from,int to,boolean[] busy,Runnable[] draw){
        network.execute(()->{try{JSONObject fresh=pairGames.fetch(game[0].getString("id"));if(fresh==null)throw new Exception("Партия не найдена");JSONObject state=new JSONObject(fresh.getJSONObject("state").toString());String me=pairGames.userId(),p1=state.optString("p1"),p2=state.optString("p2");if(!"active".equals(fresh.optString("status"))||p2.isEmpty()||!me.equals(state.optString("turn")))throw new Exception("Сейчас ход партнёра или партия ещё не началась");        String whitePlayer=GameStartRules.checkersWhitePlayer(state.optString("first_player"),p1);
        boolean whiteMovesUp=GameStartRules.checkersWhiteMovesUp(whitePlayer,p1);
        BoardGameRules.CheckersMove move=BoardGameRules.moveCheckers(state.optString("board"),me,p1,p2,whitePlayer,state.optInt("chain",-1),from,to,whiteMovesUp);state.put("board",move.board);state.put("turn",move.turn);state.put("winner",move.winner);state.put("chain",move.chain);JSONObject saved=pairGames.update(fresh.getString("id"),fresh.optLong("version"),state,move.winner.isEmpty()?"active":"finished");runOnUiThread(()->{busy[0]=false;game[0]=saved;draw[0].run();});}catch(Exception e){runOnUiThread(()->{busy[0]=false;draw[0].run();toast("Ход не сохранён: "+e.getMessage());});}});
    }

    private void onlineDotsAndBoxes(){
        if(!pairGames.ready()){toast("Войдите в аккаунт и подключитесь к партнёру по коду приглашения");return;}
        toast("Подключаем точки и квадратики…");network.execute(()->{try{JSONObject g=openOrJoinGame("dots_boxes");safeUi(()->showOnlineDotsAndBoxes(g));}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});
    }
    private void showOnlineDotsAndBoxes(JSONObject initial){
        final JSONObject[] game={initial};final boolean[] closed={false};final Runnable[] poll={null};
        LinearLayout panel=column();panel.setPadding(dp(8),dp(4),dp(8),dp(4));
        TextView status=text("",13,INK,true);status.setGravity(Gravity.CENTER);status.setPadding(dp(8),dp(8),dp(8),dp(8));status.setBackground(shape(PALE,12));panel.addView(status);
        LinearLayout board=column();board.setGravity(Gravity.CENTER);board.setPadding(dp(4),dp(4),dp(4),dp(4));board.setBackground(shape(0xFF604832,18));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(6);panel.addView(board,bp);
        TextView restart=pill("Новая партия",PURPLE);restart.setGravity(Gravity.CENTER);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(8);panel.addView(restart,rp);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Точки и квадратики · онлайн").setView(panel).setNegativeButton("Закрыть",null).create();
        final boolean[] moveBusy={false};
        Runnable[] draw={null};draw[0]=()->{JSONObject g=game[0],s=g.optJSONObject("state");if(s==null)return;String me=pairGames.userId(),p1=s.optString("p1"),p2=s.optString("p2"),edgesState=s.optString("edges",emptyBoard(40)),boxesState=s.optString("boxes",emptyBoard(16)),turn=s.optString("turn"),winner=s.optString("winner"),firstPlayer=GameStartRules.firstPlayerOrPlayerOne(s.optString("first_player"),p1);int score1=s.optInt("score1",0),score2=s.optInt("score2",0);boolean active="active".equals(g.optString("status"));
            if(p2.isEmpty())status.setText("Игра создана. Ждём партнёра.");else if(!winner.isEmpty())status.setText("draw".equals(winner)?"Ничья!":winner.equals(me)?"Вы победили! 🎉":"Партнёр победил.");else if(!active)status.setText("Партия завершена");else status.setText((moveBusy[0]?"Сохраняю ход…":turn.equals(me)?"Ваш ход":"Ход партнёра")+" · "+(me.equals(firstPlayer)?"вы первый":"вы второй")+" · счёт "+score1+":"+score2);
            restart.setVisibility(!p2.isEmpty()&&"finished".equals(g.optString("status"))?View.VISIBLE:View.GONE);
            board.removeAllViews();
            int stepSize=Math.max(dp(26),Math.min(dp(38),
                    (getResources().getDisplayMetrics().widthPixels-dp(96))/9));
            int dotSize=Math.max(dp(6),stepSize/4);
            for(int rowIndex=0;rowIndex<9;rowIndex++){
                LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);board.addView(row);
                for(int col=0;col<9;col++){
                    if(rowIndex%2==0&&col%2==0){
                        FrameLayout dotSlot=new FrameLayout(this);
                        View dot=new View(this);dot.setBackground(gameDisc(0xFF3D2F25,0xFFE4CFA9));
                        dotSlot.addView(dot,new FrameLayout.LayoutParams(dotSize,dotSize,Gravity.CENTER));
                        dotSlot.setContentDescription("Точка "+(rowIndex/2+1)+", "+(col/2+1));
                        row.addView(dotSlot,new LinearLayout.LayoutParams(stepSize,stepSize));
                    }else if(rowIndex%2==0){
                        int edge=(rowIndex/2)*4+col/2;
                        addDotsEdge(row,edgesState,edge,true,stepSize,
                                active&&turn.equals(me)&&!p2.isEmpty()&&!moveBusy[0],game,moveBusy,draw);
                    }else if(col%2==0){
                        int edge=20+(rowIndex/2)*5+col/2;
                        addDotsEdge(row,edgesState,edge,false,stepSize,
                                active&&turn.equals(me)&&!p2.isEmpty()&&!moveBusy[0],game,moveBusy,draw);
                    }else{
                        int boxIndex=(rowIndex/2)*4+col/2;char owner=boxIndex<boxesState.length()?boxesState.charAt(boxIndex):'.';
                        TextView box=text(owner=='.'?"":"●",18,
                                owner=='A'?0xFFB9384D:0xFF3F568C,true);box.setGravity(Gravity.CENTER);
                        box.setBackground(shape(owner=='.'?0xFFF0E2C8:
                                owner=='A'?0xFFF2D8D9:0xFFDDE2F0,4));
                        box.setContentDescription(owner=='.'?"Пустой квадрат":
                                owner=='A'?"Квадрат первого игрока":"Квадрат второго игрока");
                        row.addView(box,new LinearLayout.LayoutParams(stepSize,stepSize));
                    }
                }
            }
        };
        restart.setOnClickListener(v->startNextGame("dots_boxes",game,draw[0]));
        draw[0].run();dialog.setOnDismissListener(d->{closed[0]=true;});dialog.show();poll[0]=()->{if(closed[0])return;network.execute(()->{try{JSONObject fresh=pairGames.fetch(game[0].getString("id"));if(fresh!=null)runOnUiThread(()->{try{game[0]=fresh;draw[0].run();}catch(Exception ignored){}});}catch(Exception ignored){}if(!closed[0])gameHandler.postDelayed(poll[0],1400);});};gameHandler.postDelayed(poll[0],1400);
    }

    private void addDotsEdge(LinearLayout row,String edges,int edge,boolean horizontal,int stepSize,boolean enabled,
                             JSONObject[] game,boolean[] busy,Runnable[] draw){
        char mark=edge<edges.length()?edges.charAt(edge):'.';
        FrameLayout slot=new FrameLayout(this);
        slot.setFocusable(true);
        int lineColor=mark=='A'?0xFFB9384D:mark=='B'?0xFF3F568C:0xFFB49B76;
        View line=new View(this);
        line.setBackground(shape(lineColor,4));
        int thickness=Math.max(dp(5),stepSize/6);
        slot.setContentDescription(mark=='.'?"Поставить линию "+(edge+1):"Линия "+(edge+1));
        FrameLayout.LayoutParams lp=horizontal
                ?new FrameLayout.LayoutParams(stepSize,thickness,Gravity.CENTER)
                :new FrameLayout.LayoutParams(thickness,stepSize,Gravity.CENTER);
        slot.addView(line,lp);
        row.addView(slot,new LinearLayout.LayoutParams(stepSize,stepSize));
        slot.setEnabled(enabled&&mark=='.');
        if(mark=='.')slot.setOnClickListener(v->{if(busy[0])return;busy[0]=true;draw[0].run();submitDotsMove(game,edge,busy,draw);});
    }

    private void submitDotsMove(JSONObject[] game,int edge,boolean[] busy,Runnable[] draw){
        network.execute(()->{try{
            JSONObject fresh=pairGames.fetch(game[0].getString("id"));
            if(fresh==null)throw new Exception("Партия не найдена");
            JSONObject state=new JSONObject(fresh.getJSONObject("state").toString());
            String me=pairGames.userId(),p1=state.optString("p1"),p2=state.optString("p2");
            if(!"active".equals(fresh.optString("status"))||p2.isEmpty()
                    ||(!me.equals(p1)&&!me.equals(p2))||!me.equals(state.optString("turn")))
                throw new Exception("Сейчас ход партнёра или партия ещё не началась");
            String firstPlayer=GameStartRules.firstPlayerOrPlayerOne(state.optString("first_player"),p1);
            BoardGameRules.DotsMove move=BoardGameRules.moveDotsAndBoxes(
                    state.optString("edges"),state.optString("boxes"),edge,me,p1,p2,
                    state.optInt("score1"),state.optInt("score2"),firstPlayer);
            state.put("edges",move.edges);state.put("boxes",move.boxes);
            state.put("score1",move.score1);state.put("score2",move.score2);
            state.put("turn",move.turn);state.put("winner",move.winner);
            JSONObject saved=pairGames.update(fresh.getString("id"),fresh.optLong("version"),state,
                    move.winner.isEmpty()?"active":"finished");
            runOnUiThread(()->{busy[0]=false;game[0]=saved;draw[0].run();});
        }catch(Exception e){runOnUiThread(()->{busy[0]=false;draw[0].run();toast("Ход не сохранён: "+e.getMessage());});}});
    }
}
