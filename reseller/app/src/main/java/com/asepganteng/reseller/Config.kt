package com.asepganteng.reseller

/**
 * Pusat konfigurasi URL panel
 * Update di sini, otomatis berlaku ke semua MainActivity
 * 
 * Format: http(s)://domain:port/role
 */
object PanelConfig {
    // Update ini ke domain/port server web panel kamu
    const val PANEL_BASE_URL = "http://assistantpanel.bypstar7.web.id:2418"
    
    const val OWNER_ENDPOINT = "$PANEL_BASE_URL/owner"
    const val RESELLER_ENDPOINT = "$PANEL_BASE_URL/reseller"
    const val ANONYMOUS_ENDPOINT = "$PANEL_BASE_URL/anonymous-q9zk3xr7vb2mt5"
    const val PEMILIK_ENDPOINT = "$PANEL_BASE_URL/pemilik-x7fq2mz9wr3kd8"
}
