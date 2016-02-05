/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.support.v17.leanback.supportleanbackshowcase.app.wizard;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.supportleanbackshowcase.R;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;

import java.util.List;

/**
 * The first screen of the rental wizard. Gives the user the choice between renting the movie in SD
 * or HD quality.
 */
public class WizardExample1stStepFragment extends WizardExampleBaseStepFragment {

    private static final int ACTION_ID_BUY_HD = 1;
    private static final int ACTION_ID_BUY_SD = ACTION_ID_BUY_HD + 1;

    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        GuidanceStylist.Guidance guidance = new GuidanceStylist.Guidance(mMovie.getTitle(),
                getString(R.string.wizard_example_choose_rent_options),
                mMovie.getBreadcrump(), null);
        return guidance;
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        GuidedAction action = new GuidedAction.Builder(getActivity())
                .id(ACTION_ID_BUY_HD)
                .title(R.string.wizard_example_rent_hd)
                .editable(false)
                .description(mMovie.getPriceHd() + " " +
                        getString(R.string.wizard_example_watch_hd))
                .build();
        actions.add(action);
        action = new GuidedAction.Builder(getActivity())
                .id(ACTION_ID_BUY_SD)
                .title(getString(R.string.wizard_example_rent_sd))
                .editable(false)
                .description(mMovie.getPriceSd() + " " +
                        R.string.wizard_example_watch_sd)
                .build();
        actions.add(action);
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        boolean rentHd = ACTION_ID_BUY_HD == action.getId();
        GuidedStepFragment fragment = WizardExample2ndStepFragment.build(rentHd, this);
        add(getFragmentManager(), fragment);
    }
}
