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
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import java.util.Set;

/**
 * A Configuration is a collection of {@link Option}s and values.
 *
 * <p>Configuration object hold pairs of Options/Values and offer methods for querying whether
 * Options are contained in the configuration along with methods for retrieving the associated
 * values for options.
 */
public interface Configuration {

  /**
   * A callback for retrieving results of a {@link Configuration.Option} search.
   *
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  interface OptionMatcher {
    /**
     * Receives results from {@link Configuration#findOptions(String, OptionMatcher)}.
     *
     * <p>When searching for a specific option in a {@link Configuration}, {@link Option}s will be
     * sent to {@link #onOptionMatched(Option)} in the order in which they are found.
     *
     * @param option The matched option.
     * @return <code>false</code> if no further results are needed; <code>true</code> otherwise.
     */
    boolean onOptionMatched(Option<?> option);
  }

  /**
   * Returns whether this configuration contains the supplied option.
   *
   * @param id The {@link Option} to search for in this configuration.
   * @return <code>true</code> if this configuration contains the supplied option; <code>false
   *     </code> otherwise.
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  boolean containsOption(Option<?> id);

  /**
   * Retrieves the value for the specified option if it exists in the configuration.
   *
   * <p>If the option does not exist, an exception will be thrown.
   *
   * @param id The {@link Option} to search for in this configuration.
   * @param <ValueT> The type for the value associated with the supplied {@link Option}.
   * @return The value stored in this configuration, or <code>null</code> if it does not exist.
   * @throws IllegalArgumentException if the given option does not exist in this configuration.
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  <ValueT> ValueT retrieveOption(Option<ValueT> id);

  /**
   * Retrieves the value for the specified option if it exists in the configuration.
   *
   * <p>If the option does not exist, <code>valueIfMissing</code> will be returned.
   *
   * @param id The {@link Option} to search for in this configuration.
   * @param valueIfMissing The value to return if the specified {@link Option} does not exist in
   *     this configuration.
   * @param <ValueT> The type for the value associated with the supplied {@link Option}.
   * @return The value stored in this configuration, or <code>null</code> if it does not exist.
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  @Nullable
  <ValueT> ValueT retrieveOption(Option<ValueT> id, @Nullable ValueT valueIfMissing);

  /**
   * Search the configuration for {@link Option}s whose id match the supplied search string.
   *
   * @param idSearchString The id string to search for. This could be a fully qualified id such as
   *     \"<code>camerax.core.example.option</code>\" or the stem for an option such as \"<code>
   *     camerax.core.example</code>\".
   * @param matcher A callback used to receive results of the search. Results will be sent to {@link
   *     OptionMatcher#onOptionMatched(Option)} in the order in which they are found inside this
   *     configuration. Subsequent results will continue to be sent as long as {@link
   *     OptionMatcher#onOptionMatched(Option)} returns <code>true</code>.
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  void findOptions(String idSearchString, OptionMatcher matcher);

  /**
   * Lists all options contained within this configuration.
   *
   * @return A {@link Set} of {@link Option}s contained within this configuration.
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  Set<Option<?>> listOptions();

  /**
   * The Reader interface can be extended to create APIs for reading specific options.
   *
   * <p>Reader objects are also {@link Configuration} objects, so can be passed to any method that
   * expects a {@link Configuration}.
   */
  interface Reader extends Configuration {

    /**
     * Returns the underlying immutable {@link Configuration} object.
     *
     * @return The underlying {@link Configuration} object.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    Configuration getConfiguration();

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    default boolean containsOption(Option<?> id) {
      return getConfiguration().containsOption(id);
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @Nullable
    default <ValueT> ValueT retrieveOption(Option<ValueT> id) {
      return getConfiguration().retrieveOption(id);
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @Nullable
    default <ValueT> ValueT retrieveOption(Option<ValueT> id, @Nullable ValueT valueIfMissing) {
      return getConfiguration().retrieveOption(id, valueIfMissing);
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    default void findOptions(String idStem, OptionMatcher matcher) {
      getConfiguration().findOptions(idStem, matcher);
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    default Set<Option<?>> listOptions() {
      return getConfiguration().listOptions();
    }
  }

  /**
   * Builders are used to generate immutable {@link Configuration} objects.
   *
   * @param <C> The top-level type of the {@link Configuration} being generated.
   * @param <T> The top-level {@link Builder} type for this Builder.
   */
  interface Builder<C extends Configuration, T extends Builder<C, T>> {

    /**
     * Returns the underlying {@link MutableConfiguration} being modified by this builder.
     *
     * @return The underlying {@link MutableConfiguration}.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    MutableConfiguration getMutableConfiguration();

    /**
     * The solution for the unchecked cast warning.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    T builder();

    /**
     * Inserts a Option/Value pair into the configuration.
     *
     * <p>If the option already exists in this configuration, it will be replaced.
     *
     * @param opt The option to be added or modified
     * @param value The value to insert for this option.
     * @param <ValueT> The type of the value being inserted.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    default <ValueT> T insertOption(Option<ValueT> opt, ValueT value) {
      getMutableConfiguration().insertOption(opt, value);
      return builder();
    }

    /**
     * Removes an option from the configuration if it exists.
     *
     * @param opt The option to remove from the configuration.
     * @param <ValueT> The type of the value being removed.
     * @return The value that previously existed for <code>opt</code>, or <code>null</code> if the
     *     option did not exist in this configuration.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Nullable
    default <ValueT> T removeOption(Option<ValueT> opt) {
      getMutableConfiguration().removeOption(opt);
      return builder();
    }

    /**
     * Creates an immutable {@link Configuration} object from the current state of this builder.
     *
     * @return The {@link Configuration} generated from the current state.
     */
    C build();
  }

  /**
   * An {@link Option} is used to set and retrieve values for settings defined in a {@link
   * Configuration}.
   *
   * <p>{@link Option}s can be thought of as the key in a key/value pair that makes up a setting. As
   * the name suggests, {@link Option}s are optional, and may or may not exist inside a {@link
   * Configuration}.
   *
   * @param <T> The type of the value for this option.
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  @AutoValue
  abstract class Option<T> {

    /**
     * Creates an {@link Option} from an id and value class.
     *
     * @param id A unique string identifier for this option. This generally follows the scheme
     *     <code>&lt;owner&gt;.[optional.subCategories.]&lt;optionId&gt;</code>.
     * @param valueClass The class of the value stored by this option.
     * @param <T> The type of the value stored by this option.
     * @return An {@link Option} object which can be used to store/retrieve values from a {@link
     *     Configuration}.
     */
    public static <T> Option<T> create(String id, Class<T> valueClass) {
      TypeReference<T> valueType = TypeReference.createSpecializedTypeReference(valueClass);
      return create(id, valueType, /*token=*/ null);
    }

    /**
     * Creates an {@link Option} from an id, value class and token.
     *
     * @param id A unique string identifier for this option. This generally follows the scheme
     *     <code>&lt;owner&gt;.[optional.subCategories.]&lt;optionId&gt;</code>.
     * @param valueClass The class of the value stored by this option.
     * @param <T> The type of the value stored by this option.
     * @param token An optional, type-erased object for storing more context for this specific
     *     option. Generally this object should have static scope and be immutable.
     * @return An {@link Option} object which can be used to store/retrieve values from a {@link
     *     Configuration}.
     */
    public static <T> Option<T> create(String id, Class<T> valueClass, @Nullable Object token) {
      TypeReference<T> valueType = TypeReference.createSpecializedTypeReference(valueClass);
      return create(id, valueType, token);
    }

    @RestrictTo(Scope.LIBRARY_GROUP)
    public static <T> Option<T> create(String name, TypeReference<T> valueType) {
      return create(name, valueType, /*token=*/ null);
    }

    @RestrictTo(Scope.LIBRARY_GROUP)
    public static <T> Option<T> create(
        String name, TypeReference<T> valueType, @Nullable Object token) {
      return new AutoValue_Configuration_Option<>(name, valueType, token);
    }

    /**
     * Returns the unique string identifier for this option.
     *
     * <p>This generally follows the scheme * <code>
     * &lt;owner&gt;.[optional.subCategories.]&lt;optionId&gt;
     * </code>.
     *
     * @return The identifier.
     */
    public abstract String getId();

    abstract TypeReference<T> getTypeReference();

    /**
     * Returns the optional type-erased context object for this option.
     *
     * <p>Generally this object should have static scope and be immutable.
     *
     * @return The type-erased context object.
     */
    @Nullable
    public abstract Object getToken();

    /**
     * Returns the class object associated with the value for this option.
     *
     * @return The class object for the value's type.
     */
    @Memoized
    @SuppressWarnings("unchecked")
    public Class<T> getValueClass() {
      return (Class<T>) getTypeReference().getRawType();
    }

    /** Prevent subclassing */
    Option() {}
  }
}
