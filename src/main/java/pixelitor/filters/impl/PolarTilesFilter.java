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

import com.jhlabs.math.Noise;
import net.jafama.FastMath;
import pixelitor.filters.PolarTiles;

import java.awt.geom.Point2D;

/**
 * The implementation of the {@link PolarTiles} filter.
 */
public class PolarTilesFilter extends CenteredTransformFilter {
    public static final int MODE_CONCENTRIC = 0;
    public static final int MODE_SPIRAL = 1;
    public static final int MODE_VORTEX = 2;

    private final double effectRotation;
    private final float randomness;
    private final double imageRotation;

    // precomputed factors for inner-loop performance
    private final double invZoom;
    private final double halfAngularDivisions;
    private final double angularCurvatureFactor;
    private final double radialCurvatureFactor;
    private final double radialAngleDivisions;
    private final double baseSpiralAngle;
    private final double spiralAngleFactor;
    private final boolean hasAngularDistortion;
    private final boolean hasRadialDistortion;

    /**
     * Constructs a new PolarTilesFilter.
     *
     * @param filterName     the name of the filter.
     * @param edgeAction     the edge handling strategy (TRANSPARENT, REPEAT_EDGE, WRAP_AROUND, REFLECT).
     * @param interpolation  the interpolation method (NEAREST_NEIGHBOR, BILINEAR, BICUBIC).
     * @param center         the effect's center (in pixels).
     * @param mode           the tile mode (CONCENTRIC, SPIRAL, or VORTEX).
     * @param angularDivisions  the number of angular divisions.
     * @param radialDivisions  the number of radial divisions.
     * @param curvature      the curvature factor of the glass tiles.
     * @param effectRotation the rotation applied to the tile effect (0.0 to 1.0).
     * @param randomness     the amount of random noise displacement (0.0 to 1.0).
     * @param zoom           the image zoom percentage.
     * @param imageRotation  the rotation of the underlying image in radians.
     */
    public PolarTilesFilter(String filterName, int edgeAction, int interpolation, Point2D center,
                            int mode, int angularDivisions, int radialDivisions, double curvature,
                            double effectRotation, double randomness, double zoom, double imageRotation) {
        super(filterName, edgeAction, interpolation, center);

        assert zoom > 0.0;
        assert angularDivisions >= 0 && radialDivisions >= 0;

        // tan(x) has a period of π => the [0.0, 1.0] parameter
        // span covers a complete repeating cycle of the effect
        this.effectRotation = Math.PI * effectRotation;

        this.randomness = (float) (randomness * Math.PI);
        this.imageRotation = imageRotation;

        this.invZoom = 1.0 / zoom;
        double scaledCurvature = (curvature * curvature) * 0.1;
        this.angularCurvatureFactor = scaledCurvature * ((double) angularDivisions / 4);
        this.radialCurvatureFactor = scaledCurvature * ((double) radialDivisions / 2);
        this.halfAngularDivisions = angularDivisions * 0.5;
        this.radialAngleDivisions = Math.TAU * radialDivisions;
        this.baseSpiralAngle = Math.PI + this.effectRotation;
        this.hasAngularDistortion = angularDivisions > 0 && angularCurvatureFactor != 0.0;
        this.hasRadialDistortion = radialDivisions > 0 && radialCurvatureFactor != 0.0;

        this.spiralAngleFactor = switch (mode) {
            case MODE_SPIRAL -> (radialDivisions > 0) ? 0.5 : 0.0;
            case MODE_VORTEX -> 0.5 * radialDivisions;
            case MODE_CONCENTRIC -> 0.0;
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        };
    }

    @Override
    protected void transformInverse(int x, int y, float[] out) {
        double dx = x - cx;
        double dy = y - cy;
        double r2 = dx * dx + dy * dy;

        if (r2 == 0.0) {
            // (cx, cy) is the invariant center of scaling and rotation;
            // returning early also avoids division by zero
            out[0] = (float) cx;
            out[1] = (float) cy;
            return;
        }

        double radius = Math.sqrt(r2);
        double angle = FastMath.atan2(dy, dx);

        float noiseOffset = 0;
        if (randomness > 0) {
            noiseOffset = randomness * Noise.noise2((float) (dx / width), (float) (dy / height));
        }

        double distortedAngle = angle;
        if (hasAngularDistortion) {
            double angularTan = FastMath.tan(noiseOffset + effectRotation + angle * halfAngularDivisions);
            double angularShift = (angularTan * angularCurvatureFactor) / radius;
            distortedAngle += angularShift;
        }

        if (hasRadialDistortion) {
            double radialArg = (radius / width) * radialAngleDivisions;
            if (spiralAngleFactor != 0.0) {
                radialArg += (baseSpiralAngle + angle) * spiralAngleFactor;
            }
            double radialTan = FastMath.tan(3 * noiseOffset + radialArg);
            double radialShift = radialTan * radialCurvatureFactor;
            radius += radialShift;
        }

        distortedAngle += imageRotation;

        double zoomedR = radius * invZoom;
        double u = zoomedR * FastMath.cos(distortedAngle);
        double v = zoomedR * FastMath.sin(distortedAngle);

        out[0] = (float) (u + cx);
        out[1] = (float) (v + cy);
    }
}
