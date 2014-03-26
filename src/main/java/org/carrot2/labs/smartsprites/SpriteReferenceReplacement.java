package org.carrot2.labs.smartsprites;

/**
 * Represents the replacement that will be made for an individual
 * {@link SpriteReferenceOccurrence}.
 */
public class SpriteReferenceReplacement
{
    /** Properties of the sprite image this replacement refers to */
    public SpriteImage spriteImage;

    /** The {@link SpriteReferenceOccurrence} this instance refers to */
    public final SpriteReferenceOccurrence spriteReferenceOccurrence;

    /** String representation of the horizontal position of this sprite replacement */
    public final String horizontalPositionString;

    /** Numeric representation of the horizontal position of this sprite replacement */
    public final int horizontalPosition;

    /** String representation of the vertical position of this sprite replacement */
    public final String verticalPositionString;

    /** Numeric representation of the vertical position of this sprite replacement */
    public final int verticalPosition;

    /** Height of referencing image (not sprite!) */
    public final int imageHeightPx;

    /** Width of referencing image (not sprite!) */
    public final int imageWidthPx;

    public final boolean includeDimensions;

public SpriteReferenceReplacement(
        SpriteReferenceOccurrence spriteReferenceOccurrence, int verticalPosition,
        String horizontalPosition, int heightPx, int widthPx, SpriteImageOccurrence spriteImageOccurrence)
    {
        this.spriteReferenceOccurrence = spriteReferenceOccurrence;
        this.horizontalPosition = -1;
        this.horizontalPositionString = horizontalPosition;
        this.verticalPosition = verticalPosition;
        this.verticalPositionString = "-" + verticalPosition + "px";
        this.imageHeightPx = heightPx;
        this.imageWidthPx = widthPx;
        this.includeDimensions = spriteImageOccurrence.spriteImageDirective.includeDimensions;
    }

    public SpriteReferenceReplacement(
        SpriteReferenceOccurrence spriteReferenceOccurrence, String verticalPosition,
        int horizontalPosition, int heightPx, int widthPx, SpriteImageOccurrence spriteImageOccurrence)
    {
        this.spriteReferenceOccurrence = spriteReferenceOccurrence;
        this.horizontalPosition = horizontalPosition;
        this.horizontalPositionString = "-" + horizontalPosition + "px";
        this.verticalPosition = -1;
        this.verticalPositionString = verticalPosition;
        this.imageHeightPx = heightPx;
        this.imageWidthPx = widthPx;
        this.includeDimensions = spriteImageOccurrence.spriteImageDirective.includeDimensions;
    }
}
