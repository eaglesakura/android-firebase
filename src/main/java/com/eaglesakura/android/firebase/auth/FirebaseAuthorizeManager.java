package com.eaglesakura.android.firebase.auth;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthProvider;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthCredential;
import com.google.firebase.auth.GoogleAuthProvider;

import com.eaglesakura.android.firebase.error.FirebaseAuthFailedException;
import com.eaglesakura.android.gms.util.PlayServiceUtil;
import com.eaglesakura.android.rx.error.TaskCanceledException;
import com.eaglesakura.lambda.CallbackUtils;
import com.eaglesakura.lambda.CancelCallback;
import com.eaglesakura.thread.Holder;
import com.eaglesakura.util.Util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 *
 */
public class FirebaseAuthorizeManager {
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();

    private final Object lock = new Object();

    private FirebaseAuthorizeManager() {

    }

    @Nullable
    public FirebaseUser getCurrentUser() {
        return mAuth.getCurrentUser();
    }

    /**
     * ログイン完了を待つ
     */
    @NonNull
    public FirebaseUser await(CancelCallback cancelCallback) throws TaskCanceledException {
        FirebaseUser item;
        while ((item = getCurrentUser()) == null) {
            if (CallbackUtils.isCanceled(cancelCallback)) {
                throw new TaskCanceledException();
            }
            Util.sleep(1);
        }

        return item;
    }

    /**
     * Google Accountで認証を行う
     *
     * @param account        ログインされたアカウント情報
     * @param cancelCallback コールバック
     */
    public FirebaseAuthorizeManager signIn(@NonNull GoogleSignInAccount account, @NonNull CancelCallback cancelCallback) throws TaskCanceledException, FirebaseAuthFailedException {
        return signIn(
                GoogleAuthProvider.getCredential(account.getIdToken(), null),
                cancelCallback
        );
    }

    /**
     * サインインを行い、結果を得る
     *
     * signIn後はgetCurrentUserが行える。
     */
    public FirebaseAuthorizeManager signIn(@NonNull AuthCredential credential, @NonNull CancelCallback cancelCallback) throws TaskCanceledException, FirebaseAuthFailedException {
        synchronized (lock) {
            Task<AuthResult> task = PlayServiceUtil.await(mAuth.signInWithCredential(credential), cancelCallback);
            if (!task.isSuccessful()) {
                throw new FirebaseAuthFailedException();
            }
            return this;
        }
    }

    /**
     * サインアウトを完了させる
     */
    public FirebaseAuthorizeManager signOut(CancelCallback cancelCallback) throws TaskCanceledException {
        synchronized (lock) {
            if (getCurrentUser() == null) {
                return this;
            }

            Holder<Boolean> holder = new Holder<>();
            holder.set(Boolean.FALSE);
            FirebaseAuth.AuthStateListener listener = newAuth -> {
                holder.set(Boolean.TRUE);
            };
            try {
                mAuth.addAuthStateListener(listener);
                mAuth.signOut();

                while (!holder.get()) {
                    if (CallbackUtils.isCanceled(cancelCallback)) {
                        throw new TaskCanceledException();
                    }
                    Util.sleep(1);
                }

                return this;
            } finally {
                mAuth.removeAuthStateListener(listener);
            }
        }
    }


    private static FirebaseAuthorizeManager sInstance;

    public synchronized static FirebaseAuthorizeManager getInstance() {
        if (sInstance == null) {
            sInstance = new FirebaseAuthorizeManager();
        }
        return sInstance;
    }
}
