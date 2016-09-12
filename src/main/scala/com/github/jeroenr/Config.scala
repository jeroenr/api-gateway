package com.github.jeroenr

import com.typesafe.config.ConfigFactory

trait Config {
  private val config = ConfigFactory.load()
  private val httpConfig = config.getConfig("boot")
  val interface = httpConfig.getString("interface")
  val httpPort = httpConfig.getInt("port")
}
