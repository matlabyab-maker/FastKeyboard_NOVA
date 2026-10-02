package com.fastkeyboard.nova;

import android.graphics.Color;

/**
 * Fast Keyboard Nova adaptive mouse cursor color helper.
 * Chooses a high-contrast cursor color from the sampled background.
 * The caller can render the cursor with a contrasting outline.
 */
public final class AdaptiveMouseCursor {
    private AdaptiveMouseCursor() {}

    public static int chooseCursorColor(int backgroundColor) {
        double lum = relativeLuminance(backgroundColor);
        return lum > 0.50 ? Color.BLACK : Color.WHITE;
    }

    public static int chooseOutlineColor(int cursorColor) {
        return cursorColor == Color.WHITE ? Color.BLACK : Color.WHITE;
    }

    public static boolean needsColorChange(int cursorColor, int backgroundColor) {
        double a = relativeLuminance(cursorColor);
        double b = relativeLuminance(backgroundColor);
        return Math.abs(a - b) < 0.35;
    }

    private static double relativeLuminance(int c) {
        double r = linear(Color.red(c) / 255.0);
        double g = linear(Color.green(c) / 255.0);
        double b = linear(Color.blue(c) / 255.0);
        return 0.2126*r + 0.7152*g + 0.0722*b;
    }

    private static double linear(double x) {
        return x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
    }
}
