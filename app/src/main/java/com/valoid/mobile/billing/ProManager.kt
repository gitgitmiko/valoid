package com.valoid.mobile.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.valoid.mobile.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Status Valoid Pro dari langganan Google Play `valoid_pro`. Status terakhir disimpan di perangkat
 * agar tersedia saat offline dan untuk worker latar; Google Play tetap sumber kebenarannya.
 */
object ProManager : PurchasesUpdatedListener {
    const val PRODUCT_ID = "valoid_pro"
    const val FALLBACK_PRICE = "Rp19.000"

    private const val PREFS = "pro"
    private const val KEY_ACTIVE = "active"
    private const val KEY_DEBUG = "debug_override"

    private lateinit var appContext: Context
    private var client: BillingClient? = null
    private var details: ProductDetails? = null
    private var purchaseActive = false

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro

    /** Harga dari Google Play bila produk tersedia, misalnya "Rp19.000". */
    private val _price = MutableStateFlow<String?>(null)
    val price: StateFlow<String?> = _price

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        purchaseActive = prefs().getBoolean(KEY_ACTIVE, false)
        publish()
        client = BillingClient.newBuilder(appContext)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        connect()
    }

    /** Status Pro tersimpan, untuk komponen tanpa UI seperti worker. */
    fun isProCached(context: Context): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getBoolean(KEY_ACTIVE, false) || (BuildConfig.DEBUG && p.getBoolean(KEY_DEBUG, false))
    }

    val debugOverride: Boolean get() = BuildConfig.DEBUG && prefs().getBoolean(KEY_DEBUG, false)

    /** Hanya untuk build debug: menyalakan Pro tanpa pembelian untuk pengujian. */
    fun setDebugOverride(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        prefs().edit().putBoolean(KEY_DEBUG, enabled).apply()
        publish()
    }

    fun refresh() {
        val c = client ?: return
        if (!c.isReady) {
            connect()
            return
        }
        queryProduct(c)
        queryPurchases(c)
    }

    fun purchase(activity: Activity) {
        val c = client
        val d = details
        val offer = d?.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (c == null || !c.isReady || d == null || offer == null) {
            _message.value = "Langganan belum tersedia. Produk Valoid Pro belum aktif di Google Play."
            refresh()
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).setOfferToken(offer).build()),
            )
            .build()
        c.launchBillingFlow(activity, params)
    }

    fun clearMessage() {
        _message.value = null
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> handle(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            else -> _message.value = "Pembelian gagal (${result.debugMessage.ifBlank { result.responseCode.toString() }})."
        }
    }

    private fun connect() {
        client?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private fun queryProduct(c: BillingClient) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()
        c.queryProductDetailsAsync(params) { result, list ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            details = list.firstOrNull()
            _price.value = details?.subscriptionOfferDetails?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
        }
    }

    private fun queryPurchases(c: BillingClient) {
        c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result, list ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(list, replaceAll = true)
        }
    }

    private fun handle(purchases: List<Purchase>, replaceAll: Boolean = false) {
        val active = purchases.filter { PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
        active.filterNot { it.isAcknowledged }.forEach { p ->
            client?.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { }
        }
        if (replaceAll || active.isNotEmpty()) {
            purchaseActive = active.isNotEmpty()
            prefs().edit().putBoolean(KEY_ACTIVE, purchaseActive).apply()
            publish()
        }
    }

    private fun publish() {
        _isPro.value = purchaseActive || debugOverride
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
