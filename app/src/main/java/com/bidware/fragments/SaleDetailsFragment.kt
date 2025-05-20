package com.bidware.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bidware.databinding.FragmentSaleDetailsBinding
import com.bidware.models.Sale
import com.bidware.utils.FirebaseUtils
import com.bidware.utils.RazorpayPaymentService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SaleDetailsFragment : Fragment() {
    private var _binding: FragmentSaleDetailsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var sale: Sale
    private var isCurrentUserSeller = false
    private var isCurrentUserBuyer = false
    private var hasCurrentUserPaid = false
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val TAG = "SaleDetailsFragment"
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    companion object {
        private const val ARG_SALE = "sale"

        fun newInstance(sale: Sale): SaleDetailsFragment {
            val fragment = SaleDetailsFragment()
            val args = Bundle()
            args.putParcelable(ARG_SALE, sale)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            sale = it.getParcelable(ARG_SALE)!!
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSaleDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        isCurrentUserSeller = currentUserId == sale.sellerId
        isCurrentUserBuyer = currentUserId == sale.currentBidder
        hasCurrentUserPaid = sale.buyerPaid
        
        setupToolbar()
        displaySaleDetails()
    }

    private fun setupToolbar() {
        binding.toolbarSaleDetails.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    private fun displaySaleDetails() {
        // Log current state for debugging
        Log.d(TAG, "Sale state: id=${sale.id}, buyerPaid=${sale.buyerPaid}, " +
                "isCurrentUserBuyer=$isCurrentUserBuyer, isCurrentUserSeller=$isCurrentUserSeller, " +
                "status=${sale.status}, currentBidder=${sale.currentBidder}")
        Log.d(TAG, "Contact info: sellerName=${sale.sellerName}, buyerName=${sale.buyerName}, " +
                "sellerPhone=${sale.sellerPhone}, buyerPhone=${sale.buyerPhone}")
        
        // Load vehicle image
        if (sale.imageUrl.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                val bitmap = FirebaseUtils.decodeBase64(sale.imageUrl)
                withContext(Dispatchers.Main) {
                    bitmap?.let {
                        binding.ivVehicle.setImageBitmap(it)
                    }
                }
            }
        }

        // Display vehicle details
        binding.tvTitle.text = "${sale.brand} ${sale.model}"
        binding.tvPrice.text = sale.displayPrice
        binding.tvYear.text = "Year: ${sale.year}"
        binding.tvFuelType.text = "Fuel: ${sale.fuelType}"
        binding.tvKilometers.text = "Kilometers: ${sale.kilometers}"
        binding.tvLocation.text = "Location: ${sale.location}"
        
        // Display auction details
        val startDate = Date(sale.startDate.seconds * 1000)
        val endDate = Date(sale.endDate.seconds * 1000)
        binding.tvAuctionDates.text = "Auction Period: ${dateFormat.format(startDate)} - ${dateFormat.format(endDate)}"
        binding.tvStatus.text = "Status: ${sale.formattedStatus}"
        
        // Display current bid if available
        if (sale.currentBid > 0) {
            binding.tvCurrentBid.visibility = View.VISIBLE
            binding.tvCurrentBid.text = "Current Bid: ${currencyFormat.format(sale.currentBid)}"
        } else {
            binding.tvCurrentBid.visibility = View.GONE
        }
        
        // Handle contact info and document visibility
        setupContactInfoSection()
        setupDocumentSection()
    }
    
    private fun setupContactInfoSection() {
        Log.d(TAG, "setupContactInfoSection: isCurrentUserSeller=$isCurrentUserSeller, isCurrentUserBuyer=$isCurrentUserBuyer")
        Log.d(TAG, "setupContactInfoSection: sale.status=${sale.status}, sale.buyerPaid=${sale.buyerPaid}")
        Log.d(TAG, "setupContactInfoSection: sellerName=${sale.sellerName}, buyerName=${sale.buyerName}")
        
        // Contact card visibility logic
        binding.contactCard.visibility = when {
            // Seller always sees contact card if auction is completed and has a winner and buyer has paid
            isCurrentUserSeller && sale.status == "completed" && sale.buyerPaid -> {
                Log.d(TAG, "Contact card visible for seller")
                View.VISIBLE
            }
            
            // Buyer sees contact card only if they've paid service fee
            isCurrentUserBuyer && sale.buyerPaid -> {
                Log.d(TAG, "Contact card visible for buyer")
                View.VISIBLE
            }
            
            // Otherwise hide contact card
            else -> {
                Log.d(TAG, "Contact card hidden: conditions not met")
                View.GONE
            }
        }
        
        // Buyer payment required card
        binding.paymentRequiredCard.visibility = if (isCurrentUserBuyer && !sale.buyerPaid) {
            View.VISIBLE
        } else {
            View.GONE
        }
        
        // Set up payment button
        binding.btnMakePayment.setOnClickListener {
            initiatePayment()
        }
        
        // Populate contact info if visible
        if (binding.contactCard.visibility == View.VISIBLE) {
            if (isCurrentUserSeller) {
                // Seller sees buyer's info
                binding.tvContactTitle.text = "Buyer Contact Information"
                
                // Use saved information in sale object if available
                if (sale.buyerName.isNotEmpty()) {
                    Log.d(TAG, "Displaying saved buyer info to seller")
                    binding.tvContactName.text = sale.buyerName
                    binding.tvContactPhone.text = "Phone: ${sale.buyerPhone}"
                    binding.tvContactEmail.text = "Email: ${sale.buyerEmail}"
                    
                    // Show Aadhar if available
                    if (sale.buyerAadhar.isNotEmpty()) {
                        binding.tvContactAadhar.text = "Aadhar: ${sale.buyerAadhar}"
                        binding.tvContactAadhar.visibility = View.VISIBLE
                    } else {
                        binding.tvContactAadhar.visibility = View.GONE
                    }
                } else {
                    // Fall back to loading from Firestore
                    Log.d(TAG, "Loading buyer info from Firestore")
                    binding.tvContactName.text = "Loading..."
                    loadUserName(sale.currentBidder)
                }
            } else {
                // Buyer sees seller's info
                binding.tvContactTitle.text = "Seller Contact Information"
                
                // Use saved information in sale object if available
                if (sale.sellerName.isNotEmpty()) {
                    Log.d(TAG, "Displaying saved seller info to buyer")
                    binding.tvContactName.text = sale.sellerName
                    binding.tvContactPhone.text = "Phone: ${sale.sellerPhone}"
                    binding.tvContactEmail.text = "Email: ${sale.sellerEmail}"
                    
                    // Show Aadhar if available
                    if (sale.sellerAadhar.isNotEmpty()) {
                        binding.tvContactAadhar.text = "Aadhar: ${sale.sellerAadhar}"
                        binding.tvContactAadhar.visibility = View.VISIBLE
                    } else {
                        binding.tvContactAadhar.visibility = View.GONE
                    }
                } else {
                    // Fall back to loading from Firestore
                    Log.d(TAG, "Loading seller info from Firestore")
                    binding.tvContactName.text = "Loading..."
                    loadUserName(sale.sellerId)
                }
            }
        }
    }
    
    private fun loadUserName(userId: String) {
        Log.d(TAG, "Loading user details for userId: $userId, isCurrentUserSeller: $isCurrentUserSeller")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userDoc = FirebaseFirestore.getInstance().collection("users").document(userId).get().await()
                val userData = userDoc?.data
                
                if (userData != null) {
                    val fullName = userData["fullName"] as? String ?: "Unknown"
                    val mobile = userData["mobile"] as? String ?: ""
                    val email = userData["email"] as? String ?: ""
                    val aadhar = userData["aadhar"] as? String ?: ""
                    
                    Log.d(TAG, "User data loaded: name=$fullName, mobile=$mobile")
                    
                    withContext(Dispatchers.Main) {
                        binding.tvContactName.text = fullName
                        binding.tvContactPhone.text = "Phone: $mobile"
                        binding.tvContactEmail.text = "Email: $email"
                        
                        if (aadhar.isNotEmpty()) {
                            binding.tvContactAadhar.text = "Aadhar: $aadhar"
                            binding.tvContactAadhar.visibility = View.VISIBLE
                        } else {
                            binding.tvContactAadhar.visibility = View.GONE
                        }
                        
                        // Also update the Sale object with this information for future reference
                        if (isCurrentUserSeller && userId == sale.currentBidder) {
                            Log.d(TAG, "Updating sale with buyer information")
                            // We're a seller looking at buyer info, update the sale
                            val updatedSale = sale.copy(
                                buyerName = fullName,
                                buyerPhone = mobile,
                                buyerEmail = email,
                                buyerAadhar = aadhar
                            )
                            CoroutineScope(Dispatchers.IO).launch {
                                FirebaseUtils.updateSale(updatedSale)
                                sale = updatedSale // Update local reference
                            }
                        } else if (isCurrentUserBuyer && userId == sale.sellerId) {
                            Log.d(TAG, "Updating sale with seller information")
                            // We're a buyer looking at seller info, update the sale
                            val updatedSale = sale.copy(
                                sellerName = fullName,
                                sellerPhone = mobile,
                                sellerEmail = email,
                                sellerAadhar = aadhar
                            )
                            CoroutineScope(Dispatchers.IO).launch {
                                FirebaseUtils.updateSale(updatedSale)
                                sale = updatedSale // Update local reference
                            }
                        } else {
                            // No update needed for other cases
                            Log.d(TAG, "No sale update needed for this user")
                        }
                    }
                } else {
                    Log.e(TAG, "User data is null for userId: $userId")
                    withContext(Dispatchers.Main) {
                        binding.tvContactName.text = "Unknown User"
                        binding.tvContactPhone.text = "Phone: Not available"
                        binding.tvContactEmail.text = "Email: Not available"
                        binding.tvContactAadhar.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading user details", e)
                withContext(Dispatchers.Main) {
                    binding.tvContactName.text = "Error loading user"
                    binding.tvContactPhone.text = "Phone: Not available"
                    binding.tvContactEmail.text = "Email: Not available"
                    binding.tvContactAadhar.visibility = View.GONE
                }
            }
        }
    }
    
    private fun setupDocumentSection() {
        val shouldShowDocs = shouldShowDocuments()
        
        // Main document section title visibility
        binding.tvDocumentsTitle.visibility = if (shouldShowDocs || isCurrentUserBuyer) {
            View.VISIBLE
        } else {
            View.GONE
        }
        
        // Document cards visibility
        binding.cardRcBook.visibility = if (shouldShowDocs) {
            View.VISIBLE
        } else {
            View.GONE
        }
        
        binding.cardInsurance.visibility = if (shouldShowDocs) {
            View.VISIBLE
        } else {
            View.GONE
        }
        
        // Payment required message for buyer
        binding.tvDocumentsPaymentRequired.visibility = if (isCurrentUserBuyer && !sale.buyerPaid) {
            View.VISIBLE
        } else {
            View.GONE
        }
        
        // Set document click listeners if visible
        if (shouldShowDocs) {
            setupDocumentPreview()
        }
    }
    
    private fun shouldShowDocuments(): Boolean {
        // Only show documents to:
        // 1. The seller of the vehicle
        // 2. The winning bidder (highest bidder) who has paid the service fee AND the sale is completed
        return isCurrentUserSeller || (isCurrentUserBuyer && sale.buyerPaid && sale.status == "completed")
    }
    
    private fun setupDocumentPreview() {
        // RC Book preview
        binding.btnViewRc.setOnClickListener {
            if (sale.rcBookUrl.isNotEmpty()) {
                showDocumentPreview("RC Book", sale.rcBookUrl)
            } else {
                Toast.makeText(context, "RC Book not available", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Insurance preview
        binding.btnViewInsurance.setOnClickListener {
            if (sale.insuranceUrl.isNotEmpty()) {
                showDocumentPreview("Insurance", sale.insuranceUrl)
            } else {
                Toast.makeText(context, "Insurance document not available", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun initiatePayment() {
        val serviceFee = calculateBuyerServiceFee(sale.currentBid)
        
        // Create payment dialog
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Buyer Service Fee")
            .setMessage(
                "To view seller details and documents, a service fee of ${currencyFormat.format(serviceFee)} is required.\n\n" +
                "After payment, you will have access to:\n" +
                "• Seller contact information\n" +
                "• Vehicle documents\n"
            )
            .setPositiveButton("Make Payment") { _, _ ->
                processPayment(serviceFee)
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
    }
    
    private fun calculateBuyerServiceFee(finalBid: Double): Double {
        // Calculate service fee percentage of the final bid amount or minimum ₹100
        val serviceFeePercentage = 0.02 // 2% - Change this value to modify the buyer service fee
        val fee = finalBid * serviceFeePercentage
        return if (fee < 100) 100.0 else fee
    }
    
    private fun processPayment(amount: Double) {
        binding.progressBar.visibility = View.VISIBLE
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                
                // Get user profile data from Firestore
                val userDocRef = FirebaseFirestore.getInstance().collection("users").document(userId)
                val userProfile = userDocRef.get().await().data
                
                if (userProfile == null) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(context, "User profile not found", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                // Create payment record
                val payment = FirebaseUtils.createPayment(sale.id, userId, amount)
                
                // Start Razorpay payment
                withContext(Dispatchers.Main) {
                    val razorpayService = RazorpayPaymentService(requireContext())
                    try {
                        val razorpayOrderId = razorpayService.startPayment(
                            activity = requireActivity(),
                            amount = amount,
                            saleId = sale.id,
                            userName = userProfile["fullName"] as? String ?: "",
                            userEmail = userProfile["email"] as? String ?: "",
                            userPhone = userProfile["mobile"] as? String ?: ""
                        )
                        
                        // Store payment ID for reference
                        FirebaseUtils.updatePaymentRazorpayId(payment.id, razorpayOrderId)
                        
                        // Don't show success toast here - wait for callback in MainActivity
                        binding.progressBar.visibility = View.GONE
                    } catch (e: Exception) {
                        FirebaseUtils.updatePaymentStatus(
                            payment.id,
                            "failed",
                            errorMessage = e.message ?: "Payment initialization failed"
                        )
                        Toast.makeText(context, "Payment initialization failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        binding.progressBar.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, "Error initiating payment: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    fun handlePaymentSuccess(paymentId: String?) {
        Log.d(TAG, "Payment success with ID: $paymentId")
        binding.progressBar.visibility = View.VISIBLE
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Update the sale with buyerPaid = true
                val updatedSale = sale.copy(buyerPaid = true)
                
                // Get buyer contact information for the seller to see
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                val userDocRef = FirebaseFirestore.getInstance().collection("users").document(userId)
                val userProfile = userDocRef.get().await().data
                
                Log.d(TAG, "Found buyer profile: ${userProfile != null}")
                
                if (userProfile != null) {
                    // Get complete buyer information
                    val buyerPhone = userProfile["mobile"] as? String ?: ""
                    val buyerEmail = userProfile["email"] as? String ?: ""
                    val buyerName = userProfile["fullName"] as? String ?: ""
                    val buyerAadhar = userProfile["aadhar"] as? String ?: ""
                    
                    Log.d(TAG, "Buyer details: name=$buyerName, phone=$buyerPhone, email=$buyerEmail")
                    
                    // Update the sale with buyer information
                    val finalSale = updatedSale.copy(
                        buyerPhone = buyerPhone,
                        buyerEmail = buyerEmail,
                        buyerName = buyerName,
                        buyerAadhar = buyerAadhar
                    )
                    
                    // Also get seller details for the buyer
                    val sellerDocRef = FirebaseFirestore.getInstance().collection("users").document(sale.sellerId)
                    val sellerProfile = sellerDocRef.get().await().data
                    
                    Log.d(TAG, "Found seller profile: ${sellerProfile != null}")
                    
                    if (sellerProfile != null) {
                        // Get complete seller information
                        val sellerPhone = sellerProfile["mobile"] as? String ?: ""
                        val sellerEmail = sellerProfile["email"] as? String ?: ""
                        val sellerName = sellerProfile["fullName"] as? String ?: ""
                        val sellerAadhar = sellerProfile["aadhar"] as? String ?: ""
                        
                        Log.d(TAG, "Seller details: name=$sellerName, phone=$sellerPhone, email=$sellerEmail")
                        
                        // Update the sale with seller information
                        val completeSale = finalSale.copy(
                            sellerPhone = sellerPhone,
                            sellerEmail = sellerEmail,
                            sellerName = sellerName,
                            sellerAadhar = sellerAadhar
                        )
                        
                        val updateResult = FirebaseUtils.updateSale(completeSale)
                        Log.d(TAG, "Sale update result with seller+buyer details: $updateResult")
                    } else {
                        // If we couldn't get seller details, at least update buyer details
                        val updateResult = FirebaseUtils.updateSale(finalSale)
                        Log.d(TAG, "Sale update result with buyer details only: $updateResult")
                    }
                } else {
                    // At minimum, mark as paid
                    val updateResult = FirebaseUtils.updateSale(updatedSale)
                    Log.d(TAG, "Sale update result with buyerPaid only: $updateResult")
                }
                
                // Refresh the sale data
                val refreshedSale = FirebaseUtils.getSaleById(sale.id)
                Log.d(TAG, "Refreshed sale: ${refreshedSale != null}, buyerPaid=${refreshedSale?.buyerPaid}")
                
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, "Payment successful!", Toast.LENGTH_SHORT).show()
                    
                    // Update the local sale object and refresh UI
                    if (refreshedSale != null) {
                        sale = refreshedSale
                        
                        // Reset state variables to make sure UI is properly updated
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                        isCurrentUserSeller = currentUserId == sale.sellerId
                        isCurrentUserBuyer = currentUserId == sale.currentBidder
                        hasCurrentUserPaid = sale.buyerPaid
                        
                        // Force refresh UI
                        displaySaleDetails()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating sale after payment", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, "Error updating sale: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    fun handlePaymentFailure(errorDescription: String?) {
        Log.e(TAG, "Payment failed: $errorDescription")
        Toast.makeText(context, "Payment failed. Please try again.", Toast.LENGTH_SHORT).show()
    }
    
    private fun showDocumentPreview(documentTitle: String, documentBase64: String) {
        // Create a dialog to show document preview
        val dialog = DocumentPreviewDialogFragment.newInstance(documentTitle, documentBase64)
        dialog.show(parentFragmentManager, "DocumentPreview")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 