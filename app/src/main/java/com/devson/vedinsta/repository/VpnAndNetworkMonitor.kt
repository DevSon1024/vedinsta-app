package com.devson.vedinsta.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VpnAndNetworkMonitor(private val context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    
    private val _isVpnActive = MutableStateFlow(checkVpnActiveDirect())
    val isVpnActive: StateFlow<Boolean> = _isVpnActive.asStateFlow()

    private val _isNetworkChanged = MutableStateFlow(false)
    val isNetworkChanged: StateFlow<Boolean> = _isNetworkChanged.asStateFlow()

    private var initialNetworkId: String? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            val currentNetworkId = network.toString()
            if (initialNetworkId == null) {
                initialNetworkId = currentNetworkId
            } else if (initialNetworkId != currentNetworkId) {
                _isNetworkChanged.value = true
                Log.w("VpnAndNetworkMonitor", "Network interface changed from $initialNetworkId to $currentNetworkId")
            }
            updateVpnState()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            updateVpnState()
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            updateVpnState()
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.e("VpnAndNetworkMonitor", "Failed to register network callback", e)
        }
    }

    fun unregister() {
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e("VpnAndNetworkMonitor", "Failed to unregister network callback", e)
        }
    }

    fun resetNetworkChangeWarning() {
        _isNetworkChanged.value = false
    }

    private fun updateVpnState() {
        _isVpnActive.value = checkVpnActiveDirect()
    }

    fun checkVpnActiveDirect(): Boolean {
        connectivityManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } else {
            val networks = connectivityManager.allNetworks
            for (network in networks) {
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: continue
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return true
                }
            }
            return false
        }
    }
}
