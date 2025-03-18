package com.xenon.store

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.xenon.store.databinding.AppItemCellBinding

class AppListAdapter(
    private val context: Context,
    var appItems: List<AppItem>,
    private val listener: AppItemListener,
) : RecyclerView.Adapter<AppListAdapter.AppListViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppListViewHolder {
        val from = LayoutInflater.from(parent.context)
        val binding = AppItemCellBinding.inflate(from, parent, false)
        return AppListViewHolder(parent.context, binding, listener)
    }

    override fun onBindViewHolder(holder: AppListViewHolder, position: Int) {
        holder.bindTaskItem(appItems[position], position)

        val horizontalMarginInPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            10.toFloat(),
            context.resources.displayMetrics
        ).toInt()

        val layoutParams = holder.itemView.layoutParams as RecyclerView.LayoutParams
        layoutParams.setMargins(horizontalMarginInPx, 0, horizontalMarginInPx, horizontalMarginInPx)
        holder.itemView.layoutParams = layoutParams
    }

    override fun getItemCount(): Int = appItems.size

    interface AppItemListener {
        fun buttonClicked(appItem: AppItem, position: Int)
    }

    class AppListViewHolder(
        private val context: Context,
        private val binding: AppItemCellBinding,
        private val listener: AppItemListener
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bindTaskItem(appItem: AppItem, position: Int) {
            binding.name.text = appItem.name

            binding.actionButton.setOnClickListener {
                listener.buttonClicked(appItem, position)
            }

            when (appItem.state) {
                AppEntryState.NOT_INSTALLED -> {
                    binding.actionButton.text = context.getString(R.string.install)
                }
                AppEntryState.DOWNLOADING -> {
                    binding.actionButton.text = ""
                    binding.progressbar.max = appItem.fileSize
                    binding.progressbar.progress = appItem.bytesDownloaded
                }
                AppEntryState.INSTALLED -> {
                    binding.actionButton.text = context.getString(R.string.open)
                }
                AppEntryState.INSTALLED_AND_OUTDATED -> {
                    binding.actionButton.text = context.getString(R.string.update)
                }
            }
        }
    }
}
