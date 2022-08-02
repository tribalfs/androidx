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
 * Regression test for b/192562926 where the lint check should not flag overrides where there is
 * no dependency on the superclass, e.g. calls to super().
 */
@SuppressWarnings("unused")
public class RegressionTestJava192562926 {
    interface StableInterface {
        // This method will show up first in the list provided by PsiClass.allMethods, but it's
        // not the method that we want to inspect since it has a concrete implementation.
        default void abstractMethodWithDefault() {}

        @ExperimentalJavaAnnotation
        void experimentalMethod();
    }

    /**
     * Safe override since super is not called.
     */
    static class ConcreteStableInterface implements StableInterface {
        @Override
        public void experimentalMethod() {} // unsafe override
    }

    /**
     * Test different approaches to overriding interface methods.
     */
    void regressionTestOverrides() {
        @SuppressWarnings("Convert2Lambda")
        StableInterface anonymous = new StableInterface() {
            @Override
            public void experimentalMethod() {} // unsafe override
        };

        StableInterface lambda = () -> {}; // unsafe override
    }
}
