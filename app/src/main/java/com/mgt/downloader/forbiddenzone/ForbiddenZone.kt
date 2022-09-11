package com.mgt.downloader.forbiddenzone

import com.mgt.downloader.di.DI.config
import com.mgt.downloader.utils.Config

class ForbiddenZone {
    @Synchronized
    fun switchEnv() {
        when (config.getEnv()) {
            Config.Env.TEST -> config.setEnv(Config.Env.LIVE)
            Config.Env.LIVE -> config.setEnv(Config.Env.TEST)
        }
    }
}