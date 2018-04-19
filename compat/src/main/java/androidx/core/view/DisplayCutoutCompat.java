package androidx.core.view;

import android.content.res.Resources;
import android.graphics.*;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.DisplayCutout;
import android.view.WindowInsets;

import java.util.ArrayList;
import java.util.List;

import static android.os.Build.VERSION.SDK_INT;
import static android.util.DisplayMetrics.DENSITY_DEFAULT;
import static android.util.DisplayMetrics.DENSITY_DEVICE_STABLE;

/**
 * Represents the area of the display that is not functional for displaying content.
 *
 * <p>{@code DisplayCutoutCompat} instances are immutable.
 */
public final class DisplayCutoutCompat {

    private static final Rect ZERO_RECT = new Rect();
    private static final Region EMPTY_REGION = new Region();

    private final Object mDisplayCutout;

    /**
     * Creates a DisplayCutout instance.
     *
     * @param safeInsets the insets from each edge which avoid the display cutout as returned by
     *                   {@link #getSafeInsetTop()} etc.
     * @param boundingRects the bounding rects of the display cutouts as returned by
     *               {@link #getBoundingRects()} ()}.
     */
    // TODO(b/73953958): @VisibleForTesting(visibility = PRIVATE)
    public DisplayCutoutCompat(Rect safeInsets, List<Rect> boundingRects) {
        this(SDK_INT >= 28 ? new DisplayCutout(safeInsets, boundingRects) : null);
    }

    private DisplayCutoutCompat(Object displayCutout) {
        mDisplayCutout = displayCutout;
    }

    /** Returns the inset from the top which avoids the display cutout in pixels. */
    public int getSafeInsetTop() {
        if (SDK_INT >= 28) {
            return ((DisplayCutout) mDisplayCutout).getSafeInsetTop();
        } else {
            return 0;
        }
    }

    /** Returns the inset from the bottom which avoids the display cutout in pixels. */
    public int getSafeInsetBottom() {
        if (SDK_INT >= 28) {
            return ((DisplayCutout) mDisplayCutout).getSafeInsetBottom();
        } else {
            return 0;
        }
    }

    /** Returns the inset from the left which avoids the display cutout in pixels. */
    public int getSafeInsetLeft() {
        if (SDK_INT >= 28) {
            return ((DisplayCutout) mDisplayCutout).getSafeInsetLeft();
        } else {
            return 0;
        }
    }

    /** Returns the inset from the right which avoids the display cutout in pixels. */
    public int getSafeInsetRight() {
        if (SDK_INT >= 28) {
            return ((DisplayCutout) mDisplayCutout).getSafeInsetRight();
        } else {
            return 0;
        }
    }

    /**
     * Returns a list of {@code Rect}s, each of which is the bounding rectangle for a non-functional
     * area on the display.
     *
     * There will be at most one non-functional area per short edge of the device, and none on
     * the long edges.
     *
     * @return a list of bounding {@code Rect}s, one for each display cutout area.
     */
    public List<Rect> getBoundingRects() {
        if (SDK_INT >= 28) {
            return ((DisplayCutout) mDisplayCutout).getBoundingRects();
        } else {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DisplayCutoutCompat other = (DisplayCutoutCompat) o;
        return mDisplayCutout == null ? other.mDisplayCutout == null
                : mDisplayCutout.equals(other.mDisplayCutout);
    }

    @Override
    public int hashCode() {
        return mDisplayCutout == null ? 0 : mDisplayCutout.hashCode();
    }

    @Override
    public String toString() {
        return "DisplayCutoutCompat{" + mDisplayCutout + "}";
    }

    static DisplayCutoutCompat wrap(Object displayCutout) {
        return displayCutout == null ? null : new DisplayCutoutCompat(displayCutout);
    }
}
