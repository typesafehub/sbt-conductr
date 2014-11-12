# sbt-reactive-runtime #

sbt-reactive-runtime is an sbt plugin designed to faciliate the development lifecycle at the stage where deployment
to [Reactive Runtime](https://github.com/typesafehub/reactive-runtime#reactive-runtime) is considered.

## Usage

With the conductor running you can upload a file using sbt:

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
addSbtPlugin("com.typesafe.reactiveruntime" % "sbt-reactive-runtime" % "0.1.3")
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
cpusRequired   | The number of cpus required to start the bundle.
memoryRequired | The amount of memory required to run the bundle.
totalFileSize  | The amount of disk space required to host an expanded bundle and configuration.
roles          | The types of node in the cluster that this bundle can be deployed to.

An sample section from a build.sbt then setting the above given that loading bundles will be required:

```scala
ReactiveRuntimeKeys.cpusRequired := Some(1.0)

ReactiveRuntimeKeys.memoryRequired := Some(10000000)

ReactiveRuntimeKeys.totalFileSize := Some(5000000)

ReactiveRuntimeKeys.roles := Set("web-server")
```

Additional `sbt-reactive-runtime` properties are available:

Property         | Description
-----------------|------------
conductorAddress | The location of the conductor. Defaults to 'http://127.0.0.1:9005'.

The following `sbt-rr` commands are available:

Property    | Description
------------|------------
loadBundle  | Loads a bundle and an optional configuration to the conductor
startBundle | Starts a bundle given a bundle id with an optional scale

&copy; Typesafe Inc., 2014
