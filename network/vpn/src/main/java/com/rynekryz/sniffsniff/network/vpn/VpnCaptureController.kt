package com.rynekryz.sniffsniff.network.vpn

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.rynekryz.sniffsniff.core.common.repository.CaptureController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnCaptureController @Inject constructor(
    @ApplicationContext private val context: Context,
) : CaptureController {
    override fun start() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, SniffVpnService::class.java).setAction(SniffVpnService.ACTION_START),
        )
    }

    override fun stop() {
        context.startService(Intent(context, SniffVpnService::class.java).setAction(SniffVpnService.ACTION_STOP))
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VpnModule {
    @Binds
    abstract fun captureController(controller: VpnCaptureController): CaptureController
}
