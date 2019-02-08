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

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import java.util.UUID;

/**
 * Configuration containing options used to identify the target class and object being configured.
 *
 * @param <T> The type of the object being configured.
 */
public interface TargetConfiguration<T> extends Configuration.Reader {

  /**
   * Retrieves the class of the object being configured.
   *
   * @param valueIfMissing The value to return if this configuration option has not been set.
   * @return The stored value or <code>valueIfMissing</code> if the value does not exist in this
   *     configuration.
   */
  @Nullable
  default Class<T> getTargetClass(@Nullable Class<T> valueIfMissing) {
    @SuppressWarnings("unchecked") // Value should only be added via Builder#setTargetClass()
    Class<T> storedClass = (Class<T>) retrieveOption(OPTION_TARGET_CLASS, valueIfMissing);
    return storedClass;
  }

  /**
   * Retrieves the class of the object being configured.
   *
   * @return The stored value, if it exists in this configuration.
   * @throws IllegalArgumentException if the option does not exist in this configuration.
   */
  default Class<T> getTargetClass() {
    @SuppressWarnings("unchecked") // Value should only be added via Builder#setTargetClass()
    Class<T> storedClass = (Class<T>) retrieveOption(OPTION_TARGET_CLASS);
    return storedClass;
  }

  /**
   * Retrieves the name of the target object being configured.
   *
   * <p>The name should be a value that can uniquely identify an instance of the object being
   * configured.
   *
   * @param valueIfMissing The value to return if this configuration option has not been set.
   * @return The stored value or <code>valueIfMissing</code> if the value does not exist in this
   *     configuration.
   */
  @Nullable
  default String getTargetName(@Nullable String valueIfMissing) {
    return retrieveOption(OPTION_TARGET_NAME, valueIfMissing);
  }

  /**
   * Retrieves the name of the target object being configured.
   *
   * <p>The name should be a value that can uniquely identify an instance of the object being
   * configured.
   *
   * @return The stored value, if it exists in this configuration.
   * @throws IllegalArgumentException if the option does not exist in this configuration.
   */
  default String getTargetName() {
    return retrieveOption(OPTION_TARGET_NAME);
  }

  /**
   * Builder for a {@link TargetConfiguration}.
   *
   * <p>A {@link TargetConfiguration} contains options used to identify the target class and object
   * being configured.
   *
   * @param <T> The type of the object being configured.
   * @param <C> The top level configuration which will be generated by {@link #build()}.
   * @param <B> The top level builder type for which this builder is composed with.
   */
  interface Builder<T, C extends Configuration, B extends Builder<T, C, B>>
      extends Configuration.Builder<C, B> {

    /**
     * Sets the class of the object being configured.
     *
     * <p>Setting the target class will automatically generate a unique target name if one does not
     * already exist in this configuration.
     *
     * @param targetClass A class object corresponding to the class of the object being configured.
     * @return the current Builder.
     *
     * @hide
     */
    default B setTargetClass(Class<T> targetClass) {
      getMutableConfiguration().insertOption(OPTION_TARGET_CLASS, targetClass);

      // If no name is set yet, then generate a unique name
      if (null == getMutableConfiguration().retrieveOption(OPTION_TARGET_NAME, null)) {
        String targetName = targetClass.getCanonicalName() + "-" + UUID.randomUUID();
        setTargetName(targetName);
      }

      return builder();
    }

    /**
     * Sets the name of the target object being configured.
     *
     * <p>The name should be a value that can uniquely identify an instance of the object being
     * configured.
     *
     * @param targetName A unique string identifier for the instance of the class being configured.
     * @return the current Builder.
     */
    default B setTargetName(String targetName) {
      getMutableConfiguration().insertOption(OPTION_TARGET_NAME, targetName);
      return builder();
    }
  }

  // Option Declarations:
  // ***********************************************************************************************

  /**
   * Option: camerax.core.target.name
   *
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  Option<String> OPTION_TARGET_NAME = Option.create("camerax.core.target.name", String.class);

  /**
   * Option: camerax.core.target.class
   *
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  Option<Class<?>> OPTION_TARGET_CLASS =
      Option.create("camerax.core.target.class", new TypeReference<Class<?>>() {});
}
