package com.eaglesakura.android.firebase.config;

import com.google.android.gms.tasks.Task;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import com.eaglesakura.android.playservice.util.PlayServiceUtil;
import com.eaglesakura.android.rx.error.TaskCanceledException;
import com.eaglesakura.android.util.ContextUtil;
import com.eaglesakura.lambda.CancelCallback;

import android.content.Context;

public class FirebaseConfigManager {
    final Context mContext;

    final FirebaseRemoteConfig mRemoteConfig = FirebaseRemoteConfig.getInstance();

    /**
     * 成功した
     */
    public static final int FETCH_STATUS_FLAG_COMPLETED = 0x01 << 1;

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

    public FirebaseConfigManager(Context context) {
        mContext = context.getApplicationContext();
        setFirebaseDebugFlag(ContextUtil.isDebug(mContext));
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
     * ミリ秒単位でキャッシュ有効を設定する
     */
    public void setCacheExpireTimeMs(long ms) {
        mCacheExpireTimeMs = ms;
    }


    /**
     * fetch済みのコンフィグを更新する
     */
    public boolean activate() {
        return mRemoteConfig.activateFetched();
    }

    /**
     * コンフィグを同期する。
     *
     * @param activate       fetchしたデータをactivate刷る場合はtrue
     * @param cancelCallback キャンセルチェック
     * @return 完了フラグ
     */
    public int fetch(boolean activate, CancelCallback cancelCallback) throws TaskCanceledException {
        // キャッシュ時間内であれば、何も行わない
        {
            final long EXPIRE_TIME = (mRemoteConfig.getInfo().getFetchTimeMillis() + mCacheExpireTimeMs);
            if (System.currentTimeMillis() < EXPIRE_TIME) {
                return FETCH_STATUS_FLAG_COMPLETED | FETCH_STATUS_FLAG_CACHED;
            }
        }

        long cacheExpireSec = mCacheExpireTimeMs;

        if (mRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()) {
            // 開発者モードの場合は実効時間を0にする
            cacheExpireSec = 0;
        }

        Task<Void> fetch = PlayServiceUtil.await(mRemoteConfig.fetch(cacheExpireSec), cancelCallback);
        if (!fetch.isSuccessful()) {
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
}
