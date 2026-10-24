package com.lira.vmest;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.provider.Settings;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

final class GalaxyBackdropView extends View {
    private static final int STAR_COUNT = 72;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] stars = new float[STAR_COUNT * 4];
    private final RectF orbit = new RectF();
    private final ValueAnimator orbitAnimator;
    private float orbitProgress;

    GalaxyBackdropView(Context context) {
        super(context);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        Random random = new Random(73);
        for (int i = 0; i < STAR_COUNT; i++) {
            stars[i * 4] = random.nextFloat();
            stars[i * 4 + 1] = random.nextFloat();
            stars[i * 4 + 2] = 0.8f + random.nextFloat() * 2.2f;
            stars[i * 4 + 3] = random.nextFloat() * (float) (Math.PI * 2);
        }
        orbitAnimator = ValueAnimator.ofFloat(0f, 1f);
        orbitAnimator.setDuration(24_000L);
        orbitAnimator.setRepeatCount(ValueAnimator.INFINITE);
        orbitAnimator.setInterpolator(new LinearInterpolator());
        orbitAnimator.addUpdateListener(animation -> {
            orbitProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animationsEnabled()) orbitAnimator.start();
    }

    @Override protected void onDetachedFromWindow() {
        orbitAnimator.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float centerX = width * 0.5f;
        float centerY = height * 0.29f;
        float radius = Math.max(width, height) * 0.62f;

        paint.setShader(new RadialGradient(centerX, centerY, radius,
                new int[]{0x553E286B, 0x222D2452, 0x002D2452},
                new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height * 0.72f, paint);
        paint.setShader(null);

        canvas.save();
        canvas.rotate(orbitProgress * 360f, centerX, centerY);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(0x287D64B1);
        orbit.set(centerX - width * 0.43f, centerY - height * 0.14f,
                centerX + width * 0.43f, centerY + height * 0.14f);
        canvas.drawOval(orbit, paint);
        paint.setColor(0x1BBD9EE5);
        orbit.inset(dp(13), dp(6));
        canvas.drawOval(orbit, paint);
        canvas.restore();
        paint.setStyle(Paint.Style.FILL);

        for (int i = 0; i < STAR_COUNT; i++) {
            float x = stars[i * 4] * width;
            float y = stars[i * 4 + 1] * height * 0.58f;
            float pulse = 0.72f + 0.28f
                    * (float) Math.sin(orbitProgress * Math.PI * 2 + stars[i * 4 + 3]);
            int alpha = (int) (165 * pulse);
            paint.setColor((alpha << 24) | 0x00FFFFFF);
            canvas.drawCircle(x, y, dp(stars[i * 4 + 2]), paint);
        }

        paint.setColor(0xBFE8DDF7);
        canvas.drawCircle(centerX, centerY, dp(2.5f), paint);
    }

    private boolean animationsEnabled() {
        try {
            return Settings.Global.getFloat(getContext().getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        } catch (Exception ignored) {
            return true;
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
