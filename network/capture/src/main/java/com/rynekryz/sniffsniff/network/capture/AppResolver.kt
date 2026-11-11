package com.rynekryz.sniffsniff.network.capture

import android.content.Context
import android.net.ConnectivityManager
import com.rynekryz.sniffsniff.core.common.model.UNKNOWN_UID
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

class AppIdentity(val packageName: String, val label: String)

@Singleton
class AppResolver @Inject constructor(@ApplicationContext context: Context) {
    private val packageManager = context.packageManager
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val cache = ConcurrentHashMap<Int, AppIdentity>()

    fun ownerUid(protocolNumber: Int, local: InetSocketAddress, remote: InetSocketAddress): Int {
        val uid = runCatching { connectivity.getConnectionOwnerUid(protocolNumber, local, remote) }.getOrDefault(UNKNOWN_UID)
        return if (uid < 0) UNKNOWN_UID else uid
    }

    fun identify(uid: Int): AppIdentity = cache.getOrPut(uid) { resolve(uid) }

    @Suppress("DEPRECATION")
    private fun resolve(uid: Int): AppIdentity {
        val packageName = packageManager.getPackagesForUid(uid)?.firstOrNull()
            ?: return AppIdentity("uid:$uid", if (uid == 0) "root" else "UID $uid")
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
        return AppIdentity(packageName, label)
    }
}
