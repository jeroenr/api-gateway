package com.github.cupenya.gateway.integration

import java.security.cert.X509Certificate
import java.security.SecureRandom
import javax.net.ssl.{ SSLContext, TrustManager, X509TrustManager }

import akka.actor.ActorSystem
import akka.http.scaladsl.{ Http, HttpsConnectionContext }
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{ HttpRequest, _ }
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import com.github.cupenya.gateway.{ Config, Logging }
import spray.json._

import scala.language.postfixOps
import scala.concurrent.{ ExecutionContext, Future }

class KubernetesServiceDiscoveryClient()(implicit system: ActorSystem, ec: ExecutionContext, materializer: Materializer)
    extends ServiceDiscoverySource[KubernetesServiceUpdate] with KubernetesServiceUpdateParser with Logging {

  // FIXME: get rid of SSL hack
  private val trustAllCerts: Array[TrustManager] = Array(new X509TrustManager() {
    override def getAcceptedIssuers = null

    override def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = {}

    override def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = {}
  })

  private val ssl = SSLContext.getInstance("SSL")
  ssl.init(null, trustAllCerts, new SecureRandom())

  lazy val client = Http(system).outgoingConnectionHttps(
    Config.integration.kubernetes.host,
    Config.integration.kubernetes.port,
    connectionContext = new HttpsConnectionContext(ssl),
    settings = ClientConnectionSettings(system)
  )

  private val req = HttpRequest(GET, Uri(s"/api/v1/services"))
    .withHeaders(Connection("Keep-Alive"), Authorization(OAuth2BearerToken(Config.integration.kubernetes.token)))

  def source: Future[List[KubernetesServiceUpdate]] = Source
    .single(req)
    .via(client)
    .mapAsync(1)(res =>
      Unmarshal(res.entity).to[ServiceList]
        .map(_.items.flatMap(so => so.metadata.labels.flatMap(_.get("resource")).map(resource => {
          val ksu = KubernetesServiceUpdate(
            UpdateType.Addition,
            cleanMetadataString(so.metadata.name),
            cleanMetadataString(resource),
            cleanMetadataString(so.metadata.namespace),
            so.spec.ports.headOption.map(_.port).getOrElse(DEFAULT_PORT)
          )
          log.info(s"Got Kubernetes service update $ksu")
          ksu
        }))))
    .runWith(Sink.head)

  override def name: String = "Kubernetes API"
}

trait KubernetesServiceUpdateParser extends DefaultJsonProtocol with Logging {

  case class PortMapping(protocol: String, port: Int, targetPort: Int, nodePort: Option[Int])

  case class Spec(ports: List[PortMapping])

  case class Metadata(uid: String, name: String, namespace: String, labels: Option[Map[String, String]])

  case class ServiceObject(spec: Spec, metadata: Metadata)

  //  case class ServiceMutation(`type`: UpdateType, `object`: ServiceObject)

  case class ServiceList(items: List[ServiceObject])

  implicit val portMappingFormat = jsonFormat4(PortMapping)
  implicit val specFormat = jsonFormat1(Spec)
  implicit val metadataFormat = jsonFormat4(Metadata)
  implicit val serviceObjectFormat = jsonFormat2(ServiceObject)
  implicit val serviceListFormat = jsonFormat1(ServiceList)

  //  implicit object UpdateTypeFormat extends RootJsonFormat[UpdateType] {
  //    override def read(json: JsValue): UpdateType = json match {
  //      case JsString("ADDED") => UpdateType.Addition
  //      case JsString("DELETED") => UpdateType.Deletion
  //      case JsString("MODIFIED") => UpdateType.Mutation
  //      case _ =>
  //        throw DeserializationException(s"Couldn't deserialize $json. Was expecting one of [ADDED, DELETED, MODIFIED]")
  //    }
  //
  //    override def write(updateType: UpdateType): JsValue = updateType match {
  //      case UpdateType.Addition => JsString("ADDED")
  //      case UpdateType.Deletion => JsString("DELETED")
  //      case UpdateType.Mutation => JsString("MODIFIED")
  //    }
  //  }

  //  implicit val serviceMutationFormat = jsonFormat2(ServiceMutation)

  val DEFAULT_PORT = 8080

  //  def toKubernetesServiceUpdate(jsObject: JsObject): Option[KubernetesServiceUpdate] = {
  //    val serviceMutation = jsObject.convertTo[ServiceMutation]
  //    val serviceObject = serviceMutation.`object`
  //    val metadata: Metadata = serviceObject.metadata
  //    metadata.labels.flatMap(_.get("resource")).map { resource =>
  //      KubernetesServiceUpdate(
  //        serviceMutation.`type`,
  //        cleanMetadataString(metadata.name),
  //        cleanMetadataString(resource),
  //        cleanMetadataString(metadata.namespace),
  //        serviceObject.spec.ports.headOption.map(_.port).getOrElse(DEFAULT_PORT)
  //      )
  //    }
  //  }

  def cleanMetadataString(value: String) =
    value.filterNot('"' ==)
}

trait KubernetesNamespace {
  def namespace: String
}

trait DiscoverableThroughDns extends DiscoverableAddress with KubernetesNamespace {
  self: KubernetesNamespace =>
  val name: String
  def address: String = s"$name.$namespace"
}

case class KubernetesServiceUpdate(updateType: UpdateType, name: String, resource: String, namespace: String, port: Int)
  extends ServiceUpdate
  with DiscoverableThroughDns
