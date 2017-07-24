package com.github.jeroenr.gateway

import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConversions._
import scala.util.Try
import scala.language.postfixOps

object Config {
  private val rootConfig = ConfigFactory.load()
  val DEFAULT_TIMEOUT = 10 seconds

  object gateway {
    private val config = rootConfig.getConfig("gateway")
    val interface = config.getString("interface")
    val port = config.getInt("port")
    val prefix = config.getString("prefix")
  }

  object dashboard {
    private val config = rootConfig.getConfig("dashboard")
    val interface = config.getString("interface")
    val port = config.getInt("port")
  }

  object integration {
    private val config = rootConfig.getConfig("integration")

    object authentication {
      private val authConfig = config.getConfig("authentication")
      val host = authConfig.getString("host")
      val port = authConfig.getInt("port")
    }

    object kubernetes {
      private val k8sConfig = config.getConfig("kubernetes")
      val host = k8sConfig.getString("host")
      val port = k8sConfig.getInt("port")
      val token = k8sConfig.getString("token")
      val namespaces = k8sConfig.getStringList("namespaces").toList
    }

    object polling {
      private val pollingConfig = config.getConfig("polling")
      val enabled = Try(pollingConfig.getBoolean("enabled")).getOrElse(false)
    }
  }
}
