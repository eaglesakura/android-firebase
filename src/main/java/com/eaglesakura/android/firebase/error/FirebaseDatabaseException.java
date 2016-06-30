package com.eaglesakura.android.firebase.error;

public class FirebaseDatabaseException extends Exception {
    public FirebaseDatabaseException() {
    }

    public FirebaseDatabaseException(String message) {
        super(message);
    }

    public FirebaseDatabaseException(String message, Throwable cause) {
        super(message, cause);
    }

    public FirebaseDatabaseException(Throwable cause) {
        super(cause);
    }
}
