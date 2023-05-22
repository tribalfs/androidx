/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.wear.tiles.client;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import androidx.annotation.NonNull;
import androidx.wear.protolayout.ResourceBuilders;
import androidx.wear.tiles.RequestBuilders;
import androidx.wear.tiles.TileBuilders;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;

/** Client to connect and interact with a TileService. */
public interface TileClient {
    /** Gets the API version supported by the connected TileService. */
    @NonNull
    ListenableFuture<Integer> requestApiVersion();

    /** Request a tile payload from the connected TileService. */
    @NonNull
    ListenableFuture<TileBuilders.Tile> requestTile(
            @NonNull RequestBuilders.TileRequest requestParams);

    /** Request a resource bundle from the connected TileService. */
    @NonNull
    @SuppressWarnings("deprecation") // For backward compatibility
    default ListenableFuture<ResourceBuilders.Resources> requestTileResourcesAsync(
            @NonNull RequestBuilders.ResourcesRequest requestParams) {
        return FluentFuture.from(requestResources(requestParams))
                .transform(
                        res -> ResourceBuilders.Resources.fromProto(res.toProto()),
                        directExecutor());
    }

    /**
     * Request a resource bundle from the connected TileService.
     *
     * @deprecated Use {@link #requestTileResourcesAsync(RequestBuilders.ResourcesRequest)} instead.
     */
    @NonNull
    @Deprecated
    ListenableFuture<androidx.wear.tiles.ResourceBuilders.Resources> requestResources(
            @NonNull RequestBuilders.ResourcesRequest requestParams);

    /** Send a Tile Added notification to the connected TileService. */
    @NonNull
    ListenableFuture<Void> sendOnTileAddedEvent();

    /** Send a Tile Removed notification to the connected TileService. */
    @NonNull
    ListenableFuture<Void> sendOnTileRemovedEvent();

    /** Send a Tile Enter notification to the connected TileService. */
    @NonNull
    ListenableFuture<Void> sendOnTileEnterEvent();

    /** Send a Tile Leave notification to the connected TileService. */
    @NonNull
    ListenableFuture<Void> sendOnTileLeaveEvent();
}
