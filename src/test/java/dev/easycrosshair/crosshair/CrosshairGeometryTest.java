package dev.easycrosshair.crosshair;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CrosshairGeometryTest {
    @Test
    void crossHasFourArmsAroundAHole() {
        List<int[]> rects = CrosshairGeometry.build(CrosshairPolicy.SHAPE_CROSS, 6, 3, 2, 0);
        assertEquals(4, rects.size());
        assertArrayEquals(new int[] {4, -1, 10, 1}, rects.get(0));
        assertArrayEquals(new int[] {-10, -1, -4, 1}, rects.get(1));
        assertFalse(covers(rects, 0, 0));
    }

    @Test
    void crossWithoutGapIsJoined() {
        List<int[]> rects = CrosshairGeometry.build(CrosshairPolicy.SHAPE_CROSS, 6, 0, 2, 0);
        assertTrue(covers(rects, 0, 0));
        assertTrue(covers(rects, -1, -1));
    }

    @Test
    void dotShapesFillTheCenter() {
        assertTrue(covers(CrosshairGeometry.build(CrosshairPolicy.SHAPE_DOT, 6, 3, 3, 0), 0, 0));
        assertTrue(covers(CrosshairGeometry.build(CrosshairPolicy.SHAPE_CROSS_DOT, 6, 3, 3, 0), 0, 0));
        assertTrue(covers(CrosshairGeometry.build(CrosshairPolicy.SHAPE_CIRCLE_DOT, 6, 3, 3, 0), 0, 0));
    }

    @Test
    void tShapeHasNoTopArm() {
        List<int[]> rects = CrosshairGeometry.build(CrosshairPolicy.SHAPE_T, 6, 3, 2, 0);
        assertEquals(3, rects.size());
        assertFalse(covers(rects, 0, -6));
        assertTrue(covers(rects, 0, 6));
    }

    @Test
    void circleIsHollowAndSymmetric() {
        List<int[]> rects = CrosshairGeometry.build(CrosshairPolicy.SHAPE_CIRCLE, 6, 3, 2, 0);
        assertFalse(covers(rects, 0, 0));
        assertTrue(covers(rects, 8, 0));
        assertTrue(covers(rects, -9, 0));
        assertTrue(covers(rects, 0, 8));
        assertTrue(covers(rects, 0, -9));
        assertFalse(covers(rects, 12, 0));
    }

    @Test
    void xFollowsTheDiagonals() {
        List<int[]> rects = CrosshairGeometry.build(CrosshairPolicy.SHAPE_X, 6, 3, 2, 0);
        assertTrue(covers(rects, 5, 5));
        assertTrue(covers(rects, -6, 5));
        assertTrue(covers(rects, 5, -6));
        assertFalse(covers(rects, 5, 0));
        assertFalse(covers(rects, 0, 0));
    }

    @Test
    void squareIsAnOutline() {
        List<int[]> rects = CrosshairGeometry.build(CrosshairPolicy.SHAPE_SQUARE, 6, 3, 2, 0);
        assertEquals(4, rects.size());
        assertFalse(covers(rects, 0, 0));
        assertTrue(covers(rects, 9, 0));
        assertTrue(covers(rects, 0, -10));
    }

    @Test
    void growWrapsEveryShape() {
        for (String shape : CrosshairPolicy.SHAPES) {
            List<int[]> body = CrosshairGeometry.build(shape, 6, 3, 2, 0);
            List<int[]> outline = CrosshairGeometry.build(shape, 6, 3, 2, 1);
            for (int[] r : body) {
                for (int y = r[1]; y < r[3]; y++) {
                    for (int x = r[0]; x < r[2]; x++) {
                        assertTrue(covers(outline, x, y), shape + " outline misses " + x + "," + y);
                    }
                }
            }
        }
    }

    @Test
    void reachGrowsWithSize() {
        int small = CrosshairGeometry.reach(CrosshairPolicy.SHAPE_CROSS, 4, 2, 2);
        int large = CrosshairGeometry.reach(CrosshairPolicy.SHAPE_CROSS, 10, 6, 2);
        assertEquals(7, small);
        assertTrue(large > small);
    }

    private static boolean covers(List<int[]> rects, int x, int y) {
        for (int[] r : rects) {
            if (x >= r[0] && x < r[2] && y >= r[1] && y < r[3]) {
                return true;
            }
        }
        return false;
    }
}
