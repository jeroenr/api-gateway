package com.github.cupenya.gateway.configuration

import java.util.concurrent.atomic.AtomicReference

import com.github.cupenya.gateway.client.GatewayTarget


trait GatewayConfigurationManager {
  private val configHolder = new AtomicReference[GatewayConfiguration](GatewayConfiguration(Map.empty[String, GatewayTarget]))

  def currentConfig(): GatewayConfiguration =
    configHolder.get()

  def addGatewayTarget(resource: String, target: GatewayTarget): Unit = {
    val current = configHolder.get()
    configHolder.lazySet(current.copy(current.targets.updated(resource, target)))
  }

  def setConfig(config: GatewayConfiguration): Unit =
    configHolder.lazySet(config)
}
