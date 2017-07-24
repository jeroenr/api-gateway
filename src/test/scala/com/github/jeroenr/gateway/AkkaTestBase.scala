package com.github.jeroenr.gateway

import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.testkit.{ ImplicitSender, TestKit }
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.AfterAll

abstract class AkkaTestBase extends TestKit(ActorSystem("test-actor-system")) with ImplicitSender with AfterAll with SpecificationLike {

  //  implicit val ec = system.dispatcher
  implicit val materializer = ActorMaterializer()
  val sys = system

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }
}
