/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package android.support.v4.speech.tts;

import android.media.AudioManager;
import android.os.Bundle;

/**
 * Synthesis request configuration.
 *
 * This class is immutable, and can only be constructed using
 * {@link RequestConfig.Builder}.
 * @hide
 */
public final class RequestConfig {

    /** Builder for constructing RequestConfig objects. */
    public static final class Builder {
        private VoiceInfo mCurrentVoiceInfo;
        private Bundle mVoiceParams;
        private Bundle mAudioParams;

        Builder(VoiceInfo currentVoiceInfo, Bundle voiceParams, Bundle audioParams) {
            mCurrentVoiceInfo = currentVoiceInfo;
            mVoiceParams = voiceParams;
            mAudioParams = audioParams;
        }

        /**
         * Create new RequestConfig builder.
         */
        public static Builder newBuilder() {
            return new Builder(null, new Bundle(), new Bundle());
        }

        /**
         * Create new RequestConfig builder.
         * @param prototype
         *            Prototype of new RequestConfig. Copies all fields of the
         *            prototype to the constructed object.
         */
        public static Builder newBuilder(RequestConfig prototype) {
            return new Builder(prototype.mCurrentVoiceInfo,
                    (Bundle)prototype.mVoiceParams.clone(),
                    (Bundle)prototype.mAudioParams.clone());
        }

        /** Set voice for request. Will reset voice parameters to the defaults. */
        public Builder setVoice(VoiceInfo voice) {
            mCurrentVoiceInfo = voice;
            mVoiceParams = (Bundle)voice.getParamsWithDefaults().clone();
            return this;
        }

        /**
         * Set request voice parameter.
         *
         * @param paramName
         *            The name of the parameter. It has to be one of the keys
         *            from {@link VoiceInfo#getParamsWithDefaults()}
         * @param value
         *            Value of the parameter. Its type can be one of: Integer, Float,
         *            Boolean, String, VoiceInfo (will be set as a String, result of a call to
         *            the {@link VoiceInfo#getName()}) or byte[]. It has to be of the same type
         *            as the default value from {@link VoiceInfo#getParamsWithDefaults()}
         *            for that parameter.
         * @throws IllegalArgumentException
         *            If paramName is not a valid parameter name or its value is of a wrong
         *            type.
         * @throws IllegalStateException
         *            If no voice is set.
         */
        public Builder setVoiceParam(String paramName, Object value){
            if (mCurrentVoiceInfo == null) {
                throw new IllegalStateException(
                        "Couldn't set voice parameter, no voice is set");
            }
            Object defaultValue = mCurrentVoiceInfo.getParamsWithDefaults().get(paramName);
            if (defaultValue == null) {
                throw new IllegalArgumentException(
                        "Parameter \"" + paramName + "\" is not available in set voice with " +
                                "name: " + mCurrentVoiceInfo.getName());
            }

            // If it's VoiceInfo, get its name
            if (value instanceof VoiceInfo) {
                value = ((VoiceInfo)value).getName();
            }

            // Check type information
            if (!defaultValue.getClass().equals(value.getClass())) {
                throw new IllegalArgumentException(
                        "Parameter \"" + paramName +"\" is of different type. Value passed has " +
                                "type " + value.getClass().getSimpleName() + " but should have " +
                                "type " + defaultValue.getClass().getSimpleName());
            }

            setParam(mVoiceParams, paramName, value);
            return this;
        }

        /**
         * Set request audio parameter.
         *
         * Doesn't requires a set voice.
         *
         * @param paramName
         *            Name of parameter.
         * @param value
         *            Value of parameter. Its type can be one of: Integer, Float, Boolean, String
         *            or byte[].
         */
        public Builder setAudioParam(String paramName, Object value) {
            setParam(mAudioParams, paramName, value);
            return this;
        }

        /**
         * Set the {@link TextToSpeechClient.Params#AUDIO_PARAM_STREAM} audio parameter.
         *
         * @param streamId One of the STREAM_ constants defined in {@link AudioManager}.
         */
        public void setAudioParamStream(int streamId) {
            setAudioParam(TextToSpeechClient.Params.AUDIO_PARAM_STREAM, streamId);
        }

        /**
         * Set the {@link TextToSpeechClient.Params#AUDIO_PARAM_VOLUME} audio parameter.
         *
         * @param volume Float in range of 0.0 to 1.0.
         */
        public void setAudioParamVolume(float volume) {
            setAudioParam(TextToSpeechClient.Params.AUDIO_PARAM_VOLUME, volume);
        }

        /**
         * Set the {@link TextToSpeechClient.Params#AUDIO_PARAM_PAN} audio parameter.
         *
         * @param pan Float in range of -1.0 to +1.0.
         */
        public void setAudioParamPan(float pan) {
            setAudioParam(TextToSpeechClient.Params.AUDIO_PARAM_PAN, pan);
        }

        private void setParam(Bundle bundle, String featureName, Object value) {
            if (value instanceof String) {
                bundle.putString(featureName, (String)value);
            } else if(value instanceof byte[]) {
                bundle.putByteArray(featureName, (byte[])value);
            } else if(value instanceof Integer) {
                bundle.putInt(featureName, (Integer)value);
            } else if(value instanceof Float) {
                bundle.putFloat(featureName, (Float)value);
            } else if(value instanceof Double) {
                bundle.putFloat(featureName, (Float)value);
            } else if(value instanceof Boolean) {
                bundle.putBoolean(featureName, (Boolean)value);
            } else {
                throw new IllegalArgumentException("Illegal type of object");
            }
            return;
        }

        /**
         * Build new RequestConfig instance.
         */
        public RequestConfig build() {
            RequestConfig config =
                    new RequestConfig(mCurrentVoiceInfo, mVoiceParams, mAudioParams);
            return config;
        }
    }

    RequestConfig(VoiceInfo voiceInfo, Bundle voiceParams, Bundle audioParams) {
        mCurrentVoiceInfo = voiceInfo;
        mVoiceParams = voiceParams;
        mAudioParams = audioParams;
    }

    /**
     * Currently set voice.
     */
    private final VoiceInfo mCurrentVoiceInfo;

    /**
     * Voice parameters bundle.
     */
    private final Bundle mVoiceParams;

    /**
     * Audio parameters bundle.
     */
    private final Bundle mAudioParams;

    /**
     * @return Currently set request voice.
     */
    public VoiceInfo getVoice() {
        return mCurrentVoiceInfo;
    }

    /**
     * @return Request audio parameters.
     */
    public Bundle getAudioParams() {
        return mAudioParams;
    }

    /**
     * @return Request voice parameters.
     */
    public Bundle getVoiceParams() {
        return mVoiceParams;
    }

}
