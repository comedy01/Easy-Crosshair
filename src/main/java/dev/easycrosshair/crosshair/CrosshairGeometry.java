package dev.easycrosshair.crosshair;

import java.util.ArrayList;
import java.util.List;

public final class CrosshairGeometry {
    private final List<int[]> rects = new ArrayList<>();
    private final int lo;
    private final int hi;
    private final double center;
    private final int length;
    private final int gap;
    private final int thickness;
    private final int grow;

    private CrosshairGeometry(double length, double gap, int thickness, int grow) {
        this.thickness = Math.max(1, thickness);
        this.length = Math.max(0, (int) Math.round(length));
        this.gap = Math.max(0, (int) Math.round(gap));
        this.grow = Math.max(0, grow);
        this.lo = -(this.thickness / 2);
        this.hi = lo + this.thickness;
        this.center = (lo + hi) / 2.0D;
    }

    public static List<int[]> build(String shape, double length, double gap, int thickness, int grow) {
        CrosshairGeometry geometry = new CrosshairGeometry(length, gap, thickness, grow);
        switch (CrosshairPolicy.normalizeShape(shape)) {
            case CrosshairPolicy.SHAPE_CROSS_DOT -> {
                geometry.arms(true);
                geometry.dot();
            }
            case CrosshairPolicy.SHAPE_DOT -> geometry.dot();
            case CrosshairPolicy.SHAPE_CIRCLE -> geometry.ring();
            case CrosshairPolicy.SHAPE_CIRCLE_DOT -> {
                geometry.ring();
                geometry.dot();
            }
            case CrosshairPolicy.SHAPE_T -> {
                geometry.arms(false);
                if (geometry.gap == 0) {
                    geometry.dot();
                }
            }
            case CrosshairPolicy.SHAPE_X -> geometry.diagonals();
            case CrosshairPolicy.SHAPE_SQUARE -> geometry.square();
            default -> {
                geometry.arms(true);
                if (geometry.gap == 0) {
                    geometry.dot();
                }
            }
        }
        return geometry.rects;
    }

    public static int reach(String shape, double length, double gap, int thickness) {
        int extent = 0;
        for (int[] rect : build(shape, length, gap, thickness, 0)) {
            for (int value : rect) {
                extent = Math.max(extent, Math.abs(value));
            }
        }
        return extent;
    }

    private void add(int x0, int y0, int x1, int y1) {
        if (x1 > x0 && y1 > y0) {
            rects.add(new int[] {x0 - grow, y0 - grow, x1 + grow, y1 + grow});
        }
    }

    private void dot() {
        add(lo, lo, hi, hi);
    }

    private void arms(boolean top) {
        if (length == 0) {
            return;
        }
        add(hi + gap, lo, hi + gap + length, hi);
        add(lo - gap - length, lo, lo - gap, hi);
        add(lo, hi + gap, hi, hi + gap + length);
        if (top) {
            add(lo, lo - gap - length, hi, lo - gap);
        }
    }

    private void ring() {
        double outer = gap + length + thickness / 2.0D + grow;
        double inner = Math.max(0.0D, outer - thickness - 2.0D * grow);
        int rows = (int) Math.ceil(outer) + 1;
        for (int y = -rows; y <= rows; y++) {
            double dy = y + 0.5D - center;
            if (Math.abs(dy) >= outer) {
                continue;
            }
            double xo = Math.sqrt(outer * outer - dy * dy);
            int left = (int) Math.round(center - xo);
            int right = (int) Math.round(center + xo);
            if (Math.abs(dy) < inner) {
                double xi = Math.sqrt(inner * inner - dy * dy);
                int innerLeft = (int) Math.round(center - xi);
                int innerRight = (int) Math.round(center + xi);
                rects.add(new int[] {left, y, Math.max(left + 1, innerLeft), y + 1});
                rects.add(new int[] {Math.min(right - 1, innerRight), y, right, y + 1});
            } else {
                rects.add(new int[] {left, y, right, y + 1});
            }
        }
    }

    private void diagonals() {
        if (length == 0) {
            return;
        }
        double half = thickness / 2.0D + grow;
        double start = gap - grow;
        double end = gap + length + grow;
        int rows = (int) Math.ceil(end) + 1;
        for (int y = -rows; y <= rows; y++) {
            double dy = y + 0.5D - center;
            double distance = Math.abs(dy);
            if (distance < start || distance >= end) {
                continue;
            }
            for (int side = -1; side <= 1; side += 2) {
                double x = center + side * distance;
                rects.add(new int[] {(int) Math.round(x - half), y, (int) Math.round(x + half), y + 1});
            }
        }
    }

    private void square() {
        int outer = gap + length + (thickness + 1) / 2;
        int x0 = (int) Math.round(center - outer) - grow;
        int x1 = (int) Math.round(center + outer) + grow;
        int band = thickness + 2 * grow;
        rects.add(new int[] {x0, x0, x1, x0 + band});
        rects.add(new int[] {x0, x1 - band, x1, x1});
        rects.add(new int[] {x0, x0 + band, x0 + band, x1 - band});
        rects.add(new int[] {x1 - band, x0 + band, x1, x1 - band});
    }
}
