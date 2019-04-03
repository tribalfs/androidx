package com.google.r4a.examples.explorerapp.ui.components

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.r4a.Children
import com.google.r4a.Composable
import com.google.r4a.composeInto
import com.google.r4a.composer

open class Recycler(context: Context): RecyclerView(context) {

    lateinit var getItemCount: () -> Int

    @Children
    var composeItemWithType: @Composable() ((position: Int, type: Int) -> Unit) = { _, _ ->  }

    @Children
    fun setComposeItem(composeItem: @Composable() ((position: Int) -> Unit)) {
        composeItemWithType = { position, _ -> <composeItem position /> }
    }
    var getItemViewType: ((Int) -> Int)? = null

    class TypedViewHolder(val viewType: Int, context: Context): RecyclerView.ViewHolder(LinearLayout(context))

    private val myAdapter = object: RecyclerView.Adapter<TypedViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TypedViewHolder {
            return TypedViewHolder(viewType, parent.getContext())
        }

        override fun getItemCount(): Int {
            return this@Recycler.getItemCount()
        }

        override fun onBindViewHolder(holder: TypedViewHolder, position: Int) {
            val view = holder.itemView as LinearLayout
            val type = holder.viewType

            view.composeInto {
                <composeItemWithType position type />
            }
        }

        override fun getItemViewType(position: Int): Int {
            val fn = getItemViewType
            return if (fn != null) fn(position) else super.getItemViewType(position)
        }
    }

    init {
        setAdapter(myAdapter)
    }
}
