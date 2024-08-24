/*
 * Copyright (C) 2014 The Android Open Source Project
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

package androidx.appcompat.widget;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.appcompat.R;
import androidx.appcompat.util.SeslMisc;
import androidx.appcompat.util.SeslShowButtonShapesHelper;
import androidx.appcompat.view.ActionBarPolicy;
import androidx.appcompat.view.menu.ActionMenuItemView;
import androidx.appcompat.view.menu.BaseMenuPresenter;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuItemImpl;
import androidx.appcompat.view.menu.MenuPopupHelper;
import androidx.appcompat.view.menu.MenuView;
import androidx.appcompat.view.menu.ShowableListMenu;
import androidx.appcompat.view.menu.SubMenuBuilder;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ActionProvider;
import androidx.core.view.GravityCompat;
import androidx.core.widget.TextViewCompat;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

/**
 * <p><b>SESL variant</b></p><br>
 *
 * MenuPresenter for building action menus as seen in the action bar and action modes.
 */
class ActionMenuPresenter extends BaseMenuPresenter
        implements ActionProvider.SubUiVisibilityListener {

    private static final String TAG = "ActionMenuPresenter";

    OverflowMenuButton mOverflowButton;
    private Drawable mPendingOverflowIcon;
    private boolean mPendingOverflowIconSet;
    private boolean mReserveOverflow;
    private boolean mReserveOverflowSet;
    private int mWidthLimit;
    private int mActionItemWidthLimit;
    private int mMaxItems;
    private boolean mMaxItemsSet;
    private boolean mStrictWidthLimit;
    private boolean mWidthLimitSet;
    private boolean mExpandedActionViewsExclusive;
    private int mMinCellSize;



    // Group IDs that have been added as actions - used temporarily, allocated here for reuse.
    private final SparseBooleanArray mActionButtonGroups = new SparseBooleanArray();

    OverflowPopup mOverflowPopup;
    ActionButtonSubmenu mActionButtonPopup;

    OpenOverflowRunnable mPostedOpenRunnable;
    private ActionMenuPopupCallback mPopupCallback;

    final PopupPresenterCallback mPopupPresenterCallback = new PopupPresenterCallback();
    int mOpenSubMenuId;

    //Sesl
    private static final int BADGE_LIMIT_NUMBER = 99;
    private CharSequence mTooltipText;
    private final boolean mUseTextItemMode;
    private NumberFormat mNumberFormat = NumberFormat.getInstance(Locale.getDefault());
    //sesl


    public ActionMenuPresenter(Context context) {
        super(context, R.layout.sesl_action_menu_layout, R.layout.sesl_action_menu_item_layout);//sesl
        mUseTextItemMode = context.getResources().getBoolean(R.bool.sesl_action_bar_text_item_mode);//sesl
    }

    @Override
    public void initForMenu(@NonNull Context context, @Nullable MenuBuilder menu) {
        super.initForMenu(context, menu);

        final Resources res = context.getResources();

        final ActionBarPolicy abp = ActionBarPolicy.get(context);
        if (!mReserveOverflowSet) {
            mReserveOverflow = abp.showsOverflowMenuButton();
        }

        if (!mWidthLimitSet) {
            mWidthLimit = abp.getEmbeddedMenuWidthLimit();
        }

        // Measure for initial configuration
        if (!mMaxItemsSet) {
            mMaxItems = abp.getMaxActionButtons();
        }

        int width = mWidthLimit;
        if (mReserveOverflow) {
            if (mOverflowButton == null) {
                mOverflowButton = new OverflowMenuButton(mSystemContext);
                mOverflowButton.setId(R.id.sesl_action_bar_overflow_button);//sesl
                if (mPendingOverflowIconSet) {
                    if (mUseTextItemMode) {//sesl
                        ((AppCompatImageView) mOverflowButton.getInnerView()).setImageDrawable(mPendingOverflowIcon);
                    }
                    mPendingOverflowIcon = null;
                    mPendingOverflowIconSet = false;
                }
                final int spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
                mOverflowButton.measure(spec, spec);
            }
            width -= mOverflowButton.getMeasuredWidth();
        } else {
            mOverflowButton = null;
        }

        mActionItemWidthLimit = width;

        mMinCellSize = (int) (ActionMenuView.MIN_CELL_SIZE * res.getDisplayMetrics().density);
    }

    public void onConfigurationChanged(Configuration newConfig) {
        //Sesl
        final ActionBarPolicy abp = ActionBarPolicy.get(mContext);
        if (!mMaxItemsSet) {
            mMaxItems = abp.getMaxActionButtons();
        }
        if (!mWidthLimitSet) {
            mWidthLimit = abp.getEmbeddedMenuWidthLimit();
        }
        if (mReserveOverflow && mOverflowButton != null) {
            mActionItemWidthLimit = mWidthLimit - mOverflowButton.getMeasuredWidth();
        } else {
            mActionItemWidthLimit = mWidthLimit;
        }
        //sesl
        if (mMenu != null) {
            mMenu.onItemsChanged(true);
        }
    }

    public void setWidthLimit(int width, boolean strict) {
        mWidthLimit = width;
        mStrictWidthLimit = strict;
        mWidthLimitSet = true;
    }

    public void setReserveOverflow(boolean reserveOverflow) {
        mReserveOverflow = reserveOverflow;
        mReserveOverflowSet = true;
    }

    public void setItemLimit(int itemCount) {
        mMaxItems = itemCount;
        mMaxItemsSet = true;
    }

    public void setExpandedActionViewsExclusive(boolean isExclusive) {
        mExpandedActionViewsExclusive = isExclusive;
    }

    public void setOverflowIcon(Drawable icon) {
        if (mUseTextItemMode) {//sesl
            return;
        }
        if (mOverflowButton != null) {
            ((AppCompatImageView) mOverflowButton.getInnerView()).setImageDrawable(icon);//sesl
        } else {
            mPendingOverflowIconSet = true;
            mPendingOverflowIcon = icon;
        }
    }

    public Drawable getOverflowIcon() {
        if (!mUseTextItemMode) {//sesl
            if (mOverflowButton != null) {
                return ((AppCompatImageView) mOverflowButton.getInnerView()).getDrawable();
            } else if (mPendingOverflowIconSet) {
                return mPendingOverflowIcon;
            }
        }
        return null;
    }

    @Override
    public MenuView getMenuView(ViewGroup root) {
        MenuView oldMenuView = mMenuView;
        MenuView result = super.getMenuView(root);
        if (oldMenuView != result) {
            ((ActionMenuView) result).setPresenter(this);
        }
        return result;
    }

    @Override
    public View getItemView(final MenuItemImpl item, View convertView, ViewGroup parent) {
        View actionView = item.getActionView();
        if (actionView == null || item.hasCollapsibleActionView()) {
            actionView = super.getItemView(item, convertView, parent);
        }
        actionView.setVisibility(item.isActionViewExpanded() ? View.GONE : View.VISIBLE);

        final ActionMenuView menuParent = (ActionMenuView) parent;
        final ViewGroup.LayoutParams lp = actionView.getLayoutParams();
        if (!menuParent.checkLayoutParams(lp)) {
            actionView.setLayoutParams(menuParent.generateLayoutParams(lp));
        }
        return actionView;
    }

    @Override
    public void bindItemView(MenuItemImpl item, MenuView.ItemView itemView) {
        itemView.initialize(item, 0);

        final ActionMenuView menuView = (ActionMenuView) mMenuView;
        final ActionMenuItemView actionItemView = (ActionMenuItemView) itemView;
        actionItemView.setItemInvoker(menuView);

        if (mPopupCallback == null) {
            mPopupCallback = new ActionMenuPopupCallback();
        }
        actionItemView.setPopupCallback(mPopupCallback);
    }

    @Override
    public boolean shouldIncludeItem(int childIndex, MenuItemImpl item) {
        return item.isActionButton();
    }

    @Override
    public void updateMenuView(boolean cleared) {
        super.updateMenuView(cleared);

        if (mMenuView != null) {//sesl
            ((View) mMenuView).requestLayout();
        }

        if (mMenu != null) {
            final ArrayList<MenuItemImpl> actionItems = mMenu.getActionItems();
            final int count = actionItems.size();
            for (int i = 0; i < count; i++) {
                final MenuItemImpl menuItem = actionItems.get(i);
                final ActionProvider provider = menuItem.getSupportActionProvider();
                if (provider != null) {
                    provider.setSubUiVisibilityListener(this);
                }

                //Custom
                updateActionItemBadge(((ActionMenuView) mMenuView), menuItem);
            }
        }

        final ArrayList<MenuItemImpl> nonActionItems = mMenu != null ?
                mMenu.getNonActionItems() : null;

        boolean hasOverflow = false;
        if (mReserveOverflow && nonActionItems != null) {
            final int count = nonActionItems.size();
            if (count == 1) {
                hasOverflow = !nonActionItems.get(0).isActionViewExpanded();
            } else {
                hasOverflow = count > 0;
            }
        }

        if (hasOverflow) {
            if (mOverflowButton == null) {
                mOverflowButton = new OverflowMenuButton(mSystemContext);
                mOverflowButton.setId(R.id.sesl_action_bar_overflow_button);//sesl
            }
            ViewGroup parent = (ViewGroup) mOverflowButton.getParent();
            if (parent != mMenuView) {
                if (parent != null) {
                    parent.removeView(mOverflowButton);
                }
                ActionMenuView menuView = (ActionMenuView) mMenuView;
                if (menuView != null) {//sesl
                    menuView.addView(mOverflowButton, menuView.generateOverflowButtonLayoutParams());
                }
            }
        } else if (mOverflowButton != null && mOverflowButton.getParent() == mMenuView) {
            if (mMenuView != null) {//sesl
                ((ViewGroup) mMenuView).removeView(mOverflowButton);
            }

            if (isOverflowMenuShowing()) {//sesl
                hideOverflowMenu();
            }
        }

        //Sesl
        if (mOverflowButton != null && mMenuView != null) {
            ActionMenuView menuView = (ActionMenuView) mMenuView;
            mOverflowButton.setBadgeText(menuView.getOverflowBadgeText(), menuView.getSumOfDigitsInOverflowBadges()/*custom*/);
        }
        if ((mOverflowButton == null || mOverflowButton.getVisibility() != View.VISIBLE)
                && isOverflowMenuShowing()) {
            hideOverflowMenu();
        }
        //sesl

        if (mMenuView != null) {//sesl
            ((ActionMenuView) mMenuView).setOverflowReserved(mReserveOverflow);
        }
    }

    //Custom
    //Note: All `ActionMenuItemView` children of ActionMenuView must
    //be laid out  already before invoking this
    private void updateActionItemBadge(ActionMenuView actionMenuView, MenuItemImpl item) {
        final int count = actionMenuView.getChildCount();
        for (int i = 0; i < count; i++) {
            View menuItemView =  actionMenuView.getChildAt(i);
            if (!(menuItemView instanceof ActionMenuItemView)) continue;
            if (item.getItemId() == ((ActionMenuItemView)menuItemView).getItemData().getItemId()){
                actionMenuView.removeView(menuItemView);
                ActionMenuViewBadgedWrapper amvBadgedWrapper = new ActionMenuViewBadgedWrapper(actionMenuView.getContext(), (ActionMenuItemView)menuItemView);
                actionMenuView.addView(amvBadgedWrapper, i);
                return;
            }
        }
    }

    //Custom
    @RestrictTo(LIBRARY_GROUP_PREFIX)
    class ActionMenuViewBadgedWrapper extends FrameLayout {

        public ActionMenuViewBadgedWrapper(Context context, ActionMenuItemView menuItemView) {
            super(context);
            addView(menuItemView);
            addView(LayoutInflater.from(context).inflate(
                            R.layout.sesl_action_menu_item_badge, ActionMenuViewBadgedWrapper.this, false));
            updateItemViewBadge(menuItemView.getItemData().getBadgeText());
        }

        private void updateItemViewBadge(String badgeText) {

            ViewGroup badgeView = (ViewGroup) getChildAt(1);

            if (badgeText == null) {
                badgeView.setVisibility(GONE);
                return;
            }

            String formattedTextBadge;
            int badgeWidth;
            int badgeHeight;
            int badgeTopMargin;
            FrameLayout.LayoutParams badgeLp = (FrameLayout.LayoutParams) badgeView.getLayoutParams();

            Resources res = getResources();
            try {
                final int badgeCount = Math.min(Integer.parseInt(badgeText), BADGE_LIMIT_NUMBER);
                formattedTextBadge = mNumberFormat.format(badgeCount);

                final float default_width = res.getDimension(R.dimen.sesl_badge_default_width);
                final float additionalWidth = res.getDimension(R.dimen.sesl_badge_additional_width);
                badgeWidth = (int) (default_width + (formattedTextBadge.length() * additionalWidth));
                badgeHeight = (int)(default_width + additionalWidth);
                badgeTopMargin = (int) res.getDimension(R.dimen.sesl_menu_item_number_badge_top_margin);
            } catch (NumberFormatException e) {

                //This means `badgeText` is not a number
                //We will show dot badge instead
                formattedTextBadge = "";

                final int badgeSize = (int) res.getDimension(R.dimen.sesl_menu_item_badge_size);
                badgeWidth = badgeSize;
                badgeHeight = badgeSize;
                badgeTopMargin = (int) res.getDimension(R.dimen.sesl_menu_item_badge_top_margin);
            }

            ((TextView)badgeView.getChildAt(0)).setText(formattedTextBadge);
            badgeLp.setMarginEnd((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 7, res.getDisplayMetrics()));
            badgeLp.topMargin = badgeTopMargin;
            badgeLp.width = badgeWidth;
            badgeLp.height = badgeHeight;
            badgeView.setLayoutParams(badgeLp);
            badgeView.setVisibility(VISIBLE);
        }
    }

    @Override
    public boolean filterLeftoverView(ViewGroup parent, int childIndex) {
        if (parent.getChildAt(childIndex) == mOverflowButton) return false;
        return super.filterLeftoverView(parent, childIndex);
    }

    @Override
    public boolean onSubMenuSelected(SubMenuBuilder subMenu) {
        if (subMenu == null || !subMenu.hasVisibleItems()) return false;//sesl

        SubMenuBuilder topSubMenu = subMenu;
        while (topSubMenu.getParentMenu() != mMenu) {
            topSubMenu = (SubMenuBuilder) topSubMenu.getParentMenu();
        }
        View anchor = findViewForItem(topSubMenu.getItem());
        if (anchor == null) {
            // This means the submenu was opened from an overflow menu item, indicating the
            // MenuPopupHelper will handle opening the submenu via its MenuPopup. Return false to
            // ensure that the MenuPopup acts as presenter for the submenu, and acts on its
            // responsibility to display the new submenu.
            return false;
        }

        mOpenSubMenuId = subMenu.getItem().getItemId();

        boolean preserveIconSpacing = false;
        final int count = subMenu.size();
        for (int i = 0; i < count; i++) {
            MenuItem childItem = subMenu.getItem(i);
            if (childItem.isVisible() && childItem.getIcon() != null) {
                preserveIconSpacing = true;
                break;
            }
        }

        mActionButtonPopup = new ActionButtonSubmenu(mContext, subMenu, anchor);
        mActionButtonPopup.setForceShowIcon(preserveIconSpacing);
        mActionButtonPopup.show();

        super.onSubMenuSelected(subMenu);
        return true;
    }

    private View findViewForItem(MenuItem item) {
        final ViewGroup parent = (ViewGroup) mMenuView;
        if (parent == null) return null;

        final int count = parent.getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = parent.getChildAt(i);
            if (child instanceof MenuView.ItemView &&
                    ((MenuView.ItemView) child).getItemData() == item) {
                return child;
            }
        }
        return null;
    }

    /**
     * Display the overflow menu if one is present.
     * @return true if the overflow menu was shown, false otherwise.
     */
    public boolean showOverflowMenu() {
        if (mReserveOverflow && !isOverflowMenuShowing() && mMenu != null && mMenuView != null &&
                mPostedOpenRunnable == null && !mMenu.getNonActionItems().isEmpty()) {
            OverflowPopup popup = new OverflowPopup(mContext, mMenu, mOverflowButton, true);
            mPostedOpenRunnable = new OpenOverflowRunnable(popup);
            // Post this for later; we might still need a layout for the anchor to be right.
            ((View) mMenuView).post(mPostedOpenRunnable);

            return true;
        }
        return false;
    }

    /**
     * Hide the overflow menu if it is currently showing.
     *
     * @return true if the overflow menu was hidden, false otherwise.
     */
    public boolean hideOverflowMenu() {
        if (mPostedOpenRunnable != null && mMenuView != null) {
            ((View) mMenuView).removeCallbacks(mPostedOpenRunnable);
            mPostedOpenRunnable = null;
            return true;
        }

        MenuPopupHelper popup = mOverflowPopup;
        if (popup != null) {
            popup.dismiss();
            return true;
        }
        return false;
    }

    /**
     * Dismiss all popup menus - overflow and submenus.
     * @return true if popups were dismissed, false otherwise. (This can be because none were open.)
     */
    public boolean dismissPopupMenus() {
        boolean result = hideOverflowMenu();
        result |= hideSubMenus();
        return result;
    }

    /**
     * Dismiss all submenu popups.
     *
     * @return true if popups were dismissed, false otherwise. (This can be because none were open.)
     */
    public boolean hideSubMenus() {
        if (mActionButtonPopup != null) {
            mActionButtonPopup.dismiss();
            return true;
        }
        return false;
    }

    /**
     * @return true if the overflow menu is currently showing
     */
    public boolean isOverflowMenuShowing() {
        return mOverflowPopup != null && mOverflowPopup.isShowing();
    }

    public boolean isOverflowMenuShowPending() {
        return mPostedOpenRunnable != null || isOverflowMenuShowing();
    }

    /**
     * @return true if space has been reserved in the action menu for an overflow item.
     */
    public boolean isOverflowReserved() {
        return mReserveOverflow;
    }

    @Override
    public boolean flagActionItems() {
        final ArrayList<MenuItemImpl> visibleItems;
        final int itemsSize;
        if (mMenu != null) {//sesl
            visibleItems = mMenu.getVisibleItems();
            itemsSize = visibleItems.size();
        } else {
            visibleItems = null;
            itemsSize = 0;
        }

        int maxActions = mMaxItems;
        int widthLimit = mActionItemWidthLimit;
        final int querySpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        //Sesl
        if (mMenuView == null) {
            Log.d(TAG, "mMenuView is null, maybe Menu has not been initialized.");
            return false;
        }
        //sesl
        final ViewGroup parent = (ViewGroup) mMenuView;

        int requiredItems = 0;
        int requestedItems = 0;
        int firstActionWidth = 0;
        boolean hasOverflow = false;
        for (int i = 0; i < itemsSize; i++) {
            MenuItemImpl item = visibleItems.get(i);
            if (item.requiresActionButton()) {
                requiredItems++;
            } else if (item.requestsActionButton()) {
                requestedItems++;
            } else {
                hasOverflow = true;
            }
            if (mExpandedActionViewsExclusive && item.isActionViewExpanded()) {
                // Overflow everything if we have an expanded action view and we're
                // space constrained.
                maxActions = 0;
            }
        }

        // Reserve a spot for the overflow item if needed.
        if (mReserveOverflow &&
                (hasOverflow || requiredItems + requestedItems > maxActions)) {
            maxActions--;
        }
        maxActions -= requiredItems;

        final SparseBooleanArray seenGroups = mActionButtonGroups;
        seenGroups.clear();

        int cellSize = 0;
        int cellsRemaining = 0;
        if (mStrictWidthLimit) {
            cellsRemaining = widthLimit / mMinCellSize;
            final int cellSizeRemaining = widthLimit % mMinCellSize;
            cellSize = mMinCellSize + cellSizeRemaining / cellsRemaining;
        }

        // Flag as many more requested items as will fit.
        for (int i = 0; i < itemsSize; i++) {
            MenuItemImpl item = visibleItems.get(i);

            if (item.requiresActionButton()) {
                View v = getItemView(item, null, parent);
                if (mStrictWidthLimit) {
                    cellsRemaining -= ActionMenuView.measureChildForCells(v,
                            cellSize, cellsRemaining, querySpec, 0);
                } else {
                    v.measure(querySpec, querySpec);
                }
                final int measuredWidth = v.getMeasuredWidth();
                widthLimit -= measuredWidth;
                if (firstActionWidth == 0) {
                    firstActionWidth = measuredWidth;
                }
                final int groupId = item.getGroupId();
                if (groupId != 0) {
                    seenGroups.put(groupId, true);
                }
                item.setIsActionButton(true);
            } else if (item.requestsActionButton()) {
                // Items in a group with other items that already have an action slot
                // can break the max actions rule, but not the width limit.
                final int groupId = item.getGroupId();
                final boolean inGroup = seenGroups.get(groupId);
                boolean isAction = (maxActions > 0 || inGroup) && widthLimit > 0 &&
                        (!mStrictWidthLimit || cellsRemaining > 0);

                if (isAction) {
                    View v = getItemView(item, null, parent);
                    if (mStrictWidthLimit) {
                        final int cells = ActionMenuView.measureChildForCells(v,
                                cellSize, cellsRemaining, querySpec, 0);
                        cellsRemaining -= cells;
                        if (cells == 0) {
                            isAction = false;
                        }
                    } else {
                        v.measure(querySpec, querySpec);
                    }
                    final int measuredWidth = v.getMeasuredWidth();
                    widthLimit -= measuredWidth;
                    if (firstActionWidth == 0) {
                        firstActionWidth = measuredWidth;
                    }

                      //sesl
//                    if (mStrictWidthLimit) {
                        isAction &= widthLimit >= 0;
//                    } else {
//                        // Did this push the entire first item past the limit?
//                        isAction &= widthLimit + firstActionWidth > 0;
//                    }
                }

                if (isAction && groupId != 0) {
                    seenGroups.put(groupId, true);
                } else if (inGroup) {
                    // We broke the width limit. Demote the whole group, they all overflow now.
                    seenGroups.put(groupId, false);
                    for (int j = 0; j < i; j++) {
                        MenuItemImpl areYouMyGroupie = visibleItems.get(j);
                        if (areYouMyGroupie.getGroupId() == groupId) {
                            // Give back the action slot
                            if (areYouMyGroupie.isActionButton()) maxActions++;
                            areYouMyGroupie.setIsActionButton(false);
                        }
                    }
                }

                if (isAction) maxActions--;

                item.setIsActionButton(isAction);
            } else {
                // Neither requires nor requests an action button.
                item.setIsActionButton(false);
            }
        }
        return true;
    }

    @Override
    public void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing) {
        dismissPopupMenus();
        super.onCloseMenu(menu, allMenusAreClosing);
    }

    @Override
    public Parcelable onSaveInstanceState() {
        SavedState state = new SavedState();
        state.openSubMenuId = mOpenSubMenuId;
        return state;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            return;
        }

        SavedState saved = (SavedState) state;
        if (saved.openSubMenuId > 0) {
            if (mMenu != null) {//sesl
                MenuItem item = mMenu.findItem(saved.openSubMenuId);
                if (item != null) {
                    SubMenuBuilder subMenu = (SubMenuBuilder) item.getSubMenu();
                    onSubMenuSelected(subMenu);
                }
            }
        }
    }

    @Override
    public void onSubUiVisibilityChanged(boolean isVisible) {
        if (isVisible) {
            // Not a submenu, but treat it like one.
            super.onSubMenuSelected(null);
        } else if (mMenu != null) {
            mMenu.close(false /* closeAllMenus */);
        }
    }

    public void setMenuView(ActionMenuView menuView) {
        mMenuView = menuView;
        menuView.initialize(mMenu);
    }

    @SuppressLint("BanParcelableUsage")
    private static class SavedState implements Parcelable {
        public int openSubMenuId;

        SavedState() {
        }

        SavedState(Parcel in) {
            openSubMenuId = in.readInt();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(openSubMenuId);
        }

        public static final Parcelable.Creator<SavedState> CREATOR
                = new Parcelable.Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    //Sesl
    @RestrictTo(LIBRARY_GROUP_PREFIX)
    class OverflowMenuButton extends FrameLayout implements ActionMenuView.ActionMenuChildView {

        private final View mInnerView;
        private CharSequence mContentDescription;
        private final ViewGroup mBadgeBackground;
        private final TextView mBadgeText;
        private CharSequence mBadgeContentDescription;


        public OverflowMenuButton(Context context) {
            super(context);

            mInnerView = mUseTextItemMode ?
                    new OverflowTextView(context) : new OverflowImageView(context);
            addView(mInnerView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            final Resources res = getResources();

            if (mInnerView instanceof OverflowImageView) {
                mContentDescription = mInnerView.getContentDescription();
                mBadgeContentDescription = ((Object) mContentDescription) + " , "
                        + res.getString(R.string.sesl_action_menu_overflow_badge_description);
            }

            if (TextUtils.isEmpty(mContentDescription)) {
                mContentDescription = res.getString(R.string.sesl_action_menu_overflow_description);
                if (mInnerView != null) {
                    mInnerView.setContentDescription(mContentDescription);
                }
            }

            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mBadgeBackground = (ViewGroup) inflater.inflate(R.layout.sesl_action_menu_item_badge, this, false);
            mBadgeText = (TextView) mBadgeBackground.getChildAt(0);
            addView(mBadgeBackground);
        }

        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);

            final Resources res = getResources();

            mBadgeText.setTextSize(TypedValue.COMPLEX_UNIT_PX, (int) res.getDimension(R.dimen.sesl_menu_item_badge_text_size));

            ViewGroup.MarginLayoutParams lp = (MarginLayoutParams) mBadgeBackground.getLayoutParams();
            CharSequence text = this.mBadgeText.getText();
            if (text != null) {
                int badge_size = (int)res.getDimension(R.dimen.sesl_menu_item_badge_size);
                lp.width = badge_size;
                lp.height = badge_size;
            } else {
                float badge_size =  res.getDimension(R.dimen.sesl_badge_default_width);
                lp.width = (int) badge_size ;
                lp.height = (int) (badge_size + res.getDimension(R.dimen.sesl_badge_additional_width));
                lp.topMargin = (int) res.getDimension(R.dimen.sesl_menu_item_number_badge_top_margin);
                lp.setMarginEnd((int) res.getDimension(R.dimen.sesl_menu_item_number_badge_end_margin));
            }

            mBadgeBackground.setLayoutParams(lp);

            if (mInnerView instanceof OverflowImageView) {
                mContentDescription = getContentDescription();
                mBadgeContentDescription = ((Object) mContentDescription) + " , "
                        + res.getString(R.string.sesl_action_menu_overflow_badge_description);
            }

            if (TextUtils.isEmpty(mContentDescription)) {
                mContentDescription = res.getString(R.string.sesl_action_menu_overflow_description);
                mBadgeContentDescription = ((Object) mContentDescription) + " , "
                        + res.getString(R.string.sesl_action_menu_overflow_badge_description);
            }

            if (mBadgeBackground.getVisibility() == VISIBLE) {
                if (mInnerView instanceof OverflowImageView) {
                    mInnerView.setContentDescription(mBadgeContentDescription);
                }
            } else {
                if (mInnerView instanceof OverflowImageView) {
                    mInnerView.setContentDescription(mContentDescription);
                }
            }
        }

        @Override
        public boolean needsDividerBefore() {
            return false;
        }

        @Override
        public boolean needsDividerAfter() {
            return false;
        }

        public View getInnerView() {
            return mInnerView;
        }

        public void setBadgeText(String badgeText, int badgeCount) {
            if (badgeCount > BADGE_LIMIT_NUMBER) {
                badgeCount = BADGE_LIMIT_NUMBER;
            }
            String nBadge;
            int length;
            int dimension;

            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) mBadgeBackground.getLayoutParams();
            Resources res = getResources();
            if (badgeText == null) {
                nBadge = mNumberFormat.format(badgeCount);
                float badge_size  = (int)res.getDimension(R.dimen.sesl_badge_default_width);
                float badge_additional_width = res.getDimension(R.dimen.sesl_badge_additional_width);
                length = (int) (badge_size + (nBadge.length() * badge_additional_width));
                dimension = (int) (badge_size + badge_additional_width);
                lp.topMargin = (int) res.getDimension(R.dimen.sesl_menu_item_number_badge_top_margin);
                lp.setMarginEnd((int) res.getDimension(R.dimen.sesl_menu_item_number_badge_end_margin));
            } else {
                float badge_size  = (int)res.getDimension(R.dimen.sesl_menu_item_badge_size);
                length = dimension = (int) badge_size;
                nBadge = "";
            }
            mBadgeText.setText(nBadge);
            lp.width = length;
            lp.height = dimension;
            mBadgeBackground.setLayoutParams(lp);

            mBadgeBackground.setVisibility(badgeCount > 0 ? View.VISIBLE : View.GONE);
            if (mBadgeBackground.getVisibility() == View.VISIBLE) {
                if (mInnerView instanceof OverflowImageView) {
                    mInnerView.setContentDescription(mBadgeContentDescription);
                }
            } else {
                if (mInnerView instanceof OverflowImageView) {
                    mInnerView.setContentDescription(mContentDescription);
                }
            }
        }
    }


    private class OverflowImageView extends AppCompatImageView {
        private Configuration mConfiguration;

        private SeslShowButtonShapesHelper mSBSHelper;

        public OverflowImageView(Context context) {
            super(context, null, R.attr.actionOverflowButtonStyle);

            setClickable(true);
            setFocusable(true);
            setLongClickable(true);

            final Resources res = getResources();

            mTooltipText = res.getString(R.string.sesl_action_menu_overflow_description);

            TooltipCompat.setTooltipText(this, mTooltipText);

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
                mSBSHelper = new SeslShowButtonShapesHelper(this,
                        ResourcesCompat.getDrawable(res, R.drawable.sesl_more_button_show_button_shapes_background, null),
                        getBackground());
            }
            mConfiguration = res.getConfiguration();

        }

        @Override
        protected void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            Configuration configuration2 = mConfiguration;
            int configDiff;
            if (configuration2 != null) {
                configDiff = configuration2.diff(newConfig);
            } else {
                configDiff = 4096;
            }
            mConfiguration = newConfig;

            final Context context = getContext();

            TypedArray a = context.obtainStyledAttributes(null, R.styleable.View, R.attr.actionOverflowButtonStyle, 0);
            setMinimumHeight(a.getDimensionPixelSize(R.styleable.View_android_minHeight, 0));
            a.recycle();

            mTooltipText = context.getResources().getString(R.string.sesl_action_menu_overflow_description);

            if ((configDiff & 4096) != 0) {
                TypedArray a2 = context.obtainStyledAttributes(null, R.styleable.AppCompatImageView, R.attr.actionOverflowButtonStyle, 0);
                Drawable imgSrc = ContextCompat.getDrawable(context, a2.getResourceId(R.styleable.AppCompatImageView_android_src, -1));
                if (imgSrc != null) {
                    setImageDrawable(imgSrc);
                }
                a2.recycle();
            }

            if (mSBSHelper != null) {
                mSBSHelper.updateOverflowButtonBackground(ContextCompat.getDrawable(context,
                        R.drawable.sesl_more_button_show_button_shapes_background));
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (mSBSHelper != null) {
                mSBSHelper.updateButtonBackground();
            }
        }

        @Override
        public boolean performLongClick() {
            TooltipCompat.seslSetNextTooltipForceActionBarPosX(true);
            TooltipCompat.seslSetNextTooltipForceBelow(true);
            return super.performLongClick();
        }

        @Override
        public boolean performClick() {
            if (super.performClick()) {
                return true;
            }

            playSoundEffect(SoundEffectConstants.CLICK);
            if (showOverflowMenu() && isHovered()) {
                TooltipCompat.setTooltipNull(true);
            }
            return true;
        }

        @Override
        protected boolean setFrame(int l, int t, int r, int b) {
            final boolean changed = super.setFrame(l, t, r, b);

            // Set up the hotspot bounds to be centered on the image.
            final Drawable d = getDrawable();
            final Drawable bg = getBackground();
            if (d != null && bg != null) {
                final int width = getWidth();
                final int height = getHeight();
                final int offsetX = getPaddingLeft() - getPaddingRight();
                final int halfOffsetX = offsetX / 2;
                DrawableCompat.setHotspotBounds(bg, halfOffsetX, 0,
                        halfOffsetX + width, height);
            }

            return changed;
        }
    }


    private class OverflowTextView extends AppCompatTextView {
        private SeslShowButtonShapesHelper mSBBHelper;

        public OverflowTextView(Context context) {
            super(context, null, R.attr.actionOverflowButtonStyle);

            setClickable(true);
            setFocusable(true);

            TypedArray a = context.getTheme().obtainStyledAttributes(null, R.styleable.AppCompatTheme, 0, 0);
            TextViewCompat.setTextAppearance(this, a.getResourceId(R.styleable.AppCompatTheme_actionMenuTextAppearance, 0));
            a.recycle();

            final Resources res = getResources();

            setText(res.getString(R.string.sesl_more_item_label));

            //Sesl start
            if (SeslMisc.isLightTheme(context)) {
                setBackgroundResource(R.drawable.sesl_action_bar_item_text_background_light);
            } else {
                setBackgroundResource(R.drawable.sesl_action_bar_item_text_background_dark);
            }

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
                seslSetButtonShapeEnabled(true);
            } else {
                mSBBHelper = new SeslShowButtonShapesHelper(this,
                        ResourcesCompat.getDrawable(res, R.drawable.sesl_action_text_button_show_button_shapes_background, null),
                        getBackground());
            }
        }

        @Override
        protected void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            if (mSBBHelper != null) {
                mSBBHelper.updateButtonBackground();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (mSBBHelper != null) {
                mSBBHelper.updateButtonBackground();
            }
        }

        @Override
        public boolean performClick() {
            if (super.performClick()) {
                return true;
            }

            playSoundEffect(SoundEffectConstants.CLICK);
            showOverflowMenu();
            return true;
        }
    }
    //sesl

    private class OverflowPopup extends MenuPopupHelper {
        public OverflowPopup(Context context, MenuBuilder menu, View anchorView,
                boolean overflowOnly) {
            super(context, menu, anchorView, overflowOnly, R.attr.actionOverflowMenuStyle);
            setGravity(GravityCompat.END);
            setPresenterCallback(mPopupPresenterCallback);
        }

        @Override
        protected void onDismiss() {
            if (mMenu != null) {
                mMenu.close();
            }
            mOverflowPopup = null;

            super.onDismiss();
        }
    }

    private class ActionButtonSubmenu extends MenuPopupHelper {
        public ActionButtonSubmenu(Context context, SubMenuBuilder subMenu, View anchorView) {
            super(context, subMenu, anchorView, false, R.attr.actionOverflowMenuStyle);

            MenuItemImpl item = (MenuItemImpl) subMenu.getItem();
            if (!item.isActionButton()) {
                // Give a reasonable anchor to nested submenus.
                setAnchorView(mOverflowButton == null ? (View) mMenuView : mOverflowButton);
            }

            setPresenterCallback(mPopupPresenterCallback);
        }

        @Override
        protected void onDismiss() {
            mActionButtonPopup = null;
            mOpenSubMenuId = 0;

            super.onDismiss();
        }
    }

    private class PopupPresenterCallback implements Callback {
        PopupPresenterCallback() {
        }

        @Override
        public boolean onOpenSubMenu(@NonNull MenuBuilder subMenu) {
            if (subMenu == mMenu) return false;

            mOpenSubMenuId = ((SubMenuBuilder) subMenu).getItem().getItemId();
            final Callback cb = getCallback();
            return cb != null ? cb.onOpenSubMenu(subMenu) : false;
        }

        @Override
        public void onCloseMenu(@NonNull MenuBuilder menu, boolean allMenusAreClosing) {
            if (menu instanceof SubMenuBuilder) {
                menu.getRootMenu().close(false /* closeAllMenus */);
            }
            final Callback cb = getCallback();
            if (cb != null) {
                cb.onCloseMenu(menu, allMenusAreClosing);
            }
        }
    }

    private class OpenOverflowRunnable implements Runnable {
        private OverflowPopup mPopup;

        public OpenOverflowRunnable(OverflowPopup popup) {
            mPopup = popup;
        }

        @Override
        public void run() {
            if (mMenu != null) {
                mMenu.changeMenuMode();
            }
            final View menuView = (View) mMenuView;
            if (menuView != null && menuView.getWindowToken() != null && mPopup.tryShow(0, 0)) {
                 //sesl
                mOverflowPopup = mPopup;
            }
            mPostedOpenRunnable = null;
        }
    }

    private class ActionMenuPopupCallback extends ActionMenuItemView.PopupCallback {
        ActionMenuPopupCallback() {
        }

        @Override
        public ShowableListMenu getPopup() {
            return mActionButtonPopup != null ? mActionButtonPopup.getPopup() : null;
        }
    }
}
