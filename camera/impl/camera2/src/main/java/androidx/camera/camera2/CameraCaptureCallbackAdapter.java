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

package androidx.camera.camera2;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.support.annotation.NonNull;
import androidx.camera.core.CameraCaptureCallback;
import androidx.camera.core.CameraCaptureFailure;

/**
 * An adapter that passes {@link CameraCaptureSession.CaptureCallback} to {@link
 * CameraCaptureCallback}.
 */
final class CameraCaptureCallbackAdapter extends CameraCaptureSession.CaptureCallback {

  private final CameraCaptureCallback cameraCaptureCallback;

  CameraCaptureCallbackAdapter(CameraCaptureCallback cameraCaptureCallback) {
    if (cameraCaptureCallback == null) {
      throw new NullPointerException("cameraCaptureCallback is null");
    }
    this.cameraCaptureCallback = cameraCaptureCallback;
  }

  @Override
  public void onCaptureCompleted(
      @NonNull CameraCaptureSession session,
      @NonNull CaptureRequest request,
      @NonNull TotalCaptureResult result) {
    super.onCaptureCompleted(session, request, result);

    cameraCaptureCallback.onCaptureCompleted(new Camera2CameraCaptureResult(result));
  }

  @Override
  public void onCaptureFailed(
      @NonNull CameraCaptureSession session,
      @NonNull CaptureRequest request,
      @NonNull CaptureFailure failure) {
    super.onCaptureFailed(session, request, failure);

    CameraCaptureFailure cameraFailure =
        new CameraCaptureFailure(CameraCaptureFailure.Reason.ERROR);

    cameraCaptureCallback.onCaptureFailed(cameraFailure);
  }
}
