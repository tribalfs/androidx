/* This file is auto-generated from GuidedStepActivity.java.  DO NOT MODIFY. */

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

package com.example.android.leanback;

import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v17.leanback.app.GuidedStepSupportFragment;
import android.support.v17.leanback.widget.GuidedAction;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import android.text.TextUtils;
import android.util.Log;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;

import java.util.List;

/**
 * Activity that showcases different aspects of GuidedStepSupportFragments.
 */
public class GuidedStepSupportActivity extends FragmentActivity {

    private static final int CONTINUE = 1;
    private static final int BACK = 2;

    private static final int FIRST_NAME = 3;
    private static final int LAST_NAME = 4;
    private static final int PAYMENT = 5;

    private static final int OPTION_CHECK_SET_ID = 10;
    private static final int DEFAULT_OPTION = 0;
    private static final String[] OPTION_NAMES = { "Option A", "Option B", "Option C" };
    private static final String[] OPTION_DESCRIPTIONS = { "Here's one thing you can do",
            "Here's another thing you can do", "Here's one more thing you can do" };
    private static final int[] OPTION_DRAWABLES = { R.drawable.ic_guidedstep_option_a,
            R.drawable.ic_guidedstep_option_b, R.drawable.ic_guidedstep_option_c };

    private static final String TAG = GuidedStepSupportActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        GuidedStepSupportFragment.addAsRoot(this, new FirstStepFragment(), android.R.id.content);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.v(TAG, "onConfigurationChanged");
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.v(TAG, "onSaveInstanceState");
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        Log.v(TAG, "onRestoreInstanceState");
        super.onRestoreInstanceState(savedInstanceState);
    }

    private static void addAction(List<GuidedAction> actions, long id, String title, String desc) {
        actions.add(new GuidedAction.Builder()
                .id(id)
                .title(title)
                .description(desc)
                .build());
    }

    private static void addEditableAction(List<GuidedAction> actions, long id, String title, String desc) {
        actions.add(new GuidedAction.Builder()
                .id(id)
                .title(title)
                .description(desc)
                .editable(true)
                .build());
    }

    private static void addEditableAction(List<GuidedAction> actions, long id, String title,
            String editTitle, String desc) {
        actions.add(new GuidedAction.Builder()
                .id(id)
                .title(title)
                .editTitle(editTitle)
                .description(desc)
                .editable(true)
                .build());
    }

    private static void addCheckedAction(List<GuidedAction> actions, int iconResId, Context context,
            String title, String desc) {
        actions.add(new GuidedAction.Builder()
                .title(title)
                .description(desc)
                .checkSetId(OPTION_CHECK_SET_ID)
                .iconResourceId(iconResId, context)
                .build());
    }

    public static class FirstStepFragment extends GuidedStepSupportFragment {
        @Override
        public int onProvideTheme() {
            return R.style.Theme_Example_Leanback_GuidedStep_First;
        }

        @Override
        public Guidance onCreateGuidance(Bundle savedInstanceState) {
            String title = getString(R.string.guidedstep_first_title);
            String breadcrumb = getString(R.string.guidedstep_first_breadcrumb);
            String description = getString(R.string.guidedstep_first_description);
            Drawable icon = getActivity().getDrawable(R.drawable.ic_main_icon);
            return new Guidance(title, description, breadcrumb, icon);
        }

        @Override
        public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
            addAction(actions, CONTINUE, "Continue", "Let's do it");
            addAction(actions, BACK, "Cancel", "Nevermind");
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            FragmentManager fm = getFragmentManager();
            if (action.getId() == CONTINUE) {
                GuidedStepSupportFragment.add(fm, new SecondStepFragment(), android.R.id.content);
            } else {
                getActivity().finish();
            }
        }

        @Override
        protected int getContainerIdForBackground() {
            return R.id.lb_guidedstep_background;
        }
    }

    public static class SecondStepFragment extends GuidedStepSupportFragment {

        @Override
        public Guidance onCreateGuidance(Bundle savedInstanceState) {
            String title = getString(R.string.guidedstep_second_title);
            String breadcrumb = getString(R.string.guidedstep_second_breadcrumb);
            String description = getString(R.string.guidedstep_second_description);
            Drawable icon = getActivity().getDrawable(R.drawable.ic_main_icon);
            return new Guidance(title, description, breadcrumb, icon);
        }

        @Override
        public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
            addEditableAction(actions, FIRST_NAME, "Pat", "Your first name");
            addEditableAction(actions, LAST_NAME, "Smith", "Your last name");
            addEditableAction(actions, PAYMENT, "Payment", "", "Input credit card number");
            addAction(actions, CONTINUE, "Continue", "Continue");
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            if (action.getId() == CONTINUE) {
                FragmentManager fm = getFragmentManager();
                GuidedStepSupportFragment.add(fm, new ThirdStepFragment());
            }
        }

        @Override
        public void onGuidedActionEdited(GuidedAction action) {
            CharSequence editTitle = action.getEditTitle();
            if (TextUtils.isDigitsOnly(editTitle) && editTitle.length() == 16) {
                editTitle = editTitle.subSequence(editTitle.length() - 4, editTitle.length());
                action.setDescription("Visa XXXX-XXXX-XXXX-"+editTitle);
            } else if (editTitle.length() == 0){
                action.setDescription("Input credit card number");
            } else {
                action.setDescription("Error credit card number");
            }
        }
    }

    public static class ThirdStepFragment extends GuidedStepSupportFragment {

        private int mSelectedOption = DEFAULT_OPTION;

        @Override
        public Guidance onCreateGuidance(Bundle savedInstanceState) {
            String title = getString(R.string.guidedstep_third_title);
            String breadcrumb = getString(R.string.guidedstep_third_breadcrumb);
            String description = getString(R.string.guidedstep_third_description);
            Drawable icon = getActivity().getDrawable(R.drawable.ic_main_icon);
            return new Guidance(title, description, breadcrumb, icon);
        }

        @Override
        public GuidanceStylist onCreateGuidanceStylist() {
            return new GuidanceStylist() {
                @Override
                public int onProvideLayoutId() {
                    return R.layout.guidedstep_second_guidance;
                }
            };
        }

        @Override
        public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
            String desc = "The description can be quite long as well.  ";
            desc += "Just be sure to set multilineDescription to true in the GuidedAction.";
            actions.add(new GuidedAction.Builder()
                    .title("Note that Guided Actions can have titles that are quite long.")
                    .description(desc)
                    .multilineDescription(true)
                    .infoOnly(true)
                    .enabled(false)
                    .build());
            for (int i = 0; i < OPTION_NAMES.length; i++) {
                addCheckedAction(actions, OPTION_DRAWABLES[i], getActivity(), OPTION_NAMES[i],
                        OPTION_DESCRIPTIONS[i]);
                if (i == DEFAULT_OPTION) {
                    actions.get(actions.size() -1).setChecked(true);
                }
            }
            addAction(actions, CONTINUE, "Continue", "");
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            if (action.getId() == CONTINUE) {
                FragmentManager fm = getFragmentManager();
                FourthStepFragment f = new FourthStepFragment();
                Bundle arguments = new Bundle();
                arguments.putInt(FourthStepFragment.EXTRA_OPTION, mSelectedOption);
                f.setArguments(arguments);
                GuidedStepSupportFragment.add(fm, f, android.R.id.content);
            } else {
                mSelectedOption = getSelectedActionPosition()-1;
            }
        }

    }

    public static class FourthStepFragment extends GuidedStepSupportFragment {
        public static final String EXTRA_OPTION = "extra_option";

        public FourthStepFragment() {
        }

        public int getOption() {
            Bundle b = getArguments();
            if (b == null) return 0;
            return b.getInt(EXTRA_OPTION, 0);
        }

        @Override
        public Guidance onCreateGuidance(Bundle savedInstanceState) {
            String title = getString(R.string.guidedstep_fourth_title);
            String breadcrumb = getString(R.string.guidedstep_fourth_breadcrumb);
            String description = "You chose: " + OPTION_NAMES[getOption()];
            Drawable icon = getActivity().getDrawable(R.drawable.ic_main_icon);
            return new Guidance(title, description, breadcrumb, icon);
        }

        @Override
        public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
            addAction(actions, CONTINUE, "Done", "All finished");
            addAction(actions, BACK, "Back", "Forgot something...");
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            if (action.getId() == CONTINUE) {
                getActivity().finish();
            } else {
                getFragmentManager().popBackStack();
            }
        }

    }

}
