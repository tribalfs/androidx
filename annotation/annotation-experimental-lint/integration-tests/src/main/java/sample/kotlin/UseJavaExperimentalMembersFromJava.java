/*
 * Copyright 2022 The Android Open Source Project
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

package sample.kotlin;

/**
 * Tests for calls made to experimental members on a stable class.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
class UseJavaExperimentalMembersFromJava {

    /**
     * Unsafe call into an experimental field on a stable class.
     */
    int unsafeExperimentalField() {
        AnnotatedJavaMembers stableObject = new AnnotatedJavaMembers();
        return stableObject.field;
    }

    /**
     * Unsafe call into an experimental method on a stable class.
     */
    int unsafeExperimentalMethod() {
        AnnotatedJavaMembers stableObject = new AnnotatedJavaMembers();
        return stableObject.method();
    }

    /**
     * Unsafe call into an experimental static field on a stable class.
     */
    int unsafeExperimentalStaticField() {
        return AnnotatedJavaMembers.FIELD_STATIC;
    }

    /**
     * Unsafe call into an experimental static method on a stable class.
     */
    int unsafeExperimentalStaticMethod() {
        return AnnotatedJavaMembers.methodStatic();
    }

    /**
     * Unsafe references to experimental properties.
     */
    void unsafePropertyUsage() {
        new AnnotatedJavaMembers().field = -1;
        int value = new AnnotatedJavaMembers().field;
        new AnnotatedJavaMembers().setFieldWithSetMarker(-1);
        int value2 = new AnnotatedJavaMembers().getFieldWithSetMarker(); // safe
    }
}
