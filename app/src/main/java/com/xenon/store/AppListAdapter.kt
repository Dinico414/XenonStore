package com.xenon.store

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_UNINSTALL_PACKAGE
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.updateLayoutParams
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
        fun installButtonClicked(appItem: AppItem, position: Int)
        fun uninstallButtonClicked(appItem: AppItem, position: Int)
        fun openButtonClicked(appItem: AppItem, position: Int)
    }

    @Suppress("DEPRECATION")
    class AppListViewHolder(
        private val context: Context,
        private val binding: AppItemCellBinding,
        private val listener: AppItemListener
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bindItem(appItem: AppItem, position: Int) {
            val appName = appItem.getName(Util.getCurrentLanguage(context.resources))
            binding.name.text = appName

            binding.actionButton.setOnClickListener {
                listener.installButtonClicked(appItem, position)
            }

            binding.open.setOnClickListener {
                listener.openButtonClicked(appItem, position)
            }

            binding.delete.setOnClickListener {
                listener.uninstallButtonClicked(appItem, position)
            }

            val drawableId = appItem.getDrawableId(context)
            if (drawableId != 0) {
                Log.d("icon", "${appName}: Found drawable for ${appItem.iconPath}")
                binding.icon.setImageDrawable(
                    ResourcesCompat.getDrawable(
                        context.resources,
                        drawableId,
                        null
                    )
                )
            } else {
                Log.d("icon", "${appName}: No drawable found for ${appItem.iconPath}")
            }

            handleState(appItem)
        }

        fun handleState(appItem: AppItem) {
//            Log.d("bindItem", appItem.id.toString() + " " + appItem.name)
//            Log.d("bindItem", appItem.state.toString())
            var progressBarVisibility = View.GONE
            var showVersion = false

            when (appItem.state) {
                AppEntryState.NOT_INSTALLED -> {
                    binding.actionButton.text = context.getString(R.string.install)
                    binding.frameAction.visibility = View.VISIBLE
                    binding.buttonLayout.visibility = View.GONE
                    setButtonLayoutMarginStart(0)
                }

                AppEntryState.DOWNLOADING -> {
                    binding.actionButton.text = ""
                    binding.progressbar.progress = appItem.bytesDownloaded.toInt()
                    binding.progressbar.max = appItem.fileSize.toInt()
                    progressBarVisibility = View.VISIBLE
                    showVersion = appItem.isOutdated()
                    binding.frameAction.visibility = View.VISIBLE
                    binding.buttonLayout.visibility = if (appItem.isOutdated()) View.VISIBLE else View.GONE
                    setButtonLayoutMarginStart(10)
                }

                AppEntryState.INSTALLED -> {
                    binding.frameAction.visibility = View.GONE
                    binding.buttonLayout.visibility = View.VISIBLE
                    setButtonLayoutMarginStart(0)
                }

                // TODO: Remove INSTALLED_AND_OUTDATED in favor of appItem.isOutdated()
                AppEntryState.INSTALLED_AND_OUTDATED -> {
                    binding.actionButton.text = context.getString(R.string.update)
                    showVersion = true
                    binding.frameAction.visibility = View.VISIBLE
                    binding.buttonLayout.visibility = View.VISIBLE
                    setButtonLayoutMarginStart(10)
                }
            }

            if (progressBarVisibility == View.GONE) binding.progressbar.progress = 0
            binding.progressbar.visibility = progressBarVisibility

            if (showVersion) handleNewVersion(appItem)
            else binding.version.visibility = View.GONE
        }
        private fun AppListViewHolder.setButtonLayoutMarginStart(marginStartDp: Int) {
            binding.buttonLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginStart = (marginStartDp * context.resources.displayMetrics.density).toInt()
            }
        }

        private fun handleNewVersion(appItem: AppItem) {
            if (appItem.newVersion.isNotEmpty()) {
                binding.version.visibility = View.VISIBLE
                binding.installedVersion.apply {
                    text = context.getString(R.string.version_with_prefix, appItem.installedVersion)
                    paintFlags = Paint.STRIKE_THRU_TEXT_FLAG
                    alpha = 0.5f
                    visibility = View.VISIBLE
                }
                binding.newVersion.apply {
                    text = context.getString(R.string.version_with_prefix, appItem.newVersion)
                    visibility = View.VISIBLE
                }
            } else {
                binding.version.visibility = View.GONE
            }
        }
    }
}