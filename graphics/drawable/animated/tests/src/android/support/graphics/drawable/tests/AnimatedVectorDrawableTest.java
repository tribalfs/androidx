/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.support.graphics.drawable.tests;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.support.annotation.UiThread;
import android.support.graphics.drawable.AnimatedVectorDrawableCompat;
import android.graphics.drawable.Drawable.ConstantState;
import android.test.ActivityInstrumentationTestCase2;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;

import android.support.graphics.drawable.animated.test.R;

import android.view.View;
import android.widget.ImageButton;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AnimatedVectorDrawableTest extends ActivityInstrumentationTestCase2<DrawableStubActivity> {
    private static final String LOGTAG = AnimatedVectorDrawableTest.class.getSimpleName();

    private static final int IMAGE_WIDTH = 64;
    private static final int IMAGE_HEIGHT = 64;

    private DrawableStubActivity mActivity;
    private Resources mResources;
    private AnimatedVectorDrawableCompat mAnimatedVectorDrawable;
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private static final boolean DBG_DUMP_PNG = false;
    private int mResId = R.drawable.animation_vector_drawable_grouping_1;

    public AnimatedVectorDrawableTest() {
        super(DrawableStubActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mBitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mBitmap);
        mActivity = getActivity();
        mResources = mActivity.getResources();

        mAnimatedVectorDrawable = AnimatedVectorDrawableCompat.create(mActivity, mResId);
    }

    // This is only for debugging or golden image (re)generation purpose.
    private void saveVectorDrawableIntoPNG(Bitmap bitmap, int resId) throws IOException {
        // Save the image to the disk.
        FileOutputStream out = null;
        try {
            String outputFolder = "/sdcard/temp/";
            File folder = new File(outputFolder);
            if (!folder.exists()) {
                folder.mkdir();
            }
            String originalFilePath = mResources.getString(resId);
            File originalFile = new File(originalFilePath);
            String fileFullName = originalFile.getName();
            String fileTitle = fileFullName.substring(0, fileFullName.lastIndexOf("."));
            String outputFilename = outputFolder + fileTitle + "_golden.png";
            File outputFile = new File(outputFilename);
            if (!outputFile.exists()) {
                outputFile.createNewFile();
            }

            out = new FileOutputStream(outputFile, false);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            Log.v(LOGTAG, "Write test No." + outputFilename + " to file successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    @UiThread
    public void testInflate() throws Exception {
        // Setup AnimatedVectorDrawableCompat from xml file
        XmlPullParser parser = mResources.getXml(mResId);
        AttributeSet attrs = Xml.asAttributeSet(parser);

        int type;
        while ((type = parser.next()) != XmlPullParser.START_TAG &&
                type != XmlPullParser.END_DOCUMENT) {
            // Empty loop
        }

        if (type != XmlPullParser.START_TAG) {
            throw new XmlPullParserException("No start tag found");
        }

        mAnimatedVectorDrawable.inflate(mResources, parser, attrs);
        mAnimatedVectorDrawable.setBounds(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
        mBitmap.eraseColor(0);
        mAnimatedVectorDrawable.draw(mCanvas);
        int sunColor = mBitmap.getPixel(IMAGE_WIDTH / 2, IMAGE_HEIGHT / 2);
        int earthColor = mBitmap.getPixel(IMAGE_WIDTH * 3 / 4 + 2, IMAGE_HEIGHT / 2);
        assertTrue(sunColor == 0xFFFF8000);
        assertTrue(earthColor == 0xFF5656EA);

        if (DBG_DUMP_PNG) {
            saveVectorDrawableIntoPNG(mBitmap, mResId);
        }
    }

    public void testGetChangingConfigurations() {
        AnimatedVectorDrawableCompat avd = AnimatedVectorDrawableCompat.create(mActivity, mResId);
        ConstantState constantState = avd.getConstantState();

        if (constantState != null) {
            // default
            assertEquals(0, constantState.getChangingConfigurations());
            assertEquals(0, avd.getChangingConfigurations());

            // change the drawable's configuration does not affect the state's configuration
            avd.setChangingConfigurations(0xff);
            assertEquals(0xff, avd.getChangingConfigurations());
            assertEquals(0, constantState.getChangingConfigurations());

            // the state's configuration get refreshed
            constantState = avd.getConstantState();
            assertEquals(0xff, constantState.getChangingConfigurations());

            // set a new configuration to drawable
            avd.setChangingConfigurations(0xff00);
            assertEquals(0xff, constantState.getChangingConfigurations());
            assertEquals(0xffff, avd.getChangingConfigurations());
        }
    }

    public void testGetConstantState() throws InterruptedException, Throwable {
        AnimatedVectorDrawableCompat avd = AnimatedVectorDrawableCompat.create(mActivity, mResId);
        ConstantState constantState = avd.getConstantState();
        if (constantState != null) {
            assertEquals(0, constantState.getChangingConfigurations());

            avd.setChangingConfigurations(1);
            constantState = avd.getConstantState();
            assertNotNull(constantState);
            assertEquals(1, constantState.getChangingConfigurations());
        }
    }

    public void testAnimateColor() throws InterruptedException, Throwable {
        final ImageButton imageButton = (ImageButton) getActivity().findViewById(R.id.imageButton);
        final int viewW = imageButton.getWidth();
        final int viewH = imageButton.getHeight();
        int pixelX = viewW / 2;
        int pixelY = viewH / 2;
        final int numTests = 5;
        final Bitmap bitmap = Bitmap.createBitmap(imageButton.getWidth(), imageButton.getHeight(),
                Bitmap.Config.ARGB_8888);
        final Canvas c = new Canvas(bitmap);
        CountDownLatch latch = new CountDownLatch(numTests);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                AnimatedVectorDrawableCompat avd = AnimatedVectorDrawableCompat.create(mActivity,
                        R.drawable.animated_color_fill);
                imageButton.setBackgroundDrawable(avd);
                avd.start();
            }
        });
        // Check the view several times during the animation to verify that it only
        // has red color in it
        for (int i = 0; i < numTests; ++i) {
            Thread.sleep(100);
            // check fill
            verifyRedOnly(pixelX, pixelY, imageButton, bitmap, c, latch);
            // check stroke
            verifyRedOnly(1, 1, imageButton, bitmap, c, latch);
        }
        latch.await(1000, TimeUnit.MILLISECONDS);
    }

    /**
     * Utility method to verify that the pixel at the given location has only red values.
     */
    private void verifyRedOnly(final int pixelX, final int pixelY, final View button,
            final Bitmap bitmap, final Canvas canvas, final CountDownLatch latch) throws Throwable {
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                button.draw(canvas);
                int pixel = bitmap.getPixel(pixelX, pixelY);
                int blue = pixel & 0xff;
                int green = pixel & 0xff00 >> 8;
                assertEquals("Blue channel not zero", 0, blue);
                assertEquals("Green channel not zero", 0, green);
                latch.countDown();
            }
        });
    }

    public void testMutate() {
        AnimatedVectorDrawableCompat d1 = AnimatedVectorDrawableCompat.create(mActivity, mResId);
        AnimatedVectorDrawableCompat d2 = AnimatedVectorDrawableCompat.create(mActivity, mResId);
        AnimatedVectorDrawableCompat d3 = AnimatedVectorDrawableCompat.create(mActivity, mResId);

        if (d1.getConstantState() != null) {
            int originalAlpha = d2.getAlpha();
            int newAlpha = (originalAlpha + 1) % 255;

            // AVD is different than VectorDrawable. Every instance of it is a deep copy
            // of the VectorDrawable.
            // So every setAlpha operation will happen only to that specific object.
            d1.setAlpha(newAlpha);
            assertEquals(newAlpha, d1.getAlpha());
            assertEquals(originalAlpha, d2.getAlpha());
            assertEquals(originalAlpha, d3.getAlpha());

            d1.mutate();
            d1.setAlpha(0x40);
            assertEquals(0x40, d1.getAlpha());
            assertEquals(originalAlpha, d2.getAlpha());
            assertEquals(originalAlpha, d3.getAlpha());

            d2.setAlpha(0x20);
            assertEquals(0x40, d1.getAlpha());
            assertEquals(0x20, d2.getAlpha());
            assertEquals(originalAlpha, d3.getAlpha());
        }
    }
}
