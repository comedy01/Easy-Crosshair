package dev.easycrosshair.crosshair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CrosshairPolicyTest {
    @Test
    void shapesCycleThroughEveryShape() {
        String shape = CrosshairPolicy.SHAPE_CROSS;
        for (int i = 0; i < CrosshairPolicy.SHAPES.size(); i++) {
            assertEquals(CrosshairPolicy.SHAPES.get(i), shape);
            shape = CrosshairPolicy.nextShape(shape);
        }
        assertEquals(CrosshairPolicy.SHAPE_CROSS, shape);
        assertEquals(CrosshairPolicy.SHAPE_CROSS_DOT, CrosshairPolicy.nextShape("nonsense"));
    }

    @Test
    void indicatorsCycle() {
        assertEquals(CrosshairPolicy.INDICATOR_FADE, CrosshairPolicy.nextIndicator(CrosshairPolicy.INDICATOR_BAR));
        assertEquals(CrosshairPolicy.INDICATOR_VANILLA, CrosshairPolicy.nextIndicator(CrosshairPolicy.INDICATOR_FADE));
        assertEquals(CrosshairPolicy.INDICATOR_BAR, CrosshairPolicy.nextIndicator(CrosshairPolicy.INDICATOR_VANILLA));
    }

    @Test
    void pixelsFollowGuiScale() {
        assertEquals(0, CrosshairPolicy.pixels(0, 3.0));
        assertEquals(1, CrosshairPolicy.pixels(1, 1.0));
        assertEquals(1, CrosshairPolicy.pixels(1, 2.0));
        assertEquals(2, CrosshairPolicy.pixels(1, 3.0));
        assertEquals(6, CrosshairPolicy.pixels(3, 4.0));
        assertEquals(1, CrosshairPolicy.pixels(0.1, 2.0));
        assertEquals(0, CrosshairPolicy.pixels(Double.NaN, 2.0));
    }

    @Test
    void rainbowCyclesAndKeepsAlpha() {
        assertEquals(0x80FF0000, CrosshairPolicy.rainbow(0.0, 0x80));
        assertEquals(0xFF00FFFF, CrosshairPolicy.rainbow(2.0, 0xFF));
        assertEquals(CrosshairPolicy.rainbow(1.0, 0xFF), CrosshairPolicy.rainbow(5.0, 0xFF));
    }

    @Test
    void alphaHelpers() {
        assertEquals(0x80FFFFFF, CrosshairPolicy.withAlpha(0xFFFFFFFF, 128 / 255.0F));
        assertEquals(0x00123456, CrosshairPolicy.withAlpha(0xFF123456, -1.0F));
        assertEquals(0x7F000000, CrosshairPolicy.outlineColor(0x7FABCDEF));
        assertEquals(CrosshairPolicy.FADE_FLOOR, CrosshairPolicy.fade(0.0F));
        assertEquals(1.0F, CrosshairPolicy.fade(1.0F));
    }

    @Test
    void spreadOpensWithMovement() {
        assertEquals(0.0, CrosshairPolicy.spread(0.0, true, false));
        assertTrue(CrosshairPolicy.spread(0.1, true, false) > 0.0);
        assertTrue(CrosshairPolicy.spread(0.1, true, true) > CrosshairPolicy.spread(0.1, true, false));
        assertTrue(CrosshairPolicy.spread(0.0, false, false) > 0.0);
        assertEquals(CrosshairPolicy.MAX_SPREAD, CrosshairPolicy.spread(10.0, false, true));
    }

    @Test
    void approachMovesTowardTarget() {
        assertEquals(0.0, CrosshairPolicy.approach(0.0, 5.0, 0.0));
        double partway = CrosshairPolicy.approach(0.0, 5.0, 0.05);
        assertTrue(partway > 0.0 && partway < 5.0);
        assertEquals(5.0, CrosshairPolicy.approach(0.0, 5.0, 10.0), 1.0E-6);
    }

    @Test
    void pulseRisesAndSettles() {
        assertEquals(0.0, CrosshairPolicy.pulse(-1.0));
        assertEquals(0.0, CrosshairPolicy.pulse(CrosshairPolicy.PULSE_SECONDS));
        assertEquals(CrosshairPolicy.PULSE_GROWTH, CrosshairPolicy.pulse(CrosshairPolicy.PULSE_SECONDS / 2.0), 1.0E-9);
    }

    @Test
    void drawFocusReachesFullAfterOneSecond() {
        assertEquals(0.0, CrosshairPolicy.drawFocus(0));
        assertEquals(0.5, CrosshairPolicy.drawFocus(10));
        assertEquals(1.0, CrosshairPolicy.drawFocus(40));
    }
}
