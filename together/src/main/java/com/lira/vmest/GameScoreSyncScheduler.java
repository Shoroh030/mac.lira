package com.lira.vmest;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

final class GameScoreSyncScheduler {
    static final int PERIODIC_JOB_ID = 7310;
    static final int RETRY_JOB_ID = 7311;
    private static final long PERIODIC_INTERVAL_MILLIS = 15 * 60_000L;

    private GameScoreSyncScheduler() { }

    static boolean ensurePeriodic(Context context) {
        JobScheduler scheduler = scheduler(context);
        if (scheduler == null) return false;
        if (scheduler.getPendingJob(PERIODIC_JOB_ID) != null) return true;
        JobInfo job = new JobInfo.Builder(PERIODIC_JOB_ID, component(context))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(PERIODIC_INTERVAL_MILLIS)
                .build();
        return scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS;
    }

    static boolean requestSoon(Context context) {
        JobScheduler scheduler = scheduler(context);
        if (scheduler == null) return false;
        JobInfo job = new JobInfo.Builder(RETRY_JOB_ID, component(context))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(10_000L)
                .setOverrideDeadline(5 * 60_000L)
                .setBackoffCriteria(10_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build();
        return scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS;
    }

    private static JobScheduler scheduler(Context context) {
        return (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    }

    private static ComponentName component(Context context) {
        return new ComponentName(context, GameScoreSyncJobService.class);
    }
}
