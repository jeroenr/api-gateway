package com.github.cupenya.gateway.configuration

import com.github.cupenya.gateway.client.GatewayTarget

case class GatewayConfiguration(targets: Map[String, GatewayTarget])
