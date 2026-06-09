import org.goldenport.cozy.CozyPlugin.autoImport._

ThisBuild / organization := "org.goldenport"
ThisBuild / version := "0.1.3"
ThisBuild / scalaVersion := "3.3.7"
ThisBuild / publishMavenStyle := true

cozyCoursierChannelPath := "repository/textus/coursier-channel.json"

cozyCoursierChannelEntries := Seq(CozyCoursierChannelEntry(
  name = "cncf",
  repositories = Seq("central", "https://www.simplemodeling.org/repository/maven"),
  dependencies = Seq(s"org.goldenport:cncf_3:${version.value}"),
  mainClass = "cncf.launcher.CncfLauncherMain"
))

def launcherBuildInfoSource(target: File, packageName: String, launcherName: String, launcherVersion: String): File = {
  val file = target / "LauncherBuildInfo.scala"
  IO.write(file,
    s"""package $packageName
       |
       |object LauncherBuildInfo {
       |  val name: String = "$launcherName"
       |  val version: String = "$launcherVersion"
       |}
       |""".stripMargin)
  file
}

lazy val root = (project in file("."))
  .enablePlugins(org.goldenport.cozy.CozyPlugin)
  .settings(
    name := "cncf",
    Compile / sourceGenerators += Def.task {
      Seq(launcherBuildInfoSource(
        (Compile / sourceManaged).value / "launcher-build-info",
        "cncf.launcher",
        name.value,
        version.value
      ))
    }.taskValue,
    Compile / mainClass := Some("cncf.launcher.CncfLauncherMain"),
    Test / test := {
      (Test / runMain).toTask(" cncf.launcher.CncfLauncherSpec").value
    },
    publishTo := {
      val repo = sys.env.get("SIMPLEMODELING_MAVEN_LOCAL")
        .map(file)
        .getOrElse(baseDirectory.value / "maven-local")
      Some(Resolver.file("local-simplemodeling-maven", repo))
    },
    Compile / packageDoc / publishArtifact := false
    ,
    publish / packagedArtifacts := {
      cozyPublishCoursierChannel.value
      (publish / packagedArtifacts).value
    }
  )
