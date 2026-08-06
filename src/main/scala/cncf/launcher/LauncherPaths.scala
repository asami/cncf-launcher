package cncf.launcher

import java.nio.file.{Path, Paths}

/*
 * @since   May. 17, 2026
 *  version Jul. 22, 2026
 * @version Aug.  6, 2026
 * @author  ASAMI, Tomoharu
 */
final case class LauncherPaths(
  home: Path = LauncherPaths.defaultHome(),
  cwd: Path = Paths.get("").toAbsolutePath.normalize
) {
  val cncfHome: Path = home.resolve(".cncf")
  val globalConfig: Path = cncfHome.resolve("launcher.yaml")
  val supervisorConfig: Path = cncfHome.resolve("launcher").resolve("supervisor.yaml")
  val supervisorState: Path = cncfHome.resolve("launcher").resolve("supervisor-state.json")
  val supervisorLog: Path = cncfHome.resolve("launcher").resolve("supervisor.log")
  val serverEvidence: Path = cncfHome.resolve("launcher").resolve("server-evidence.json")
  val projectConfig: Path = cwd.resolve("conf").resolve("cncf").resolve("launcher.yaml")
  val projectLocalConfig: Path = cwd.resolve(".cncf").resolve("launcher.yaml")
  val globalRuntimeConfig: Path = cncfHome.resolve("config.yaml")
  val projectRuntimeConfig: Path = cwd.resolve("conf").resolve("cncf").resolve("config.yaml")
  val projectLocalRuntimeConfig: Path = cwd.resolve(".cncf").resolve("config.yaml")
  val globalVersion: Path = cncfHome.resolve("version")
  val projectVersion: Path = cwd.resolve(".cncf").resolve("version")
  val runtimeRoot: Path = cncfHome.resolve("runtimes")
  val runtimeCatalog: Path = cncfHome.resolve("catalog").resolve("cncf").resolve("runtime-catalog.yaml")
  val coursierCache: Path = cncfHome.resolve("cache").resolve("coursier")
  val localRepository: Path = cncfHome.resolve("local")
  val localCarRepository: Path = localRepository.resolve("repository").resolve("car")
  val localSarRepository: Path = localRepository.resolve("repository").resolve("sar")
  val cacheRepository: Path = cncfHome.resolve("cache")
  val cacheCarRepository: Path = cacheRepository.resolve("car")
  val cacheSarRepository: Path = cacheRepository.resolve("sar")

  def withCwd(path: Path): LauncherPaths =
    copy(cwd = path.toAbsolutePath.normalize)
}

object LauncherPaths {
  private[launcher] def admitApplicationHome(
    environment: Map[String, String] = sys.env,
    properties: scala.collection.mutable.Map[String, String] = sys.props
  ): Unit =
    environment.get("HOME").map(_.trim).filter(_.nonEmpty).foreach { home =>
      properties.update("user.home", Paths.get(home).toAbsolutePath.normalize.toString)
    }

  def defaultHome(
    environment: Map[String, String] = sys.env,
    properties: Map[String, String] = sys.props.toMap
  ): Path =
    Paths.get(
      environment.get("HOME").filter(_.nonEmpty)
        .orElse(properties.get("user.home").filter(_.nonEmpty))
        .getOrElse(".")
    ).toAbsolutePath.normalize
}
