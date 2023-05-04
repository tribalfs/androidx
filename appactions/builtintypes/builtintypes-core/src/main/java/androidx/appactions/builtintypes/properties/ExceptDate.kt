// Copyright 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package androidx.appactions.builtintypes.properties

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.Objects
import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.error
import kotlin.jvm.JvmName

/**
 * Defines a `Date` or `DateTime` during which a scheduled `Event` will not take place. The property
 * allows exceptions to a `Schedule` to be specified. If an exception is specified as a `DateTime`
 * then only the event that would have started at that specific date and time should be excluded
 * from the schedule. If an exception is specified as a `Date` then any event that is scheduled for
 * that 24 hour period should be excluded from the schedule. This allows a whole day to be excluded
 * from the schedule without having to itemise every scheduled event.
 *
 * See http://schema.org/exceptDate for context.
 *
 * Holds one of:
 * * Date i.e. [LocalDate]
 * * [LocalDateTime]
 * * [ZonedDateTime]
 *
 * May hold more types over time.
 */
public class ExceptDate
internal constructor(
  /** The [LocalDate] variant, or null if constructed using a different variant. */
  @get:JvmName("asDate") public val asDate: LocalDate? = null,
  /** The [LocalDateTime] variant, or null if constructed using a different variant. */
  @get:JvmName("asLocalDateTime") public val asLocalDateTime: LocalDateTime? = null,
  /** The [ZonedDateTime] variant, or null if constructed using a different variant. */
  @get:JvmName("asZonedDateTime") public val asZonedDateTime: ZonedDateTime? = null,
  /**
   * The AppSearch document's identifier.
   *
   * Every AppSearch document needs an identifier. Since property wrappers are only meant to be used
   * at nested levels, this is internal and will always be an empty string.
   */
  internal val identifier: String = "",
) {
  /** Constructor for the [LocalDate] variant. */
  public constructor(date: LocalDate) : this(asDate = date)

  /** Constructor for the [LocalDateTime] variant. */
  public constructor(localDateTime: LocalDateTime) : this(asLocalDateTime = localDateTime)

  /** Constructor for the [ZonedDateTime] variant. */
  public constructor(zonedDateTime: ZonedDateTime) : this(asZonedDateTime = zonedDateTime)

  /**
   * Maps each of the possible underlying variants to some [R].
   *
   * A visitor can be provided to handle the possible variants. A catch-all default case must be
   * provided in case a new type is added in a future release of this library.
   *
   * @sample [androidx.appactions.builtintypes.samples.properties.exceptDateMapWhenUsage]
   */
  public fun <R> mapWhen(mapper: Mapper<R>): R =
    when {
      asDate != null -> mapper.date(asDate)
      asLocalDateTime != null -> mapper.localDateTime(asLocalDateTime)
      asZonedDateTime != null -> mapper.zonedDateTime(asZonedDateTime)
      else -> error("No variant present in ExceptDate")
    }

  public override fun toString(): String = toString(includeWrapperName = true)

  internal fun toString(includeWrapperName: Boolean): String =
    when {
      asDate != null ->
        if (includeWrapperName) {
          """ExceptDate($asDate)"""
        } else {
          asDate.toString()
        }
      asLocalDateTime != null ->
        if (includeWrapperName) {
          """ExceptDate($asLocalDateTime)"""
        } else {
          asLocalDateTime.toString()
        }
      asZonedDateTime != null ->
        if (includeWrapperName) {
          """ExceptDate($asZonedDateTime)"""
        } else {
          asZonedDateTime.toString()
        }
      else -> error("No variant present in ExceptDate")
    }

  public override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ExceptDate) return false
    if (asDate != other.asDate) return false
    if (asLocalDateTime != other.asLocalDateTime) return false
    if (asZonedDateTime != other.asZonedDateTime) return false
    return true
  }

  public override fun hashCode(): Int = Objects.hash(asDate, asLocalDateTime, asZonedDateTime)

  /** Maps each of the possible variants of [ExceptDate] to some [R]. */
  public interface Mapper<R> {
    /** Returns some [R] when the [ExceptDate] holds some [LocalDate] instance. */
    public fun date(instance: LocalDate): R = orElse()

    /** Returns some [R] when the [ExceptDate] holds some [LocalDateTime] instance. */
    public fun localDateTime(instance: LocalDateTime): R = orElse()

    /** Returns some [R] when the [ExceptDate] holds some [ZonedDateTime] instance. */
    public fun zonedDateTime(instance: ZonedDateTime): R = orElse()

    /** The catch-all handler that is invoked when a particular variant isn't explicitly handled. */
    public fun orElse(): R
  }
}
