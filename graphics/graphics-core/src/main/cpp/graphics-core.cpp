/*
 * Copyright 2021 The Android Open Source Project
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

#define LOG_TAG "ASurfaceControlTest"

#include <jni.h>
#include <string>
#include <poll.h>
#include <unistd.h>
#include <ctime>
#include <android/native_activity.h>
#include <android/surface_control.h>
#include <android/api-level.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <android/sync.h>

#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C"
JNIEXPORT jlong JNICALL
Java_androidx_graphics_surface_JniBindings_00024Companion_nCreate(JNIEnv *env, jobject thiz,
                                                                  jlong surfaceControl,
                                                                  jstring debug_name) {
    if (android_get_device_api_level() >= 29) {
        auto aSurfaceControl = reinterpret_cast<ASurfaceControl *>(surfaceControl);
        auto debugName = env->GetStringUTFChars(debug_name, nullptr);
        return reinterpret_cast<jlong>(ASurfaceControl_create(aSurfaceControl,
                                                              debugName));

    } else {
        return 0;
    }
}

extern "C"
JNIEXPORT jlong JNICALL
Java_androidx_graphics_surface_JniBindings_00024Companion_nCreateFromSurface(JNIEnv *env,
                                                                             jobject thiz,
                                                                             jobject surface,
                                                                             jstring debug_name) {
    if (android_get_device_api_level() >= 29) {
        auto AWindow = ANativeWindow_fromSurface(env, surface);
        auto debugName = env->GetStringUTFChars(debug_name, nullptr);
        auto surfaceControl = reinterpret_cast<jlong>(ASurfaceControl_createFromWindow(AWindow,
                                                                                       debugName));

        ANativeWindow_release(AWindow);
        return surfaceControl;
    } else {
        return 0;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_androidx_graphics_surface_JniBindings_00024Companion_nRelease(JNIEnv *env, jobject thiz,
                                                                   jlong surfaceControl) {
    if (android_get_device_api_level() >= 29) {
        ASurfaceControl_release(reinterpret_cast<ASurfaceControl *>(surfaceControl));
    } else {
        return;
    }
}

extern "C"
JNIEXPORT jlong JNICALL
Java_androidx_graphics_surface_JniBindings_00024Companion_nTransactionCreate(
        JNIEnv *env, jobject thiz) {
    if (android_get_device_api_level() >= 29) {
        return reinterpret_cast<jlong>(ASurfaceTransaction_create());
    } else {
        return 0;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_androidx_graphics_surface_JniBindings_00024Companion_nTransactionDelete(
        JNIEnv *env, jobject thiz,
        jlong surfaceTransaction) {
    if (android_get_device_api_level() >= 29) {
        ASurfaceTransaction_delete(reinterpret_cast<ASurfaceTransaction *>(surfaceTransaction));
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_androidx_graphics_surface_JniBindings_00024Companion_nTransactionApply(
        JNIEnv *env, jobject thiz,
        jlong surfaceTransaction) {
    if (android_get_device_api_level() >= 29) {
        ASurfaceTransaction_apply(reinterpret_cast<ASurfaceTransaction *>(surfaceTransaction));
    }
}

static struct {
    bool CLASS_INFO_INITIALIZED = false;
    jclass clazz{};
    jmethodID onComplete{};

} gTransactionCompletedListenerClassInfo;

static struct {
    bool CLASS_INFO_INITIALIZED = false;
    jclass clazz{};
    jmethodID onCommit{};
} gTransactionCommittedListenerClassInfo;

#define NANO_SECONDS 1000000000LL

int64_t getSystemTime() {
    struct timespec time;
    int result = clock_gettime(CLOCK_MONOTONIC, &time);
    if (result < 0) {
        return -errno;
    }
    return (time.tv_sec * NANO_SECONDS) + time.tv_nsec;
}

/**
 * This wrapper class mimics the one found in CTS tests, specifcally
 * android_view_cts_ASurfaceControlTest.cpp and serves
 * to allow us to set a callback for Transaction onComplete.
 */
class CallbackWrapper {
public:
    virtual ~CallbackWrapper() = default;

    virtual void callback(ASurfaceTransactionStats *stats) = 0;

    static void transactionCallbackThunk(void *context, ASurfaceTransactionStats *stats) {
        CallbackWrapper *listener = reinterpret_cast<CallbackWrapper *>(context);
        listener->callback(stats);
        delete listener;
    }

protected:
    JavaVM *mVm{};
    jobject mCallbackObject{};

    JNIEnv *getEnv() {
        JNIEnv *env;
        mVm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
        return env;
    }
};

class OnCompleteCallbackWrapper : public CallbackWrapper {
public:
    explicit OnCompleteCallbackWrapper(JNIEnv *env, jobject object) {
        env->GetJavaVM(&mVm);
        mCallbackObject = env->NewGlobalRef(object);
    }

    ~OnCompleteCallbackWrapper() override {
        getEnv()->DeleteGlobalRef(mCallbackObject);
    }

    void callback(ASurfaceTransactionStats *stats) override {
        JNIEnv *env = getEnv();
        int64_t latchTime = ASurfaceTransactionStats_getLatchTime(stats);
        uint64_t presentTime = getSystemTime();

        env->CallVoidMethod(mCallbackObject,
                            gTransactionCompletedListenerClassInfo.onComplete,
                            latchTime,
                            presentTime);
    }
};

class OnCommitCallbackWrapper : public CallbackWrapper {
public:
    explicit OnCommitCallbackWrapper(JNIEnv *env, jobject object) {
        env->GetJavaVM(&mVm);
        mCallbackObject = env->NewGlobalRef(object);
    }

    ~OnCommitCallbackWrapper() override {
        getEnv()->DeleteGlobalRef(mCallbackObject);
    }

    void callback(ASurfaceTransactionStats *stats) override {
        JNIEnv *env = getEnv();
        int64_t latchTime = ASurfaceTransactionStats_getLatchTime(stats);
        uint64_t presentTime = getSystemTime();

        env->CallVoidMethod(mCallbackObject,
                            gTransactionCommittedListenerClassInfo.onCommit,
                            latchTime,
                            presentTime);
    }
};

void setupTransactionCompletedListenerClassInfo(JNIEnv *env) {
    //ensure we only ever initialize class info once
    if (!gTransactionCompletedListenerClassInfo.CLASS_INFO_INITIALIZED) {
        //setup transactionCompleteListenerClassInfo for test usage
        jclass transactionCompletedListenerClazz =
                env->FindClass(
                    "androidx/graphics/surface/SurfaceControlCompat$TransactionCompletedListener");
        gTransactionCompletedListenerClassInfo.clazz =
                static_cast<jclass>(env->NewGlobalRef(transactionCompletedListenerClazz));
        gTransactionCompletedListenerClassInfo.onComplete =
                env->GetMethodID(transactionCompletedListenerClazz, "onComplete",
                                 "(JJ)V");

        gTransactionCompletedListenerClassInfo.CLASS_INFO_INITIALIZED = true;
    }

}

void setupTransactionCommittedListenerClassInfo(JNIEnv *env) {
    //ensure we only ever initialize class info once
    if (!gTransactionCommittedListenerClassInfo.CLASS_INFO_INITIALIZED) {
        //setup transactionCommittedListenerClassInfo for test usage
        jclass transactionCommittedListenerClazz =
                env->FindClass(
                    "androidx/graphics/surface/SurfaceControlCompat$TransactionCommittedListener");
        gTransactionCommittedListenerClassInfo.clazz =
                static_cast<jclass>(env->NewGlobalRef(transactionCommittedListenerClazz));
        gTransactionCommittedListenerClassInfo.onCommit =
                env->GetMethodID(transactionCommittedListenerClazz, "onCommit",
                                 "(JJ)V");

        gTransactionCommittedListenerClassInfo.CLASS_INFO_INITIALIZED = true;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_androidx_graphics_surface_JniBindings_00024Companion_nTransactionSetOnComplete(
        JNIEnv *env,
        jobject thiz,
        jlong surfaceTransaction, jobject callback) {
    if (android_get_device_api_level() >= 29) {
        setupTransactionCompletedListenerClassInfo(env);
        void *context = new OnCompleteCallbackWrapper(env, callback);
        ASurfaceTransaction_setOnComplete(
                reinterpret_cast<ASurfaceTransaction *>(surfaceTransaction),
                reinterpret_cast<void *>(context),
                CallbackWrapper::transactionCallbackThunk);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_androidx_graphics_surface_JniBindings_00024Companion_nTransactionSetOnCommit(
        JNIEnv *env, jobject thiz, jlong surfaceTransaction, jobject listener) {
    if (android_get_device_api_level() >= 31) {
        setupTransactionCommittedListenerClassInfo(env);
        void *context = new OnCommitCallbackWrapper(env, listener);
        ASurfaceTransaction_setOnCommit(
                reinterpret_cast<ASurfaceTransaction *>(surfaceTransaction),
                reinterpret_cast<void *>(context),
                CallbackWrapper::transactionCallbackThunk);
    }
}