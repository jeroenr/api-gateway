package com.github.cupenya.gateway.health

import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.http.scaladsl.model.DateTime

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.language.postfixOps

trait HealthCheckService {
  def checks: List[HealthCheck]

  def runChecks()(implicit ec: ExecutionContext, actorSystem: ActorSystem): Future[List[HealthCheckResult]] =
    Future.sequence(checks.map(_.checkWithRecovery))

  implicit class RichHealthCheck(check: HealthCheck) {
    def checkWithRecovery()(implicit ec: ExecutionContext, actorSystem: ActorSystem): Future[HealthCheckResult] =
      check
        .runCheck()
        .timeoutAfter(5 seconds)
        .recover {
          case e => HealthCheckResult(
            name = check.name,
            status = HealthCheckStatus.Critical,
            timestamp = DateTime.now.clicks,
            message = Some(s"Error while executing health check: ${e.getMessage}")
          )
        }
  }

  implicit class FutureExtensions[T](f: Future[T]) {
    import akka.pattern._

    def timeoutAfter(d: FiniteDuration)(implicit ec: ExecutionContext, sys: ActorSystem): Future[T] = {
      val eventualTimeout = after(d, sys.scheduler)(Future.failed(new TimeoutException(s"Timed out after ${d.toMillis} ms")))
      Future firstCompletedOf (f :: eventualTimeout :: Nil)
    }

  }
}
