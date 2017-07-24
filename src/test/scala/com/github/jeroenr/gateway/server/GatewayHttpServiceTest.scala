package com.github.jeroenr.gateway.server

import akka.actor.{ ActorRef, ActorSystem }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.testkit.TestProbe
import akka.util.Timeout
import com.github.jeroenr.gateway.{ AkkaTestBase, Config }
import com.github.jeroenr.gateway.client.{ AuthServiceClient, GatewayTargetClient }
import akka.http.scaladsl.testkit.Specs2RouteTest
import akka.http.scaladsl.server._
import org.specs2.mutable._
import com.github.jeroenr.gateway.configuration.{ GatewayConfiguration, GatewayConfigurationManagerActor }

import scala.concurrent.{ ExecutionContext, Future }

class GatewayHttpServiceTest extends Specification with Specs2RouteTest {
  sequential

  val sys = system

  "GatewayHttpService" should {
    "have dynamic route" in {
      val svc = new GatewayHttpService {
        override implicit def ec: ExecutionContext = sys.dispatcher

        override implicit def system: ActorSystem = sys

        override val authClient: AuthServiceClient = null
        override val gatewayConfigurationManager: ActorRef = null
        override implicit val materializer: Materializer = ActorMaterializer()
        override implicit val timeout: Timeout = Config.DEFAULT_TIMEOUT

        override protected def currentConfig: Future[GatewayConfiguration] =
          Future.successful(GatewayConfiguration(
            Map(
              "foo" -> GatewayTargetClient("localhost", 80, false)
            )
          ))
      }
      Get("/api/foo") ~> svc.gatewayRoute ~> check {
        handled should beTrue
      }
    }

    "not handle unexisting routes" in {
      val svc = new GatewayHttpService {
        override implicit def ec: ExecutionContext = sys.dispatcher

        override implicit def system: ActorSystem = sys

        override val authClient: AuthServiceClient = null
        override val gatewayConfigurationManager: ActorRef = null
        override implicit val materializer: Materializer = ActorMaterializer()
        override implicit val timeout: Timeout = Config.DEFAULT_TIMEOUT
        override protected def currentConfig: Future[GatewayConfiguration] =
          Future.successful(GatewayConfiguration(Map.empty[String, GatewayTargetClient]))
      }

      Get("/api/foo") ~> svc.gatewayRoute ~> check {
        handled should beFalse
      }
    }
  }
}
