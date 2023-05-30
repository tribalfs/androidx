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

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Objects
import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.error
import kotlin.jvm.JvmName

/**
 * The endTime of something.
 *
 * For a reserved event or service (e.g. `FoodEstablishmentReservation`), the time that it is
 * expected to end. For actions that span a period of time, when the action was performed. E.g. John
 * wrote a book from January to *December*. For media, including audio and video, it's the time
 * offset of the end of a clip within a larger file.
 *
 * See http://schema.org/endTime for context.
 *
 * Holds one of:
 * * Time i.e. [LocalTime]
 * * [LocalDateTime]
 * * [Instant]
 *
 * May hold more types over time.
 */
public class EndTime
internal constructor(
  /** The [LocalTime] variant, or null if constructed using a different variant. */
  @get:JvmName("asTime") public val asTime: LocalTime? = null,
  /** The [LocalDateTime] variant, or null if constructed using a different variant. */
  @get:JvmName("asLocalDateTime") public val asLocalDateTime: LocalDateTime? = null,
  /** The [Instant] variant, or null if constructed using a different variant. */
  @get:JvmName("asInstant") public val asInstant: Instant? = null,
  /**
   * The AppSearch document's identifier.
   *
   * Every AppSearch document needs an identifier. Since property wrappers are only meant to be used
   * at nested levels, this is internal and will always be an empty string.
   */
  internal val identifier: String = "",
) {
  /** Constructor for the [LocalTime] variant. */
  public constructor(time: LocalTime) : this(asTime = time)

  /** Constructor for the [LocalDateTime] variant. */
  public constructor(localDateTime: LocalDateTime) : this(asLocalDateTime = localDateTime)

  /** Constructor for the [Instant] variant. */
  public constructor(instant: Instant) : this(asInstant = instant)

  /**
   * Maps each of the possible underlying variants to some [R].
   *
   * A visitor can be provided to handle the possible variants. A catch-all default case must be
   * provided in case a new type is added in a future release of this library.
   *
   * @sample [androidx.appactions.builtintypes.samples.properties.endTimeMapWhenUsage]
   */
  public fun <R> mapWhen(mapper: Mapper<R>): R =
    when {
      asTime != null -> mapper.time(asTime)
      asLocalDateTime != null -> mapper.localDateTime(asLocalDateTime)
      asInstant != null -> mapper.instant(asInstant)
      else -> error("No variant present in EndTime")
    }

  public override fun toString(): String = toString(includeWrapperName = true)

  internal fun toString(includeWrapperName: Boolean): String =
    when {
      asTime != null ->
        if (includeWrapperName) {
          """EndTime($asTime)"""
        } else {
          asTime.toString()
        }
      asLocalDateTime != null ->
        if (includeWrapperName) {
          """EndTime($asLocalDateTime)"""
        } else {
          asLocalDateTime.toString()
        }
      asInstant != null ->
        if (includeWrapperName) {
          """EndTime($asInstant)"""
        } else {
          asInstant.toString()
        }
      else -> error("No variant present in EndTime")
    }

  public override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is EndTime) return false
    if (asTime != other.asTime) return false
    if (asLocalDateTime != other.asLocalDateTime) return false
    if (asInstant != other.asInstant) return false
    return true
  }

  public override fun hashCode(): Int = Objects.hash(asTime, asLocalDateTime, asInstant)

  /** Maps each of the possible variants of [EndTime] to some [R]. */
  public interface Mapper<R> {
    /** Returns some [R] when the [EndTime] holds some [LocalTime] instance. */
    public fun time(instance: LocalTime): R = orElse()

    /** Returns some [R] when the [EndTime] holds some [LocalDateTime] instance. */
    public fun localDateTime(instance: LocalDateTime): R = orElse()

    /** Returns some [R] when the [EndTime] holds some [Instant] instance. */
    public fun instant(instance: Instant): R = orElse()

    /** The catch-all handler that is invoked when a particular variant isn't explicitly handled. */
    public fun orElse(): R
  }
}
