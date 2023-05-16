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
package androidx.appactions.builtintypes.types

import androidx.appactions.builtintypes.properties.DisambiguatingDescription
import androidx.appactions.builtintypes.properties.Name
import java.util.Objects
import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.collections.Map
import kotlin.collections.emptyMap
import kotlin.collections.joinToString
import kotlin.collections.map
import kotlin.collections.mutableMapOf
import kotlin.collections.plusAssign
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * An alarm set to go off at a specified schedule.
 *
 * See http://schema.googleapis.com/Alarm for context.
 *
 * Should not be directly implemented. More properties may be added over time. Instead consider
 * using [Companion.Builder] or see [GenericAlarm] if you need to extend this type.
 */
public interface Alarm : Thing {
  /** Associates an Alarm with a Schedule. */
  public val alarmSchedule: Schedule?

  /** Converts this [Alarm] to its builder with all the properties copied over. */
  public override fun toBuilder(): Builder<*>

  public companion object {
    /** Returns a default implementation of [Builder] with no properties set. */
    @JvmStatic public fun Builder(): Builder<*> = AlarmImpl.Builder()
  }

  /**
   * Builder for [Alarm].
   *
   * Should not be directly implemented. More methods may be added over time. See
   * [GenericAlarm.Builder] if you need to extend this builder.
   */
  public interface Builder<Self : Builder<Self>> : Thing.Builder<Self> {
    /** Returns a built [Alarm]. */
    public override fun build(): Alarm

    /** Sets the `alarmSchedule`. */
    public fun setAlarmSchedule(schedule: Schedule?): Self

    /** Sets the `disambiguatingDescription` to a canonical [DisambiguatingDescriptionValue]. */
    public fun setDisambiguatingDescription(canonicalValue: DisambiguatingDescriptionValue): Self =
      setDisambiguatingDescription(DisambiguatingDescription(canonicalValue))
  }

  /**
   * A canonical value that may be assigned to [DisambiguatingDescription] properties in the context
   * of [Alarm].
   *
   * Represents an open enum. See [Companion] for the different possible variants. More variants may
   * be added over time.
   */
  public class DisambiguatingDescriptionValue
  private constructor(
    public override val textValue: String,
  ) : DisambiguatingDescription.CanonicalValue() {
    public override fun toString(): String = """Alarm.DisambiguatingDescriptionValue($textValue)"""

    public companion object {
      @JvmField
      public val FAMILY_BELL: DisambiguatingDescriptionValue =
        DisambiguatingDescriptionValue("FamilyBell")
    }
  }
}

/**
 * A generic implementation of [Alarm].
 *
 * Allows for extension like:
 * ```kt
 * class MyAlarm internal constructor(
 *   alarm: Alarm,
 *   val foo: String,
 *   val bars: List<Int>,
 * ) : GenericAlarm<
 *   MyAlarm,
 *   MyAlarm.Builder
 * >(alarm) {
 *
 *   override val selfTypeName =
 *     "MyAlarm"
 *
 *   override val additionalProperties: Map<String, Any?>
 *     get() = mapOf("foo" to foo, "bars" to bars)
 *
 *   override fun toBuilderWithAdditionalPropertiesOnly(): Builder {
 *     return Builder()
 *       .setFoo(foo)
 *       .addBars(bars)
 *   }
 *
 *   class Builder :
 *     GenericAlarm.Builder<
 *       Builder,
 *       MyAlarm> {...}
 * }
 * ```
 *
 * Also see [GenericAlarm.Builder].
 */
@Suppress("UNCHECKED_CAST")
public abstract class GenericAlarm<
  Self : GenericAlarm<Self, Builder>, Builder : GenericAlarm.Builder<Builder, Self>>
internal constructor(
  public final override val alarmSchedule: Schedule?,
  public final override val disambiguatingDescription: DisambiguatingDescription?,
  public final override val identifier: String?,
  public final override val name: Name?,
) : Alarm {
  /**
   * Human readable name for the concrete [Self] class.
   *
   * Used in the [toString] output.
   */
  protected abstract val selfTypeName: String

  /**
   * The additional properties that exist on the concrete [Self] class.
   *
   * Used for equality comparison and computing the hash code.
   */
  protected abstract val additionalProperties: Map<String, Any?>

  /** A copy-constructor that copies over properties from another [Alarm] instance. */
  public constructor(
    alarm: Alarm
  ) : this(alarm.alarmSchedule, alarm.disambiguatingDescription, alarm.identifier, alarm.name)

  /** Returns a concrete [Builder] with the additional, non-[Alarm] properties copied over. */
  protected abstract fun toBuilderWithAdditionalPropertiesOnly(): Builder

  public final override fun toBuilder(): Builder =
    toBuilderWithAdditionalPropertiesOnly()
      .setAlarmSchedule(alarmSchedule)
      .setDisambiguatingDescription(disambiguatingDescription)
      .setIdentifier(identifier)
      .setName(name)

  public final override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || this::class.java != other::class.java) return false
    other as Self
    if (alarmSchedule != other.alarmSchedule) return false
    if (disambiguatingDescription != other.disambiguatingDescription) return false
    if (identifier != other.identifier) return false
    if (name != other.name) return false
    if (additionalProperties != other.additionalProperties) return false
    return true
  }

  public final override fun hashCode(): Int =
    Objects.hash(alarmSchedule, disambiguatingDescription, identifier, name, additionalProperties)

  public final override fun toString(): String {
    val attributes = mutableMapOf<String, String>()
    if (alarmSchedule != null) {
      attributes["alarmSchedule"] = alarmSchedule.toString()
    }
    if (disambiguatingDescription != null) {
      attributes["disambiguatingDescription"] =
        disambiguatingDescription.toString(includeWrapperName = false)
    }
    if (identifier != null) {
      attributes["identifier"] = identifier
    }
    if (name != null) {
      attributes["name"] = name.toString(includeWrapperName = false)
    }
    attributes += additionalProperties.map { (k, v) -> k to v.toString() }
    val commaSeparated = attributes.entries.joinToString(separator = ", ") { (k, v) -> """$k=$v""" }
    return """$selfTypeName($commaSeparated)"""
  }

  /**
   * A generic implementation of [Alarm.Builder].
   *
   * Allows for extension like:
   * ```kt
   * class MyAlarm :
   *   : GenericAlarm<
   *     MyAlarm,
   *     MyAlarm.Builder>(...) {
   *
   *   class Builder
   *   : Builder<
   *       Builder,
   *       MyAlarm
   *   >() {
   *     private var foo: String? = null
   *     private val bars = mutableListOf<Int>()
   *
   *     override val selfTypeName =
   *       "MyAlarm.Builder"
   *
   *     override val additionalProperties: Map<String, Any?>
   *       get() = mapOf("foo" to foo, "bars" to bars)
   *
   *     override fun buildFromAlarm(
   *       alarm: Alarm
   *     ): MyAlarm {
   *       return MyAlarm(
   *         alarm,
   *         foo,
   *         bars.toList()
   *       )
   *     }
   *
   *     fun setFoo(string: String): Builder {
   *       return apply { foo = string }
   *     }
   *
   *     fun addBar(int: Int): Builder {
   *       return apply { bars += int }
   *     }
   *
   *     fun addBars(values: Iterable<Int>): Builder {
   *       return apply { bars += values }
   *     }
   *   }
   * }
   * ```
   *
   * Also see [GenericAlarm].
   */
  @Suppress("StaticFinalBuilder")
  public abstract class Builder<Self : Builder<Self, Built>, Built : GenericAlarm<Built, Self>> :
    Alarm.Builder<Self> {
    /**
     * Human readable name for the concrete [Self] class.
     *
     * Used in the [toString] output.
     */
    @get:Suppress("GetterOnBuilder") protected abstract val selfTypeName: String

    /**
     * The additional properties that exist on the concrete [Self] class.
     *
     * Used for equality comparison and computing the hash code.
     */
    @get:Suppress("GetterOnBuilder") protected abstract val additionalProperties: Map<String, Any?>

    private var alarmSchedule: Schedule? = null

    private var disambiguatingDescription: DisambiguatingDescription? = null

    private var identifier: String? = null

    private var name: Name? = null

    /**
     * Builds a concrete [Built] instance, given a built [Alarm].
     *
     * Subclasses should override this method to build a concrete [Built] instance that holds both
     * the [Alarm]-specific properties and the subclass specific [additionalProperties].
     *
     * See the sample code in the documentation of this class for more context.
     */
    @Suppress("BuilderSetStyle") protected abstract fun buildFromAlarm(alarm: Alarm): Built

    public final override fun build(): Built =
      buildFromAlarm(AlarmImpl(alarmSchedule, disambiguatingDescription, identifier, name))

    public final override fun setAlarmSchedule(schedule: Schedule?): Self {
      this.alarmSchedule = schedule
      return this as Self
    }

    public final override fun setDisambiguatingDescription(
      disambiguatingDescription: DisambiguatingDescription?
    ): Self {
      this.disambiguatingDescription = disambiguatingDescription
      return this as Self
    }

    public final override fun setIdentifier(text: String?): Self {
      this.identifier = text
      return this as Self
    }

    public final override fun setName(name: Name?): Self {
      this.name = name
      return this as Self
    }

    @Suppress("BuilderSetStyle")
    public final override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || this::class.java != other::class.java) return false
      other as Self
      if (alarmSchedule != other.alarmSchedule) return false
      if (disambiguatingDescription != other.disambiguatingDescription) return false
      if (identifier != other.identifier) return false
      if (name != other.name) return false
      if (additionalProperties != other.additionalProperties) return false
      return true
    }

    @Suppress("BuilderSetStyle")
    public final override fun hashCode(): Int =
      Objects.hash(alarmSchedule, disambiguatingDescription, identifier, name, additionalProperties)

    @Suppress("BuilderSetStyle")
    public final override fun toString(): String {
      val attributes = mutableMapOf<String, String>()
      if (alarmSchedule != null) {
        attributes["alarmSchedule"] = alarmSchedule!!.toString()
      }
      if (disambiguatingDescription != null) {
        attributes["disambiguatingDescription"] =
          disambiguatingDescription!!.toString(includeWrapperName = false)
      }
      if (identifier != null) {
        attributes["identifier"] = identifier!!
      }
      if (name != null) {
        attributes["name"] = name!!.toString(includeWrapperName = false)
      }
      attributes += additionalProperties.map { (k, v) -> k to v.toString() }
      val commaSeparated =
        attributes.entries.joinToString(separator = ", ") { (k, v) -> """$k=$v""" }
      return """$selfTypeName($commaSeparated)"""
    }
  }
}

internal class AlarmImpl : GenericAlarm<AlarmImpl, AlarmImpl.Builder> {
  protected override val selfTypeName: String
    get() = "Alarm"

  protected override val additionalProperties: Map<String, Any?>
    get() = emptyMap()

  public constructor(
    alarmSchedule: Schedule?,
    disambiguatingDescription: DisambiguatingDescription?,
    identifier: String?,
    name: Name?,
  ) : super(alarmSchedule, disambiguatingDescription, identifier, name)

  public constructor(alarm: Alarm) : super(alarm)

  protected override fun toBuilderWithAdditionalPropertiesOnly(): Builder = Builder()

  internal class Builder : GenericAlarm.Builder<Builder, AlarmImpl>() {
    protected override val selfTypeName: String
      get() = "Alarm.Builder"

    protected override val additionalProperties: Map<String, Any?>
      get() = emptyMap()

    protected override fun buildFromAlarm(alarm: Alarm): AlarmImpl =
      alarm as? AlarmImpl ?: AlarmImpl(alarm)
  }
}
