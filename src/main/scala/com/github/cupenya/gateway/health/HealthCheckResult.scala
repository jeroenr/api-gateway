package com.github.cupenya.gateway.health

import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat}

/**
  * A health check status reflects the current status of a health check
  * after it has been successfully run.
  */
sealed trait HealthCheckStatus

object HealthCheckStatus {
  /**
    * Indicating the health check is running normally.
    */
  case object Ok extends HealthCheckStatus

  /**
    * Indicating that the health check is still running, i.e. functionality still provided,
    * but going toward a critical state,
    * e.g. there are higher than expected latencies or something is running out of space.
    */
  case object Warning extends HealthCheckStatus

  /**
    * Critical indicates that a health check failed and functionality is probably not
    * available anymore.
    */
  case object Critical extends HealthCheckStatus

  /**
    * Unknown indicates that the health check is in an undetermined state. This should typically
    * not be used but is reflected here because it is a standard in a lot of tools supporting
    * health checks. Unknown status should be either critical or warning either way.
    */
  case object Unknown extends HealthCheckStatus
}

/**
  * The basic data model for any health check run in the Cupenya framework.
  */
sealed trait HealthCheckModel

object HealthCheckModel extends DefaultJsonProtocol {

  implicit object HealthCheckStatusFormat extends JsonFormat[HealthCheckStatus] {
    import HealthCheckStatus._

    def write(healthCheckStatus: HealthCheckStatus): JsValue = healthCheckStatus match {
      case Ok => JsString("ok")
      case Warning => JsString("warning")
      case Critical => JsString("critical")
      case Unknown => JsString("unknown")
    }

    def read(jsValue: JsValue): HealthCheckStatus = jsValue match {
      case JsString("ok") => Ok
      case JsString("warning") => Warning
      case JsString("critical") => Critical
      case JsString("unknown") => Unknown
      case e => throw DeserializationException("Enum string expected, but got: " + e)
    }
  }

  implicit val healthCheckResultFormat = jsonFormat6(HealthCheckResult)
}

/**
  * A result of a single health check run.
  *
  * @param name the name of the executed health check.
  * @param status the status of the health check after this run.
  * @param timestamp the time when the health check was performed, indicating when the associated status was valid.
  * @param latency how long it took to run the health check, for mosts checks this reflects
  *                the latency for the checked service, but for other checks, e.g. disk space,
  *                it can also just indicate how long the actual test took.
  * @param message optional additional information or a description of why this status occured.
  * @param tags an optional list of key val pairs for this check to give some more context.
  */
case class HealthCheckResult(
                              name: String,
                              status: HealthCheckStatus,
                              timestamp: Long,
                              latency: Option[Long] = None,
                              message: Option[String] = None,
                              tags: Map[String, String] = Map.empty
                            ) extends HealthCheckModel
