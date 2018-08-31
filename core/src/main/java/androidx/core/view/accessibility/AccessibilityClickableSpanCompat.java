/*
 * Copyright 2018 The Android Open Source Project
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
package androidx.core.view.accessibility;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.os.Bundle;
import android.text.style.ClickableSpan;
import android.view.View;

import androidx.annotation.RestrictTo;
import androidx.core.R;

/**
 * {@link ClickableSpan} cannot be parceled, but accessibility services need to be able to cause
 * their callback handlers to be called. This class serves as a placeholder for the
 * real spans. Calling onClick on these from an accessibility service will result in onClick being
 * called on the represented span in the app process.
 */
public final class AccessibilityClickableSpanCompat extends ClickableSpan {

    // The id of the span this one replaces
    private final int mOriginalClickableSpanId;

    private final AccessibilityNodeInfoCompat mNodeInfoCompat;

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public static final String SPAN_ID = "ACCESSIBILITY_CLICKABLE_SPAN_ID";

    /**
     * @param originalClickableSpanId The id of the span this one replaces
     * @param nodeInfoCompat The nodeInfoCompat to be associated with this span.
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public AccessibilityClickableSpanCompat(int originalClickableSpanId,
            AccessibilityNodeInfoCompat nodeInfoCompat) {
        mOriginalClickableSpanId = originalClickableSpanId;
        mNodeInfoCompat = nodeInfoCompat;
    }

    /**
     * Perform the click from an accessibility service.
     *
     * @param unused This argument is required by the superclass but is unused. The real view will
     * be determined by the AccessibilityNodeInfo.
     */
    @Override
    public void onClick(View unused) {
        Bundle arguments = new Bundle();
        arguments.putInt(SPAN_ID, mOriginalClickableSpanId);
        mNodeInfoCompat.performAction(R.id.accessibility_action_clickable_span, arguments);
    }
}
