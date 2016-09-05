package com.eaglesakura.android.firebase.database;

import android.content.Context;

/**
 * ダミーデータの配信を行う
 */
public interface FirebaseMockDataProvider {

    /**
     * Mockデータを取得する
     */
    <T> T getData(FirebaseData<T> data, String path);

    /**
     * 参照先のContextを取得する
     */
    Context getContext();
}
