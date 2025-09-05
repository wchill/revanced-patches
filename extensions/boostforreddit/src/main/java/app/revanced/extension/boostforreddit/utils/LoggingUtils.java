package app.revanced.extension.boostforreddit.utils;

import net.dean.jraw.http.LoggingMode;

import java.util.function.Supplier;

import app.revanced.extension.shared.Logger;

public class LoggingUtils {
    public static LoggingMode getLoggingMode() {
        return LoggingMode.ON_FAIL;
    }

    private static boolean shouldLog(boolean success) {
        LoggingMode loggingMode = getLoggingMode();
        return loggingMode == LoggingMode.ALWAYS || (!success && loggingMode == LoggingMode.ON_FAIL);
    }

    public static void logInfo(boolean success, Supplier<String> logSupplier) {
        if (shouldLog(success)) {
            Logger.printInfo(logSupplier::get);
        }
    }

    public static void logException(boolean success, Supplier<String> logSupplier) {
        if (shouldLog(success)) {
            Logger.printException(logSupplier::get);
        }
    }

    public static void logException(boolean success, Supplier<String> logSupplier, Throwable t) {
        if (shouldLog(success)) {
            Logger.printException(logSupplier::get, t);
        }
    }
}
