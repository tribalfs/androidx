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

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureRequest.Key;
import android.view.Surface;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.camera.core.Configuration.Option;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configurations needed for a capture request.
 *
 * <p>The CaptureRequestConfiguration contains all the {@link android.hardware.camera2} parameters
 * that are required to issue a {@link CaptureRequest}.
 *
 * @hide
 */
@RestrictTo(Scope.LIBRARY_GROUP)
public final class CaptureRequestConfiguration {

    /** The set of {@link Surface} that data from the camera will be put into. */
    final List<DeferrableSurface> mSurfaces;

    /** The parameters used to configure the {@link CaptureRequest}. */
    final Map<Key<?>, CaptureRequestParameter<?>> mCaptureRequestParameters;

    final Configuration mImplementationOptions;

    /**
     * The templates used for configuring a {@link CaptureRequest}. This must match the constants
     * defined by {@link CameraDevice}
     */
    final int mTemplateType;

    /** The camera capture callback for a {@link CameraCaptureSession}. */
    final CameraCaptureCallback mCameraCaptureCallback;

    /** True if this capture request needs a repeating surface */
    private final boolean mUseRepeatingSurface;

    /**
     * Private constructor for a CaptureRequestConfiguration.
     *
     * <p>In practice, the {@link CaptureRequestConfiguration.Builder} will be used to construct a
     * CaptureRequestConfiguration.
     *
     * @param surfaces                 The set of {@link Surface} where data will be put into.
     * @param captureRequestParameters The parameters used to configure the {@link CaptureRequest}.
     * @param implementationOptions    The generic parameters to be passed to the {@link BaseCamera}
     *                                 class.
     * @param templateType             The template for parameters of the CaptureRequest. This
     *                                 must match the
     *                                 constants defined by {@link CameraDevice}.
     * @param cameraCaptureCallback    The camera capture callback.
     */
    CaptureRequestConfiguration(
            List<DeferrableSurface> surfaces,
            Map<Key<?>, CaptureRequestParameter<?>> captureRequestParameters,
            Configuration implementationOptions,
            int templateType,
            CameraCaptureCallback cameraCaptureCallback,
            boolean useRepeatingSurface) {
        mSurfaces = surfaces;
        mCaptureRequestParameters = captureRequestParameters;
        mImplementationOptions = implementationOptions;
        mTemplateType = templateType;
        mCameraCaptureCallback = cameraCaptureCallback;
        mUseRepeatingSurface = useRepeatingSurface;
    }

    /** Get all the surfaces that the request will write data to. */
    public List<DeferrableSurface> getSurfaces() {
        return Collections.unmodifiableList(mSurfaces);
    }

    public Map<Key<?>, CaptureRequestParameter<?>> getCameraCharacteristics() {
        return Collections.unmodifiableMap(mCaptureRequestParameters);
    }

    public Configuration getImplementationOptions() {
        return mImplementationOptions;
    }

    int getTemplateType() {
        return mTemplateType;
    }

    public boolean isUseRepeatingSurface() {
        return mUseRepeatingSurface;
    }

    public CameraCaptureCallback getCameraCaptureCallback() {
        return mCameraCaptureCallback;
    }

    /**
     * Return the builder of a {@link CaptureRequest} which can be issued.
     *
     * <p>Returns {@code null} if a valid {@link CaptureRequest} can not be constructed.
     */
    @Nullable
    public CaptureRequest.Builder buildCaptureRequest(@Nullable CameraDevice device)
            throws CameraAccessException {
        if (device == null) {
            return null;
        }
        CaptureRequest.Builder builder = device.createCaptureRequest(mTemplateType);

        for (CaptureRequestParameter<?> captureRequestParameter :
                mCaptureRequestParameters.values()) {
            captureRequestParameter.apply(builder);
        }

        List<Surface> surfaceList = DeferrableSurfaces.surfaceList(mSurfaces);

        if (surfaceList.isEmpty()) {
            return null;
        }

        for (Surface surface : surfaceList) {
            builder.addTarget(surface);
        }

        return builder;
    }

    /**
     * Builder for easy modification/rebuilding of a {@link CaptureRequestConfiguration}.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public static final class Builder {
        private final Set<DeferrableSurface> mSurfaces = new HashSet<>();
        private final Map<Key<?>, CaptureRequestParameter<?>> mCaptureRequestParameters =
                new HashMap<>();
        private MutableConfiguration mImplementationOptions = MutableOptionsBundle.create();
        private int mTemplateType = -1;
        private CameraCaptureCallback mCameraCaptureCallback =
                CameraCaptureCallbacks.createNoOpCallback();
        private boolean mUseRepeatingSurface = false;

        public Builder() {
        }

        private Builder(CaptureRequestConfiguration base) {
            mSurfaces.addAll(base.mSurfaces);
            mCaptureRequestParameters.putAll(base.mCaptureRequestParameters);
            mImplementationOptions = MutableOptionsBundle.from(base.mImplementationOptions);
            mTemplateType = base.mTemplateType;
            mCameraCaptureCallback = base.mCameraCaptureCallback;
            mUseRepeatingSurface = base.isUseRepeatingSurface();
        }

        /** Create a {@link Builder} from a {@link CaptureRequestConfiguration} */
        public static Builder from(CaptureRequestConfiguration base) {
            return new Builder(base);
        }

        int getTemplateType() {
            return mTemplateType;
        }

        /**
         * Set the template characteristics of the CaptureRequestConfiguration.
         *
         * @param templateType Template constant that must match those defined by {@link
         *                     CameraDevice}
         */
        public void setTemplateType(int templateType) {
            mTemplateType = templateType;
        }

        CameraCaptureCallback getCameraCaptureCallback() {
            return mCameraCaptureCallback;
        }

        public void setCameraCaptureCallback(CameraCaptureCallback cameraCaptureCallback) {
            mCameraCaptureCallback = cameraCaptureCallback;
        }

        /** Add a surface that the request will write data to. */
        public void addSurface(DeferrableSurface surface) {
            mSurfaces.add(surface);
        }

        /** Remove a surface that the request will write data to. */
        public void removeSurface(DeferrableSurface surface) {
            mSurfaces.remove(surface);
        }

        /** Remove all the surfaces that the request will write data to. */
        public void clearSurfaces() {
            mSurfaces.clear();
        }

        Set<DeferrableSurface> getSurfaces() {
            return mSurfaces;
        }

        /** Add a {@link CaptureRequest.Key}-value pair to the request. */
        public <T> void addCharacteristic(Key<T> key, T value) {
            mCaptureRequestParameters.put(key, CaptureRequestParameter.create(key, value));
        }

        /** Add a set of {@link CaptureRequest.Key}-value pairs to the request. */
        public void addCharacteristics(Map<Key<?>, CaptureRequestParameter<?>> characteristics) {
            mCaptureRequestParameters.putAll(characteristics);
        }

        public void setImplementationOptions(Configuration config) {
            mImplementationOptions = MutableOptionsBundle.from(config);
        }

        /** Add a set of implementation specific options to the request. */
        public void addImplementationOptions(Configuration config) {
            for (Option<?> option : config.listOptions()) {
                @SuppressWarnings("unchecked") // Options/values are being copied directly
                        Option<Object> objectOpt = (Option<Object>) option;
                mImplementationOptions.insertOption(objectOpt, config.retrieveOption(objectOpt));
            }
        }

        Map<Key<?>, CaptureRequestParameter<?>> getCharacteristic() {
            return mCaptureRequestParameters;
        }

        boolean isUseRepeatingSurface() {
            return mUseRepeatingSurface;
        }

        public void setUseRepeatingSurface(boolean useRepeatingSurface) {
            mUseRepeatingSurface = useRepeatingSurface;
        }

        /**
         * Builds an instance of a CaptureRequestConfiguration that has all the combined parameters
         * of the CaptureRequestConfiguration that have been added to the Builder.
         */
        public CaptureRequestConfiguration build() {
            return new CaptureRequestConfiguration(
                    new ArrayList<>(mSurfaces),
                    new HashMap<>(mCaptureRequestParameters),
                    OptionsBundle.from(mImplementationOptions),
                    mTemplateType,
                    mCameraCaptureCallback,
                    mUseRepeatingSurface);
        }
    }
}
