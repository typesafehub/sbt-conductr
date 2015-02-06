# sbt-typesafe-conductr #

sbt-typesafe-conductr is an sbt plugin designed to facilitate the development lifecycle at the stage where deployment
to Typesafe ConductR is required.

## Usage

With the ConductR running you can upload a file using sbt:

```bash
cd sbt-typesafe-conductr-tester/
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
loadBundle <HIT THE TAB KEY AND THEN RETURN>
```

Using the tab completion feature of sbt will produce a URI representing the location of the last distribution
produced by the native packager.

Hitting return will cause the bundle to be uploaded. On successfully uploading the bundle the plugin will report
the `BundleId` to use for subsequent commands on that bundle.

### To use sbt-typesafe-conductr in your own project

Add the `sbt-typesafe-conductr` plugin:

```scala
addSbtPlugin("com.typesafe.conductr" % "sbt-typesafe-conductr" % "0.19.0")
```

You must also enable the plugin explicitly for your project:

```scala
lazy val root = project
  .in(file("."))
  .enablePlugins(SbtTypesafeConductR, <your other plugins go here>)
```

_Note that if you have used Play 2.3 that you must also additionally enable `JavaAppPackaging` for your build e.g.:_

```scala
enablePlugins(JavaAppPackaging, PlayScala, SbtTypesafeConductR)
```

_Note also that if you have used a pre 1.0 version of sbt-native-packager then you must remove imports such as the following from your `.sbt` files:_


```scala
import com.typesafe.sbt.SbtNativePackager._
import NativePackagerKeys._
```

_...otherwise you will get duplicate imports reported. This is because the new 1.0+ version uses sbt's auto plugin feature._


The following properties will be required if you will be loading bundles:

Property       | Description
---------------|------------
nrOfCpus       | The number of cpus required to run the bundle.
memory         | The amount of memory required to run the bundle.
diskSpace      | The amount of disk space required to host an expanded bundle and configuration.
roles          | The types of node in the cluster that this bundle can be deployed to.

An sample section from a build.sbt then setting the above given that loading bundles will be required:

```scala
ConductRKeys.nrOfCpus := 1.0

ConductRKeys.memory := 10000000

ConductRKeys.diskSpace := 5000000

ConductRKeys.roles := Set("web-server")
```

Unless the ConductR is running at http://127.0.0.1:9005, and instead supposing that it is running at
http://192.168.59.103:9005 then you will typically issue the following command from the sbt console:

```
conductr http://192.168.59.103:9005
```

This then sets the sbt session up to subsequently communicate with the ConductR at 192.168.59.103 on
port 9005.

The following `sbt-typesafe-conductr` commands are available:

Property     | Description
-------------|------------
conductr     | Sets the ConductR's address to a provided url (the default here is http://127.0.0.1:9005)
loadBundle   | Loads a bundle and an optional configuration to the ConductR
startBundle  | Starts a bundle given a bundle id with an optional absolute scale value
stopBundle   | Stops all executions of a bundle given a bundle id
unloadBundle | Unloads a bundle entirely (requires that the bundle has stopped executing everywhere)

&copy; Typesafe Inc., 2014
