# sbt-conductr #

[![Build Status](https://api.travis-ci.org/sbt/sbt-conductr.png?branch=master)](https://travis-ci.org/sbt/sbt-conductr)

sbt-conductr is an sbt plugin designed to facilitate the development lifecycle at the stage where deployment
to Typesafe ConductR is required.

## Usage

With the ConductR running you can upload a file using sbt:

```bash
cd sbt-conductr-tester/
sbt
```

The above puts you into the console of a test project. The test project is configured to use the sbt-native-packager
to produce RR bundles, and declares some scheduling configuration. To learn more check out the build.sbt file of this
same directory.

Produce a bundle by typing:

```bash
bundle:dist
```

A bundle will be produced from the native packager settings of this project. A bundle effectively wraps a native
packager distribution and includes some component configuration. To load the bundle:

```bash
conduct load <HIT THE TAB KEY AND THEN RETURN>
```

Using the tab completion feature of sbt will produce a URI representing the location of the last distribution
produced by the native packager.

Hitting return will cause the bundle to be uploaded. On successfully uploading the bundle the plugin will report
the `BundleId` to use for subsequent commands on that bundle.

### To use sbt-typesafe-conductr in your own project

Add the `sbt-conductr` plugin:

```scala
addSbtPlugin("com.typesafe.conductr" % "sbt-conductr" % "0.32.0")
```

You must also enable the plugin explicitly for your project:

```scala
lazy val root = project
  .in(file("."))
  .enablePlugins(ConductRPlugin, <your other plugins go here>)
```

_Note that if you have used Play 2.3 that you must also additionally enable `JavaAppPackaging` for your build e.g.:_

```scala
enablePlugins(JavaAppPackaging, PlayScala, ConductRPlugin)
```

_Note also that if you have used a pre 1.0 version of sbt-native-packager then you must remove imports such as the following from your `.sbt` files:_


```scala
import com.typesafe.sbt.SbtNativePackager._
import NativePackagerKeys._
```

_...otherwise you will get duplicate imports reported. This is because the new 1.0+ version uses sbt's auto plugin feature._

Unless the ConductR is running at `127.0.0.1:9005`, and instead supposing that it is running at
`192.168.59.103:9005` then you will typically issue the following command from the sbt console:

```
controlServer 192.168.59.103:9005
```

This then sets the sbt session up to subsequently communicate with the ConductR at 192.168.59.103 on port 9005.

The following `sbt-conductr` commands are available:

Property               | Description
-----------------------|------------
conduct info           | Gain infomation on the cluster
conduct load           | Loads a bundle and an optional configuration to the ConductR
conduct run            | Runs a bundle given a bundle id with an optional absolute scale value
conduct stop           | Stops all executions of a bundle given a bundle id
conduct unload         | Unloads a bundle entirely (requires that the bundle has stopped executing everywhere)

&copy; Typesafe Inc., 2014-2015
