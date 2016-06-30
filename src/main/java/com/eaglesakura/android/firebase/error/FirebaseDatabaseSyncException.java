package com.eaglesakura.android.firebase.error;

import com.google.firebase.database.DatabaseError;

import android.support.annotation.NonNull;

public class FirebaseDatabaseSyncException extends FirebaseDatabaseException {
    @NonNull
    private final DatabaseError mDatabaseError;

    public FirebaseDatabaseSyncException(@NonNull DatabaseError databaseError) {
        mDatabaseError = databaseError;
    }

    public FirebaseDatabaseSyncException(String message, @NonNull DatabaseError databaseError) {
        super(message);
        mDatabaseError = databaseError;
    }

    public FirebaseDatabaseSyncException(String message, Throwable cause, @NonNull DatabaseError databaseError) {
        super(message, cause);
        mDatabaseError = databaseError;
    }

    public FirebaseDatabaseSyncException(Throwable cause, @NonNull DatabaseError databaseError) {
        super(cause);
        mDatabaseError = databaseError;
    }

    @NonNull
    public DatabaseError getDatabaseError() {
        return mDatabaseError;
    }
}
