package com.lira.vmest;

import android.content.BroadcastReceiver;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "vmeste_shared_reminders";
    @Override public void onReceive(Context context, Intent intent) {
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID,context.getString(R.string.app_name)+" · общие напоминания",
                NotificationManager.IMPORTANCE_DEFAULT));
        android.app.Notification.Builder builder=Build.VERSION.SDK_INT>=26?new android.app.Notification.Builder(context,CHANNEL_ID):new android.app.Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(intent.getStringExtra("title")).setContentText(intent.getStringExtra("body")).setAutoCancel(true);
        manager.notify(intent.getIntExtra("notification_id",0),builder.build());
    }
}
