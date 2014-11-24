# sbt-reactive-runtime #

sbt-reactive-runtime is an sbt plugin designed to facilitate the development lifecycle at the stage where deployment
to Reactive Runtime is considered.

## Usage

With the Conductor running you can upload a file using sbt:

```bash
cd sbt-reactive-runtime-tester/
sbt
```

The above puts you into the console of a test project. The test project is configured to use the sbt-native-packager
to produce RR bundles, and declares some scheduling configuration. To learn more check out the build.sbt file of this
same directory.

Produce a bundle by typing:

```bash
rr:dist
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

#### To use sbt-reactive-runtime in your own project

Add the `sbt-reactive-runtime` plugin:

```scala
addSbtPlugin("com.typesafe.reactiveruntime" % "sbt-reactive-runtime" % "0.6.0")
```

If you will be creating bundles then you may override the following native packager properties:

Property     | Description
-------------|------------
bundleConf   | The bundle configuration file contents.
bundleType   | The type of configuration that this bundling relates to. By default Universal is used.
endpoints    | Provides a port mapping between an external facing endpoint and an internal one. The default is Map("web" -> ("http://0.0.0.0:9000" -> "http://0.0.0.0:9000"))
startCommand | Command line args required to start the component. Paths are expressed relative to the component's bin folder. The default is to use the bash script in the bin folder.

The following `sbt-reactive-runtime` properties will be required if you will be loading bundles:

Property       | Description
---------------|------------
nrOfCpus       | The number of cpus required to run the bundle.
memory         | The amount of memory required to run the bundle.
diskSpace      | The amount of disk space required to host an expanded bundle and configuration.
roles          | The types of node in the cluster that this bundle can be deployed to.

An sample section from a build.sbt then setting the above given that loading bundles will be required:

```scala
ReactiveRuntimeKeys.nrOfCpus := 1.0

ReactiveRuntimeKeys.memory := 10000000

ReactiveRuntimeKeys.diskSpace := 5000000

ReactiveRuntimeKeys.roles := Set("web-server")
```

The following `sbt-reactive-runtime` commands are available:

Property     | Description
-------------|------------
conductor    | Sets the conductor's address to a provided url (the default here is http://127.0.0.1:9005)
loadBundle   | Loads a bundle and an optional configuration to the Conductor
startBundle  | Starts a bundle given a bundle id with an optional absolute scale value
stopBundle   | Stops all executions of a bundle given a bundle id
unloadBundle | Unloads a bundle entirely (requires that the bundle has stopped executing everywhere)

&copy; Typesafe Inc., 2014
