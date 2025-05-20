package com.bidware.fragments

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bidware.MainActivity
import com.bidware.ProfileActivity
import com.bidware.R
import com.bidware.adapters.SaleAdapter
import com.bidware.databinding.FragmentWishlistBinding
import com.bidware.models.Sale
import com.bidware.utils.FirebaseUtils
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WishlistFragment : Fragment() {
    private var _binding: FragmentWishlistBinding? = null
    private val binding get() = _binding!!

    private val allPurchases = mutableListOf<Sale>()
    private val filteredItems = mutableListOf<Sale>()
    private lateinit var saleAdapter: SaleAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWishlistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        setupSearch()
        loadUserPurchases()
    }

    private fun setupToolbar() {
        // Set menu item click listener
        binding.btnMenu.setOnClickListener {
            // Show menu options
            val popupMenu = android.widget.PopupMenu(requireContext(), binding.btnMenu)
            popupMenu.menuInflater.inflate(R.menu.main_menu, popupMenu.menu)
            
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_profile -> {
                        val intent = Intent(requireContext(), ProfileActivity::class.java)
                        startActivity(intent)
                        true
                    }
                    R.id.action_logout -> {
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
                // Navigate to sale details
                val saleDetailsFragment = SaleDetailsFragment.newInstance(sale)
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, saleDetailsFragment)
                    .addToBackStack(null)
                    .commit()
            }
        )
        
        binding.wishlistRecyclerView.apply {
            adapter = saleAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun setupSearch() {
        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterPurchases(s.toString())
            }
        })
    }

    private fun loadUserPurchases() {
        showLoading()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId != null) {
                    val purchases = FirebaseUtils.getUserPurchases(userId)
                    withContext(Dispatchers.Main) {
                        updatePurchaseItems(purchases)
                        hideLoading()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showError("User not logged in")
                        hideLoading()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showError("Error loading purchases: ${e.message}")
                    hideLoading()
                }
            }
        }
    }

    private fun filterPurchases(query: String) {
        val filtered = if (query.isEmpty()) {
            allPurchases
        } else {
            allPurchases.filter { sale ->
                sale.brand.contains(query, true) || 
                sale.model.contains(query, true) ||
                sale.description.contains(query, true)
            }
        }
        
        filteredItems.clear()
        filteredItems.addAll(filtered)
        saleAdapter.submitList(filteredItems)
        updateEmptyState()
    }

    private fun updatePurchaseItems(items: List<Sale>) {
        allPurchases.clear()
        allPurchases.addAll(items)
        
        filteredItems.clear()
        filteredItems.addAll(items)
        
        saleAdapter.submitList(filteredItems)
        updateEmptyState()
    }

    private fun updateEmptyState() {
        binding.emptyView.text = "No purchased vehicles"
        if (filteredItems.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.wishlistRecyclerView.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.wishlistRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.wishlistRecyclerView.visibility = View.GONE
        binding.emptyView.visibility = View.GONE
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        updateEmptyState()
    }

    private fun showError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        loadUserPurchases() // Refresh when returning to this fragment
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 