package com.lira.vmest;

import android.app.AlertDialog;
import android.content.Context;

final class VmesteDialogTheme {
    private VmesteDialogTheme() {}

    static int dialogStyle(Context context) {
        boolean dark = context.getSharedPreferences("vmeste_data", Context.MODE_PRIVATE)
                .getBoolean("soft_dark_theme", false);
        return dark ? R.style.ThemeOverlay_Vmeste_Dialog_Dark : R.style.ThemeOverlay_Vmeste_Dialog;
    }

    static AlertDialog.Builder builder(Context context) {
        return new AlertDialog.Builder(context, dialogStyle(context));
    }
}
