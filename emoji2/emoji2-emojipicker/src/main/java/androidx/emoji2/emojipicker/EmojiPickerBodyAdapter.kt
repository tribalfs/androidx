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

package androidx.emoji2.emojipicker

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import androidx.annotation.LayoutRes
import androidx.annotation.UiThread
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat
import androidx.emoji2.emojipicker.Extensions.toItemType
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.tracing.Trace

/** RecyclerView adapter for emoji body.  */
internal class EmojiPickerBodyAdapter(
    private val context: Context,
    private val emojiGridColumns: Int,
    private val emojiGridRows: Float,
    private val stickyVariantProvider: StickyVariantProvider,
    private val emojiPickerItems: EmojiPickerItems,
    private val onEmojiPickedListener: EmojiPickerBodyAdapter.(EmojiViewItem) -> Unit,
) : Adapter<ViewHolder>() {
    private val layoutInflater: LayoutInflater = LayoutInflater.from(context)

    @UiThread
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        Trace.beginSection("EmojiPickerBodyAdapter.onCreateViewHolder")
        return try {
            when (viewType.toItemType()) {
                ItemType.CATEGORY_TITLE -> createSimpleHolder(R.layout.category_text_view, parent)
                ItemType.PLACEHOLDER_TEXT -> createSimpleHolder(
                    R.layout.emoji_picker_empty_category_text_view, parent
                ) {
                    minimumHeight = (parent.measuredHeight / emojiGridRows).toInt()
                }

                ItemType.EMOJI -> {
                    EmojiViewHolder(context,
                        parent,
                        layoutInflater,
                        getParentWidth(parent) / emojiGridColumns,
                        (parent.measuredHeight / emojiGridRows).toInt(),
                        stickyVariantProvider,
                        onEmojiPickedListener = { emojiViewItem ->
                            onEmojiPickedListener(emojiViewItem)
                        },
                        onEmojiPickedFromPopupListener = { emoji ->
                            with(
                                emojiPickerItems.getBodyItem(bindingAdapterPosition)
                                    as EmojiViewData
                            ) {
                                if (updateToSticky) {
                                    this.emoji = emoji
                                    notifyItemChanged(bindingAdapterPosition)
                                }
                            }
                        })
                }

                ItemType.PLACEHOLDER_EMOJI -> object : ViewHolder(View(context)) {}
            }
        } finally {
            Trace.endSection()
        }
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val item = emojiPickerItems.getBodyItem(position)
        when (getItemViewType(position).toItemType()) {
            ItemType.CATEGORY_TITLE -> ViewCompat.requireViewById<AppCompatTextView>(
                viewHolder.itemView, R.id.category_name
            ).text = (item as CategoryTitle).title

            ItemType.PLACEHOLDER_TEXT -> ViewCompat.requireViewById<AppCompatTextView>(
                viewHolder.itemView, R.id.emoji_picker_empty_category_view
            ).text = (item as PlaceholderText).text

            ItemType.EMOJI -> {
                (viewHolder as EmojiViewHolder).bindEmoji((item as EmojiViewData).emoji)
            }

            ItemType.PLACEHOLDER_EMOJI -> {}
        }
    }

    override fun getItemCount(): Int {
        return emojiPickerItems.size
    }

    override fun getItemViewType(position: Int): Int {
        return emojiPickerItems.getBodyItem(position).viewType
    }

    private fun getParentWidth(parent: ViewGroup): Int {
        return parent.measuredWidth - parent.paddingLeft - parent.paddingRight
    }

    private fun createSimpleHolder(
        @LayoutRes layoutId: Int,
        parent: ViewGroup,
        init: (View.() -> Unit)? = null,
    ) = object : ViewHolder(layoutInflater.inflate(
        layoutId, parent, /* attachToRoot = */ false
    ).also {
        it.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        )
        init?.invoke(it)
    }) {}
}