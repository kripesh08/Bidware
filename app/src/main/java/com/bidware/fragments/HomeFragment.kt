package com.bidware.fragments

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bidware.MainActivity
import com.bidware.R
import com.bidware.adapters.SaleAdapter
import com.bidware.databinding.FragmentHomeBinding
import com.bidware.models.Sale
import com.bidware.utils.FirebaseUtils
import com.bidware.fragments.SaleDetailsFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale
import com.bidware.ProfileActivity

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var btnMenu: ImageButton
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private var allSales = mutableListOf<Sale>()
    private var filteredSales = mutableListOf<Sale>()
    private lateinit var saleAdapter: SaleAdapter
    private val TAG = "HomeFragment"
    private var currentUserId: String? = null
    private var salesListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = binding.salesRecyclerView
        searchEditText = binding.searchEditText
        progressBar = binding.progressBar
        btnMenu = binding.btnMenu
        swipeRefreshLayout = binding.swipeRefreshLayout

        currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        setupMenu()
        setupRecyclerView()
        setupSearch()
        setupSwipeRefresh()
        listenToActiveAuctions()
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            swipeRefreshLayout.isRefreshing = false
        }
        swipeRefreshLayout.setColorSchemeResources(
            R.color.colorPrimary,
            R.color.colorAccent
        )
    }

    override fun onResume() {
        super.onResume()
    }

    private fun setupMenu() {
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
                // Open the bidding dialog instead of sale details
                showSaleDetailsWithBidding(sale)
            }
        )
        
        recyclerView.apply {
            adapter = saleAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun setupSearch() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterSales(s.toString())
            }
        })
    }

    private fun listenToActiveAuctions() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        salesListener?.remove() // Remove any previous listener

        salesListener = FirebaseFirestore.getInstance()
            .collection("sales")
            .whereEqualTo("status", "in_auction")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    // Handle error
                    progressBar.visibility = View.GONE
                    swipeRefreshLayout.isRefreshing = false
                    Toast.makeText(context, "Error loading sales: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                val sales = snapshots?.documents?.mapNotNull { com.bidware.models.Sale.fromDocument(it) } ?: emptyList()
                // Filter out the current user's sales
                val filteredSales = sales.filter { it.sellerId != currentUserId && !it.isAuctionEnded }
                allSales.clear()
                allSales.addAll(filteredSales)
                // Always submit a new list reference to the adapter
                saleAdapter.submitList(ArrayList(allSales))
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
            }
    }

    private fun showSaleDetailsWithBidding(sale: Sale) {
        // Always fetch the latest sale data before showing the dialog
        binding.progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val latestSale = com.bidware.utils.FirebaseUtils.getSaleById(sale.id) ?: sale
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    showBidDialogWithLatestSale(latestSale)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, "Error loading sale: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Extract the dialog logic to a new function
    private fun showBidDialogWithLatestSale(sale: Sale) {
        if (sale.isAuctionEnded) {
            Toast.makeText(context, "This auction has ended.", Toast.LENGTH_SHORT).show()
            val saleDetailsFragment = SaleDetailsFragment.newInstance(sale)
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, saleDetailsFragment)
                .addToBackStack(null)
                .commit()
            return
        }
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val inflater = LayoutInflater.from(requireContext())
        val bidView = inflater.inflate(com.bidware.R.layout.dialog_place_bid, null)
        val etBidAmount = bidView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.bidware.R.id.etBidAmount)
        val tvCurrentBid = bidView.findViewById<android.widget.TextView>(com.bidware.R.id.tvCurrentBid)
        val tvBasePrice = bidView.findViewById<android.widget.TextView>(com.bidware.R.id.tvBasePrice)
        val tvMessage = bidView.findViewById<android.widget.TextView>(com.bidware.R.id.tvMessage)
        val isCurrentUserHighestBidder = sale.currentBidder == currentUserId && sale.currentBid > 0
        val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        val minBidAmount = if (sale.currentBid > 0) sale.currentBid + 500 else sale.price
        tvBasePrice.text = "Base Price: ${format.format(sale.price)}"
        tvCurrentBid.text = if (sale.currentBid > 0) {
            "Current Bid: ${format.format(sale.currentBid)}"
        } else {
            "No bids yet"
        }
        etBidAmount.setText(minBidAmount.toString())
        if (isCurrentUserHighestBidder) {
            tvMessage.visibility = View.VISIBLE
            tvMessage.text = "You are already the highest bidder. You cannot bid again until someone else places a bid."
            etBidAmount.isEnabled = false
        } else {
            tvMessage.visibility = View.GONE
            etBidAmount.isEnabled = true
        }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Place a Bid")
            .setView(bidView)
            .setPositiveButton("Bid", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            val bidButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            if (isCurrentUserHighestBidder) {
                bidButton.isEnabled = false
            }
            bidButton.setOnClickListener {
                val bidAmount = etBidAmount.text.toString().toDoubleOrNull()
                if (bidAmount != null && bidAmount >= minBidAmount && !isCurrentUserHighestBidder) {
                    binding.progressBar.visibility = View.VISIBLE
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val currentTime = System.currentTimeMillis()
                            val endTimeMs = sale.endDate.seconds * 1000
                            if (currentTime >= endTimeMs) {
                                withContext(Dispatchers.Main) {
                                    binding.progressBar.visibility = View.GONE
                                    Toast.makeText(context, "This auction has ended.", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                }
                                return@launch
                            }

                            // Get the latest sale data to ensure we have the most recent bid
                            val latestSale = FirebaseUtils.getSaleById(sale.id)
                            if (latestSale == null) {
                                withContext(Dispatchers.Main) {
                                    binding.progressBar.visibility = View.GONE
                                    Toast.makeText(context, "Sale not found", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                }
                                return@launch
                            }

                            // Check if the bid is still valid
                            if (bidAmount <= latestSale.currentBid) {
                                withContext(Dispatchers.Main) {
                                    binding.progressBar.visibility = View.GONE
                                    Toast.makeText(context, "Someone else has placed a higher bid. Please try again.", Toast.LENGTH_LONG).show()
                                    dialog.dismiss()
                                }
                                return@launch
                            }

                            // Update the sale with optimistic concurrency control
                            val updatedSale = latestSale.copy(
                                currentBid = bidAmount,
                                currentBidder = currentUserId
                            )
                            
                            // Use a transaction to ensure atomic update
                            val success = FirebaseUtils.updateSaleWithTransaction(updatedSale)
                            
                            if (success) {
                                withContext(Dispatchers.Main) {
                                    binding.progressBar.visibility = View.GONE
                                    Toast.makeText(context, "Bid placed successfully!", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    binding.progressBar.visibility = View.GONE
                                    Toast.makeText(context, "Failed to place bid. Please try again.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error placing bid", e)
                            withContext(Dispatchers.Main) {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(context, "Error placing bid: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else if (isCurrentUserHighestBidder) {
                    Toast.makeText(
                        context,
                        "You are already the highest bidder",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "Bid must be at least ${format.format(minBidAmount)}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        dialog.show()
    }

    private fun filterSales(query: String) {
        val filtered = if (query.isEmpty()) {
            allSales
        } else {
            allSales.filter { sale ->
                sale.brand.contains(query, true) || sale.model.contains(query, true)
            }
        }
        saleAdapter.submitList(filtered)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        salesListener?.remove()
        _binding = null
    }
} 