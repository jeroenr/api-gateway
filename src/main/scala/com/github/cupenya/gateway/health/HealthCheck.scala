package com.github.cupenya.gateway.health

import scala.concurrent.{ExecutionContext, Future}

trait HealthCheck {
  def name: String

  def runCheck()(implicit ec: ExecutionContext): Future[HealthCheckResult]
}
