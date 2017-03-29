package com.eaglesakura.android.firebase;

import com.eaglesakura.log.Logger;
import com.eaglesakura.util.EnvironmentUtil;
import com.eaglesakura.util.StringUtil;

import android.util.Log;

/**
 * Log
 */
public class FbLog {
    private static final Logger.Impl sAppLogger;

    static {
        if (EnvironmentUtil.isRunningRobolectric()) {
            sAppLogger = new Logger.RobolectricLogger() {
                @Override
                protected int getStackDepth() {
                    return super.getStackDepth() + 1;
                }
            };
        } else {
            sAppLogger = new Logger.AndroidLogger(Log.class) {
                @Override
                protected int getStackDepth() {
                    return super.getStackDepth() + 1;
                }
            }.setStackInfo(BuildConfig.DEBUG);
        }
    }

    public static void config(String fmt, Object... args) {
        String tag = "Fb.Config";
        sAppLogger.out(Logger.LEVEL_DEBUG, tag, StringUtil.format(fmt, args));
    }

    public static void debug(String fmt, Object... args) {
        String tag = "Fb.Debug";
        sAppLogger.out(Logger.LEVEL_DEBUG, tag, StringUtil.format(fmt, args));
    }

}
