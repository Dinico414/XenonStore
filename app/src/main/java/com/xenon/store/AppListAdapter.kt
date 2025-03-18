package com.xenon.store

import android.content.Context
import android.graphics.Paint
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import androidx.recyclerview.widget.RecyclerView
import com.xenon.store.databinding.AppItemCellBinding

enum class AppListChangeType {
    ALL,
    STATE_CHANGE,
}

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
        holder.bindItem(appItems[position], position)

        val horizontalMarginInPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            10.toFloat(),
            context.resources.displayMetrics
        ).toInt()

        val layoutParams = holder.itemView.layoutParams as RecyclerView.LayoutParams
        layoutParams.setMargins(horizontalMarginInPx, 0, horizontalMarginInPx, horizontalMarginInPx)
        holder.itemView.layoutParams = layoutParams
    }

    override fun onBindViewHolder(
        holder: AppListViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        // Selective update by using payloads parameter of this onBindViewHolder overload
        for (payload in payloads) {
            val changeType = payload as? AppListChangeType
            when (changeType) {
                AppListChangeType.ALL -> onBindViewHolder(holder, position)
                AppListChangeType.STATE_CHANGE -> holder.handleState(appItems[position])
                null -> {}
            }
            return
        }

        onBindViewHolder(holder, position)
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
        fun bindItem(appItem: AppItem, position: Int) {
            binding.name.text = appItem.name

            binding.actionButton.setOnClickListener {
                listener.buttonClicked(appItem, position)
            }

            handleState(appItem)
        }

        fun handleState(appItem: AppItem) {
            Log.d("bindItem", appItem.name)
            Log.d("bindItem", appItem.state.toString())
            var progressBarVisibility = View.GONE

            when (appItem.state) {
                AppEntryState.NOT_INSTALLED -> {
                    binding.actionButton.text = context.getString(R.string.install)
                }
                AppEntryState.DOWNLOADING -> {
                    binding.actionButton.text = ""

                    progressBarVisibility = View.VISIBLE
                    binding.progressbar.max = appItem.fileSize.toInt()
                    binding.progressbar.progress = appItem.bytesDownloaded.toInt()
                }
                AppEntryState.INSTALLED -> {
                    binding.actionButton.text = context.getString(R.string.open)
                }
                AppEntryState.INSTALLED_AND_OUTDATED -> {
                    binding.actionButton.text = context.getString(R.string.update)
                    handleNewVersion(appItem)
                }
            }
            binding.progressbar.visibility = progressBarVisibility
        }

        private fun handleNewVersion(appItem: AppItem) {
            if (appItem.newVersion != "") {
                binding.version.visibility = View.VISIBLE
                binding.installedVersion.apply {
                    text = "v.${appItem.installedVersion}"
                    paintFlags = Paint.STRIKE_THRU_TEXT_FLAG
                    alpha = 0.5f
                    visibility = View.VISIBLE
                }
                binding.newVersion.apply {
                    text = "v.${appItem.newVersion}"
                    visibility = View.VISIBLE
                }
            }
            else binding.version.visibility = View.GONE
        }

        private fun fadeIn(view: View) {
            val fadeIn = AlphaAnimation(0f, 1f)
            fadeIn.duration = 500
            view.startAnimation(fadeIn)
        }
    }
}
