/*
 * Copyright (C) 2022 The Android Open Source Project
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

package androidx.reflect;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;

import android.util.Log;

import androidx.annotation.RestrictTo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */

@RestrictTo(LIBRARY_GROUP_PREFIX)
public class SeslBaseReflector {
    private static final String TAG = "SeslBaseReflector";

    private SeslBaseReflector() {
    }

    public static Class<?> getClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "Fail to get class = " + className);
            return null;
        }
    }

    public static Method getMethod(String className, String methodName, Class<?>... parameterTypes) {
        if (className == null || methodName == null) {
            Log.d(TAG, "className = " + className + ", methodName = " + methodName);
            return null;
        }

        Class<?> cls = getClass(className);
        if (cls != null) {
            try {
                return cls.getMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException e) {
                Log.w(TAG, "Reflector did not find method = " + methodName);
                return null;
            }
        }

        return null;
    }

    public static <T> Method getMethod(Class<T> classT, String methodName, Class<?>... parameterTypes) {
        if (classT == null || methodName == null) {
            Log.d(TAG, "classT = " + classT + ", methodName = " + methodName);
            return null;
        }

        try {
            return classT.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            Log.w(TAG, "Reflector did not find method = " + methodName);
            return null;
        }
    }

    public static Method getDeclaredMethod(String className, String methodName, Class<?>... parameterTypes) {
        if (className == null || methodName == null) {
            Log.d(TAG, "className = " + className + ", methodName = " + methodName);
            return null;
        }

        Class<?> cls = getClass(className);
        Method method = null;
        if (cls != null) {
            try {
                method = cls.getDeclaredMethod(methodName, parameterTypes);
                if (method != null) {
                    method.setAccessible(true);
                }
            } catch (NoSuchMethodException e) {
                Log.w(TAG, "Reflector did not find method = " + methodName);
            }
        }

        return method;
    }

    public static <T> Method getDeclaredMethod(Class<T> classT, String methodName, Class<?>... parameterTypes) {
        if (classT == null || methodName == null) {
            Log.d(TAG, "classT = " + classT + ", methodName = " + methodName);
            return null;
        }

        Method method = null;
        try {
            method = classT.getDeclaredMethod(methodName, parameterTypes);
            if (method != null) {
                method.setAccessible(true);
            }
        } catch (NoSuchMethodException e) {
            Log.w(TAG, "Reflector did not find method = " + methodName);
        }

        return method;
    }

    public static Object invoke(Object callerInstance, Method method, Object... args) {
        if (method == null) {
            Log.d(TAG, "method is null");
            return null;
        }

        try {
            return method.invoke(callerInstance, args);
        } catch (IllegalAccessException e) {
            Log.e(TAG, method.getName() + " IllegalAccessException", e);
            return null;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, method.getName() + " IllegalArgumentException", e);
            return null;
        } catch (InvocationTargetException e) {
            Log.e(TAG, method.getName() + " InvocationTargetException", e);
            return null;
        }
    }

    public static Field getField(String className, String fieldName) {
        if (className == null || fieldName == null) {
            Log.d(TAG, "className = " + className + ", fieldName = " + fieldName);
            return null;
        }

        Class<?> cls = getClass(className);
        if (cls != null) {
            try {
                return cls.getField(fieldName);
            } catch (NoSuchFieldException e) {
                Log.w(TAG, "Reflector did not find field = " + fieldName);
                return null;
            }
        }

        return null;
    }

    public static <T> Field getField(Class<T> classT, String fieldName) {
        if (classT == null || fieldName == null) {
            Log.d(TAG, "classT = " + classT + ", fieldName = " + fieldName);
            return null;
        }

        try {
            return classT.getField(fieldName);
        } catch (NoSuchFieldException e) {
            Log.w(TAG, "Reflector did not find field = " + fieldName);
            return null;
        }
    }

    public static Field getDeclaredField(String className, String fieldName) {
        if (className == null || fieldName == null) {
            Log.d(TAG, "className = " + className + ", fieldName = " + fieldName);
            return null;
        }

        Class<?> cls = getClass(className);
        Field field = null;
        if (cls != null) {
            try {
                field = cls.getDeclaredField(fieldName);
                if (field != null) {
                    field.setAccessible(true);
                }
            } catch (NoSuchFieldException e) {
                Log.w(TAG, "Reflector did not find field = " + fieldName);
            }
        }

        return field;
    }

    public static <T> Field getDeclaredField(Class<T> classT, String fieldName) {
        if (classT == null || fieldName == null) {
            Log.d(TAG, "classT = " + classT + ", fieldName = " + fieldName);
            return null;
        }

        Field field = null;
        try {
            field = classT.getDeclaredField(fieldName);
            if (field != null) {
                field.setAccessible(true);
            }
        } catch (NoSuchFieldException e) {
            Log.w(TAG, "Reflector did not find field = " + fieldName);;
        }

        return field;
    }

    public static Object get(Object callerInstance, Field field) {
        if (field == null) {
            Log.e(TAG, "field is null");
            return null;
        }

        try {
            return field.get(callerInstance);
        } catch (IllegalAccessException e) {
            Log.e(TAG, field.getName() + " IllegalAccessException", e);
            return null;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, field.getName() + " IllegalArgumentException", e);
            return null;
        }
    }

    public static void set(Object callerInstance, Field field, Object value) {
        if (field == null) {
            Log.e(TAG, "field is null");
            return;
        }

        try {
            field.set(callerInstance, value);
        } catch (IllegalAccessException e) {
            Log.e(TAG, field.getName() + " IllegalAccessException", e);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, field.getName() + " IllegalArgumentException", e);
        }
    }

    public static Constructor<?> getConstructor(String className, Class<?>... paramTypes) {
        try {
            return Class.forName(className).getDeclaredConstructor(paramTypes);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
    }
}