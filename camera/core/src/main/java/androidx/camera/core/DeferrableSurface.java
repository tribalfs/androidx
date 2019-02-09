/*
 * Copyright (C) 2019 The Android Open Source Project
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

package androidx.camera.core;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import android.view.Surface;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * A reference to a {@link Surface} whose creation can be deferred to a later time.
 * @hide
 */
@RestrictTo(Scope.LIBRARY_GROUP)
public interface DeferrableSurface {
  /** Returns a {@link Surface} that is wrapped in a {@link ListenableFuture}. */
  @Nullable
  ListenableFuture<Surface> getSurface();
}
