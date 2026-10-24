package com.lira.vmest;

final class AvatarImageTransform {
    final int rotationDegrees;
    final boolean flipHorizontal;

    private AvatarImageTransform(int rotationDegrees, boolean flipHorizontal) {
        this.rotationDegrees = rotationDegrees;
        this.flipHorizontal = flipHorizontal;
    }

    static AvatarImageTransform fromExifOrientation(int orientation) {
        switch (orientation) {
            case 2: return new AvatarImageTransform(0, true);
            case 3: return new AvatarImageTransform(180, false);
            case 4: return new AvatarImageTransform(180, true);
            case 5: return new AvatarImageTransform(90, true);
            case 6: return new AvatarImageTransform(90, false);
            case 7: return new AvatarImageTransform(270, true);
            case 8: return new AvatarImageTransform(270, false);
            default: return new AvatarImageTransform(0, false);
        }
    }
}
