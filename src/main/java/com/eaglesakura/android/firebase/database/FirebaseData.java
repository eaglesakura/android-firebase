package com.eaglesakura.android.firebase.database;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import com.eaglesakura.android.firebase.error.FirebaseDatabaseException;
import com.eaglesakura.android.firebase.error.FirebaseDatabaseSyncException;
import com.eaglesakura.android.rx.error.TaskCanceledException;
import com.eaglesakura.lambda.Action1;
import com.eaglesakura.lambda.CallbackUtils;
import com.eaglesakura.lambda.CancelCallback;

import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Firebase databaseに保持されたデータ構造
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

    @NonNull
    final FirebaseDatabase mDatabase = FirebaseDatabase.getInstance();

    DatabaseReference mReference;

    /**
     * 最後に受信したエラー
     */
    @Nullable
    DatabaseError mLastError;

    @NonNull
    private final Object lock = new Object();

    public FirebaseData(@NonNull Class<T> valueClass) {
        mValueClass = valueClass;
    }

    public FirebaseData<T> connect(String path) {
        mReference = mDatabase.getReference(path);
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

    /**
     * アイテムを取得する
     *
     * @param cancelCallback キャンセルチェック
     */
    @NonNull
    public T await(CancelCallback cancelCallback) throws TaskCanceledException {
        T item;
        while ((item = getValue()) == null) {
            // キャンセルされた
            if (CallbackUtils.isCanceled(cancelCallback)) {
                throw new TaskCanceledException();
            }
        }

        return item;
    }

    /**
     * エラーが発生するまで問い合わせを続け、アイテムを取得する
     *
     * エラーが発生した場合、ハンドリングを中止して例外を投げる
     */
    @NonNull
    public T awaitIfSuccess(CancelCallback cancelCallback) throws TaskCanceledException, FirebaseDatabaseException {
        T item = null;
        DatabaseError error = null;
        while ((item = getValue()) == null && ((error = getLastError()) == null)) {
            // キャンセルされた
            if (CallbackUtils.isCanceled(cancelCallback)) {
                throw new TaskCanceledException();
            }
        }

        // エラーが設定されている
        if (error != null) {
            throw new FirebaseDatabaseSyncException(error);
        }

        return item;

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
}
