package com.github.jeroenr.gateway.configuration

import com.github.jeroenr.gateway.client.GatewayTargetClient

case class GatewayConfiguration(targets: Map[String, GatewayTargetClient])
