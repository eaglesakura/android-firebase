package com.eaglesakura.android.firebase.database;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import com.eaglesakura.android.db.DBOpenType;
import com.eaglesakura.android.db.TextKeyValueStore;
import com.eaglesakura.android.error.NetworkNotConnectException;
import com.eaglesakura.android.firebase.error.FirebaseDatabaseException;
import com.eaglesakura.android.firebase.error.FirebaseDatabaseSyncException;
import com.eaglesakura.android.rx.error.TaskCanceledException;
import com.eaglesakura.android.util.AndroidNetworkUtil;
import com.eaglesakura.json.JSON;
import com.eaglesakura.lambda.Action1;
import com.eaglesakura.lambda.CallbackUtils;
import com.eaglesakura.lambda.CancelCallback;
import com.eaglesakura.util.StringUtil;
import com.eaglesakura.util.Util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Firebase databaseに保持されたデータ構造を管理する。
 *
 * 指定時点のDataを明示的にDumpする等の機能を構築する。
 */
public class FirebaseData<T> {

    /**
     * 接続用のclass
     */
    @NonNull
    final Class<T> mValueClass;

    /**
     * 値
     */
    @Nullable
    T mValue;

    /**
     * 更新カウンタ
     */
    int mSyncCount;

    DatabaseReference mReference;

    /**
     * 最後に受信したエラー
     */
    @Nullable
    DatabaseError mLastError;

    @NonNull
    private final Object lock = new Object();

    /**
     * ネットワーク状態を確認する場合はtrue
     */
    private boolean mCheckNetworkStatus = true;

    /**
     * 参照へのパス
     */
    private String mPath;

    /**
     * モックデータの配信を行う
     */
    private FirebaseMockDataProvider mMockDataProvider;

    /**
     * グローバルで利用するモックデータを設定する
     */
    private static FirebaseMockDataProvider sMockDataProvider;

    public FirebaseData(@NonNull Class<T> valueClass) {
        mValueClass = valueClass;
        mMockDataProvider = sMockDataProvider;
    }

    public FirebaseData<T> connect(String path) {
        mPath = path;

        if (mMockDataProvider != null) {
            mValue = mMockDataProvider.getData(this, path);
            return this;
        }

        mReference = FirebaseDatabase.getInstance().getReference(path);
        mReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                synchronized (lock) {
                    mValue = dataSnapshot.getValue(mValueClass);
                    ++mSyncCount;
                    mLastError = null;  // エラーは無視する
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                synchronized (lock) {
                    mLastError = databaseError;
                }
            }
        });
        return this;
    }

    /**
     * 最後のエラーを取得する
     */
    @Nullable
    public DatabaseError getLastError() {
        synchronized (lock) {
            return mLastError;
        }
    }

    /**
     * 最新の値を取得する
     */
    @Nullable
    public T getValue() {
        synchronized (lock) {
            return mValue;
        }
    }

    @NonNull
    public Class<T> getValueClass() {
        return mValueClass;
    }

    /**
     * 値の更新回数を取得する
     */
    @IntRange(from = 0)
    public int getSyncCount() {
        synchronized (lock) {
            return mSyncCount;
        }
    }

    /**
     * awaitでネットワーク状態を確認する場合はtrue
     */
    public FirebaseData<T> checkNetworkStatus(boolean checkNetworkStatus) {
        mCheckNetworkStatus = checkNetworkStatus;
        return this;
    }

    /**
     * 値が取得済みの場合のみコールバックを処理する
     *
     * @param action 処理内容
     */
    public FirebaseData<T> ifPresent(Action1<T> action) {
        T item = getValue();
        if (item != null) {
            try {
                action.action(item);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
        return this;
    }

    private void validConnectionWait(CancelCallback cancelCallback) throws TaskCanceledException, NetworkNotConnectException {
        if (mCheckNetworkStatus && (mMockDataProvider == null)) {
            AndroidNetworkUtil.assertNetworkConnected(getContext());
        }

        if (CallbackUtils.isCanceled(cancelCallback)) {
            throw new TaskCanceledException();
        }
    }

    /**
     * アイテムを取得する
     *
     * @param cancelCallback キャンセルチェック
     */
    @NonNull
    public FirebaseData<T> await(CancelCallback cancelCallback) throws TaskCanceledException, NetworkNotConnectException {
        while (getValue() == null) {
            validConnectionWait(cancelCallback);
            Util.sleep(1);
        }

        return this;
    }

    /**
     * エラーが発生するまで問い合わせを続け、アイテムを取得する
     *
     * エラーが発生した場合、ハンドリングを中止して例外を投げる
     */
    @NonNull
    public FirebaseData<T> awaitIfSuccess(CancelCallback cancelCallback) throws TaskCanceledException, FirebaseDatabaseException, NetworkNotConnectException {
        DatabaseError error = null;
        while (getValue() == null && ((error = getLastError()) == null)) {
            validConnectionWait(cancelCallback);
            Util.sleep(1);
        }

        // エラーが設定されている
        if (error != null) {
            throw new FirebaseDatabaseSyncException(error);
        }

        return this;
    }

    /**
     * インスタンスを取得する
     */
    public static <T> FirebaseData<T> newInstance(Class<T> clazz) {
        return new FirebaseData<>(clazz);
    }

    /**
     * インスタンスを取得し、パスへ接続する
     *
     * @param clazz 変換対象クラス
     * @param path  接続対象のパス
     */
    public static <T> FirebaseData<T> newInstance(Class<T> clazz, String path) {
        return new FirebaseData<>(clazz).connect(path);
    }

    /**
     * LocalDumpを行う際のKeyを指定する
     */
    String getDumpKey(@Nullable String optionalKey) {
        String key = mPath;
        if (mReference != null) {
            key += ("@" + mReference.getKey());
        }

        if (!StringUtil.isEmpty(optionalKey)) {
            return key + "@" + optionalKey;
        } else {
            return key;
        }
    }

    File getDatabasePath(Context context) {
        return context.getDatabasePath("firebase-dump.db");
    }

    /**
     * データをローカルストレージに保存する
     */
    public FirebaseData<T> dump() {
        return dump(null);
    }

    /**
     * データをローカルストレージに保存する
     *
     * 内容はJSON/Textとして保持する。
     *
     * @param optionalKey Keyに付与される文字。指定されない場合はデフォルトのKeyで保持する。
     * @return this
     */
    @SuppressLint("NewApi")
    public FirebaseData<T> dump(@Nullable String optionalKey) {
        final String key = getDumpKey(optionalKey);
        Context context = getContext();

        try (
                TextKeyValueStore kvs = new TextKeyValueStore(context, getDatabasePath(context), TextKeyValueStore.TABLE_NAME_DEFAULT)
        ) {
            kvs.open(DBOpenType.Write);
            T value = getValue();
            String text = value != null ? JSON.encodeOrNull(value) : "";
            kvs.putDirect(key, text);
        }
        return this;
    }

    /**
     * ローカルにDumpされたデータを削除する
     */
    @SuppressLint("NewApi")
    public FirebaseData<T> removeDumpValue(@Nullable String optionalKey) {
        final String key = getDumpKey(optionalKey);
        Context context = getContext();

        try (
                TextKeyValueStore kvs = new TextKeyValueStore(context, getDatabasePath(context), TextKeyValueStore.TABLE_NAME_DEFAULT)
        ) {
            kvs.open(DBOpenType.Write);
            kvs.remove(key);
        }
        return this;
    }

    /**
     * Dumpした値を復旧する
     */
    public FirebaseData<T> restore() {
        return restore(null, 0);
    }

    /**
     * Dumpした値を復旧する
     *
     * @param expireTimeMs Dumpしたデータが有効な時間（ミリ秒）, Dumpしたデータが1時間有効であれば1000*3600を指定する。期限切れの場合は削除する。0以下の場合は常に有効
     * @return this
     */
    public FirebaseData<T> restore(long expireTimeMs) {
        return restore(null, expireTimeMs);
    }

    /**
     * Dumpした値を復旧する
     *
     * @param optionalKey Keyに付与される文字。指定されない場合はデフォルトのKeyで保持する。
     * @return this
     */
    public FirebaseData<T> restore(@Nullable String optionalKey) {
        return restore(optionalKey, 0);
    }

    private Context getContext() {
        if (mMockDataProvider != null) {
            return mMockDataProvider.getContext();
        } else {
            return FirebaseApp.getInstance().getApplicationContext();
        }
    }

    /**
     * Dumpした値を復旧する。
     *
     * dumpされていない場合、もしくはdump結果が空文字である場合、valueにはnullを上書きする。
     * 既にvalueが設定されている場合、このメソッドは何もしない。
     *
     * @param optionalKey  Keyに付与される文字。指定されない場合はデフォルトのKeyで保持する。
     * @param expireTimeMs Dumpしたデータが有効な時間（ミリ秒）, Dumpしたデータが1時間有効であれば1000*3600を指定する。期限切れの場合は削除する。0以下の場合は常に有効
     * @return this
     */
    @SuppressLint("NewApi")
    public FirebaseData<T> restore(@Nullable String optionalKey, long expireTimeMs) {
        final String key = getDumpKey(optionalKey);
        Context context = getContext();

        T item = null;
        try (
                TextKeyValueStore kvs = new TextKeyValueStore(context, getDatabasePath(context), TextKeyValueStore.TABLE_NAME_DEFAULT)
        ) {
            kvs.open(expireTimeMs < 0 ? DBOpenType.Read : DBOpenType.Write);    // expireされる可能性があるならWriteモードで開く
            TextKeyValueStore.Data data = kvs.get(key);
            if (data != null) {
                if (expireTimeMs > 0) {
                    // 有効チェック
                    if (System.currentTimeMillis() > (data.date + expireTimeMs)) {
                        // データを削除する
                        kvs.remove(key);
                        data = null;
                    }
                }

                // データをデコードする
                if (data != null && !StringUtil.isEmpty(data.value)) {
                    item = JSON.decodeOrNull(data.value, mValueClass);
                }
            }
        }

        synchronized (lock) {
            // Restore前に同期が完了していたら、最新版を使うべきである。
            if (mValue != null) {
                return this;
            }

            mValue = item;
        }
        return this;
    }

    /**
     * ダミーデータを与える
     */
    public FirebaseData<T> mock(T value) {
        mLastError = null;
        mValue = value;
        return this;
    }

    /**
     * ダミーデータを与える
     */
    public FirebaseData<T> mock(InputStream json) throws IOException {
        return mock(JSON.decode(json, mValueClass));
    }

    /**
     * ダミーデータを与える
     */
    public FirebaseData<T> mock(FirebaseMockDataProvider provider) {
        mMockDataProvider = provider;
        return this;
    }

    /**
     * 全体で使用するモックを切り替える
     */
    public static void globalMock(FirebaseMockDataProvider mockDataProvider) {
        sMockDataProvider = mockDataProvider;
    }
}
