# sbt-conductr #

[![Build Status](https://api.travis-ci.org/typesafehub/sbt-conductr.png?branch=master)](https://travis-ci.org/typesafehub/sbt-conductr)

sbt-conductr is a sbt plugin that provides commands in sbt to:
 
* Produce a ConductR bundle
* Start and stop a local ConductR cluster
* Manage a ConductR cluster within a sbt session

## Prerequisite

* [Docker](https://www.docker.com/)
* [conductr-cli](http://conductr.lightbend.com/docs/1.1.x/CLI)

Docker is required to run the ConductR cluster as if it were running on a number of machines in your network. You won't need to understand much about Docker for ConductR other than installing it as described in its "Get Started" section. If you are on Windows or Mac then you will become familiar with `docker-machine` which is a utility that controls a virtual machine for the purposes of running Docker.

The conductr-cli is used to mange the ConductR cluster.

## Usage

sbt-conductr is enabled by simply declaring its dependency:

```scala
addSbtPlugin("com.lightbend.conductr" % "sbt-conductr" % "2.0.1")
```

This plugin is triggered for each project that enables a native packager plugin in the `build.sbt`, e.g.:

```scala
lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)
```

Note that Lagom or Play user can enable one of the following plugins instead:

| Project            | Description                                                                         |
|--------------------|-------------------------------------------------------------------------------------|
| Lagom Java         | `lazy val myService = (project in file(".")).enablePlugins(LagomJava)`              |
| Play Java in Lagom | `lazy val myService = (project in file(".")).enablePlugins(LagomPlay)`              |
| Play Scala 2.4+    | `lazy val root = (project in file(".")).enablePlugins(PlayScala)`                   |
| Play Scala 2.3     | `lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging, PlayScala)` |
| Play Java 2.4+     | `lazy val root = (project in file(".")).enablePlugins(PlayJava)`                    |
| Play Java 2.3      | `lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging, PlayJava)`  |

Now you can produce a bundle for your project:

```console
bundle:dist
```

To manage the ConductR cluster use the `sandbox` or `conduct` command. To start a three-node ConductR cluster on your local machine use:

```console
sandbox run <CONDUCTR_VERSION> --nr-of-containers 3
```

To check the status of your bundles use:

```console
conduct info
```

This will obtain information on the ConductR cluster.

The IP address of the ConductR host is automatically retrieved and used if:
- ConductR is running on the same host inside a docker container. The ConductR address is set to `http://{docker-host-ip}:9005`.
- ConductR is running on the same host. The ConductR address is set to `http://{hostname}:9005`.

In other scenarios it is necessary to set the address to the ConductR server manually. You can do this by using the `--ip` and `--port` options of the `conduct command, e.g.

```console
conduct info --ip 192.168.59.103 --port 9999
```

Produce a bundle by typing:

```console
bundle:dist
```

...and then load the bundle by typing:

```console
conduct load <HIT THE TAB KEY AND THEN RETURN>
```

Using the tab completion feature of sbt will produce a URI representing the location of the last distribution
produced by the native packager.

Hitting return will cause the bundle to be uploaded. On successfully uploading the bundle the plugin will report
the `BundleId` to use for subsequent commands on that bundle.

### Configuration

It is possible to produce additional configuration bundles that contain an optional `bundle.conf` the value of which override the main bundle, as
well as arbitrary shell scripts. These additional configuration files must be placed in your project's src/bundle-configuration/default folder.

The bundle-configuration folder may contain many configurations in order to support development style scenarios, the desired configuration can be specified with the setting ("default" is the default folder name):

```
BundleKeys.configurationName := "default"
```

...in the `build.sbt`.

Then, to produce this additional bundle execute:

```
configuration:dist
```

> Note that bundle configuration that is generally performed from within sbt is therefore part of the project to support developer use-cases. Operational use-cases where sensitive data is held in configuration is intended to be performed outside of sbt, and in conjunction with the [ConductR CLI](https://github.com/typesafehub/conductr-cli#command-line-interface-cli-for-typesafe-conductr) (specifically the `shazar` command).

The `conduct load` command will pick up the latest configuration with the tab key. Simply hit the tab key after specifying the bundle:

```console
conduct load /my-project/target/bundle/my-bundle <HIT THE TAB KEY TO USE THE LATEST CONFIGURATION>
```

### Advanced bundles and configuration

sbt-conductr is capable of producing many bundles and bundle configurations for a given sbt module.

#### Adding Java options

Suppose you need to add Java options to your start command. You'll need to do this, say, to get a Play application binding to the correct IP address and port (supposing that the endpoint is named "web"):

```scala
javaOptions in Bundle ++= Seq("-Dhttp.address=$WEB_BIND_IP", "-Dhttp.port=$WEB_BIND_PORT")
```

#### Renaming an executable

Sometimes you need to invoke something other than the script that the native packager assumes. For example, if you have a script in the bin folder named `start.sh`, and it isn't expecting any Java options:

```scala
BundleKeys.executableScriptPath in Bundle := (file((normalizedName in Bundle).value) / "bin" / "start.sh").getPath
javaOptions in Bundle := Seq.empty
```

#### Extending bundles

Suppose that you have an sbt module where there are multiple ways in which it can be produced. 
[ReactiveMaps](https://github.com/typesafehub/ReactiveMaps) is one such example where the one application can be 
deployed in three ways:

* frontend
* backend-region
* backend-summary

Its frontend configuration is expressed in the regular way i.e. within the global scope:

```scala

// Main bundle configuration

normalizedName := "reactive-maps-frontend"
BundleKeys.nrOfCpus := 2.0
...
```

Thus a regular `bundle:dist` will produce the frontend bundle. 

We can then extend the bundle configuration and overlay some new values for a different target. Here's a sample
of what the backend-region target looks like:

```scala

lazy val BackendRegion = config("backend-region").extend(Bundle)
BundlePlugin.bundleSettings(BackendRegion)
inConfig(BackendRegion)(Seq(
  normalizedName := "reactive-maps-backend-region",
  BundleKeys.configurationName := (normalizedName in BackendRegion).value,
  ...
))
```

A new configuration is created that extends the regular `Bundle` one for the purposes of delegating sbt settings.
Therefore anything declared within the `inConfig` function will have precedence over that which is declared in the
`Bundle` sbt configuration. The `bundleSettings` function defines a few important settings that you need.

To produce the above bundle then becomes a matter of just `backend-region:dist`.

#### Extending bundle configurations

The optional `bundle.conf` file can either be provided directly, or be generated via sbt settings. The following shows
how to create an sbt configuration and then define `bundle.conf` settings. The settings are for a fictitious `backend`
configuration that overrides the bundle name and the roles:

```scala
lazy val Backend = config("backend").extend(BundleConfiguration)
BundlePlugin.configurationSettings(Backend)
inConfig(Backend)(Seq(
  normalizedName := "reactive-maps-backend",
  roles := Set("big-backend-server")
))
```

Note the distinction between the `configurationSettings` and `bundleSettings` for bundle configurations and bundles 
respectively.

You must also associate the configuration with your project:

```
lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .configs(Backend)
```

A configuration for the above can then be generated:

```
backend:dist
```

### Retrieve logs and events

The log messages and events of a particular bundle can be displayed with the commands:

```console
conduct logs my-bundle
conduct events my-bundle
```

Make sure that your logging infrastructure is up an running. Otherwise the command will timeout: 

```console
[trace] Stack trace suppressed: run last conductr-service-lookup/*:conduct for the full output.
[error] (conductr-service-lookup/*:conduct) java.util.concurrent.TimeoutException: Futures timed out after [5 seconds]
[error] Total time: 5 s, completed Sep 28, 2015 11:40:47 AM
```

With the `sandbox` command you can start the default logging infrastructure during ConductR cluster startup easily:

```console
sandbox run --feature logging
conduct logs my-bundle
```

Give the logging infrastructure enough time to start before entering the `logs` or `events` command.

### Bundle settings

The following bundle settings are provided under the `BundleKeys` object:

Name                  | Description
----------------------|-------------
bundleConf            | The bundle configuration file contents
bundleConfVersion     | The version of the bundle.conf file. By default this is 1.
bundleType            | The type of configuration that this bundling relates to. By default Universal is used.
checkInitialDelay     | Initial delay before the check uris are triggered. The `FiniteDuration` value gets rounded up to full seconds. Default is 3 seconds.
checks                | Declares uris to check to signal to ConductR that the bundle components have started for situations where component doesn't do that. For example `Seq(uri("$WEB_HOST"))` will check that a endpoint named "web" will be checked given its host environment var. Once that URL becomes available then ConductR will be signalled that the bundle is ready. Note that a `docker+` prefix should be used when waiting on Docker components so that the Docker build event is waited on e.g. `Seq(uri("docker+$WEB_HOST"))`<br/>Optional params are: 'retry-count': Number of retries, 'retry-delay': Delay in seconds between retries, 'docker-timeout': Timeout in seconds for docker container start. For example: `Seq(uri("$WEB_HOST?retry-count=5&retry-delay=2"))`.
compatibilityVersion  | A versioning scheme that will be included in a bundle's name that describes the level of compatibility with bundles that go before it. By default we take the major version component of a version as defined by [http://semver.org/]. However you can make this mean anything that you need it to mean in relation to bundles produced prior to it. We take the notion of a compatibility version from [http://ometer.com/parallel.html]."
conductrTargetVersion | The version of ConductR to that this bundle can be deployed on. During bundle creation a compatibility check is made whether this bundle can be deployed on the specified ConductR version. Defaults to 1.1.
configurationName     | The name of the directory of the additional configuration to use. Defaults to 'default'
diskSpace             | The amount of disk space required to host an expanded bundle and configuration. Append the letter k or K to indicate kilobytes, or m or M to indicate megabytes. Required.
enableAcls            | Acls can be declared on an endpoint if this setting is 'true'. Otherwise only service endpoints can be declared. Endpoint acls can be used from ConductR 1.2 onwards. Therefore, the default in ConductR 1.1- is 'false' and in ConductR 1.2+ 'true'.
endpoints             | Declares endpoints. The default is Map("web" -> Endpoint("http", 0, Set.empty)). The endpoint key is used to form a set of environment variables for your components, e.g. for the endpoint key "web" ConductR creates the environment variable `WEB_BIND_PORT`.
executableScriptPath  | The relative path of the executableScript within the bundle.
memory                | The amount of memory required to run the bundle.
nrOfCpus              | The number of cpus required to run the bundle (can be fractions thereby expressing a portion of CPU). Required.
overrideEndpoints     | Overrides the endpoints settings key with new endpoints. This task should be used if the endpoints need to be specified programmatically. The default is None.
roles                 | The types of node in the cluster that this bundle can be deployed to. Defaults to "web".
startCommand          | Command line args required to start the component. Paths are expressed relative to the component's bin folder. The default is to use the bash script in the bin folder. <br/> Example JVM component: </br> `BundleKeys.startCommand += "-Dhttp.address=$WEB_BIND_IP -Dhttp.port=$WEB_BIND_PORT"` </br> Example Docker component (should additional args be required): </br> `BundleKeys.startCommand += "dockerArgs -v /var/lib/postgresql/data:/var/lib/postgresql/data"` (this adds arguments to `docker run`). Note that memory heap is controlled by the memory BundleKey and heap flags should not be passed here.
system                | A logical name that can be used to associate multiple bundles with each other. This could be an application or service association and should include a version e.g. myapp-1.0.0. Defaults to the package name.
systemVersion         | A version to associate with a system. This setting defaults to the value of compatibilityVersion.

### Commands

The following `sbt-conductr` commands are available:

Property               | Description
-----------------------|------------
bundle:dist            | Produce a ConductR bundle for all projects that have the native packager enabled
configuration:dist     | Produce a bundle configuration for all projects that have the native packager enabled
sandbox help           | Get usage information of the sandbox command
sandbox run            | Start a local ConductR cluster
sandbox stop           | Stop the local ConductR cluster
conduct help           | Get usage information of the conduct command
conduct info           | Gain information on the cluster
conduct load           | Loads a bundle and an optional configuration to the ConductR
conduct run            | Runs a bundle given a bundle id with an optional absolute scale value specified with --scale
conduct stop           | Stops all executions of a bundle given a bundle id
conduct unload         | Unloads a bundle entirely (requires that the bundle has stopped executing everywhere)
conduct logs           | Retrieves log messages of a given bundle
conduct events         | Retrieves events of a given bundle


&copy; Lightbend Inc., 2014-2016
