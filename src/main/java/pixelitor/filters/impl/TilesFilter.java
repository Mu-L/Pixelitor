/*
 * Copyright 2026 Laszlo Balazs-Csiki and Contributors
 *
 * This file is part of Pixelitor. Pixelitor is free software: you
 * can redistribute it and/or modify it under the terms of the GNU
 * General Public License, version 3 as published by the Free
 * Software Foundation.
 *
 * Pixelitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Pixelitor. If not, see <http://www.gnu.org/licenses/>.
 */

package pixelitor.filters.impl;

import pixelitor.filters.GlassTiles;

import static net.jafama.FastMath.tan;

/**
 * The implementation of the {@link GlassTiles} filter.
 */
public class TilesFilter extends RotatingEffectFilter {
    public static final int MODE_SQUARES = 0;
    public static final int MODE_BRICK = 1;
    public static final int MODE_TRIANGLES = 2;
    public static final int MODE_HEXAGONS = 3;
    public static final int MODE_OCTAGONS_AND_SQUARES = 4;
    public static final int MODE_FISH_SCALES = 5;

    private static final double HALF_PI = Math.PI / 2.0;
    private static final double QUARTER_PI = Math.PI / 4.0;
    private static final double SQRT3_OVER_2 = Math.sqrt(3.0) / 2.0;
    private static final double INV_SQRT2 = 1.0 / Math.sqrt(2.0);
    private static final double OCTAGON_CORNER_THRESHOLD = Math.PI * INV_SQRT2;
    private static final double SQUARE_SCALE = 1.0 + INV_SQRT2;

    private final int mode;
    private final double freqX;
    private final double freqY;
    private final double curvatureX;
    private final double curvatureY;
    private final double curvatureAvg;
    private final double phaseAngleX;
    private final double phaseAngleY;

    /**
     * Constructs a new TilesFilter.
     *
     * @param filterName    the name of the filter.
     * @param edgeAction    the edge handling strategy (TRANSPARENT, REPEAT_EDGE, WRAP_AROUND, REFLECT).
     * @param interpolation the interpolation method (NEAREST_NEIGHBOR, BILINEAR, BICUBIC).
     * @param angle         the rotation angle of the tiles (in radians).
     * @param mode          the layout mode ({@link #MODE_SQUARES}, {@link #MODE_BRICK}, {@link #MODE_TRIANGLES},
     *                      {@link #MODE_HEXAGONS}, {@link #MODE_OCTAGONS_AND_SQUARES}, or {@link #MODE_FISH_SCALES}).
     * @param sizeX         the horizontal size of the tiles.
     * @param shiftX        the horizontal phase shift/movement of the tiles.
     * @param sizeY         the vertical size of the tiles.
     * @param shiftY        the vertical phase shift/movement of the tiles.
     * @param curvatureXVal the horizontal curvature intensity.
     * @param curvatureYVal the vertical curvature intensity.
     */
    public TilesFilter(String filterName, int edgeAction, int interpolation, double angle, int mode,
                       double sizeX, double shiftX,
                       double sizeY, double shiftY,
                       double curvatureXVal, double curvatureYVal) {
        super(filterName, edgeAction, interpolation, angle);

        assert sizeX > 0 && sizeY > 0;
        assert mode == MODE_SQUARES || mode == MODE_BRICK || mode == MODE_TRIANGLES
            || mode == MODE_HEXAGONS || mode == MODE_OCTAGONS_AND_SQUARES || mode == MODE_FISH_SCALES;

        this.mode = mode;

        // the triangles/hexagons should be equilateral/regular when sizeX = sizeY
        double baseFreqX = switch (mode) {
            case MODE_TRIANGLES -> (Math.PI * SQRT3_OVER_2) / sizeX;
            case MODE_HEXAGONS -> (Math.PI / SQRT3_OVER_2) / sizeX;
            default -> Math.PI / sizeX;
        };

        // for some reason the effect looks nice only
        // with the reduced double => float precision
        this.freqX = (float) baseFreqX;
        this.freqY = (float) (Math.PI / sizeY);

        this.phaseAngleX = shiftX / this.freqX;
        this.phaseAngleY = shiftY / this.freqY;

        this.curvatureX = (curvatureXVal * curvatureXVal) / 10.0;
        this.curvatureY = (curvatureYVal * curvatureYVal) / 10.0;
        this.curvatureAvg = 0.5 * (this.curvatureX + this.curvatureY);
    }

    @Override
    protected void coreTransformInverse(double x, double y, double[] out) {
        double tanYArg = y * freqY - phaseAngleY;
        double tanXArg = x * freqX - phaseAngleX;

        switch (mode) {
            case MODE_SQUARES -> {
                out[0] = x + curvatureX * tan(tanXArg);
                out[1] = y + curvatureY * tan(tanYArg);
            }
            case MODE_BRICK -> brick(x, y, out, tanYArg, tanXArg);
            case MODE_TRIANGLES -> triangles(x, y, out, tanYArg, tanXArg);
            case MODE_HEXAGONS -> hexagons(x, y, out, tanXArg, tanYArg);
            case MODE_OCTAGONS_AND_SQUARES -> octagons(x, y, out, tanXArg, tanYArg);
            case MODE_FISH_SCALES -> fishScales(x, y, out, tanYArg, tanXArg);
            default -> throw new IllegalStateException("Unexpected mode: " + mode);
        }
    }

    private void brick(double x, double y, double[] out, double tanYArg, double tanXArg) {
        int row = (int) Math.floor(tanYArg / Math.PI + 0.5);
        if ((row & 1) != 0) {
            tanXArg += HALF_PI;
        }
        out[0] = x + curvatureX * tan(tanXArg);
        out[1] = y + curvatureY * tan(tanYArg);
    }

    private void triangles(double x, double y, double[] out, double tanYArg, double tanXArg) {
        double halfY = 0.5 * tanYArg;
        double t1 = tan(tanYArg);
        double t2 = tan(tanXArg - halfY - QUARTER_PI);
        double t3 = tan(tanXArg + halfY + QUARTER_PI);

        double dx = SQRT3_OVER_2 * (t2 + t3);
        double dy = t1 + 0.5 * (t3 - t2);

        out[0] = x + curvatureX * dx;
        out[1] = y + curvatureY * dy;
    }

    private void hexagons(double x, double y, double[] out, double tanXArg, double tanYArg) {
        int col = (int) Math.floor(tanXArg / Math.PI + 0.5);
        double dX = tanXArg - col * Math.PI;

        double shiftedY = ((col & 1) != 0) ? tanYArg - HALF_PI : tanYArg;
        int row = (int) Math.floor(shiftedY / Math.PI + 0.5);
        double dY = shiftedY - row * Math.PI;

        // check if the point falls in a slanted corner belonging to an adjacent column
        if (Math.abs(dY) + 1.5 * Math.abs(dX) > Math.PI) {
            if (dX > 0) {
                dX -= Math.PI;
                dY += (dY > 0) ? -HALF_PI : HALF_PI;
            } else {
                dX += Math.PI;
                dY += (dY > 0) ? -HALF_PI : HALF_PI;
            }
        }

        // normal distances to the 3 pairs of opposite faces
        double u1 = dY;
        double u2 = 0.5 * (dY - 1.5 * dX);
        double u3 = 0.5 * (dY + 1.5 * dX);

        double t1 = tan(u1);
        double t2 = tan(u2);
        double t3 = tan(u3);

        double dx = SQRT3_OVER_2 * (t3 - t2);
        double dy = t1 + 0.5 * (t2 + t3);

        out[0] = x + curvatureX * dx;
        out[1] = y + curvatureY * dy;
    }

    private void octagons(double x, double y, double[] out, double tanXArg, double tanYArg) {
        int col = (int) Math.floor(tanXArg / Math.PI + 0.5);
        double dX = tanXArg - col * Math.PI;

        int row = (int) Math.floor(tanYArg / Math.PI + 0.5);
        double dY = tanYArg - row * Math.PI;

        if (Math.abs(dX) + Math.abs(dY) > OCTAGON_CORNER_THRESHOLD) {
            // corner accent square (cabochon)
            double cX = (dX >= 0) ? HALF_PI : -HALF_PI;
            double cY = (dY >= 0) ? HALF_PI : -HALF_PI;

            double qX = dX - cX;
            double qY = dY - cY;

            double v1 = Math.clamp((qX + qY) * SQUARE_SCALE, -HALF_PI, HALF_PI);
            double v2 = Math.clamp((qX - qY) * SQUARE_SCALE, -HALF_PI, HALF_PI);

            double t1 = tan(v1);
            double t2 = tan(v2);

            double dx = INV_SQRT2 * (t1 + t2);
            double dy = INV_SQRT2 * (t1 - t2);

            out[0] = x + curvatureAvg * dx;
            out[1] = y + curvatureAvg * dy;
        } else {
            // octagon
            double u1 = Math.clamp(dX, -HALF_PI, HALF_PI);
            double u2 = Math.clamp(dY, -HALF_PI, HALF_PI);
            double u3 = Math.clamp(INV_SQRT2 * (dX + dY), -HALF_PI, HALF_PI);
            double u4 = Math.clamp(INV_SQRT2 * (dX - dY), -HALF_PI, HALF_PI);

            double t1 = tan(u1);
            double t2 = tan(u2);
            double t3 = tan(u3);
            double t4 = tan(u4);

            double dxDiag = INV_SQRT2 * (t3 + t4);
            double dyDiag = INV_SQRT2 * (t3 - t4);

            out[0] = x + curvatureX * t1 + curvatureAvg * dxDiag;
            out[1] = y + curvatureY * t2 + curvatureAvg * dyDiag;
        }
    }

    private void fishScales(double x, double y, double[] out, double tanYArg, double tanXArg) {
        // find candidate row r such that tanYArg is in [r * PI, (r + 1) * PI)
        int r = (int) Math.floor(tanYArg / Math.PI);
        double rOffset = ((r & 1) != 0) ? HALF_PI : 0.0;
        double uShiftedR = tanXArg - rOffset;
        double colR = Math.floor(uShiftedR / Math.PI + 0.5);
        double duR = uShiftedR - colR * Math.PI;
        double nxR = Math.clamp(duR / HALF_PI, -1.0, 1.0);

        // bottom boundary curve of row r: an ellipse reaching depth PI at the tile's center
        double vBound = r * Math.PI + Math.PI * Math.sqrt(Math.max(0.0, 1.0 - nxR * nxR));

        int row;
        double nx;
        double ny;

        if (tanYArg < vBound) {
            // point is above the row's bottom boundary arc: belongs to row r
            row = r;
            nx = nxR;
            double dv = tanYArg - row * Math.PI;
            ny = Math.clamp(dv / Math.PI, 0.0, 1.0);
        } else {
            // point is below the arc: belongs to the staggered tile in row r + 1
            row = r + 1;
            double offset = ((row & 1) != 0) ? HALF_PI : 0.0;
            double uShifted = tanXArg - offset;
            double col = Math.floor(uShifted / Math.PI + 0.5);
            double du = uShifted - col * Math.PI;
            nx = Math.clamp(du / HALF_PI, -1.0, 1.0);
            double dv = tanYArg - row * Math.PI;
            ny = Math.clamp(dv / Math.PI, -1.0, 0.0);
        }

        // in normalized tile coordinates (nx, ny) in [-1, 1] x [-1, 1],
        // the tile is bounded by the bottom convex arc and the top concave arcs
        double rLen = Math.sqrt(nx * nx + ny * ny);
        double dnorm;
        if (ny >= 0.0) {
            // lower half: distance to the elliptical arc normal is simply rLen
            dnorm = rLen;
        } else {
            // upper half: exact closed-form radial distance to the overlapping arcs of the row above
            double absNx = Math.abs(nx);
            dnorm = (absNx - ny) + Math.sqrt(Math.max(0.0, -2.0 * absNx * ny));
        }

        double dx = 0.0;
        double dy = 0.0;
        if (rLen > 1e-9) {
            double angle = Math.min(dnorm, 0.995) * HALF_PI;
            double mag = tan(angle);
            double invR = 1.0 / rLen;
            dx = (nx * invR) * mag;
            dy = (ny * invR) * mag;
        }

        out[0] = x + curvatureX * dx;
        out[1] = y + curvatureY * dy;
    }
}
