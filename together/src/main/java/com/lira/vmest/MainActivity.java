package com.lira.vmest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Random;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.HashSet;
import java.text.SimpleDateFormat;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A standalone, offline-first shared-life companion prototype. */
public class MainActivity extends Activity {
    private static final int BG = 0xFFFBF8FC;
    private static final int PAPER = 0xFFFFFFFF;
    private static final int INK = 0xFF302B35;
    private static final int MUTED = 0xFF817988;
    private static final int PURPLE = 0xFF8158A5;
    private static final int PALE = 0xFFF0E8F6;
    private static final int LINE = 0xFFE9E1ED;
    private android.content.SharedPreferences prefs;
    private LinearLayout root, body, nav;
    private String tab = "Главная";
    private String feature = null;
    private String pendingPhotoKey = "", pendingPhotoItemId = "";
    private Runnable sharedTimerTick, sharedTimerPoll;
    private Calendar visibleMonth = Calendar.getInstance();
    private Calendar selectedDay = Calendar.getInstance();
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private SupabaseSync sync;
    private PairGameSync pairGames;
    private final Handler gameHandler = new Handler(Looper.getMainLooper());

    private static final String[] MORE = {
            "💬  Чат для двоих", "📝  Заметки", "📎  Файлы", "💳  Карты лояльности",
            "🎁  Подарочный радар", "⏳  Капсула времени", "🗳  Голосование",
            "🎲  Совместные игры", "🎬  Комната просмотра", "🧳  Общие планы",
            "💌  Пригласить партнёра", "📲  NFC-оплата"
    };
    private static final String[] TUNES={"Ода к радости","Вальс цветов","Маленькая ночная серенада","Щелкунчик: Танец феи Драже"};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        prefs = getSharedPreferences("vmeste_data", MODE_PRIVATE);
        sync = new SupabaseSync(this);
        pairGames = new PairGameSync(this);
        showApp();
        refreshProfiles();
    }

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable shape(int fill, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); return d;
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
        TextView avatars = text(profileName.isEmpty() ? "♡  Профиль" : prefs.getString("profile_avatar", "♡") + "  " + profileName, 13, PURPLE, true); avatars.setGravity(Gravity.CENTER);
        avatars.setBackground(shape(PALE, 22)); header.addView(avatars, new LinearLayout.LayoutParams(dp(96), dp(40)));
        avatars.setOnClickListener(v -> profileDialog());
        root.addView(header);

        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false); scroll.setClipToPadding(false);
        body = column(); body.setPadding(dp(18), dp(6), dp(18), dp(20)); scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        nav = new LinearLayout(this); nav.setGravity(Gravity.CENTER); nav.setPadding(dp(3), dp(5), dp(3), dp(7));
        nav.setBackgroundColor(PAPER); root.addView(nav, new LinearLayout.LayoutParams(-1, dp(62)));
        render();
    }

    private void render() {
        body.removeAllViews(); nav.removeAllViews();
        if (feature != null && feature.startsWith("list:")) renderSharedList();
        else if (feature != null) renderFeature();
        else if (tab.equals("Главная")) renderHome();
        else if (tab.equals("Дела")) renderTasks();
        else if (tab.equals("Календарь")) renderCalendar();
        else if (tab.equals("Хотим")) renderCollection("Хочется вместе", "Маленькие и большие мечты", "wishes", "Добавить хотелку");
        else if (tab.equals("Ещё")) renderMore();
        for (String item : new String[]{"Главная", "Дела", "Календарь", "Хотим", "Ещё"}) {
            final String dest = item;
            TextView b = text(iconFor(item) + "\n" + item, 10, item.equals(tab) && feature == null ? PURPLE : MUTED, item.equals(tab) && feature == null);
            b.setGravity(Gravity.CENTER); b.setPadding(dp(4), dp(3), dp(4), dp(3));
            b.setBackground(shape(item.equals(tab) && feature == null ? PALE : PAPER, 16));
            nav.addView(b, new LinearLayout.LayoutParams(0, -1, 1));
            b.setOnClickListener(v -> { feature = null; tab = dest; render(); });
        }
    }
    private String iconFor(String s) {
        switch(s) { case "Главная": return "⌂"; case "Дела": return "✓"; case "Календарь": return "▦"; case "Хотим": return "♡"; default: return "•••"; }
    }

    private void renderHome() {
        LinearLayout welcome = card(); welcome.setBackground(shape(PALE, 24));
        welcome.addView(text("ВАШ ОБЩИЙ ДЕНЬ", 10, PURPLE, true));
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

        heading("На сегодня", "Ваши общие дела");
        LinearLayout taskCard = card();
        JSONArray tasks = read("tasks");
        int shown = 0;
        for (int i = 0; i < tasks.length() && shown < 3; i++) {
            JSONObject item = tasks.optJSONObject(i);
            if (item != null && !item.optBoolean("done")) { addTask(taskCard, tasks, i); shown++; }
        }
        if (shown == 0) simpleCard("✓", "Все общие дела закрыты", "Добавьте новое дело во вкладке «Дела»", null);
        TextView link = text("Все дела  →", 13, PURPLE, true); link.setPadding(0, dp(8), 0, 0);
        taskCard.addView(link); link.setOnClickListener(v -> { tab = "Дела"; render(); });

        heading("Скоро", "Чтобы ничего не забыть");
        JSONArray events = read("events");
        JSONObject nextEvent = null; long now = System.currentTimeMillis(), best = Long.MAX_VALUE;
        for (int i=0;i<events.length();i++) { JSONObject event=events.optJSONObject(i); if(event==null)continue; long at=event.optLong("reminder_at",0); if(at>=now&&at<best){best=at;nextEvent=event;} }
        if (nextEvent == null) simpleCard("🗓", "Ближайших событий нет", "Добавьте дату в календаре", () -> { tab = "Календарь"; render(); });
        else simpleCard("🗓", nextEvent.optString("title"), new SimpleDateFormat("d MMMM · HH:mm",new Locale("ru")).format(new java.util.Date(best)), () -> { tab = "Календарь"; render(); });

        heading("Проведите время вместе", "Выбирайте настроение");
        simpleCard("🎬", "Кино-комната", "Соберитесь на совместный просмотр", () -> open("🎬  Комната просмотра"));
        simpleCard("🗳", "Выбрать, чем заняться", "Создайте голосование для двоих", () -> open("🗳  Голосование"));
        simpleCard("🎁", "Подарочный радар", "Сохраняйте идеи подарков", () -> open("🎁  Подарочный радар"));
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
        heading("Наш календарь", "События и дела по дням");
        LinearLayout monthCard = card();
        LinearLayout monthBar = new LinearLayout(this); monthBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView prev = text("‹", 28, PURPLE, true); prev.setGravity(Gravity.CENTER);
        monthBar.addView(prev, new LinearLayout.LayoutParams(dp(42), dp(42)));
        String monthName = new SimpleDateFormat("LLLL yyyy", new Locale("ru")).format(visibleMonth.getTime());
        TextView month = text(monthName.substring(0,1).toUpperCase(new Locale("ru")) + monthName.substring(1), 17, INK, true);
        month.setGravity(Gravity.CENTER); monthBar.addView(month, new LinearLayout.LayoutParams(0, -2, 1));
        TextView next = text("›", 28, PURPLE, true); next.setGravity(Gravity.CENTER);
        monthBar.addView(next, new LinearLayout.LayoutParams(dp(42), dp(42))); monthCard.addView(monthBar);
        prev.setOnClickListener(v -> moveMonth(-1));
        next.setOnClickListener(v -> moveMonth(1));

        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        grid.setColumnCount(7); grid.setRowCount(7);
        String[] weekdays={"ПН","ВТ","СР","ЧТ","ПТ","СБ","ВС"};
        for(String wd:weekdays){ TextView d=text(wd,10,MUTED,true);d.setGravity(Gravity.CENTER);grid.addView(d,new android.widget.GridLayout.LayoutParams()); }
        Calendar first=(Calendar)visibleMonth.clone(); first.set(Calendar.DAY_OF_MONTH,1);
        int offset=(first.get(Calendar.DAY_OF_WEEK)+5)%7; first.add(Calendar.DAY_OF_MONTH,-offset);
        String selectedKey=dateKey(selectedDay);
        for(int i=0;i<42;i++){
            Calendar date=(Calendar)first.clone(); date.add(Calendar.DAY_OF_MONTH,i);
            String key=dateKey(date); boolean thisMonth=date.get(Calendar.MONTH)==visibleMonth.get(Calendar.MONTH);
            boolean isSelected=key.equals(selectedKey); boolean hasTasks=read("day_"+key).length()>0;
            TextView day=text(String.valueOf(date.get(Calendar.DAY_OF_MONTH))+(hasTasks?"\n•":""),13,
                    isSelected?PAPER:thisMonth?INK:0xFFB9B2BE,isSelected);
            day.setGravity(Gravity.CENTER); day.setBackground(shape(isSelected?PURPLE:(hasTasks?PALE:BG),14));
            android.widget.GridLayout.LayoutParams p=new android.widget.GridLayout.LayoutParams();
            p.width=0; p.height=dp(43); p.columnSpec=android.widget.GridLayout.spec(i%7,1,1f);
            p.setMargins(dp(1),dp(1),dp(1),dp(1)); grid.addView(day,p);
            day.setOnClickListener(v->{selectedDay.setTime(date.getTime());visibleMonth.set(Calendar.YEAR,date.get(Calendar.YEAR));visibleMonth.set(Calendar.MONTH,date.get(Calendar.MONTH));render();});
        }
        monthCard.addView(grid,new LinearLayout.LayoutParams(-1,-2));
        String humanDate=new SimpleDateFormat("EEEE, d MMMM",new Locale("ru")).format(selectedDay.getTime());
        heading(humanDate.substring(0,1).toUpperCase(new Locale("ru"))+humanDate.substring(1),"Планы на выбранную дату");
        String key="day_"+selectedKey;
        TextView add=pill("＋  Добавить дело на эту дату",PURPLE);body.addView(add);
        add.setOnClickListener(v->addDialog(key,"Дело на "+new SimpleDateFormat("d MMMM",new Locale("ru")).format(selectedDay.getTime())));
        JSONArray dayTasks=read(key);
        if(dayTasks.length()==0) simpleCard("＋","Пока свободно","Запланируйте что-нибудь вдвоём",null);
        else { LinearLayout list=card(); for(int i=0;i<dayTasks.length();i++)addTask(list,dayTasks,i,key); }
        heading("События и даты",null);
        JSONArray events=read("events");
        if(events.length()==0)simpleCard("♡","Добавьте ваше событие","День рождения, годовщина или свидание",()->addDialog("events","Новое событие"));
        else for(int i=0;i<events.length();i++){JSONObject e=events.optJSONObject(i);if(e!=null)simpleCard("♡",e.optString("title"),e.optString("detail"),null);}
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
        add.setOnClickListener(v -> addDialog(key, action));
        JSONArray list = read(key);
        if (list.length() == 0) {
            simpleCard("＋", "Пока записей нет", "Добавьте вашу первую общую запись", null);
        } else for (int i=0;i<list.length();i++) {
            JSONObject item = list.optJSONObject(i); if (item == null) continue;
            renderEditableEntry(key, list, i, key.equals("wishes") ? "♡" : "🗓");
        }
        if (key.equals("wishes")) simpleCard("🗳", "Голосование за идею", "Пусть каждый выберет, чего хочется", () -> open("🗳  Голосование"));
    }

    private void renderMore() {
        heading("Всё для вас двоих", "Ваши общие уголки");
        for (String item : MORE) {
            String icon = item.substring(0, 2);
            simpleCard(icon, item.substring(4), description(item), () -> open(item));
        }
        simpleCard("⏱", "Общий таймер", "Запустите отсчёт и следите за ним вместе", () -> open("shared_timer"));
        simpleCard("🔔", "Напомнить партнёру", "Общая дата и уведомление на телефонах пары", () -> open("shared_reminder"));
    }
    private String description(String title) {
        if (title.contains("Чат")) return "Сообщения, которые остаются между вами";
        if (title.contains("Заметки")) return "Мысли, рецепты и маленькие напоминания";
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
        heading(feature, description(feature));
        if ("shared_timer".equals(feature)) {
            renderSharedTimer();
        } else if ("shared_reminder".equals(feature)) {
            renderSharedReminder();
        } else if (feature.contains("Вопрос дня")) {
            simpleCard("💭", "Что из наших общих воспоминаний хочется пережить ещё раз?", "Вопрос для вас обоих", null);
            TextView answer = pill("Написать ответ", PURPLE); body.addView(answer); answer.setOnClickListener(v -> addDialog("answers", "Ваш ответ"));
            renderEntries("answers");
        } else if (feature.contains("Пригласить партнёра")) {
            renderCoupleSetup();
        } else if (feature.contains("NFC-оплата")) {
            boolean supported=getPackageManager().hasSystemFeature("android.hardware.nfc");
            simpleCard(supported?"📲":"ℹ","NFC "+(supported?"есть на этом телефоне":"не поддерживается телефоном"),
                    "Приложение для пары не может самостоятельно токенизировать банковскую карту. Для бесконтактной оплаты нужна поддержка банка и платёжного кошелька.",null);
            TextView settings=pill("Открыть настройки NFC",PURPLE);body.addView(settings);
            settings.setOnClickListener(v->{try{startActivity(new Intent(android.provider.Settings.ACTION_NFC_SETTINGS));}catch(Exception ex){Toast.makeText(this,"Откройте NFC в настройках телефона",Toast.LENGTH_SHORT).show();}});
            simpleCard("💳","Карты лояльности","Добавляйте штрихкоды скидочных карт без банковских реквизитов.",()->open("💳  Карты лояльности"));
        } else if (feature.contains("просмотра")) {
            renderMovieRoom();
        } else if (feature.contains("игры")) {
            gameCard("Крестики-нолики", "Сетевая партия с партнёром на другом устройстве", this::onlineTicTacToe);
            gameCard("Морской бой", "Каждый расставляет свой флот на своём телефоне", this::onlineSeaBattle);
            gameCard("Дурак", "Карточная партия с партнёром на его телефоне", this::onlineDurak);
            gameCard("Угадай мелодию", "Напойте друг другу и отвечайте каждый со своего телефона", this::onlineGuessTune);
            gameCard("Кто скорее…", "Выберите ответ на своих телефонах и сравните", () -> onlineCoupleQuiz("who", "Кто скорее выберет спонтанную поездку?"));
            gameCard("Угадай меня", "Ответьте независимо и откройте ответы вместе", () -> onlineCoupleQuiz("guess", "Какой мой идеальный выходной?"));
        } else if (feature.contains("Файлы")) {
            TextView attach = pill("＋  Выбрать файл", PURPLE); body.addView(attach);
            attach.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i, 402); });
            renderEntries(keyFor(feature));
        } else if (feature.contains("Чат")) {
            renderChat();
            LinearLayout composer = new LinearLayout(this); composer.setGravity(Gravity.CENTER_VERTICAL); body.addView(composer);
            EditText msg = new EditText(this); msg.setSingleLine(true); msg.setHint("Написать сообщение…"); composer.addView(msg, new LinearLayout.LayoutParams(0, dp(52), 1));
            TextView send = pill("Отправить", PURPLE); composer.addView(send); send.setOnClickListener(v -> { String s=msg.getText().toString().trim(); if(!s.isEmpty()){append("messages",s,"Сейчас"); ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(msg.getWindowToken(),0); feature=feature; render(); } });
        } else {
            if (feature.contains("Карты")) simpleCard("🔒", "Не храните реквизиты банковских карт", "Для безопасности добавляйте только название программы и штрихкод карты лояльности.", null);
            if (feature.contains("Капсула")) simpleCard("⏳", "Послание для будущих вас", "Добавьте текст — откройте его в особенный день.", null);
            TextView add = pill(feature.contains("Капсула") ? "＋  Написать послание" : feature.contains("Карты") ? "＋  Добавить карту лояльности" : feature.contains("Голосование") ? "＋  Предложить вариант" : "＋  Добавить", PURPLE);
            body.addView(add); add.setOnClickListener(v -> addDialog(keyFor(feature), feature.contains("Карты") ? "Название карты или штрихкод" : feature.contains("Капсула") ? "Послание в будущее" : feature.contains("Голосование") ? "Вариант для голосования" : feature));
            renderEntries(keyFor(feature));
        }
    }

    private void renderSharedTimer() {
        heading("Общий таймер","Отсчёт хранится в общем пространстве пары");
        JSONArray current=read("shared_timer");JSONObject timer=current.length()==0?null:current.optJSONObject(0);
        TextView counter=text("Таймер не запущен",24,PURPLE,true);counter.setGravity(Gravity.CENTER);LinearLayout timerCard=card();timerCard.addView(counter);
        EditText minutes=new EditText(this);minutes.setSingleLine(true);minutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);minutes.setHint("Минуты");body.addView(minutes);
        TextView start=pill("Запустить общий таймер",PURPLE);body.addView(start);start.setOnClickListener(v->{int value;try{value=Integer.parseInt(minutes.getText().toString());}catch(Exception e){minutes.setError("Введите минуты");return;}if(value<1||value>1440){minutes.setError("От 1 минуты до суток");return;}JSONObject next=new JSONObject();try{next.put("id",UUID.randomUUID().toString());next.put("title","Общий таймер");next.put("minutes",value);next.put("timer_end",System.currentTimeMillis()+value*60000L);next.put("started_by",sync.userId());}catch(Exception ignored){}JSONArray rows=new JSONArray();rows.put(next);save("shared_timer",rows);syncItem("shared_timer",next);scheduleReminder(next.optString("id"),next.optLong("timer_end"),"Таймер завершён","Ваш общий таймер закончился");render();});
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
        if(id.isEmpty()||at<=System.currentTimeMillis())return;
        if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},8101);}
        android.content.Intent intent=new android.content.Intent(this,ReminderReceiver.class);intent.putExtra("title",title);intent.putExtra("body",message);intent.putExtra("notification_id",id.hashCode());
        android.app.PendingIntent pending=android.app.PendingIntent.getBroadcast(this,id.hashCode(),intent,android.app.PendingIntent.FLAG_UPDATE_CURRENT|android.app.PendingIntent.FLAG_IMMUTABLE);
        android.app.AlarmManager alarm=(android.app.AlarmManager)getSystemService(ALARM_SERVICE);if(alarm!=null)alarm.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP,at,pending);
    }

    private void syncQuietly() {
        if(!sync.ready())return;network.execute(()->{try{JSONArray rows=sync.fetchItems();boolean hasTimer=false;for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);sync.storeRemote(row,prefs);JSONObject payload=row.optJSONObject("payload");if(payload!=null){if("shared_timer".equals(payload.optString("app_key")))hasTimer=true;if(payload.optBoolean("remind_partner"))scheduleReminder(row.optString("id"),payload.optLong("reminder_at"),"Вместе",payload.optString("title","Напоминание"));}}if(!hasTimer)prefs.edit().remove("shared_timer").apply();}catch(Exception ignored){}});
    }

    @Override protected void onResume(){super.onResume();syncQuietly();}

    private void renderCoupleSetup() {
        if (!sync.configured()) {
            simpleCard("☁", "Общее пространство", "Добавьте адрес и публичный ключ Supabase, чтобы связывать данные двух телефонов.", null);
            TextView configure = pill("Настроить подключение", PURPLE); body.addView(configure);
            configure.setOnClickListener(v -> configureServer());
            return;
        }
        if (!sync.signedIn()) {
            simpleCard("♡", "Войдите или создайте аккаунт", "Используйте один аккаунт на своём телефоне и пригласите партнёра кодом.", null);
            TextView account = pill("Войти / зарегистрироваться", PURPLE); body.addView(account);
            account.setOnClickListener(v -> accountDialog());
            return;
        }
        if (sync.coupleId().isEmpty()) {
            simpleCard("♡", "Создайте пространство для двоих", "Один из вас создаёт пару и отправляет код приглашения второму.", null);
            TextView create = pill("Создать приглашение", PURPLE); body.addView(create);
            create.setOnClickListener(v -> createCoupleDialog());
            TextView join = pill("Ввести код партнёра", PURPLE); body.addView(join);
            join.setOnClickListener(v -> joinCoupleDialog());
        } else {
            String partnerName=prefs.getString("partner_name","").trim();
            simpleCard("✓", partnerName.isEmpty()?"Пара подключена":"Вы в паре с "+partnerName,
                    "Общее пространство доступно только двум аккаунтам по коду: " + sync.inviteCode(), null);
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
            if ("EMAIL_CONFIRMATION_REQUIRED".equals(result)) toast("Подтвердите почту по ссылке из письма, затем войдите");
            else { toast(signUp ? "Аккаунт создан" : "Вы вошли"); render(); }
        });
    }

    private void createCoupleDialog() {
        EditText name = new EditText(this); name.setSingleLine(true); name.setHint("Название пары, например: Мы");
        new AlertDialog.Builder(this).setTitle("Создать пространство").setView(name).setNegativeButton("Отмена", null)
                .setPositiveButton("Создать", (d, w) -> runNetwork(() -> sync.createCouple(name.getText().toString()), result -> {
                    render(); shareText("Присоединяйся к нашему пространству «Вместе». Код: " + sync.inviteCode());
                })).show();
    }

    private void joinCoupleDialog() {
        EditText code = new EditText(this); code.setSingleLine(true); code.setHint("Код приглашения");
        new AlertDialog.Builder(this).setTitle("Присоединиться к партнёру").setView(code).setNegativeButton("Отмена", null)
                .setPositiveButton("Присоединиться", (d, w) -> runNetwork(() -> sync.joinCouple(code.getText().toString()), result -> {
                    render(); toast("Вы теперь в общем пространстве");
                })).show();
    }

    private void renderMovieRoom() {
        JSONObject room = readMovieRoom();
        if (room == null) {
            simpleCard("🍿", "Выберите фильм или видео", "Добавьте ссылку из сервиса, которым пользуетесь оба. Комната сохранит её и общий статус просмотра.", null);
            TextView create = pill("＋  Создать кино-комнату", PURPLE); body.addView(create);
            create.setOnClickListener(v -> editMovieRoom(null));
            TextView youtube = pill("Найти видео на YouTube", PURPLE); body.addView(youtube);
            youtube.setOnClickListener(v -> openUrl("https://www.youtube.com"));
            TextView syncRoom = pill("Обновить комнату партнёра", PURPLE); body.addView(syncRoom);
            syncRoom.setOnClickListener(v -> fetchMovieRoom());
            TextView broadcast = pill("🔴  Начать парную трансляцию", PURPLE); body.addView(broadcast);
            broadcast.setOnClickListener(v -> createLiveRoom());
            TextView join = pill("Войти в комнату по коду", PURPLE); body.addView(join);
            join.setOnClickListener(v -> joinLiveRoom());
            return;
        }
        String title = room.optString("title", "Вечер кино"), url = room.optString("url", "");
        String status = room.optString("status", "ready");
        simpleCard("🎬", title, statusText(status) + (sync.signedIn() && !sync.coupleId().isEmpty() ? " · общее пространство подключено" : " · только на этом телефоне"), null);
        if (!url.isEmpty()) {
            TextView open = pill("▶  Открыть видео", PURPLE); body.addView(open); open.setOnClickListener(v -> openUrl(url));
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
        TextView broadcast = pill("🔴  Начать парную трансляцию", PURPLE); body.addView(broadcast);
        broadcast.setOnClickListener(v -> createLiveRoom());
        TextView join = pill("Войти в комнату по коду", PURPLE); body.addView(join);
        join.setOnClickListener(v -> joinLiveRoom());
    }

    private void createLiveRoom() {
        if (!sync.configured() || !sync.signedIn() || sync.coupleId().isEmpty()) {
            toast("Для парной трансляции сначала подключите Supabase, войдите и пригласите партнёра.");
            return;
        }
        JSONObject room = readMovieRoom(); String roomTitle = room == null ? "Совместный просмотр" : room.optString("title", "Совместный просмотр");
        String roomId = UUID.randomUUID().toString();
        this.<String>runNetwork(() -> { sync.createWatchRoom(roomId, roomTitle); return roomId; }, created -> {
            Intent intent = WatchPartyActivity.intent(this, created, true, roomTitle).putExtra(WatchPartyActivity.EXTRA_PLAYER_URL, room == null ? "" : room.optString("url", ""));
            startActivity(intent);
        });
    }

    private void joinLiveRoom() {
        if (!sync.configured() || !sync.signedIn() || sync.coupleId().isEmpty()) { toast("Сначала подключите общее пространство с партнёром"); return; }
        EditText code = new EditText(this); code.setSingleLine(true); code.setHint("Код комнаты от партнёра");
        new AlertDialog.Builder(this).setTitle("Войти в парную трансляцию").setView(code).setNegativeButton("Отмена", null)
                .setPositiveButton("Подключиться", (d,w) -> {
                    String roomId = code.getText().toString().trim();
                    this.<JSONObject>runNetwork(() -> { JSONObject found=sync.getWatchRoom(roomId);if(found!=null){JSONObject movie=sync.fetchMovieRoom();if(movie!=null)found.put("player_url",movie.optString("url",""));}return found; }, room -> {
                        if (room == null || !"active".equals(room.optString("state"))) { toast("Активная комната с таким кодом не найдена"); return; }
                        startActivity(WatchPartyActivity.intent(this, roomId, false, room.optString("title", "Совместный просмотр")).putExtra(WatchPartyActivity.EXTRA_PLAYER_URL, room.optString("player_url", "")));
                    });
                }).show();
    }

    private void profileDialog() {
        LinearLayout form = column(); form.setPadding(dp(22), 0, dp(22), 0);
        EditText name = new EditText(this); name.setSingleLine(true); name.setHint("Ваше имя"); name.setText(prefs.getString("profile_name", ""));
        EditText avatar = new EditText(this); avatar.setSingleLine(true); avatar.setHint("Эмодзи профиля, например 🌿"); avatar.setText(prefs.getString("profile_avatar", "♡"));
        EditText bio = new EditText(this); bio.setHint("О себе или короткое пожелание"); bio.setText(prefs.getString("profile_bio", "")); bio.setMinLines(3); bio.setGravity(Gravity.TOP | Gravity.START);
        LinearLayout previewRow = new LinearLayout(this); previewRow.setGravity(Gravity.CENTER_VERTICAL); previewRow.setPadding(dp(14), dp(12), dp(14), dp(12)); previewRow.setBackground(shape(PALE, 18));
        TextView avatarPreview = text(avatar.getText().toString().trim().isEmpty() ? "♡" : avatar.getText().toString().trim(), 27, PURPLE, true); avatarPreview.setGravity(Gravity.CENTER); avatarPreview.setBackground(shape(PAPER, 30));
        previewRow.addView(avatarPreview, new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout previewCopy = column(); previewCopy.setPadding(dp(12), 0, 0, 0); previewCopy.addView(text("Ваш профиль", 15, INK, true));
        TextView previewName = text(name.getText().toString().trim().isEmpty() ? "Как вас будут видеть" : name.getText().toString().trim(), 12, MUTED, false); previewCopy.addView(previewName);
        previewRow.addView(previewCopy);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, -2); previewParams.bottomMargin = dp(14); form.addView(previewRow, previewParams);
        TextView nameLabel = text("Имя", 12, MUTED, true); form.addView(nameLabel); form.addView(name);
        TextView avatarLabel = text("Аватар", 12, MUTED, true); avatarLabel.setPadding(0, dp(8), 0, 0); form.addView(avatarLabel); form.addView(avatar);
        TextView bioLabel = text("Пара слов о себе", 12, MUTED, true); bioLabel.setPadding(0, dp(8), 0, 0); form.addView(bioLabel); form.addView(bio);
        name.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { previewName.setText(s.length() == 0 ? "Как вас будут видеть" : s); }
            public void afterTextChanged(android.text.Editable e) { }
        });
        avatar.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { avatarPreview.setText(s.length() == 0 ? "♡" : s); }
            public void afterTextChanged(android.text.Editable e) { }
        });
        new AlertDialog.Builder(this).setTitle("Мой профиль").setView(form).setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (d,w) -> {
                    String n = name.getText().toString().trim(), a = avatar.getText().toString().trim(), b = bio.getText().toString().trim();
                    if (n.isEmpty()) { toast("Введите имя для профиля"); return; }
                    if (a.isEmpty()) a = "♡";
                    prefs.edit().putString("profile_name", n).putString("profile_avatar", a).putString("profile_bio", b).apply();
                    JSONObject profile = new JSONObject();
                    try { profile.put("display_name", n); profile.put("avatar", a); profile.put("bio", b); } catch (Exception ignored) { }
                    persistProfile(profile); render();
                }).show();
    }

    private void persistProfile(JSONObject profile) {
        if (!sync.configured() || !sync.signedIn() || sync.coupleId().isEmpty()) { toast("Профиль сохранён на этом телефоне. Подключите пару для синхронизации."); return; }
        runNetwork(() -> { sync.saveProfile(profile); return "ok"; }, result -> toast("Профиль синхронизирован с партнёром"));
    }

    private void refreshProfiles() {
        if (!sync.configured() || !sync.signedIn() || sync.coupleId().isEmpty()) return;
        this.<JSONArray>runNetwork(() -> sync.fetchProfiles(), rows -> {
            String ownId = sync.userId();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                if (ownId.equals(row.optString("user_id"))) {
                    String name = row.optString("display_name", "").trim();
                    if (!name.isEmpty()) prefs.edit().putString("profile_name", name)
                            .putString("profile_avatar", row.optString("avatar", "♡"))
                            .putString("profile_bio", row.optString("bio", "")).apply();
                } else {
                    prefs.edit().putString("partner_name", row.optString("display_name", "").trim())
                            .putString("partner_avatar", row.optString("avatar", "♡"))
                            .putString("partner_bio", row.optString("bio", "")).apply();
                }
            }
            if (body != null) render();
        });
    }

    private JSONObject readMovieRoom() { try { String raw = prefs.getString("movie_room", ""); return raw.isEmpty() ? null : new JSONObject(raw); } catch (Exception e) { return null; } }
    private void editMovieRoom(JSONObject existing) {
        LinearLayout form = column(); form.setPadding(dp(18), 0, dp(18), 0);
        EditText title = new EditText(this); title.setSingleLine(true); title.setHint("Название фильма");
        EditText url = new EditText(this); url.setSingleLine(true); url.setHint("Ссылка на фильм или видео"); url.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        if (existing != null) { title.setText(existing.optString("title")); url.setText(existing.optString("url")); }
        form.addView(title); form.addView(url);
        new AlertDialog.Builder(this).setTitle("Кино-комната").setMessage("Ссылка откроется в приложении или браузере, где доступен фильм. Для синхронизации комнаты оба телефона должны быть подключены к одному пространству.")
                .setView(form).setNegativeButton("Отмена", null).setPositiveButton("Сохранить", (d, w) -> {
                    String name = title.getText().toString().trim(), link = url.getText().toString().trim();
                    if (name.isEmpty()) name = "Вечер кино";
                    if (!link.isEmpty() && !link.startsWith("https://") && !link.startsWith("http://")) link = "https://" + link;
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

    private void shareText(String value) {
        Intent send = new Intent(Intent.ACTION_SEND); send.setType("text/plain"); send.putExtra(Intent.EXTRA_TEXT, value);
        startActivity(Intent.createChooser(send, "Поделиться"));
    }

    private interface NetworkWork { Object run() throws Exception; }
    private interface NetworkDone<T> { void done(T result); }
    @SuppressWarnings("unchecked") private <T> void runNetwork(NetworkWork work, NetworkDone<T> done) {
        Toast.makeText(this, "Подключаемся…", Toast.LENGTH_SHORT).show();
        network.execute(() -> {
            try { Object value = work.run(); runOnUiThread(() -> done.done((T) value)); }
            catch (Exception e) { runOnUiThread(() -> toast("Не удалось выполнить действие: " + e.getMessage())); }
        });
    }

    private void syncItems() {
        runNetwork(() -> { org.json.JSONArray rows = sync.fetchItems(); for (int i=0;i<rows.length();i++) sync.storeRemote(rows.getJSONObject(i), prefs); return rows.length(); }, count -> { render(); toast("Получено записей: " + count); });
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

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
                String next="sea_battle".equals(type)?"waiting":"active";
                game=pairGames.update(game.getString("id"),game.optLong("version"),state,next);
            }else throw new Exception("Идёт другая партия; её можно открыть только в аккаунте вашего партнёра");
        }
        return game;
    }
    private JSONArray randomFleet(){
        JSONArray fleet=new JSONArray();java.util.HashSet<Integer> occupied=new java.util.HashSet<>();Random random=new Random();int[] lengths={3,2,1};
        for(int len:lengths){boolean placed=false;for(int tries=0;tries<1000&&!placed;tries++){boolean horizontal=random.nextBoolean();int row=random.nextInt(8),col=random.nextInt(8);if(horizontal&&col+len>8||!horizontal&&row+len>8)continue;ArrayList<Integer> cells=new ArrayList<>();boolean clear=true;for(int k=0;k<len;k++){int r=row+(horizontal?0:k),c=col+(horizontal?k:0),p=r*8+c;cells.add(p);for(int dr=-1;dr<=1;dr++)for(int dc=-1;dc<=1;dc++){int nr=r+dr,nc=c+dc;if(nr>=0&&nr<8&&nc>=0&&nc<8&&occupied.contains(nr*8+nc))clear=false;}}if(clear){JSONArray ship=new JSONArray();for(int p:cells){occupied.add(p);ship.put(p);}fleet.put(ship);placed=true;}}
            if(!placed)return randomFleet();
        }return fleet;
    }
    private void onlineTicTacToe(){
        if(!pairGames.ready()){toast("Войдите в аккаунт и подключитесь к партнёру по коду приглашения");return;}
        toast("Подключаем партию…");network.execute(()->{try{JSONObject g=openOrJoinGame("tic_tac_toe");runOnUiThread(()->showOnlineTicTacToe(g));}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});
    }
    private void showOnlineTicTacToe(JSONObject initial){
        final JSONObject[] game={initial};final boolean[] closed={false};final Runnable[] poll={null};
        LinearLayout panel=column();panel.setPadding(dp(14),dp(8),dp(14),dp(8));
        TextView status=text("",14,INK,true);status.setGravity(Gravity.CENTER);status.setPadding(dp(10),dp(10),dp(10),dp(10));status.setBackground(shape(PALE,15));panel.addView(status);
        LinearLayout board=column();board.setGravity(Gravity.CENTER);board.setPadding(dp(8),dp(8),dp(8),dp(8));board.setBackground(shape(0xFFEDE4F1,20));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(10);panel.addView(board,bp);
        TextView restart=pill("Новая партия",PURPLE);restart.setGravity(Gravity.CENTER);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(10);panel.addView(restart,rp);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Крестики-нолики · онлайн").setView(panel).setNegativeButton("Закрыть",null).create();
        Runnable[] draw={null};draw[0]=()->{JSONObject g=game[0],s=g.optJSONObject("state");if(s==null)return;String me=pairGames.userId(),p1=s.optString("p1"),p2=s.optString("p2"),boardState=s.optString("board","........."),turn=s.optString("turn"),winner=s.optString("winner");boolean active="active".equals(g.optString("status"));
            if(p2.isEmpty())status.setText("Комната создана. Ждём, пока партнёр откроет эту игру на своём телефоне.");else if(!winner.isEmpty())status.setText(winner.equals(me)?"Вы победили! ✨":"Партнёр победил. Сыграем ещё? 💛");else if(!active)status.setText("Партия завершена");else status.setText(turn.equals(me)?"Ваш ход · "+(me.equals(p1)?"X":"O"):"Ход партнёра · "+(me.equals(p1)?"O":"X"));
            board.removeAllViews();for(int row=0;row<3;row++){LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER);board.addView(line);for(int col=0;col<3;col++){int i=row*3+col;char mark=i<boardState.length()?boardState.charAt(i):'.';TextView cell=text(mark=='.'?"":String.valueOf(mark),31,mark=='X'?0xFF71529B:0xFFD66D92,true);cell.setGravity(Gravity.CENTER);cell.setBackground(shape(0xFFFBF9FC,14));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(70),1);cp.setMargins(dp(3),dp(3),dp(3),dp(3));line.addView(cell,cp);cell.setEnabled(active&&turn.equals(me)&&mark=='.'&&winner.isEmpty());cell.setOnClickListener(v->{network.execute(()->{try{JSONObject fresh=pairGames.fetch(g.getString("id"));if(fresh==null)return;JSONObject ns=new JSONObject(fresh.getJSONObject("state").toString());if(!pairGames.userId().equals(ns.optString("turn")))return;String b=ns.optString("board",".........");if(b.charAt(i)!='.')return;char player=me.equals(ns.optString("p1"))?'X':'O';StringBuilder sb=new StringBuilder(b);sb.setCharAt(i,player);ns.put("board",sb.toString());boolean won=hasWinner(sb.toString(),player);boolean full=sb.indexOf(".")<0;if(won)ns.put("winner",me);else if(!full)ns.put("turn",me.equals(ns.optString("p1"))?ns.optString("p2"):ns.optString("p1"));String newStatus=won||full?"finished":"active";JSONObject saved=pairGames.update(fresh.getString("id"),fresh.optLong("version"),ns,newStatus);runOnUiThread(()->{game[0]=saved;draw[0].run();});}catch(Exception ex){runOnUiThread(()->toast(ex.getMessage()));}});});}}
        };
        restart.setOnClickListener(v->{JSONObject g=game[0],s=g.optJSONObject("state");if(s==null||!s.optString("p1").equals(pairGames.userId())&&!s.optString("p2").equals(pairGames.userId()))return;network.execute(()->{try{JSONObject ns=new JSONObject(s.toString());ns.put("board",".........");ns.put("winner","");ns.put("turn",ns.optString("p1"));JSONObject saved=pairGames.update(g.getString("id"),g.optLong("version"),ns,"active");runOnUiThread(()->{game[0]=saved;draw[0].run();});}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});});
        draw[0].run();dialog.setOnDismissListener(d->{closed[0]=true;});dialog.show();poll[0]=()->{if(closed[0])return;network.execute(()->{try{JSONObject fresh=pairGames.fetch(game[0].getString("id"));if(fresh!=null)runOnUiThread(()->{game[0]=fresh;draw[0].run();});}catch(Exception ignored){}if(!closed[0])gameHandler.postDelayed(poll[0],1400);});};gameHandler.postDelayed(poll[0],1400);
    }
    private boolean hasWinner(String board,char p){int[][] lines={{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};for(int[] l:lines)if(board.charAt(l[0])==p&&board.charAt(l[1])==p&&board.charAt(l[2])==p)return true;return false;}
    private void onlineSeaBattle(){
        if(!pairGames.ready()){toast("Войдите в аккаунт и подключитесь к партнёру по коду приглашения");return;}
        toast("Подключаем морской бой…");network.execute(()->{try{JSONObject g=openOrJoinGame("sea_battle");runOnUiThread(()->showOnlineSeaBattle(g));}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});
    }
    private void showOnlineSeaBattle(JSONObject initial){
        final JSONObject[] game={initial};final boolean[] closed={false};final Runnable[] poll={null};
        LinearLayout panel=column();panel.setPadding(dp(8),dp(6),dp(8),dp(6));
        TextView status=text("",13,INK,true);status.setGravity(Gravity.CENTER);status.setPadding(dp(8),dp(8),dp(8),dp(8));status.setBackground(shape(PALE,14));panel.addView(status);
        LinearLayout board=column();board.setPadding(dp(3),dp(3),dp(3),dp(3));board.setBackground(shape(0xFFDCECF1,16));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(7);panel.addView(board,bp);
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=dp(6);panel.addView(actions,ap);
        TextView shuffle=pill("↻  Переставить",PURPLE);shuffle.setGravity(Gravity.CENTER);actions.addView(shuffle,new LinearLayout.LayoutParams(0,dp(44),1));
        TextView ready=pill("Готов",PURPLE);ready.setGravity(Gravity.CENTER);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(44),1);rp.leftMargin=dp(8);actions.addView(ready,rp);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Морской бой · онлайн").setView(panel).setNegativeButton("Закрыть",null).create();
        Runnable[] draw={null};draw[0]=()->{JSONObject g=game[0],s=g.optJSONObject("state");if(s==null)return;String me=pairGames.userId(),p1=s.optString("p1"),p2=s.optString("p2"),winner=s.optString("winner"),turn=s.optString("turn");boolean first=me.equals(p1);String fleetKey=first?"p1Fleet":"p2Fleet",shotsKey=first?"p1Shots":"p2Shots",readyKey=first?"p1Ready":"p2Ready";JSONArray own=s.optJSONArray(fleetKey);if(own==null)own=new JSONArray();JSONArray shots=s.optJSONArray(shotsKey);if(shots==null)shots=new JSONArray();boolean bothReady=s.optBoolean("p1Ready")&&s.optBoolean("p2Ready"),active="active".equals(g.optString("status"));
            if(p2.isEmpty())status.setText("Расставьте флот. Ждём, когда партнёр войдёт в игру со своего телефона.");else if(!winner.isEmpty())status.setText(winner.equals(me)?"Вы победили! ⚓":"Партнёр нашёл ваш флот. Сыграем ещё? 💛");else if(!bothReady)status.setText("Поставьте флот и нажмите «Готов». Игра начнётся, когда будут готовы оба.");else if(turn.equals(me))status.setText("Ваш ход · выберите клетку на поле партнёра");else status.setText("Ход партнёра · ваш флот пока скрыт");
            boolean setup=!bothReady&&winner.isEmpty();shuffle.setVisibility(setup?View.VISIBLE:View.GONE);ready.setVisibility(setup?View.VISIBLE:View.GONE);ready.setText(s.optBoolean(readyKey)?"Вы готовы ✓":"Готов");
            board.removeAllViews();HashSet<Integer> ownCells=new HashSet<>();for(int j=0;j<own.length();j++){JSONArray ship=own.optJSONArray(j);if(ship!=null)for(int k=0;k<ship.length();k++)ownCells.add(ship.optInt(k,-1));}JSONArray enemyFleet=s.optJSONArray(first?"p2Fleet":"p1Fleet");if(enemyFleet==null)enemyFleet=new JSONArray();HashSet<Integer> enemyCells=new HashSet<>();for(int j=0;j<enemyFleet.length();j++){JSONArray ship=enemyFleet.optJSONArray(j);if(ship!=null)for(int k=0;k<ship.length();k++)enemyCells.add(ship.optInt(k,-1));}
            for(int row=0;row<8;row++){LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER);board.addView(line);for(int col=0;col<8;col++){int index=row*8+col;boolean wasShot=contains(shots,index);TextView cell=text("",13,INK,true);cell.setGravity(Gravity.CENTER);int bg=0xFFB9D9E4;if(setup&&ownCells.contains(index)){cell.setText("▰");bg=0xFF597F91;}else if(!setup&&wasShot){boolean hit=enemyCells.contains(index);cell.setText(hit?"✦":"·");bg=hit?0xFFF0C8CC:0xFFEAF4F6;}cell.setBackground(shape(bg,5));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(34),1);cp.setMargins(dp(1),dp(1),dp(1),dp(1));line.addView(cell,cp);cell.setEnabled(setup||active&&turn.equals(me)&&!wasShot&&winner.isEmpty());cell.setOnClickListener(v->{ });if(!setup){cell.setOnClickListener(v->fireSeaShot(index,game,draw));}}}
        };
        shuffle.setOnClickListener(v->network.execute(()->{try{JSONObject fresh=pairGames.fetch(game[0].getString("id"));JSONObject s=new JSONObject(fresh.getJSONObject("state").toString());s.put(meets(s,"p1")?"p1Fleet":"p2Fleet",randomFleet());s.put(meets(s,"p1")?"p1Ready":"p2Ready",false);JSONObject saved=pairGames.update(fresh.getString("id"),fresh.optLong("version"),s,"waiting");runOnUiThread(()->{game[0]=saved;draw[0].run();});}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}}));
        ready.setOnClickListener(v->network.execute(()->{try{JSONObject fresh=pairGames.fetch(game[0].getString("id"));JSONObject s=new JSONObject(fresh.getJSONObject("state").toString());boolean first=pairGames.userId().equals(s.optString("p1"));String key=first?"p1Ready":"p2Ready";s.put(key,true);boolean start=s.optBoolean(first?"p2Ready":"p1Ready")&&!s.optString("p2").isEmpty();if(start)s.put("turn",s.optString("p1"));JSONObject saved=pairGames.update(fresh.getString("id"),fresh.optLong("version"),s,start?"active":"waiting");runOnUiThread(()->{game[0]=saved;draw[0].run();});}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}}));
        draw[0].run();dialog.setOnDismissListener(d->{closed[0]=true;});dialog.show();poll[0]=()->{if(closed[0])return;network.execute(()->{try{JSONObject fresh=pairGames.fetch(game[0].getString("id"));if(fresh!=null)runOnUiThread(()->{game[0]=fresh;draw[0].run();});}catch(Exception ignored){}if(!closed[0])gameHandler.postDelayed(poll[0],1400);});};gameHandler.postDelayed(poll[0],1400);
    }
    private boolean meets(JSONObject s,String key){return pairGames.userId().equals(s.optString(key));}
    private boolean contains(JSONArray values,int n){for(int i=0;i<values.length();i++)if(values.optInt(i,-1)==n)return true;return false;}
    private void fireSeaShot(int index,JSONObject[] game,Runnable[] draw){network.execute(()->{try{JSONObject fresh=pairGames.fetch(game[0].getString("id"));JSONObject s=new JSONObject(fresh.getJSONObject("state").toString());String me=pairGames.userId();if(!"active".equals(fresh.optString("status"))||!me.equals(s.optString("turn")))return;boolean first=me.equals(s.optString("p1"));String shotsKey=first?"p1Shots":"p2Shots",targetFleetKey=first?"p2Fleet":"p1Fleet";JSONArray shots=s.optJSONArray(shotsKey);if(shots==null)shots=new JSONArray();if(contains(shots,index))return;shots.put(index);s.put(shotsKey,shots);JSONArray fleet=s.optJSONArray(targetFleetKey);boolean hit=false;int totalHits=0;if(fleet!=null)for(int j=0;j<fleet.length();j++){JSONArray ship=fleet.optJSONArray(j);if(ship!=null)for(int k=0;k<ship.length();k++){int cell=ship.optInt(k,-1);if(cell==index)hit=true;if(contains(shots,cell))totalHits++;}}String winner=s.optString("winner");String status="active";if(totalHits>=6){winner=me;s.put("winner",me);status="finished";}else if(!hit)s.put("turn",first?s.optString("p2"):s.optString("p1"));JSONObject saved=pairGames.update(fresh.getString("id"),fresh.optLong("version"),s,status);runOnUiThread(()->{game[0]=saved;draw[0].run();});}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});}
    private void onlineDurak(){
        if(!pairGames.ready()){toast("Войдите в аккаунт и подключитесь к партнёру по коду приглашения");return;}
        network.execute(()->{try{JSONObject g=openOrJoinGame("durak");runOnUiThread(()->showOnlineDurak(g));}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});
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
            table.setText(attack<0?"Стол пуст":"Атака  "+showDurakCard(attack)+(defense>=0?"     ·     Отбой  "+showDurakCard(defense):""));deckInfo.setText("Карт у партнёра: "+s.optJSONArray(first?"p2Hand":"p1Hand").length()+"  ·  колода: "+deck+"  ·  козырь: "+s.optString("trump"));boolean mayTake=active&&p2.length()>0&&attacker.equals(me)?false:active&&p2.length()>0&&!attacker.equals(me)&&attack>=0&&defense<0;take.setVisibility(mayTake?View.VISIBLE:View.GONE);
            hand.removeAllViews();for(int k=0;k<own.length();k++){int code=own.optInt(k,-1);if(code<0||code>=36)continue;Card card=new Card(6+code/4,code%4);TextView button=text(card.show(),17,card.suit==1||card.suit==2?0xFFD84B5E:INK,true);button.setGravity(Gravity.CENTER);button.setBackground(shape(PAPER,11));button.setElevation(dp(2));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(62),1);cp.setMargins(dp(2),dp(2),dp(2),dp(2));hand.addView(button,cp);boolean canPlay=active&&p2.length()>0&&(attacker.equals(me)?attack<0:attack>=0&&defense<0&&beats(card,new Card(6+attack/4,attack%4),s.optInt("trump")));button.setEnabled(canPlay);button.setOnClickListener(v->playDurakCard(code,game,draw));}
        };
        take.setOnClickListener(v->takeDurakCard(game,draw));draw[0].run();dialog.setOnDismissListener(d->{closed[0]=true;});dialog.show();poll[0]=()->{if(closed[0])return;network.execute(()->{try{JSONObject fresh=pairGames.fetch(game[0].getString("id"));if(fresh!=null)runOnUiThread(()->{game[0]=fresh;draw[0].run();});}catch(Exception ignored){}if(!closed[0])gameHandler.postDelayed(poll[0],1400);});};gameHandler.postDelayed(poll[0],1400);
    }
    private String showDurakCard(int code){return new Card(6+code/4,code%4).show();}
    private void playDurakCard(int code,JSONObject[] game,Runnable[] draw){network.execute(()->{try{JSONObject fresh=pairGames.fetch(game[0].getString("id"));JSONObject s=new JSONObject(fresh.getJSONObject("state").toString());String me=pairGames.userId(),attacker=s.optString("attacker");boolean first=me.equals(s.optString("p1"));String handKey=first?"p1Hand":"p2Hand";JSONArray hand=s.optJSONArray(handKey);int attack=s.optInt("attack",-1),defense=s.optInt("defense",-1);int cardIndex=-1;for(int i=0;i<hand.length();i++)if(hand.optInt(i)==code)cardIndex=i;if(cardIndex<0)throw new Exception("Этой карты уже нет в вашей руке");if(me.equals(attacker)){if(attack>=0)throw new Exception("Сначала завершите текущую пару карт");s.put("attack",code);hand.remove(cardIndex);}else{if(attack<0||defense>=0)throw new Exception("Сейчас нельзя отбить карту");Card c=new Card(6+code/4,code%4),a=new Card(6+attack/4,attack%4);if(!beats(c,a,s.optInt("trump")))throw new Exception("Подходит только карта выше по масти или козырь");s.put("defense",code);hand.remove(cardIndex);settleDurak(s,true);}
            JSONObject saved=pairGames.update(fresh.getString("id"),fresh.optLong("version"),s,s.optString("winner").isEmpty()?"active":"finished");runOnUiThread(()->{game[0]=saved;draw[0].run();});}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});}
    private void takeDurakCard(JSONObject[] game,Runnable[] draw){network.execute(()->{try{JSONObject fresh=pairGames.fetch(game[0].getString("id"));JSONObject s=new JSONObject(fresh.getJSONObject("state").toString());if(s.optInt("attack",-1)<0||s.optInt("defense",-1)>=0)throw new Exception("Сейчас нечего забирать");settleDurak(s,false);JSONObject saved=pairGames.update(fresh.getString("id"),fresh.optLong("version"),s,s.optString("winner").isEmpty()?"active":"finished");runOnUiThread(()->{game[0]=saved;draw[0].run();});}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});}
    private void settleDurak(JSONObject s,boolean defended)throws Exception{String attacker=s.optString("attacker"),defender=attacker.equals(s.optString("p1"))?s.optString("p2"):s.optString("p1");JSONArray h1=s.optJSONArray("p1Hand"),h2=s.optJSONArray("p2Hand"),deck=s.optJSONArray("deck");if(!defended){JSONArray dh=defender.equals(s.optString("p1"))?h1:h2;dh.put(s.optInt("attack"));}s.put("attacker",defended?defender:attacker);s.put("attack",-1);s.put("defense",-1);for(String player:new String[]{attacker,defender}){JSONArray h=player.equals(s.optString("p1"))?h1:h2;while(h.length()<6&&deck.length()>0)h.put(deck.remove(deck.length()-1));}s.put("p1Hand",h1);s.put("p2Hand",h2);s.put("deck",deck);if(deck.length()==0){boolean one=h1.length()==0,two=h2.length()==0;if(one&&two)s.put("winner","draw");else if(one)s.put("winner",s.optString("p1"));else if(two)s.put("winner",s.optString("p2"));}}
    private void onlineCoupleQuiz(String mode,String question){if(!pairGames.ready()){toast("Сначала войдите в аккаунт и подключитесь к партнёру");return;}network.execute(()->{try{JSONObject g=openOrJoinGame("couple_quiz",question);runOnUiThread(()->showOnlineCoupleQuiz(g));}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});}
    private void showOnlineCoupleQuiz(JSONObject initial){
        final JSONObject[] game={initial};final boolean[] closed={false};final Runnable[] poll={null};LinearLayout panel=column();panel.setPadding(dp(18),dp(8),dp(18),dp(8));TextView prompt=text("",16,INK,true);panel.addView(prompt);TextView status=text("",13,PURPLE,true);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(10);panel.addView(status,sp);EditText answer=new EditText(this);answer.setHint("Ваш ответ — он пока скрыт от партнёра");answer.setMinLines(2);LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.topMargin=dp(9);panel.addView(answer,ep);TextView submit=pill("Отправить ответ",PURPLE);submit.setGravity(Gravity.CENTER);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(46));bp.topMargin=dp(8);panel.addView(submit,bp);TextView reveal=text("",14,INK,false);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(10);panel.addView(reveal,rp);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Вопрос для двоих · онлайн").setView(panel).setNegativeButton("Закрыть",null).create();Runnable[] draw={null};draw[0]=()->{JSONObject g=game[0],s=g.optJSONObject("state");if(s==null)return;String me=pairGames.userId(),p2=s.optString("p2"),ownKey=me.equals(s.optString("p1"))?"p1Answer":"p2Answer",a=s.optString("p1Answer"),b=s.optString("p2Answer");prompt.setText(s.optString("question"));boolean ready="active".equals(g.optString("status")),done=!a.isEmpty()&&!b.isEmpty();status.setText(p2.isEmpty()?"Ждём, когда партнёр присоединится со своего телефона.":done?"Оба ответа открыты 💛":!ready?"Ожидаем партнёра…":s.optString(ownKey).isEmpty()?"Напишите свой ответ — партнёр увидит его, когда ответит тоже.":"Ваш ответ сохранён. Ждём ответ партнёра…");answer.setVisibility(ready&&!done&&s.optString(ownKey).isEmpty()?View.VISIBLE:View.GONE);submit.setVisibility(answer.getVisibility());reveal.setText(done?"Ваш ответ: "+a+"\nОтвет партнёра: "+b:"");};submit.setOnClickListener(v->{String value=answer.getText().toString().trim();if(value.isEmpty())return;network.execute(()->{try{JSONObject fresh=pairGames.fetch(game[0].getString("id"));JSONObject s=new JSONObject(fresh.getJSONObject("state").toString());String key=pairGames.userId().equals(s.optString("p1"))?"p1Answer":"p2Answer";if(!s.optString(key).isEmpty())return;s.put(key,value);boolean done=!s.optString("p1Answer").isEmpty()&&!s.optString("p2Answer").isEmpty();JSONObject saved=pairGames.update(fresh.getString("id"),fresh.optLong("version"),s,done?"finished":"active");runOnUiThread(()->{game[0]=saved;answer.setText("");draw[0].run();});}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});});draw[0].run();dialog.setOnDismissListener(d->closed[0]=true);dialog.show();poll[0]=()->{if(closed[0])return;network.execute(()->{try{JSONObject g=pairGames.fetch(game[0].getString("id"));if(g!=null)runOnUiThread(()->{game[0]=g;draw[0].run();});}catch(Exception ignored){}if(!closed[0])gameHandler.postDelayed(poll[0],1600);});};gameHandler.postDelayed(poll[0],1600);
    }
    private void onlineGuessTune(){if(!pairGames.ready()){toast("Сначала войдите в аккаунт и подключитесь к партнёру");return;}network.execute(()->{try{JSONObject g=openOrJoinGame("guess_tune");runOnUiThread(()->showOnlineGuessTune(g));}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});}
    private void showOnlineGuessTune(JSONObject initial){
        final JSONObject[] game={initial};final boolean[] closed={false};final Runnable[] poll={null};LinearLayout panel=column();panel.setPadding(dp(18),dp(8),dp(18),dp(8));TextView prompt=text("",16,INK,true);panel.addView(prompt);TextView status=text("",13,PURPLE,true);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(9);panel.addView(status,sp);EditText answer=new EditText(this);answer.setSingleLine(true);answer.setHint("Введите название мелодии");LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.topMargin=dp(9);panel.addView(answer,ep);TextView action=pill("Я напел — угадывай",PURPLE);action.setGravity(Gravity.CENTER);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(46));ap.topMargin=dp(8);panel.addView(action,ap);TextView result=text("",14,INK,true);panel.addView(result);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Угадай мелодию · онлайн").setView(panel).setNegativeButton("Закрыть",null).create();Runnable[] draw={null};draw[0]=()->{JSONObject s=game[0].optJSONObject("state");if(s==null)return;String me=pairGames.userId();boolean host=me.equals(s.optString("p1"));String guess=s.optString("p2Answer"),winner=s.optString("winner");if(host){prompt.setText("Напойте партнёру: «"+s.optString("correct")+"».");answer.setVisibility(View.GONE);action.setVisibility("finished".equals(game[0].optString("status"))?View.GONE:View.VISIBLE);status.setText(s.optBoolean("p1Ready")?"Партнёр угадывает…":"Найдите эту мелодию и спойте её вслух.");result.setText(!guess.isEmpty()?"Ответ: "+guess+" · "+(winner.equals(me)?"Угадано! 🎉":"Попробуйте ещё раз в следующей партии") : "");}else{prompt.setText(s.optBoolean("p1Ready")?"Партнёр напел мелодию. Что это было?":"Ждём, пока партнёр напоёт мелодию…");answer.setVisibility(s.optBoolean("p1Ready")&&guess.isEmpty()?View.VISIBLE:View.GONE);action.setVisibility(answer.getVisibility());action.setText("Отправить ответ");status.setText(guess.isEmpty()?"Введите название, затем отправьте ответ.":"Ответ отправлен. Ждём результат…");result.setText(!guess.isEmpty()?"Ответ: "+guess+" · "+(winner.equals(me)?"Верно! 🎉":"Не угадано — следующая песня будет вашей") : "");}};
        action.setOnClickListener(v->{boolean host=pairGames.userId().equals(game[0].optJSONObject("state").optString("p1"));String value=answer.getText().toString().trim();if(!host&&value.isEmpty())return;network.execute(()->{try{JSONObject fresh=pairGames.fetch(game[0].getString("id"));JSONObject s=new JSONObject(fresh.getJSONObject("state").toString());String next="active";if(host){s.put("p1Ready",true);}else{if(!s.optBoolean("p1Ready"))throw new Exception("Сначала дождитесь мелодию");s.put("p2Answer",value);boolean correct=value.equalsIgnoreCase(s.optString("correct"));if(correct)s.put("winner",pairGames.userId());else s.put("winner","wrong");next="finished";}JSONObject saved=pairGames.update(fresh.getString("id"),fresh.optLong("version"),s,next);runOnUiThread(()->{game[0]=saved;answer.setText("");draw[0].run();});}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});});draw[0].run();dialog.setOnDismissListener(d->closed[0]=true);dialog.show();poll[0]=()->{if(closed[0])return;network.execute(()->{try{JSONObject g=pairGames.fetch(game[0].getString("id"));if(g!=null)runOnUiThread(()->{game[0]=g;draw[0].run();});}catch(Exception ignored){}if(!closed[0])gameHandler.postDelayed(poll[0],1600);});};gameHandler.postDelayed(poll[0],1600);
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
        if (s.contains("Заметки")) return "notes"; if (s.contains("Файлы")) return "files";
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
        JSONArray messages=read("messages");
        LinearLayout box=column();box.setPadding(dp(12),dp(12),dp(12),dp(12));box.setBackground(shape(PAPER,22));body.addView(box);
        if(messages.length()==0){TextView empty=text("Напишите первое сообщение — чат только для вас двоих",13,MUTED,false);empty.setGravity(Gravity.CENTER);box.addView(empty);}
        for(int i=0;i<messages.length();i++){
            JSONObject row=messages.optJSONObject(i);if(row==null)continue;
            boolean mine=row.optString("sender_id",sync.userId()).equals(sync.userId());
            LinearLayout line=new LinearLayout(this);line.setGravity(mine?Gravity.RIGHT:Gravity.LEFT);line.setPadding(dp(2),dp(4),dp(2),dp(4));
            TextView avatar=text(mine?prefs.getString("profile_avatar","♡"):prefs.getString("partner_avatar","♡"),16,PURPLE,true);avatar.setGravity(Gravity.CENTER);avatar.setBackground(shape(PALE,18));
            TextView bubble=text(row.optString("title"),14,INK,false);bubble.setPadding(dp(13),dp(10),dp(13),dp(10));bubble.setMaxWidth(dp(280));bubble.setBackground(shape(mine?0xFFEFE5F6:0xFFF3F4F6,18));
            if(!mine)line.addView(avatar,new LinearLayout.LayoutParams(dp(32),dp(32)));
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2);bp.setMargins(dp(7),0,dp(7),0);line.addView(bubble,bp);
            if(mine)line.addView(avatar,new LinearLayout.LayoutParams(dp(32),dp(32)));
            box.addView(line);
        }
        LinearLayout composer=new LinearLayout(this);composer.setGravity(Gravity.CENTER_VERTICAL);body.addView(composer);
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("Сообщение партнёру…");composer.addView(input,new LinearLayout.LayoutParams(0,dp(52),1));
        TextView send=pill("Отправить",PURPLE);composer.addView(send);send.setOnClickListener(v->{String value=input.getText().toString().trim();if(!value.isEmpty()){append("messages",value,"Сейчас");render();}});
        if(sync.ready())network.execute(()->{try{JSONArray remote=sync.fetchItems();for(int i=0;i<remote.length();i++)sync.storeRemote(remote.getJSONObject(i),prefs);runOnUiThread(()->{if(feature!=null&&feature.contains("чата"))render();});}catch(Exception ignored){}});
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
    private void syncItem(String key,JSONObject item){if(sync!=null&&sync.ready())network.execute(()->{try{sync.upsert(key,item);}catch(Exception e){android.util.Log.w("VmesteSync","Could not sync shared item",e);}});}
    private void replaceItem(String key,JSONObject item){JSONArray rows=read(key);for(int i=0;i<rows.length();i++){JSONObject old=rows.optJSONObject(i);if(old!=null&&old.optString("id").equals(item.optString("id"))){try{rows.put(i,item);save(key,rows);syncItem(key,item);}catch(Exception ignored){}return;}}}
    private void removeRemoteItem(String id){if(sync!=null&&sync.ready())network.execute(()->{try{sync.deleteItem(id);}catch(Exception e){android.util.Log.w("VmesteSync","Could not delete shared item",e);}});}
    private void removeItem(String key,String id){JSONArray rows=read(key);for(int i=rows.length()-1;i>=0;i--){JSONObject item=rows.optJSONObject(i);if(item!=null&&id.equals(item.optString("id"))){rows.remove(i);break;}}save(key,rows);removeRemoteItem(id);}
    private void append(String key, String title, String detail) {
        JSONArray list=read(key); JSONObject item=new JSONObject();
        try { item.put("id", UUID.randomUUID().toString()); item.put("title", title); item.put("detail", detail); item.put("done", false); list.put(item); } catch(Exception ignored) { }
        save(key,list);
        if (sync != null && sync.configured() && sync.signedIn() && !sync.coupleId().isEmpty()) {
            JSONObject row = list.optJSONObject(list.length() - 1);
            if (row != null) syncItem(key,row);
        }
    }

    private void itemActions(String key,JSONObject item){
        ArrayList<String> options=new ArrayList<>();options.add("Редактировать");if("plans".equals(key))options.add("Добавить фото-отчёт");options.add("Удалить");
        String[] choices=options.toArray(new String[0]);new AlertDialog.Builder(this).setItems(choices,(d,which)->{
            String choice=choices[which];if(choice.equals("Редактировать")){editItemDialog(key,item);return;}
            if(choice.equals("Добавить фото-отчёт")){pendingPhotoKey=key;pendingPhotoItemId=item.optString("id");Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("image/*");pick.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(pick,403);return;}
            new AlertDialog.Builder(this).setMessage("Удалить «"+item.optString("title")+"»?").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(x,y)->{removeItem(key,item.optString("id"));render();}).show();
        }).show();
    }

    private void editItemDialog(String key,JSONObject item){
        LinearLayout form=column();form.setPadding(dp(18),0,dp(18),0);
        EditText title=new EditText(this);title.setSingleLine(true);title.setHint("Название");title.setText(item.optString("title"));form.addView(title);
        EditText detail=new EditText(this);detail.setHint("Описание или заметка");detail.setText(item.optString("detail"));form.addView(detail);
        EditText category=new EditText(this);category.setSingleLine(true);category.setHint("Раздел / категория");category.setText(item.optString("category"));if("wishes".equals(key))form.addView(category);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Изменить запись").setView(form).setNegativeButton("Отмена",null).setNeutralButton("Удалить",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(v->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{String value=title.getText().toString().trim();if(value.isEmpty()){title.setError("Введите название");return;}try{item.put("title",value);item.put("detail",detail.getText().toString().trim());if("wishes".equals(key))item.put("category",category.getText().toString().trim());replaceItem(key,item);dialog.dismiss();render();}catch(Exception ignored){}});dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(x->{removeItem(key,item.optString("id"));dialog.dismiss();render();});});dialog.show();
    }
    private void addDialog(String key, String title) {
        LinearLayout form = column(); form.setPadding(dp(22), dp(8), dp(22), dp(2));
        EditText name = new EditText(this); name.setHint("Что хотите сохранить?"); name.setSingleLine(true); form.addView(name);
        EditText detail = new EditText(this); detail.setHint("Заметка или дата (необязательно)"); form.addView(detail);
        EditText category = new EditText(this); category.setSingleLine(true); category.setHint("Раздел / категория"); if ("wishes".equals(key)) form.addView(category);
        new AlertDialog.Builder(this).setTitle(title).setView(form).setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (d,w) -> { String t=name.getText().toString().trim(); if (!t.isEmpty()) { append(key,t,detail.getText().toString().trim()); if("wishes".equals(key)){JSONArray rows=read(key);JSONObject item=rows.optJSONObject(rows.length()-1);if(item!=null)try{item.put("category",category.getText().toString().trim());save(key,rows);syncItem(key,item);}catch(Exception ignored){}} render(); } }).show();
    }
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
        c.addView(copy,new LinearLayout.LayoutParams(0,-2,1)); if(click!=null)c.setOnClickListener(v->click.run());
    }
    private LinearLayout card() {
        LinearLayout c=column(); c.setPadding(dp(17),dp(16),dp(17),dp(16)); c.setBackground(shape(PAPER,22)); c.setElevation(dp(1));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(9);body.addView(c,p);return c;
    }
    private void heading(String title,String sub) {
        TextView h=text(title,19,INK,true); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(13);p.bottomMargin=dp(3);body.addView(h,p);
        if(sub!=null){TextView s=text(sub,12,MUTED,false);LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(-1,-2);q.bottomMargin=dp(11);body.addView(s,q);}
    }
    private TextView pill(String value,int color) { TextView b=text(value,13,color,true);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(46));b.setPadding(dp(16),dp(11),dp(16),dp(11));b.setBackground(shape(PALE,22));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.bottomMargin=dp(10);b.setLayoutParams(p);return b; }
    private void addGap(LinearLayout parent,int h) { View gap=new View(this);parent.addView(gap,new LinearLayout.LayoutParams(1,dp(h))); }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==403&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();String key=pendingPhotoKey,id=pendingPhotoItemId;
            if(!sync.ready()){toast("Фото-отчёт можно синхронизировать после подключения пары");return;}
            toast("Загружаю фото-отчёт…");network.execute(()->{try{String path=sync.uploadAttachment(uri,id,this);runOnUiThread(()->{JSONObject item=findItem(read(key),id);if(item!=null)try{item.put("photo_path",path);item.put("reported_at",System.currentTimeMillis());replaceItem(key,item);render();toast("Фото прикреплено к плану для вас двоих");}catch(Exception e){toast(e.getMessage());}});}catch(Exception e){runOnUiThread(()->toast("Не удалось загрузить фото: "+e.getMessage()));}});return;
        }
        if(requestCode==402&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null) {
            Uri uri=data.getData(); String name=uri.getLastPathSegment(); append("files",name==null?"Выбранный файл":name,uri.toString()); render();
        }
    }
    @Override public void onBackPressed() {
        if(feature!=null){feature=null;render();} else if(!tab.equals("Главная")){tab="Главная";render();} else super.onBackPressed();
    }
}
