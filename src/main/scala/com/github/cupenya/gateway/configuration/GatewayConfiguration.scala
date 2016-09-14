package com.github.cupenya.gateway.configuration

import com.github.cupenya.gateway.client.GatewayTargetClient

case class GatewayConfiguration(targets: Map[String, GatewayTargetClient])
