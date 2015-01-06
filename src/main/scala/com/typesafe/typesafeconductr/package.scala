/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe

import akka.actor.{ Address, AddressFromURIString }
import akka.cluster.UniqueAddress
import com.typesafe.typesafeconductr.ConductRController.{ BundleExecution, BundleInfo, BundleInstallation, SchedulingRequirement }
import play.api.libs.json.{ Format, Json, JsResult, JsString, JsValue }
import java.net.URI

package object typesafeconductr {

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

  implicit val uniqueAddressFormat: Format[UniqueAddress] =
    Json.format

  implicit val schedulingRequirementFormat: Format[SchedulingRequirement] =
    Json.format

  implicit val bundleInstallationFormat: Format[BundleInstallation] =
    Json.format

  implicit val bundleExecutionFormat: Format[BundleExecution] =
    Json.format

  implicit val bundleInfoFormat: Format[BundleInfo] =
    Json.format
}
