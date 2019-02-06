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

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraDevice;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.RestrictTo;
import android.support.annotation.RestrictTo.Scope;
import android.util.Size;
import androidx.camera.core.CameraX;
import androidx.camera.core.CameraX.LensFacing;
import androidx.camera.core.FakeUseCase;
import androidx.camera.core.FakeUseCaseConfiguration;
import androidx.camera.core.ImmediateSurface;
import androidx.camera.core.SessionConfiguration;
import androidx.camera.core.UseCaseConfiguration;
import java.util.Map;

/** A fake {@link FakeUseCase} which contain a repeating surface. */
@RestrictTo(Scope.LIBRARY_GROUP)
public class FakeRepeatingUseCase extends FakeUseCase {

  /** The repeating surface. */
  private final ImageReader imageReader =
      ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2);

  public FakeRepeatingUseCase(FakeUseCaseConfiguration configuration) {
    super(configuration);

    FakeUseCaseConfiguration configWithDefaults =
        (FakeUseCaseConfiguration) getUseCaseConfiguration();
    imageReader.setOnImageAvailableListener(
        imageReader -> {
          Image image = imageReader.acquireLatestImage();
          if (image != null) {
            image.close();
          }
        },
        new Handler(Looper.getMainLooper()));

    SessionConfiguration.Builder builder =
        SessionConfiguration.Builder.createFrom(configWithDefaults);
    builder.addSurface(new ImmediateSurface(imageReader.getSurface()));
    try {
      String cameraId = CameraX.getCameraWithLensFacing(configWithDefaults.getLensFacing());
      attachToCamera(cameraId, builder.build());
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Unable to attach to camera with LensFacing " + configWithDefaults.getLensFacing(), e);
    }
  }

  @Override
  protected UseCaseConfiguration.Builder<?, ?, ?> getDefaultBuilder() {
    return new FakeUseCaseConfiguration.Builder()
        .setLensFacing(LensFacing.BACK)
        .setOptionUnpacker(
            (useCaseConfig, sessionConfigBuilder) -> {
              // Set the template since it is currently required by implementation
              sessionConfigBuilder.setTemplateType(CameraDevice.TEMPLATE_PREVIEW);
            });
  }

  @Override
  public void clear() {
    super.clear();
    imageReader.close();
  }

  @Override
  protected Map<String, Size> onSuggestedResolutionUpdated(
      Map<String, Size> suggestedResolutionMap) {
    return suggestedResolutionMap;
  }
}
