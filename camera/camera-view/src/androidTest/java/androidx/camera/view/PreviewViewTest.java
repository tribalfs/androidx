/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.camera.view;

import static androidx.camera.view.PreviewView.ImplementationMode.SURFACE_VIEW;
import static androidx.camera.view.PreviewView.ImplementationMode.TEXTURE_VIEW;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.testing.fakes.FakeActivity;
import androidx.camera.view.test.R;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.rule.GrantPermissionRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Instrumented tests for {@link PreviewView}.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class PreviewViewTest {

    @Rule
    public final GrantPermissionRule mRuntimePermissionRule = GrantPermissionRule.grant(
            Manifest.permission.CAMERA);
    @Rule
    public final ActivityTestRule<FakeActivity> mActivityRule = new ActivityTestRule<>(
            FakeActivity.class);
    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    @UiThreadTest
    public void usesTextureView_whenCameraInfoNull() {
        final PreviewView previewView = new PreviewView(mContext);
        setContentView(previewView);
        previewView.setPreferredImplementationMode(SURFACE_VIEW);
        previewView.createSurfaceProvider(null);

        assertThat(previewView.mImplementation).isInstanceOf(TextureViewImplementation.class);
    }

    @Test
    @UiThreadTest
    public void usesTextureView_whenLegacyDevice() {
        final CameraInfo cameraInfo = mock(CameraInfo.class);
        when(cameraInfo.getImplementationType()).thenReturn(
                CameraInfo.IMPLEMENTATION_TYPE_CAMERA2_LEGACY);

        final PreviewView previewView = new PreviewView(mContext);
        setContentView(previewView);
        previewView.setPreferredImplementationMode(SURFACE_VIEW);
        previewView.createSurfaceProvider(cameraInfo);

        assertThat(previewView.mImplementation).isInstanceOf(TextureViewImplementation.class);
    }

    @Test
    @UiThreadTest
    public void usesSurfaceView_whenNonLegacyDevice_andPreferredImplModeSurfaceView() {
        final CameraInfo cameraInfo = mock(CameraInfo.class);
        when(cameraInfo.getImplementationType()).thenReturn(
                CameraInfo.IMPLEMENTATION_TYPE_CAMERA2);

        final PreviewView previewView = new PreviewView(mContext);
        setContentView(previewView);
        previewView.setPreferredImplementationMode(SURFACE_VIEW);
        previewView.createSurfaceProvider(cameraInfo);

        assertThat(previewView.mImplementation).isInstanceOf(SurfaceViewImplementation.class);
    }

    @Test
    @UiThreadTest
    public void usesTextureView_whenNonLegacyDevice_andPreferredImplModeTextureView() {
        final CameraInfo cameraInfo = mock(CameraInfo.class);
        when(cameraInfo.getImplementationType()).thenReturn(
                CameraInfo.IMPLEMENTATION_TYPE_CAMERA2);

        final PreviewView previewView = new PreviewView(mContext);
        setContentView(previewView);
        previewView.setPreferredImplementationMode(TEXTURE_VIEW);
        previewView.createSurfaceProvider(cameraInfo);

        assertThat(previewView.mImplementation).isInstanceOf(TextureViewImplementation.class);
    }

    @Test(expected = NullPointerException.class)
    public void throwsException_whenCreatingMeteringPointFactory_beforeCreatingSurfaceProvider() {
        final PreviewView previewView = new PreviewView(mContext);
        previewView.createMeteringPointFactory(CameraSelector.DEFAULT_BACK_CAMERA);
    }

    @Test
    @UiThreadTest
    public void getsPreferredImplementationMode() {
        final PreviewView previewView = new PreviewView(mContext);
        previewView.setPreferredImplementationMode(SURFACE_VIEW);

        assertThat(previewView.getPreferredImplementationMode()).isEqualTo(SURFACE_VIEW);
    }

    @Test
    @UiThreadTest
    public void getsScaleTypeProgrammatically() {
        final PreviewView previewView = new PreviewView(mContext);
        previewView.setScaleType(PreviewView.ScaleType.FIT_END);

        assertThat(previewView.getScaleType()).isEqualTo(PreviewView.ScaleType.FIT_END);
    }

    @Test
    @UiThreadTest
    public void getsScaleTypeFromXMLLayout() {
        final PreviewView previewView = (PreviewView) LayoutInflater.from(mContext).inflate(
                R.layout.preview_view_scale_type_fit_end, null);
        assertThat(previewView.getScaleType()).isEqualTo(PreviewView.ScaleType.FIT_END);
    }

    @Test
    @UiThreadTest
    public void redrawsPreview_whenScaleTypeChanges() {
        final PreviewView previewView = new PreviewView(mContext);
        final PreviewViewImplementation implementation = mock(TestPreviewViewImplementation.class);
        previewView.mImplementation = implementation;

        previewView.setScaleType(PreviewView.ScaleType.FILL_START);

        verify(implementation, times(1)).redrawPreview();
    }

    @Test
    public void redrawsPreview_whenLayoutResized() throws Throwable {
        final AtomicReference<PreviewView> previewView = new AtomicReference<>();
        final AtomicReference<FrameLayout> container = new AtomicReference<>();
        final PreviewViewImplementation implementation = mock(TestPreviewViewImplementation.class);

        mActivityRule.runOnUiThread(() -> {
            previewView.set(new PreviewView(mContext));
            previewView.get().mImplementation = implementation;

            container.set(new FrameLayout(mContext));
            container.get().addView(previewView.get());
            setContentView(container.get());

            // Resize container in order to trigger PreviewView's onLayoutChanged listener.
            final FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) container.get().getLayoutParams();
            params.width = params.width / 2;
            container.get().requestLayout();
        });

        verify(implementation, timeout(1_000).times(1)).redrawPreview();
    }

    @Test
    public void doesNotRedrawPreview_whenDetachedFromWindow() throws Throwable {
        final AtomicReference<PreviewView> previewView = new AtomicReference<>();
        final AtomicReference<FrameLayout> container = new AtomicReference<>();
        final PreviewViewImplementation implementation = mock(TestPreviewViewImplementation.class);

        mActivityRule.runOnUiThread(() -> {
            previewView.set(new PreviewView(mContext));
            previewView.get().mImplementation = implementation;

            container.set(new FrameLayout(mContext));
            container.get().addView(previewView.get());
            setContentView(container.get());

            container.get().removeView(previewView.get());

            // Resize container
            final FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) container.get().getLayoutParams();
            params.width = params.width / 2;
            container.get().requestLayout();
        });

        verify(implementation, never()).redrawPreview();
    }

    @Test
    public void redrawsPreview_whenReattachedToWindow() throws Throwable {
        final AtomicReference<PreviewView> previewView = new AtomicReference<>();
        final AtomicReference<FrameLayout> container = new AtomicReference<>();
        final PreviewViewImplementation implementation = mock(TestPreviewViewImplementation.class);

        mActivityRule.runOnUiThread(() -> {
            previewView.set(new PreviewView(mContext));
            previewView.get().mImplementation = implementation;

            container.set(new FrameLayout(mContext));
            container.get().addView(previewView.get());
            setContentView(container.get());

            container.get().removeView(previewView.get());
            container.get().addView(previewView.get());
        });

        verify(implementation, timeout(1_000).times(1)).redrawPreview();
    }

    private void setContentView(View view) {
        mActivityRule.getActivity().setContentView(view);
    }

    /**
     * An empty implementation of {@link PreviewViewImplementation} used for testing. It allows
     * mocking {@link PreviewViewImplementation} since the latter is package private.
     */
    public static class TestPreviewViewImplementation extends PreviewViewImplementation {

        @Override
        public void initializePreview() {
        }

        @Nullable
        @Override
        public View getPreview() {
            return null;
        }

        @SuppressWarnings("ConstantConditions")
        @NonNull
        @Override
        public Preview.SurfaceProvider getSurfaceProvider() {
            return null;
        }

        @Override
        public void redrawPreview() {
        }
    }
}
