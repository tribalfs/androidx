/*
 * Copyright (C) 2011-2012 The Android Open Source Project
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

#define LOG_TAG "libRS_jni"

#include <stdlib.h>
#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>
#include <math.h>
#include <android/bitmap.h>
#include <android/log.h>
#include "jni.h"
#include <rs.h>
#include <rsEnv.h>

#include "rsDispatch.h"
#include <dlfcn.h>

//#define LOG_API ALOG
#define LOG_API(...)

#define NELEM(m) (sizeof(m) / sizeof((m)[0]))

class AutoJavaStringToUTF8 {
public:
    AutoJavaStringToUTF8(JNIEnv* env, jstring str) : fEnv(env), fJStr(str) {
        fCStr = env->GetStringUTFChars(str, NULL);
        fLength = env->GetStringUTFLength(str);
    }
    ~AutoJavaStringToUTF8() {
        fEnv->ReleaseStringUTFChars(fJStr, fCStr);
    }
    const char* c_str() const { return fCStr; }
    jsize length() const { return fLength; }

private:
    JNIEnv*     fEnv;
    jstring     fJStr;
    const char* fCStr;
    jsize       fLength;
};

class AutoJavaStringArrayToUTF8 {
public:
    AutoJavaStringArrayToUTF8(JNIEnv* env, jobjectArray strings, jsize stringsLength)
    : mEnv(env), mStrings(strings), mStringsLength(stringsLength) {
        mCStrings = NULL;
        mSizeArray = NULL;
        if (stringsLength > 0) {
            mCStrings = (const char **)calloc(stringsLength, sizeof(char *));
            mSizeArray = (size_t*)calloc(stringsLength, sizeof(size_t));
            for (jsize ct = 0; ct < stringsLength; ct ++) {
                jstring s = (jstring)mEnv->GetObjectArrayElement(mStrings, ct);
                mCStrings[ct] = mEnv->GetStringUTFChars(s, NULL);
                mSizeArray[ct] = mEnv->GetStringUTFLength(s);
            }
        }
    }
    ~AutoJavaStringArrayToUTF8() {
        for (jsize ct=0; ct < mStringsLength; ct++) {
            jstring s = (jstring)mEnv->GetObjectArrayElement(mStrings, ct);
            mEnv->ReleaseStringUTFChars(s, mCStrings[ct]);
        }
        free(mCStrings);
        free(mSizeArray);
    }
    const char **c_str() const { return mCStrings; }
    size_t *c_str_len() const { return mSizeArray; }
    jsize length() const { return mStringsLength; }

private:
    JNIEnv      *mEnv;
    jobjectArray mStrings;
    const char **mCStrings;
    size_t      *mSizeArray;
    jsize        mStringsLength;
};


// ---------------------------------------------------------------------------

static dispatchTable dispatchTab;

static bool loadSymbols(void* handle) {

    dispatchTab.AllocationGetType = (AllocationGetTypeFnPtr)dlsym(handle, "rsaAllocationGetType");
    if (dispatchTab.AllocationGetType == NULL) {
        LOG_API("Couldn't initialize dispatchTab.AllocationGetType");
        return false;
    }
    dispatchTab.TypeGetNativeData = (TypeGetNativeDataFnPtr)dlsym(handle, "rsaTypeGetNativeData");
    if (dispatchTab.TypeGetNativeData == NULL) {
        LOG_API("Couldn't initialize dispatchTab.TypeGetNativeData");
        return false;
    }
    dispatchTab.ElementGetNativeData = (ElementGetNativeDataFnPtr)dlsym(handle, "rsaElementGetNativeData");
    if (dispatchTab.ElementGetNativeData == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ElementGetNativeData");
        return false;
    }
    dispatchTab.ElementGetSubElements = (ElementGetSubElementsFnPtr)dlsym(handle, "rsaElementGetSubElements");
    if (dispatchTab.ElementGetSubElements == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ElementGetSubElements");
        return false;
    }
    dispatchTab.DeviceCreate = (DeviceCreateFnPtr)dlsym(handle, "rsDeviceCreate");
    if (dispatchTab.DeviceCreate == NULL) {
        LOG_API("Couldn't initialize dispatchTab.DeviceCreate");
        return false;
    }
    dispatchTab.DeviceDestroy = (DeviceDestroyFnPtr)dlsym(handle, "rsDeviceDestroy");
    if (dispatchTab.DeviceDestroy == NULL) {
        LOG_API("Couldn't initialize dispatchTab.DeviceDestroy");
        return false;
    }
    dispatchTab.DeviceSetConfig = (DeviceSetConfigFnPtr)dlsym(handle, "rsDeviceSetConfig");
    if (dispatchTab.DeviceSetConfig == NULL) {
        LOG_API("Couldn't initialize dispatchTab.DeviceSetConfig");
        return false;
    }
    dispatchTab.ContextCreate = (ContextCreateFnPtr)dlsym(handle, "rsContextCreate");;
    if (dispatchTab.ContextCreate == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ContextCreate");
        return false;
    }
    dispatchTab.GetName = (GetNameFnPtr)dlsym(handle, "rsaGetName");;
    if (dispatchTab.GetName == NULL) {
        LOG_API("Couldn't initialize dispatchTab.GetName");
        return false;
    }
    dispatchTab.ContextDestroy = (ContextDestroyFnPtr)dlsym(handle, "rsContextDestroy");
    if (dispatchTab.ContextDestroy == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ContextDestroy");
        return false;
    }
    dispatchTab.ContextGetMessage = (ContextGetMessageFnPtr)dlsym(handle, "rsContextGetMessage");
    if (dispatchTab.ContextGetMessage == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ContextGetMessage");
        return false;
    }
    dispatchTab.ContextPeekMessage = (ContextPeekMessageFnPtr)dlsym(handle, "rsContextPeekMessage");
    if (dispatchTab.ContextPeekMessage == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ContextPeekMessage");
        return false;
    }
    dispatchTab.ContextSendMessage = (ContextSendMessageFnPtr)dlsym(handle, "rsContextSendMessage");
    if (dispatchTab.ContextSendMessage == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ContextSendMessage");
        return false;
    }
    dispatchTab.ContextInitToClient = (ContextInitToClientFnPtr)dlsym(handle, "rsContextInitToClient");
    if (dispatchTab.ContextInitToClient == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ContextInitToClient");
        return false;
    }
    dispatchTab.ContextDeinitToClient = (ContextDeinitToClientFnPtr)dlsym(handle, "rsContextDeinitToClient");
    if (dispatchTab.ContextDeinitToClient == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ContextDeinitToClient");
        return false;
    }
    dispatchTab.TypeCreate = (TypeCreateFnPtr)dlsym(handle, "rsTypeCreate");
    if (dispatchTab.TypeCreate == NULL) {
        LOG_API("Couldn't initialize dispatchTab.TypeCreate");
        return false;
    }
    dispatchTab.AllocationCreateTyped = (AllocationCreateTypedFnPtr)dlsym(handle, "rsAllocationCreateTyped");
    if (dispatchTab.AllocationCreateTyped == NULL) {
        LOG_API("Couldn't initialize dispatchTab.AllocationCreateTyped");
        return false;
    }
    dispatchTab.AllocationCreateFromBitmap = (AllocationCreateFromBitmapFnPtr)dlsym(handle, "rsAllocationCreateFromBitmap");
    if (dispatchTab.AllocationCreateFromBitmap == NULL) {
        LOG_API("Couldn't initialize dispatchTab.AllocationCreateFromBitmap");
        return false;
    }
    dispatchTab.AllocationCubeCreateFromBitmap = (AllocationCubeCreateFromBitmapFnPtr)dlsym(handle, "rsAllocationCubeCreateFromBitmap");
    if (dispatchTab.AllocationCubeCreateFromBitmap == NULL) {
        LOG_API("Couldn't initialize dispatchTab.AllocationCubeCreateFromBitmap");
        return false;
    }
    dispatchTab.AllocationGetSurface = (AllocationGetSurfaceFnPtr)dlsym(handle, "rsAllocationGetSurface");
    if (dispatchTab.AllocationGetSurface == NULL) {
        LOG_API("Couldn't initialize dispatchTab.AllocationGetSurface");
        return false;
    }
    dispatchTab.AllocationSetSurface = (AllocationSetSurfaceFnPtr)dlsym(handle, "rsAllocationSetSurface");
    if (dispatchTab.AllocationSetSurface == NULL) {
        LOG_API("Couldn't initialize dispatchTab.AllocationSetSurface");
        return false;
    }
    dispatchTab.ContextFinish = (ContextFinishFnPtr)dlsym(handle, "rsContextFinish");
    if (dispatchTab.ContextFinish == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ContextFinish");
        return false;
    }
    dispatchTab.ContextDump = (ContextDumpFnPtr)dlsym(handle, "rsContextDump");
    if (dispatchTab.ContextDump == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ContextDump");
        return false;
    }
    dispatchTab.ContextSetPriority = (ContextSetPriorityFnPtr)dlsym(handle, "rsContextSetPriority");
    if (dispatchTab.ContextSetPriority == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ContextSetPriority");
        return false;
    }
    dispatchTab.AssignName = (AssignNameFnPtr)dlsym(handle, "rsAssignName");
    if (dispatchTab.AssignName == NULL) {
        LOG_API("Couldn't initialize dispatchTab.AssignName");
        return false;
    }
    dispatchTab.ObjDestroy = (ObjDestroyFnPtr)dlsym(handle, "rsObjDestroy");
    if (dispatchTab.ObjDestroy == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ObjDestroy");
        return false;
    }
    dispatchTab.ElementCreate = (ElementCreateFnPtr)dlsym(handle, "rsElementCreate");
    if (dispatchTab.ElementCreate == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ElementCreate");
        return false;
    }
    dispatchTab.ElementCreate2 = (ElementCreate2FnPtr)dlsym(handle, "rsElementCreate2");
    if (dispatchTab.ElementCreate2 == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ElementCreate2");
        return false;
    }
    dispatchTab.AllocationCopyToBitmap = (AllocationCopyToBitmapFnPtr)dlsym(handle, "rsAllocationCopyToBitmap");
    if (dispatchTab.AllocationCopyToBitmap == NULL) {
        LOG_API("Couldn't initialize dispatchTab.AllocationCopyToBitmap");
        return false;
    }
    dispatchTab.Allocation1DData = (Allocation1DDataFnPtr)dlsym(handle, "rsAllocation1DData");
    if (dispatchTab.Allocation1DData == NULL) {
        LOG_API("Couldn't initialize dispatchTab.Allocation1DData");
        return false;
    }
    dispatchTab.Allocation1DElementData = (Allocation1DElementDataFnPtr)dlsym(handle, "rsAllocation1DElementData");
    if (dispatchTab.Allocation1DElementData == NULL) {
        LOG_API("Couldn't initialize dispatchTab.Allocation1DElementData");
        return false;
    }
    dispatchTab.Allocation2DData = (Allocation2DDataFnPtr)dlsym(handle, "rsAllocation2DData");
    if (dispatchTab.Allocation2DData == NULL) {
        LOG_API("Couldn't initialize dispatchTab.Allocation2DData");
        return false;
    }
    dispatchTab.Allocation3DData = (Allocation3DDataFnPtr)dlsym(handle, "rsAllocation3DData");
    if (dispatchTab.Allocation3DData == NULL) {
        LOG_API("Couldn't initialize dispatchTab.Allocation3DData");
        return false;
    }
    dispatchTab.AllocationGenerateMipmaps = (AllocationGenerateMipmapsFnPtr)dlsym(handle, "rsAllocationGenerateMipmaps");
    if (dispatchTab.AllocationGenerateMipmaps == NULL) {
        LOG_API("Couldn't initialize dispatchTab.AllocationGenerateMipmaps");
        return false;
    }
    dispatchTab.AllocationRead = (AllocationReadFnPtr)dlsym(handle, "rsAllocationRead");
    if (dispatchTab.AllocationRead == NULL) {
        LOG_API("Couldn't initialize dispatchTab.AllocationRead");
        return false;
    }
    dispatchTab.Allocation1DRead = (Allocation1DReadFnPtr)dlsym(handle, "rsAllocation1DRead");
    if (dispatchTab.Allocation1DRead == NULL) {
        LOG_API("Couldn't initialize dispatchTab.Allocation1DRead");
        return false;
    }
    dispatchTab.Allocation2DRead = (Allocation2DReadFnPtr)dlsym(handle, "rsAllocation2DRead");
    if (dispatchTab.Allocation2DRead == NULL) {
        LOG_API("Couldn't initialize dispatchTab.Allocation2DRead");
        return false;
    }
    dispatchTab.AllocationSyncAll = (AllocationSyncAllFnPtr)dlsym(handle, "rsAllocationSyncAll");
    if (dispatchTab.AllocationSyncAll == NULL) {
        LOG_API("Couldn't initialize dispatchTab.AllocationSyncAll");
        return false;
    }
    dispatchTab.AllocationResize1D = (AllocationResize1DFnPtr)dlsym(handle, "rsAllocationResize1D");
    if (dispatchTab.AllocationResize1D == NULL) {
        LOG_API("Couldn't initialize dispatchTab.AllocationResize1D");
        return false;
    }
    dispatchTab.AllocationCopy2DRange = (AllocationCopy2DRangeFnPtr)dlsym(handle, "rsAllocationCopy2DRange");
    if (dispatchTab.AllocationCopy2DRange == NULL) {
        LOG_API("Couldn't initialize dispatchTab.AllocationCopy2DRange");
        return false;
    }
    dispatchTab.AllocationCopy3DRange = (AllocationCopy3DRangeFnPtr)dlsym(handle, "rsAllocationCopy3DRange");
    if (dispatchTab.AllocationCopy3DRange == NULL) {
        LOG_API("Couldn't initialize dispatchTab.AllocationCopy3DRange");
        return false;
    }
    dispatchTab.SamplerCreate = (SamplerCreateFnPtr)dlsym(handle, "rsSamplerCreate");
    if (dispatchTab.SamplerCreate == NULL) {
        LOG_API("Couldn't initialize dispatchTab.SamplerCreate");
        return false;
    }
    dispatchTab.ScriptBindAllocation = (ScriptBindAllocationFnPtr)dlsym(handle, "rsScriptBindAllocation");
    if (dispatchTab.ScriptBindAllocation == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ScriptBindAllocation");
        return false;
    }
    dispatchTab.ScriptSetTimeZone = (ScriptSetTimeZoneFnPtr)dlsym(handle, "rsScriptSetTimeZone");
    if (dispatchTab.ScriptSetTimeZone == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ScriptSetTimeZone");
        return false;
    }
    dispatchTab.ScriptInvoke = (ScriptInvokeFnPtr)dlsym(handle, "rsScriptInvoke");
    if (dispatchTab.ScriptInvoke == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ScriptInvoke");
        return false;
    }
    dispatchTab.ScriptInvokeV = (ScriptInvokeVFnPtr)dlsym(handle, "rsScriptInvokeV");
    if (dispatchTab.ScriptInvokeV == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ScriptInvokeV");
        return false;
    }
    dispatchTab.ScriptForEach = (ScriptForEachFnPtr)dlsym(handle, "rsScriptForEach");
    if (dispatchTab.ScriptForEach == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ScriptForEach");
        return false;
    }
    dispatchTab.ScriptSetVarI = (ScriptSetVarIFnPtr)dlsym(handle, "rsScriptSetVarI");
    if (dispatchTab.ScriptSetVarI == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ScriptSetVarI");
        return false;
    }
    dispatchTab.ScriptSetVarObj = (ScriptSetVarObjFnPtr)dlsym(handle, "rsScriptSetVarObj");
    if (dispatchTab.ScriptSetVarObj == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ScriptSetVarObj");
        return false;
    }
    dispatchTab.ScriptSetVarJ = (ScriptSetVarJFnPtr)dlsym(handle, "rsScriptSetVarJ");
    if (dispatchTab.ScriptSetVarJ == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ScriptSetVarJ");
        return false;
    }
    dispatchTab.ScriptSetVarF = (ScriptSetVarFFnPtr)dlsym(handle, "rsScriptSetVarF");
    if (dispatchTab.ScriptSetVarF == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ScriptSetVarF");
        return false;
    }
    dispatchTab.ScriptSetVarD = (ScriptSetVarDFnPtr)dlsym(handle, "rsScriptSetVarD");
    if (dispatchTab.ScriptSetVarD == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ScriptSetVarD");
        return false;
    }
    dispatchTab.ScriptSetVarV = (ScriptSetVarVFnPtr)dlsym(handle, "rsScriptSetVarV");
    if (dispatchTab.ScriptSetVarV == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ScriptSetVarV");
        return false;
    }
    dispatchTab.ScriptGetVarV = (ScriptGetVarVFnPtr)dlsym(handle, "rsScriptGetVarV");
    if (dispatchTab.ScriptGetVarV == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ScriptGetVarV");
        return false;
    }
    dispatchTab.ScriptSetVarVE = (ScriptSetVarVEFnPtr)dlsym(handle, "rsScriptSetVarVE");
    if (dispatchTab.ScriptSetVarVE == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ScriptSetVarVE");
        return false;
    }
    dispatchTab.ScriptCCreate = (ScriptCCreateFnPtr)dlsym(handle, "rsScriptCCreate");
    if (dispatchTab.ScriptCCreate == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ScriptCCreate");
        return false;
    }
    dispatchTab.ScriptIntrinsicCreate = (ScriptIntrinsicCreateFnPtr)dlsym(handle, "rsScriptIntrinsicCreate");
    if (dispatchTab.ScriptIntrinsicCreate == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ScriptIntrinsicCreate");
        return false;
    }
    dispatchTab.ScriptKernelIDCreate = (ScriptKernelIDCreateFnPtr)dlsym(handle, "rsScriptKernelIDCreate");
    if (dispatchTab.ScriptKernelIDCreate == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ScriptKernelIDCreate");
        return false;
    }
    dispatchTab.ScriptFieldIDCreate = (ScriptFieldIDCreateFnPtr)dlsym(handle, "rsScriptFieldIDCreate");
    if (dispatchTab.ScriptFieldIDCreate == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ScriptFieldIDCreate");
        return false;
    }
    dispatchTab.ScriptGroupCreate = (ScriptGroupCreateFnPtr)dlsym(handle, "rsScriptGroupCreate");
    if (dispatchTab.ScriptGroupCreate == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ScriptGroupCreate");
        return false;
    }
    dispatchTab.ScriptGroupSetOutput = (ScriptGroupSetOutputFnPtr)dlsym(handle, "rsScriptGroupSetOutput");
    if (dispatchTab.ScriptGroupSetOutput == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ScriptGroupSetOutput");
        return false;
    }
    dispatchTab.ScriptGroupSetInput = (ScriptGroupSetInputFnPtr)dlsym(handle, "rsScriptGroupSetInput");
    if (dispatchTab.ScriptGroupSetInput == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ScriptGroupSetInput");
        return false;
    }
    dispatchTab.ScriptGroupExecute = (ScriptGroupExecuteFnPtr)dlsym(handle, "rsScriptGroupExecute");
    if (dispatchTab.ScriptGroupExecute == NULL) {
        LOG_API("Couldn't initialize dispatchTab.ScriptGroupExecute");
        return false;
    }
    dispatchTab.AllocationIoSend = (AllocationIoSendFnPtr)dlsym(handle, "rsAllocationIoSend");
    if (dispatchTab.AllocationIoSend == NULL) {
        LOG_API("Couldn't initialize dispatchTab.AllocationIoSend");
        return false;
    }
    dispatchTab.AllocationIoReceive = (AllocationIoReceiveFnPtr)dlsym(handle, "rsAllocationIoReceive");
    if (dispatchTab.AllocationIoReceive == NULL) {
        LOG_API("Couldn't initialize dispatchTab.AllocationIoReceive");
        return false;
    }
    dispatchTab.AllocationGetPointer = (AllocationGetPointerFnPtr)dlsym(handle, "rsAllocationGetPointer");
    if (dispatchTab.AllocationGetPointer == NULL) {
        LOG_API("Couldn't initialize dispatchTab.AllocationGetPointer");
        return false;
    }

    return true;
}


static jboolean nLoadSO(JNIEnv *_env, jobject _this, jboolean useNative) {
    void* handle = NULL;
    if (useNative) {
        handle = dlopen("libRS.so", RTLD_LAZY | RTLD_LOCAL);
    } else {
        handle = dlopen("libRSSupport.so", RTLD_LAZY | RTLD_LOCAL);
    }
    if (handle == NULL) {
        LOG_API("couldn't dlopen %s, %s", filename, dlerror());
        return false;
    }

    if (loadSymbols(handle) == false) {
        LOG_API("%s init failed!", filename);
        return false;
    }
    LOG_API("Successfully loaded %s", filename);
    return true;
}

// ---------------------------------------------------------------------------

static void
nContextFinish(JNIEnv *_env, jobject _this, RsContext con)
{
    LOG_API("nContextFinish, con(%p)", con);
    dispatchTab.ContextFinish(con);
}

static void
nObjDestroy(JNIEnv *_env, jobject _this, RsContext con, jint obj)
{
    LOG_API("nObjDestroy, con(%p) obj(%p)", con, (void *)obj);
    dispatchTab.ObjDestroy(con, (void *)obj);
}

// ---------------------------------------------------------------------------

static jint
nDeviceCreate(JNIEnv *_env, jobject _this)
{
    LOG_API("nDeviceCreate");
    return (jint)dispatchTab.DeviceCreate();
}

static void
nDeviceDestroy(JNIEnv *_env, jobject _this, jint dev)
{
    LOG_API("nDeviceDestroy");
    return dispatchTab.DeviceDestroy((RsDevice)dev);
}

static void
nDeviceSetConfig(JNIEnv *_env, jobject _this, jint dev, jint p, jint value)
{
    LOG_API("nDeviceSetConfig  dev(%p), param(%i), value(%i)", (void *)dev, p, value);
    return dispatchTab.DeviceSetConfig((RsDevice)dev, (RsDeviceParam)p, value);
}

static jint
nContextCreate(JNIEnv *_env, jobject _this, jint dev, jint ver, jint sdkVer, jint ct)
{
    LOG_API("nContextCreate");
    return (jint)dispatchTab.ContextCreate((RsDevice)dev, ver, sdkVer, (RsContextType)ct, 0);
}


static void
nContextSetPriority(JNIEnv *_env, jobject _this, RsContext con, jint p)
{
    LOG_API("ContextSetPriority, con(%p), priority(%i)", con, p);
    dispatchTab.ContextSetPriority(con, p);
}



static void
nContextDestroy(JNIEnv *_env, jobject _this, RsContext con)
{
    LOG_API("nContextDestroy, con(%p)", con);
    dispatchTab.ContextDestroy(con);
}

static void
nContextDump(JNIEnv *_env, jobject _this, RsContext con, jint bits)
{
    LOG_API("nContextDump, con(%p)  bits(%i)", (RsContext)con, bits);
    dispatchTab.ContextDump((RsContext)con, bits);
}


static jstring
nContextGetErrorMessage(JNIEnv *_env, jobject _this, RsContext con)
{
    LOG_API("nContextGetErrorMessage, con(%p)", con);
    char buf[1024];

    size_t receiveLen;
    uint32_t subID;
    int id = dispatchTab.ContextGetMessage(con,
                                 buf, sizeof(buf),
                                 &receiveLen, sizeof(receiveLen),
                                 &subID, sizeof(subID));
    if (!id && receiveLen) {
        //        __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG,
        //            "message receive buffer too small.  %zu", receiveLen);
    }
    return _env->NewStringUTF(buf);
}

static jint
nContextGetUserMessage(JNIEnv *_env, jobject _this, RsContext con, jintArray data)
{
    jint len = _env->GetArrayLength(data);
    LOG_API("nContextGetMessage, con(%p), len(%i)", con, len);
    jint *ptr = _env->GetIntArrayElements(data, NULL);
    size_t receiveLen;
    uint32_t subID;
    int id = dispatchTab.ContextGetMessage(con,
                                 ptr, len * 4,
                                 &receiveLen, sizeof(receiveLen),
                                 &subID, sizeof(subID));
    if (!id && receiveLen) {
        //        __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG,
        //            "message receive buffer too small.  %zu", receiveLen);
    }
    _env->ReleaseIntArrayElements(data, ptr, 0);
    return id;
}

static jint
nContextPeekMessage(JNIEnv *_env, jobject _this, RsContext con, jintArray auxData)
{
    LOG_API("nContextPeekMessage, con(%p)", con);
    jint *auxDataPtr = _env->GetIntArrayElements(auxData, NULL);
    size_t receiveLen;
    uint32_t subID;
    int id = dispatchTab.ContextPeekMessage(con, &receiveLen, sizeof(receiveLen),
                                  &subID, sizeof(subID));
    auxDataPtr[0] = (jint)subID;
    auxDataPtr[1] = (jint)receiveLen;
    _env->ReleaseIntArrayElements(auxData, auxDataPtr, 0);
    return id;
}

static void nContextInitToClient(JNIEnv *_env, jobject _this, RsContext con)
{
    LOG_API("nContextInitToClient, con(%p)", con);
    dispatchTab.ContextInitToClient(con);
}

static void nContextDeinitToClient(JNIEnv *_env, jobject _this, RsContext con)
{
    LOG_API("nContextDeinitToClient, con(%p)", con);
    dispatchTab.ContextDeinitToClient(con);
}

static void
nContextSendMessage(JNIEnv *_env, jobject _this, RsContext con, jint id, jintArray data)
{
    jint *ptr = NULL;
    jint len = 0;
    if (data) {
        len = _env->GetArrayLength(data);
        jint *ptr = _env->GetIntArrayElements(data, NULL);
    }
    LOG_API("nContextSendMessage, con(%p), id(%i), len(%i)", con, id, len);
    dispatchTab.ContextSendMessage(con, id, (const uint8_t *)ptr, len * sizeof(int));
    if (data) {
        _env->ReleaseIntArrayElements(data, ptr, JNI_ABORT);
    }
}



static jint
nElementCreate(JNIEnv *_env, jobject _this, RsContext con, jint type, jint kind, jboolean norm, jint size)
{
    LOG_API("nElementCreate, con(%p), type(%i), kind(%i), norm(%i), size(%i)", con, type, kind, norm, size);
    return (jint)dispatchTab.ElementCreate(con, (RsDataType)type, (RsDataKind)kind, norm, size);
}

static jint
nElementCreate2(JNIEnv *_env, jobject _this, RsContext con,
                jintArray _ids, jobjectArray _names, jintArray _arraySizes)
{
    int fieldCount = _env->GetArrayLength(_ids);
    LOG_API("nElementCreate2, con(%p)", con);

    jint *ids = _env->GetIntArrayElements(_ids, NULL);
    jint *arraySizes = _env->GetIntArrayElements(_arraySizes, NULL);

    AutoJavaStringArrayToUTF8 names(_env, _names, fieldCount);

    const char **nameArray = names.c_str();
    size_t *sizeArray = names.c_str_len();

    jint id = (jint)dispatchTab.ElementCreate2(con,
                                     (RsElement *)ids, fieldCount,
                                     nameArray, fieldCount * sizeof(size_t),  sizeArray,
                                     (const uint32_t *)arraySizes, fieldCount);

    _env->ReleaseIntArrayElements(_ids, ids, JNI_ABORT);
    _env->ReleaseIntArrayElements(_arraySizes, arraySizes, JNI_ABORT);
    return (jint)id;
}



static void
nElementGetSubElements(JNIEnv *_env, jobject _this, RsContext con, jint id,
                       jintArray _IDs,
                       jobjectArray _names,
                       jintArray _arraySizes)
{
    int dataSize = _env->GetArrayLength(_IDs);
    LOG_API("nElementGetSubElements, con(%p)", con);

    uint32_t *ids = (uint32_t *)malloc((uint32_t)dataSize * sizeof(uint32_t));
    const char **names = (const char **)malloc((uint32_t)dataSize * sizeof(const char *));
    uint32_t *arraySizes = (uint32_t *)malloc((uint32_t)dataSize * sizeof(uint32_t));

    dispatchTab.ElementGetSubElements(con, (RsElement)id, ids, names, arraySizes, (uint32_t)dataSize);

    for(jint i = 0; i < dataSize; i++) {
        _env->SetObjectArrayElement(_names, i, _env->NewStringUTF(names[i]));
        _env->SetIntArrayRegion(_IDs, i, 1, (const jint*)&ids[i]);
        _env->SetIntArrayRegion(_arraySizes, i, 1, (const jint*)&arraySizes[i]);
    }

    free(ids);
    free(names);
    free(arraySizes);
}

// -----------------------------------

static int
nTypeCreate(JNIEnv *_env, jobject _this, RsContext con, RsElement eid,
            jint dimx, jint dimy, jint dimz, jboolean mips, jboolean faces, jint yuv)
{
    LOG_API("nTypeCreate, con(%p) eid(%p), x(%i), y(%i), z(%i), mips(%i), faces(%i), yuv(%i)",
            con, eid, dimx, dimy, dimz, mips, faces, yuv);

    jint id = (jint)dispatchTab.TypeCreate(con, (RsElement)eid, dimx, dimy, dimz, mips, faces, yuv);
    return (jint)id;
}

// -----------------------------------

static jint
nAllocationCreateTyped(JNIEnv *_env, jobject _this, RsContext con, jint type, jint mips, jint usage, jint pointer)
{
    LOG_API("nAllocationCreateTyped, con(%p), type(%p), mip(%i), usage(%i), ptr(%p)", con, (RsElement)type, mips, usage, (void *)pointer);
    return (jint) dispatchTab.AllocationCreateTyped(con, (RsType)type, (RsAllocationMipmapControl)mips, (uint32_t)usage, (uint32_t)pointer);
}

static void
nAllocationSyncAll(JNIEnv *_env, jobject _this, RsContext con, jint a, jint bits)
{
    LOG_API("nAllocationSyncAll, con(%p), a(%p), bits(0x%08x)", con, (RsAllocation)a, bits);
    dispatchTab.AllocationSyncAll(con, (RsAllocation)a, (RsAllocationUsageType)bits);
}

static void
nAllocationGenerateMipmaps(JNIEnv *_env, jobject _this, RsContext con, jint alloc)
{
    LOG_API("nAllocationGenerateMipmaps, con(%p), a(%p)", con, (RsAllocation)alloc);
    dispatchTab.AllocationGenerateMipmaps(con, (RsAllocation)alloc);
}

static size_t GetBitmapSize(JNIEnv *env, jobject jbitmap) {
    AndroidBitmapInfo info;
    memset(&info, 0, sizeof(info));
    AndroidBitmap_getInfo(env, jbitmap, &info);
    size_t s = info.width * info.height;
    switch (info.format) {
        case ANDROID_BITMAP_FORMAT_RGBA_8888: s *= 4; break;
        case ANDROID_BITMAP_FORMAT_RGB_565: s *= 2; break;
        case ANDROID_BITMAP_FORMAT_RGBA_4444: s *= 2; break;
    }
    return s;
}

static int
nAllocationCreateFromBitmap(JNIEnv *_env, jobject _this, RsContext con, jint type, jint mip, jobject jbitmap, jint usage)
{
    jint id = 0;
    void *pixels = NULL;
    AndroidBitmap_lockPixels(_env, jbitmap, &pixels);

    if (pixels != NULL) {
        id = (jint)dispatchTab.AllocationCreateFromBitmap(con,
                                                (RsType)type, (RsAllocationMipmapControl)mip,
                                                pixels, GetBitmapSize(_env, jbitmap), usage);
        AndroidBitmap_unlockPixels(_env, jbitmap);
    }
    return id;
}

static int
nAllocationCreateBitmapBackedAllocation(JNIEnv *_env, jobject _this, RsContext con, jint type, jint mip, jobject jbitmap, jint usage)
{
    jint id = 0;
    void *pixels = NULL;
    AndroidBitmap_lockPixels(_env, jbitmap, &pixels);

    if (pixels != NULL) {
        id = (jint)dispatchTab.AllocationCreateTyped(con,
                                          (RsType)type, (RsAllocationMipmapControl)mip,
                                          (uint32_t)usage, (uintptr_t)pixels);
        AndroidBitmap_unlockPixels(_env, jbitmap);
    }
    return id;
}

static int
nAllocationCubeCreateFromBitmap(JNIEnv *_env, jobject _this, RsContext con, jint type, jint mip, jobject jbitmap, jint usage)
{
    void *pixels = NULL;
    AndroidBitmap_lockPixels(_env, jbitmap, &pixels);

    jint id = 0;
    if (pixels != NULL) {
        id = (jint)dispatchTab.AllocationCubeCreateFromBitmap(con,
                                                    (RsType)type, (RsAllocationMipmapControl)mip,
                                                    pixels, GetBitmapSize(_env, jbitmap), usage);
        AndroidBitmap_unlockPixels(_env, jbitmap);
    }
    return id;
}

static void
nAllocationCopyFromBitmap(JNIEnv *_env, jobject _this, RsContext con, jint alloc, jobject jbitmap)
{
    AndroidBitmapInfo info;
    memset(&info, 0, sizeof(info));
    AndroidBitmap_getInfo(_env, jbitmap, &info);

    void *pixels = NULL;
    AndroidBitmap_lockPixels(_env, jbitmap, &pixels);

    if (pixels != NULL) {
        dispatchTab.Allocation2DData(con, (RsAllocation)alloc, 0, 0,
                           0, RS_ALLOCATION_CUBEMAP_FACE_POSITIVE_X,
                           info.width, info.height, pixels, GetBitmapSize(_env, jbitmap), 0);
        AndroidBitmap_unlockPixels(_env, jbitmap);
    }
}

static void
nAllocationCopyToBitmap(JNIEnv *_env, jobject _this, RsContext con, jint alloc, jobject jbitmap)
{
    AndroidBitmapInfo info;
    memset(&info, 0, sizeof(info));
    AndroidBitmap_getInfo(_env, jbitmap, &info);

    void *pixels = NULL;
    AndroidBitmap_lockPixels(_env, jbitmap, &pixels);

    if (pixels != NULL) {
        dispatchTab.AllocationCopyToBitmap(con, (RsAllocation)alloc, pixels, GetBitmapSize(_env, jbitmap));
        AndroidBitmap_unlockPixels(_env, jbitmap);
    }
    //bitmap.notifyPixelsChanged();
}


static void
nAllocationData1D_i(JNIEnv *_env, jobject _this, RsContext con, jint alloc, jint offset, jint lod, jint count, jintArray data, int sizeBytes)
{
    jint len = _env->GetArrayLength(data);
    LOG_API("nAllocation1DData_i, con(%p), adapter(%p), offset(%i), count(%i), len(%i), sizeBytes(%i)", con, (RsAllocation)alloc, offset, count, len, sizeBytes);
    jint *ptr = _env->GetIntArrayElements(data, NULL);
    dispatchTab.Allocation1DData(con, (RsAllocation)alloc, offset, lod, count, ptr, sizeBytes);
    _env->ReleaseIntArrayElements(data, ptr, JNI_ABORT);
}

static void
nAllocationData1D_s(JNIEnv *_env, jobject _this, RsContext con, jint alloc, jint offset, jint lod, jint count, jshortArray data, int sizeBytes)
{
    jint len = _env->GetArrayLength(data);
    LOG_API("nAllocation1DData_s, con(%p), adapter(%p), offset(%i), count(%i), len(%i), sizeBytes(%i)", con, (RsAllocation)alloc, offset, count, len, sizeBytes);
    jshort *ptr = _env->GetShortArrayElements(data, NULL);
    dispatchTab.Allocation1DData(con, (RsAllocation)alloc, offset, lod, count, ptr, sizeBytes);
    _env->ReleaseShortArrayElements(data, ptr, JNI_ABORT);
}

static void
nAllocationData1D_b(JNIEnv *_env, jobject _this, RsContext con, jint alloc, jint offset, jint lod, jint count, jbyteArray data, int sizeBytes)
{
    jint len = _env->GetArrayLength(data);
    LOG_API("nAllocation1DData_b, con(%p), adapter(%p), offset(%i), count(%i), len(%i), sizeBytes(%i)", con, (RsAllocation)alloc, offset, count, len, sizeBytes);
    jbyte *ptr = _env->GetByteArrayElements(data, NULL);
    dispatchTab.Allocation1DData(con, (RsAllocation)alloc, offset, lod, count, ptr, sizeBytes);
    _env->ReleaseByteArrayElements(data, ptr, JNI_ABORT);
}

static void
nAllocationData1D_f(JNIEnv *_env, jobject _this, RsContext con, jint alloc, jint offset, jint lod, jint count, jfloatArray data, int sizeBytes)
{
    jint len = _env->GetArrayLength(data);
    LOG_API("nAllocation1DData_f, con(%p), adapter(%p), offset(%i), count(%i), len(%i), sizeBytes(%i)", con, (RsAllocation)alloc, offset, count, len, sizeBytes);
    jfloat *ptr = _env->GetFloatArrayElements(data, NULL);
    dispatchTab.Allocation1DData(con, (RsAllocation)alloc, offset, lod, count, ptr, sizeBytes);
    _env->ReleaseFloatArrayElements(data, ptr, JNI_ABORT);
}

static void
//    native void rsnAllocationElementData1D(int con, int id, int xoff, int compIdx, byte[] d, int sizeBytes);
nAllocationElementData1D(JNIEnv *_env, jobject _this, RsContext con, jint alloc, jint offset, jint lod, jint compIdx, jbyteArray data, int sizeBytes)
{
    jint len = _env->GetArrayLength(data);
    LOG_API("nAllocationElementData1D, con(%p), alloc(%p), offset(%i), comp(%i), len(%i), sizeBytes(%i)", con, (RsAllocation)alloc, offset, compIdx, len, sizeBytes);
    jbyte *ptr = _env->GetByteArrayElements(data, NULL);
    dispatchTab.Allocation1DElementData(con, (RsAllocation)alloc, offset, lod, ptr, sizeBytes, compIdx);
    _env->ReleaseByteArrayElements(data, ptr, JNI_ABORT);
}

static void
nAllocationData2D_s(JNIEnv *_env, jobject _this, RsContext con, jint alloc, jint xoff, jint yoff, jint lod, jint face,
                    jint w, jint h, jshortArray data, int sizeBytes)
{
    jint len = _env->GetArrayLength(data);
    LOG_API("nAllocation2DData_s, con(%p), adapter(%p), xoff(%i), yoff(%i), w(%i), h(%i), len(%i)", con, (RsAllocation)alloc, xoff, yoff, w, h, len);
    jshort *ptr = _env->GetShortArrayElements(data, NULL);
    dispatchTab.Allocation2DData(con, (RsAllocation)alloc, xoff, yoff, lod, (RsAllocationCubemapFace)face, w, h, ptr, sizeBytes, 0);
    _env->ReleaseShortArrayElements(data, ptr, JNI_ABORT);
}

static void
nAllocationData2D_b(JNIEnv *_env, jobject _this, RsContext con, jint alloc, jint xoff, jint yoff, jint lod, jint face,
                    jint w, jint h, jbyteArray data, int sizeBytes)
{
    jint len = _env->GetArrayLength(data);
    LOG_API("nAllocation2DData_b, con(%p), adapter(%p), xoff(%i), yoff(%i), w(%i), h(%i), len(%i)", con, (RsAllocation)alloc, xoff, yoff, w, h, len);
    jbyte *ptr = _env->GetByteArrayElements(data, NULL);
    dispatchTab.Allocation2DData(con, (RsAllocation)alloc, xoff, yoff, lod, (RsAllocationCubemapFace)face, w, h, ptr, sizeBytes, 0);
    _env->ReleaseByteArrayElements(data, ptr, JNI_ABORT);
}

static void
nAllocationData2D_i(JNIEnv *_env, jobject _this, RsContext con, jint alloc, jint xoff, jint yoff, jint lod, jint face,
                    jint w, jint h, jintArray data, int sizeBytes)
{
    jint len = _env->GetArrayLength(data);
    LOG_API("nAllocation2DData_i, con(%p), adapter(%p), xoff(%i), yoff(%i), w(%i), h(%i), len(%i)", con, (RsAllocation)alloc, xoff, yoff, w, h, len);
    jint *ptr = _env->GetIntArrayElements(data, NULL);
    dispatchTab.Allocation2DData(con, (RsAllocation)alloc, xoff, yoff, lod, (RsAllocationCubemapFace)face, w, h, ptr, sizeBytes, 0);
    _env->ReleaseIntArrayElements(data, ptr, JNI_ABORT);
}

static void
nAllocationData2D_f(JNIEnv *_env, jobject _this, RsContext con, jint alloc, jint xoff, jint yoff, jint lod, jint face,
                    jint w, jint h, jfloatArray data, int sizeBytes)
{
    jint len = _env->GetArrayLength(data);
    LOG_API("nAllocation2DData_i, con(%p), adapter(%p), xoff(%i), yoff(%i), w(%i), h(%i), len(%i)", con, (RsAllocation)alloc, xoff, yoff, w, h, len);
    jfloat *ptr = _env->GetFloatArrayElements(data, NULL);
    dispatchTab.Allocation2DData(con, (RsAllocation)alloc, xoff, yoff, lod, (RsAllocationCubemapFace)face, w, h, ptr, sizeBytes, 0);
    _env->ReleaseFloatArrayElements(data, ptr, JNI_ABORT);
}

static void
nAllocationData2D_alloc(JNIEnv *_env, jobject _this, RsContext con,
                        jint dstAlloc, jint dstXoff, jint dstYoff,
                        jint dstMip, jint dstFace,
                        jint width, jint height,
                        jint srcAlloc, jint srcXoff, jint srcYoff,
                        jint srcMip, jint srcFace)
{
    LOG_API("nAllocation2DData_s, con(%p), dstAlloc(%p), dstXoff(%i), dstYoff(%i),"
            " dstMip(%i), dstFace(%i), width(%i), height(%i),"
            " srcAlloc(%p), srcXoff(%i), srcYoff(%i), srcMip(%i), srcFace(%i)",
            con, (RsAllocation)dstAlloc, dstXoff, dstYoff, dstMip, dstFace,
            width, height, (RsAllocation)srcAlloc, srcXoff, srcYoff, srcMip, srcFace);

    dispatchTab.AllocationCopy2DRange(con,
                            (RsAllocation)dstAlloc,
                            dstXoff, dstYoff,
                            dstMip, dstFace,
                            width, height,
                            (RsAllocation)srcAlloc,
                            srcXoff, srcYoff,
                            srcMip, srcFace);
}

static void
nAllocationData3D_s(JNIEnv *_env, jobject _this, RsContext con, jint alloc, jint xoff, jint yoff, jint zoff, jint lod,
                    jint w, jint h, jint d, jshortArray data, int sizeBytes)
{
    jint len = _env->GetArrayLength(data);
    LOG_API("nAllocation3DData_s, con(%p), adapter(%p), xoff(%i), yoff(%i), w(%i), h(%i), len(%i)", con, (RsAllocation)alloc, xoff, yoff, zoff, w, h, d, len);
    jshort *ptr = _env->GetShortArrayElements(data, NULL);
    dispatchTab.Allocation3DData(con, (RsAllocation)alloc, xoff, yoff, zoff, lod, w, h, d, ptr, sizeBytes, 0);
    _env->ReleaseShortArrayElements(data, ptr, JNI_ABORT);
}

static void
nAllocationData3D_b(JNIEnv *_env, jobject _this, RsContext con, jint alloc, jint xoff, jint yoff, jint zoff, jint lod,
                    jint w, jint h, jint d, jbyteArray data, int sizeBytes)
{
    jint len = _env->GetArrayLength(data);
    LOG_API("nAllocation3DData_b, con(%p), adapter(%p), xoff(%i), yoff(%i), w(%i), h(%i), len(%i)", con, (RsAllocation)alloc, xoff, yoff, zoff, w, h, d, len);
    jbyte *ptr = _env->GetByteArrayElements(data, NULL);
    dispatchTab.Allocation3DData(con, (RsAllocation)alloc, xoff, yoff, zoff, lod, w, h, d, ptr, sizeBytes, 0);
    _env->ReleaseByteArrayElements(data, ptr, JNI_ABORT);
}

static void
nAllocationData3D_i(JNIEnv *_env, jobject _this, RsContext con, jint alloc, jint xoff, jint yoff, jint zoff, jint lod,
                    jint w, jint h, jint d, jintArray data, int sizeBytes)
{
    jint len = _env->GetArrayLength(data);
    LOG_API("nAllocation3DData_i, con(%p), adapter(%p), xoff(%i), yoff(%i), w(%i), h(%i), len(%i)", con, (RsAllocation)alloc, xoff, yoff, zoff, w, h, d, len);
    jint *ptr = _env->GetIntArrayElements(data, NULL);
    dispatchTab.Allocation3DData(con, (RsAllocation)alloc, xoff, yoff, zoff, lod, w, h, d, ptr, sizeBytes, 0);
    _env->ReleaseIntArrayElements(data, ptr, JNI_ABORT);
}

static void
nAllocationData3D_f(JNIEnv *_env, jobject _this, RsContext con, jint alloc, jint xoff, jint yoff, jint zoff, jint lod,
                    jint w, jint h, jint d, jfloatArray data, int sizeBytes)
{
    jint len = _env->GetArrayLength(data);
    LOG_API("nAllocation3DData_f, con(%p), adapter(%p), xoff(%i), yoff(%i), w(%i), h(%i), len(%i)", con, (RsAllocation)alloc, xoff, yoff, zoff, w, h, d, len);
    jfloat *ptr = _env->GetFloatArrayElements(data, NULL);
    dispatchTab.Allocation3DData(con, (RsAllocation)alloc, xoff, yoff, zoff, lod, w, h, d, ptr, sizeBytes, 0);
    _env->ReleaseFloatArrayElements(data, ptr, JNI_ABORT);
}

static void
nAllocationData3D_alloc(JNIEnv *_env, jobject _this, RsContext con,
                        jint dstAlloc, jint dstXoff, jint dstYoff, jint dstZoff,
                        jint dstMip,
                        jint width, jint height, jint depth,
                        jint srcAlloc, jint srcXoff, jint srcYoff, jint srcZoff,
                        jint srcMip)
{
    LOG_API("nAllocationData3D_alloc, con(%p), dstAlloc(%p), dstXoff(%i), dstYoff(%i),"
            " dstMip(%i), width(%i), height(%i),"
            " srcAlloc(%p), srcXoff(%i), srcYoff(%i), srcMip(%i)",
            con, (RsAllocation)dstAlloc, dstXoff, dstYoff, dstMip, dstFace,
            width, height, (RsAllocation)srcAlloc, srcXoff, srcYoff, srcMip, srcFace);

    dispatchTab.AllocationCopy3DRange(con,
                            (RsAllocation)dstAlloc,
                            dstXoff, dstYoff, dstZoff, dstMip,
                            width, height, depth,
                            (RsAllocation)srcAlloc,
                            srcXoff, srcYoff, srcZoff, srcMip);
}

static void
nAllocationRead_i(JNIEnv *_env, jobject _this, RsContext con, jint alloc, jintArray data)
{
    jint len = _env->GetArrayLength(data);
    LOG_API("nAllocationRead_i, con(%p), alloc(%p), len(%i)", con, (RsAllocation)alloc, len);
    jint *ptr = _env->GetIntArrayElements(data, NULL);
    jsize length = _env->GetArrayLength(data);
    dispatchTab.AllocationRead(con, (RsAllocation)alloc, ptr, length * sizeof(int));
    _env->ReleaseIntArrayElements(data, ptr, 0);
}

static void
nAllocationRead_s(JNIEnv *_env, jobject _this, RsContext con, jint alloc, jshortArray data)
{
    jint len = _env->GetArrayLength(data);
    LOG_API("nAllocationRead_i, con(%p), alloc(%p), len(%i)", con, (RsAllocation)alloc, len);
    jshort *ptr = _env->GetShortArrayElements(data, NULL);
    jsize length = _env->GetArrayLength(data);
    dispatchTab.AllocationRead(con, (RsAllocation)alloc, ptr, length * sizeof(short));
    _env->ReleaseShortArrayElements(data, ptr, 0);
}

static void
nAllocationRead_b(JNIEnv *_env, jobject _this, RsContext con, jint alloc, jbyteArray data)
{
    jint len = _env->GetArrayLength(data);
    LOG_API("nAllocationRead_i, con(%p), alloc(%p), len(%i)", con, (RsAllocation)alloc, len);
    jbyte *ptr = _env->GetByteArrayElements(data, NULL);
    jsize length = _env->GetArrayLength(data);
    dispatchTab.AllocationRead(con, (RsAllocation)alloc, ptr, length * sizeof(char));
    _env->ReleaseByteArrayElements(data, ptr, 0);
}

static void
nAllocationRead_f(JNIEnv *_env, jobject _this, RsContext con, jint alloc, jfloatArray data)
{
    jint len = _env->GetArrayLength(data);
    LOG_API("nAllocationRead_f, con(%p), alloc(%p), len(%i)", con, (RsAllocation)alloc, len);
    jfloat *ptr = _env->GetFloatArrayElements(data, NULL);
    jsize length = _env->GetArrayLength(data);
    dispatchTab.AllocationRead(con, (RsAllocation)alloc, ptr, length * sizeof(float));
    _env->ReleaseFloatArrayElements(data, ptr, 0);
}

static jint
nAllocationGetType(JNIEnv *_env, jobject _this, RsContext con, jint a)
{
    LOG_API("nAllocationGetType, con(%p), a(%p)", con, (RsAllocation)a);
    return (jint) dispatchTab.AllocationGetType(con, (RsAllocation)a);
}

static void
nAllocationResize1D(JNIEnv *_env, jobject _this, RsContext con, jint alloc, jint dimX)
{
    LOG_API("nAllocationResize1D, con(%p), alloc(%p), sizeX(%i)", con, (RsAllocation)alloc, dimX);
    dispatchTab.AllocationResize1D(con, (RsAllocation)alloc, dimX);
}

// -----------------------------------

static void
nScriptBindAllocation(JNIEnv *_env, jobject _this, RsContext con, jint script, jint alloc, jint slot)
{
    LOG_API("nScriptBindAllocation, con(%p), script(%p), alloc(%p), slot(%i)", con, (RsScript)script, (RsAllocation)alloc, slot);
    dispatchTab.ScriptBindAllocation(con, (RsScript)script, (RsAllocation)alloc, slot);
}

static void
nScriptSetVarI(JNIEnv *_env, jobject _this, RsContext con, jint script, jint slot, jint val)
{
    LOG_API("nScriptSetVarI, con(%p), s(%p), slot(%i), val(%i)", con, (void *)script, slot, val);
    dispatchTab.ScriptSetVarI(con, (RsScript)script, slot, val);
}

static void
nScriptSetVarObj(JNIEnv *_env, jobject _this, RsContext con, jint script, jint slot, jint val)
{
    LOG_API("nScriptSetVarObj, con(%p), s(%p), slot(%i), val(%i)", con, (void *)script, slot, val);
    dispatchTab.ScriptSetVarObj(con, (RsScript)script, slot, (RsObjectBase)val);
}

static void
nScriptSetVarJ(JNIEnv *_env, jobject _this, RsContext con, jint script, jint slot, jlong val)
{
    LOG_API("nScriptSetVarJ, con(%p), s(%p), slot(%i), val(%lli)", con, (void *)script, slot, val);
    dispatchTab.ScriptSetVarJ(con, (RsScript)script, slot, val);
}

static void
nScriptSetVarF(JNIEnv *_env, jobject _this, RsContext con, jint script, jint slot, float val)
{
    LOG_API("nScriptSetVarF, con(%p), s(%p), slot(%i), val(%f)", con, (void *)script, slot, val);
    dispatchTab.ScriptSetVarF(con, (RsScript)script, slot, val);
}

static void
nScriptSetVarD(JNIEnv *_env, jobject _this, RsContext con, jint script, jint slot, double val)
{
    LOG_API("nScriptSetVarD, con(%p), s(%p), slot(%i), val(%lf)", con, (void *)script, slot, val);
    dispatchTab.ScriptSetVarD(con, (RsScript)script, slot, val);
}

static void
nScriptSetVarV(JNIEnv *_env, jobject _this, RsContext con, jint script, jint slot, jbyteArray data)
{
    LOG_API("nScriptSetVarV, con(%p), s(%p), slot(%i)", con, (void *)script, slot);
    jint len = _env->GetArrayLength(data);
    jbyte *ptr = _env->GetByteArrayElements(data, NULL);
    dispatchTab.ScriptSetVarV(con, (RsScript)script, slot, ptr, len);
    _env->ReleaseByteArrayElements(data, ptr, JNI_ABORT);
}

static void
nScriptSetVarVE(JNIEnv *_env, jobject _this, RsContext con, jint script, jint slot, jbyteArray data, jint elem, jintArray dims)
{
    LOG_API("nScriptSetVarVE, con(%p), s(%p), slot(%i)", con, (void *)script, slot);
    jint len = _env->GetArrayLength(data);
    jbyte *ptr = _env->GetByteArrayElements(data, NULL);
    jint dimsLen = _env->GetArrayLength(dims) * sizeof(int);
    jint *dimsPtr = _env->GetIntArrayElements(dims, NULL);
    dispatchTab.ScriptSetVarVE(con, (RsScript)script, slot, ptr, len, (RsElement)elem,
                     (const uint32_t *)dimsPtr, dimsLen);
    _env->ReleaseByteArrayElements(data, ptr, JNI_ABORT);
    _env->ReleaseIntArrayElements(dims, dimsPtr, JNI_ABORT);
}


static void
nScriptSetTimeZone(JNIEnv *_env, jobject _this, RsContext con, jint script, jbyteArray timeZone)
{
    LOG_API("nScriptCSetTimeZone, con(%p), s(%p), timeZone(%s)", con, (void *)script, (const char *)timeZone);

    jint length = _env->GetArrayLength(timeZone);
    jbyte* timeZone_ptr;
    timeZone_ptr = (jbyte *) _env->GetPrimitiveArrayCritical(timeZone, (jboolean *)0);

    dispatchTab.ScriptSetTimeZone(con, (RsScript)script, (const char *)timeZone_ptr, length);

    if (timeZone_ptr) {
        _env->ReleasePrimitiveArrayCritical(timeZone, timeZone_ptr, 0);
    }
}

static void
nScriptInvoke(JNIEnv *_env, jobject _this, RsContext con, jint obj, jint slot)
{
    LOG_API("nScriptInvoke, con(%p), script(%p)", con, (void *)obj);
    dispatchTab.ScriptInvoke(con, (RsScript)obj, slot);
}

static void
nScriptInvokeV(JNIEnv *_env, jobject _this, RsContext con, jint script, jint slot, jbyteArray data)
{
    LOG_API("nScriptInvokeV, con(%p), s(%p), slot(%i)", con, (void *)script, slot);
    jint len = _env->GetArrayLength(data);
    jbyte *ptr = _env->GetByteArrayElements(data, NULL);
    dispatchTab.ScriptInvokeV(con, (RsScript)script, slot, ptr, len);
    _env->ReleaseByteArrayElements(data, ptr, JNI_ABORT);
}

static void
nScriptForEach(JNIEnv *_env, jobject _this, RsContext con,
               jint script, jint slot, jint ain, jint aout)
{
    LOG_API("nScriptForEach, con(%p), s(%p), slot(%i)", con, (void *)script, slot);
    dispatchTab.ScriptForEach(con, (RsScript)script, slot, (RsAllocation)ain, (RsAllocation)aout, NULL, 0, NULL, 0);
}
static void
nScriptForEachV(JNIEnv *_env, jobject _this, RsContext con,
                jint script, jint slot, jint ain, jint aout, jbyteArray params)
{
    LOG_API("nScriptForEach, con(%p), s(%p), slot(%i)", con, (void *)script, slot);
    jint len = _env->GetArrayLength(params);
    jbyte *ptr = _env->GetByteArrayElements(params, NULL);
    dispatchTab.ScriptForEach(con, (RsScript)script, slot, (RsAllocation)ain, (RsAllocation)aout, ptr, len, NULL, 0);
    _env->ReleaseByteArrayElements(params, ptr, JNI_ABORT);
}

static void
nScriptForEachClipped(JNIEnv *_env, jobject _this, RsContext con,
                      jint script, jint slot, jint ain, jint aout,
                      jint xstart, jint xend,
                      jint ystart, jint yend, jint zstart, jint zend)
{
    LOG_API("nScriptForEachClipped, con(%p), s(%p), slot(%i)", con, (void *)script, slot);
    RsScriptCall sc;
    sc.xStart = xstart;
    sc.xEnd = xend;
    sc.yStart = ystart;
    sc.yEnd = yend;
    sc.zStart = zstart;
    sc.zEnd = zend;
    sc.strategy = RS_FOR_EACH_STRATEGY_DONT_CARE;
    sc.arrayStart = 0;
    sc.arrayEnd = 0;
    dispatchTab.ScriptForEach(con, (RsScript)script, slot, (RsAllocation)ain, (RsAllocation)aout, NULL, 0, &sc, sizeof(sc));
}

static void
nScriptForEachClippedV(JNIEnv *_env, jobject _this, RsContext con,
                       jint script, jint slot, jint ain, jint aout,
                       jbyteArray params, jint xstart, jint xend,
                       jint ystart, jint yend, jint zstart, jint zend)
{
    LOG_API("nScriptForEachClipped, con(%p), s(%p), slot(%i)", con, (void *)script, slot);
    jint len = _env->GetArrayLength(params);
    jbyte *ptr = _env->GetByteArrayElements(params, NULL);
    RsScriptCall sc;
    sc.xStart = xstart;
    sc.xEnd = xend;
    sc.yStart = ystart;
    sc.yEnd = yend;
    sc.zStart = zstart;
    sc.zEnd = zend;
    sc.strategy = RS_FOR_EACH_STRATEGY_DONT_CARE;
    sc.arrayStart = 0;
    sc.arrayEnd = 0;
    dispatchTab.ScriptForEach(con, (RsScript)script, slot, (RsAllocation)ain, (RsAllocation)aout, ptr, len, &sc, sizeof(sc));
    _env->ReleaseByteArrayElements(params, ptr, JNI_ABORT);
}

// -----------------------------------

static jint
nScriptCCreate(JNIEnv *_env, jobject _this, RsContext con,
               jstring resName, jstring cacheDir,
               jbyteArray scriptRef, jint length)
{
    LOG_API("nScriptCCreate, con(%p)", con);

    AutoJavaStringToUTF8 resNameUTF(_env, resName);
    AutoJavaStringToUTF8 cacheDirUTF(_env, cacheDir);
    jint ret = 0;
    jbyte* script_ptr = NULL;
    jint _exception = 0;
    jint remaining;
    if (!scriptRef) {
        _exception = 1;
        //jniThrowException(_env, "java/lang/IllegalArgumentException", "script == null");
        goto exit;
    }
    if (length < 0) {
        _exception = 1;
        //jniThrowException(_env, "java/lang/IllegalArgumentException", "length < 0");
        goto exit;
    }
    remaining = _env->GetArrayLength(scriptRef);
    if (remaining < length) {
        _exception = 1;
        //jniThrowException(_env, "java/lang/IllegalArgumentException",
        //        "length > script.length - offset");
        goto exit;
    }
    script_ptr = (jbyte *)
        _env->GetPrimitiveArrayCritical(scriptRef, (jboolean *)0);

    //rsScriptCSetText(con, (const char *)script_ptr, length);

    ret = (jint)dispatchTab.ScriptCCreate(con,
                                resNameUTF.c_str(), resNameUTF.length(),
                                cacheDirUTF.c_str(), cacheDirUTF.length(),
                                (const char *)script_ptr, length);

exit:
    if (script_ptr) {
        _env->ReleasePrimitiveArrayCritical(scriptRef, script_ptr,
                _exception ? JNI_ABORT: 0);
    }

    return ret;
}

static jint
nScriptIntrinsicCreate(JNIEnv *_env, jobject _this, RsContext con, jint id, jint eid)
{
    LOG_API("nScriptIntrinsicCreate, con(%p) id(%i) element(%p)", con, id, (void *)eid);
    return (jint)dispatchTab.ScriptIntrinsicCreate(con, id, (RsElement)eid);
}

static jint
nScriptKernelIDCreate(JNIEnv *_env, jobject _this, RsContext con, jint sid, jint slot, jint sig)
{
    LOG_API("nScriptKernelIDCreate, con(%p) script(%p), slot(%i), sig(%i)", con, (void *)sid, slot, sig);
    return (jint)dispatchTab.ScriptKernelIDCreate(con, (RsScript)sid, slot, sig);
}

static jint
nScriptFieldIDCreate(JNIEnv *_env, jobject _this, RsContext con, jint sid, jint slot)
{
    LOG_API("nScriptFieldIDCreate, con(%p) script(%p), slot(%i)", con, (void *)sid, slot);
    return (jint)dispatchTab.ScriptFieldIDCreate(con, (RsScript)sid, slot);
}

static jint
nScriptGroupCreate(JNIEnv *_env, jobject _this, RsContext con, jintArray _kernels, jintArray _src,
    jintArray _dstk, jintArray _dstf, jintArray _types)
{
    LOG_API("nScriptGroupCreate, con(%p)", con);

    jint kernelsLen = _env->GetArrayLength(_kernels) * sizeof(int);
    jint *kernelsPtr = _env->GetIntArrayElements(_kernels, NULL);
    jint srcLen = _env->GetArrayLength(_src) * sizeof(int);
    jint *srcPtr = _env->GetIntArrayElements(_src, NULL);
    jint dstkLen = _env->GetArrayLength(_dstk) * sizeof(int);
    jint *dstkPtr = _env->GetIntArrayElements(_dstk, NULL);
    jint dstfLen = _env->GetArrayLength(_dstf) * sizeof(int);
    jint *dstfPtr = _env->GetIntArrayElements(_dstf, NULL);
    jint typesLen = _env->GetArrayLength(_types) * sizeof(int);
    jint *typesPtr = _env->GetIntArrayElements(_types, NULL);

    int id = (int)dispatchTab.ScriptGroupCreate(con,
                               (RsScriptKernelID *)kernelsPtr, kernelsLen,
                               (RsScriptKernelID *)srcPtr, srcLen,
                               (RsScriptKernelID *)dstkPtr, dstkLen,
                               (RsScriptFieldID *)dstfPtr, dstfLen,
                               (RsType *)typesPtr, typesLen);

    _env->ReleaseIntArrayElements(_kernels, kernelsPtr, 0);
    _env->ReleaseIntArrayElements(_src, srcPtr, 0);
    _env->ReleaseIntArrayElements(_dstk, dstkPtr, 0);
    _env->ReleaseIntArrayElements(_dstf, dstfPtr, 0);
    _env->ReleaseIntArrayElements(_types, typesPtr, 0);
    return id;
}

static void
nScriptGroupSetInput(JNIEnv *_env, jobject _this, RsContext con, jint gid, jint kid, jint alloc)
{
    LOG_API("nScriptGroupSetInput, con(%p) group(%p), kernelId(%p), alloc(%p)", con,
        (void *)gid, (void *)kid, (void *)alloc);
    dispatchTab.ScriptGroupSetInput(con, (RsScriptGroup)gid, (RsScriptKernelID)kid, (RsAllocation)alloc);
}

static void
nScriptGroupSetOutput(JNIEnv *_env, jobject _this, RsContext con, jint gid, jint kid, jint alloc)
{
    LOG_API("nScriptGroupSetOutput, con(%p) group(%p), kernelId(%p), alloc(%p)", con,
        (void *)gid, (void *)kid, (void *)alloc);
    dispatchTab.ScriptGroupSetOutput(con, (RsScriptGroup)gid, (RsScriptKernelID)kid, (RsAllocation)alloc);
}

static void
nScriptGroupExecute(JNIEnv *_env, jobject _this, RsContext con, jint gid)
{
    LOG_API("nScriptGroupSetOutput, con(%p) group(%p)", con, (void *)gid);
    dispatchTab.ScriptGroupExecute(con, (RsScriptGroup)gid);
}

// ---------------------------------------------------------------------------

static jint
nSamplerCreate(JNIEnv *_env, jobject _this, RsContext con, jint magFilter, jint minFilter,
               jint wrapS, jint wrapT, jint wrapR, jfloat aniso)
{
    LOG_API("nSamplerCreate, con(%p)", con);
    return (jint)dispatchTab.SamplerCreate(con,
                                 (RsSamplerValue)magFilter,
                                 (RsSamplerValue)minFilter,
                                 (RsSamplerValue)wrapS,
                                 (RsSamplerValue)wrapT,
                                 (RsSamplerValue)wrapR,
                                 aniso);
}

// ---------------------------------------------------------------------------


static const char *classPathName = "android/support/v8/renderscript/RenderScript";

static JNINativeMethod methods[] = {
{"nLoadSO",                        "(Z)Z",                                    (bool*)nLoadSO },
{"nDeviceCreate",                  "()I",                                     (void*)nDeviceCreate },
{"nDeviceDestroy",                 "(I)V",                                    (void*)nDeviceDestroy },
{"nDeviceSetConfig",               "(III)V",                                  (void*)nDeviceSetConfig },
{"nContextGetUserMessage",         "(I[I)I",                                  (void*)nContextGetUserMessage },
{"nContextGetErrorMessage",        "(I)Ljava/lang/String;",                   (void*)nContextGetErrorMessage },
{"nContextPeekMessage",            "(I[I)I",                                  (void*)nContextPeekMessage },

{"nContextInitToClient",           "(I)V",                                    (void*)nContextInitToClient },
{"nContextDeinitToClient",         "(I)V",                                    (void*)nContextDeinitToClient },


// All methods below are thread protected in java.
{"rsnContextCreate",                 "(IIII)I",                               (void*)nContextCreate },
{"rsnContextFinish",                 "(I)V",                                  (void*)nContextFinish },
{"rsnContextSetPriority",            "(II)V",                                 (void*)nContextSetPriority },
{"rsnContextDestroy",                "(I)V",                                  (void*)nContextDestroy },
{"rsnContextDump",                   "(II)V",                                 (void*)nContextDump },
{"rsnContextSendMessage",            "(II[I)V",                               (void*)nContextSendMessage },
{"rsnObjDestroy",                    "(II)V",                                 (void*)nObjDestroy },

{"rsnElementCreate",                 "(IIIZI)I",                              (void*)nElementCreate },
{"rsnElementCreate2",                "(I[I[Ljava/lang/String;[I)I",           (void*)nElementCreate2 },
{"rsnElementGetSubElements",         "(II[I[Ljava/lang/String;[I)V",          (void*)nElementGetSubElements },

{"rsnTypeCreate",                    "(IIIIIZZI)I",                           (void*)nTypeCreate },

{"rsnAllocationCreateTyped",         "(IIIII)I",                               (void*)nAllocationCreateTyped },
{"rsnAllocationCreateFromBitmap",    "(IIILandroid/graphics/Bitmap;I)I",      (void*)nAllocationCreateFromBitmap },
{"rsnAllocationCreateBitmapBackedAllocation",    "(IIILandroid/graphics/Bitmap;I)I",      (void*)nAllocationCreateBitmapBackedAllocation },
{"rsnAllocationCubeCreateFromBitmap","(IIILandroid/graphics/Bitmap;I)I",      (void*)nAllocationCubeCreateFromBitmap },

{"rsnAllocationCopyFromBitmap",      "(IILandroid/graphics/Bitmap;)V",        (void*)nAllocationCopyFromBitmap },
{"rsnAllocationCopyToBitmap",        "(IILandroid/graphics/Bitmap;)V",        (void*)nAllocationCopyToBitmap },

{"rsnAllocationSyncAll",             "(III)V",                                (void*)nAllocationSyncAll },
{"rsnAllocationData1D",              "(IIIII[II)V",                           (void*)nAllocationData1D_i },
{"rsnAllocationData1D",              "(IIIII[SI)V",                           (void*)nAllocationData1D_s },
{"rsnAllocationData1D",              "(IIIII[BI)V",                           (void*)nAllocationData1D_b },
{"rsnAllocationData1D",              "(IIIII[FI)V",                           (void*)nAllocationData1D_f },
{"rsnAllocationElementData1D",       "(IIIII[BI)V",                           (void*)nAllocationElementData1D },
{"rsnAllocationData2D",              "(IIIIIIII[II)V",                        (void*)nAllocationData2D_i },
{"rsnAllocationData2D",              "(IIIIIIII[SI)V",                        (void*)nAllocationData2D_s },
{"rsnAllocationData2D",              "(IIIIIIII[BI)V",                        (void*)nAllocationData2D_b },
{"rsnAllocationData2D",              "(IIIIIIII[FI)V",                        (void*)nAllocationData2D_f },
{"rsnAllocationData2D",              "(IIIIIIIIIIIII)V",                      (void*)nAllocationData2D_alloc },
{"rsnAllocationData3D",              "(IIIIIIIII[II)V",                       (void*)nAllocationData3D_i },
{"rsnAllocationData3D",              "(IIIIIIIII[SI)V",                       (void*)nAllocationData3D_s },
{"rsnAllocationData3D",              "(IIIIIIIII[BI)V",                       (void*)nAllocationData3D_b },
{"rsnAllocationData3D",              "(IIIIIIIII[FI)V",                       (void*)nAllocationData3D_f },
{"rsnAllocationData3D",              "(IIIIIIIIIIIIII)V",                     (void*)nAllocationData3D_alloc },
{"rsnAllocationRead",                "(II[I)V",                               (void*)nAllocationRead_i },
{"rsnAllocationRead",                "(II[S)V",                               (void*)nAllocationRead_s },
{"rsnAllocationRead",                "(II[B)V",                               (void*)nAllocationRead_b },
{"rsnAllocationRead",                "(II[F)V",                               (void*)nAllocationRead_f },
{"rsnAllocationGetType",             "(II)I",                                 (void*)nAllocationGetType},
{"rsnAllocationResize1D",            "(III)V",                                (void*)nAllocationResize1D },
{"rsnAllocationGenerateMipmaps",     "(II)V",                                 (void*)nAllocationGenerateMipmaps },

{"rsnScriptBindAllocation",          "(IIII)V",                               (void*)nScriptBindAllocation },
{"rsnScriptSetTimeZone",             "(II[B)V",                               (void*)nScriptSetTimeZone },
{"rsnScriptInvoke",                  "(III)V",                                (void*)nScriptInvoke },
{"rsnScriptInvokeV",                 "(III[B)V",                              (void*)nScriptInvokeV },
{"rsnScriptForEach",                 "(IIIII)V",                              (void*)nScriptForEach },
{"rsnScriptForEach",                 "(IIIII[B)V",                            (void*)nScriptForEachV },
{"rsnScriptForEachClipped",          "(IIIIIIIIIII)V",                        (void*)nScriptForEachClipped },
{"rsnScriptForEachClipped",          "(IIIII[BIIIIII)V",                      (void*)nScriptForEachClippedV },
{"rsnScriptSetVarI",                 "(IIII)V",                               (void*)nScriptSetVarI },
{"rsnScriptSetVarJ",                 "(IIIJ)V",                               (void*)nScriptSetVarJ },
{"rsnScriptSetVarF",                 "(IIIF)V",                               (void*)nScriptSetVarF },
{"rsnScriptSetVarD",                 "(IIID)V",                               (void*)nScriptSetVarD },
{"rsnScriptSetVarV",                 "(III[B)V",                              (void*)nScriptSetVarV },
{"rsnScriptSetVarVE",                "(III[BI[I)V",                           (void*)nScriptSetVarVE },
{"rsnScriptSetVarObj",               "(IIII)V",                               (void*)nScriptSetVarObj },

{"rsnScriptCCreate",                 "(ILjava/lang/String;Ljava/lang/String;[BI)I",  (void*)nScriptCCreate },
{"rsnScriptIntrinsicCreate",         "(III)I",                                (void*)nScriptIntrinsicCreate },
{"rsnScriptKernelIDCreate",          "(IIII)I",                               (void*)nScriptKernelIDCreate },
{"rsnScriptFieldIDCreate",           "(III)I",                                (void*)nScriptFieldIDCreate },
{"rsnScriptGroupCreate",             "(I[I[I[I[I[I)I",                        (void*)nScriptGroupCreate },
{"rsnScriptGroupSetInput",           "(IIII)V",                               (void*)nScriptGroupSetInput },
{"rsnScriptGroupSetOutput",          "(IIII)V",                               (void*)nScriptGroupSetOutput },
{"rsnScriptGroupExecute",            "(II)V",                                 (void*)nScriptGroupExecute },

{"rsnSamplerCreate",                 "(IIIIIIF)I",                            (void*)nSamplerCreate },

};

// ---------------------------------------------------------------------------

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* env = NULL;
    jclass clazz = NULL;
    jint result = -1;

    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        //        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
        //            "ERROR: GetEnv failed\n");
        goto bail;
    }
    if (env == NULL) {
        //        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "ERROR: env == NULL");
        goto bail;
    }

    clazz = env->FindClass(classPathName);
    if (clazz == NULL) {
        goto bail;
    }

    if (env->RegisterNatives(clazz, methods, NELEM(methods)) < 0) {
        //        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
        //            "ERROR: MediaPlayer native registration failed\n");
        goto bail;
    }

    /* success -- return valid version number */
    result = JNI_VERSION_1_4;

bail:
    return result;
}
