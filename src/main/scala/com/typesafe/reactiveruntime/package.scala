/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe

package object reactiveruntime {

  type Traversable[+A] = scala.collection.immutable.Traversable[A]

  type Iterable[+A] = scala.collection.immutable.Iterable[A]

  type Seq[+A] = scala.collection.immutable.Seq[A]

  type IndexedSeq[+A] = scala.collection.immutable.IndexedSeq[A]
}
