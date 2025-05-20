package com.bidware.fragments

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import com.bidware.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.graphics.Bitmap

class DocumentPreviewDialogFragment : DialogFragment() {
    private lateinit var documentTitle: String
    private lateinit var documentBase64: String
    private val TAG = "DocumentPreview"

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_BASE64 = "base64"

        fun newInstance(title: String, base64: String): DocumentPreviewDialogFragment {
            val fragment = DocumentPreviewDialogFragment()
            val args = Bundle()
            args.putString(ARG_TITLE, title)
            args.putString(ARG_BASE64, base64)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            documentTitle = it.getString(ARG_TITLE, "Document")
            documentBase64 = it.getString(ARG_BASE64, "")
            Log.d(TAG, "Document base64 length: ${documentBase64.length}")
        }

        // Set dialog style
        setStyle(STYLE_NORMAL, R.style.FullScreenDialogStyle)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_document_preview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Set up toolbar with title and close button
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        val titleText = view.findViewById<TextView>(R.id.tvTitle)
        val pdfImageView = view.findViewById<android.widget.ImageView>(R.id.pdfImageView)
        val progressBar = view.findViewById<android.widget.ProgressBar>(R.id.progressBar)
        
        titleText.text = documentTitle
        toolbar.setNavigationOnClickListener { dismiss() }
        
        // Load document from base64
        if (documentBase64.isNotEmpty()) {
            progressBar.visibility = View.VISIBLE
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d(TAG, "Starting to decode base64 document")
                    Log.d(TAG, "Base64 length: ${documentBase64.length}")
                    Log.d(TAG, "Base64 sample: ${documentBase64.take(100)}")
                    
                    // Clean the base64 string if it contains format prefix
                    val cleanBase64 = if (documentBase64.contains(",")) {
                        documentBase64.substring(documentBase64.indexOf(",") + 1)
                    } else {
                        documentBase64
                    }
                    
                    // Decode base64 to bytes
                    val decodedBytes = try {
                        Base64.decode(cleanBase64, Base64.DEFAULT)
                    } catch (e: Exception) {
                        Log.e(TAG, "Base64 decode failed", e)
                        withContext(Dispatchers.Main) {
                            progressBar.visibility = View.GONE
                            Toast.makeText(context, "Document data is corrupted or incomplete.", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                    Log.d(TAG, "Successfully decoded ${decodedBytes.size} bytes")
                    if (decodedBytes.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            progressBar.visibility = View.GONE
                            Toast.makeText(context, "Document data is empty after decoding.", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                    // Create a temporary file to store the PDF
                    val file = try {
                        createTempFileFromBytes(requireContext(), decodedBytes, "document", ".pdf")
                    } catch (e: Exception) {
                        Log.e(TAG, "Temp file creation failed", e)
                        withContext(Dispatchers.Main) {
                            progressBar.visibility = View.GONE
                            Toast.makeText(context, "Failed to create file for document preview.", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                    Log.d(TAG, "Created temp file: ${file.absolutePath}, size: ${file.length()} bytes")
                    
                    withContext(Dispatchers.Main) {
                        try {
                            val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                            val pdfRenderer = PdfRenderer(fileDescriptor)
                            if (pdfRenderer.pageCount > 0) {
                                val page = pdfRenderer.openPage(0)
                                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                pdfImageView.setImageBitmap(bitmap)
                                page.close()
                            } else {
                                Toast.makeText(context, "PDF has no pages.", Toast.LENGTH_LONG).show()
                            }
                            pdfRenderer.close()
                            fileDescriptor.close()
                            progressBar.visibility = View.GONE
                        } catch (e: Exception) {
                            progressBar.visibility = View.GONE
                            Toast.makeText(context, "Error displaying PDF: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error decoding document", e)
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        Toast.makeText(context, "Error loading document: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            progressBar.visibility = View.GONE
            Toast.makeText(context, "No document data available", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Document base64 string is empty")
        }
    }
    
    private fun createTempFileFromBytes(context: Context, bytes: ByteArray, prefix: String, suffix: String): File {
        val cacheDir = context.cacheDir
        val tempFile = File.createTempFile(prefix, suffix, cacheDir)
        FileOutputStream(tempFile).use { 
            it.write(bytes) 
        }
        return tempFile
    }
    
    override fun onStart() {
        super.onStart()
        
        // Make dialog full width
        dialog?.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }
} 