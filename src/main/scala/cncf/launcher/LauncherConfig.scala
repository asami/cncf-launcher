package cncf.launcher

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

/*
 * @since   May. 17, 2026
 * @version May. 27, 2026
 * @author  ASAMI, Tomoharu
 */
final case class LauncherConfig(
  runtimeVersion: Option[String] = None,
  runtimeDevDir: Option[String] = None,
  runtimeCatalogUrl: Option[String] = None,
  runtimeSelectionPolicy: Option[RuntimeSelectionPolicy] = None,
  runtimeNoCompatiblePolicy: Option[RuntimeNoCompatiblePolicy] = None,
  devExecutionProfile: Option[CncfCommand.DevExecutionProfile] = None,
  devProjectDev: Option[String] = None,
  devPort: Option[String] = None,
  devRestart: Option[Boolean] = None,
  devForceExisting: Option[Boolean] = None,
  cncfConfigFiles: Vector[String] = Vector.empty,
  textusKnowledgeRdfNodePrefix: Option[String] = None,
  textusKnowledgeRdfPublicBaseUri: Option[String] = None,
  textusKnowledgeRdfNamespacePrefixes: Option[String] = None,
  textusKnowledgeRdfNamespaces: Vector[(String, String)] = Vector.empty,
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
      devExecutionProfile = higher.devExecutionProfile.orElse(devExecutionProfile),
      devProjectDev = higher.devProjectDev.orElse(devProjectDev),
      devPort = higher.devPort.orElse(devPort),
      devRestart = higher.devRestart.orElse(devRestart),
      devForceExisting = higher.devForceExisting.orElse(devForceExisting),
      cncfConfigFiles = _merge_list(cncfConfigFiles, higher.cncfConfigFiles),
      textusKnowledgeRdfNodePrefix = higher.textusKnowledgeRdfNodePrefix.orElse(textusKnowledgeRdfNodePrefix),
      textusKnowledgeRdfPublicBaseUri = higher.textusKnowledgeRdfPublicBaseUri.orElse(textusKnowledgeRdfPublicBaseUri),
      textusKnowledgeRdfNamespacePrefixes = higher.textusKnowledgeRdfNamespacePrefixes.orElse(textusKnowledgeRdfNamespacePrefixes),
      textusKnowledgeRdfNamespaces = _merge_namespaces(textusKnowledgeRdfNamespaces, higher.textusKnowledgeRdfNamespaces),
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
      carRepositories = _append_defaults(carRepositories, LauncherConfig.localCarRepositories(paths) ++ LauncherConfig.cacheCarRepositories(paths) ++ LauncherConfig.DEFAULT_CAR_REPOSITORIES),
      sarRepositories = _append_defaults(sarRepositories, LauncherConfig.localSarRepositories(paths) ++ LauncherConfig.cacheSarRepositories(paths) ++ LauncherConfig.DEFAULT_SAR_REPOSITORIES),
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

  private def _merge_namespaces(
    lower: Vector[(String, String)],
    higher: Vector[(String, String)]
  ): Vector[(String, String)] =
    (higher ++ lower).foldLeft(Vector.empty[(String, String)]) { (z, x) =>
      val prefix = _normalize_namespace_prefix(x._1)
      val namespaceuri = x._2.trim
      if (prefix.isEmpty || namespaceuri.isEmpty || z.exists(_._1 == prefix))
        z
      else
        z :+ (prefix -> namespaceuri)
    }

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

  private def _normalize_namespace_prefix(value: String): String =
    value.trim.toLowerCase.replace('_', '-').replaceAll("[^a-z0-9-]+", "-").replaceAll("(^-+|-+$)", "")
}

object LauncherConfig {
  val DEFAULT_RUNTIME_VERSION = "recommended"
  val DEFAULT_RUNTIME_CATALOG_URL = "https://www.simplemodeling.org/repository/textus/runtime-catalog.yaml"
  val DEFAULT_DEV_PORT = "19532"
  val DEFAULT_CAR_REPOSITORIES = Vector("https://www.simplemodeling.org/repository/car")
  val DEFAULT_SAR_REPOSITORIES = Vector("https://www.simplemodeling.org/repository/sar")
  val DEFAULT_MAVEN_REPOSITORIES = Vector("https://www.simplemodeling.org/repository/maven")

  def load(paths: LauncherPaths): LauncherConfig =
    load(paths, Vector.empty)

  def load(
    paths: LauncherPaths,
    configfiles: Vector[String]
  ): LauncherConfig = {
    val global = loadFile(paths.globalConfig)
    val project = loadFile(paths.projectConfig)
    val base = LauncherConfig()
      .mergeHigher(global)
      .mergeHigher(project)
    val explicit = configfiles.foldLeft(base) { (acc, file) =>
      acc.mergeHigher(loadRequiredFile(paths.cwd.resolve(file).normalize.toAbsolutePath.normalize))
    }
    explicit.normalizedWithDefaults(paths)
  }

  def loadFile(path: Path): LauncherConfig =
    if (Files.isRegularFile(path)) {
      val text = Files.readString(path, StandardCharsets.UTF_8)
      fromParsed(LauncherConfigParser.parse(path, text))
    } else {
      LauncherConfig()
    }

  def loadRequiredFile(path: Path): LauncherConfig =
    if (Files.isRegularFile(path))
      loadFile(path)
    else
      throw CncfException(s"launcher config file not found: ${path}")

  def fromParsed(values: Map[String, Vector[String]]): LauncherConfig = {
    def _first_(keys: String*): Option[String] =
      keys.toVector.flatMap(k => values.getOrElse(k, Vector.empty)).headOption.map(_.trim).filter(_.nonEmpty)
    def _all_(keys: String*): Vector[String] =
      keys.toVector.flatMap(k => values.getOrElse(k, Vector.empty)).map(_.trim).filter(_.nonEmpty).distinct
    def _boolean_(keys: String*): Option[Boolean] =
      _first_(keys*).map {
        case "true" | "yes" | "on" | "1" => true
        case "false" | "no" | "off" | "0" => false
        case other => throw CncfException(s"invalid boolean config value: $other")
      }

    val namespaceprefixes =
      _all_("textus.knowledge.rdf.namespace-prefixes", "textus.knowledge.rdf.namespace_prefixes", "textus.knowledge.rdf.namespacePrefixes")
        .flatMap(_.split(",").toVector.map(_.trim).filter(_.nonEmpty))
        .distinct

    LauncherConfig(
      runtimeVersion = _first_("runtime.version", "cncf.runtime.version", "version"),
      runtimeDevDir = _first_("runtime.dev-dir", "runtime.dev_dir", "cncf.runtime.dev-dir", "cncf.runtime.dev_dir", "runtime.devDir", "runtime.dev.dir", "cncf.runtime.devDir", "cncf.runtime.dev.dir"),
      runtimeCatalogUrl = _first_("runtime.catalog.url", "cncf.runtime.catalog.url", "catalog.url"),
      runtimeSelectionPolicy = _first_("runtime.cncf.selection-policy", "runtime.cncf.selection_policy", "cncf.runtime.cncf.selection-policy", "cncf.runtime.cncf.selection_policy", "runtime.cncf.selectionPolicy", "cncf.runtime.cncf.selectionPolicy").
        map(RuntimeSelectionPolicy.parse),
      runtimeNoCompatiblePolicy = _first_("runtime.cncf.no-compatible-policy", "runtime.cncf.no_compatible_policy", "cncf.runtime.cncf.no-compatible-policy", "cncf.runtime.cncf.no_compatible_policy", "runtime.cncf.noCompatiblePolicy", "cncf.runtime.cncf.noCompatiblePolicy").
        map(RuntimeNoCompatiblePolicy.parse),
      devExecutionProfile = _first_("dev.profile", "dev.execution-profile", "dev.execution_profile", "cncf.dev.profile", "cncf.dev.execution-profile", "cncf.dev.execution_profile", "dev.executionProfile", "cncf.dev.executionProfile").
        map(CncfCommand.DevExecutionProfile.parse),
      devProjectDev = _first_(
        "dev.project-dev",
        "dev.project_dev",
        "cncf.dev.project-dev",
        "cncf.dev.project_dev",
        "dev.projectDev",
        "cncf.dev.projectDev",
        "dev.project",
        "cncf.dev.project"
      ),
      devPort = _first_("dev.port", "cncf.dev.port"),
      devRestart = _boolean_("dev.restart", "cncf.dev.restart", "dev.stop-existing", "dev.stop_existing", "cncf.dev.stop-existing", "cncf.dev.stop_existing", "dev.stopExisting", "cncf.dev.stopExisting"),
      devForceExisting = _boolean_("dev.force-existing", "dev.force_existing", "cncf.dev.force-existing", "cncf.dev.force_existing", "dev.forceExisting", "cncf.dev.forceExisting"),
      cncfConfigFiles = _cncf_config_files(values),
      textusKnowledgeRdfNodePrefix = _first_("textus.knowledge.rdf.current-prefix", "textus.knowledge.rdf.current_prefix", "textus.knowledge.rdf.currentPrefix", "textus.knowledge.rdf.node-prefix", "textus.knowledge.rdf.node_prefix", "textus.knowledge.rdf.nodePrefix", "textus.knowledge.rdf.prefix"),
      textusKnowledgeRdfPublicBaseUri = _first_("textus.knowledge.rdf.public-base-uri", "textus.knowledge.rdf.public_base_uri", "textus.knowledge.rdf.publicBaseUri", "textus.knowledge.rdf.public-base-url", "textus.knowledge.rdf.public_base_url", "textus.knowledge.rdf.publicBaseUrl"),
      textusKnowledgeRdfNamespacePrefixes = if (namespaceprefixes.isEmpty) None else Some(namespaceprefixes.mkString(",")),
      textusKnowledgeRdfNamespaces = _rdf_namespaces(values),
      devComponentDevDirs = _all_("dev.component-dev-dirs", "dev.component_dev_dirs", "cncf.dev.component-dev-dirs", "cncf.dev.component_dev_dirs", "dev.componentDevDirs", "dev.component.dev.dirs", "cncf.dev.componentDevDirs", "cncf.component.dev.dir"),
      carRepositories = _all_("repositories.car", "componentRepositories.car", "cncf.repository.car", "cncf.component.repository.car"),
      sarRepositories = _all_("repositories.sar", "componentRepositories.sar", "cncf.repository.sar", "cncf.subsystem.repository.sar"),
      mavenRepositories = _all_("repositories.maven", "cncf.repository.maven"),
      coursierRepositories = _all_("repositories.coursier", "cncf.repository.coursier")
    )
  }

  private def _cncf_config_files(
    values: Map[String, Vector[String]]
  ): Vector[String] =
    _config_files(values, Vector(
      "cncf.config.file",
      "cncf.config.files"
    ))

  private def _config_files(
    values: Map[String, Vector[String]],
    keys: Vector[String]
  ): Vector[String] =
    keys.flatMap(key =>
      values.getOrElse(key, Vector.empty).flatMap(_.split(",").toVector)
    ).map(_.trim).filter(_.nonEmpty).distinct

  private def _rdf_namespaces(
    values: Map[String, Vector[String]]
  ): Vector[(String, String)] = {
    val prefixes = Vector("textus.knowledge.rdf.namespaces.", "textus.knowledge.rdf.namespace.")
    values.toVector.flatMap { case (key, xs) =>
      prefixes.find(key.startsWith).toVector.flatMap { prefixkey =>
        val prefix = key.stripPrefix(prefixkey)
        xs.headOption.map(value => _normalize_namespace_prefix(prefix) -> value.trim)
      }
    }.filter { case (prefix, value) =>
      prefix.nonEmpty && value.nonEmpty
    }.foldLeft(Vector.empty[(String, String)]) { (z, x) =>
      if (z.exists(_._1 == x._1))
        z
      else
        z :+ x
    }
  }

  private def _normalize_namespace_prefix(value: String): String =
    value.trim.toLowerCase.replace('_', '-').replaceAll("[^a-z0-9-]+", "-").replaceAll("(^-+|-+$)", "")

  def localCarRepositories(paths: LauncherPaths): Vector[String] =
    Vector(paths.localCarRepository.toString)

  def localSarRepositories(paths: LauncherPaths): Vector[String] =
    Vector(paths.localSarRepository.toString)

  def cacheCarRepositories(paths: LauncherPaths): Vector[String] =
    Vector(paths.cacheCarRepository.toString)

  def cacheSarRepositories(paths: LauncherPaths): Vector[String] =
    Vector(paths.cacheSarRepository.toString)

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
      c.carRepositories.find(_.contains("/.cncf/local/repository/car")).
        map(_.stripSuffix("/repository/car")).
        getOrElse("~/.cncf/local")
    val cacherepository =
      c.carRepositories.find(_.contains("/.cncf/cache/car")).
        map(_.stripSuffix("/car")).
        getOrElse("~/.cncf/cache")
    val devproject = c.devProjectDev.getOrElse("(not configured)")
    val devprofile = c.devExecutionProfile.map(_.name).getOrElse("(not configured)")
    val devport = c.devPort.getOrElse(LauncherConfig.DEFAULT_DEV_PORT)
    val devrestart = c.devRestart.getOrElse(false)
    val devforce = c.devForceExisting.getOrElse(false)
    val rdfprefix = c.textusKnowledgeRdfNodePrefix.getOrElse("(not configured)")
    val rdfbaseuri = c.textusKnowledgeRdfPublicBaseUri.getOrElse("(not configured)")
    val rdfnamespaceprefixes = c.textusKnowledgeRdfNamespacePrefixes.getOrElse("(not configured)")
    val rdfnamespaces =
      if (c.textusKnowledgeRdfNamespaces.isEmpty)
        "(not configured)"
      else
        c.textusKnowledgeRdfNamespaces.map { case (prefix, uri) => s"$prefix=$uri" }.mkString(", ")
    val devdirs = c.devComponentDevDirs.mkString(", ")
    s"""runtime.version: $runtime
       |runtime.dev-dir: $runtimedevdir
       |runtime.catalog.url: $catalog
       |runtime.cncf.selection-policy: $selection
       |runtime.cncf.no-compatible-policy: $nocompatible
       |dev.project-dev: $devproject
       |dev.profile: $devprofile
       |dev.port: $devport
       |dev.restart: $devrestart
       |dev.force-existing: $devforce
       |dev.component-dev-dirs: $devdirs
       |textus.knowledge.rdf.node-prefix: $rdfprefix
       |textus.knowledge.rdf.public-base-uri: $rdfbaseuri
       |textus.knowledge.rdf.namespace-prefixes: $rdfnamespaceprefixes
       |textus.knowledge.rdf.namespaces: $rdfnamespaces
       |local.repository: $localrepository
       |cache.repository: $cacherepository
       |local.repository.note: ~/.cncf/local is developer local publish state; ~/.cncf/cache is runtime-managed remote artifact cache
       |repositories.car: $cars
       |repositories.sar: $sars
       |repositories.maven: $mavens
       |repositories.coursier: $coursiers""".stripMargin
  }

  private def _has_local_repository(config: LauncherConfig): Boolean =
    (config.carRepositories ++ config.sarRepositories).exists(_.contains("/.cncf/local/repository/"))
}

object LauncherConfigParser {
  def parse(
    path: Path,
    text: String
  ): Map[String, Vector[String]] =
    _file_type(path) match {
      case "yaml" | "yml" => _parse_light_yaml(text)
      case "properties" | "props" | "conf" => _parse_properties(text)
      case other =>
        throw CncfException(s"unsupported launcher config file type: .$other; use yaml, yml, properties, props, or conf")
    }

  private def _file_type(path: Path): String = {
    val name = path.getFileName.toString
    val i = name.lastIndexOf('.')
    if (i >= 0 && i + 1 < name.length)
      name.substring(i + 1).toLowerCase
    else
      "yaml"
  }

  private def _parse_properties(text: String): Map[String, Vector[String]] = {
    var values = Map.empty[String, Vector[String]]
    text.linesIterator.foreach { raw =>
      val uncommented = _strip_comment(raw)
      val trimmed = uncommented.trim
      if (trimmed.nonEmpty) {
        val idx = _key_value_index(trimmed)
        if (idx >= 0) {
          val key = trimmed.substring(0, idx).trim
          val value = trimmed.substring(idx + 1).trim
          _put_value(key, value, values).foreach(v => values = v)
        }
      }
    }
    values
  }

  private def _parse_light_yaml(text: String): Map[String, Vector[String]] = {
    val builder = Map.newBuilder[String, Vector[String]]
    var values = Map.empty[String, Vector[String]]
    var stack = Vector.empty[(Int, String)]
    var pendingkey: Option[String] = None

    def _put_(path: String, value: String): Unit = {
      _put_value(path, value, values).foreach(v => values = v)
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
          val idx = _key_value_index(trimmed)
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

  private def _put_value(
    path: String,
    value: String,
    values: Map[String, Vector[String]]
  ): Option[Map[String, Vector[String]]] = {
    val key = path.trim
    val clean = _unquote(value.trim)
    if (key.nonEmpty && clean.nonEmpty)
      Some(values.updated(key, values.getOrElse(key, Vector.empty) :+ clean))
    else
      None
  }

  private def _strip_comment(s: String): String = {
    val idx = s.indexOf('#')
    if (idx < 0) s else s.substring(0, idx)
  }

  private def _key_value_index(s: String): Int = {
    val colon = s.indexOf(':')
    val equals = s.indexOf('=')
    (colon, equals) match {
      case (-1, -1) => -1
      case (-1, x) => x
      case (x, -1) => x
      case (x, y) => math.min(x, y)
    }
  }

  private def _unquote(s: String): String =
    if (s.length >= 2 && ((s.head == '"' && s.last == '"') || (s.head == '\'' && s.last == '\'')))
      s.substring(1, s.length - 1)
    else
      s
}

object SimpleYaml {
  def parse(text: String): Map[String, Vector[String]] =
    LauncherConfigParser.parse(Path.of("config.yaml"), text)
}
