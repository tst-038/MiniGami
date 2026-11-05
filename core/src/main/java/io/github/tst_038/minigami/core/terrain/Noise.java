package io.github.tst_038.minigami.core.terrain;

import java.util.SplittableRandom;

/**
 * Smooth pseudo-random noise (improved Perlin) that is the same for the same seed. Values lie in
 * roughly -1 to 1; nearby points give nearby values, which is what makes hills instead of static.
 */
public final class Noise {

    private final int[] permutation = new int[512];

    public Noise(long seed) {
        var order = new int[256];
        for (int i = 0; i < 256; i++) {
            order[i] = i;
        }
        var random = new SplittableRandom(seed);
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = order[i];
            order[i] = order[j];
            order[j] = swap;
        }
        for (int i = 0; i < 512; i++) {
            permutation[i] = order[i & 255];
        }
    }

    public double sample(double x, double z) {
        return sample(x, 0.5, z);
    }

    public double sample(double x, double y, double z) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        int zi = (int) Math.floor(z);
        double xf = x - xi;
        double yf = y - yi;
        double zf = z - zi;
        xi &= 255;
        yi &= 255;
        zi &= 255;
        double u = fade(xf);
        double v = fade(yf);
        double w = fade(zf);

        int a = permutation[xi] + yi;
        int aa = permutation[a] + zi;
        int ab = permutation[a + 1] + zi;
        int b = permutation[xi + 1] + yi;
        int ba = permutation[b] + zi;
        int bb = permutation[b + 1] + zi;

        return lerp(w,
                lerp(v,
                        lerp(u, gradient(permutation[aa], xf, yf, zf), gradient(permutation[ba], xf - 1, yf, zf)),
                        lerp(u, gradient(permutation[ab], xf, yf - 1, zf), gradient(permutation[bb], xf - 1, yf - 1, zf))),
                lerp(v,
                        lerp(u, gradient(permutation[aa + 1], xf, yf, zf - 1), gradient(permutation[ba + 1], xf - 1, yf, zf - 1)),
                        lerp(u, gradient(permutation[ab + 1], xf, yf - 1, zf - 1), gradient(permutation[bb + 1], xf - 1, yf - 1, zf - 1))));
    }

    /**
     * Several layers of noise, each finer and fainter than the last, added up and scaled back into
     * -1 to 1. The first layer gives the broad shape, the later ones the roughness.
     */
    public double fractal(double x, double z, int octaves) {
        double total = 0;
        double amplitude = 1;
        double frequency = 1;
        double norm = 0;
        for (int i = 0; i < octaves; i++) {
            total += sample(x * frequency, z * frequency) * amplitude;
            norm += amplitude;
            amplitude /= 2;
            frequency *= 2;
        }
        return total / norm;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double gradient(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : h == 12 || h == 14 ? x : z;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}
