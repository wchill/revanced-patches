package app.revanced.extension.boostforreddit;

import android.util.Log;

import app.revanced.extension.shared.Logger;

public class ExceptionHook {
    public static void handleException(Throwable t, String s) {
        String stackTrace = Log.getStackTraceString(t);
        Logger.printException(
                () -> String.format("Exception:\n%s\n\nContext:\n%s\n\nTraceback:\n%s", t, s, stackTrace),
                t
        );
    }
}
