package com.rynekryz.sniffsniff.network.capture

import com.rynekryz.sniffsniff.core.common.repository.TrafficRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureModule {
    @Binds
    abstract fun trafficRepository(engine: CaptureEngine): TrafficRepository
}
