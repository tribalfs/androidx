/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package android.support.v17.leanback.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.v17.leanback.R;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * A subclass of {@link BaseCardView} with an {@link ImageView} as its main
 * region. The {@link ImageCardView} is highly customizable and can be used for
 * various use-cases by adjusting the ImageViewCard's type to any combination of
 * Title, Content, Badge or ImageOnly.
 * <p>
 * <h3>Styling</h3> There are three different ways to style the ImageCardView.
 * <br>
 * No matter what way you use, all your styles applied to an ImageCardView have
 * to extend the style {@link R.style#Widget_Leanback_ImageCardViewStyle}.
 * <p>
 * <u>Example:</u><br>
 * 
 * <pre>
 * {@code <style name="CustomImageCardViewStyle" parent="Widget.Leanback.ImageCardViewStyle">
        <item name="cardBackground">#F0F</item>
        <item name="lbImageCardViewType">Title|Content</item>
        <item name="lbImageCardViewInfoAreaStyle">@style/ImageCardViewColoredInfoArea</item>
        <item name="lbImageCardViewTitleStyle">@style/ImageCardViewColoredTitle</item>
    </style>}
 * </pre>
 * <p>
 * The first possibility is to set a custom Style in the Leanback Theme's
 * attribute <code>imageCardViewStyle</code>. The style set here, is the default
 * style for all ImageCardViews. The other two possibilities allow you to style
 * a particular ImageCardView. This is usefull if you want to create multiple
 * types of cards. E.g. you might want to display a card with only a title and
 * another one with title and content. Thus you need to define two different
 * <code>ImageCardViewStyles</code> and apply them to the ImageCardViews. You
 * can do this by either using a the {@link #ImageCardView(Context, int)}
 * constructor and passing a style as second argument or by setting the style in
 * a layout.
 * <p>
 * <u>Example (using constructor):</u><br>
 * 
 * <pre>
 * {@code
 *     new ImageCardView(context, R.style.CustomImageCardViewStyle);
 * }
 * </pre>
 * 
 * <u>Example (using style attribute in a layout):</u><br>
 * 
 * <pre>
 * {@code     <android.support.v17.leanback.widget.ImageCardView
        android:id="@+id/imageCardView"
        style="@style/CustomImageCardViewStyle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content">
    </android.support.v17.leanback.widget.ImageCardView>}
 * </pre>
 * <p>
 * You can style all ImageCardView's components such as the title, content,
 * badge, infoArea and the image itself by extending the corresponding style and
 * overriding the specific attribute in your custom
 * <code>ImageCardViewStyle</code>.
 * 
 * <h3>Components</h3> The ImageCardView contains three components which can be
 * combined in any combination:
 * <ul>
 * <li>Title: The card's title</li>
 * <li>Content: A short description</li>
 * <li>Badge: An icon which can be displayed on the right or left side of the
 * card.</li>
 * </ul>
 * In order to choose the components you want to use in your ImageCardView, you
 * have to specify them in the <code>lbImageCardViewType</code> attribute of
 * your custom <code>ImageCardViewStyle</code>. You can combine the following
 * values: <code>Title, Content, IconOnRight, IconOnLeft, ImageOnly</code>.
 * <p>
 * <u>Examples:</u><br>
 * 
 * <pre>
 * {@code <style name="CustomImageCardViewStyle" parent="Widget.Leanback.ImageCardViewStyle">
        ...
        <item name="lbImageCardViewType">Title|Content|IconOnLeft</item>
        ...
    </style>}
 * </pre>
 * 
 * <pre>
 * {@code <style name="CustomImageCardViewStyle" parent="Widget.Leanback.ImageCardViewStyle">
        ...
        <item name="lbImageCardViewType">ImageOnly</item>
        ...
    </style>}
 * </pre>
 * 
 * @attr ref android.support.v17.leanback.R.styleable#LeanbackTheme_imageCardViewStyle
 * @attr ref android.support.v17.leanback.R.styleable#lbImageCardView_lbImageCardViewType
 * @attr ref android.support.v17.leanback.R.styleable#lbImageCardView_lbImageCardViewTitleStyle
 * @attr ref android.support.v17.leanback.R.styleable#lbImageCardView_lbImageCardViewContentStyle
 * @attr ref android.support.v17.leanback.R.styleable#lbImageCardView_lbImageCardViewBadgeStyle
 * @attr ref android.support.v17.leanback.R.styleable#lbImageCardView_lbImageCardViewImageStyle
 * @attr ref android.support.v17.leanback.R.styleable#lbImageCardView_lbImageCardViewInfoAreaStyle
 */
public class ImageCardView extends BaseCardView {

    public static final int CARD_TYPE_FLAG_IMAGE_ONLY = 0;
    public static final int CARD_TYPE_FLAG_TITLE = 1;
    public static final int CARD_TYPE_FLAG_CONTENT = 2;
    public static final int CARD_TYPE_FLAG_ICON_RIGHT = 4;
    public static final int CARD_TYPE_FLAG_ICON_LEFT = 8;

    private ImageView mImageView;
    private ViewGroup mInfoArea;
    private TextView mTitleView;
    private TextView mContentView;
    private ImageView mBadgeImage;
    private boolean mAttachedToWindow;

    /**
     * Create an ImageCardView using a given style for customization.
     * 
     * @param context
     *            The Context the view is running in, through which it can
     *            access the current theme, resources, etc.
     * @param styleResId
     *            The resourceId of the style you want to apply to the
     *            ImageCardView. The style has to extend
     *            {@link R.style#Widget_Leanback_ImageCardViewStyle}.
     */
    public ImageCardView(Context context, int styleResId) {
        super(new ContextThemeWrapper(context, styleResId), null, 0);
        buildImageCardView(styleResId);
    }

    /**
     * @see #View(Context, AttributeSet, int)
     */
    public ImageCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(getStyledContext(context, attrs, defStyleAttr), attrs, defStyleAttr);
        buildImageCardView(getImageCardViewStyle(context, attrs, defStyleAttr));
    }

    private void buildImageCardView(int styleResId) {
        // Make sure the ImageCardView is focusable.
        setFocusable(true);
        setFocusableInTouchMode(true);

        LayoutInflater inflater = LayoutInflater.from(getContext());
        inflater.inflate(R.layout.lb_image_card_view, this);
        TypedArray cardAttrs = getContext().obtainStyledAttributes(styleResId, R.styleable.lbImageCardView);
        int cardType = cardAttrs.getInt(R.styleable.lbImageCardView_lbImageCardViewType, CARD_TYPE_FLAG_IMAGE_ONLY);
        boolean hasImageOnly = cardType == CARD_TYPE_FLAG_IMAGE_ONLY;
        boolean hasTitle = (cardType & CARD_TYPE_FLAG_TITLE) == CARD_TYPE_FLAG_TITLE;
        boolean hasContent = (cardType & CARD_TYPE_FLAG_CONTENT) == CARD_TYPE_FLAG_CONTENT;
        boolean hasIconRight = (cardType & CARD_TYPE_FLAG_ICON_RIGHT) == CARD_TYPE_FLAG_ICON_RIGHT;
        boolean hasIconLeft = !hasIconRight && (cardType & CARD_TYPE_FLAG_ICON_LEFT) == CARD_TYPE_FLAG_ICON_LEFT;

        mImageView = (ImageView) findViewById(R.id.main_image);
        if (mImageView.getDrawable() == null) {
            mImageView.setVisibility(View.INVISIBLE);
        }

        mInfoArea = (ViewGroup) findViewById(R.id.info_field);
        if (hasImageOnly) {
            removeView(mInfoArea);
            cardAttrs.recycle();
            return;
        }
        // Create children
        if (hasTitle) {
            mTitleView = (TextView) inflater.inflate(R.layout.lb_image_card_view_themed_title, mInfoArea, false);
            mInfoArea.addView(mTitleView);
        }

        if (hasContent) {
            mContentView = (TextView) inflater.inflate(R.layout.lb_image_card_view_themed_content, mInfoArea, false);
            mInfoArea.addView(mContentView);
        }

        if (hasIconRight || hasIconLeft) {
            int layoutId = R.layout.lb_image_card_view_themed_badge_right;
            if (hasIconLeft) {
                layoutId = R.layout.lb_image_card_view_themed_badge_left;
            }
            mBadgeImage = (ImageView) inflater.inflate(layoutId, mInfoArea, false);
            mInfoArea.addView(mBadgeImage);
        }

        // Set up LayoutParams for children
        if (hasTitle && !hasContent && mBadgeImage != null) {
            RelativeLayout.LayoutParams relativeLayoutParams = (RelativeLayout.LayoutParams) mTitleView
                    .getLayoutParams();
            // Adjust title TextView if there is an icon but no content
            if (hasIconLeft) {
                relativeLayoutParams.addRule(RelativeLayout.END_OF, mBadgeImage.getId());
            } else {
                relativeLayoutParams.addRule(RelativeLayout.START_OF, mBadgeImage.getId());
            }
            mTitleView.setLayoutParams(relativeLayoutParams);
        }

        // Set up LayoutParams for children
        if (hasContent) {
            RelativeLayout.LayoutParams relativeLayoutParams = (RelativeLayout.LayoutParams) mContentView
                    .getLayoutParams();
            if (!hasTitle) {
                relativeLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            }
            // Adjust content TextView if icon is on the left
            if (hasIconLeft) {
                relativeLayoutParams.removeRule(RelativeLayout.START_OF);
                relativeLayoutParams.removeRule(RelativeLayout.ALIGN_PARENT_START);
                relativeLayoutParams.addRule(RelativeLayout.END_OF, mBadgeImage.getId());
            }
            mContentView.setLayoutParams(relativeLayoutParams);
        }

        if (mBadgeImage != null) {
            RelativeLayout.LayoutParams relativeLayoutParams = (RelativeLayout.LayoutParams) mBadgeImage
                    .getLayoutParams();
            if (hasContent) {
                relativeLayoutParams.addRule(RelativeLayout.ALIGN_BOTTOM, mContentView.getId());
            } else if (hasTitle) {
                relativeLayoutParams.addRule(RelativeLayout.ALIGN_BOTTOM, mTitleView.getId());
            }
            mBadgeImage.setLayoutParams(relativeLayoutParams);
        }

        // Backward compatibility: Newly created ImageCardViews should change
        // the InfoArea's background color in XML using the corresponding style.
        // However, since older implementations might make use of the
        // 'infoAreaBackground' attribute, we have to make sure to support it.
        // If the user has set a specific value here, it will differ from null.
        // In this case, we do want to override the value set in the style.
        Drawable background = cardAttrs.getDrawable(R.styleable.lbImageCardView_infoAreaBackground);
        if (null != background) {
            setInfoAreaBackground(background);
        }
        // Backward compatibility: There has to be an icon in the default
        // version. If there is one, we have to set it's visibility to 'GONE'.
        // Disabling 'adjustIconVisibility' allows the user to set the icon's
        // visibility state in XML rather than code.
        if (mBadgeImage != null && mBadgeImage.getDrawable() == null) {
            mBadgeImage.setVisibility(View.GONE);
        }
        cardAttrs.recycle();
    }

    private static Context getStyledContext(Context context, AttributeSet attrs, int defStyleAttr) {
        int style = getImageCardViewStyle(context, attrs, defStyleAttr);
        return new ContextThemeWrapper(context, style);
    }

    private static int getImageCardViewStyle(Context context, AttributeSet attrs, int defStyleAttr) {
        // Read style attribute defined in XML layout.
        int style = null == attrs ? 0 : attrs.getStyleAttribute();
        if (0 == style) {
            // Not found? Read global ImageCardView style from Theme attribute.
            TypedArray styledAttrs = context.obtainStyledAttributes(R.styleable.LeanbackTheme);
            style = styledAttrs.getResourceId(R.styleable.LeanbackTheme_imageCardViewStyle, 0);
            styledAttrs.recycle();
        }
        return style;
    }

    /**
     * @see #View(Context)
     */
    public ImageCardView(Context context) {
        this(context, null);
    }

    /**
     * @see #View(Context, AttributeSet)
     */
    public ImageCardView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.imageCardViewStyle);
    }

    /**
     * Returns the main image view.
     */
    public final ImageView getMainImageView() {
        return mImageView;
    }

    /**
     * Enables or disables adjustment of view bounds on the main image.
     */
    public void setMainImageAdjustViewBounds(boolean adjustViewBounds) {
        if (mImageView != null) {
            mImageView.setAdjustViewBounds(adjustViewBounds);
        }
    }

    /**
     * Sets the ScaleType of the main image.
     */
    public void setMainImageScaleType(ScaleType scaleType) {
        if (mImageView != null) {
            mImageView.setScaleType(scaleType);
        }
    }

    /**
     * Sets the image drawable with fade-in animation.
     */
    public void setMainImage(Drawable drawable) {
        setMainImage(drawable, true);
    }

    /**
     * Sets the image drawable with optional fade-in animation.
     */
    public void setMainImage(Drawable drawable, boolean fade) {
        if (mImageView == null) {
            return;
        }

        mImageView.setImageDrawable(drawable);
        if (drawable == null) {
            mImageView.animate().cancel();
            mImageView.setAlpha(1f);
            mImageView.setVisibility(View.INVISIBLE);
        } else {
            mImageView.setVisibility(View.VISIBLE);
            if (fade) {
                fadeIn();
            } else {
                mImageView.animate().cancel();
                mImageView.setAlpha(1f);
            }
        }
    }

    /**
     * Sets the layout dimensions of the ImageView.
     */
    public void setMainImageDimensions(int width, int height) {
        ViewGroup.LayoutParams lp = mImageView.getLayoutParams();
        lp.width = width;
        lp.height = height;
        mImageView.setLayoutParams(lp);
    }

    /**
     * Returns the ImageView drawable.
     */
    public Drawable getMainImage() {
        if (mImageView == null) {
            return null;
        }

        return mImageView.getDrawable();
    }

    /**
     * Returns the info area background drawable.
     */
    public Drawable getInfoAreaBackground() {
        if (mInfoArea != null) {
            return mInfoArea.getBackground();
        }
        return null;
    }

    /**
     * Sets the info area background drawable.
     */
    public void setInfoAreaBackground(Drawable drawable) {
        if (mInfoArea != null) {
            mInfoArea.setBackground(drawable);
        }
    }

    /**
     * Sets the info area background color.
     */
    public void setInfoAreaBackgroundColor(@ColorInt int color) {
        if (mInfoArea != null) {
            mInfoArea.setBackgroundColor(color);
        }
    }

    /**
     * Sets the title text.
     */
    public void setTitleText(CharSequence text) {
        if (mTitleView == null) {
            return;
        }
        mTitleView.setText(text);
    }

    /**
     * Returns the title text.
     */
    public CharSequence getTitleText() {
        if (mTitleView == null) {
            return null;
        }

        return mTitleView.getText();
    }

    /**
     * Sets the content text.
     */
    public void setContentText(CharSequence text) {
        if (mContentView == null) {
            return;
        }
        mContentView.setText(text);
    }

    /**
     * Returns the content text.
     */
    public CharSequence getContentText() {
        if (mContentView == null) {
            return null;
        }

        return mContentView.getText();
    }

    /**
     * Sets the badge image drawable.
     */
    public void setBadgeImage(Drawable drawable) {
        if (mBadgeImage == null) {
            return;
        }
        mBadgeImage.setImageDrawable(drawable);
        if (drawable != null) {
            mBadgeImage.setVisibility(View.VISIBLE);
        } else {
            mBadgeImage.setVisibility(View.GONE);
        }
    }

    /**
     * Returns the badge image drawable.
     */
    public Drawable getBadgeImage() {
        if (mBadgeImage == null) {
            return null;
        }

        return mBadgeImage.getDrawable();
    }

    private void fadeIn() {
        mImageView.setAlpha(0f);
        if (mAttachedToWindow) {
            mImageView.animate().alpha(1f)
                    .setDuration(mImageView.getResources().getInteger(android.R.integer.config_shortAnimTime));
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAttachedToWindow = true;
        if (mImageView.getAlpha() == 0) {
            fadeIn();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        mAttachedToWindow = false;
        mImageView.animate().cancel();
        mImageView.setAlpha(1f);
        super.onDetachedFromWindow();
    }

}
