/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.core.graphics.drawable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SdkSuppress;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import androidx.core.content.ContextCompat;
import androidx.core.test.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class IconCompatTest {

    private static void verifyClippedCircle(Bitmap bitmap, int fillColor, int size) {
        assertEquals(size, bitmap.getHeight());
        assertEquals(bitmap.getWidth(), bitmap.getHeight());
        assertEquals(fillColor, bitmap.getPixel(size / 2, size / 2));

        assertEquals(Color.TRANSPARENT, bitmap.getPixel(0, 0));
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(0, size - 1));
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(size - 1, 0));

        // The badge is a full rectangle located at the bottom right corner. Check a single pixel
        // in that region to verify that badging was properly applied.
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(size - 1, size - 1));
    }

    public static void verifyBadgeBitmap(Intent intent, int bgColor, int badgeColor) {
        Bitmap bitmap = intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        assertEquals(bgColor, bitmap.getPixel(2, 2));
        assertEquals(bgColor, bitmap.getPixel(w - 2, 2));
        assertEquals(bgColor, bitmap.getPixel(2, h - 2));
        assertEquals(badgeColor, bitmap.getPixel(w - 2, h - 2));
    }

    @Test
    public void testClipAdaptiveIcon() throws Throwable {
        Bitmap source = Bitmap.createBitmap(200, 150, Bitmap.Config.ARGB_8888);
        source.eraseColor(Color.RED);
        Bitmap result = IconCompat.createLegacyIconFromAdaptiveIcon(source, false);
        verifyClippedCircle(result, Color.RED, 100);
    }

    @Test
    public void testCreateWithBitmap_legacy() {
        Bitmap bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.RED);
        Intent intent = new Intent();
        IconCompat.createWithBitmap(bitmap).addToShortcutIntent(intent, null);
        assertEquals(bitmap, intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON));
    }

    @Test
    public void testAddBitmapToShortcutIntent_badged() {
        Context context = InstrumentationRegistry.getContext();
        Bitmap bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.RED);
        Intent intent = new Intent();

        Drawable badge = ContextCompat.getDrawable(context, R.drawable.test_drawable_blue);
        IconCompat.createWithBitmap(bitmap).addToShortcutIntent(intent, badge);
        assertNotSame(bitmap, intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON));

        verifyBadgeBitmap(intent, Color.RED, ContextCompat.getColor(context, R.color.test_blue));
    }

    @Test
    public void testAddResourceToShortcutIntent_badged() {
        Context context = InstrumentationRegistry.getContext();
        Intent intent = new Intent();

        // No badge
        IconCompat.createWithResource(context, R.drawable.test_drawable_green)
                .addToShortcutIntent(intent, null);
        assertNotNull(intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE));
        assertNull(intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON));

        intent = new Intent();
        Drawable badge = ContextCompat.getDrawable(context, R.drawable.test_drawable_red);
        IconCompat.createWithResource(context, R.drawable.test_drawable_blue)
                .addToShortcutIntent(intent, badge);

        assertNull(intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE));
        verifyBadgeBitmap(intent, ContextCompat.getColor(context, R.color.test_blue),
                ContextCompat.getColor(context, R.color.test_red));
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.M)
    public void testCreateWithBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.RED);
        IconCompat compat = IconCompat.createWithBitmap(bitmap);
        Drawable d = compat.toIcon().loadDrawable(InstrumentationRegistry.getContext());
        assertTrue(d instanceof BitmapDrawable);
        assertEquals(bitmap, ((BitmapDrawable) d).getBitmap());
    }

    @Test
    public void testCreateWithAdaptiveBitmap_legacy() {
        Bitmap bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.GREEN);
        Intent intent = new Intent();
        IconCompat.createWithAdaptiveBitmap(bitmap).addToShortcutIntent(intent, null);

        Bitmap clipped = intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);
        verifyClippedCircle(clipped, Color.GREEN, clipped.getWidth());
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.M)
    public void testCreateWithAdaptiveBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.GREEN);
        IconCompat compat = IconCompat.createWithAdaptiveBitmap(bitmap);
        Drawable d = compat.toIcon().loadDrawable(InstrumentationRegistry.getContext());
        if (Build.VERSION.SDK_INT >= 26) {
            assertTrue(d instanceof AdaptiveIconDrawable);
        } else {
            assertTrue(d instanceof BitmapDrawable);
            Bitmap clipped = ((BitmapDrawable) d).getBitmap();
            verifyClippedCircle(clipped, Color.GREEN, clipped.getWidth());
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.M)
    public void testCreateWithData() {
        Bitmap bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.YELLOW);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        byte[] bytes = out.toByteArray();

        byte[] resultCopy = Arrays.copyOf(bytes, bytes.length + 100);
        // Shift all elements by 20
        for (int i = bytes.length - 1; i >= 0; i--) {
            resultCopy[i + 20] = resultCopy[i];
        }

        IconCompat compat = IconCompat.createWithData(resultCopy, 20, bytes.length);
        Drawable d = compat.toIcon().loadDrawable(InstrumentationRegistry.getContext());
        assertTrue(d instanceof BitmapDrawable);
        assertTrue(bitmap.sameAs(((BitmapDrawable) d).getBitmap()));
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.M)
    public void testCreateWithResource() {
        Context context = InstrumentationRegistry.getContext();
        Drawable original = context.getDrawable(R.drawable.test_drawable_red);

        IconCompat compat = IconCompat.createWithResource(context, R.drawable.test_drawable_red);
        Drawable d = compat.toIcon().loadDrawable(InstrumentationRegistry.getContext());

        // Drawables are same classes
        assertEquals(original.getClass(), d.getClass());

        Bitmap orgBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        original.setBounds(0, 0, 200, 200);
        original.draw(new Canvas(orgBitmap));

        Bitmap compatBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, 200, 200);
        d.draw(new Canvas(compatBitmap));

        // Drawables behave the same
        assertTrue(orgBitmap.sameAs(compatBitmap));
    }
}
