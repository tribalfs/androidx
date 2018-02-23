/*
 * Copyright 2017 The Android Open Source Project
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

package androidx.app.slice.builders;

import static android.support.annotation.RestrictTo.Scope.LIBRARY;

import android.app.PendingIntent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.annotation.RestrictTo;

import java.util.function.Consumer;

import androidx.app.slice.builders.impl.TemplateBuilderImpl;


/**
 * Builder to construct a row of slice content in a grid format.
 * <p>
 * A grid row is composed of cells, each cell can have a combination of text and images. For more
 * details see {@link CellBuilder}.
 * </p>
 */
public class GridBuilder extends TemplateSliceBuilder {

    private androidx.app.slice.builders.impl.GridBuilder mImpl;
    private boolean mHasSeeMore;

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @IntDef({
            LARGE_IMAGE, SMALL_IMAGE, ICON_IMAGE
    })
    public @interface ImageMode{}

    /**
     * Indicates that an image presented in the grid is a icon and it can be tinted.
     */
    public static final int ICON_IMAGE = 0;
    /**
     * Indicates that an image presented in the grid should be displayed in a small format.
     */
    public static final int SMALL_IMAGE = 1;
    /**
     * Indicates that an image presented in the grid should be displayed in a large format.
     */
    public static final int LARGE_IMAGE = 2;

    /**
     * Create a builder which will construct a slice displayed in a grid format.
     * @param parent The builder constructing the parent slice.
     */
    public GridBuilder(@NonNull ListBuilder parent) {
        super(parent.getImpl().createGridBuilder());
    }

    @Override
    void setImpl(TemplateBuilderImpl impl) {
        mImpl = (androidx.app.slice.builders.impl.GridBuilder) impl;
    }

    /**
     * Add a cell to the grid builder.
     */
    @NonNull
    public GridBuilder addCell(@NonNull CellBuilder builder) {
        mImpl.addCell((TemplateBuilderImpl) builder.mImpl);
        return this;
    }

    /**
     * Add a cell to the grid builder.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    @NonNull
    public GridBuilder addCell(@NonNull Consumer<CellBuilder> c) {
        CellBuilder b = new CellBuilder(this);
        c.accept(b);
        return addCell(b);
    }

    /**
     * If all content in a slice cannot be shown, the cell added here may be displayed where the
     * content is cut off.
     * <p>
     * This method should only be used if you want to display a custom cell to indicate more
     * content, consider using {@link #addSeeMoreAction(PendingIntent)} otherwise. If you do
     * choose to specify a custom cell, the cell should have
     * {@link CellBuilder#setContentIntent(PendingIntent)} specified to take the user to an
     * activity to see all of the content.
     * </p>
     * <p>
     * Only one see more affordance can be added, this throws {@link IllegalStateException} if
     * a row or action has been previously added.
     * </p>
     */
    @NonNull
    public GridBuilder addSeeMoreCell(@NonNull CellBuilder builder) {
        if (mHasSeeMore) {
            throw new IllegalStateException("Trying to add see more cell when one has "
                    + "already been added");
        }
        mImpl.addSeeMoreCell((TemplateBuilderImpl) builder.mImpl);
        mHasSeeMore = true;
        return this;
    }

    /**
     * If all content in a slice cannot be shown, the cell added here may be displayed where the
     * content is cut off.
     * <p>
     * This method should only be used if you want to display a custom cell to indicate more
     * content, consider using {@link #addSeeMoreAction(PendingIntent)} otherwise. If you do
     * choose to specify a custom cell, the cell should have
     * {@link CellBuilder#setContentIntent(PendingIntent)} specified to take the user to an
     * activity to see all of the content.
     * </p>
     * <p>
     * Only one see more affordance can be added, this throws {@link IllegalStateException} if
     * a row or action has been previously added.
     * </p>
     */
    @RequiresApi(Build.VERSION_CODES.N)
    @NonNull
    public GridBuilder addSeeMoreCell(@NonNull Consumer<CellBuilder> c) {
        CellBuilder b = new CellBuilder(this);
        c.accept(b);
        return addSeeMoreCell(b);
    }

    /**
     * If all content in a slice cannot be shown, a "see more" affordance may be displayed where
     * the content is cut off. The action added here should take the user to an activity to see
     * all of the content, and will be invoked when the "see more" affordance is tapped.
     * <p>
     * Only one see more affordance can be added, this throws {@link IllegalStateException} if
     * a row or action has been previously added.
     * </p>
     */
    @NonNull
    public GridBuilder addSeeMoreAction(@NonNull PendingIntent intent) {
        if (mHasSeeMore) {
            throw new IllegalStateException("Trying to add see more action when one has "
                    + "already been added");
        }
        mImpl.addSeeMoreAction(intent);
        mHasSeeMore = true;
        return this;
    }

    /**
     * Sets the intent to send when the slice is activated.
     */
    @NonNull
    public GridBuilder setPrimaryAction(@NonNull SliceAction action) {
        mImpl.setPrimaryAction(action);
        return this;
    }

    /**
     * @hide
     */
    @RestrictTo(LIBRARY)
    public androidx.app.slice.builders.impl.GridBuilder getImpl() {
        return mImpl;
    }

    /**
     * Sub-builder to construct a cell to be displayed in a grid.
     * <p>
     * Content added to a cell will be displayed in order vertically, for example the below code
     * would construct a cell with "First text", and image below it, and then "Second text" below
     * the image.
     *
     * <pre class="prettyprint">
     * CellBuilder cb = new CellBuilder(parent, sliceUri);
     * cb.addText("First text")
     *   .addImage(middleIcon)
     *   .addText("Second text");
     * </pre>
     *
     * A cell can have at most two text items and one image.
     * </p>
     */
    public static final class CellBuilder extends TemplateSliceBuilder {
        private androidx.app.slice.builders.impl.GridBuilder.CellBuilder mImpl;

        /**
         * Create a builder which will construct a slice displayed as a cell in a grid.
         * @param parent The builder constructing the parent slice.
         */
        public CellBuilder(@NonNull GridBuilder parent) {
            super(parent.mImpl.createGridBuilder());
        }

        /**
         * Create a builder which will construct a slice displayed as a cell in a grid.
         * @param uri Uri to tag for this slice.
         */
        public CellBuilder(@NonNull GridBuilder parent, @NonNull Uri uri) {
            super(parent.mImpl.createGridBuilder(uri));
        }

        @Override
        void setImpl(TemplateBuilderImpl impl) {
            mImpl = (androidx.app.slice.builders.impl.GridBuilder.CellBuilder) impl;
        }

        /**
         * Adds text to the cell. There can be at most two text items, the first two added
         * will be used, others will be ignored.
         */
        @NonNull
        public CellBuilder addText(@NonNull CharSequence text) {
            return addText(text, false /* isLoading */);
        }

        /**
         * Adds text to the cell. There can be at most two text items, the first two added
         * will be used, others will be ignored.
         * <p>
         * Use this method to specify content that will appear in the template once it's been
         * loaded.
         * </p>
         * @param isLoading indicates whether the app is doing work to load the added content in the
         *                  background or not.
         */
        @NonNull
        public CellBuilder addText(@Nullable CharSequence text, boolean isLoading) {
            mImpl.addText(text, isLoading);
            return this;
        }

        /**
         * Adds text to the cell. Text added with this method will be styled as a title.
         * There can be at most two text items, the first two added will be used, others
         * will be ignored.
         */
        @NonNull
        public CellBuilder addTitleText(@NonNull CharSequence text) {
            return addTitleText(text, false /* isLoading */);
        }

        /**
         * Adds text to the cell. Text added with this method will be styled as a title.
         * There can be at most two text items, the first two added will be used, others
         * will be ignored.
         * <p>
         * Use this method to specify content that will appear in the template once it's been
         * loaded.
         * </p>
         * @param isLoading indicates whether the app is doing work to load the added content in the
         *                  background or not.
         */
        @NonNull
        public CellBuilder addTitleText(@Nullable CharSequence text, boolean isLoading) {
            mImpl.addTitleText(text, isLoading);
            return this;
        }

        /**
         * Adds an image to the cell that should be displayed as large as the cell allows.
         * There can be at most one image, the first one added will be used, others will be ignored.
         *
         * @param image the image to display in the cell.
         */
        @NonNull
        @Deprecated
        public CellBuilder addLargeImage(@NonNull Icon image) {
            return addImage(image, LARGE_IMAGE, false /* isLoading */);
        }

        /**
         * Adds an image to the cell that should be displayed as large as the cell allows.
         * There can be at most one image, the first one added will be used, others will be ignored.
         * <p>
         * Use this method to specify content that will appear in the template once it's been
         * loaded.
         * </p>
         * @param isLoading indicates whether the app is doing work to load the added content in the
         *                  background or not.
         */
        @NonNull
        @Deprecated
        public CellBuilder addLargeImage(@Nullable Icon image, boolean isLoading) {
            return addImage(image, LARGE_IMAGE, isLoading);
        }

        /**
         * Adds an image to the cell. There can be at most one image, the first one added
         * will be used, others will be ignored.
         *
         * @param image the image to display in the cell.
         */
        @NonNull
        @Deprecated
        public CellBuilder addImage(@NonNull Icon image) {
            return addImage(image, SMALL_IMAGE, false /* isLoading */);
        }

        /**
         * Adds an image to the cell. There can be at most one image, the first one added
         * will be used, others will be ignored.
         * <p>
         * Use this method to specify content that will appear in the template once it's been
         * loaded.
         * </p>
         * @param isLoading indicates whether the app is doing work to load the added content in the
         *                  background or not.
         */
        @NonNull
        @Deprecated
        public CellBuilder addImage(@Nullable Icon image, boolean isLoading) {
            return addImage(image, SMALL_IMAGE, isLoading);
        }

        /**
         * Adds an image to the cell. There can be at most one image, the first one added will be
         * used, others will be ignored.
         *
         * @param image the image to display in the cell.
         * @param imageMode the mode that image should be displayed in.
         *
         * @see #ICON_IMAGE
         * @see #SMALL_IMAGE
         * @see #LARGE_IMAGE
         */
        @NonNull
        public CellBuilder addImage(@NonNull Icon image, @ImageMode int imageMode) {
            return addImage(image, imageMode, false /* isLoading */);
        }

        /**
         * Adds an image to the cell. There can be at most one image, the first one added will be
         * used, others will be ignored.
         * <p>
         * Use this method to specify content that will appear in the template once it's been
         * loaded.
         * </p>
         * @param image the image to display in the cell.
         * @param imageMode the mode that image should be displayed in.
         * @param isLoading indicates whether the app is doing work to load the added content in the
         *                  background or not.
         *
         * @see #ICON_IMAGE
         * @see #SMALL_IMAGE
         * @see #LARGE_IMAGE
         */
        @NonNull
        public CellBuilder addImage(@Nullable Icon image, @ImageMode int imageMode,
                boolean isLoading) {
            mImpl.addImage(image, imageMode, isLoading);
            return this;
        }

        /**
         * Sets the action to be invoked if the user taps on this cell in the row.
         */
        @NonNull
        public CellBuilder setContentIntent(@NonNull PendingIntent intent) {
            mImpl.setContentIntent(intent);
            return this;
        }
    }
}
