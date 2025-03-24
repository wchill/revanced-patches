package app.revanced.extension.boostforreddit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import app.revanced.extension.shared.Logger;

public class MarkdownRenderer {
    private static final Method markdownRenderer;
    static {
        try {
            Class<?> klass = Class.forName("com.rubenmayayo.androidsnudown.Snudown");
            markdownRenderer = klass.getDeclaredMethod("markdown", String.class);
        } catch (Exception e) {
            Logger.printException(() -> "Failed to init Snudown", e);
            throw new RuntimeException(e);
        }
    }

    public static String render(String text) {
        try {
            return markdownRenderer.invoke(null, text.replace("<", "&amp;lt;")) + "&nbsp;";
        } catch (Exception e) {
            Logger.printException(() -> "Failed to render using Snudown", e);
            throw new RuntimeException(e);
        }
    }
}
