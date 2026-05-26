package cncf.launcher

import java.nio.file.{Path, Paths}

/*
 * @since   May. 17, 2026
 * @version May. 27, 2026
 * @author  ASAMI, Tomoharu
 */
final case class LauncherPaths(
  home: Path = Paths.get(sys.props.getOrElse("user.home", ".")).toAbsolutePath.normalize,
  cwd: Path = Paths.get("").toAbsolutePath.normalize
) {
  val cncfHome: Path = home.resolve(".cncf")
  val globalConfig: Path = cncfHome.resolve("launcher.yaml")
  val projectConfig: Path = cwd.resolve(".cncf").resolve("launcher.yaml")
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
