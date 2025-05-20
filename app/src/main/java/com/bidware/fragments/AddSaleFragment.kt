package com.bidware.fragments

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bidware.R
import com.bidware.databinding.FragmentAddSaleBinding
import com.bidware.models.Sale
import com.bidware.utils.FirebaseUtils
import com.bidware.utils.ImageUtils
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import android.app.Activity
import android.content.Intent
import android.provider.MediaStore
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import android.util.Log

class AddSaleFragment : Fragment() {
    private var _binding: FragmentAddSaleBinding? = null
    private val binding get() = _binding!!

    private var selectedImageUri: Uri? = null
    private var rcBookUri: Uri? = null
    private var insuranceUri: Uri? = null
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private var editSale: Sale? = null
    private var isViewOnly: Boolean = false
    
    // Activity Result Launchers
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            result.data?.data?.let { uri ->
                if (checkFileSize(uri, isImage = true)) {
                    selectedImageUri = uri
                    displaySelectedImage(uri)
                    Toast.makeText(context, "Image selected", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Image too large. Please select an image under 5MB", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private val rcDocumentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            result.data?.data?.let { uri ->
                if (checkFileSize(uri, isImage = false)) {
                    if (isPdfFile(uri)) {
                        rcBookUri = uri
                        binding.btnAddRC.setText(R.string.rc_document_added)
                        binding.btnAddRC.setIconResource(android.R.drawable.ic_menu_upload)
                    } else {
                        Toast.makeText(context, "Please select a PDF file for RC Document", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "RC Document too large. Please select a file under 200KB to ensure it can be uploaded to our servers", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private val insuranceDocumentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            result.data?.data?.let { uri ->
                if (checkFileSize(uri, isImage = false)) {
                    if (isPdfFile(uri)) {
                        insuranceUri = uri
                        binding.btnAddInsurance.setText(R.string.insurance_document_added)
                        binding.btnAddInsurance.setIconResource(android.R.drawable.ic_menu_upload)
                    } else {
                        Toast.makeText(context, "Please select a PDF file for Insurance Document", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Insurance Document too large. Please select a file under 200KB to ensure it can be uploaded to our servers", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        private const val ARG_SALE = "sale"
        private const val ARG_VIEW_ONLY = "view_only"

        fun newInstance(sale: Sale?, viewOnly: Boolean = false): AddSaleFragment {
            val fragment = AddSaleFragment()
            val args = Bundle()
            if (sale != null) args.putParcelable(ARG_SALE, sale)
            args.putBoolean(ARG_VIEW_ONLY, viewOnly)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editSale = arguments?.getParcelable(ARG_SALE)
        isViewOnly = arguments?.getBoolean(ARG_VIEW_ONLY, false) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddSaleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupFuelTypeSpinner()
        setupDatePickers()
        setupImageUpload()
        setupDocumentUpload()
        setupSubmitButton()
        setupDeleteButton()
        if (editSale != null) fillForm(editSale!!)
        
        // If view only mode, disable all inputs
        if (isViewOnly) {
            disableAllInputs()
        }
    }

    private fun setupToolbar() {
        binding.toolbarAddSale.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    private fun setupFuelTypeSpinner() {
        val fuelTypes = resources.getStringArray(R.array.fuel_types)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, fuelTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFuelType.adapter = adapter
    }

    private fun setupDatePickers() {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val minStartCalendar = Calendar.getInstance().apply {
            timeInMillis = today.timeInMillis
            add(Calendar.DAY_OF_MONTH, 3)
        }
        val minStartDate = minStartCalendar.timeInMillis
        var selectedStartDate: Calendar? = null
        binding.etStartDate.setOnClickListener {
            val datePicker = DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    val selectedCalendar = Calendar.getInstance().apply {
                        set(year, month, day)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (selectedCalendar.timeInMillis < minStartDate) {
                        Toast.makeText(context, "Start date must be at least 3 days from today", Toast.LENGTH_SHORT).show()
                        binding.etStartDate.setText("")
                        return@DatePickerDialog
                    }
                    selectedStartDate = selectedCalendar
                    binding.etStartDate.setText(dateFormat.format(selectedCalendar.time))
                    binding.etEndDate.setText("")
                },
                minStartCalendar.get(Calendar.YEAR),
                minStartCalendar.get(Calendar.MONTH),
                minStartCalendar.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.datePicker.minDate = minStartDate
            datePicker.show()
        }
        binding.etEndDate.setOnClickListener {
            val startDate = selectedStartDate
            if (startDate == null) {
                Toast.makeText(context, "Please select a start date first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val minEndCalendar = Calendar.getInstance().apply {
                timeInMillis = startDate.timeInMillis
                add(Calendar.DAY_OF_MONTH, 7)
            }
            val minEndDate = minEndCalendar.timeInMillis
            val datePicker = DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    val selectedCalendar = Calendar.getInstance().apply {
                        set(year, month, day)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (selectedCalendar.timeInMillis < minEndDate) {
                        Toast.makeText(context, "End date must be at least 7 days after start date", Toast.LENGTH_SHORT).show()
                        binding.etEndDate.setText("")
                        return@DatePickerDialog
                    }
                    binding.etEndDate.setText(dateFormat.format(selectedCalendar.time))
                },
                minEndCalendar.get(Calendar.YEAR),
                minEndCalendar.get(Calendar.MONTH),
                minEndCalendar.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.datePicker.minDate = minEndDate
            datePicker.show()
        }
    }

    private fun setupImageUpload() {
        binding.btnAddImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
            }
            imagePickerLauncher.launch(intent)
        }
    }

    private fun setupDocumentUpload() {
        binding.btnAddRC.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/pdf"
            }
            rcDocumentLauncher.launch(intent)
        }
        
        binding.btnAddInsurance.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/pdf"
            }
            insuranceDocumentLauncher.launch(intent)
        }
    }

    private fun setupSubmitButton() {
        binding.btnSubmit.setOnClickListener {
            if (validateForm()) {
                uploadFilesAndCreateSale()
            }
        }
    }

    private fun setupDeleteButton() {
        binding.btnDelete.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        // Show delete button only for pending or approved sales that haven't been paid
        if (editSale != null) {
            binding.btnDelete.visibility = when {
                editSale?.status == "pending" -> View.VISIBLE
                editSale?.status == "approved" && !editSale?.buyerPaid!! -> View.VISIBLE
                editSale?.status == "rejected" -> View.VISIBLE
                else -> View.GONE
            }
        }
    }

    private fun showDeleteConfirmationDialog() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Sale")
            .setMessage("Are you sure you want to delete this sale? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteSale()
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun deleteSale() {
        binding.progressBarSubmit.visibility = View.VISIBLE
        binding.btnSubmit.isEnabled = false
        binding.btnDelete.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                editSale?.let { sale ->
                    if (FirebaseUtils.deleteSale(sale.id)) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Sale deleted successfully", Toast.LENGTH_SHORT).show()
                            // Notify parent fragment that sale was deleted
                            parentFragmentManager.setFragmentResult("sale_updated", Bundle())
                            // Go back to previous screen
                            parentFragmentManager.popBackStack()
                        }
                    } else {
                        throw Exception("Failed to delete sale")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBarSubmit.visibility = View.GONE
                    binding.btnSubmit.isEnabled = true
                    binding.btnDelete.isEnabled = true
                    Toast.makeText(context, "Error deleting sale: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun fillForm(sale: Sale) {
        binding.etVehicleBrand.setText(sale.brand)
        binding.etVehicleModel.setText(sale.model)
        binding.etYear.setText(sale.year.toString())
        binding.etKilometers.setText(sale.kilometers.toString())
        binding.etBasePrice.setText(sale.price.toString())
        binding.etLocation.setText(sale.location)
        binding.etStartDate.setText(dateFormat.format(Date(sale.startDate.seconds * 1000)))
        binding.etEndDate.setText(dateFormat.format(Date(sale.endDate.seconds * 1000)))
        
        // Set spinner selection
        val fuelTypes = resources.getStringArray(R.array.fuel_types)
        val fuelTypePosition = fuelTypes.indexOf(sale.fuelType)
        if (fuelTypePosition != -1) {
            binding.spinnerFuelType.setSelection(fuelTypePosition)
        }

        // Load and display existing image
        if (sale.imageUrl.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val bitmap = FirebaseUtils.decodeBase64(sale.imageUrl)
                    withContext(Dispatchers.Main) {
                        bitmap?.let {
                            // Create ImageView for the existing image
                            val imageView = ImageView(context).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    resources.getDimensionPixelSize(R.dimen.image_preview_size),
                                    resources.getDimensionPixelSize(R.dimen.image_preview_size)
                                )
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                setPadding(8, 8, 8, 8)
                                setImageBitmap(it)
                            }
                            
                            // Clear existing views and add the image
                            binding.imageContainer.removeAllViews()
                            binding.imageContainer.addView(imageView)
                            binding.imageContainer.addView(binding.btnAddImage)
                            
                            // Store the existing image URL
                            selectedImageUri = null // Reset since we're using the existing image
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AddSaleFragment", "Error loading existing image", e)
                }
            }
        }

        // Set up document buttons to show existing documents
        if (sale.rcBookUrl.isNotEmpty()) {
            binding.btnAddRC.setText(R.string.rc_document_added)
            binding.btnAddRC.setIconResource(android.R.drawable.ic_menu_upload)
            rcBookUri = null // Reset since we're using the existing document
        }

        if (sale.insuranceUrl.isNotEmpty()) {
            binding.btnAddInsurance.setText(R.string.insurance_document_added)
            binding.btnAddInsurance.setIconResource(android.R.drawable.ic_menu_upload)
            insuranceUri = null // Reset since we're using the existing document
        }
    }

    private fun validateForm(): Boolean {
        var isValid = true
        if (binding.etVehicleBrand.text.isNullOrEmpty()) {
            binding.etVehicleBrand.error = getString(R.string.brand_required)
            isValid = false
        }
        if (binding.etVehicleModel.text.isNullOrEmpty()) {
            binding.etVehicleModel.error = getString(R.string.model_required)
            isValid = false
        }
        if (binding.etYear.text.isNullOrEmpty()) {
            binding.etYear.error = getString(R.string.year_required)
            isValid = false
        }
        if (binding.etKilometers.text.isNullOrEmpty()) {
            binding.etKilometers.error = getString(R.string.kilometers_required)
            isValid = false
        }
        if (binding.etBasePrice.text.isNullOrEmpty()) {
            binding.etBasePrice.error = getString(R.string.base_price_required)
            isValid = false
        }
        if (binding.etStartDate.text.isNullOrEmpty()) {
            binding.etStartDate.error = getString(R.string.start_date_required)
            isValid = false
        }
        if (binding.etEndDate.text.isNullOrEmpty()) {
            binding.etEndDate.error = getString(R.string.end_date_required)
            isValid = false
        }

        // Check for image - either new upload or existing image
        if (selectedImageUri == null && editSale?.imageUrl.isNullOrEmpty()) {
            Toast.makeText(context, getString(R.string.add_image_required), Toast.LENGTH_SHORT).show()
            isValid = false
        }

        // Check for RC document - either new upload or existing document
        if (rcBookUri == null && editSale?.rcBookUrl.isNullOrEmpty()) {
            Toast.makeText(context, getString(R.string.upload_rc_required), Toast.LENGTH_SHORT).show()
            isValid = false
        }

        // Check for Insurance document - either new upload or existing document
        if (insuranceUri == null && editSale?.insuranceUrl.isNullOrEmpty()) {
            Toast.makeText(context, getString(R.string.upload_insurance_required), Toast.LENGTH_SHORT).show()
            isValid = false
        }

        return isValid
    }

    private fun uploadFilesAndCreateSale() {
        // Show progress
        binding.progressBarSubmit.visibility = View.VISIBLE
        binding.btnSubmit.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                    ?: throw Exception("User not logged in")

                // Handle image upload - use existing image if no new one selected
                val imageUrl = if (selectedImageUri != null) {
                    val imageBytes = requireContext().contentResolver.openInputStream(selectedImageUri!!)?.use {
                        it.readBytes()
                    } ?: throw Exception("Failed to read image file")
                    FirebaseUtils.uploadImage(imageBytes)
                } else {
                    editSale?.imageUrl ?: throw Exception("Image not selected")
                }

                // Handle RC document upload - use existing document if no new one selected
                val rcUrl = if (rcBookUri != null) {
                    val rcBytes = requireContext().contentResolver.openInputStream(rcBookUri!!)?.use {
                        it.readBytes()
                    } ?: throw Exception("Failed to read RC document")
                    FirebaseUtils.uploadDocument(rcBytes, "rc")
                } else {
                    editSale?.rcBookUrl ?: throw Exception("RC document not selected")
                }

                // Handle Insurance document upload - use existing document if no new one selected
                val insuranceUrl = if (insuranceUri != null) {
                    val insuranceBytes = requireContext().contentResolver.openInputStream(insuranceUri!!)?.use {
                        it.readBytes()
                    } ?: throw Exception("Failed to read insurance document")
                    FirebaseUtils.uploadDocument(insuranceBytes, "insurance")
                } else {
                    editSale?.insuranceUrl ?: throw Exception("Insurance document not selected")
                }

                // Parse dates
                val startDate = dateFormat.parse(binding.etStartDate.text.toString())
                    ?: throw Exception("Invalid start date")
                val endDate = dateFormat.parse(binding.etEndDate.text.toString())
                    ?: throw Exception("Invalid end date")

                // Create or update sale object
                val sale = Sale(
                    id = editSale?.id ?: "", // Empty for new sales, existing ID for updates
                    sellerId = userId,
                    brand = binding.etVehicleBrand.text.toString(),
                    model = binding.etVehicleModel.text.toString(),
                    year = binding.etYear.text.toString().toInt(),
                    kilometers = binding.etKilometers.text.toString().toInt(),
                    fuelType = binding.spinnerFuelType.selectedItem.toString(),
                    price = binding.etBasePrice.text.toString().toDouble(),
                    location = binding.etLocation.text.toString(),
                    imageUrl = imageUrl,
                    rcBookUrl = rcUrl,
                    insuranceUrl = insuranceUrl,
                    startDate = Timestamp(startDate),
                    endDate = Timestamp(endDate),
                    status = if (editSale?.status == "rejected" || editSale?.status == "approved") "pending" else editSale?.status ?: "pending",
                    currentBid = editSale?.currentBid ?: 0.0,
                    currentBidder = editSale?.currentBidder ?: "",
                    rejectionComments = if (editSale?.status == "rejected") "" else editSale?.rejectionComments ?: "",
                    buyerPaid = false, // Reset buyerPaid when editing
                    // Preserve existing contact information if available
                    sellerName = editSale?.sellerName ?: "",
                    sellerPhone = editSale?.sellerPhone ?: "",
                    sellerEmail = editSale?.sellerEmail ?: "",
                    sellerAadhar = editSale?.sellerAadhar ?: "",
                    buyerName = editSale?.buyerName ?: "",
                    buyerPhone = editSale?.buyerPhone ?: "",
                    buyerEmail = editSale?.buyerEmail ?: "",
                    buyerAadhar = editSale?.buyerAadhar ?: ""
                )

                // Save to Firestore
                if (editSale != null) {
                    if (!FirebaseUtils.updateSale(sale)) {
                        throw Exception("Failed to update sale")
                    }
                } else {
                    val saleId = FirebaseUtils.createSale(sale)
                    if (saleId.isEmpty()) {
                        throw Exception("Failed to create sale")
                    }
                }

                withContext(Dispatchers.Main) {
                    binding.progressBarSubmit.visibility = View.GONE
                    Toast.makeText(
                        context,
                        if (editSale != null) "Sale updated successfully" else "Sale created successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Notify parent fragment that sale was updated
                    parentFragmentManager.setFragmentResult("sale_updated", Bundle())
                    
                    // Go back to previous screen
                    parentFragmentManager.popBackStack()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBarSubmit.visibility = View.GONE
                    binding.btnSubmit.isEnabled = true
                    Toast.makeText(
                        context,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Utility functions for file handling
    private fun isPdfFile(uri: Uri): Boolean {
        val contentResolver = requireContext().contentResolver
        val mimeType = contentResolver.getType(uri)
        return mimeType == "application/pdf"
    }
    
    private fun checkFileSize(uri: Uri, isImage: Boolean): Boolean {
        val contentResolver = requireContext().contentResolver
        val cursor = contentResolver.query(uri, null, null, null, null)
        val sizeIndex = cursor?.getColumnIndex(MediaStore.MediaColumns.SIZE)
        cursor?.moveToFirst()
        
        val size = if (sizeIndex != null && sizeIndex >= 0) {
            cursor.getLong(sizeIndex)
        } else {
            try {
                contentResolver.openInputStream(uri)?.use { 
                    it.available().toLong()
                } ?: 0L
            } catch (e: Exception) {
                0L
            }
        }
        
        cursor?.close()
        
        return if (isImage) {
            // 5MB limit for images
            size <= 5 * 1024 * 1024
        } else {
            // 200KB limit for documents
            size <= 200 * 1024
        }
    }
    
    private fun displaySelectedImage(uri: Uri) {
        // Clear the existing image container first
        binding.imageContainer.removeAllViews()
        
        // Create a new ImageView
        val imageView = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.image_preview_size),
                resources.getDimensionPixelSize(R.dimen.image_preview_size)
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setPadding(8, 8, 8, 8)
            setImageURI(uri)
        }
        
        // Add the ImageView and the add button
        binding.imageContainer.addView(imageView)
        binding.imageContainer.addView(binding.btnAddImage)
    }

    private fun disableAllInputs() {
        // Disable all EditTexts
        binding.etVehicleBrand.isEnabled = false
        binding.etVehicleModel.isEnabled = false
        binding.etYear.isEnabled = false
        binding.etKilometers.isEnabled = false
        binding.etBasePrice.isEnabled = false
        binding.etLocation.isEnabled = false
        binding.etStartDate.isEnabled = false
        binding.etEndDate.isEnabled = false
        
        // Disable spinner
        binding.spinnerFuelType.isEnabled = false
        
        // Hide upload buttons
        binding.btnAddImage.visibility = View.GONE
        binding.btnAddRC.visibility = View.GONE
        binding.btnAddInsurance.visibility = View.GONE
        
        // Hide submit button
        binding.btnSubmit.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 