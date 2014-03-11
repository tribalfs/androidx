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

import android.view.View;

/**
 * Interface for receiving notification when a item is clicked.
 * <p>
 * Alternatively {@link Presenter} can attach its own {@link View.OnClickListener} in
 * {@link Presenter#onCreateViewHolder(android.view.ViewGroup)}; but developer should never
 * use these two listeners together.
 * </p>
 */
public interface OnItemClickedListener {

    public void onItemClicked(Object item, Row row);

}
