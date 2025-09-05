package app.revanced.extension.boostforreddit.debug;

import android.util.Log;

import app.revanced.extension.boostforreddit.utils.LoggingUtils;
import app.revanced.extension.shared.Logger;

/**
 * @noinspection unused
 */
public class ExceptionHook {
    public static void handleException(Throwable t, String s) {
        String stackTrace = Log.getStackTraceString(t);
        LoggingUtils.logException(
                true,
                () -> String.format("Exception:\n%s\n\nContext:\n%s\n\nTraceback:\n%s", t, s, stackTrace),
                t
        );
    }
    public static void handleException(Throwable t) {
        String stackTrace = Log.getStackTraceString(t);
        LoggingUtils.logException(
                true,
                () -> String.format("Exception:\n%s\n\nTraceback:\n%s", t, stackTrace),
                t
        );
    }
}
