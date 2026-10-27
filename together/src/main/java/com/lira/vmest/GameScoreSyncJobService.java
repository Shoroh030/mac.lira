package com.lira.vmest;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class GameScoreSyncJobService extends JobService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile Future<?> currentTask;

    @Override
    public boolean onStartJob(JobParameters params) {
        currentTask = executor.submit(() -> {
            boolean success = false;
            try {
                if (new PairGameSync(this).ready()) {
                    new GameScoreSync(this).refresh();
                    success = true;
                }
            } catch (Exception e) {
                Log.w("GameScoreSync", "Background score sync failed: "
                        + e.getClass().getSimpleName());
            }
            jobFinished(params, !success && params.getJobId() == GameScoreSyncScheduler.RETRY_JOB_ID);
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Future<?> task = currentTask;
        if (task != null) task.cancel(true);
        return true;
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
