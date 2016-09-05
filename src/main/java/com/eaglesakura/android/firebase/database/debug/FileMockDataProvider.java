package com.eaglesakura.android.firebase.database.debug;

import com.eaglesakura.android.firebase.database.FirebaseData;
import com.eaglesakura.android.firebase.database.FirebaseMockDataProvider;
import com.eaglesakura.json.JSON;

import android.annotation.SuppressLint;
import android.content.Context;

import java.io.InputStream;

/**
 * 開発用のJSONファイルからモックの生成を行わせる
 */
public abstract class FileMockDataProvider implements FirebaseMockDataProvider {
    Context mContext;

    public FileMockDataProvider(Context context) {
        mContext = context;
    }

    protected abstract InputStream open(String path) throws Throwable;

    @SuppressLint("all")
    @Override
    public <T> T getData(FirebaseData<T> data, String path) {
        try (InputStream is = open(path)) {
            T model = JSON.decode(is, data.getValueClass());
            return model;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Context getContext() {
        return mContext;
    }
}
