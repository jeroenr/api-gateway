package com.github.cupenya.gateway

import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory

object Config {
  private val rootConfig = ConfigFactory.load()

  object gateway {
    private val config = rootConfig.getConfig("gateway")
    val interface = config.getString("interface")
    val port = config.getInt("port")
  }

  object dashboard {
    private val config = rootConfig.getConfig("dashboard")
    val interface = config.getString("interface")
    val port = config.getInt("port")
  }

  object integration {
    private val config = rootConfig.getConfig("integration")

    object kubernetes {
      private val k8sConfig = config.getConfig("kubernetes")
      val host = k8sConfig.getString("host")
      val port = k8sConfig.getInt("port")
      val token = k8sConfig.getString("token")
    }

    object polling {
      private val reconnectConfig = config.getConfig("polling")
      val interval = reconnectConfig.getDuration("interval", TimeUnit.SECONDS)
    }
  }
}
