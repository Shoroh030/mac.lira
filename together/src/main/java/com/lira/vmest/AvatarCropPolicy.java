package com.lira.vmest;

final class AvatarCropPolicy {
    static final class Crop {
        final int left;
        final int top;
        final int side;

        private Crop(int left, int top, int side) {
            this.left = left;
            this.top = top;
            this.side = side;
        }
    }

    private AvatarCropPolicy() { }

    static Crop centeredSquare(int width, int height) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("Image dimensions must be positive");
        int side = Math.min(width, height);
        return new Crop((width - side) / 2, (height - side) / 2, side);
    }

    static float scaleToTarget(int sourceSide, int targetSize) {
        if (sourceSide < 1 || targetSize < 1) throw new IllegalArgumentException("Image dimensions must be positive");
        return (float) targetSize / sourceSide;
    }
}
