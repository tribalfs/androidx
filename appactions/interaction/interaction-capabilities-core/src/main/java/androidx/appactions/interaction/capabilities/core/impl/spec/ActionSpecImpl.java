/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.appactions.interaction.capabilities.core.impl.spec;

import static androidx.appactions.interaction.capabilities.core.impl.utils.ImmutableCollectors.toImmutableList;

import androidx.annotation.NonNull;
import androidx.appactions.interaction.capabilities.core.impl.BuilderOf;
import androidx.appactions.interaction.capabilities.core.impl.exceptions.StructConversionException;
import androidx.appactions.interaction.proto.AppActionsContext.AppAction;
import androidx.appactions.interaction.proto.FulfillmentResponse.StructuredOutput;
import androidx.appactions.interaction.proto.ParamValue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/** The implementation of {@code ActionSpec} interface. */
final class ActionSpecImpl<
        PropertyT, ArgumentT, ArgumentBuilderT extends BuilderOf<ArgumentT>, OutputT>
        implements ActionSpec<PropertyT, ArgumentT, OutputT> {

    private final String mCapabilityName;
    private final Supplier<ArgumentBuilderT> mArgumentBuilderSupplier;
    private final List<ParamBinding<PropertyT, ArgumentT, ArgumentBuilderT>> mParamBindingList;
    private final Map<String, Function<OutputT, List<ParamValue>>> mOutputBindings;

    ActionSpecImpl(
            String capabilityName,
            Supplier<ArgumentBuilderT> argumentBuilderSupplier,
            List<ParamBinding<PropertyT, ArgumentT, ArgumentBuilderT>> paramBindingList,
            Map<String, Function<OutputT, List<ParamValue>>> outputBindings) {
        this.mCapabilityName = capabilityName;
        this.mArgumentBuilderSupplier = argumentBuilderSupplier;
        this.mParamBindingList = paramBindingList;
        this.mOutputBindings = outputBindings;
    }

    @NonNull
    @Override
    public AppAction convertPropertyToProto(PropertyT property) {
        return AppAction.newBuilder()
                .setName(mCapabilityName)
                .addAllParams(
                        mParamBindingList.stream()
                                .map(binding -> binding.paramGetter().apply(property))
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .collect(toImmutableList()))
                .build();
    }

    @NonNull
    @Override
    public ArgumentT buildArgument(Map<String, List<ParamValue>> args)
            throws StructConversionException {
        ArgumentBuilderT argumentBuilder = mArgumentBuilderSupplier.get();
        for (ParamBinding<PropertyT, ArgumentT, ArgumentBuilderT> binding : mParamBindingList) {
            List<ParamValue> paramValues = args.get(binding.name());
            if (paramValues == null) {
                continue;
            }
            try {
                binding.argumentSetter().setArgument(argumentBuilder, paramValues);
            } catch (StructConversionException e) {
                // Wrap the exception with a more meaningful error message.
                throw new StructConversionException(
                        String.format(
                                "Failed to parse parameter '%s' from assistant because of "
                                        + "failure: %s",
                                binding.name(), e.getMessage()));
            }
        }
        return argumentBuilder.build();
    }

    @Override
    public StructuredOutput convertOutputToProto(OutputT output) {
        StructuredOutput.Builder outputBuilder = StructuredOutput.newBuilder();
        for (Map.Entry<String, Function<OutputT, List<ParamValue>>> entry :
                mOutputBindings.entrySet()) {
            outputBuilder.addOutputValues(
                    StructuredOutput.OutputValue.newBuilder()
                            .setName(entry.getKey())
                            .addAllValues(entry.getValue().apply(output))
                            .build());
        }
        return outputBuilder.build();
    }
}
