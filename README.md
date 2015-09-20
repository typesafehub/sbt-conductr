# sbt-conductr #

[![Build Status](https://api.travis-ci.org/sbt/sbt-conductr.png?branch=master)](https://travis-ci.org/sbt/sbt-conductr)

sbt-conductr is an sbt plugin provides commands that communicate with ConductR from within an sbt session. It can
also be used in conjunction with [sbt-bundle](https://github.com/sbt/sbt-bundle#conductr-bundle-plugin)
 to deploy your application or service to ConductR without leaving the comforts of sbt.

## Usage

sbt-conductr is enabled by simply declaring its dependency:

```scala
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
addSbtPlugin("com.typesafe.conductr" % "sbt-conductr" % "1.1.0")
```

The plugin has no requirements for other plugins to be enabled. 

Unless the ConductR is running at `127.0.0.1:9005`, and instead supposing that it is running at
`192.168.59.103:9005` then you will typically issue the following command from the sbt console:

```
controlServer 192.168.59.103:9005
```

This then sets the sbt session up to subsequently communicate with the ConductR at 192.168.59.103 on port 9005. 
You may type commands such as:

```
conduct info
```

...which will obtain information on the ConductR cluster.

If you have [sbt-bundle](https://github.com/sbt/sbt-bundle#conductr-bundle-plugin) enabled e.g.:

```scala
lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)
```

You can then produce a bundle by typing:

```bash
bundle:dist
```

...and then load the bundle by typing:

```bash
conduct load <HIT THE TAB KEY AND THEN RETURN>
```

Using the tab completion feature of sbt will produce a URI representing the location of the last distribution
produced by the native packager.

Hitting return will cause the bundle to be uploaded. On successfully uploading the bundle the plugin will report
the `BundleId` to use for subsequent commands on that bundle.

#### Configuration

When loading a bundle you can also load configuration if there is any e.g.:

```bash
configuration:dist
```

...and then hit the tab key after specifying the bundle for `conduct load`.

### Retrieve logs and events

The log messages and events of a particular bundle can be displayed with the commands:

```bash
conduct logs my-bundle
conduct events my-bundle
```

Make sure that your logging infrastructure is up an running. Otherwise the command will timeout: 

```
[trace] Stack trace suppressed: run last conductr-service-lookup/*:conduct for the full output.
[error] (conductr-service-lookup/*:conduct) java.util.concurrent.TimeoutException: Futures timed out after [5 seconds]
[error] Total time: 5 s, completed Sep 28, 2015 11:40:47 AM
```

With [sbt-conductr-sandbox](https://github.com/typesafehub/sbt-conductr-sandbox) you can start the default logging infrastructure during ConductR cluster startup easily:

```bash
sandbox run --withFeatures logging
conduct logs my-bundle
```

Give the logging infrastructure enough time to start before entering the `logs` or `events` command.

### Settings

The following `sbt-conductr` commands are available:

Property               | Description
-----------------------|------------
conduct info           | Gain infomation on the cluster
conduct load           | Loads a bundle and an optional configuration to the ConductR
conduct run            | Runs a bundle given a bundle id with an optional absolute scale value specified with --scale
conduct stop           | Stops all executions of a bundle given a bundle id
conduct unload         | Unloads a bundle entirely (requires that the bundle has stopped executing everywhere)
conduct logs           | Retrieves log messages of a given bundle
conduct events         | Retrieves events of a given bundle

In addition the following settings are available:

Property           | Description
-------------------|------------
conductrApiVersion | The api version to use when communicating with ConductR. Defaults to 1.0 which is required by ConductR 1.0.


&copy; Typesafe Inc., 2014-2015
