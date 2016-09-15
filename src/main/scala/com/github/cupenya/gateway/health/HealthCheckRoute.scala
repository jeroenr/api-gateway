package com.github.cupenya.gateway.health

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import akka.stream.Materializer
import com.github.cupenya.gateway.Logging
import spray.json.DefaultJsonProtocol

import scala.concurrent.ExecutionContext

trait HealthCheckRoute extends Directives with DefaultJsonProtocol with SprayJsonSupport with Logging {
  self: HealthCheckService =>

  implicit def ec: ExecutionContext
  implicit def system: ActorSystem
  implicit def materializer: Materializer

  case class HealthCheckResults(statuses: List[HealthCheckResult])

  implicit val healthCheckResultsFormat = jsonFormat1(HealthCheckResults)

  val healthRoute =
    path("health") {
      get {
        complete {
          runChecks().map(statuses =>
            statusCodeForStatuses(statuses) -> HealthCheckResults(statuses))
        }
      }
    }

  private def statusCodeForStatuses(statuses: List[HealthCheckResult]) =
    if (statuses.forall(_.status == HealthCheckStatus.Ok)) StatusCodes.OK else StatusCodes.InternalServerError
}
