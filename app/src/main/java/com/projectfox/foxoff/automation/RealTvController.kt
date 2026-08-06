package com.projectfox.foxoff.automation

import com.projectfox.foxoff.tv.FoxTvEngine

class RealTvController(private val engine: FoxTvEngine) : TvController {
    override suspend fun pause() {
        engine.pause()
    }
}
