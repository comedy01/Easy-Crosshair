package dev.easycrosshair.showcase;

import net.minecraft.util.Util;

// A clock that only moves when the showcase says so, one video frame at a time. The game and its built-in server
// both read time through Util, so everything stays in step however long a frame takes to capture.
final class Clock {
    private static final long FRAME = 1_000_000_000L / Recorder.FPS;
    private static volatile long nanos;
    private static boolean fixed;

    private Clock() {
    }

    static void fix() {
        if (!fixed) {
            fixed = true;
            nanos = System.nanoTime();
            Util.setTimeSource(() -> nanos);
        }
    }

    // Hands time back to the real clock. Saving and shutting down wait on it, so this has to happen before quitting.
    static void release() {
        if (fixed) {
            fixed = false;
            Util.setTimeSource(System::nanoTime);
        }
    }

    static void step() {
        if (fixed) {
            nanos += FRAME;
        }
    }

    static long nanos() {
        return fixed ? nanos : System.nanoTime();
    }
}
