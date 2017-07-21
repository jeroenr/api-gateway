package com.github.jeroenr.gateway.health

import akka.http.scaladsl.model.DateTime
import com.github.jeroenr.gateway.Logging
import com.github.jeroenr.gateway.client.AuthServiceClient
import com.github.jeroenr.service.discovery.health._

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

class AuthServiceHealthCheck(authClient: AuthServiceClient) extends HealthCheck with Logging {
  override def name: String = "AuthService"

  override def runCheck()(implicit ec: ExecutionContext): Future[HealthCheckResult] = {
    val start = System.nanoTime()
    authClient.health.filter(_.status.isSuccess()).map { _ =>
      {
        val timeElapsedInNanos = System.nanoTime() - start
        HealthCheckResult(
          name = name,
          status = HealthCheckStatus.Ok,
          timestamp = DateTime.now.clicks,
          latency = Some((timeElapsedInNanos nanos).toMillis),
          message = None
        )
      }
    }
  }
}
