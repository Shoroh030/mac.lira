package com.lira.vmest;

import android.Manifest;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public final class ScannerDependencySmokeTest {
    @Test
    public void scannerCanLoadAndroidXPermissionHelper() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ClassLoader loader = context.getClassLoader();
        Class<?> contextCompat = loader.loadClass("androidx.core.content.ContextCompat");
        Class<?> captureManager = loader.loadClass("com.journeyapps.barcodescanner.CaptureManager");
        assertNotNull(contextCompat.getMethod("checkSelfPermission", Context.class, String.class));
        assertNotNull(captureManager);
        contextCompat.getMethod("checkSelfPermission", Context.class, String.class)
                .invoke(null, context, Manifest.permission.CAMERA);
    }
}
