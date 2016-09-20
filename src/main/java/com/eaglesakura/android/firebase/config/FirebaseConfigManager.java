package com.eaglesakura.android.firebase.config;

import com.google.android.gms.tasks.Task;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import com.eaglesakura.android.gms.util.PlayServiceUtil;
import com.eaglesakura.android.rx.error.TaskCanceledException;
import com.eaglesakura.android.thread.ui.UIHandler;
import com.eaglesakura.android.util.AndroidThreadUtil;
import com.eaglesakura.android.util.ContextUtil;
import com.eaglesakura.lambda.CallbackUtils;
import com.eaglesakura.lambda.CancelCallback;
import com.eaglesakura.util.EnvironmentUtil;
import com.eaglesakura.util.Util;

import android.content.Context;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;

public class FirebaseConfigManager {
    final FirebaseRemoteConfig mRemoteConfig;

    /**
     * 一度でも同期を行えている場合はtrue
     */
    public static final int FETCH_STATUS_HAS_VALUES = 0x01 << 6;
    /**
     * 成功した
     */
    public static final int FETCH_STATUS_FLAG_COMPLETED = 0x01 << 1 | FETCH_STATUS_HAS_VALUES;

    /**
     * キャッシュを利用する
     */
    public static final int FETCH_STATUS_FLAG_CACHED = 0x1 << 2;

    /**
     * 失敗した
     */
    public static final int FETCH_STATUS_FLAG_FAILED = 0x01 << 3;

    /**
     * ネットワーク系が原因でFetchが失敗した
     */
    public static final int FETCH_STATUS_FLAG_NETWORK = 0x01 << 4;

    /**
     * Configの書き込みに失敗した
     */
    public static final int FETCH_STATUS_FLAG_ACTIVATE = 0x01 << 5;


    /**
     * キャッシュ有効時間
     *
     * デフォルト = 1時間
     */
    long mCacheExpireTimeMs = 1000 * 3600;

    /**
     * 発行したFetchタスク一覧
     *
     * Taskが正常終了しない場合があるため、こちらで管理する
     */
    Task<Void> mFetchTask;

    int mTaskId;

    final Object lock = new Object();

    protected FirebaseConfigManager() {
        if (EnvironmentUtil.isRunningRobolectric()) {
            // Roborectricでは実行できないので、null許容
            mRemoteConfig = null;
        } else {
            mRemoteConfig = FirebaseRemoteConfig.getInstance();
        }
    }

    /**
     * Firebaseのデバッグフラグを強制的に上書きする
     */
    public void setFirebaseDebugFlag(boolean set) {
        mRemoteConfig.setConfigSettings(
                new FirebaseRemoteConfigSettings.Builder().setDeveloperModeEnabled(set).build()
        );
    }

    /**
     * Firebaseのデバッグフラグを強制的に上書きする
     */
    public void setFirebaseDebugFlag(Context context) {
        setFirebaseDebugFlag(ContextUtil.isDebug(context));
    }

    /**
     * ミリ秒単位でキャッシュ有効を設定する
     */
    public void setCacheExpireTimeMs(long ms) {
        mCacheExpireTimeMs = ms;
    }

    /**
     * fetch済みのコンフィグを更新する
     *
     * @return 完了フラグ
     */
    public int activate() {
        // Fetchが失敗したのでキャッシュを見る
        int state = mRemoteConfig.getInfo().getLastFetchStatus();
        if (state == FirebaseRemoteConfig.LAST_FETCH_STATUS_SUCCESS) {
            return forceActivate();
        } else {
            int flags = FETCH_STATUS_FLAG_FAILED | FETCH_STATUS_FLAG_ACTIVATE;
            if (state != FirebaseRemoteConfig.LAST_FETCH_STATUS_NO_FETCH_YET) {
                // 一度は同期できているであろうと考えられる
                flags |= FETCH_STATUS_HAS_VALUES;
            }
            return flags;
        }
    }

    /**
     * fetch済みのコンフィグを強制的に更新を試みる
     *
     * @return 完了フラグ
     */
    public int forceActivate() {
        // fetchが裏で成功しているので、古い結果でactivateする
        if (mRemoteConfig.activateFetched()) {
            return FETCH_STATUS_FLAG_COMPLETED | FETCH_STATUS_FLAG_CACHED;
        } else {
            int state = mRemoteConfig.getInfo().getLastFetchStatus();
            int flags = FETCH_STATUS_FLAG_FAILED | FETCH_STATUS_FLAG_ACTIVATE;
            if (state != FirebaseRemoteConfig.LAST_FETCH_STATUS_NO_FETCH_YET) {
                // 一度は同期できているであろうと考えられる
                flags |= FETCH_STATUS_HAS_VALUES;
            }
            return flags;
        }
    }

    private int getExpireTimeSec() {
        int cacheExpireSec = (int) (mCacheExpireTimeMs / 1000);

        if (mRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()) {
            // 開発者モードの場合は実効時間を0にする
            cacheExpireSec = 0;
        }

        return cacheExpireSec;
    }

    private Task preFetchImpl() {
        synchronized (lock) {
            if (mFetchTask != null) {
                return mFetchTask;
            }

            final int TASK_ID = (++mTaskId);

            mFetchTask = mRemoteConfig.fetch(getExpireTimeSec());
            mFetchTask.addOnCompleteListener(task -> {
                synchronized (lock) {
                    // 新しいタスクが発行されていなければ、Fetchを無効化する
                    if (TASK_ID == mTaskId) {
                        mFetchTask = null;
                    }
                }
            });


            return mFetchTask;
        }
    }

    /**
     * 事前Fetchを行う
     */
    @UiThread
    public void preFetch() {
        AndroidThreadUtil.assertUIThread();
        preFetchImpl();
    }

    /**
     * 既に始まっているFetchタスクをキャンセルする
     */
    public void clearFetchTask() {
        synchronized (lock) {
            ++mTaskId;
            mFetchTask = null;
        }
    }

    /**
     * 初回同期以外に確実性を求めないfetchを行う。
     *
     * ver9.2.1時点のライブラリではfetchの結果が返ってこないことがあるため、fetchできるタイミングでabortする。
     *
     * @param cancelCallback     このタスク自体のキャンセルチェック
     * @param fetchAbortCallback trueを返却した場合、fetchを諦めてローカルステートを見る
     * @return 完了フラグ
     */
    @WorkerThread
    public int safeFetch(CancelCallback cancelCallback, CancelCallback fetchAbortCallback) throws TaskCanceledException {
        try {
            clearFetchTask();
            Task<Void> task = UIHandler.await(() -> preFetchImpl());
            while (true) {
                if (task.isComplete()) {
                    if (task.isSuccessful()) {
                        // fetch成功した
                        if (mRemoteConfig.activateFetched() || (mRemoteConfig.getInfo().getLastFetchStatus() != FirebaseRemoteConfig.LAST_FETCH_STATUS_NO_FETCH_YET)) {
                            return FETCH_STATUS_FLAG_COMPLETED;
                        } else {
                            return FETCH_STATUS_FLAG_FAILED | FETCH_STATUS_FLAG_ACTIVATE;
                        }
                    } else {
                        // 裏のステータスを見る
                        return forceActivate();
                    }
                }

                if (CallbackUtils.isCanceled(fetchAbortCallback)) {
                    // fetchを諦めてステートを見る
                    return activate();
                }

                if (CallbackUtils.isCanceled(cancelCallback)) {
                    // タスクキャンセル命令が出た
                    throw new TaskCanceledException();
                }

                Util.sleep(1);
            }
        } finally {
            clearFetchTask();
        }
    }

    /**
     * コンフィグを同期する。
     *
     * @param activate       fetchしたデータをactivate刷る場合はtrue
     * @param cancelCallback キャンセルチェック
     * @return 完了フラグ
     */
    @WorkerThread
    public int fetch(boolean activate, CancelCallback cancelCallback) throws TaskCanceledException {
        AndroidThreadUtil.assertBackgroundThread();

        Task<Void> task = UIHandler.await(() -> preFetchImpl());

        PlayServiceUtil.await(task, cancelCallback);
        if (!task.isSuccessful()) {
            // fetch自体に失敗した
            return FETCH_STATUS_FLAG_FAILED | FETCH_STATUS_FLAG_NETWORK;
        }

        if (activate) {
            if (!mRemoteConfig.activateFetched()) {
                return FETCH_STATUS_FLAG_FAILED | FETCH_STATUS_FLAG_ACTIVATE;
            }
        }

        return FETCH_STATUS_FLAG_COMPLETED;
    }


    private static FirebaseConfigManager sInstance;

    public static FirebaseConfigManager getInstance() {
        if (sInstance == null) {
            synchronized (FirebaseConfigManager.class) {
                if (sInstance == null) {
                    sInstance = new FirebaseConfigManager();
                }
            }
        }
        return sInstance;
    }
}
