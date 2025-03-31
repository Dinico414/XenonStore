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

    @Suppress("DEPRECATION")
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

            binding.delete.setOnClickListener {
                openUninstallDialog(appItem.packageName)
            }

            val drawableId = appItem.getDrawableId(context)
            if (drawableId != 0) {
                Log.d("icon", "${appItem.name}: Found drawable for ${appItem.iconPath}")
                binding.icon.setImageDrawable(
                    ResourcesCompat.getDrawable(
                        context.resources,
                        drawableId,
                        null
                    )
                )
            } else {
                Log.d("icon", "${appItem.name}: No drawable found for ${appItem.iconPath}")
            }

            handleState(appItem)
        }

        fun handleState(appItem: AppItem) {
            Log.d("bindItem", appItem.id.toString() + " " + appItem.name)
            Log.d("bindItem", appItem.state.toString())
            var progressBarVisibility = View.GONE
            var showVersion = false

            when (appItem.state) {
                AppEntryState.NOT_INSTALLED -> {
                    binding.actionButton.text = context.getString(R.string.install)
                }

                AppEntryState.DOWNLOADING -> {
                    binding.actionButton.text = ""
                    binding.progressbar.progress = appItem.bytesDownloaded.toInt()
                    binding.progressbar.max = appItem.fileSize.toInt()
                    progressBarVisibility = View.VISIBLE
                    showVersion = true
                }

                AppEntryState.INSTALLED -> {
                    binding.actionButton.text = context.getString(R.string.open)
                }

                AppEntryState.INSTALLED_AND_OUTDATED -> {
                    binding.actionButton.text = context.getString(R.string.update)
                    showVersion = true
                }
            }

            if (progressBarVisibility == View.GONE) binding.progressbar.progress = 0
            binding.progressbar.visibility = progressBarVisibility

            if (showVersion) handleNewVersion(appItem)
            else binding.version.visibility = View.GONE
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

        private fun openUninstallDialog(packageName: String) {
            val intent = Intent(ACTION_UNINSTALL_PACKAGE).apply {
                data = Uri.parse("package:$packageName")
            }
            context.startActivity(intent)
        }
    }
}