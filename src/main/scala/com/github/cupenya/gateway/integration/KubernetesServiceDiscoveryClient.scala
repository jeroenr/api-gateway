package com.github.cupenya.gateway.integration

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.{ SSLContext, TrustManager, X509TrustManager }

import akka.actor.ActorSystem
import akka.http.scaladsl.{ Http, HttpsConnectionContext }
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.http.scaladsl.unmarshalling.{ Unmarshal, Unmarshaller }
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import com.github.cupenya.gateway.{ Config, Logging }
import spray.json._

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps

class KubernetesServiceDiscoveryClient()(implicit system: ActorSystem, ec: ExecutionContext, materializer: Materializer)
    extends ServiceDiscoverySource[KubernetesServiceUpdate] with KubernetesServiceUpdateParser with SprayJsonSupport with Logging {

  // FIXME: get rid of SSL hack
  private val trustAllCerts: Array[TrustManager] = Array(new X509TrustManager() {
    override def getAcceptedIssuers = null

    override def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = {}

    override def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = {}
  })

  private val ssl = SSLContext.getInstance("SSL")
  ssl.init(null, trustAllCerts, new SecureRandom())

  private val port = Config.integration.kubernetes.port
  private val host = Config.integration.kubernetes.host

  private lazy val client = if (port == 443) {
    Http(system).outgoingConnectionHttps(
      host,
      port,
      connectionContext = new HttpsConnectionContext(ssl),
      settings = ClientConnectionSettings(system)
    )
  } else {
    Http(system).outgoingConnection(host, port, settings = ClientConnectionSettings(system))
  }

  private val req = Get(s"/api/v1/services")
    .withHeaders(Connection("Keep-Alive"), Authorization(OAuth2BearerToken(Config.integration.kubernetes.token)))

  def healthCheck: Future[_] =
    Source.single(req).via(client).runWith(Sink.head).filter(_.status.isSuccess())

  def listServices: Future[List[KubernetesServiceUpdate]] = Source
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
          log.debug(s"Got Kubernetes service update $ksu")
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

  case class ServiceList(items: List[ServiceObject])

  implicit val portMappingFormat = jsonFormat4(PortMapping)
  implicit val specFormat = jsonFormat1(Spec)
  implicit val metadataFormat = jsonFormat4(Metadata)
  implicit val serviceObjectFormat = jsonFormat2(ServiceObject)
  implicit val serviceListFormat = jsonFormat1(ServiceList)

  // FIXME: is this really necessary?
  implicit val toServiceListUnmarshaller: Unmarshaller[HttpEntity, ServiceList] =
    Unmarshaller.withMaterializer { implicit ex ⇒ implicit mat ⇒ entity: HttpEntity ⇒
      entity.dataBytes
        .map(_.utf8String.parseJson)
        .collect {
          case jsObj: JsObject => jsObj.convertTo[ServiceList]
        }
        .runWith(Sink.head)
    }

  val DEFAULT_PORT = 8080

  protected def cleanMetadataString(value: String) =
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
