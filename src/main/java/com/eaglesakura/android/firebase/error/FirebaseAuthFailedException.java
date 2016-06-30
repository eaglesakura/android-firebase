package com.eaglesakura.android.firebase.error;

/**
 * Firebaseの認証に失敗した
 */
public class FirebaseAuthFailedException extends Exception {
    public FirebaseAuthFailedException() {
    }

    public FirebaseAuthFailedException(String message) {
        super(message);
    }

    public FirebaseAuthFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public FirebaseAuthFailedException(Throwable cause) {
        super(cause);
    }
}
