/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr

import akka.actor.{ Address, AddressFromURIString }
import akka.cluster.UniqueAddress
import java.net.URI
import play.api.libs.json.{ Format, Json, JsResult, JsString, JsValue }

package object client {

  type Traversable[+A] = scala.collection.immutable.Traversable[A]

  type Iterable[+A] = scala.collection.immutable.Iterable[A]

  type Seq[+A] = scala.collection.immutable.Seq[A]

  type IndexedSeq[+A] = scala.collection.immutable.IndexedSeq[A]

  implicit object AddressFormat extends Format[Address] {
    override def writes(address: Address): JsValue = JsString(address.toString)
    override def reads(json: JsValue): JsResult[Address] = implicitly[Format[String]].reads(json).map(AddressFromURIString(_))
  }

  implicit object UriFormat extends Format[URI] {
    override def writes(uri: URI): JsValue = implicitly[Format[String]].writes(uri.toString)
    override def reads(json: JsValue): JsResult[URI] = implicitly[Format[String]].reads(json).map(new URI(_))
  }

  implicit val uniqueAddressFormat: Format[UniqueAddress] = Json.format[UniqueAddress]

  // TODO This import is only needed for Play 2.3; with Play 2.4 we can use the qualified return type `Format[ConductRController.Attributes]`
  import ConductRController.Attributes
  implicit val attributesFormat: Format[Attributes] = Json.format

  // TODO This import is only needed for Play 2.3; with Play 2.4 we can use the qualified return type `Format[ConductRController.BundleInstallation]`
  import ConductRController.BundleInstallation
  implicit val bundleInstallationFormat: Format[BundleInstallation] = Json.format

  // TODO This import is only needed for Play 2.3; with Play 2.4 we can use the qualified return type `Format[ConductRController.BundleExecution]`
  import ConductRController.BundleExecution
  implicit val bundleExecutionFormat: Format[BundleExecution] = Json.format

  // TODO This import is only needed for Play 2.3; with Play 2.4 we can use the qualified return type `Format[ConductRController.BundleInfo]`
  import ConductRController.BundleInfo
  implicit val bundleInfoFormat: Format[BundleInfo] = Json.format

  // TODO This import is only needed for Play 2.3; with Play 2.4 we can use the qualified return type `Format[ConductRController.BundleInfo]`
  import ConductRController.Event
  implicit val eventFormat: Format[Event] = Json.format

  // TODO This import is only needed for Play 2.3; with Play 2.4 we can use the qualified return type `Format[ConductRController.BundleInfo]`
  import ConductRController.Log
  implicit val logFormat: Format[Log] = Json.format
}
