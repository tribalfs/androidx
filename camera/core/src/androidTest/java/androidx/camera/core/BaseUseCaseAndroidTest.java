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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.util.Size;
import androidx.test.runner.AndroidJUnit4;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

@RunWith(AndroidJUnit4.class)
public class BaseUseCaseAndroidTest {
  static class TestUseCase extends FakeUseCase {
    TestUseCase(FakeUseCaseConfiguration configuration) {
      super(configuration);
    }

    void activate() {
      notifyActive();
    }

    void deactivate() {
      notifyInactive();
    }

    void update() {
      notifyUpdated();
    }

    @Override
    protected void updateUseCaseConfiguration(UseCaseConfiguration<?> useCaseConfiguration) {
      super.updateUseCaseConfiguration(useCaseConfiguration);
    }

    @Override
    protected Map<String, Size> onSuggestedResolutionUpdated(
        Map<String, Size> suggestedResolutionMap) {
      return suggestedResolutionMap;
    }
  }

  private BaseUseCase.StateChangeListener mockUseCaseListener;

  @Before
  public void setup() {
    mockUseCaseListener = Mockito.mock(BaseUseCase.StateChangeListener.class);
  }

  @Test
  public void getAttachedCamera() {
    FakeUseCaseConfiguration configuration =
        new FakeUseCaseConfiguration.Builder().setTargetName("UseCase").build();
    TestUseCase testUseCase = new TestUseCase(configuration);
    SessionConfiguration sessionToAttach = new SessionConfiguration.Builder().build();
    testUseCase.attachToCamera("Camera", sessionToAttach);

    Set<String> attachedCameras = testUseCase.getAttachedCameraIds();

    assertThat(attachedCameras).contains("Camera");
  }

  @Test
  public void getAttachedSessionConfiguration() {
    FakeUseCaseConfiguration configuration =
        new FakeUseCaseConfiguration.Builder().setTargetName("UseCase").build();
    TestUseCase testUseCase = new TestUseCase(configuration);
    SessionConfiguration sessionToAttach = new SessionConfiguration.Builder().build();
    testUseCase.attachToCamera("Camera", sessionToAttach);

    SessionConfiguration attachedSession = testUseCase.getSessionConfiguration("Camera");

    assertThat(attachedSession).isEqualTo(sessionToAttach);
  }

  @Test
  public void removeListener() {
    FakeUseCaseConfiguration configuration =
        new FakeUseCaseConfiguration.Builder().setTargetName("UseCase").build();
    TestUseCase testUseCase = new TestUseCase(configuration);
    testUseCase.addStateChangeListener(mockUseCaseListener);
    testUseCase.removeStateChangeListener(mockUseCaseListener);

    testUseCase.activate();

    verify(mockUseCaseListener, never()).onUseCaseActive(any());
  }

  @Test
  public void clearListeners() {
    FakeUseCaseConfiguration configuration =
        new FakeUseCaseConfiguration.Builder().setTargetName("UseCase").build();
    TestUseCase testUseCase = new TestUseCase(configuration);
    testUseCase.addStateChangeListener(mockUseCaseListener);
    testUseCase.clear();

    testUseCase.activate();
    verify(mockUseCaseListener, never()).onUseCaseActive(any());
  }

  @Test
  public void notifyActiveState() {
    FakeUseCaseConfiguration configuration =
        new FakeUseCaseConfiguration.Builder().setTargetName("UseCase").build();
    TestUseCase testUseCase = new TestUseCase(configuration);
    testUseCase.addStateChangeListener(mockUseCaseListener);

    testUseCase.activate();
    verify(mockUseCaseListener, times(1)).onUseCaseActive(testUseCase);
  }

  @Test
  public void notifyInactiveState() {
    FakeUseCaseConfiguration configuration =
        new FakeUseCaseConfiguration.Builder().setTargetName("UseCase").build();
    TestUseCase testUseCase = new TestUseCase(configuration);
    testUseCase.addStateChangeListener(mockUseCaseListener);

    testUseCase.deactivate();
    verify(mockUseCaseListener, times(1)).onUseCaseInactive(testUseCase);
  }

  @Test
  public void notifyUpdatedSettings() {
    FakeUseCaseConfiguration configuration =
        new FakeUseCaseConfiguration.Builder().setTargetName("UseCase").build();
    TestUseCase testUseCase = new TestUseCase(configuration);
    testUseCase.addStateChangeListener(mockUseCaseListener);

    testUseCase.update();
    verify(mockUseCaseListener, times(1)).onUseCaseUpdated(testUseCase);
  }

  @Test
  public void notifyResetUseCase() {
    FakeUseCaseConfiguration configuration =
        new FakeUseCaseConfiguration.Builder().setTargetName("UseCase").build();
    TestUseCase testUseCase = new TestUseCase(configuration);
    testUseCase.addStateChangeListener(mockUseCaseListener);

    testUseCase.notifyReset();
    verify(mockUseCaseListener, times(1)).onUseCaseReset(testUseCase);
  }

  @Test
  public void notifySingleCapture() {
    FakeUseCaseConfiguration configuration =
        new FakeUseCaseConfiguration.Builder().setTargetName("UseCase").build();
    TestUseCase testUseCase = new TestUseCase(configuration);
    testUseCase.addStateChangeListener(mockUseCaseListener);
    CaptureRequestConfiguration captureRequestConfiguration =
        new CaptureRequestConfiguration.Builder().build();

    testUseCase.notifySingleCapture(captureRequestConfiguration);
    verify(mockUseCaseListener, times(1))
        .onUseCaseSingleRequest(testUseCase, captureRequestConfiguration);
  }

  @Test
  public void useCaseConfiguration_canBeUpdated() {
    String originalName = "UseCase";
    FakeUseCaseConfiguration.Builder configurationBuilder =
        new FakeUseCaseConfiguration.Builder().setTargetName(originalName);

    TestUseCase testUseCase = new TestUseCase(configurationBuilder.build());
    String originalRetrievedName = testUseCase.getUseCaseConfiguration().getTargetName();

    // NOTE: Updating the use case name is probably a very bad idea in most cases. However, we'll do
    // it here for the sake of this test.
    String newName = "UseCase-New";
    configurationBuilder.setTargetName(newName);
    testUseCase.updateUseCaseConfiguration(configurationBuilder.build());
    String newRetrievedName = testUseCase.getUseCaseConfiguration().getTargetName();

    assertThat(originalRetrievedName).isEqualTo(originalName);
    assertThat(newRetrievedName).isEqualTo(newName);
  }
}
