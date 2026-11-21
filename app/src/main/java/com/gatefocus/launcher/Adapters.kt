package com.gatefocus.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.gatefocus.launcher.data.PdfItem
import com.gatefocus.launcher.data.VideoItem

class VideoAdapter(
    private val videos: List<VideoItem>,
    private val onVideoClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumbnail: ImageView = view.findViewById(R.id.ivThumbnail)
        val tvCompletedBadge: TextView = view.findViewById(R.id.tvCompletedBadge)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvFolder: TextView = view.findViewById(R.id.tvFolder)
        val pbVideoProgress: ProgressBar = view.findViewById(R.id.pbVideoProgress)
        val tvProgressText: TextView = view.findViewById(R.id.tvProgressText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videos[position]
        holder.tvTitle.text = video.title
        holder.tvFolder.text = video.playlistName

        if (video.thumbnailFile != null && video.thumbnailFile.exists()) {
            holder.ivThumbnail.load(video.thumbnailFile)
        } else {
            holder.ivThumbnail.setImageResource(android.R.drawable.ic_media_play)
        }

        if (video.isCompleted) {
            holder.tvCompletedBadge.visibility = View.VISIBLE
            holder.pbVideoProgress.progress = 100
            holder.tvProgressText.text = "Completed"
        } else if (video.progressMs > 0) {
            holder.tvCompletedBadge.visibility = View.GONE
            holder.pbVideoProgress.progress = video.progressPercent
            val currentMins = video.progressMs / 60000
            val totalMins = video.durationMs / 60000
            holder.tvProgressText.text = "Resuming: $currentMins min / $totalMins min (${video.progressPercent}%)"
        } else {
            holder.tvCompletedBadge.visibility = View.GONE
            holder.pbVideoProgress.progress = 0
            holder.tvProgressText.text = "Not watched yet"
        }

        holder.itemView.setOnClickListener { onVideoClick(video) }
    }

    override fun getItemCount(): Int = videos.size
}

class PdfAdapter(
    private val pdfs: List<PdfItem>,
    private val onPdfClick: (PdfItem) -> Unit
) : RecyclerView.Adapter<PdfAdapter.PdfViewHolder>() {

    class PdfViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvPdfTitle: TextView = view.findViewById(R.id.tvPdfTitle)
        val tvPdfInfo: TextView = view.findViewById(R.id.tvPdfInfo)
        val btnOpenPdf: Button = view.findViewById(R.id.btnOpenPdf)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf, parent, false)
        return PdfViewHolder(view)
    }

    override fun onBindViewHolder(holder: PdfViewHolder, position: Int) {
        val pdf = pdfs[position]
        holder.tvPdfTitle.text = pdf.title
        val folderPrefix = if (pdf.folderName.isNotBlank() && pdf.folderName != "Workbooks") "${pdf.folderName} • " else ""
        holder.tvPdfInfo.text = if (pdf.lastPage > 0) "${folderPrefix}Last solved: Page ${pdf.lastPage + 1}" else "${folderPrefix}Workbook • Tap to solve"

        val openAction = { onPdfClick(pdf) }
        holder.itemView.setOnClickListener { openAction() }
        holder.btnOpenPdf.setOnClickListener { openAction() }
    }

    override fun getItemCount(): Int = pdfs.size
}
