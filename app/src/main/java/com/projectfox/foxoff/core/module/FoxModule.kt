package com.projectfox.foxoff.core.module

interface FoxModule {
    val descriptor: ModuleDescriptor
    suspend fun initialize()
    suspend fun start()
    suspend fun stop()
    suspend fun shutdown()
}

data class ModuleDescriptor(val id: String, val name: String)
