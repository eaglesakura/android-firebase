package com.eaglesakura.android.firebase.config;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import com.eaglesakura.android.db.DBOpenType;
import com.eaglesakura.android.db.TextKeyValueStore;
import com.eaglesakura.android.error.NetworkNotConnectException;
import com.eaglesakura.android.firebase.FbLog;
import com.eaglesakura.android.firebase.database.FirebaseData;
import com.eaglesakura.android.rx.error.TaskCanceledException;
import com.eaglesakura.json.JSON;
import com.eaglesakura.lambda.CancelCallback;
import com.eaglesakura.util.EnvironmentUtil;
import com.eaglesakura.util.StringUtil;
import com.eaglesakura.util.Timer;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Firebase Remote ConfigとDatabase Pathを組み合わせてコンフィグを構築する
 * Configパスは1つのみ対応している。
 *
 * 1. RemoteConfigを同期
 * 2. RemoteConfig内の1コンフィグにパスを格納しておき、パスを取得
 * 3. パスを解析する
 */
public class FirebaseReferenceConfigManager<T> {
    @NonNull
    final FirebaseConfigManager mConfigManager = FirebaseConfigManager.getInstance();

    @NonNull
    final Context mContext;

    /**
     * RemoteConfigとして扱うパス名
     */
    @NonNull
    final String mConfigPathName;

    /**
     * Config用クラス
     */
    @NonNull
    final Class<T> mConfigRootModelClass;

    /**
     * スキーマバージョン
     */
    final int mSchemaVersion;

    /**
     * 現在のコンフィグ情報
     */
    @Nullable
    T mCurrentConfig;

    public FirebaseReferenceConfigManager(@NonNull Context context, int schemaVersion, Class<T> configRootModel, String configPathName) {
        mContext = context;
        mSchemaVersion = schemaVersion;
        mConfigRootModelClass = configRootModel;
        mConfigPathName = configPathName;
        if (!EnvironmentUtil.isRunningRobolectric()) {
            mConfigManager.setFirebaseDebugFlag(context);
        }

        restore();
    }

    protected void restore() {
        TextKeyValueStore kvs = new TextKeyValueStore(mContext, mContext.getDatabasePath(FirebaseData.DUMP_DATABASE_FILE_NAME), TextKeyValueStore.TABLE_NAME_DEFAULT);
        try {
            kvs.open(DBOpenType.Read);

            TextKeyValueStore.Data data = kvs.get(getDatabaseKey());
            if (data != null && !StringUtil.isEmpty(data.value)) {
                mCurrentConfig = JSON.decodeOrNull(data.value, mConfigRootModelClass);
            }
        } finally {
            kvs.close();
        }
    }

    protected void dump() {
        TextKeyValueStore kvs = new TextKeyValueStore(mContext, mContext.getDatabasePath(FirebaseData.DUMP_DATABASE_FILE_NAME), TextKeyValueStore.TABLE_NAME_DEFAULT);
        try {
            kvs.open(DBOpenType.Write);

            kvs.putDirect(getDatabaseKey(), JSON.encodeOrNull(mCurrentConfig));
        } finally {
            kvs.close();
        }
    }

    protected String getDatabaseKey() {
        return "config@" + mSchemaVersion + "@" + mConfigRootModelClass.getName();
    }

    /**
     * 現在のコンフィグ情報を取得する
     */
    @Nullable
    public T get() {
        return mCurrentConfig;
    }

    /**
     * 開発フラグを切り替える
     */
    public void setDebugFlag(boolean set) {
        mConfigManager.setFirebaseDebugFlag(set);
    }

    /**
     * DatabaseのReference pathを取得する
     */
    protected String getConfigPath() {
        FirebaseRemoteConfig rawConfig = FirebaseRemoteConfig.getInstance();
        return rawConfig.getString(mConfigPathName);
    }

    /**
     * fetch&activateを行う
     */
    public int fetch(CancelCallback cancelCallback) throws TaskCanceledException, NetworkNotConnectException {
        Timer timer = new Timer();
        try {

            // Remote Configを取得する
            int result = mConfigManager.safeFetch(cancelCallback, cancelCallback);
            if ((result & FirebaseConfigManager.FETCH_STATUS_HAS_VALUES) != 0) {
                FbLog.config("Firebase Config Sync Completed [%d ms]", timer.end());
                FbLog.config("Firebase Config Path %s", getConfigPath());

                // 新規に接続し、Database Configを取得する
                timer.start();
                FirebaseData<T> configRoot = FirebaseData.newInstance(mConfigRootModelClass, getConfigPath());
                try {
                    configRoot.await(cancelCallback);
                    // データをダンプし、最新版を保持する
                    mCurrentConfig = configRoot.getValue();
                    dump();
                } finally {
                    FbLog.config("Firebase Database Config Sync Completed [%d ms]", timer.end());
                    configRoot.disconnect();
                }
            } else {
                String msg = "";
                if ((result & FirebaseConfigManager.FETCH_STATUS_FLAG_NETWORK) != 0) {
                    msg += "FETCH_STATUS_FLAG_NETWORK,";
                }
                if ((result & FirebaseConfigManager.FETCH_STATUS_FLAG_ACTIVATE) != 0) {
                    msg += "FETCH_STATUS_FLAG_ACTIVATE,";
                }
                FbLog.config("Firebase Config Sync Failed [%d ms] flags[%x] msg[%s]", timer.end(), result, msg);
            }
            return result;
        } catch (TaskCanceledException e) {
            FbLog.config("Firebase Config Sync Abort [%.1f sec]", timer.endSec());
            throw e;
        } catch (NetworkNotConnectException e) {
            FbLog.config("Firebase Config Sync Abort [%.1f sec]", timer.endSec());
            throw e;
        }
    }
}
