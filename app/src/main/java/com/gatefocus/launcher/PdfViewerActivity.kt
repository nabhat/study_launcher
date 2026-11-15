package com.gatefocus.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gatefocus.launcher.data.StudyProgressManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfViewerActivity : AppCompatActivity() {

    private lateinit var ivPdfPage: ImageView
    private lateinit var drawingView: DrawingView
    private lateinit var tvPageIndicator: TextView
    private lateinit var progressManager: StudyProgressManager

    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var currentPage: PdfRenderer.Page? = null

    private var pdfPath: String = ""
    private var currentPageIndex: Int = 0
    private var totalPages: Int = 0

    // In-memory cache for handwritten scratchpad drawings per page
    private val pageDrawings = HashMap<Int, Bitmap>()

    companion object {
        const val EXTRA_PDF_PATH = "extra_pdf_path"
        const val EXTRA_PDF_TITLE = "extra_pdf_title"

        fun start(context: Context, filePath: String, title: String) {
            val intent = Intent(context, PdfViewerActivity::class.java).apply {
                putExtra(EXTRA_PDF_PATH, filePath)
                putExtra(EXTRA_PDF_TITLE, title)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        pdfPath = intent.getStringExtra(EXTRA_PDF_PATH) ?: ""
        val pdfTitle = intent.getStringExtra(EXTRA_PDF_TITLE) ?: "Workbook"

        progressManager = StudyProgressManager(this)

        ivPdfPage = findViewById(R.id.ivPdfPage)
        drawingView = findViewById(R.id.drawingView)
        tvPageIndicator = findViewById(R.id.tvPageIndicator)
        val tvPdfName = findViewById<TextView>(R.id.tvPdfName)
        val btnBack = findViewById<ImageButton>(R.id.btnPdfBack)

        tvPdfName.text = pdfTitle
        btnBack.setOnClickListener { finish() }

        val btnPrev = findViewById<Button>(R.id.btnPrevPage)
        val btnNext = findViewById<Button>(R.id.btnNextPage)
        val btnPen = findViewById<Button>(R.id.btnPen)
        val btnEraser = findViewById<Button>(R.id.btnEraser)
        val btnClear = findViewById<Button>(R.id.btnClearDraw)

        btnPrev.setOnClickListener { showPreviousPage() }
        btnNext.setOnClickListener { showNextPage() }

        btnPen.setOnClickListener {
            drawingView.setPenColor(Color.parseColor("#38BDF8"))
            btnPen.setBackgroundColor(Color.parseColor("#38BDF8"))
            btnEraser.setBackgroundColor(Color.parseColor("#1E293B"))
        }

        btnEraser.setOnClickListener {
            drawingView.setEraserMode()
            btnEraser.setBackgroundColor(Color.parseColor("#38BDF8"))
            btnPen.setBackgroundColor(Color.parseColor("#1E293B"))
        }

        btnClear.setOnClickListener {
            drawingView.clearCanvas()
            pageDrawings.remove(currentPageIndex)
            Toast.makeText(this, "Notes cleared for this page", Toast.LENGTH_SHORT).show()
        }

        openPdf()
    }

    private fun openPdf() {
        val file = File(pdfPath)
        if (!file.exists()) {
            Toast.makeText(this, "PDF file not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor!!)
            totalPages = pdfRenderer!!.pageCount

            val savedPage = progressManager.getPdfPage(pdfPath)
            currentPageIndex = if (savedPage in 0 until totalPages) savedPage else 0

            renderCurrentPage()
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening PDF: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun renderCurrentPage() {
        val renderer = pdfRenderer ?: return
        if (currentPageIndex < 0 || currentPageIndex >= totalPages) return

        // Save current drawings before flipping page
        drawingView.getBitmap()?.let {
            pageDrawings[currentPageIndex] = it.copy(Bitmap.Config.ARGB_8888, true)
        }

        currentPage?.close()
        val page = renderer.openPage(currentPageIndex)
        currentPage = page

        lifecycleScope.launch(Dispatchers.Default) {
            val bitmap = Bitmap.createBitmap(
                page.width * 2, // 2x supersampling for crisp reading on Tab A9
                page.height * 2,
                Bitmap.Config.ARGB_8888
            )
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            withContext(Dispatchers.Main) {
                ivPdfPage.setImageBitmap(bitmap)
                tvPageIndicator.text = "Page ${currentPageIndex + 1} / $totalPages"

                // Restore previous handwriting for this page, or clear
                val savedDrawing = pageDrawings[currentPageIndex]
                drawingView.setBitmap(savedDrawing)

                progressManager.savePdfPage(pdfPath, currentPageIndex)
            }
        }
    }

    private fun showNextPage() {
        if (currentPageIndex < totalPages - 1) {
            currentPageIndex++
            renderCurrentPage()
        }
    }

    private fun showPreviousPage() {
        if (currentPageIndex > 0) {
            currentPageIndex--
            renderCurrentPage()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentPage?.close()
        pdfRenderer?.close()
        fileDescriptor?.close()
    }
}
