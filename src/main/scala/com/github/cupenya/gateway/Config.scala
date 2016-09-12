package com.github.cupenya.gateway

import com.typesafe.config.ConfigFactory

trait Config {
  private val config = ConfigFactory.load()
  private val httpConfig = config.getConfig("http")
  val interface = httpConfig.getString("interface")
  val httpPort = httpConfig.getInt("port")
}
