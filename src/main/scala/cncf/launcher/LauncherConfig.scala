package cncf.launcher

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

/*
 * @since   May. 17, 2026
 * @version May. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final case class LauncherConfig(
  runtimeVersion: Option[String] = None,
  runtimeDevDir: Option[String] = None,
  runtimeCatalogUrl: Option[String] = None,
  runtimeSelectionPolicy: Option[RuntimeSelectionPolicy] = None,
  runtimeNoCompatiblePolicy: Option[RuntimeNoCompatiblePolicy] = None,
  devProject: Option[String] = None,
  devPort: Option[String] = None,
  devComponentDevDirs: Vector[String] = Vector.empty,
  carRepositories: Vector[String] = Vector.empty,
  sarRepositories: Vector[String] = Vector.empty,
  mavenRepositories: Vector[String] = Vector.empty,
  coursierRepositories: Vector[String] = Vector.empty
) {
  def mergeHigher(higher: LauncherConfig): LauncherConfig =
    LauncherConfig(
      runtimeVersion = higher.runtimeVersion.orElse(runtimeVersion),
      runtimeDevDir = higher.runtimeDevDir.orElse(runtimeDevDir),
      runtimeCatalogUrl = higher.runtimeCatalogUrl.orElse(runtimeCatalogUrl),
      runtimeSelectionPolicy = higher.runtimeSelectionPolicy.orElse(runtimeSelectionPolicy),
      runtimeNoCompatiblePolicy = higher.runtimeNoCompatiblePolicy.orElse(runtimeNoCompatiblePolicy),
      devProject = higher.devProject.orElse(devProject),
      devPort = higher.devPort.orElse(devPort),
      devComponentDevDirs = _merge_list(devComponentDevDirs, higher.devComponentDevDirs),
      carRepositories = _merge_list(carRepositories, higher.carRepositories),
      sarRepositories = _merge_list(sarRepositories, higher.sarRepositories),
      mavenRepositories = _merge_list(mavenRepositories, higher.mavenRepositories),
      coursierRepositories = _merge_list(coursierRepositories, higher.coursierRepositories)
    )

  def normalizedWithDefaults: LauncherConfig =
    normalizedWithDefaults(LauncherPaths())

  def normalizedWithDefaults(paths: LauncherPaths): LauncherConfig =
    copy(
      carRepositories = _append_defaults(carRepositories, LauncherConfig.localCarRepositories(paths) ++ LauncherConfig.DEFAULT_CAR_REPOSITORIES),
      sarRepositories = _append_defaults(sarRepositories, LauncherConfig.localSarRepositories(paths) ++ LauncherConfig.DEFAULT_SAR_REPOSITORIES),
      mavenRepositories = _append_defaults(mavenRepositories, LauncherConfig.DEFAULT_MAVEN_REPOSITORIES),
      runtimeSelectionPolicy = runtimeSelectionPolicy.orElse(Some(RuntimeSelectionPolicy.CurrentCompatible)),
      runtimeNoCompatiblePolicy = runtimeNoCompatiblePolicy.orElse(Some(RuntimeNoCompatiblePolicy.Error)),
      runtimeCatalogUrl = runtimeCatalogUrl.orElse(Some(LauncherConfig.DEFAULT_RUNTIME_CATALOG_URL))
    )

  def withCatalog(catalog: RuntimeCatalog): LauncherConfig =
    copy(
      carRepositories = _merge_catalog_list(carRepositories, catalog.carRepositories, LauncherConfig.DEFAULT_CAR_REPOSITORIES),
      sarRepositories = _merge_catalog_list(sarRepositories, catalog.sarRepositories, LauncherConfig.DEFAULT_SAR_REPOSITORIES),
      mavenRepositories = _merge_catalog_list(mavenRepositories, catalog.mavenRepositories, LauncherConfig.DEFAULT_MAVEN_REPOSITORIES),
      coursierRepositories = _merge_catalog_list(coursierRepositories, catalog.coursierRepositories, Vector.empty)
    )

  private def _merge_list(
    lower: Vector[String],
    higher: Vector[String]
  ): Vector[String] =
    (higher ++ lower).distinct

  private def _append_defaults(
    configured: Vector[String],
    defaults: Vector[String]
  ): Vector[String] =
    configured ++ defaults.filterNot(configured.contains)

  private def _merge_catalog_list(
    configured: Vector[String],
    catalog: Vector[String],
    defaults: Vector[String]
  ): Vector[String] = {
    val explicit = configured.filterNot(defaults.contains)
    (explicit ++ catalog ++ defaults).distinct
  }
}

object LauncherConfig {
  val DEFAULT_RUNTIME_VERSION = "recommended"
  val DEFAULT_RUNTIME_CATALOG_URL = "https://www.simplemodeling.org/repository/textus/runtime-catalog.yaml"
  val DEFAULT_DEV_PORT = "19532"
  val DEFAULT_CAR_REPOSITORIES = Vector("https://www.simplemodeling.org/repository/car")
  val DEFAULT_SAR_REPOSITORIES = Vector("https://www.simplemodeling.org/repository/sar")
  val DEFAULT_MAVEN_REPOSITORIES = Vector("https://www.simplemodeling.org/repository/maven")

  def load(paths: LauncherPaths): LauncherConfig = {
    val global = loadFile(paths.globalConfig)
    val project = loadFile(paths.projectConfig)
    LauncherConfig()
      .mergeHigher(global)
      .mergeHigher(project)
      .normalizedWithDefaults(paths)
  }

  def loadFile(path: Path): LauncherConfig =
    if (Files.isRegularFile(path)) {
      val text = Files.readString(path, StandardCharsets.UTF_8)
      fromParsed(SimpleYaml.parse(text))
    } else {
      LauncherConfig()
    }

  def fromParsed(values: Map[String, Vector[String]]): LauncherConfig = {
    def _first_(keys: String*): Option[String] =
      keys.toVector.flatMap(k => values.getOrElse(k, Vector.empty)).headOption.map(_.trim).filter(_.nonEmpty)
    def _all_(keys: String*): Vector[String] =
      keys.toVector.flatMap(k => values.getOrElse(k, Vector.empty)).map(_.trim).filter(_.nonEmpty).distinct

    LauncherConfig(
      runtimeVersion = _first_("runtime.version", "cncf.runtime.version", "version"),
      runtimeDevDir = _first_("runtime.devDir", "runtime.dev.dir", "cncf.runtime.devDir", "cncf.runtime.dev.dir"),
      runtimeCatalogUrl = _first_("runtime.catalog.url", "cncf.runtime.catalog.url", "catalog.url"),
      runtimeSelectionPolicy = _first_("runtime.cncf.selectionPolicy", "cncf.runtime.cncf.selectionPolicy").
        map(RuntimeSelectionPolicy.parse),
      runtimeNoCompatiblePolicy = _first_("runtime.cncf.noCompatiblePolicy", "cncf.runtime.cncf.noCompatiblePolicy").
        map(RuntimeNoCompatiblePolicy.parse),
      devProject = _first_("dev.project", "cncf.dev.project"),
      devPort = _first_("dev.port", "cncf.dev.port"),
      devComponentDevDirs = _all_("dev.componentDevDirs", "dev.component.dev.dirs", "cncf.dev.componentDevDirs", "cncf.component.dev.dir"),
      carRepositories = _all_("repositories.car", "componentRepositories.car", "cncf.repository.car", "cncf.component.repository.car"),
      sarRepositories = _all_("repositories.sar", "componentRepositories.sar", "cncf.repository.sar", "cncf.subsystem.repository.sar"),
      mavenRepositories = _all_("repositories.maven", "cncf.repository.maven"),
      coursierRepositories = _all_("repositories.coursier", "cncf.repository.coursier")
    )
  }

  def localCarRepositories(paths: LauncherPaths): Vector[String] =
    Vector(paths.localCarRepository.toString)

  def localSarRepositories(paths: LauncherPaths): Vector[String] =
    Vector(paths.localSarRepository.toString)

  def render(config: LauncherConfig): String = {
    val c =
      if (_has_local_repository(config))
        config
      else
        config.normalizedWithDefaults
    val runtime = c.runtimeVersion.getOrElse("(not configured)")
    val runtimedevdir = c.runtimeDevDir.getOrElse("(not configured)")
    val catalog = c.runtimeCatalogUrl.getOrElse("(not configured)")
    val selection = c.runtimeSelectionPolicy.map(RuntimeSelectionPolicy.render).getOrElse("current-compatible")
    val nocompatible = c.runtimeNoCompatiblePolicy.map(RuntimeNoCompatiblePolicy.render).getOrElse("error")
    val cars = c.carRepositories.mkString(", ")
    val sars = c.sarRepositories.mkString(", ")
    val mavens = c.mavenRepositories.mkString(", ")
    val coursiers = c.coursierRepositories.mkString(", ")
    val localrepository =
      c.carRepositories.find(_.contains("/.cncf/repository/repository/car")).
        map(_.stripSuffix("/repository/car")).
        getOrElse("~/.cncf/repository")
    val devproject = c.devProject.getOrElse("(not configured)")
    val devport = c.devPort.getOrElse(LauncherConfig.DEFAULT_DEV_PORT)
    val devdirs = c.devComponentDevDirs.mkString(", ")
    s"""runtime.version: $runtime
       |runtime.devDir: $runtimedevdir
       |runtime.catalog.url: $catalog
       |runtime.cncf.selectionPolicy: $selection
       |runtime.cncf.noCompatiblePolicy: $nocompatible
       |dev.project: $devproject
       |dev.port: $devport
       |dev.componentDevDirs: $devdirs
       |local.repository: $localrepository
       |local.repository.note: local CAR/SAR publish target; ~/.cncf/cache is remote artifact cache
       |repositories.car: $cars
       |repositories.sar: $sars
       |repositories.maven: $mavens
       |repositories.coursier: $coursiers""".stripMargin
  }

  private def _has_local_repository(config: LauncherConfig): Boolean =
    (config.carRepositories ++ config.sarRepositories).exists(_.contains("/.cncf/repository/repository/"))
}

object SimpleYaml {
  def parse(text: String): Map[String, Vector[String]] = {
    val builder = Map.newBuilder[String, Vector[String]]
    var values = Map.empty[String, Vector[String]]
    var stack = Vector.empty[(Int, String)]
    var pendingkey: Option[String] = None

    def _put_(path: String, value: String): Unit = {
      val clean = _unquote(value.trim)
      if (clean.nonEmpty)
        values = values.updated(path, values.getOrElse(path, Vector.empty) :+ clean)
    }

    text.linesIterator.foreach { raw =>
      val uncommented = _strip_comment(raw)
      if (uncommented.trim.nonEmpty) {
        val indent = uncommented.takeWhile(_ == ' ').length
        val trimmed = uncommented.trim
        stack = stack.dropRight(stack.count(_._1 >= indent))
        if (trimmed.startsWith("- ")) {
          pendingkey.foreach(k => _put_(k, trimmed.drop(2)))
        } else {
          val idx = trimmed.indexOf(':')
          if (idx >= 0) {
            val key = trimmed.substring(0, idx).trim
            val value = trimmed.substring(idx + 1).trim
            val path = (stack.map(_._2) :+ key).mkString(".")
            if (value.isEmpty) {
              stack = stack :+ (indent, key)
              pendingkey = Some(path)
            } else {
              _put_(path, value)
              pendingkey = Some(path)
            }
          }
        }
      }
    }
    builder ++= values
    builder.result()
  }

  private def _strip_comment(s: String): String = {
    val idx = s.indexOf('#')
    if (idx < 0) s else s.substring(0, idx)
  }

  private def _unquote(s: String): String =
    if (s.length >= 2 && ((s.head == '"' && s.last == '"') || (s.head == '\'' && s.last == '\'')))
      s.substring(1, s.length - 1)
    else
      s
}
