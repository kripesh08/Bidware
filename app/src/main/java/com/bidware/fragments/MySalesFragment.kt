package com.bidware.fragments

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bidware.MainActivity
import com.bidware.R
import com.bidware.adapters.SaleAdapter
import com.bidware.databinding.FragmentContainerMySalesBinding
import com.bidware.models.Sale
import com.bidware.utils.FirebaseUtils
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import android.util.Log
import android.os.Handler
import android.os.Looper
import com.bidware.ProfileActivity
import com.bidware.utils.RazorpayPaymentService
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class MySalesFragment : Fragment() {
    private var _binding: FragmentContainerMySalesBinding? = null
    private val binding get() = _binding!!
    
    private val tag = "MySalesFragment"
    
    // Member variables for periodic check
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var periodicCheckRunnable: Runnable

    // We don't need to inflate these separately as they're included in the container
    private val salesBinding get() = binding.salesListContainer
    private val addSaleBinding get() = binding.addSaleContainer

    private lateinit var saleAdapter: SaleAdapter
    private val allSales = mutableListOf<Sale>()
    private var currentFilter = "pending"
    private var currentSale: Sale? = null
    private var isEditMode = false
    
    // Date format for display
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    companion object {
        fun newAddSaleInstance(): MySalesFragment {
            val fragment = MySalesFragment()
            fragment.arguments = Bundle().apply {
                putBoolean("isAddMode", true)
            }
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isEditMode = arguments?.getBoolean("isAddMode", false) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContainerMySalesBinding.inflate(inflater, container, false)

        if (isEditMode) {
            showAddSaleForm()
        } else {
            showSalesList()
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!isEditMode) {
            val btnMenu = salesBinding.btnMenu
            setupMenu(btnMenu)
            setupRecyclerView()
            setupTabLayout()
            setupAddButton()
            loadSales()
            startPeriodicStatusCheck()
        }
        // Listen for result from AddSaleFragment
        parentFragmentManager.setFragmentResultListener("sale_updated", this) { _, _ ->
            loadSales()
        }
        Log.d(tag, "setupRecyclerView: Adapter created by Fragment instance: ${this.hashCode()}")
    }

    private fun setupMenu(btnMenu: ImageButton) {
        btnMenu.setOnClickListener {
            // Show menu options
            val popupMenu = android.widget.PopupMenu(requireContext(), btnMenu)
            popupMenu.menuInflater.inflate(R.menu.main_menu, popupMenu.menu)
            
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_profile -> {
                        val intent = Intent(requireContext(), ProfileActivity::class.java)
                        startActivity(intent)
                        true
                    }
                    R.id.action_logout -> {
                        // Handle logout action
                        (activity as MainActivity).signOut()
                        true
                    }
                    else -> false
                }
            }
            
            popupMenu.show()
        }
    }

    private fun setupRecyclerView() {
        saleAdapter = SaleAdapter(
            onSaleClick = { sale ->
                when {
                    sale.status == "rejected" -> {
                        // For rejected sales, show rejection dialog with edit option
                        showRejectionDialog(sale)
                    }
                    currentFilter == "pending" -> {
                        // For pending sales, open AddSaleFragment for editing
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, AddSaleFragment.newInstance(sale))
                            .addToBackStack(null)
                            .commit()
                    }
                    currentFilter == "payment" -> {
                        // For approved sales, show payment dialog
                        currentSale = sale
                        showPaymentDialog(sale)
                    }
                    else -> {
                        // For other statuses, show sale details
                        showSaleDetails(sale)
                    }
                }
            },
            onEditClick = { sale ->
                // Open AddSaleFragment for editing
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, AddSaleFragment.newInstance(sale))
                    .addToBackStack(null)
                    .commit()
            }
        )
        
        salesBinding.recyclerView.apply {
            adapter = saleAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun setupTabLayout() {
        // Set initial tab selection based on currentFilter
        val initialTabPosition = when (currentFilter) {
            "pending" -> 0
            "payment" -> 1
            "in_auction" -> 2
            "completed" -> 3
            else -> 0
        }
        salesBinding.tabLayout.getTabAt(initialTabPosition)?.select()

        salesBinding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> filterSales("pending")
                    1 -> filterSales("payment")
                    2 -> filterSales("in_auction")
                    3 -> filterSales("completed")
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupAddButton() {
        salesBinding.fabAddSale.setOnClickListener {
            // Open AddSaleFragment for new sale
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, AddSaleFragment.newInstance(null))
                .addToBackStack(null)
                .commit()
        }
    }

    private fun loadSales() {
        salesBinding.progressBar.visibility = View.VISIBLE
        Log.d(tag, "Loading sales for user: ${FirebaseAuth.getInstance().currentUser?.uid}")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                val sales = FirebaseUtils.getSalesBySellerId(userId)
                Log.d(tag, "Fetched ${sales.size} sales from Firestore")
                
                withContext(Dispatchers.Main) {
                    // Check if binding is still valid before accessing UI
                    if (_binding == null) return@withContext 
                    allSales.clear()
                    allSales.addAll(sales)
                    Log.d(tag, "Added ${allSales.size} sales to allSales list")
                    filterSales(currentFilter)
                    salesBinding.progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e(tag, "Error loading sales", e)
                withContext(Dispatchers.Main) {
                    // Check if binding is still valid before accessing UI
                    if (_binding == null) return@withContext
                    salesBinding.progressBar.visibility = View.GONE
                    Toast.makeText(context, "Error loading sales: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun filterSales(status: String) {
        currentFilter = status
        val filteredList = when (status) {
            "pending" -> allSales.filter { it.status == "pending" }
            "payment" -> allSales.filter { it.status == "approved" }
            "in_auction" -> allSales.filter { it.status == "in_auction" || it.status == "waiting_for_start" }
            "completed" -> allSales.filter { it.status == "completed" || it.status == "rejected" }
            else -> allSales
        }
        saleAdapter.submitList(filteredList)
    }

    private fun checkAndUpdateSaleStatuses() {
        val currentTime = System.currentTimeMillis()
        Log.d(tag, "Checking sale statuses at time: $currentTime")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                val sales = FirebaseUtils.getSalesBySellerId(userId)
                
                for (sale in sales) {
                    // Convert timestamps to milliseconds for comparison
                    val startTimeMs = sale.startDate.seconds * 1000
                    val endTimeMs = sale.endDate.seconds * 1000
                    
                    Log.d(tag, "Sale ${sale.id}: status=${sale.status}, startTime=$startTimeMs, current=$currentTime")
                    
                    when (sale.status) {
                        "waiting_for_start" -> {
                            if (currentTime >= startTimeMs) {
                                Log.d(tag, "Updating sale ${sale.id} from waiting_for_start to in_auction")
                                // Update to in_auction if start time has been reached
                                FirebaseUtils.updateSale(sale.copy(status = "in_auction"))
                            }
                        }
                        "in_auction" -> {
                            if (currentTime >= endTimeMs) {
                                Log.d(tag, "Updating sale ${sale.id} from in_auction to completed")
                                // Update to completed if end time has been reached
                                FirebaseUtils.updateSale(sale.copy(status = "completed"))
                            }
                        }
                        "approved" -> {
                            if (currentTime >= startTimeMs) {
                                Log.d(tag, "Updating sale ${sale.id} from approved to rejected (payment not made)")
                                // Update to rejected if payment wasn't made in time
                                FirebaseUtils.updateSale(
                                    sale.copy(
                                        status = "rejected",
                                        rejectionComments = "Sale was automatically rejected due to non-payment before auction start time."
                                    )
                                )
                            }
                        }
                    }
                }
                
                // Refresh the sales list only if view is still available
                withContext(Dispatchers.Main) {
                    // Check if binding is still valid before calling loadSales
                    if (_binding == null) return@withContext
                    loadSales()
                }
            } catch (e: Exception) {
                Log.e(tag, "Error checking sale statuses", e)
                e.printStackTrace()
            }
        }
    }

    private fun startPeriodicStatusCheck() {
        // Check status every minute
        // Use the member handler and define the runnable
        periodicCheckRunnable = object : Runnable {
            override fun run() {
                // Also check binding here before running the check
                if (_binding != null) { 
                    checkAndUpdateSaleStatuses()
                }
                // Reschedule only if view might still be around
                handler.postDelayed(this, 60000) // 60 seconds 
            }
        }
        handler.post(periodicCheckRunnable)
    }

    private fun showPaymentDialog(sale: Sale) {
        val serviceFee = calculateServiceFee()
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("Payment Required")
            .setMessage(
                "Your sale has been approved!\n\n" +
                "Please make a payment of " +
                String.format(Locale.getDefault(), "₹%.2f", serviceFee) +
                " to proceed with the auction."
            )
            .setPositiveButton("Make Payment") { _, _ ->
                initiatePayment(sale, serviceFee)
            }
            .setNeutralButton("Edit Sale") { _, _ ->
                // Open AddSaleFragment for editing
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, AddSaleFragment.newInstance(sale))
                    .addToBackStack(null)
                    .commit()
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
    }

    private fun calculateServiceFee(): Double {
        // Fixed service fee of ₹700
        return 700.0
    }

    private fun initiatePayment(sale: Sale, amount: Double) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                
                // Get user profile data from Firestore directly
                val userDocRef = FirebaseFirestore.getInstance().collection("users").document(userId)
                val userProfile = userDocRef.get().await().data
                
                if (userProfile == null) {
                    withContext(Dispatchers.Main) {
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
                        
                        // Note: We do NOT update payment status here
                        // It will be handled by PaymentResultListener in MainActivity
                        
                        // Store payment ID for reference
                        FirebaseUtils.updatePaymentRazorpayId(payment.id, razorpayOrderId)
                        
                        // Don't show success toast here - wait for callback
                    } catch (e: Exception) {
                        FirebaseUtils.updatePaymentStatus(
                            payment.id,
                            "failed",
                            errorMessage = e.message ?: "Payment initialization failed"
                        )
                        Toast.makeText(context, "Payment initialization failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error initiating payment: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showSaleDetails(sale: Sale) {
        // Navigate to the SaleDetailsFragment instead of showing a dialog
        val saleDetailsFragment = SaleDetailsFragment.newInstance(sale)
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, saleDetailsFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun showSalesList() {
        try {
            binding.salesListContainer.root.visibility = View.VISIBLE
            binding.addSaleContainer.root.visibility = View.GONE
        } catch (e: Exception) {
            // Log error but don't crash
            e.printStackTrace()
        }
    }

    private fun showAddSaleForm() {
        try {
            binding.salesListContainer.root.visibility = View.GONE
            binding.addSaleContainer.root.visibility = View.VISIBLE
        } catch (e: Exception) {
            // Log error but don't crash
            e.printStackTrace()
        }
    }

    private fun showRejectionDialog(sale: Sale) {
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("Sale Rejected")
            .setMessage(
                "Your sale was rejected.\n\n" +
                "Reason: ${sale.rejectionComments}\n\n" +
                "Would you like to edit and resubmit this sale?"
            )
            .setPositiveButton("Edit Sale") { _, _ ->
                // Open AddSaleFragment with the rejected sale for editing
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, AddSaleFragment.newInstance(sale))
                    .addToBackStack(null)
                    .commit()
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Stop the periodic check when the view is destroyed
        handler.removeCallbacks(periodicCheckRunnable)
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        if (!isEditMode) {
            loadSales() // Reload sales when returning to this fragment
            // Ensure correct tab is selected
            val tabPosition = when (currentFilter) {
                "pending" -> 0
                "payment" -> 1
                "in_auction" -> 2
                "completed" -> 3
                else -> 0
            }
            salesBinding.tabLayout.getTabAt(tabPosition)?.select()
        }
    }

    // Add these methods to handle payment callbacks from MainActivity
    fun handlePaymentSuccess(razorpayPaymentId: String?) {
        Log.d(tag, "Payment success callback received: $razorpayPaymentId")
        // Get currently processing sale and payment
        val sale = currentSale ?: return
        
        // Update sale status and reload
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Update sale status to waiting_for_start
                FirebaseUtils.updateSale(sale.copy(status = "waiting_for_start"))
                
                // Update payment status in Firestore
                val payments = FirebaseUtils.getPaymentsBySaleId(sale.id)
                val latestPayment = payments.maxByOrNull { it.createdAt }
                
                latestPayment?.let {
                    FirebaseUtils.updatePaymentStatus(
                        paymentId = it.id,
                        status = "completed",
                        razorpayOrderId = razorpayPaymentId ?: ""
                    )
                }
                
                // Reload sales on main thread
                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        isEditMode = false
                        showSalesList()
                        loadSales()
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error updating sale/payment after successful payment", e)
            }
        }
    }
    
    fun handlePaymentFailure(errorDescription: String?) {
        Log.d(tag, "Payment failure callback received: $errorDescription")
        // Get currently processing sale and payment
        val sale = currentSale ?: return
        
        // Save original error for debugging
        val originalError = errorDescription ?: "Unknown payment error"
        
        // Update payment status only
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Find latest payment for this sale
                val payments = FirebaseUtils.getPaymentsBySaleId(sale.id)
                val latestPayment = payments.maxByOrNull { it.createdAt }
                
                latestPayment?.let {
                    FirebaseUtils.updatePaymentStatus(
                        paymentId = it.id,
                        status = "failed",
                        errorMessage = originalError
                    )
                }
                
                // Offer to retry on main thread
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    
                    // If in edit mode, remain there. Otherwise reload sales.
                    if (!isEditMode) {
                        loadSales()
                    }
                    
                    // Offer to retry the payment
                    android.app.AlertDialog.Builder(requireContext())
                        .setTitle("Payment Failed")
                        .setMessage("Would you like to try making the payment again?")
                        .setPositiveButton("Try Again") { _, _ ->
                            showPaymentDialog(sale)
                        }
                        .setNegativeButton("Not Now", null)
                        .show()
                }
            } catch (e: Exception) {
                Log.e(tag, "Error updating payment after failed payment", e)
            }
        }
    }
} 