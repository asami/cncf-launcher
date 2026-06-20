package cncf.launcher

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipFile
import scala.util.Try

/*
 * @since   May. 17, 2026
 *  version May. 27, 2026
 * @version Jun. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfLauncher(
  paths: LauncherPaths = LauncherPaths(),
  runtimeresolver: CncfRuntimeResolver = CoursierCncfRuntimeResolver(),
  cncfinvoker: CncfInvoker = CncfInvoker(),
  classpathexporter: RuntimeClasspathExporter = SbtRuntimeClasspathExporter,
  processmanager: DevServerProcessManager = DevServerProcessManager.System,
  launcherdevinvoker: LauncherDevInvoker = LauncherDevInvoker.System
) {
  def run(args: Vector[String]): Int = {
    val (configfiles, cncfconfigfiles, commandargs) = _take_config_options(args)
    val config = LauncherConfig.load(paths, configfiles)
      .mergeHigher(LauncherConfig(cncfConfigFiles = cncfconfigfiles))
    _delegate_launcher_dev_dir(config, args) match {
      case Some(code) => return code
      case None => ()
    }
    val command = CncfCommandParser.parse(commandargs)
    command match {
      case CncfCommand.LauncherVersion =>
        println(s"${LauncherBuildInfo.name} ${LauncherBuildInfo.version}")
        0
      case CncfCommand.Help =>
        println(CncfCommandParser.helpText)
        0
      case runtime: CncfCommand.Runtime =>
        _run_runtime(runtime, config)
      case dev: CncfCommand.Dev =>
        _run_dev(dev, configfiles, cncfconfigfiles)
    }
  }

  private def _delegate_launcher_dev_dir(
    config: LauncherConfig,
    args: Vector[String]
  ): Option[Int] =
    if (sys.env.get("CNCF_LAUNCHER_DEV_DELEGATED").contains("1"))
      None
    else
      config.launcherDevDir.map { dir =>
        val path = paths.cwd.resolve(dir).normalize.toAbsolutePath.normalize
        launcherdevinvoker.invoke(path, args, paths.cwd.toAbsolutePath.normalize)
      }

  private def _run_runtime(
    command: CncfCommand.Runtime,
    config: LauncherConfig
  ): Int = {
    val store = RuntimeVersionStore(paths)
    val catalogstore = RuntimeCatalogStore(paths)
    command match {
      case CncfCommand.Runtime.Current =>
        _run_runtime_current(store, catalogstore, config)
      case CncfCommand.Runtime.LocalList =>
        val installed =
          if (Files.isDirectory(paths.runtimeRoot)) {
            val stream = Files.list(paths.runtimeRoot)
            try {
              import scala.jdk.CollectionConverters.*
              stream.iterator().asScala.filter(Files.isDirectory(_)).map(_.getFileName.toString).toVector.sorted
            } finally {
              stream.close()
            }
          } else {
            Vector.empty
          }
        installed.foreach(println)
        0
      case CncfCommand.Runtime.RemoteList =>
        val catalog = catalogstore.loadOrRefresh(config)
          .getOrElse(throw CncfException("failed to load Cncf runtime catalog"))
        println(catalog.renderRemoteList)
        0
      case CncfCommand.Runtime.Refresh =>
        catalogstore.refresh(config)
        println(s"refreshed Cncf runtime catalog: ${paths.runtimeCatalog}")
        0
      case CncfCommand.Runtime.CatalogShow =>
        val catalog = catalogstore.loadOrRefresh(config)
          .getOrElse(throw CncfException("failed to load Cncf runtime catalog"))
        println(catalog.render)
        0
      case CncfCommand.Runtime.Descriptor(format) =>
        _validate_yaml_format(format)
        println(_runtime_descriptor(store, config, catalogstore.loadOrRefresh(config)))
        0
      case CncfCommand.Runtime.BaseProvided(format) =>
        _validate_yaml_format(format)
        println(RuntimeCatalog.parse(_runtime_descriptor(store, config, catalogstore.loadOrRefresh(config))).renderBaseProvided)
        0
      case CncfCommand.Runtime.Channels =>
        val catalog = catalogstore.loadOrRefresh(config)
          .getOrElse(throw CncfException("failed to load Cncf runtime catalog"))
        println(catalog.renderChannels)
        0
      case CncfCommand.Runtime.Install(version) =>
        val concreteversion = runtimeresolver.resolveVersion(version, config, paths)
        runtimeresolver.resolve(concreteversion, config, paths)
        println(s"installed CNCF runtime $concreteversion")
        0
      case CncfCommand.Runtime.Use(version, target) =>
        val concreteversion = runtimeresolver.resolveVersion(version, config, paths)
        val resolvedtarget = _resolve_runtime_use_target(target)
        resolvedtarget match {
          case CncfCommand.RuntimeUseTarget.Global => store.useGlobal(version)
          case CncfCommand.RuntimeUseTarget.Project => store.useProject(version)
          case CncfCommand.RuntimeUseTarget.Auto => throw CncfException("unresolved runtime use target")
        }
        println(s"using CNCF runtime $version -> $concreteversion (${resolvedtarget.toString.toLowerCase})")
        0
      case CncfCommand.Runtime.CacheStatus() =>
        println(s"cncf home: ${paths.cncfHome}")
        println(s"local repository: ${paths.localRepository}")
        println(s"artifact cache: ${paths.cacheRepository}")
        println(s"runtime cache: ${paths.runtimeRoot}")
        println(s"coursier cache: ${paths.coursierCache}")
        0
      case CncfCommand.Runtime.ConfigShow() =>
        println(LauncherConfig.render(config))
        0
    }
  }

  private def _run_runtime_current(
    store: RuntimeVersionStore,
    catalogstore: RuntimeCatalogStore,
    config: LauncherConfig
  ): Int = {
    val selector = store.current(None, config)
    val current = runtimeresolver.resolveVersion(selector, config, paths)
    println(current)
    _warn_if_runtime_catalog_is_stale(selector, current, catalogstore, config)
    0
  }

  private def _warn_if_runtime_catalog_is_stale(
    selector: String,
    current: String,
    catalogstore: RuntimeCatalogStore,
    config: LauncherConfig
  ): Unit =
    if (_is_dynamic_runtime_selector(selector) && !_is_dynamic_runtime_selector(current)) {
      val remoteversion =
        try Some(catalogstore.fetch(config).resolve(selector).version)
        catch {
          case _: Throwable => None
        }
      remoteversion.filter(_ != current).foreach { version =>
        Console.err.println(
          s"warning: cached CNCF runtime catalog resolves $selector to $current, but remote catalog resolves it to $version."
        )
        Console.err.println("Run 'cncf runtime refresh' to update the local runtime catalog cache.")
      }
    }

  private def _is_dynamic_runtime_selector(selector: String): Boolean =
    selector match {
      case "recommended" | "latest" | "latest-stable" | "latest.release" | "latest-snapshot" | "newest" => true
      case _ => false
    }

  private def _current_runtime_catalog_version(
    store: RuntimeVersionStore,
    config: LauncherConfig,
    catalog: RuntimeCatalog
  ): RuntimeCatalogVersion =
    catalog.resolve(store.current(None, config))

  private def _validate_yaml_format(format: String): Unit =
    if (format != "yaml")
      throw CncfException(s"unsupported runtime descriptor format: $format")

  private def _runtime_descriptor(
    store: RuntimeVersionStore,
    config: LauncherConfig,
    catalog: Option[RuntimeCatalog]
  ): String = {
    val selector = store.current(None, config)
    val version = catalog.map(_.resolve(selector)).getOrElse(_runtime_catalog_version_without_catalog(selector, config))
    val effectiveconfig = catalog.map(config.withCatalog).getOrElse(config)
    val descriptor =
      Try(runtimeresolver.resolve(version.version, effectiveconfig, paths)).toOption.
        flatMap(_runtime_descriptor_from_classpath)
    descriptor.getOrElse {
      catalog.map(_.renderRuntimeDescriptor(version)).
        getOrElse(RuntimeCatalog.empty.renderRuntimeDescriptor(version))
    }
  }

  private def _runtime_catalog_version_without_catalog(
    selector: String,
    config: LauncherConfig
  ): RuntimeCatalogVersion =
    RuntimeCatalogVersion(
      version = runtimeresolver.resolveVersion(selector, config, paths),
      channel = None,
      status = Some("active"),
      scalaBinaryVersion = Some("3"),
      module = None,
      publishedAt = None,
      checksumUrl = None,
      metadataUrl = None
    )

  private def _runtime_descriptor_from_classpath(
    classpath: Vector[java.nio.file.Path]
  ): Option[String] =
    classpath.iterator.flatMap(_runtime_descriptor_from_jar).nextOption()

  private def _runtime_descriptor_from_jar(path: java.nio.file.Path): Option[String] =
    if (Files.isRegularFile(path) && path.getFileName.toString.endsWith(".jar"))
      Try {
        val zip = new ZipFile(path.toFile)
        try {
          Option(zip.getEntry("META-INF/cncf/runtime.yaml")).map { entry =>
            val in = zip.getInputStream(entry)
            try new String(in.readAllBytes(), StandardCharsets.UTF_8)
            finally in.close()
          }
        } finally {
          zip.close()
        }
      }.toOption.flatten
    else
      None

  private def _resolve_runtime_use_target(
    target: CncfCommand.RuntimeUseTarget
  ): CncfCommand.RuntimeUseTarget =
    target match {
      case CncfCommand.RuntimeUseTarget.Auto =>
        if (Files.exists(paths.cwd.resolve(".cncf")))
          CncfCommand.RuntimeUseTarget.Project
        else
          CncfCommand.RuntimeUseTarget.Global
      case x => x
    }

  private def _run_dev(
    command: CncfCommand.Dev,
    configfiles: Vector[String],
    cncfconfigfiles: Vector[String]
  ): Int = {
    val explicitconfig = LauncherConfig(cncfConfigFiles = cncfconfigfiles)
    val initialconfig = LauncherConfig.load(paths, configfiles).mergeHigher(explicitconfig)
    val initialoptions = command.options.copy(target = _config_dev_target(command.options.target, initialconfig))
    val effectivepaths = _dev_paths(initialoptions)
    val config =
      if (effectivepaths.cwd == paths.cwd)
        initialconfig
      else
        LauncherConfig.load(effectivepaths, configfiles).mergeHigher(explicitconfig)
    val store = RuntimeVersionStore(effectivepaths)
    val basecatalog = RuntimeCatalogStore(effectivepaths).loadOrRefresh(config)
    val baseconfig = basecatalog.map(config.withCatalog).getOrElse(config)
    val devsupport = new DevSupport(effectivepaths, classpathexporter, processmanager)
    val effectiveoptions = command.options.copy(
      target = _normalize_dev_target(_config_dev_target(command.options.target, baseconfig)),
      stopExisting = command.options.stopExisting || baseconfig.devRestart.getOrElse(false),
      forceExisting = command.options.forceExisting || baseconfig.devForceExisting.getOrElse(false)
    )
    val rawcontext = devsupport.context(effectiveoptions, baseconfig, store)
    val catalog =
      rawcontext.runtimeDevDir.flatMap(RuntimeCatalogStore.loadRuntimeDevelopmentCatalog)
        .orElse(basecatalog)
    val effectiveconfig = catalog.map(config.withCatalog).getOrElse(config)
    val selectionpolicy = effectiveoptions.runtimeSelectionPolicy.
      orElse(effectiveconfig.runtimeSelectionPolicy).
      getOrElse(RuntimeSelectionPolicy.CurrentCompatible)
    val policy = effectiveoptions.runtimeNoCompatiblePolicy.orElse(effectiveconfig.runtimeNoCompatiblePolicy).getOrElse(RuntimeNoCompatiblePolicy.Error)
    val runtimeversion = RuntimeVersionSelection.select(
      requested = effectiveoptions.runtimeVersion,
      stored = store.current(None, effectiveconfig),
      requirements = rawcontext.runtimeRequirements,
      catalog = catalog,
      selectionPolicy = selectionpolicy,
      policy = policy
    )
    val context = rawcontext.copy(runtimeVersion = runtimeversion)
    command match {
      case CncfCommand.Dev.Classpath(_) =>
        val file = devsupport.writeRuntimeClasspath(context)
        println(s"Wrote ${file}")
        0
      case CncfCommand.Dev.Check(_) =>
        val items = devsupport.check(context)
        items.foreach(item => println(item.render))
        if (items.exists(_.isError)) 2 else 0
      case CncfCommand.Dev.Server(_) =>
        val state = devsupport.prepareDevServerStart(context, effectiveoptions)
        try {
          _invoke_dev(effectivepaths, context, effectiveconfig, devsupport, "server", Vector.empty)
        } finally {
          devsupport.cleanupDevServerState(state)
        }
      case CncfCommand.Dev.Stop(_) =>
        devsupport.stopDevServer(context, effectiveoptions.forceExisting, effectiveoptions.port.isDefined)
      case CncfCommand.Dev.ServerEmulation(_, args) =>
        _invoke_dev(effectivepaths, context, effectiveconfig, devsupport, "server-emulator", args)
      case CncfCommand.Dev.Client(_, args) =>
        _invoke_dev(effectivepaths, context, effectiveconfig, devsupport, "client", args)
      case CncfCommand.Dev.Command(_, operation, args) =>
        _invoke_dev(effectivepaths, context, effectiveconfig, devsupport, "command", operation +: args)
    }
  }

  private def _take_config_options(
    args: Vector[String]
  ): (Vector[String], Vector[String], Vector[String]) = {
    val configfiles = Vector.newBuilder[String]
    val cncfconfigfiles = Vector.newBuilder[String]
    val out = Vector.newBuilder[String]
    var i = 0
    while (i < args.length) {
      args(i) match {
        case "--" =>
          out ++= args.drop(i)
          i = args.length
        case "--config" | "--launcher-config" =>
          if (i + 1 >= args.length)
            throw CncfException(s"${args(i)} requires a value")
          configfiles += _config_file(args(i + 1))
          i += 2
        case x if x.startsWith("--config=") =>
          configfiles += _config_file(x.stripPrefix("--config="))
          i += 1
        case x if x.startsWith("--launcher-config=") =>
          configfiles += _config_file(x.stripPrefix("--launcher-config="))
          i += 1
        case "--cncf-config" =>
          if (i + 1 >= args.length)
            throw CncfException(s"${args(i)} requires a value")
          cncfconfigfiles += _config_file(args(i + 1))
          i += 2
        case x if x.startsWith("--cncf-config=") =>
          cncfconfigfiles += _config_file(x.stripPrefix("--cncf-config="))
          i += 1
        case x =>
          out += x
          i += 1
      }
    }
    (configfiles.result(), cncfconfigfiles.result(), out.result())
  }

  private def _config_file(path: String): String =
    paths.cwd.resolve(path).normalize.toAbsolutePath.normalize.toString

  private def _dev_paths(
    options: CncfCommand.DevOptions
  ): LauncherPaths =
    options.target match {
      case CncfCommand.DevTarget.ProjectDev(Some(path)) =>
        paths.withCwd(paths.cwd.resolve(path).normalize)
      case CncfCommand.DevTarget.ProjectCar(path) =>
        paths.withCwd(paths.cwd.resolve(path).normalize)
      case _ =>
        paths
    }

  private def _config_dev_target(
    target: CncfCommand.DevTarget,
    config: LauncherConfig
  ): CncfCommand.DevTarget =
    target match {
      case CncfCommand.DevTarget.ProjectDev(None) =>
        config.devProjectDev.map(p => CncfCommand.DevTarget.ProjectDev(Some(p))).getOrElse(target)
      case x => x
    }

  private def _normalize_dev_target(
    target: CncfCommand.DevTarget
  ): CncfCommand.DevTarget =
    target match {
      case CncfCommand.DevTarget.ProjectDev(Some(_)) => CncfCommand.DevTarget.ProjectDev(None)
      case CncfCommand.DevTarget.ProjectCar(_) => CncfCommand.DevTarget.ProjectCar(".")
      case x => x
    }

  private def _invoke_dev(
    effectivepaths: LauncherPaths,
    context: DevContext,
    config: LauncherConfig,
    devsupport: DevSupport,
    mode: String,
    args: Vector[String]
  ): Int = {
    val runtimeclasspath = context.runtimeDevDir match {
      case Some(dir) => devsupport.cncfRuntimeClasspath(dir)
      case None => runtimeresolver.resolve(context.runtimeVersion, config, effectivepaths)
    }
    val devclasspath = devsupport.runtimeClasspath(context)
    val cncfargs =
      devsupport.cncfArgs(context.copy(runtimeArgs = context.runtimeArgs ++ _cncf_config_args(config) ++ _textus_knowledge_rdf_args(config)), mode, args)
    _with_dev_system_properties(context) {
      cncfinvoker.invoke(runtimeclasspath ++ devclasspath, cncfargs)
    }
  }

  private def _with_dev_system_properties[A](
    context: DevContext
  )(body: => A): A = {
    val updates = Vector(
      "cncf.server.port" -> context.port,
      "textus.server.port" -> context.port,
      "cncf.http.baseurl" -> s"http://127.0.0.1:${context.port}",
      "textus.http.baseurl" -> s"http://127.0.0.1:${context.port}",
      "user.dir" -> context.project.toString
    )
    val old = updates.map { case (k, _) => k -> sys.props.get(k) }
    try {
      updates.foreach { case (k, v) => sys.props.update(k, v) }
      body
    } finally {
      old.foreach {
        case (k, Some(v)) => sys.props.update(k, v)
        case (k, None) => sys.props.remove(k)
      }
    }
  }

  private def _textus_knowledge_rdf_args(
    config: LauncherConfig
  ): Vector[String] =
    config.textusKnowledgeRdfNodePrefix.toVector.map("--textus.knowledge.rdf.node-prefix=" + _) ++
      config.textusKnowledgeRdfPublicBaseUri.toVector.map("--textus.knowledge.rdf.public-base-uri=" + _) ++
      config.textusKnowledgeRdfNamespacePrefixes.toVector.map("--textus.knowledge.rdf.namespace-prefixes=" + _) ++
      config.textusKnowledgeRdfNamespaces.map { case (prefix, namespaceuri) =>
        s"--textus.knowledge.rdf.namespaces.${prefix}=${namespaceuri}"
      }

  private def _cncf_config_args(
    config: LauncherConfig
  ): Vector[String] =
    if (config.cncfConfigFiles.isEmpty)
      Vector.empty
    else
      Vector(s"--cncf.config.files=${config.cncfConfigFiles.distinct.mkString(",")}")
}

object CncfLauncher {
  def apply(): CncfLauncher =
    new CncfLauncher()
}
