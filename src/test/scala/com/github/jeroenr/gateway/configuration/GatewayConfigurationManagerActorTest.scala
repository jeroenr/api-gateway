package com.github.jeroenr.gateway.configuration

import akka.actor.Props
import com.github.jeroenr.gateway.AkkaTestBase
import com.github.jeroenr.gateway.client.GatewayTargetClient
import com.github.jeroenr.gateway.model.GatewayTarget

class GatewayConfigurationManagerActorTest extends AkkaTestBase {
  sequential

  implicit val ec = system.dispatcher

  "GatewayConfigurationManagerActor" should {
    "insert gateway target" in {
      val ref = system.actorOf(Props(new GatewayConfigurationManagerActor))
      ref ! GatewayConfigurationManagerActor.GetGatewayConfig
      expectMsg(GatewayConfiguration(Map.empty[String, GatewayTargetClient]))
      ref ! GatewayConfigurationManagerActor.UpsertGatewayTarget(GatewayTarget("foo", "localhost", 80, false))
      ref ! GatewayConfigurationManagerActor.GetGatewayConfig
      expectMsg(GatewayConfiguration(Map(
        "foo" -> GatewayTargetClient("localhost", 80, false)
      )))
      success
    }

    "update gateway target" in {
      val ref = system.actorOf(Props(new GatewayConfigurationManagerActor))
      ref ! GatewayConfigurationManagerActor.GetGatewayConfig
      expectMsg(GatewayConfiguration(Map.empty[String, GatewayTargetClient]))
      ref ! GatewayConfigurationManagerActor.UpsertGatewayTarget(GatewayTarget("foo", "localhost", 80, false))
      ref ! GatewayConfigurationManagerActor.UpsertGatewayTarget(GatewayTarget("foo", "localhost", 80, true))
      ref ! GatewayConfigurationManagerActor.GetGatewayConfig
      expectMsg(GatewayConfiguration(Map(
        "foo" -> GatewayTargetClient("localhost", 80, true)
      )))
      success
    }

    "insert gateway targets" in {
      val ref = system.actorOf(Props(new GatewayConfigurationManagerActor))
      ref ! GatewayConfigurationManagerActor.GetGatewayConfig
      expectMsg(GatewayConfiguration(Map.empty[String, GatewayTargetClient]))
      ref ! GatewayConfigurationManagerActor.SetGatewayTargets(List(GatewayTarget("foo", "localhost", 80, false), GatewayTarget("bar", "localhost", 9090, true)))
      ref ! GatewayConfigurationManagerActor.GetGatewayConfig
      expectMsg(GatewayConfiguration(Map(
        "foo" -> GatewayTargetClient("localhost", 80, false),
        "bar" -> GatewayTargetClient("localhost", 9090, true)
      )))
      success
    }

  }

}
