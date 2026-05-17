package cncf.launcher

import java.nio.file.Files

/*
 * @since   May. 17, 2026
 * @version May. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfLauncher(
  paths: LauncherPaths = LauncherPaths(),
  runtimeresolver: CncfRuntimeResolver = CoursierCncfRuntimeResolver(),
  cncfinvoker: CncfInvoker = CncfInvoker()
) {
  def run(args: Vector[String]): Int = {
    val config = LauncherConfig.load(paths)
    val command = CncfCommandParser.parse(args)
    command match {
      case CncfCommand.Help =>
        println(CncfCommandParser.helpText)
        0
      case runtime: CncfCommand.Runtime =>
        _run_runtime(runtime, config)
      case dev: CncfCommand.Dev =>
        _run_dev(dev)
    }
  }

  private def _run_runtime(
    command: CncfCommand.Runtime,
    config: LauncherConfig
  ): Int = {
    val store = RuntimeVersionStore(paths)
    val catalogstore = RuntimeCatalogStore(paths)
    command match {
      case CncfCommand.Runtime.Current =>
        println(runtimeresolver.resolveVersion(store.current(None, config), config, paths))
        0
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
        println(s"runtime cache: ${paths.runtimeRoot}")
        println(s"coursier cache: ${paths.coursierCache}")
        0
      case CncfCommand.Runtime.ConfigShow() =>
        println(LauncherConfig.render(config))
        0
    }
  }

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
    command: CncfCommand.Dev
  ): Int = {
    val effectivepaths = _dev_paths(command.options)
    val config = LauncherConfig.load(effectivepaths)
    val store = RuntimeVersionStore(effectivepaths)
    val catalog = RuntimeCatalogStore(effectivepaths).loadOrRefresh(config)
    val effectiveconfig = catalog.map(config.withCatalog).getOrElse(config)
    val devsupport = DevSupport(effectivepaths)
    val effectiveoptions = command.options.copy(project = None)
    val context = devsupport.context(effectiveoptions, effectiveconfig, store)
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
        _invoke_dev(effectivepaths, context, effectiveconfig, devsupport, "server", Vector.empty)
      case CncfCommand.Dev.ServerEmulation(_, args) =>
        _invoke_dev(effectivepaths, context, effectiveconfig, devsupport, "server-emulator", args)
      case CncfCommand.Dev.Client(_, args) =>
        _invoke_dev(effectivepaths, context, effectiveconfig, devsupport, "client", args)
      case CncfCommand.Dev.Command(_, operation, args) =>
        _invoke_dev(effectivepaths, context, effectiveconfig, devsupport, "command", operation +: args)
    }
  }

  private def _dev_paths(
    options: CncfCommand.DevOptions
  ): LauncherPaths =
    options.project
      .map(p => paths.withCwd(paths.cwd.resolve(p).normalize))
      .getOrElse(paths)

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
    val cncfargs = devsupport.cncfArgs(context, mode, args)
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
}

object CncfLauncher {
  def apply(): CncfLauncher =
    new CncfLauncher()
}
