# sbt-conductr #

[![Build Status](https://api.travis-ci.org/sbt/sbt-conductr.png?branch=master)](https://travis-ci.org/sbt/sbt-conductr)

sbt-conductr is an sbt plugin provides commands that communicate with ConductR from within an sbt session. It can
also be used in conjunction with [sbt-bundle](https://github.com/sbt/sbt-bundle#conductr-bundle-plugin)
 to deploy your application or service to ConductR without leaving the comforts of sbt.

## Usage

sbt-conductr is enabled by simply declaring its dependency:

```scala
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
addSbtPlugin("com.typesafe.conductr" % "sbt-conductr" % "1.4.0")
```

The plugin has no requirements for other plugins to be enabled. 

The IP address of the ConductR host is automatically retrieved and used if:
- ConductR is running on the same host inside a docker container. The ConductR address is set to `http://{docker-host-ip}:9005`.
- ConductR is running on the same host. The ConductR address is set to `http://{hostname}:9005`.

In other scenarios it is necessary yo set the address to the ConductR control server manually, e.g. if ConductR is running on `192.168.59.103:9005` you will issue the following command from the sbt console:

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
conduct help           | Get usage information of the conduct command
conduct info           | Gain information on the cluster
conduct load           | Loads a bundle and an optional configuration to the ConductR
conduct run            | Runs a bundle given a bundle id with an optional absolute scale value specified with --scale
conduct stop           | Stops all executions of a bundle given a bundle id
conduct unload         | Unloads a bundle entirely (requires that the bundle has stopped executing everywhere)
conduct logs           | Retrieves log messages of a given bundle
conduct events         | Retrieves events of a given bundle


&copy; Typesafe Inc., 2014-2015
