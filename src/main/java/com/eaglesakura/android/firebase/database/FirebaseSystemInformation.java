package com.eaglesakura.android.firebase.database;

import android.support.annotation.Keep;

/**
 * Firebaseのシステムデータを管理する
 */
public class FirebaseSystemInformation extends FirebaseData<FirebaseSystemInformation.FbInformation> {
    FirebaseSystemInformation() {
        super(FbInformation.class);
    }

    /**
     * オンラインであることが保証できる場合にtrue
     */
    public boolean isOnline() {
        if (getValue() == null) {
            return false;
        } else {
            return getValue().connected;
        }
    }

    /**
     * サーバー時刻を取得する
     */
    public Long getServerTimeStamp() {
        if (getValue() == null) {
            return null;
        } else {
            return System.currentTimeMillis() + getValue().serverTimeOffset;
        }
    }

    private static FirebaseSystemInformation sInstance;

    public synchronized static FirebaseSystemInformation getInstance() {
        if (sInstance == null) {
            sInstance = (FirebaseSystemInformation) new FirebaseSystemInformation().connect(".info");
        }
        return sInstance;
    }

    public static class FbInformation {
        @Keep
        public boolean authenticated;
        @Keep
        public boolean connected;
        @Keep
        public long serverTimeOffset;
    }
}
