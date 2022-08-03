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

@file:RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java

package androidx.camera.camera2.pipe.graph

import android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL
import android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL
import android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
import android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
import android.hardware.camera2.params.OutputConfiguration
import android.os.Build
import android.util.Size
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.camera.camera2.pipe.CameraGraph
import androidx.camera.camera2.pipe.CameraId
import androidx.camera.camera2.pipe.CameraMetadata
import androidx.camera.camera2.pipe.CameraStream
import androidx.camera.camera2.pipe.InputStream
import androidx.camera.camera2.pipe.OutputId
import androidx.camera.camera2.pipe.OutputStream
import androidx.camera.camera2.pipe.StreamFormat
import androidx.camera.camera2.pipe.StreamGraph
import androidx.camera.camera2.pipe.StreamId
import androidx.camera.camera2.pipe.compat.Api24Compat
import androidx.camera.camera2.pipe.config.CameraGraphScope
import javax.inject.Inject
import kotlinx.atomicfu.atomic

private val streamIds = atomic(0)
internal fun nextStreamId(): StreamId = StreamId(streamIds.incrementAndGet())

private val outputIds = atomic(0)
internal fun nextOutputId(): OutputId = OutputId(outputIds.incrementAndGet())

private val configIds = atomic(0)
internal fun nextConfigId(): OutputConfigId = OutputConfigId(configIds.incrementAndGet())

private val groupIds = atomic(0)
internal fun nextGroupId(): Int = groupIds.incrementAndGet()

/**
 * This object keeps track of which surfaces have been configured for each stream. In addition,
 * it will keep track of which surfaces have changed or replaced so that the CaptureSession can be
 * reconfigured if the configured surfaces change.
 */
@CameraGraphScope
internal class StreamGraphImpl @Inject constructor(
    cameraMetadata: CameraMetadata,
    graphConfig: CameraGraph.Config
) : StreamGraph {
    private val _streamMap: Map<CameraStream.Config, CameraStream>

    internal val outputConfigs: List<OutputConfig>

    // TODO: Build InputStream(s)
    override val input: InputStream? = null
    override val streams: List<CameraStream>
    override val streamIds: Set<StreamId>
    override val outputs: List<OutputStream>

    override fun get(config: CameraStream.Config): CameraStream? = _streamMap[config]

    init {
        val outputConfigListBuilder = mutableListOf<OutputConfig>()
        val outputConfigMap = mutableMapOf<OutputStream.Config, OutputConfig>()

        val streamListBuilder = mutableListOf<CameraStream>()
        val streamMapBuilder = mutableMapOf<CameraStream.Config, CameraStream>()

        val deferredOutputsAllowed = computeIfDeferredStreamsAreSupported(
            cameraMetadata,
            graphConfig
        )

        // Compute groupNumbers for buffer sharing.
        val groupNumbers = mutableMapOf<CameraStream.Config, Int>()
        for (group in graphConfig.streamSharingGroups) {
            check(group.size > 1)
            val surfaceGroupId = computeNextSurfaceGroupId(graphConfig)
            for (config in group) {
                check(!groupNumbers.containsKey(config))
                groupNumbers[config] = surfaceGroupId
            }
        }

        // Create outputConfigs. If outputs are shared there can be fewer entries in map than there
        // are streams.
        for (streamConfig in graphConfig.streams) {
            for (output in streamConfig.outputs) {
                if (outputConfigMap.containsKey(output)) {
                    continue
                }

                @SuppressWarnings("SyntheticAccessor")
                val outputConfig = OutputConfig(
                    nextConfigId(),
                    output.size,
                    output.format,
                    output.camera ?: graphConfig.camera,
                    groupNumber = groupNumbers[streamConfig],
                    deferredOutputType = if (deferredOutputsAllowed) {
                        (output as? OutputStream.Config.LazyOutputConfig)?.outputType
                    } else {
                        null
                    },
                    externalOutputConfig =
                    (output as? OutputStream.Config.ExternalOutputConfig)?.output
                )
                outputConfigMap[output] = outputConfig
                outputConfigListBuilder.add(outputConfig)
            }
        }

        // Build the streams
        for (streamConfigIdx in graphConfig.streams.indices) {
            val streamConfig = graphConfig.streams[streamConfigIdx]

            val outputs = streamConfig.outputs.map {
                val outputConfig = outputConfigMap[it]!!

                @SuppressWarnings("SyntheticAccessor")
                val outputStream = OutputStreamImpl(
                    nextOutputId(),
                    outputConfig.size,
                    outputConfig.format,
                    outputConfig.camera
                )
                outputStream
            }

            val stream = CameraStream(nextStreamId(), outputs)
            streamMapBuilder[streamConfig] = stream
            streamListBuilder.add(stream)
            for (output in outputs) {
                output.stream = stream
            }
            for (cameraOutputConfig in streamConfig.outputs) {
                outputConfigMap[cameraOutputConfig]!!.streamBuilder.add(stream)
            }
        }

        // TODO: Sort outputs by type to try and put the viewfinder output first in the list
        //   This is important as some devices assume that the first surface is the viewfinder and
        //   will treat it differently.

        streams = streamListBuilder
        streamIds = streams.map { it.id }.toSet()
        _streamMap = streamMapBuilder
        outputs = streams.flatMap { it.outputs }
        outputConfigs = outputConfigListBuilder
    }

    @Suppress("SyntheticAccessor") // StreamId generates a synthetic constructor
    class OutputConfig(
        val id: OutputConfigId,
        val size: Size,
        val format: StreamFormat,
        val camera: CameraId,
        val groupNumber: Int?,
        val externalOutputConfig: OutputConfiguration?,
        val deferredOutputType: OutputStream.OutputType?,
    ) {
        internal val streamBuilder = mutableListOf<CameraStream>()
        val streams: List<CameraStream>
            get() = streamBuilder
        val deferrable: Boolean
            get() = deferredOutputType != null
        val surfaceSharing = streamBuilder.size > 1
        override fun toString(): String = id.toString()
    }

    @Suppress("SyntheticAccessor") // OutputId generates a synthetic constructor
    private class OutputStreamImpl(
        override val id: OutputId,
        override val size: Size,
        override val format: StreamFormat,
        override val camera: CameraId,
    ) : OutputStream {
        override lateinit var stream: CameraStream
        override fun toString(): String = id.toString()
    }

    interface SurfaceListener {
        fun onSurfaceMapUpdated(surfaces: Map<StreamId, Surface>)
    }

    private fun computeNextSurfaceGroupId(graphConfig: CameraGraph.Config): Int {
        // If there are any existing surfaceGroups, make sure the groups we define do not overlap
        // with any existing values.
        val existingGroupNumbers: List<Int> = readExistingGroupNumbers(graphConfig.streams)

        // Loop until we produce a groupId that was not already used.
        var number = nextGroupId()
        while (existingGroupNumbers.contains(number)) {
            number = nextGroupId()
        }
        return number
    }

    private fun readExistingGroupNumbers(outputs: List<CameraStream.Config>): List<Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            outputs
                .flatMap { it.outputs }
                .filterIsInstance<OutputStream.Config.ExternalOutputConfig>()
                .fold(mutableListOf()) { values, config ->
                    val groupId = Api24Compat.getSurfaceGroupId(config.output)
                    if (!values.contains(groupId)) {
                        values.add(groupId)
                    }
                    values
                }
        } else {
            emptyList()
        }
    }

    private fun computeIfDeferredStreamsAreSupported(
        cameraMetadata: CameraMetadata,
        graphConfig: CameraGraph.Config
    ): Boolean {
        val hardwareLevel = cameraMetadata[INFO_SUPPORTED_HARDWARE_LEVEL]
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            graphConfig.sessionMode == CameraGraph.OperatingMode.NORMAL &&
            hardwareLevel != INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY &&
            hardwareLevel != INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
                    hardwareLevel != INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL
                )
    }

    override fun toString(): String {
        return "StreamGraphImpl $_streamMap"
    }
}

@JvmInline
internal value class OutputConfigId(val value: Int) {
    override fun toString(): String = "OutputConfig-$value"
}