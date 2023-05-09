/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.wear.protolayout.material;

import static androidx.annotation.Dimension.DP;
import static androidx.wear.protolayout.DimensionBuilders.dp;
import static androidx.wear.protolayout.LayoutElementBuilders.CONTENT_SCALE_MODE_FILL_BOUNDS;
import static androidx.wear.protolayout.material.ButtonDefaults.DEFAULT_SIZE;
import static androidx.wear.protolayout.material.ButtonDefaults.EXTRA_LARGE_SIZE;
import static androidx.wear.protolayout.material.ButtonDefaults.LARGE_SIZE;
import static androidx.wear.protolayout.material.ButtonDefaults.PRIMARY_COLORS;
import static androidx.wear.protolayout.material.Helper.checkNotNull;
import static androidx.wear.protolayout.material.Helper.checkTag;
import static androidx.wear.protolayout.material.Helper.getMetadataTagName;
import static androidx.wear.protolayout.material.Helper.getTagBytes;
import static androidx.wear.protolayout.material.Helper.radiusOf;

import android.content.Context;

import androidx.annotation.Dimension;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.wear.protolayout.ColorBuilders.ColorProp;
import androidx.wear.protolayout.DimensionBuilders.ContainerDimension;
import androidx.wear.protolayout.DimensionBuilders.DpProp;
import androidx.wear.protolayout.LayoutElementBuilders.Box;
import androidx.wear.protolayout.LayoutElementBuilders.ColorFilter;
import androidx.wear.protolayout.LayoutElementBuilders.Image;
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement;
import androidx.wear.protolayout.ModifiersBuilders.Background;
import androidx.wear.protolayout.ModifiersBuilders.Clickable;
import androidx.wear.protolayout.ModifiersBuilders.Corner;
import androidx.wear.protolayout.ModifiersBuilders.ElementMetadata;
import androidx.wear.protolayout.ModifiersBuilders.Modifiers;
import androidx.wear.protolayout.ModifiersBuilders.Semantics;
import androidx.wear.protolayout.TypeBuilders.StringProp;
import androidx.wear.protolayout.expression.Fingerprint;
import androidx.wear.protolayout.material.Typography.TypographyName;
import androidx.wear.protolayout.proto.LayoutElementProto;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Map;

/**
 * ProtoLayout component {@link Button} that represents clickable button with the given content.
 *
 * <p>The Button is circular in shape. The recommended sizes are defined in {@link ButtonDefaults}.
 *
 * <p>The recommended set of {@link ButtonColors} styles can be obtained from {@link
 * ButtonDefaults}., e.g. {@link ButtonDefaults#PRIMARY_COLORS} to get a color scheme for a primary
 * {@link Button}.
 *
 * <p>When accessing the contents of a container for testing, note that this element can't be simply
 * casted back to the original type, i.e.:
 *
 * <pre>{@code
 * Button button = new Button...
 * Box box = new Box.Builder().addContent(button).build();
 *
 * Button myButton = (Button) box.getContents().get(0);
 * }</pre>
 *
 * will fail.
 *
 * <p>To be able to get {@link Button} object from any layout element, {@link #fromLayoutElement}
 * method should be used, i.e.:
 *
 * <pre>{@code
 * Button myButton = Button.fromLayoutElement(box.getContents().get(0));
 * }</pre>
 */
public class Button implements LayoutElement {
    /** Tool tag for Metadata in Modifiers, so we know that Box is actually a Button with text. */
    static final String METADATA_TAG_TEXT = "TXTBTN";
    /** Tool tag for Metadata in Modifiers, so we know that Box is actually a Button with icon. */
    static final String METADATA_TAG_ICON = "ICNBTN";
    /** Tool tag for Metadata in Modifiers, so we know that Box is actually a Button with image. */
    static final String METADATA_TAG_IMAGE = "IMGBTN";
    /**
     * Tool tag for Metadata in Modifiers, so we know that Box is actually a Button with custom
     * content.
     */
    static final String METADATA_TAG_CUSTOM_CONTENT = "CSTBTN";

    @NonNull private final Box mElement;

    Button(@NonNull Box element) {
        mElement = element;
    }

    /** Builder class for {@link Button}. */
    public static final class Builder implements LayoutElement.Builder {
        private static final int NOT_SET = -1;
        private static final int ICON = 0;
        private static final int TEXT = 1;
        private static final int IMAGE = 2;
        private static final int CUSTOM_CONTENT = 3;

        @NonNull static final Map<Integer, String> TYPE_TO_TAG = new HashMap<>();

        @RestrictTo(Scope.LIBRARY)
        @Retention(RetentionPolicy.SOURCE)
        @IntDef({NOT_SET, ICON, TEXT, IMAGE, CUSTOM_CONTENT})
        @interface ButtonType {}

        @NonNull private final Context mContext;
        @Nullable private LayoutElement mCustomContent;
        @NonNull private final Clickable mClickable;
        @Nullable private StringProp mContentDescription;
        @NonNull private DpProp mSize = DEFAULT_SIZE;
        @Nullable private String mText = null;
        @Nullable private Integer mTypographyName = null;
        @Nullable private String mIcon = null;
        @Nullable private DpProp mIconSize = null;
        @Nullable private String mImage = null;
        @NonNull private ButtonColors mButtonColors = PRIMARY_COLORS;
        @ButtonType private int mType = NOT_SET;

        static {
            TYPE_TO_TAG.put(ICON, METADATA_TAG_ICON);
            TYPE_TO_TAG.put(TEXT, METADATA_TAG_TEXT);
            TYPE_TO_TAG.put(IMAGE, METADATA_TAG_IMAGE);
            TYPE_TO_TAG.put(CUSTOM_CONTENT, METADATA_TAG_CUSTOM_CONTENT);
        }

        /**
         * Creates a builder for the {@link Button} from the given content. Custom content should be
         * later set with one of the following ({@link #setIconContent}, {@link #setTextContent},
         * {@link #setImageContent}.
         *
         * @param context The application's context.
         * @param clickable Associated {@link Clickable} for click events. When the Button is
         *     clicked it will fire the associated action.
         */
        public Builder(@NonNull Context context, @NonNull Clickable clickable) {
            mClickable = clickable;
            mContext = context;
        }

        /**
         * Sets the static content description for the {@link Button}. It is highly recommended to
         * provide this for button containing icon or image.
         */
        @NonNull
        public Builder setContentDescription(@NonNull CharSequence contentDescription) {
            mContentDescription = new StringProp.Builder(contentDescription.toString()).build();
            return this;
        }

        /**
         * Sets the content description for the {@link Button}. It is highly recommended to provide
         * this for button containing icon or image.
         *
         * <p>While this field is statically accessible from 1.0, it's only bindable since version
         * 1.2 and renderers supporting version 1.2 will use the dynamic value (if set).
         */
        @NonNull
        public Builder setContentDescription(@NonNull StringProp contentDescription) {
            this.mContentDescription = contentDescription;
            return this;
        }

        /**
         * Sets the size for the {@link Button}. Strongly recommended values are {@link
         * ButtonDefaults#DEFAULT_SIZE}, {@link ButtonDefaults#LARGE_SIZE} and {@link
         * ButtonDefaults#EXTRA_LARGE_SIZE}. If not set, {@link ButtonDefaults#DEFAULT_SIZE} will be
         * used.
         */
        @NonNull
        public Builder setSize(@NonNull DpProp size) {
            mSize = size;
            return this;
        }

        /**
         * Sets the size for the {@link Button}. Strongly recommended values are {@link
         * ButtonDefaults#DEFAULT_SIZE}, {@link ButtonDefaults#LARGE_SIZE} and {@link
         * ButtonDefaults#EXTRA_LARGE_SIZE}. If not set, {@link ButtonDefaults#DEFAULT_SIZE} will be
         * used.
         */
        @NonNull
        public Builder setSize(@Dimension(unit = DP) float size) {
            mSize = dp(size);
            return this;
        }

        /**
         * Sets the colors for the {@link Button}. If not set, {@link ButtonDefaults#PRIMARY_COLORS}
         * will be used.
         */
        @NonNull
        public Builder setButtonColors(@NonNull ButtonColors buttonColors) {
            mButtonColors = buttonColors;
            return this;
        }

        /**
         * Sets the custom content for this Button. Any previously added content will be overridden.
         */
        @NonNull
        public Builder setCustomContent(@NonNull LayoutElement content) {
            resetContent();
            this.mCustomContent = content;
            this.mType = CUSTOM_CONTENT;
            return this;
        }

        /**
         * Sets the content of this Button to be the given icon with the given size. Any previously
         * added content will be overridden. Provided icon will be tinted to the given content color
         * from {@link ButtonColors} and with the given size. This icon should be image with chosen
         * alpha channel and not an actual image.
         */
        @NonNull
        public Builder setIconContent(@NonNull String imageResourceId, @NonNull DpProp size) {
            resetContent();
            this.mIcon = imageResourceId;
            this.mType = ICON;
            this.mIconSize = size;
            return this;
        }

        /**
         * Sets the content of this Button to be the given icon with the default size that is half
         * of the set size of the button. Any previously added content will be overridden. Provided
         * icon will be tinted to the given content color from {@link ButtonColors}. This icon
         * should be image with chosen alpha channel and not an actual image.
         */
        @NonNull
        public Builder setIconContent(@NonNull String imageResourceId) {
            resetContent();
            this.mIcon = imageResourceId;
            this.mType = ICON;
            return this;
        }

        /**
         * Sets the content of this Button to be the given text with the default font for the set
         * size (for the {@link ButtonDefaults#DEFAULT_SIZE}, {@link ButtonDefaults#LARGE_SIZE} and
         * {@link ButtonDefaults#EXTRA_LARGE_SIZE} is {@link Typography#TYPOGRAPHY_TITLE2}, {@link
         * Typography#TYPOGRAPHY_TITLE1} and {@link Typography#TYPOGRAPHY_DISPLAY3} respectively).
         * Any previously added content will be overridden. Text should contain no more than 3
         * characters, otherwise it will overflow from the edges.
         */
        @NonNull
        public Builder setTextContent(@NonNull String text) {
            resetContent();
            this.mText = text;
            this.mType = TEXT;
            return this;
        }

        /**
         * Sets the content of this Button to be the given text with the given font. If you need
         * more font related customization, consider using {@link #setCustomContent} with {@link
         * Text} component. Any previously added content will be overridden. Text should contain no
         * more than 3 characters, otherwise it will overflow from the edges.
         */
        @NonNull
        public Builder setTextContent(@NonNull String text, @TypographyName int typographyName) {
            resetContent();
            this.mText = text;
            this.mTypographyName = typographyName;
            this.mType = TEXT;
            return this;
        }

        /**
         * Sets the content of this Button to be the given image, i.e. contacts photo. Any
         * previously added content will be overridden.
         */
        @NonNull
        public Builder setImageContent(@NonNull String imageResourceId) {
            resetContent();
            this.mImage = imageResourceId;
            this.mType = IMAGE;
            return this;
        }

        private void resetContent() {
            this.mText = null;
            this.mTypographyName = null;
            this.mIcon = null;
            this.mImage = null;
            this.mCustomContent = null;
            this.mIconSize = null;
        }

        /** Constructs and returns {@link Button} with the provided field and look. */
        @NonNull
        @Override
        public Button build() {
            Modifiers.Builder modifiers =
                    new Modifiers.Builder()
                            .setClickable(mClickable)
                            .setBackground(
                                    new Background.Builder()
                                            .setColor(mButtonColors.getBackgroundColor())
                                            .setCorner(
                                                    new Corner.Builder()
                                                            .setRadius(radiusOf(mSize))
                                                            .build())
                                            .build())
                            .setMetadata(
                                    new ElementMetadata.Builder()
                                            .setTagData(
                                                    getTagBytes(
                                                            checkNotNull(TYPE_TO_TAG.get(mType))))
                                            .build());
            if (mContentDescription != null) {
                modifiers.setSemantics(
                        new Semantics.Builder().setContentDescription(mContentDescription).build());
            }

            Box.Builder element =
                    new Box.Builder()
                            .setHeight(mSize)
                            .setWidth(mSize)
                            .setModifiers(modifiers.build());

            element.addContent(getCorrectContent());

            return new Button(element.build());
        }

        @NonNull
        private LayoutElement getCorrectContent() {
            LayoutElement.Builder content;
            switch (mType) {
                case ICON:
                    {
                        DpProp iconSize =
                                mIconSize != null
                                        ? mIconSize
                                        : ButtonDefaults.recommendedIconSize(mSize);
                        content =
                                new Image.Builder()
                                        .setResourceId(checkNotNull(mIcon))
                                        .setHeight(checkNotNull(iconSize))
                                        .setWidth(iconSize)
                                        .setContentScaleMode(CONTENT_SCALE_MODE_FILL_BOUNDS)
                                        .setColorFilter(
                                                new ColorFilter.Builder()
                                                        .setTint(mButtonColors.getContentColor())
                                                        .build());

                        return content.build();
                    }
                case TEXT:
                    {
                        @TypographyName
                        int typographyName =
                                mTypographyName != null
                                        ? mTypographyName
                                        : getDefaultTypographyForSize(mSize);
                        content =
                                new Text.Builder(mContext, checkNotNull(mText))
                                        .setMaxLines(1)
                                        .setTypography(typographyName)
                                        .setColor(mButtonColors.getContentColor());

                        return content.build();
                    }
                case IMAGE:
                    {
                        content =
                                new Image.Builder()
                                        .setResourceId(checkNotNull(mImage))
                                        .setHeight(mSize)
                                        .setWidth(mSize)
                                        .setContentScaleMode(CONTENT_SCALE_MODE_FILL_BOUNDS);
                        return content.build();
                    }
                case CUSTOM_CONTENT:
                    return checkNotNull(mCustomContent);
                case NOT_SET:
                    // Shouldn't happen.
                default:
                    // Shouldn't happen.
                    throw new IllegalArgumentException("Wrong Button type");
            }
        }

        private static @TypographyName int getDefaultTypographyForSize(@NonNull DpProp size) {
            if (size.getValue() == LARGE_SIZE.getValue()) {
                return Typography.TYPOGRAPHY_TITLE1;
            } else if (size.getValue() == EXTRA_LARGE_SIZE.getValue()) {
                return Typography.TYPOGRAPHY_DISPLAY3;
            } else {
                return Typography.TYPOGRAPHY_TITLE2;
            }
        }
    }

    /**
     * Returns the custom content of this Button if it has been added. Otherwise, it returns null.
     */
    @Nullable
    public LayoutElement getCustomContent() {
        if (!getMetadataTag().equals(METADATA_TAG_CUSTOM_CONTENT)) {
            return null;
        }
        return getAnyContent();
    }

    /** Returns the icon content of this Button if it has been added. Otherwise, it returns null. */
    @Nullable
    public String getIconContent() {
        Image icon = getIconContentObject();
        return icon != null ? checkNotNull(icon.getResourceId()).getValue() : null;
    }

    /**
     * Returns the image content of this Button if it has been added. Otherwise, it returns null.
     */
    @Nullable
    public String getImageContent() {
        Image image = getImageContentObject();
        return image != null ? checkNotNull(image.getResourceId()).getValue() : null;
    }

    /** Returns the text content of this Button if it has been added. Otherwise, it returns null. */
    @Nullable
    public String getTextContent() {
        Text text = getTextContentObject();
        return text != null ? text.getText().getValue() : null;
    }

    @NonNull
    private LayoutElement getAnyContent() {
        return checkNotNull(mElement.getContents().get(0));
    }

    @Nullable
    private Image getIconContentObject() {
        if (!getMetadataTag().equals(METADATA_TAG_ICON)) {
            return null;
        }
        return (Image) getAnyContent();
    }

    @Nullable
    private Text getTextContentObject() {
        if (!getMetadataTag().equals(METADATA_TAG_TEXT)) {
            return null;
        }
        return Text.fromLayoutElement(getAnyContent());
    }

    @Nullable
    private Image getImageContentObject() {
        if (!getMetadataTag().equals(METADATA_TAG_IMAGE)) {
            return null;
        }
        return (Image) getAnyContent();
    }

    /** Returns click event action associated with this Button. */
    @NonNull
    public Clickable getClickable() {
        return checkNotNull(checkNotNull(mElement.getModifiers()).getClickable());
    }

    /** Returns content description for this Button. */
    @Nullable
    public StringProp getContentDescription() {
        Semantics semantics = checkNotNull(mElement.getModifiers()).getSemantics();
        if (semantics == null) {
            return null;
        }
        return semantics.getContentDescription();
    }

    /** Returns size for this Button. */
    @NonNull
    public ContainerDimension getSize() {
        return checkNotNull(mElement.getWidth());
    }

    private ColorProp getBackgroundColor() {
        return checkNotNull(
                checkNotNull(checkNotNull(mElement.getModifiers()).getBackground()).getColor());
    }

    /** Returns button color of this Button. */
    @NonNull
    public ButtonColors getButtonColors() {
        ColorProp backgroundColor = getBackgroundColor();
        ColorProp contentColor = null;

        switch (getMetadataTag()) {
            case METADATA_TAG_TEXT:
                contentColor = checkNotNull(getTextContentObject()).getColor();
                break;
            case METADATA_TAG_ICON:
                contentColor =
                        checkNotNull(checkNotNull(getIconContentObject()).getColorFilter())
                                .getTint();
                break;
            case METADATA_TAG_IMAGE:
                contentColor =
                        checkNotNull(checkNotNull(getImageContentObject()).getColorFilter())
                                .getTint();
                break;
            case METADATA_TAG_CUSTOM_CONTENT:
                break;
        }

        if (contentColor == null) {
            contentColor = new ColorProp.Builder(0).build();
        }

        return new ButtonColors(backgroundColor, contentColor);
    }

    /** Returns metadata tag set to this Button. */
    @NonNull
    String getMetadataTag() {
        return getMetadataTagName(
                checkNotNull(checkNotNull(mElement.getModifiers()).getMetadata()));
    }

    /**
     * Returns Button object from the given LayoutElement (e.g. one retrieved from a container's
     * content with {@code container.getContents().get(index)}) if that element can be converted to
     * Button. Otherwise, it will return null.
     */
    @Nullable
    public static Button fromLayoutElement(@NonNull LayoutElement element) {
        if (element instanceof Button) {
            return (Button) element;
        }
        if (!(element instanceof Box)) {
            return null;
        }
        Box boxElement = (Box) element;
        if (!checkTag(boxElement.getModifiers(), Builder.TYPE_TO_TAG.values())) {
            return null;
        }
        // Now we are sure that this element is a Button.
        return new Button(boxElement);
    }

    @NonNull
    @Override
    @RestrictTo(Scope.LIBRARY_GROUP)
    public LayoutElementProto.LayoutElement toLayoutElementProto() {
        return checkNotNull(mElement.toLayoutElementProto());
    }

    @Nullable
    @Override
    @RestrictTo(Scope.LIBRARY_GROUP)
    public Fingerprint getFingerprint() {
        return mElement.getFingerprint();
    }
}
