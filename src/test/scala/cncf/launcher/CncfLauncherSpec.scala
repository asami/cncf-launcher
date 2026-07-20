package cncf.launcher

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.jdk.CollectionConverters.*

/*
 * @since   May. 17, 2026
 *  version Jun. 29, 2026
 * @version Jul. 19, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfLauncherSpec {
  def main(args: Array[String]): Unit = {
    val spec = new CncfLauncherSpec
    spec.parser()
    spec.runtimeVersion()
    spec.launcherVersion()
    spec.runtimeHelp()
    spec.configMerge()
    spec.launcherDevDirDelegatesToDevelopmentLauncher()
    spec.launcherDevDirRejectsStaleDevelopmentClasspath()
    spec.configSupportsAdditionalRdfNamespaces()
    spec.configFileOptionOverridesProjectConfig()
    spec.workspaceRootConfigAppliesToNestedCwd()
    spec.launcherConfigControlsDevelopmentRuntime()
    spec.launcherConfigSupportsPropertiesAndConfFiles()
    spec.defaultRuntimeConfigFilesAreForwarded()
    spec.configFileProjectDevSurvivesTargetCwdSwitch()
    spec.cncfConfigOptionIsForwardedToRuntime()
    spec.configFileOptionRequiresExistingFile()
    spec.runtimeVersionPrecedence()
    spec.runtimeUseWritesExpectedFiles()
    spec.runtimeUseAutoSelectsProjectWhenCncfDirectoryExists()
    spec.installCliWritesDevelopmentCommand()
    spec.installCliPinsDevelopmentRuntimeWithoutCatalog()
    spec.installCliRejectsIncompatibleDevelopmentRuntime()
    spec.textusControlCenterRegistrationLifecycle()
    spec.standaloneControlCenterLocatorLifecycle()
    spec.textusControlCenterRegistrationHttpLifecycle()
    spec.textusControlCenterRegistrationHttpFailureIsolation()
    spec.executeTargetFirstDelegatesToRuntime()
    spec.serverExecutionDelegatesDefaultPortResolutionToRuntime()
    spec.runtimeCatalogParseAndSelectorResolution()
    spec.runtimeCatalogCommands()
    spec.runtimeCurrentWarnsWhenCachedRecommendedIsStale()
    spec.runtimeDescriptorCommands()
    spec.runtimeDescriptorPrefersRuntimeJarDescriptor()
    spec.devParser()
    spec.devServerRewritesToCncfArgs()
    spec.devServerParserSupportsProcessManagementOptions()
    spec.devServerWritesStateDuringInvocation()
    spec.devServerRejectsAliveExistingState()
    spec.devServerStopExistingBeforeInvocation()
    spec.devStopStopsExistingWithoutInvocation()
    spec.devStopUsesRecordedPortWhenPortIsOmitted()
    spec.devServerForceStopsAfterGracefulFailure()
    spec.devServerRequiresForceForAmbiguousAliveState()
    spec.devServerRejectsPidReuseWithoutForce()
    spec.devServerProfileAddsLocalPersistentSqliteArgs()
    spec.devConfigCanSelectExecutionProfile()
    spec.devServerUsesRuntimeDevelopmentDirectory()
    spec.devServerUsesRuntimeDevelopmentCatalogForSelection()
    spec.devCommandPassesRuntimeLeadingArgs()
    spec.devCommandKeepsSampleMainClassValueAsRuntimeArg()
    spec.devCommandCanDisableProjectClasspath()
    spec.devCommandCanDisableProjectComponentDevDir()
    spec.devCommandDoesNotAutoActivateComponentDirArtifacts()
    spec.devTargetOptionsAreMutuallyExclusive()
    spec.devNameTargetUsesLocalSnapshotOnly()
    spec.devNameTargetSnapshotBypassesReleaseCatalog()
    spec.devNameTargetUsesReleaseRepositories()
    spec.devServerEmulationRewritesToCncfArgs()
    spec.devProjectLoadsTargetProjectConfig()
    spec.devHelpExplainsResolutionModel()
    spec.devCheckReportsMainTargetAndDependencyResolution()
    spec.devCheckReportsDevServerState()
    spec.devCheckTreatsMissingMainTargetClasspathAsWarning()
    spec.devCheckTreatsMissingDependencyClasspathAsError()
    spec.devServerAutoGeneratesMainTargetClasspath()
    spec.devServerReportsMainTargetClasspathExportFailure()
    spec.devUsesCurrentCompatibleRuntimeByDefault()
    spec.devCanSelectLatestTestedRuntime()
    spec.devCanSelectLatestCompatibleRuntime()
    spec.devCanSelectNewestCompatibleRuntime()
    spec.devParsesInlineRuntimeRequirementLists()
    spec.devSelectsCommonRuntimeAcrossProjectAndDependency()
    spec.devRuntimeConflictDefaultsToError()
    spec.devRuntimeConflictCanUseNewestPolicy()
    spec.runtimeCommandDoesNotLoadCncf()
    spec.latestRuntimeIsConcrete()
    spec.noRuntimeLibraryDependencies()
    println("CncfLauncherSpec: OK")
  }
}

final class CncfLauncherSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "cncf launcher" should {
    "command parsing" which {
      "parser" in {
        Given("the cncf launcher scenario: parser")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        parser()
      }

      "runtime catalog parse and selector resolution" in {
        Given("the cncf launcher scenario: runtime catalog parse and selector resolution")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        runtimeCatalogParseAndSelectorResolution()
      }

      "dev parser" in {
        Given("the cncf launcher scenario: dev parser")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devParser()
      }

      "dev server parser supports process management options" in {
        Given("the cncf launcher scenario: dev server parser supports process management options")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devServerParserSupportsProcessManagementOptions()
      }

    }

    "configuration and launcher metadata" which {
      "launcher version" in {
        Given("the cncf launcher scenario: launcher version")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        launcherVersion()
      }

      "config merge" in {
        Given("the cncf launcher scenario: config merge")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        configMerge()
      }

      "launcher dev dir delegates to development launcher" in {
        Given("the cncf launcher scenario: launcher dev dir delegates to development launcher")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        launcherDevDirDelegatesToDevelopmentLauncher()
      }

      "launcher dev dir rejects stale development classpath" in {
        Given("the cncf launcher scenario: launcher dev dir rejects stale development classpath")
        When("the launcher development classpath does not contain the launcher main class")
        Then("the launcher reports the stale classpath before spawning the delegated process")
        launcherDevDirRejectsStaleDevelopmentClasspath()
      }

      "config supports additional rdf namespaces" in {
        Given("the cncf launcher scenario: config supports additional rdf namespaces")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        configSupportsAdditionalRdfNamespaces()
      }

      "config file option overrides project config" in {
        Given("the cncf launcher scenario: config file option overrides project config")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        configFileOptionOverridesProjectConfig()
      }

      "workspace root config applies to nested cwd" in {
        Given("the cncf launcher scenario: workspace root config applies to nested cwd")
        When("the launcher loads config from a nested sample directory")
        Then("the executable specification holds through inherited root config")
        workspaceRootConfigAppliesToNestedCwd()
      }

      "launcher config controls development runtime" in {
        launcherConfigControlsDevelopmentRuntime()
      }

      "launcher config supports properties and conf files" in {
        Given("the cncf launcher scenario: launcher config supports properties and conf files")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        launcherConfigSupportsPropertiesAndConfFiles()
      }

      "default runtime config files are forwarded" in {
        Given("the cncf launcher scenario: default runtime config files are forwarded")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        defaultRuntimeConfigFilesAreForwarded()
      }

      "config file project dev survives target cwd switch" in {
        Given("the cncf launcher scenario: config file project dev survives target cwd switch")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        configFileProjectDevSurvivesTargetCwdSwitch()
      }

      "cncf config option is forwarded to runtime" in {
        Given("the cncf launcher scenario: cncf config option is forwarded to runtime")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        cncfConfigOptionIsForwardedToRuntime()
      }

      "config file option requires existing file" in {
        Given("the cncf launcher scenario: config file option requires existing file")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        configFileOptionRequiresExistingFile()
      }

      "dev config can select execution profile" in {
        Given("the cncf launcher scenario: dev config can select execution profile")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devConfigCanSelectExecutionProfile()
      }

      "dev project loads target project config" in {
        Given("the cncf launcher scenario: dev project loads target project config")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devProjectLoadsTargetProjectConfig()
      }

    }

    "runtime selection and catalog operations" which {
      "runtime version" in {
        Given("the cncf launcher scenario: runtime version")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        runtimeVersion()
      }

      "runtime help" in {
        Given("the cncf launcher scenario: runtime help")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        runtimeHelp()
      }

      "runtime version precedence" in {
        Given("the cncf launcher scenario: runtime version precedence")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        runtimeVersionPrecedence()
      }

      "runtime use writes expected files" in {
        Given("the cncf launcher scenario: runtime use writes expected files")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        runtimeUseWritesExpectedFiles()
      }

      "runtime use auto selects project when cncf directory exists" in {
        Given("the cncf launcher scenario: runtime use auto selects project when cncf directory exists")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        runtimeUseAutoSelectsProjectWhenCncfDirectoryExists()
      }

      "install cli writes development command" in {
        Given("the cncf launcher scenario: install cli writes development command")
        When("the launcher installs a development command")
        Then("the command delegates to cncf target-first command with file parameter expansion")
        installCliWritesDevelopmentCommand()
      }

      "install cli pins an explicit development runtime without a runtime catalog" in {
        Given("a development runtime and component requirements with compatible mixed minimum versions")
        When("the launcher installs a development command")
        Then("the wrapper pins the runtime directory without selecting from the normal catalog")
        installCliPinsDevelopmentRuntimeWithoutCatalog()
      }

      "install cli rejects an explicit incompatible development runtime" in {
        Given("a development runtime below the target component minimum version")
        When("the launcher installs a development command")
        Then("the incompatible runtime is rejected before a wrapper is written")
        installCliRejectsIncompatibleDevelopmentRuntime()
      }

      "canonical server commands report one Textus Control Center lifecycle" in {
        textusControlCenterRegistrationLifecycle()
      }

      "canonical server commands resolve one standalone Control Center locator" in {
        standaloneControlCenterLocatorLifecycle()
      }

      "Textus Control Center reporter calls automatic REST operations" in {
        textusControlCenterRegistrationHttpLifecycle()
      }

      "Textus Control Center authorization rejection is isolated from server lifecycle" in {
        textusControlCenterRegistrationHttpFailureIsolation()
      }

      "target-first execution delegates to runtime" in {
        Given("the cncf launcher scenario: target-first execution delegates to runtime")
        When("the launcher receives canonical target-first syntax")
        Then("the runtime receives expanded runtime activation arguments")
        executeTargetFirstDelegatesToRuntime()
      }

      "server execution delegates default port resolution to the runtime" in {
        serverExecutionDelegatesDefaultPortResolutionToRuntime()
      }

      "runtime catalog commands" in {
        Given("the cncf launcher scenario: runtime catalog commands")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        runtimeCatalogCommands()
      }

      "runtime current warns when cached recommended is stale" in {
        Given("the cncf launcher scenario: runtime current warns when cached recommended is stale")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        runtimeCurrentWarnsWhenCachedRecommendedIsStale()
      }

      "runtime descriptor commands" in {
        Given("the cncf launcher scenario: runtime descriptor commands")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        runtimeDescriptorCommands()
      }

      "runtime descriptor prefers runtime jar descriptor" in {
        Given("the cncf launcher scenario: runtime descriptor prefers runtime jar descriptor")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        runtimeDescriptorPrefersRuntimeJarDescriptor()
      }

      "runtime command does not load cncf" in {
        Given("the cncf launcher scenario: runtime command does not load cncf")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        runtimeCommandDoesNotLoadCncf()
      }

      "latest runtime is concrete" in {
        Given("the cncf launcher scenario: latest runtime is concrete")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        latestRuntimeIsConcrete()
      }

    }

    "development runtime operations" which {
      "dev server rewrites to cncf args" in {
        Given("the cncf launcher scenario: dev server rewrites to cncf args")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devServerRewritesToCncfArgs()
      }

      "dev server writes state during invocation" in {
        Given("the cncf launcher scenario: dev server writes state during invocation")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devServerWritesStateDuringInvocation()
      }

      "dev server rejects alive existing state" in {
        Given("the cncf launcher scenario: dev server rejects alive existing state")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devServerRejectsAliveExistingState()
      }

      "dev server stop existing before invocation" in {
        Given("the cncf launcher scenario: dev server stop existing before invocation")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devServerStopExistingBeforeInvocation()
      }

      "dev stop stops existing without invocation" in {
        Given("the cncf launcher scenario: dev stop stops existing without invocation")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devStopStopsExistingWithoutInvocation()
      }

      "dev stop uses recorded port when port is omitted" in {
        Given("the cncf launcher scenario: dev stop uses recorded port when port is omitted")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devStopUsesRecordedPortWhenPortIsOmitted()
      }

      "dev server force stops after graceful failure" in {
        Given("the cncf launcher scenario: dev server force stops after graceful failure")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devServerForceStopsAfterGracefulFailure()
      }

      "dev server requires force for ambiguous alive state" in {
        Given("the cncf launcher scenario: dev server requires force for ambiguous alive state")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devServerRequiresForceForAmbiguousAliveState()
      }

      "dev server rejects pid reuse without force" in {
        Given("the cncf launcher scenario: dev server rejects pid reuse without force")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devServerRejectsPidReuseWithoutForce()
      }

      "dev server profile adds local persistent sqlite args" in {
        Given("the cncf launcher scenario: dev server profile adds local persistent sqlite args")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devServerProfileAddsLocalPersistentSqliteArgs()
      }

      "dev server uses runtime development directory" in {
        Given("the cncf launcher scenario: dev server uses runtime development directory")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devServerUsesRuntimeDevelopmentDirectory()
      }

      "dev server uses runtime development catalog for selection" in {
        Given("the cncf launcher scenario: dev server uses runtime development catalog for selection")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devServerUsesRuntimeDevelopmentCatalogForSelection()
      }

      "dev command passes runtime leading args" in {
        Given("the cncf launcher scenario: dev command passes runtime leading args")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devCommandPassesRuntimeLeadingArgs()
      }

      "dev command keeps sample main class value as runtime arg" in {
        Given("the cncf launcher scenario: dev command keeps sample main class value as runtime arg")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devCommandKeepsSampleMainClassValueAsRuntimeArg()
      }

      "dev command can disable project classpath" in {
        Given("the cncf launcher scenario: dev command can disable project classpath")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devCommandCanDisableProjectClasspath()
      }

      "dev command can disable project component dev dir" in {
        Given("the cncf launcher scenario: dev command can disable project component dev dir")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devCommandCanDisableProjectComponentDevDir()
      }

      "dev command does not auto activate component dir artifacts" in {
        Given("the cncf launcher scenario: dev command does not auto activate component dir artifacts")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devCommandDoesNotAutoActivateComponentDirArtifacts()
      }

      "dev target options are mutually exclusive" in {
        Given("the cncf launcher scenario: dev target options are mutually exclusive")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devTargetOptionsAreMutuallyExclusive()
      }

      "dev name target uses local snapshot only" in {
        Given("the cncf launcher scenario: dev name target uses local snapshot only")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devNameTargetUsesLocalSnapshotOnly()
      }

      "dev name target snapshot bypasses release catalog" in {
        Given("the cncf launcher scenario: dev name target snapshot bypasses release catalog")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devNameTargetSnapshotBypassesReleaseCatalog()
      }

      "dev name target uses release repositories" in {
        Given("the cncf launcher scenario: dev name target uses release repositories")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devNameTargetUsesReleaseRepositories()
      }

      "dev server emulation rewrites to cncf args" in {
        Given("the cncf launcher scenario: dev server emulation rewrites to cncf args")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devServerEmulationRewritesToCncfArgs()
      }

      "dev help explains resolution model" in {
        Given("the cncf launcher scenario: dev help explains resolution model")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devHelpExplainsResolutionModel()
      }

      "dev check reports main target and dependency resolution" in {
        Given("the cncf launcher scenario: dev check reports main target and dependency resolution")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devCheckReportsMainTargetAndDependencyResolution()
      }

      "dev check reports dev server state" in {
        Given("the cncf launcher scenario: dev check reports dev server state")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devCheckReportsDevServerState()
      }

      "dev check treats missing main target classpath as warning" in {
        Given("the cncf launcher scenario: dev check treats missing main target classpath as warning")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devCheckTreatsMissingMainTargetClasspathAsWarning()
      }

      "dev check treats missing dependency classpath as error" in {
        Given("the cncf launcher scenario: dev check treats missing dependency classpath as error")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devCheckTreatsMissingDependencyClasspathAsError()
      }

      "dev server auto generates main target classpath" in {
        Given("the cncf launcher scenario: dev server auto generates main target classpath")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devServerAutoGeneratesMainTargetClasspath()
      }

      "dev server reports main target classpath export failure" in {
        Given("the cncf launcher scenario: dev server reports main target classpath export failure")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devServerReportsMainTargetClasspathExportFailure()
      }

      "dev uses current compatible runtime by default" in {
        Given("the cncf launcher scenario: dev uses current compatible runtime by default")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devUsesCurrentCompatibleRuntimeByDefault()
      }

      "dev can select latest tested runtime" in {
        Given("the cncf launcher scenario: dev can select latest tested runtime")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devCanSelectLatestTestedRuntime()
      }

      "dev can select latest compatible runtime" in {
        Given("the cncf launcher scenario: dev can select latest compatible runtime")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devCanSelectLatestCompatibleRuntime()
      }

      "dev can select newest compatible runtime" in {
        Given("the cncf launcher scenario: dev can select newest compatible runtime")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devCanSelectNewestCompatibleRuntime()
      }

      "dev parses inline runtime requirement lists" in {
        Given("the cncf launcher scenario: dev parses inline runtime requirement lists")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devParsesInlineRuntimeRequirementLists()
      }

      "dev selects common runtime across project and dependency" in {
        Given("the cncf launcher scenario: dev selects common runtime across project and dependency")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devSelectsCommonRuntimeAcrossProjectAndDependency()
      }

      "dev runtime conflict defaults to error" in {
        Given("the cncf launcher scenario: dev runtime conflict defaults to error")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devRuntimeConflictDefaultsToError()
      }

      "dev runtime conflict can use newest policy" in {
        Given("the cncf launcher scenario: dev runtime conflict can use newest policy")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        devRuntimeConflictCanUseNewestPolicy()
      }

    }

    "packaging boundaries" which {
      "no runtime library dependencies" in {
        Given("the cncf launcher scenario: no runtime library dependencies")
        When("the launcher behavior is exercised")
        Then("the executable specification holds through scenario-specific expectations")
        noRuntimeLibraryDependencies()
      }

    }

  }

  def parser(): Unit = {
    val autouse = CncfCommandParser.parse(Vector("runtime", "use", "latest"))
      .asInstanceOf[CncfCommand.Runtime.Use]
    _assert_equals(autouse.version, "latest")
    _assert_equals(autouse.target, CncfCommand.RuntimeUseTarget.Auto)

    val install = CncfCommandParser.parse(Vector(
      "install-cli",
      "sanpomap",
      "--project-dev",
      ".",
      "--component-dev-dir",
      "../textus-georesolver",
      "--overwrite"
    )).asInstanceOf[CncfCommand.InstallCli]
    _assert_equals(install.name, "sanpomap")
    _assert_equals(install.installedName, "sanpomap-dev")
    _assert_equals(install.projectDev, Some("."))
    _assert_equals(install.operationPrefix, None)
    _assert_equals(install.fileParams, Vector.empty)
    _assert_equals(install.componentDevDirs, Vector("../textus-georesolver"))
    _assert_equals(install.binDir, None)
    _assert_equals(install.runtimeVersion, None)
    _assert_equals(install.runtimeDevDir, None)
    _assert_equals(install.launcherDevDir, None)
    install.overwrite shouldBe true

    val installruntime = CncfCommandParser.parse(Vector(
      "--runtime", "0.4.13-SNAPSHOT",
      "--runtime-dev-dir", "/tmp/cncf-runtime",
      "install-cli",
      "sanpomap"
    )).asInstanceOf[CncfCommand.InstallCli]
    _assert_equals(installruntime.runtimeVersion, Some("0.4.13-SNAPSHOT"))
    _assert_equals(installruntime.runtimeDevDir, Some("/tmp/cncf-runtime"))

    val currenttarget = CncfCommandParser.parse(Vector(
      "command",
      "validate-presentation"
    )).asInstanceOf[CncfCommand.Execute]
    _assert_equals(currenttarget.args, Vector("command", "--component-dev-dir=.", "validate-presentation"))

    val explicitcurrenttarget = CncfCommandParser.parse(Vector(
      ".",
      "command",
      "validate-presentation"
    )).asInstanceOf[CncfCommand.Execute]
    _assert_equals(explicitcurrenttarget.args, Vector("command", "--component-dev-dir=.", "validate-presentation"))

    val packagedtarget = CncfCommandParser.parse(Vector(
      "command",
      "--no-project-classpath",
      "--component-car-dir",
      "car.d",
      "testcomp.main.hello"
    )).asInstanceOf[CncfCommand.Execute]
    _assert_equals(packagedtarget.args, Vector("command", "--no-project-classpath", "--component-car-dir", "car.d", "testcomp.main.hello"))

    val namedtarget = CncfCommandParser.parse(Vector(
      "textus-sanpomap:1",
      "command",
      "validate-presentation"
    )).asInstanceOf[CncfCommand.Execute]
    _assert_equals(namedtarget.args, Vector("command", "--textus.component=textus-sanpomap", "--textus.component.version=1", "validate-presentation"))
  }

  def runtimeVersion(): Unit = _with_temp_paths { paths =>
    Given("a project selects a CNCF runtime version in launcher config")
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"), "runtime:\n  version: 0.1.0\n")
    val invoker = FakeInvoker()
    val resolver = FakeResolver()
    val launcher = new CncfLauncher(paths, resolver, invoker)

    When("the launcher version command is executed")
    val code = launcher.run(Vector("version"))

    Then("the launcher delegates version reporting to the selected CNCF runtime")
    _assert_equals(code, 0)
    _assert_equals(resolver.resolvedVersions, Vector("0.1.0"))
    _assert_equals(resolver.resolvedClasspaths, Vector("0.1.0"))
    _assert_equals(invoker.lastClasspath, Vector(paths.cwd.resolve("fake-cncf-0.1.0.jar")))
    _assert_equals(invoker.lastArgs, Vector("version"))
    _assert_equals(CncfCommandParser.parse(Vector("version")), CncfCommand.Runtime.Version(None, None))
    _assert_equals(CncfCommandParser.parse(Vector("--version")), CncfCommand.Runtime.Version(None, None))
  }

  def launcherVersion(): Unit = _with_temp_paths { paths =>
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker())
    val (code, output) = _capture_stdout {
      launcher.run(Vector("launcher", "version"))
    }
    _assert_equals(code, 0)
    _assert_equals(output.trim, s"cncf ${LauncherBuildInfo.version}")
    _assert_equals(CncfCommandParser.parse(Vector("launcher", "version")), CncfCommand.LauncherVersion)
    _assert_equals(CncfCommandParser.parse(Vector("launcher", "--version")), CncfCommand.LauncherVersion)
  }

  def runtimeHelp(): Unit = _with_temp_paths { paths =>
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    val (code, output) = _capture_stdout {
      launcher.run(Vector("help"))
    }
    _assert_equals(code, 0)
    _assert_equals(invoker.lastArgs, Vector("--help"))
    output.contains("Launcher help:") shouldBe true
    output.contains("cncf launcher version") shouldBe true
    output.contains("[--runtime <version>] [--runtime-dev-dir <dir>] install-cli") shouldBe true
    output.contains("[--runtime <version>] [--runtime-dev-dir <dir>] <target> command") shouldBe true
    output.contains("cncf dev is deprecated") shouldBe true
    output.contains("runtime.dev-dir is the configuration equivalent of --runtime-dev-dir") shouldBe true
    output.contains("ancestor conf/cncf/launcher.yaml and .cncf/launcher.yaml") shouldBe true
    _assert_equals(CncfCommandParser.parse(Vector("help")), CncfCommand.RuntimeHelp)
    _assert_equals(CncfCommandParser.parse(Vector("--help")), CncfCommand.RuntimeHelp)
    _assert_equals(CncfCommandParser.parse(Vector("launcher", "help")), CncfCommand.LauncherHelp)
  }

  def configMerge(): Unit = _with_temp_paths { paths =>
    _write(paths.cncfHome.resolve("launcher.yaml"),
      """runtime:
        |  version: 0.1.0
        |  devDir: ../global-cncf
        |  catalog:
        |    url: https://global.example/catalog.yaml
        |dev:
        |  port: 19000
        |  componentDevDirs:
        |    - ../global-component
        |repositories:
        |  car:
        |    - https://global.example/car
        |  sar:
        |    - https://global.example/sar
        |""".stripMargin)
    _write(paths.cwd.resolve("conf").resolve("cncf").resolve("launcher.yaml"),
      """runtime:
        |  version: 0.2.0
        |dev:
        |  project-dev: .
        |  componentDevDirs:
        |    - ../project-component
        |    - ../shared-component
        |repositories:
        |  car:
        |    - https://project.example/car
        |""".stripMargin)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      """cncf:
        |  launcher:
        |    dev:
        |      dir: ../launcher-cncf
        |runtime:
        |  devDir: ../local-cncf
        |dev:
        |  componentDevDirs:
        |    - ../local-component
        |repositories:
        |  car:
        |    - https://local.example/car
        |""".stripMargin)
    val config = LauncherConfig.load(paths)
    _assert_equals(config.launcherDevDir, Some("../launcher-cncf"))
    _assert_equals(config.runtimeVersion, Some("0.2.0"))
    _assert_equals(config.runtimeDevDir, Some("../local-cncf"))
    _assert_equals(config.runtimeCatalogUrl, Some("https://global.example/catalog.yaml"))
    _assert_equals(config.devProjectDev, Some("."))
    _assert_equals(config.devPort, Some("19000"))
    _assert_equals(config.devComponentDevDirs, Vector("../local-component", "../project-component", "../shared-component", "../global-component"))
    config.carRepositories.head == "https://local.example/car" shouldBe true
    config.carRepositories(1) == "https://project.example/car" shouldBe true
    config.carRepositories(2) == "https://global.example/car" shouldBe true
    config.sarRepositories.head == "https://global.example/sar" shouldBe true
    config.carRepositories.contains(paths.localCarRepository.toString) shouldBe true
    config.sarRepositories.contains(paths.localSarRepository.toString) shouldBe true
    config.carRepositories.contains(paths.cacheCarRepository.toString) shouldBe true
    config.sarRepositories.contains(paths.cacheSarRepository.toString) shouldBe true
    config.carRepositories.contains("https://www.simplemodeling.org/repository/car") shouldBe true
  }

  def launcherDevDirDelegatesToDevelopmentLauncher(): Unit = _with_temp_paths { paths =>
    val launcherdevdir = paths.cwd.resolve("launcher-cncf")
    Files.createDirectories(launcherdevdir)
    _write(paths.cwd.resolve("conf").resolve("cncf").resolve("launcher.yaml"),
      """cncf:
        |  launcher:
        |    dev:
        |      dir: launcher-cncf
        |runtime:
        |  version: 0.5.0
        |""".stripMargin)
    val invoker = FakeLauncherDevInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker(), SbtRuntimeClasspathExporter, DevServerProcessManager.System, invoker)

    val code = launcher.run(Vector("launcher", "version"))

    _assert_equals(code, 0)
    _assert_equals(invoker.devDir, Some(launcherdevdir.toAbsolutePath.normalize))
    _assert_equals(invoker.args, Vector("launcher", "version"))
    _assert_equals(invoker.cwd, Some(paths.cwd.toAbsolutePath.normalize))
  }

  def launcherDevDirRejectsStaleDevelopmentClasspath(): Unit = _with_temp_paths { paths =>
    val launcherdevdir = paths.cwd.resolve("launcher-cncf")
    val staleclassdir = paths.cwd.resolve("stale-cncf-runtime-classes")
    Files.createDirectories(launcherdevdir)
    Files.createDirectories(staleclassdir)
    _write(launcherdevdir.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), staleclassdir.toString)

    val e = intercept[CncfException] {
      LauncherDevInvoker.System.invoke(launcherdevdir, Vector("launcher", "version"), paths.cwd)
    }

    e.getMessage.contains("does not contain cncf.launcher.CncfLauncherMain") shouldBe true
    e.getMessage.contains("run sbt --batch compile and cncf dev classpath") shouldBe true
  }

  def configSupportsAdditionalRdfNamespaces(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("conf").resolve("cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  version: 0.5.0
         |  catalog:
         |    url: ${paths.cwd.resolve("missing-runtime-catalog.yaml")}
         |textus:
         |  knowledge:
         |    rdf:
         |      current-prefix: acme
         |      namespace-prefixes:
         |        - acme
         |        - sm
         |      namespaces:
         |        acme: https://example.com/acme
         |        sm: https://www.simplemodeling.org
         |""".stripMargin)
    val config = LauncherConfig.load(paths)
    _assert_equals(config.textusKnowledgeRdfNodePrefix, Some("acme"))
    _assert_equals(config.textusKnowledgeRdfNamespacePrefixes, Some("acme,sm"))
    config.textusKnowledgeRdfNamespaces.contains("acme" -> "https://example.com/acme") shouldBe true
    config.textusKnowledgeRdfNamespaces.contains("sm" -> "https://www.simplemodeling.org") shouldBe true

    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    launcher.run(Vector("dev", "server"))
    invoker.lastArgs.contains("--textus.knowledge.rdf.node-prefix=acme") shouldBe true
    invoker.lastArgs.contains("--textus.knowledge.rdf.namespace-prefixes=acme,sm") shouldBe true
    invoker.lastArgs.contains("--textus.knowledge.rdf.namespaces.acme=https://example.com/acme") shouldBe true
    invoker.lastArgs.contains("--textus.knowledge.rdf.namespaces.sm=https://www.simplemodeling.org") shouldBe true
  }

  def configFileOptionOverridesProjectConfig(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("conf").resolve("cncf").resolve("launcher.yaml"),
      """runtime:
        |  version: 0.1.0
        |dev:
        |  port: 19500
        |""".stripMargin)
    _write(paths.cwd.resolve("etc").resolve("debug.yaml"),
      """runtime:
        |  version: 0.2.0
        |dev:
        |  port: 19600
        |""".stripMargin)
    val resolver = FakeResolver()
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, resolver, invoker)

    launcher.run(Vector("--config", "etc/debug.yaml", "dev", "server"))

    _assert_equals(resolver.resolvedClasspaths, Vector("0.2.0"))
    invoker.lastArgs.contains("--config") shouldBe false
    invoker.lastArgs.contains("etc/debug.yaml") shouldBe false
  }

  def workspaceRootConfigAppliesToNestedCwd(): Unit = _with_temp_paths { paths =>
    val workspace = paths.cwd
    val sample = workspace.resolve("samples").resolve("01-hello")
    _write(workspace.resolve(".cncf").resolve("launcher.yaml"),
      """runtime:
        |  version: root
        |  dev-dir: ../cncf-runtime
        |""".stripMargin)
    _write(workspace.resolve(".cncf").resolve("config.yaml"),
      """textus:
        |  knowledge:
        |    rdf:
        |      current-prefix: root
        |""".stripMargin)
    _write(sample.resolve(".cncf").resolve("launcher.yaml"),
      """runtime:
        |  version: sample
        |""".stripMargin)

    val config = LauncherConfig.load(paths.withCwd(sample))

    _assert_equals(config.runtimeVersion, Some("sample"))
    _assert_equals(config.runtimeDevDir, Some("../cncf-runtime"))
    config.cncfConfigFiles.exists(_.contains("work/.cncf/config.yaml")) shouldBe true
  }

  def launcherConfigControlsDevelopmentRuntime(): Unit = _with_temp_paths { paths =>
    Given("a launcher config contains disabled development runtime and launcher candidates")
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      """development:
        |  enabled: false
        |  launcher:
        |    dev-dir: ../candidate-launcher
        |  runtime:
        |    dev-dir: ../candidate-runtime
        |""".stripMargin)

    When("the launcher loads the disabled development configuration")
    val inert = LauncherConfig.load(paths, Vector.empty, Map.empty)

    Then("the development candidates are recorded but not activated")
    _assert_equals(inert.developmentEnabled, Some(false))
    _assert_equals(inert.launcherDevDir, None)
    _assert_equals(inert.runtimeDevDir, None)
    _assert_equals(inert.developmentLauncherDevDir, Some("../candidate-launcher"))
    _assert_equals(inert.developmentRuntimeDevDir, Some("../candidate-runtime"))

    When("development.enabled is changed to true in the same launcher config")
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      """development:
        |  enabled: true
        |  launcher:
        |    dev-dir: ../candidate-launcher
        |  runtime:
        |    dev-dir: ../candidate-runtime
        |""".stripMargin)
    val active = LauncherConfig.load(paths, Vector.empty, Map.empty)

    Then("the development launcher and runtime candidates become active")
    _assert_equals(active.developmentEnabled, Some(true))
    _assert_equals(active.launcherDevDir, Some("../candidate-launcher"))
    _assert_equals(active.runtimeDevDir, Some("../candidate-runtime"))

    When("the launcher section enables only the development launcher")
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      """development:
        |  enabled: false
        |  launcher:
        |    enabled: true
        |    dev-dir: ../candidate-launcher
        |  runtime:
        |    enabled: false
        |    dev-dir: ../candidate-runtime
        |""".stripMargin)
    val launcheronly = LauncherConfig.load(paths, Vector.empty, Map.empty)

    Then("the runtime remains published while the launcher delegates to its checkout")
    _assert_equals(launcheronly.launcherDevDir, Some("../candidate-launcher"))
    _assert_equals(launcheronly.runtimeDevDir, None)

    When("the runtime section enables only the development runtime")
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      """development:
        |  enabled: false
        |  launcher:
        |    enabled: false
        |    dev-dir: ../candidate-launcher
        |  runtime:
        |    enabled: true
        |    dev-dir: ../candidate-runtime
        |""".stripMargin)
    val runtimeonly = LauncherConfig.load(paths, Vector.empty, Map.empty)

    Then("the installed launcher selects only the runtime checkout")
    _assert_equals(runtimeonly.launcherDevDir, None)
    _assert_equals(runtimeonly.runtimeDevDir, Some("../candidate-runtime"))

    Given("the launcher development switch is enabled without a candidate directory")
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      """development:
        |  enabled: false
        |  launcher:
        |    enabled: true
        |  runtime:
        |    enabled: false
        |""".stripMargin)

    When("the incomplete launcher configuration is loaded")
    val missinglauncher = intercept[CncfException] {
      LauncherConfig.load(paths, Vector.empty, Map.empty)
    }

    Then("the missing launcher directory is reported deterministically")
    missinglauncher.getMessage should include("development.launcher.dev-dir is required")

    Given("the runtime development switch is enabled without a candidate directory")
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      """development:
        |  enabled: false
        |  launcher:
        |    enabled: false
        |  runtime:
        |    enabled: true
        |""".stripMargin)

    When("the incomplete runtime configuration is loaded")
    val missingruntime = intercept[CncfException] {
      LauncherConfig.load(paths, Vector.empty, Map.empty)
    }

    Then("the missing runtime directory is reported deterministically")
    missingruntime.getMessage should include("development.runtime.dev-dir is required")

    Given("both development switches are enabled without configured candidate directories")
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      """development:
        |  enabled: true
        |""".stripMargin)

    When("emergency environment overrides provide both directories")
    val emergency = LauncherConfig.load(paths, Vector.empty, Map(
      "CNCF_RUNTIME_DEV_DIR" -> "../emergency-runtime",
      "CNCF_LAUNCHER_DEV_DIR" -> "../emergency-launcher"
    ))

    Then("the explicit overrides satisfy the enabled development selections")
    _assert_equals(emergency.runtimeDevDir, Some("../emergency-runtime"))
    _assert_equals(emergency.launcherDevDir, Some("../emergency-launcher"))

    When("the removed environment activation flag is present")
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      """development:
        |  enabled: false
        |  launcher:
        |    dev-dir: ../candidate-launcher
        |  runtime:
        |    dev-dir: ../candidate-runtime
        |""".stripMargin)
    val legacyenvironment = LauncherConfig.load(paths, Vector.empty, Map("CNCF_USE_DEVELOPMENT" -> "true"))

    Then("the file switch remains authoritative")
    _assert_equals(legacyenvironment.launcherDevDir, None)
    _assert_equals(legacyenvironment.runtimeDevDir, None)

    When("a higher-priority project config disables globally enabled development candidates")
    _write(paths.cncfHome.resolve("launcher.yaml"),
      """development:
        |  enabled: true
        |  launcher:
        |    dev-dir: ../global-launcher
        |  runtime:
        |    dev-dir: ../global-runtime
        |""".stripMargin)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"), "development:\n  enabled: false\n")
    val disabledoverride = LauncherConfig.load(paths, Vector.empty, Map.empty)

    Then("the project switch disables inherited development directories")
    _assert_equals(disabledoverride.launcherDevDir, None)
    _assert_equals(disabledoverride.runtimeDevDir, None)

    When("explicit environment overrides are supplied")
    val env = LauncherConfig.load(paths, Vector.empty, Map(
      "CNCF_VERSION" -> "0.4.12-SNAPSHOT",
      "CNCF_RUNTIME_DEV_DIR" -> "../env-runtime",
      "CNCF_LAUNCHER_DEV_DIR" -> "../env-launcher"
    ))

    Then("explicit runtime and launcher development directories take precedence")
    _assert_equals(env.runtimeVersion, Some("0.4.12-SNAPSHOT"))
    _assert_equals(env.runtimeDevDir, Some("../env-runtime"))
    _assert_equals(env.launcherDevDir, Some("../env-launcher"))
  }

  def launcherConfigSupportsPropertiesAndConfFiles(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("etc").resolve("launcher.properties"),
      """runtime.version = 0.2.0
        |dev.port = 19601
        |""".stripMargin)
    _write(paths.cwd.resolve("etc").resolve("launcher.conf"),
      """dev.restart = true
        |cncf.config.file = etc/runtime-debug.yaml
        |""".stripMargin)
    val resolver = FakeResolver()
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, resolver, invoker)

    launcher.run(Vector("--config", "etc/launcher.properties", "--config", "etc/launcher.conf", "dev", "server"))

    _assert_equals(resolver.resolvedClasspaths, Vector("0.2.0"))
    invoker.lastArgs.exists(_.startsWith("--cncf.config.files=")) shouldBe true
    invoker.lastArgs.exists(_.contains("runtime-debug.yaml")) shouldBe true
  }

  def defaultRuntimeConfigFilesAreForwarded(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.globalRuntimeConfig, "textus:\n  global: true\n")
    _write(paths.projectRuntimeConfig, "textus:\n  project: true\n")
    _write(paths.projectLocalRuntimeConfig, "textus:\n  local: true\n")
    _write(paths.cwd.resolve("etc").resolve("runtime-extra.yaml"), "textus:\n  extra: true\n")
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    launcher.run(Vector("--cncf-config", "etc/runtime-extra.yaml", "dev", "server"))

    val configarg = invoker.lastArgs.find(_.startsWith("--cncf.config.files=")).getOrElse("")
    val values = configarg.stripPrefix("--cncf.config.files=").split(",").toVector
    _assert_equals(values, Vector(
      paths.globalRuntimeConfig.toAbsolutePath.normalize.toString,
      paths.projectRuntimeConfig.toAbsolutePath.normalize.toString,
      paths.projectLocalRuntimeConfig.toAbsolutePath.normalize.toString,
      paths.cwd.resolve("etc").resolve("runtime-extra.yaml").toAbsolutePath.normalize.toString
    ))
    invoker.lastArgs.contains("--cncf-config") shouldBe false
  }

  def configFileProjectDevSurvivesTargetCwdSwitch(): Unit = _with_temp_paths { paths =>
    val project = paths.cwd.resolve("blog")
    val classdir = project.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(project.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("etc").resolve("debug.yaml"),
      """runtime:
        |  version: 0.2.0
        |dev:
        |  project-dev: blog
        |  restart: true
        |""".stripMargin)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    launcher.run(Vector("--config", "etc/debug.yaml", "dev", "server"))

    invoker.lastArgs.contains(project.toAbsolutePath.normalize.toString) shouldBe true
    invoker.lastArgs.contains("--config") shouldBe false
    invoker.lastArgs.contains("etc/debug.yaml") shouldBe false
  }

  def cncfConfigOptionIsForwardedToRuntime(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("etc").resolve("runtime-debug.yaml"),
      """textus:
        |  knowledge:
        |    rdf:
        |      current-prefix: sm
        |""".stripMargin)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    launcher.run(Vector("--cncf-config", "etc/runtime-debug.yaml", "dev", "server"))

    invoker.lastArgs.exists(_.startsWith("--cncf.config.files=")) shouldBe true
    invoker.lastArgs.exists(_.contains("runtime-debug.yaml")) shouldBe true
    invoker.lastArgs.contains("--cncf-config") shouldBe false
    invoker.lastArgs.contains("etc/runtime-debug.yaml") shouldBe false
  }

  def configFileOptionRequiresExistingFile(): Unit = _with_temp_paths { paths =>
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker())
    val failed =
      try {
        launcher.run(Vector("--config", "etc/missing.conf", "runtime", "config", "show"))
        false
      } catch {
        case e: CncfException =>
          e.getMessage.contains("launcher config file not found") &&
            e.getMessage.contains("etc/missing.conf")
      }
    failed shouldBe true
  }

  def runtimeVersionPrecedence(): Unit = _with_temp_paths { paths =>
    val store = RuntimeVersionStore(paths)
    val config = LauncherConfig(runtimeVersion = Some("0.1.0"))
    _assert_equals(store.current(None, config), "0.1.0")
    store.useGlobal("0.2.0")
    _assert_equals(store.current(None, config), "0.2.0")
    store.useProject("0.3.0")
    _assert_equals(store.current(None, config), "0.3.0")
    _assert_equals(store.current(Some("0.4.0"), config), "0.4.0")
  }

  def runtimeUseWritesExpectedFiles(): Unit = _with_temp_paths { paths =>
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker())
    launcher.run(Vector("runtime", "use", "latest"))
    _assert_equals(Files.readString(paths.globalVersion).trim, "latest")
    launcher.run(Vector("runtime", "use", "0.2.0", "--global"))
    launcher.run(Vector("runtime", "use", "0.3.0", "--project"))
    _assert_equals(Files.readString(paths.globalVersion).trim, "0.2.0")
    _assert_equals(Files.readString(paths.projectVersion).trim, "0.3.0")
  }

  def runtimeUseAutoSelectsProjectWhenCncfDirectoryExists(): Unit = _with_temp_paths { paths =>
    Files.createDirectories(paths.cwd.resolve(".cncf"))
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker())
    launcher.run(Vector("runtime", "use", "latest"))
    _assert_equals(Files.readString(paths.projectVersion).trim, "latest")
    Files.isRegularFile(paths.globalVersion) shouldBe false
  }

  def installCliWritesDevelopmentCommand(): Unit = _with_temp_paths { paths =>
    _write(
      paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"launcher:\n  dev-dir: ${paths.cwd.resolve("launcher-dev").toAbsolutePath.normalize}\nruntime:\n  version: 0.4.12\n"
    )
    val launcher = new CncfLauncher(
      paths,
      FakeResolver(),
      FakeInvoker(),
      environment = Map("CNCF_LAUNCHER_DEV_DELEGATED" -> "1")
    )
    val code = launcher.run(Vector(
      "install-cli",
      "sanpomap",
      "--project-dev",
      ".",
      "--component-dev-dir",
      "../textus-georesolver"
    ))

    _assert_equals(code, 0)
    val command = paths.home.resolve("bin").resolve("sanpomap-dev")
    val script = Files.readString(command)
    script.contains(s"fixed_target='${paths.cwd.toAbsolutePath.normalize}'") shouldBe true
    script.contains("operation_prefix=''") shouldBe true
    script.contains("runtime_version='0.4.12'") shouldBe true
    script.contains("runtime_dev_dir=''") shouldBe true
    script.contains(s"launcher_dev_dir='${paths.cwd.resolve("launcher-dev").toAbsolutePath.normalize}'") shouldBe true
    script.contains("export CNCF_LAUNCHER_DEV_DIR=\"$launcher_dev_dir\"") shouldBe true
    script.contains("component_dev_dirs=(") shouldBe true
    script.contains(s"'${paths.cwd.resolve("../textus-georesolver").toAbsolutePath.normalize}'") shouldBe true
    script.contains("component_dev_args+=(\"--component-dev-dir\" \"$dir\")") shouldBe true
    script.contains("exec cncf \"${cncf_args[@]}\" \"$fixed_target\" command \"${component_dev_args[@]}\" \"${command_args[@]}\"") shouldBe true
    Files.isExecutable(command) shouldBe true
  }

  def installCliPinsDevelopmentRuntimeWithoutCatalog(): Unit = _with_temp_paths { paths =>
    val runtimeproject = paths.cwd.resolve("../cncf-runtime").normalize
    val georesolver = paths.cwd.resolve("../textus-georesolver").normalize
    val toolchainrunner = paths.cwd.resolve("../textus-toolchain-runner").normalize
    _write(runtimeproject.resolve("build.sbt"), "ThisBuild / version := \"0.5.1-SNAPSHOT\"\n")
    _write(paths.cwd.resolve("project.yaml"), _project_yaml("0.5.1-SNAPSHOT", Vector("0.5.1-SNAPSHOT")))
    _write(georesolver.resolve("project.yaml"), _project_yaml("0.5.0", Vector("0.5.0")))
    _write(toolchainrunner.resolve("project.yaml"), _project_yaml("0.5.0", Vector("0.5.0")))
    val launcher = new CncfLauncher(
      paths,
      FakeResolver(),
      FakeInvoker(),
      environment = Map("CNCF_LAUNCHER_DEV_DELEGATED" -> "1")
    )

    val code = launcher.run(Vector(
      "--runtime-dev-dir", runtimeproject.toString,
      "install-cli",
      "sanpomap",
      "--project-dev", ".",
      "--component-dev-dir", georesolver.toString,
      "--component-dev-dir", toolchainrunner.toString
    ))

    _assert_equals(code, 0)
    val script = Files.readString(paths.home.resolve("bin").resolve("sanpomap-dev"))
    script.contains("runtime_version=''") shouldBe true
    script.contains(s"runtime_dev_dir='${runtimeproject.toAbsolutePath.normalize}'") shouldBe true
    script.contains(s"'${georesolver.toAbsolutePath.normalize}'") shouldBe true
    script.contains(s"'${toolchainrunner.toAbsolutePath.normalize}'") shouldBe true
  }

  def installCliRejectsIncompatibleDevelopmentRuntime(): Unit = _with_temp_paths { paths =>
    val runtimeproject = paths.cwd.resolve("../cncf-runtime").normalize
    _write(runtimeproject.resolve("build.sbt"), "ThisBuild / version := \"0.5.1-SNAPSHOT\"\n")
    _write(paths.cwd.resolve("project.yaml"), _project_yaml("0.5.2", Vector("0.5.2")))
    val launcher = new CncfLauncher(
      paths,
      FakeResolver(),
      FakeInvoker(),
      environment = Map("CNCF_LAUNCHER_DEV_DELEGATED" -> "1")
    )

    val failure = try {
      launcher.run(Vector(
        "--runtime-dev-dir", runtimeproject.toString,
        "install-cli",
        "sanpomap",
        "--project-dev", "."
      ))
      None
    } catch {
      case e: CncfException => Some(e)
    }

    failure.map(_.getMessage) shouldBe Some("CNCF runtime 0.5.1-SNAPSHOT is not compatible with component requirements: main-target")
    Files.exists(paths.home.resolve("bin").resolve("sanpomap-dev")) shouldBe false
  }

  def textusControlCenterRegistrationLifecycle(): Unit = _with_temp_paths { paths =>
    Given("a CNCF launcher with opt-in Textus Control Center registration")
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      """textus-control-center:
        |  registration:
        |    enabled: true
        |    endpoint: https://admin.example.test/rest/v1/textus-control-center/subsystem-inventory
        |    token-env: TEXTUS_ADMIN_REGISTRATION_TOKEN
        |    timeout: 2s
        |    heartbeat-interval: 30s
        |    host-label: acceptance
        |    base-url: https://subsystem.example.test
        |""".stripMargin)
    _write(paths.cwd.resolve("project.yaml"),
      """project:
        |  name: textus-current
        |  component:
        |    name: current-component
        |""".stripMargin)
    val reporter = FakeCncfTextusControlCenterRegistrationReporter()
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(
      paths,
      FakeResolver(),
      invoker,
      environment = Map("TEXTUS_ADMIN_REGISTRATION_TOKEN" -> "secret-token"),
      registrationreporter = reporter
    )

    When("the target-first canonical server command completes")
    val targetfirstcode = launcher.run(Vector("textus-registration:0.1.0", "server"))

    Then("the launcher reports the resolved target, version, and only the selected credential")
    _assert_equals(targetfirstcode, 0)
    _assert_equals(reporter.starts.size, 1)
    _assert_equals(reporter.closes, 1)
    _assert_equals(reporter.starts.head._1.target, "textus-registration")
    _assert_equals(reporter.starts.head._1.executionMode, "repository")
    _assert_equals(reporter.starts.head._1.developmentDirectory, None)
    _assert_equals(reporter.starts.head._1.subsystemName, Some("textus-registration"))
    _assert_equals(reporter.starts.head._1.subsystemVersion, Some("0.1.0"))
    _assert_equals(reporter.starts.head._2, Some("secret-token"))
    invoker.lastArgs.head shouldBe "server"

    When("the current-project canonical server command completes")
    val currentprojectcode = launcher.run(Vector("server", "--textus.server.port=18014"))

    Then("it reports the current component development directory through the same lifecycle")
    _assert_equals(currentprojectcode, 0)
    _assert_equals(reporter.starts.size, 2)
    _assert_equals(reporter.closes, 2)
    _assert_equals(reporter.starts(1)._1.target, "current-component")
    _assert_equals(reporter.starts(1)._1.artifactId, Some("textus-current"))
    _assert_equals(reporter.starts(1)._1.executionMode, "development")
    _assert_equals(reporter.starts(1)._1.developmentDirectory, Some(paths.cwd.toAbsolutePath.normalize.toString))
    _assert_equals(reporter.starts(1)._1.subsystemName, Some("current-component"))

    And("registration setup failure does not prevent canonical server startup")
    val outageinvoker = FakeInvoker()
    val outage = new CncfTextusControlCenterRegistrationOutageReporter
    val outagelauncher = new CncfLauncher(
      paths,
      FakeResolver(),
      outageinvoker,
      environment = Map("TEXTUS_ADMIN_REGISTRATION_TOKEN" -> "secret-token"),
      registrationreporter = outage
    )
    val outagecode = outagelauncher.run(Vector("textus-registration:0.1.0", "server"))
    _assert_equals(outagecode, 0)
    outageinvoker.lastArgs.head shouldBe "server"
  }

  def standaloneControlCenterLocatorLifecycle(): Unit = _with_temp_paths { paths =>
    Given("a machine-local standalone Control Center locator and owner-only launcher token")
    val root = paths.cncfHome.resolve("textus-control-center")
    val token = root.resolve("credentials").resolve("launcher-registration.token")
    _write(token, "standalone-token\n")
    Files.setPosixFilePermissions(token, java.util.Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ, java.nio.file.attribute.PosixFilePermission.OWNER_WRITE))
    _write(root.resolve("standalone-locator.yaml"),
      """schemaVersion: 1
        |profile: standalone
        |scopeId: scope-test
        |installationId: standalone-test
        |endpoint: http://127.0.0.1:18013/rest/v1/textus-control-center/subsystem-inventory
        |credentialRef: credentials/launcher-registration.token
        |timeout: 2s
        |heartbeatInterval: 30s
        |hostLabel: standalone-test
        |""".stripMargin)
    val reporter = FakeCncfTextusControlCenterRegistrationReporter()
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker, registrationreporter = reporter)

    When("both canonical CNCF server forms run without inline registration configuration")
    val currentprojectcode = launcher.run(Vector("server", "--textus.server.port=18014"))
    val targetfirstcode = launcher.run(Vector("textus-registration:0.1.0", "server"))

    Then("the locator credential is used for both lifecycle sessions")
    _assert_equals(currentprojectcode, 0)
    _assert_equals(targetfirstcode, 0)
    _assert_equals(reporter.starts.size, 2)
    reporter.starts.map(_._2) shouldBe Vector(Some("standalone-token"), Some("standalone-token"))
    reporter.configs.head.baseUrl shouldBe "http://127.0.0.1:18014"
    _assert_equals(reporter.closes, 2)

    When("the shared credential is no longer owner-readable and owner-writable only")
    Files.setPosixFilePermissions(token, java.util.Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ))
    val invalidlocatorcode = launcher.run(Vector("server"))

    Then("the invalid locator is ignored without changing server startup")
    _assert_equals(invalidlocatorcode, 0)
    _assert_equals(reporter.starts.size, 2)

    When("an explicit registration disable is configured")
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      """textus-control-center:
        |  registration:
        |    enabled: false
        |""".stripMargin)
    val disabledlauncher = new CncfLauncher(paths, FakeResolver(), FakeInvoker(), registrationreporter = reporter)
    val disabledcode = disabledlauncher.run(Vector("server"))

    Then("the machine locator is not used")
    _assert_equals(disabledcode, 0)
    _assert_equals(reporter.starts.size, 2)
  }

  def textusControlCenterRegistrationHttpLifecycle(): Unit = {
    Given("a reachable Textus Control Center automatic REST endpoint")
    val requests = new ConcurrentLinkedQueue[(String, String)]()
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        requests.add(exchange.getRequestURI.toString -> exchange.getRequestHeaders.getFirst("Authorization"))
        exchange.sendResponseHeaders(204, -1)
        exchange.close()
      }
    })
    server.start()
    try {
      val config = CncfTextusControlCenterRegistrationConfig(
        endpoint = s"http://127.0.0.1:${server.getAddress.getPort}/rest/v1/textus-control-center/subsystem-inventory",
        tokenEnv = "TEXTUS_ADMIN_REGISTRATION_TOKEN",
        timeout = java.time.Duration.ofSeconds(1),
        heartbeatInterval = java.time.Duration.ofSeconds(30),
        hostLabel = "acceptance",
        baseUrl = "https://subsystem.example.test"
      )
      val report = CncfTextusControlCenterRegistrationReport(
        instanceId = "cncf-registration-http-spec",
        target = "textus-registration",
        artifactId = Some("textus-registration"),
        executionMode = "repository",
        developmentDirectory = None,
        subsystemName = Some("textus-registration"),
        subsystemVersion = Some("0.1.0"),
        runtimeVersion = "0.5.0",
        startedAt = java.time.Instant.parse("2026-07-18T00:00:00Z")
      )

      When("the reporter starts and closes one server registration session")
      val session = CncfTextusControlCenterRegistrationReporter.System.start(config, report, Some("test-token"))
      session.close()
      session.close()

      Then("it sends one register and one deregister operation with the configured bearer credential")
      _assert_equals(requests.size, 2)
      requests.iterator.asScala.map(_._1).exists(_.contains("register-subsystem")) shouldBe true
      requests.iterator.asScala.map(_._1).exists(_.contains("deregister-subsystem")) shouldBe true
      requests.iterator.asScala.forall(_._2 == "Bearer test-token") shouldBe true
      requests.iterator.asScala.forall { case (path, _) => path.contains("instanceId=cncf-registration-http-spec") } shouldBe true
      requests.iterator.asScala.forall { case (path, _) => path.contains("artifactId=textus-registration") } shouldBe true
      requests.iterator.asScala.forall { case (path, _) => path.contains("?protocolVersion=1&instanceId=") } shouldBe true

      Given("registration without an explicit public base URL")
      requests.clear()
      val propertykey = "textus.server.bound-base-url"
      sys.props.remove(propertykey)

      When("CNCF publishes the endpoint after the server has bound")
      val dynamicsession = CncfTextusControlCenterRegistrationReporter.System.start(config.copy(baseUrl = ""), report, Some("test-token"))
      sys.props.update(propertykey, "http://127.0.0.1:38000")
      val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2)
      while (requests.size < 1 && System.nanoTime() < deadline)
        Thread.sleep(10L)
      dynamicsession.close()
      val closedeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2)
      while (requests.size < 2 && System.nanoTime() < closedeadline)
        Thread.sleep(10L)
      sys.props.remove(propertykey)

      Then("registration uses the bound endpoint rather than a guessed default port")
      _assert_equals(requests.size, 2)
      requests.iterator.asScala.forall(_._1.contains("baseUrl=http%3A%2F%2F127.0.0.1%3A38000")) shouldBe true

      And("the previous canonical textus-admin key remains readable during migration")
      val legacyvalues = LauncherConfigParser.parse(
        Path.of("legacy-launcher.yaml"),
        """textus-admin:
          |  registration:
          |    enabled: true
          |    endpoint: https://admin.example.test/inventory
          |    token-env: TOKEN
          |    host-label: legacy
          |""".stripMargin
      )
      CncfTextusControlCenterRegistrationConfig.fromParsed(legacyvalues).map(_.hostLabel) shouldBe Some("legacy")
    } finally {
      sys.props.remove("textus.server.bound-base-url")
      server.stop(0)
    }
  }

  def textusControlCenterRegistrationHttpFailureIsolation(): Unit = {
    Given("a Textus Control Center endpoint that rejects a launcher registration")
    val requests = scala.collection.mutable.ArrayBuffer.empty[String]
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        requests += exchange.getRequestURI.toString
        exchange.sendResponseHeaders(401, -1)
        exchange.close()
      }
    })
    server.start()
    try {
      val config = CncfTextusControlCenterRegistrationConfig(
        endpoint = s"http://127.0.0.1:${server.getAddress.getPort}/rest/v1/textus-control-center/subsystem-inventory",
        tokenEnv = "TEXTUS_ADMIN_REGISTRATION_TOKEN",
        timeout = java.time.Duration.ofSeconds(1),
        heartbeatInterval = java.time.Duration.ofSeconds(30),
        hostLabel = "acceptance",
        baseUrl = "https://subsystem.example.test"
      )
      val report = CncfTextusControlCenterRegistrationReport(
        instanceId = "cncf-registration-http-failure-spec",
        target = "textus-registration",
        artifactId = Some("textus-registration"),
        executionMode = "repository",
        developmentDirectory = None,
        subsystemName = Some("textus-registration"),
        subsystemVersion = Some("0.1.0"),
        runtimeVersion = "0.5.0",
        startedAt = java.time.Instant.parse("2026-07-18T00:00:00Z")
      )

      When("the reporter receives authorization rejection for registration and deregistration")
      val session = CncfTextusControlCenterRegistrationReporter.System.start(config, report, Some("rejected-token"))
      session.close()

      Then("it performs exactly one bounded request for each lifecycle transition without a retry loop")
      _assert_equals(requests.size, 2)
      requests.exists(_.contains("register-subsystem")) shouldBe true
      requests.exists(_.contains("deregister-subsystem")) shouldBe true
    } finally {
      server.stop(0)
    }
  }

  def executeTargetFirstDelegatesToRuntime(): Unit = _with_temp_paths { paths =>
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"), "runtime:\n  version: 0.4.12\n")
    val resolver = FakeResolver()
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, resolver, invoker)

    val code = launcher.run(Vector("textus-sanpomap:1", "command", "validate-presentation", "--format", "yaml"))

    _assert_equals(code, 0)
    _assert_equals(resolver.resolvedClasspaths, Vector("0.4.12"))
    _assert_equals(invoker.lastArgs, Vector("command", "--textus.component=textus-sanpomap", "--textus.component.version=1", "validate-presentation", "--format", "yaml"))
  }

  def serverExecutionDelegatesDefaultPortResolutionToRuntime(): Unit = _with_temp_paths { paths =>
    Given("target-first CAR and SAR server invocations without an explicit server port")
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"), "runtime:\n  version: 0.4.12\n")
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    When("the launcher delegates the CAR invocation to CNCF")
    val carcode = launcher.run(Vector("textus-sanpomap:1", "server"))

    Then("CAR activation is forwarded without a launcher-owned port override")
    _assert_equals(carcode, 0)
    _assert_equals(invoker.lastArgs, Vector("server", "--textus.component=textus-sanpomap", "--textus.component.version=1"))
    invoker.lastArgs.exists(_.startsWith("--textus.server.port=")) shouldBe false
    invoker.lastArgs.exists(_.startsWith("--cncf.server.port=")) shouldBe false

    When("the launcher delegates the SAR invocation to CNCF")
    val sarcode = launcher.run(Vector("textus-platform.sar", "server"))

    Then("SAR activation is forwarded without a launcher-owned port override")
    _assert_equals(sarcode, 0)
    _assert_equals(invoker.lastArgs, Vector("server", "--subsystem-file=textus-platform.sar"))
    invoker.lastArgs.exists(_.startsWith("--textus.server.port=")) shouldBe false
    invoker.lastArgs.exists(_.startsWith("--cncf.server.port=")) shouldBe false
  }

  def runtimeCatalogParseAndSelectorResolution(): Unit = {
    val catalog = RuntimeCatalog.parse(_catalog_text)
    _assert_equals(catalog.resolve("recommended").version, "0.2.0")
    _assert_equals(catalog.resolve("latest").version, "0.2.0")
    _assert_equals(catalog.resolve("latest-stable").version, "0.2.0")
    _assert_equals(catalog.resolve("latest-snapshot").version, "0.3.0-SNAPSHOT")
    _assert_equals(catalog.resolve("newest").version, "0.3.0-SNAPSHOT")
    _assert_equals(catalog.baseProvided, Vector("org.goldenport:goldenport-cncf_3", "org.typelevel:cats-core_3"))
    val disabled =
      try {
        catalog.resolve("0.1.0")
        false
      } catch {
        case e: CncfException => e.getMessage.contains("disabled")
      }
    disabled shouldBe true
  }

  def runtimeCatalogCommands(): Unit = _with_temp_paths { paths =>
    val catalogfile = paths.cwd.resolve("runtime-catalog.yaml")
    _write(catalogfile, _catalog_text)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: $catalogfile
         |""".stripMargin)
    val launcher = new CncfLauncher(paths, CoursierCncfRuntimeResolver("false"), FakeInvoker())
    launcher.run(Vector("runtime", "refresh"))
    Files.isRegularFile(paths.runtimeCatalog) shouldBe true
    launcher.run(Vector("runtime", "remote", "list"))
    launcher.run(Vector("runtime", "catalog", "show"))
    launcher.run(Vector("runtime", "channels"))
    launcher.run(Vector("runtime", "use", "recommended", "--project"))
    _assert_equals(Files.readString(paths.projectVersion).trim, "recommended")
    launcher.run(Vector("runtime", "current"))
  }

  def runtimeCurrentWarnsWhenCachedRecommendedIsStale(): Unit = _with_temp_paths { paths =>
    val remotecatalog = paths.cwd.resolve("runtime-catalog.yaml")
    _write(paths.runtimeCatalog, _catalog_text)
    _write(remotecatalog, _catalog_text.replace("recommended: 0.2.0", "recommended: 0.3.0-SNAPSHOT"))
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: $remotecatalog
         |""".stripMargin)
    val launcher = new CncfLauncher(paths, CoursierCncfRuntimeResolver("false"), FakeInvoker())

    val (code, stdout, stderr) = _capture_stdout_stderr {
      launcher.run(Vector("runtime", "current"))
    }

    _assert_equals(code, 0)
    _assert_equals(stdout.trim, "0.2.0")
    stderr.contains("cached CNCF runtime catalog resolves recommended to 0.2.0") shouldBe true
    stderr.contains("remote catalog resolves it to 0.3.0-SNAPSHOT") shouldBe true
    stderr.contains("cncf runtime refresh") shouldBe true
  }

  def runtimeDescriptorCommands(): Unit = _with_temp_paths { paths =>
    val catalogfile = paths.cwd.resolve("runtime-catalog.yaml")
    _write(catalogfile, _catalog_text)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: $catalogfile
         |""".stripMargin)
    val launcher = new CncfLauncher(paths, CoursierCncfRuntimeResolver("false"), FakeInvoker())
    val (_, descriptor) = _capture_stdout {
      launcher.run(Vector("runtime", "descriptor", "--format", "yaml"))
    }
    descriptor.contains("runtime: cncf") shouldBe true
    descriptor.contains("version: 0.2.0") shouldBe true
    descriptor.contains("module: org.goldenport:goldenport-cncf_3:0.2.0") shouldBe true
    descriptor.contains("baseProvided:") shouldBe true
    descriptor.contains("org.typelevel:cats-core_3") shouldBe true

    val (_, baseprovided) = _capture_stdout {
      launcher.run(Vector("runtime", "base-provided", "--format=yaml"))
    }
    baseprovided.contains("baseProvided:") shouldBe true
    baseprovided.contains("org.goldenport:goldenport-cncf_3") shouldBe true
  }

  def runtimeDescriptorPrefersRuntimeJarDescriptor(): Unit = _with_temp_paths { paths =>
    val catalogfile = paths.cwd.resolve("runtime-catalog.yaml")
    val jar = paths.cwd.resolve("goldenport-cncf.jar")
    _write(catalogfile, _catalog_text)
    _write_zip(
      jar,
      "META-INF/cncf/runtime.yaml",
      """schemaVersion: 1
        |runtime: cncf
        |version: 0.2.0
        |scalaBinaryVersion: "3"
        |module: org.goldenport:goldenport-cncf_3:0.2.0
        |baseProvided:
        |  - org.goldenport:goldenport-cncf_3
        |  - org.typelevel:spire_3
        |""".stripMargin
    )
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: $catalogfile
         |""".stripMargin)
    val launcher = new CncfLauncher(paths, FakeResolver(Some(Vector(jar))), FakeInvoker())

    val (_, descriptor) = _capture_stdout {
      launcher.run(Vector("runtime", "descriptor"))
    }
    val (_, baseprovided) = _capture_stdout {
      launcher.run(Vector("runtime", "base-provided"))
    }

    descriptor.contains("org.typelevel:spire_3") shouldBe true
    descriptor.contains("org.typelevel:cats-core_3") shouldBe false
    baseprovided.contains("org.typelevel:spire_3") shouldBe true
    baseprovided.contains("org.typelevel:cats-core_3") shouldBe false
  }

  def devParser(): Unit = {
    val server = CncfCommandParser.parse(Vector(
      "--runtime", "0.4.7",
      "--runtime-dev-dir", "/tmp/cncf",
      "dev", "server",
      "--project-dev", "/tmp/blog",
      "--port", "19599",
      "--component-dev-dir", "../account",
      "--repository-dir", "repository.d"
    )).asInstanceOf[CncfCommand.Dev.Server]
    _assert_equals(server.options.runtimeVersion, Some("0.4.7"))
    _assert_equals(server.options.runtimeSelectionPolicy, None)
    _assert_equals(server.options.target, CncfCommand.DevTarget.ProjectDev(Some("/tmp/blog")))
    _assert_equals(server.options.runtimeDevDir, Some("/tmp/cncf"))
    _assert_equals(server.options.port, Some("19599"))
    _assert_equals(server.options.executionProfile, None)
    _assert_equals(server.options.componentDevDirs, Vector("../account"))
    _assert_equals(server.options.runtimeArgs, Vector("--repository-dir", "repository.d"))

    val command = CncfCommandParser.parse(Vector("dev", "command", "blog.post.search", "limit=10"))
      .asInstanceOf[CncfCommand.Dev.Command]
    _assert_equals(command.operation, "blog.post.search")
    _assert_equals(command.args, Vector("limit=10"))

    val selection = CncfCommandParser.parse(Vector("--runtime-selection=tested-latest", "dev", "server"))
      .asInstanceOf[CncfCommand.Dev.Server]
    _assert_equals(selection.options.runtimeSelectionPolicy, Some(RuntimeSelectionPolicy.TestedLatest))
    val latestselection = CncfCommandParser.parse(Vector("--runtime-selection=latest", "dev", "server"))
      .asInstanceOf[CncfCommand.Dev.Server]
    _assert_equals(latestselection.options.runtimeSelectionPolicy, Some(RuntimeSelectionPolicy.LatestCompatible))

    val emulation = CncfCommandParser.parse(Vector("dev", "server-emulation", "blog.component.search"))
      .asInstanceOf[CncfCommand.Dev.ServerEmulation]
    _assert_equals(emulation.args, Vector("blog.component.search"))
    val profile = CncfCommandParser.parse(Vector("dev", "server", "--profile", "local-persistent"))
      .asInstanceOf[CncfCommand.Dev.Server]
    _assert_equals(profile.options.executionProfile, Some(CncfCommand.DevExecutionProfile.LocalPersistent))
  }

  def devServerParserSupportsProcessManagementOptions(): Unit = {
    val server = CncfCommandParser.parse(Vector("dev", "server", "--stop-existing", "--force-existing"))
      .asInstanceOf[CncfCommand.Dev.Server]
    _assert_equals(server.options.stopExisting, true)
    _assert_equals(server.options.forceExisting, true)

    val restart = CncfCommandParser.parse(Vector("dev", "server", "--restart"))
      .asInstanceOf[CncfCommand.Dev.Server]
    _assert_equals(restart.options.stopExisting, true)

    val stop = CncfCommandParser.parse(Vector("dev", "stop", "--project-dev", "/tmp/blog", "--port", "19532", "--force-existing"))
      .asInstanceOf[CncfCommand.Dev.Stop]
    _assert_equals(stop.options.target, CncfCommand.DevTarget.ProjectDev(Some("/tmp/blog")))
    _assert_equals(stop.options.port, Some("19532"))
    _assert_equals(stop.options.forceExisting, true)
  }

  def devServerRewritesToCncfArgs(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("scala-3.3.7").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  version: 0.5.0
         |  catalog:
         |    url: ${paths.cwd.resolve("missing-runtime-catalog.yaml")}
         |dev:
         |  port: 19600
         |  componentDevDirs:
         |    - ../account
         |textus-control-center:
         |  registration:
         |    enabled: true
         |    endpoint: https://admin.example.test/rest/v1/textus-control-center/subsystem-inventory
         |    token-env: TEXTUS_ADMIN_REGISTRATION_TOKEN
         |    host-label: acceptance
         |    base-url: https://subsystem.example.test
         |""".stripMargin)
    Files.createDirectories(paths.cwd.getParent.resolve("account"))
    _write(paths.cwd.getParent.resolve("account").resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    val invoker = FakeInvoker()
    val reporter = FakeCncfTextusControlCenterRegistrationReporter()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker, registrationreporter = reporter)
    launcher.run(Vector("dev", "server"))
    _assert_equals(invoker.lastArgs.take(4), Vector(
      "--component-dev-dir",
      paths.cwd.toString,
      "--component-dev-dir",
      paths.cwd.getParent.resolve("account").toAbsolutePath.normalize.toString
    ))
    invoker.lastArgs.contains("server") shouldBe true
    invoker.lastClasspath.contains(classdir) shouldBe true
    _assert_equals(reporter.starts, Vector.empty)
  }

  def devServerWritesStateDuringInvocation(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    val processmanager = FakeDevServerProcessManager(1234L)
    val invoker = FakeInvoker()
    invoker.onInvoke = () => {
      val pidfile = DevSupport.devServerPidFile(paths.cwd)
      val jsonfile = DevSupport.devServerJsonFile(paths.cwd)
      Files.isRegularFile(pidfile) shouldBe true
      Files.isRegularFile(jsonfile) shouldBe true
      _assert_equals(Files.readString(pidfile).trim, "1234")
      val json = Files.readString(jsonfile)
      json.contains(""""port": "19601"""") shouldBe true
      json.contains(""""project": """) shouldBe true
    }
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker, SbtRuntimeClasspathExporter, processmanager)

    launcher.run(Vector("dev", "server", "--port", "19601"))

    Files.exists(DevSupport.devServerPidFile(paths.cwd)) shouldBe false
    Files.exists(DevSupport.devServerJsonFile(paths.cwd)) shouldBe false
  }

  def devServerRejectsAliveExistingState(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write_dev_server_state(paths.cwd, 2222L, "19602")
    val processmanager = FakeDevServerProcessManager(3333L)
    processmanager.alive = Set(2222L)
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker(), SbtRuntimeClasspathExporter, processmanager)
    val failed =
      try {
        launcher.run(Vector("dev", "server", "--port", "19602"))
        false
      } catch {
        case e: CncfException => e.getMessage.contains("dev server already running") && e.getMessage.contains("--restart")
      }
    failed shouldBe true
  }

  def devServerStopExistingBeforeInvocation(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write_dev_server_state(paths.cwd, 2223L, "19603")
    val processmanager = FakeDevServerProcessManager(3334L)
    processmanager.alive = Set(2223L)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker, SbtRuntimeClasspathExporter, processmanager)

    launcher.run(Vector("dev", "server", "--port", "19603", "--stop-existing"))

    _assert_equals(processmanager.gracefulStopped, Vector(2223L))
    invoker.lastArgs.contains("server") shouldBe true
  }

  def devStopStopsExistingWithoutInvocation(): Unit = _with_temp_paths { paths =>
    _write_dev_server_state(paths.cwd, 2224L, "19604")
    val processmanager = FakeDevServerProcessManager(3335L)
    processmanager.alive = Set(2224L)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker, SbtRuntimeClasspathExporter, processmanager)

    launcher.run(Vector("dev", "stop", "--port", "19604"))

    _assert_equals(processmanager.gracefulStopped, Vector(2224L))
    _assert_equals(invoker.lastArgs, Vector.empty)
    Files.exists(DevSupport.devServerJsonFile(paths.cwd)) shouldBe false
  }

  def devStopUsesRecordedPortWhenPortIsOmitted(): Unit = _with_temp_paths { paths =>
    _write_dev_server_state(paths.cwd, 2227L, "19607")
    val processmanager = FakeDevServerProcessManager(3339L)
    processmanager.alive = Set(2227L)
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker(), SbtRuntimeClasspathExporter, processmanager)

    launcher.run(Vector("dev", "stop"))

    _assert_equals(processmanager.gracefulStopped, Vector(2227L))
  }

  def devServerForceStopsAfterGracefulFailure(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write_dev_server_state(paths.cwd, 2225L, "19605")
    val processmanager = FakeDevServerProcessManager(3336L)
    processmanager.alive = Set(2225L)
    processmanager.gracefulSucceeds = false
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker(), SbtRuntimeClasspathExporter, processmanager)

    launcher.run(Vector("dev", "server", "--port", "19605", "--stop-existing", "--force-existing"))

    _assert_equals(processmanager.gracefulStopped, Vector(2225L))
    _assert_equals(processmanager.forceStopped, Vector(2225L))
  }

  def devServerRequiresForceForAmbiguousAliveState(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(DevSupport.devServerPidFile(paths.cwd), "2226\n")
    val processmanager = FakeDevServerProcessManager(3338L)
    processmanager.alive = Set(2226L)
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker(), SbtRuntimeClasspathExporter, processmanager)
    val failed =
      try {
        launcher.run(Vector("dev", "server", "--port", "19606", "--stop-existing"))
        false
      } catch {
        case e: CncfException => e.getMessage.contains("state is ambiguous") && e.getMessage.contains("--force-existing")
      }

    failed shouldBe true
  }

  def devServerRejectsPidReuseWithoutForce(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write_dev_server_state(paths.cwd, 2228L, "19608", java.time.Instant.parse("2026-05-24T00:00:00Z"))
    val processmanager = FakeDevServerProcessManager(3340L)
    processmanager.alive = Set(2228L)
    processmanager.processStarts = Map(2228L -> java.time.Instant.parse("2026-05-24T01:00:00Z"))
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker(), SbtRuntimeClasspathExporter, processmanager)
    val failed =
      try {
        launcher.run(Vector("dev", "server", "--port", "19608", "--stop-existing"))
        false
      } catch {
        case e: CncfException => e.getMessage.contains("state is ambiguous") && e.getMessage.contains("--force-existing")
      }

    failed shouldBe true
  }

  def devServerProfileAddsLocalPersistentSqliteArgs(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    launcher.run(Vector("dev", "server", "--profile", "local-persistent"))

    val sqlitepath = paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime.sqlite").toAbsolutePath.normalize.toString
    invoker.lastArgs.contains(s"--textus.datastore.sqlite.path=$sqlitepath") shouldBe true
    invoker.lastArgs.contains(s"--cncf.datastore.sqlite.path=$sqlitepath") shouldBe true
    invoker.lastArgs.contains("--textus.datastore.sqlite.normalize-column-names=true") shouldBe true
    Files.isDirectory(paths.cwd.resolve("target").resolve("cncf.d")) shouldBe true
  }

  def devConfigCanSelectExecutionProfile(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      """dev:
        |  profile: local-persistent
        |""".stripMargin)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    launcher.run(Vector("dev", "server"))

    invoker.lastArgs.exists(_.startsWith("--textus.datastore.sqlite.path=")) shouldBe true
  }

  def devServerUsesRuntimeDevelopmentDirectory(): Unit = _with_temp_paths { paths =>
    val appclassdir = paths.cwd.resolve("target").resolve("scala-3.3.7").resolve("classes")
    val runtimeproject = paths.cwd.resolve("cncf-runtime")
    val runtimeclassdir = runtimeproject.resolve("target").resolve("scala-3.3.7").resolve("classes")
    Files.createDirectories(appclassdir)
    Files.createDirectories(runtimeclassdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), appclassdir.toString)
    _write(runtimeproject.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), runtimeclassdir.toString)
    val resolver = FakeResolver()
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, resolver, invoker)
    launcher.run(Vector("dev", "server", "--runtime-dev-dir", "cncf-runtime"))
    _assert_equals(resolver.resolvedClasspaths, Vector.empty)
    invoker.lastClasspath.contains(runtimeclassdir) shouldBe true
    invoker.lastClasspath.contains(appclassdir) shouldBe true
  }

  def devServerUsesRuntimeDevelopmentCatalogForSelection(): Unit = _with_temp_paths { paths =>
    val appclassdir = paths.cwd.resolve("target").resolve("scala-3.3.7").resolve("classes")
    val runtimeproject = paths.cwd.resolve("cncf-runtime")
    val runtimeclassdir = runtimeproject.resolve("target").resolve("scala-3.3.7").resolve("classes")
    Files.createDirectories(appclassdir)
    Files.createDirectories(runtimeclassdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), appclassdir.toString)
    _write(paths.cwd.resolve("project.yaml"), _project_yaml("0.4.10-SNAPSHOT", Vector("0.4.10-SNAPSHOT")))
    _write(runtimeproject.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), runtimeclassdir.toString)
    _write(runtimeproject.resolve("target").resolve("cncf.d").resolve("runtime-catalog.yaml"), _runtime_dev_catalog_text)
    val resolver = FakeResolver()
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, resolver, invoker)

    launcher.run(Vector("--runtime", "0.4.10-SNAPSHOT", "dev", "server", "--runtime-dev-dir", "cncf-runtime"))

    _assert_equals(resolver.resolvedClasspaths, Vector.empty)
    invoker.lastClasspath.contains(runtimeclassdir) shouldBe true
    invoker.lastClasspath.contains(appclassdir) shouldBe true
  }

  def devCommandPassesRuntimeLeadingArgs(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    launcher.run(Vector(
      "dev", "command",
      "--repository-dir", "repository.d",
      "--component-car-dir", "car.d",
      "--textus.runtime.component=minimal",
      "minimal.main.hello",
      "--format", "yaml"
    ))
    val commandindex = invoker.lastArgs.indexOf("command")
    commandindex > 0 shouldBe true
    _assert_equals(invoker.lastArgs.slice(commandindex - 5, commandindex), Vector(
      "--repository-dir", "repository.d",
      "--component-car-dir", "car.d",
      "--textus.runtime.component=minimal"
    ))
    _assert_equals(invoker.lastArgs.drop(commandindex), Vector("command", "minimal.main.hello", "--format", "yaml"))
  }

  def devCommandKeepsSampleMainClassValueAsRuntimeArg(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    launcher.run(Vector(
      "dev", "command",
      "--sample-main-class", "sample.Main",
      "minimal.main.hello"
    ))
    val commandindex = invoker.lastArgs.indexOf("command")
    commandindex > 0 shouldBe true
    _assert_equals(invoker.lastArgs.slice(commandindex - 2, commandindex), Vector(
      "--sample-main-class", "sample.Main"
    ))
    _assert_equals(invoker.lastArgs.drop(commandindex), Vector("command", "minimal.main.hello"))
  }

  def devCommandCanDisableProjectClasspath(): Unit = _with_temp_paths { paths =>
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    val code = launcher.run(Vector("dev", "command", "--no-project-classpath", "--repository-dir", "repository.d", "minimal.main.hello"))
    _assert_equals(code, 0)
    invoker.lastArgs.contains("--repository-dir") shouldBe true
    invoker.lastArgs.contains(paths.cwd.toAbsolutePath.normalize.toString) shouldBe false
  }

  def devCommandCanDisableProjectComponentDevDir(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    val code = launcher.run(Vector("dev", "command", "--no-project-component-dev-dir", "--discover=classes", "minimal.main.hello"))
    _assert_equals(code, 0)
    invoker.lastClasspath.contains(classdir) shouldBe true
    invoker.lastArgs.contains("--component-dev-dir") shouldBe false
    invoker.lastArgs.contains("--discover=classes") shouldBe true
  }


  def devCommandDoesNotAutoActivateComponentDirArtifacts(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("component.d").resolve("testcomp.car"), "fake-car")
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    val code = launcher.run(Vector("dev", "command", "testcomp.main.hello"))
    _assert_equals(code, 0)
    invoker.lastArgs.contains("--component-dir") shouldBe false
    invoker.lastArgs.contains("--component-dev-dir") shouldBe true
    invoker.lastClasspath.contains(classdir) shouldBe true
  }

  def devTargetOptionsAreMutuallyExclusive(): Unit = {
    val failed =
      try {
        CncfCommandParser.parse(Vector("dev", "server", "--project-dev", "app", "--name", "textus-blog"))
        false
      } catch {
        case e: CncfException => e.getMessage.contains("mutually exclusive")
      }
    failed shouldBe true
    val oldproject =
      try {
        CncfCommandParser.parse(Vector("dev", "server", "--project", "app"))
        false
      } catch {
        case e: CncfException => e.getMessage.contains("--project is no longer supported")
      }
    oldproject shouldBe true
  }

  def devNameTargetUsesLocalSnapshotOnly(): Unit = _with_temp_paths { paths =>
    val name = "textus-demo"
    val version = "0.1.0-SNAPSHOT"
    _write(paths.localCarRepository.resolve(name).resolve(version).resolve(s"$name-$version.car"), "fake-car")
    _write(paths.cacheCarRepository.resolve(name).resolve(version).resolve(s"$name-$version.car"), "wrong-car")
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    launcher.run(Vector("dev", "server", "--name", s"$name:$version"))

    invoker.lastArgs.contains(s"--textus.component=$name") shouldBe true
    invoker.lastArgs.contains(s"--textus.component.version=$version") shouldBe true
    invoker.lastArgs.contains(s"--repository-dir=${paths.localCarRepository}") shouldBe true
    invoker.lastArgs.contains(s"--repository-dir=${paths.cacheCarRepository}") shouldBe false
  }

  def devNameTargetSnapshotBypassesReleaseCatalog(): Unit = _with_temp_paths { paths =>
    val name = "textus-demo"
    val releaseversion = "0.1.0"
    val snapshotversion = "0.1.1-SNAPSHOT"
    _write(paths.localCarRepository.resolve(name).resolve(snapshotversion).resolve(s"$name-$snapshotversion.car"), "fake-car")
    _write(paths.localCarRepository.getParent.resolve("catalog").resolve("car").resolve(s"$name.yaml"),
      s"""schemaVersion: 1
         |kind: car
         |artifactId: $name
         |recommended: $releaseversion
         |latestStable: $releaseversion
         |status: active
         |aliases: []
         |versions:
         |  - version: $releaseversion
         |    channel: stable
         |    status: active
         |    component: $name
         |    file: repository/car/$name/$releaseversion/$name-$releaseversion.car
         |""".stripMargin)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    launcher.run(Vector("dev", "server", "--name", s"$name:$snapshotversion"))

    invoker.lastArgs.contains(s"--textus.component=$name") shouldBe true
    invoker.lastArgs.contains(s"--textus.component.version=$snapshotversion") shouldBe true
    invoker.lastArgs.contains(s"--repository-dir=${paths.localCarRepository}") shouldBe true
  }

  def devNameTargetUsesReleaseRepositories(): Unit = _with_temp_paths { paths =>
    val name = "textus-demo"
    val version = "0.1.0"
    _write(paths.cacheCarRepository.resolve(name).resolve(version).resolve(s"$name-$version.car"), "fake-car")
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    launcher.run(Vector("dev", "server", "--name", s"$name:$version"))

    invoker.lastArgs.contains(s"--textus.component=$name") shouldBe true
    invoker.lastArgs.contains(s"--textus.component.version=$version") shouldBe true
    invoker.lastArgs.contains(s"--repository-dir=${paths.localCarRepository}") shouldBe true
    invoker.lastArgs.contains(s"--repository-dir=${paths.cacheCarRepository}") shouldBe true
  }

  def devServerEmulationRewritesToCncfArgs(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  version: 0.5.0
         |  catalog:
         |    url: ${paths.cwd.resolve("missing-runtime-catalog.yaml")}
         |""".stripMargin)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    launcher.run(Vector("dev", "server-emulation", "blog.component.search"))
    invoker.lastArgs.contains("server-emulator") shouldBe true
    invoker.lastArgs.contains("blog.component.search") shouldBe true
  }

  def devProjectLoadsTargetProjectConfig(): Unit = _with_temp_paths { paths =>
    val project = paths.cwd.resolve("blog")
    val classdir = project.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(project.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(project.resolve("conf").resolve("cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  version: 0.5.0
         |  catalog:
         |    url: ${project.resolve("missing-runtime-catalog.yaml")}
         |dev:
         |  port: 19700
         |""".stripMargin)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    launcher.run(Vector("dev", "server", "--project-dev", "blog"))
    invoker.lastArgs.contains(project.toAbsolutePath.normalize.toString) shouldBe true
    invoker.lastArgs.exists(_.startsWith("--cncf.server.port=")) shouldBe false
  }

  def devHelpExplainsResolutionModel(): Unit = {
    val help = CncfCommandParser.helpText
    help.contains("Execution:") shouldBe true
    help.contains("cncf command/server/client forwards to CncfMain") shouldBe true
    help.contains("Target-first execution adds target activation") shouldBe true
    help.contains("Low-level dev commands:") shouldBe false
    help.contains("cncf dev server") shouldBe false
    help.contains("cncf dev is deprecated") shouldBe true
    help.contains("dev-server.pid") shouldBe false
    help.contains("--component-dev-dir <dir> is a dependency component local override") shouldBe true
    help.contains("cozyPublishLocalCar") shouldBe true
    help.contains("~/.cncf/local is developer local publish state") shouldBe true
    help.contains("Snapshot components are local-only") shouldBe true
    help.contains("descriptor source metadata lives under src/main/web-inf") shouldBe true
    help.contains("textus server <artifact> is the CAR/SAR artifact launcher") shouldBe true
    help.contains("ancestor conf/cncf/config.yaml and .cncf/config.yaml") shouldBe true
  }

  def devCheckReportsMainTargetAndDependencyResolution(): Unit = _with_temp_paths { paths =>
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker())
    val (code, output) = _capture_stdout {
      launcher.run(Vector("dev", "check"))
    }
    _assert_equals(code, 0)
    output.contains("dev-target mode=project-dev") shouldBe true
    output.contains("main-target source=local-project") shouldBe true
    output.contains("main-target-repository-lookup disabled in project-dev mode") shouldBe true
    output.contains("dependency-components local dev overrides") shouldBe true
    output.contains("local-repository") shouldBe true
    output.contains(paths.localRepository.toString) shouldBe true
    output.contains("cache-repository") shouldBe true
    output.contains("status=not-created") shouldBe true
    output.contains("web-descriptor-source none; use src/main/web-inf/web.yaml|form.yaml|admin.yaml") shouldBe true
  }

  def devCheckReportsDevServerState(): Unit = _with_temp_paths { paths =>
    _write_dev_server_state(paths.cwd, 2230L, "19610")
    val processmanager = FakeDevServerProcessManager(3337L)
    processmanager.alive = Set(2230L)
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker(), SbtRuntimeClasspathExporter, processmanager)
    val (code, output) = _capture_stdout {
      launcher.run(Vector("dev", "check", "--port", "19610"))
    }

    _assert_equals(code, 0)
    output.contains("dev-server") shouldBe true
    output.contains("pid=2230") shouldBe true
    output.contains("status=alive") shouldBe true
    output.contains("port=19610") shouldBe true
  }

  def devCheckTreatsMissingMainTargetClasspathAsWarning(): Unit = _with_temp_paths { paths =>
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker())
    val (code, output) = _capture_stdout {
      launcher.run(Vector("dev", "check"))
    }
    _assert_equals(code, 0)
    output.contains("WARN") shouldBe true
    output.contains("runtime-classpath") shouldBe true
    output.contains("dev server will run cncf dev classpath automatically") shouldBe true
  }

  def devCheckTreatsMissingDependencyClasspathAsError(): Unit = _with_temp_paths { paths =>
    Files.createDirectories(paths.cwd.getParent.resolve("account"))
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker())
    val (code, output) = _capture_stdout {
      launcher.run(Vector("dev", "check", "--component-dev-dir", "../account"))
    }
    _assert_equals(code, 2)
    output.contains("ERROR") shouldBe true
    output.contains("dependency-component-dev-dir") shouldBe true
    output.contains("run cncf dev classpath --project-dev") shouldBe true
  }

  def devServerAutoGeneratesMainTargetClasspath(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    val exporter = FakeClasspathExporter.success(classdir.toString)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker, exporter)

    val code = launcher.run(Vector("dev", "server"))

    _assert_equals(code, 0)
    _assert_equals(exporter.projects, Vector(paths.cwd))
    Files.isRegularFile(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt")) shouldBe true
    invoker.lastClasspath.contains(classdir) shouldBe true
    invoker.lastArgs.contains("--component-dev-dir") shouldBe true
    invoker.lastArgs.contains(paths.cwd.toString) shouldBe true
  }

  def devServerReportsMainTargetClasspathExportFailure(): Unit = _with_temp_paths { paths =>
    val exporter = FakeClasspathExporter.failure("sbt failed")
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker(), exporter)
    val failed =
      try {
        launcher.run(Vector("dev", "server"))
        false
      } catch {
        case e: CncfException =>
          e.getMessage.contains("failed to prepare main target runtime classpath") &&
            e.getMessage.contains("run cncf dev classpath --project-dev") &&
            e.getMessage.contains("sbt failed")
      }
    failed shouldBe true
  }

  def devUsesCurrentCompatibleRuntimeByDefault(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("project.yaml"), _project_yaml("0.2.0", Vector("0.2.0", "0.3.0-SNAPSHOT")))
    _write(paths.cwd.resolve("runtime-catalog.yaml"), _catalog_text)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: ${paths.cwd.resolve("runtime-catalog.yaml")}
         |""".stripMargin)
    val resolver = FakeResolver()
    val launcher = new CncfLauncher(paths, resolver, FakeInvoker())

    launcher.run(Vector("dev", "server"))

    _assert_equals(resolver.resolvedClasspaths, Vector("0.2.0"))
  }

  def devCanSelectLatestTestedRuntime(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("project.yaml"), _project_yaml("0.2.0", Vector("0.2.0", "0.3.0-SNAPSHOT")))
    _write(paths.cwd.resolve("runtime-catalog.yaml"), _catalog_text)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: ${paths.cwd.resolve("runtime-catalog.yaml")}
         |""".stripMargin)
    val resolver = FakeResolver()
    val launcher = new CncfLauncher(paths, resolver, FakeInvoker())

    launcher.run(Vector("--runtime-selection=tested-latest", "dev", "server"))

    _assert_equals(resolver.resolvedClasspaths, Vector("0.2.0"))
  }

  def devCanSelectLatestCompatibleRuntime(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("project.yaml"), _project_yaml("0.2.0", Vector("0.2.0")))
    _write(paths.cwd.resolve("runtime-catalog.yaml"), _catalog_text)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: ${paths.cwd.resolve("runtime-catalog.yaml")}
         |""".stripMargin)
    val resolver = FakeResolver()
    val launcher = new CncfLauncher(paths, resolver, FakeInvoker())

    launcher.run(Vector("--runtime-selection=latest", "dev", "server"))

    _assert_equals(resolver.resolvedClasspaths, Vector("0.2.0"))
  }

  def devCanSelectNewestCompatibleRuntime(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("project.yaml"), _project_yaml("0.2.0", Vector("0.2.0")))
    _write(paths.cwd.resolve("runtime-catalog.yaml"), _catalog_text)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: ${paths.cwd.resolve("runtime-catalog.yaml")}
         |""".stripMargin)
    val resolver = FakeResolver()
    val launcher = new CncfLauncher(paths, resolver, FakeInvoker())

    launcher.run(Vector("--runtime-selection=newest", "dev", "server"))

    _assert_equals(resolver.resolvedClasspaths, Vector("0.3.0-SNAPSHOT"))
  }

  def devParsesInlineRuntimeRequirementLists(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("project.yaml"),
      """packaging:
        |  kind: car
        |  car:
        |    runtime:
        |      cncf:
        |        minimum: 0.2.0
        |        excluded: []
        |        tested: [0.3.0-SNAPSHOT]
        |""".stripMargin)
    _write(paths.cwd.resolve("runtime-catalog.yaml"), _catalog_text)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: ${paths.cwd.resolve("runtime-catalog.yaml")}
         |""".stripMargin)
    val resolver = FakeResolver()
    val launcher = new CncfLauncher(paths, resolver, FakeInvoker())

    launcher.run(Vector("--runtime-selection=tested-latest", "dev", "server"))

    _assert_equals(resolver.resolvedClasspaths, Vector("0.3.0-SNAPSHOT"))
  }

  def devSelectsCommonRuntimeAcrossProjectAndDependency(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    val account = paths.cwd.getParent.resolve("account")
    Files.createDirectories(classdir)
    Files.createDirectories(account.resolve("target").resolve("classes"))
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(account.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("project.yaml"), _project_yaml("0.2.0", Vector("0.2.0", "0.3.0-SNAPSHOT")))
    _write(account.resolve("project.yaml"), _project_yaml("0.2.0", Vector("0.2.0")))
    _write(paths.cwd.resolve("runtime-catalog.yaml"), _catalog_text)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: ${paths.cwd.resolve("runtime-catalog.yaml")}
         |dev:
         |  componentDevDirs:
         |    - ../account
         |""".stripMargin)
    val resolver = FakeResolver()
    val launcher = new CncfLauncher(paths, resolver, FakeInvoker())

    launcher.run(Vector("dev", "server"))

    _assert_equals(resolver.resolvedClasspaths, Vector("0.2.0"))
  }

  def devRuntimeConflictDefaultsToError(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("project.yaml"), _project_yaml("9.0.0", Vector("9.0.0")))
    _write(paths.cwd.resolve("runtime-catalog.yaml"), _catalog_text)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: ${paths.cwd.resolve("runtime-catalog.yaml")}
         |""".stripMargin)
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker())
    val failed =
      try {
        launcher.run(Vector("dev", "server"))
        false
      } catch {
        case e: CncfException => e.getMessage.contains("no compatible CNCF runtime version")
      }
    failed shouldBe true
  }

  def devRuntimeConflictCanUseNewestPolicy(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("project.yaml"), _project_yaml("9.0.0", Vector("9.0.0")))
    _write(paths.cwd.resolve("runtime-catalog.yaml"), _catalog_text)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: ${paths.cwd.resolve("runtime-catalog.yaml")}
         |""".stripMargin)
    val resolver = FakeResolver()
    val launcher = new CncfLauncher(paths, resolver, FakeInvoker())

    launcher.run(Vector("--runtime-no-compatible=newest", "dev", "server"))

    _assert_equals(resolver.resolvedClasspaths, Vector("0.3.0-SNAPSHOT"))
  }

  def runtimeCommandDoesNotLoadCncf(): Unit = _with_temp_paths { paths =>
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"), "runtime:\n  version: 0.5.0\n")
    val resolver = FakeResolver()
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, resolver, invoker)
    launcher.run(Vector("runtime", "current"))
    _assert_equals(resolver.resolvedClasspaths, Vector.empty)
    _assert_equals(invoker.lastArgs, Vector.empty)
  }

  def latestRuntimeIsConcrete(): Unit = _with_temp_paths { paths =>
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: ${paths.cwd.resolve("missing-runtime-catalog.yaml")}
         |""".stripMargin)
    val resolver = FakeResolver()
    val launcher = new CncfLauncher(paths, resolver, FakeInvoker())
    launcher.run(Vector("runtime", "current"))
    _assert_equals(resolver.resolvedVersions, Vector(LauncherConfig.DEFAULT_RUNTIME_VERSION))
  }

  def noRuntimeLibraryDependencies(): Unit = {
    val lines = Files.readString(Path.of("build.sbt")).linesIterator.toVector.map(_.trim)
    def _runtime_library_dependency_(line: String): Boolean =
      line.contains("libraryDependencies") &&
        line.contains("\"") &&
        !line.contains("goldenport-launcher-core") &&
        !line.contains("% Test") &&
        !line.contains("% \"test\"")
    lines.exists(_runtime_library_dependency_) shouldBe false
    lines.exists(_.contains("goldenport-launcher-core")) shouldBe true
  }

  private def _capture_stdout(f: => Int): (Int, String) = {
    val out = new java.io.ByteArrayOutputStream()
    val code = Console.withOut(new java.io.PrintStream(out)) {
      f
    }
    (code, out.toString)
  }

  private def _capture_stdout_stderr(f: => Int): (Int, String, String) = {
    val out = new java.io.ByteArrayOutputStream()
    val err = new java.io.ByteArrayOutputStream()
    val code = Console.withOut(new java.io.PrintStream(out)) {
      Console.withErr(new java.io.PrintStream(err)) {
        f
      }
    }
    (code, out.toString, err.toString)
  }

  private def _with_temp_paths(f: LauncherPaths => Unit): Unit = {
    val root = Files.createTempDirectory("cncf-launcher-spec-")
    val home = root.resolve("home")
    val cwd = root.resolve("work")
    Files.createDirectories(home)
    Files.createDirectories(cwd)
    f(LauncherPaths(home, cwd))
  }

  private def _write(path: Path, value: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, value)
  }

  private def _write_zip(
    path: Path,
    entry: String,
    value: String
  ): Path = {
    Files.createDirectories(path.getParent)
    val out = new ZipOutputStream(Files.newOutputStream(path))
    try {
      out.putNextEntry(new ZipEntry(entry))
      out.write(value.getBytes(StandardCharsets.UTF_8))
      out.closeEntry()
    } finally {
      out.close()
    }
    path
  }

  private def _write_dev_server_state(
    project: Path,
    pid: Long,
    port: String,
    processstartedat: java.time.Instant = java.time.Instant.parse("2026-05-24T00:00:00Z")
  ): Unit = {
    val state = DevServerState(
      pid = pid,
      project = project.toAbsolutePath.normalize,
      port = port,
      runtimeVersion = "0.9.0",
      executionProfile = None,
      startedAt = java.time.Instant.parse("2026-05-24T00:00:00Z"),
      processStartedAt = Some(processstartedat),
      commandLine = Some("cncf dev server"),
      command = "cncf dev server"
    )
    _write(DevSupport.devServerPidFile(project), s"${pid}\n")
    _write(DevSupport.devServerJsonFile(project), state.renderJson)
  }

  private def _assert_equals[A](actual: A, expected: A): Unit =
    actual shouldBe expected

  private val _catalog_text: String =
    """schemaVersion: 1
      |generatedAt: 2026-05-17T00:00:00Z
      |recommended: 0.2.0
      |latestStable: 0.2.0
      |latestSnapshot: 0.3.0-SNAPSHOT
      |mavenRepositories:
      |  - https://repo.example/maven
      |carRepositories:
      |  - https://repo.example/car
      |sarRepositories:
      |  - https://repo.example/sar
      |coursierRepositories:
      |  - https://repo.example/coursier
      |baseProvided:
      |  - org.goldenport:goldenport-cncf_3
      |  - org.typelevel:cats-core_3
      |versions:
      |  - version: 0.1.0
      |    channel: stable
      |    status: disabled
      |    scalaBinaryVersion: "3"
      |    module: org.goldenport:goldenport-cncf_3:0.1.0
      |  - version: 0.2.0
      |    channel: stable
      |    status: active
      |    scalaBinaryVersion: "3"
      |    module: org.goldenport:goldenport-cncf_3:0.2.0
      |    publishedAt: 2026-05-17T01:00:00Z
      |  - version: 0.3.0-SNAPSHOT
      |    channel: snapshot
      |    status: active
      |    scalaBinaryVersion: "3"
      |    module: org.goldenport:goldenport-cncf_3:0.3.0-SNAPSHOT
      |    publishedAt: 2026-05-17T02:00:00Z
      |""".stripMargin

  private val _runtime_dev_catalog_text: String =
    """schemaVersion: 1
      |generatedAt: 2026-05-25T00:00:00Z
      |recommended: 0.4.9
      |latestStable: 0.4.9
      |latestSnapshot: 0.4.10-SNAPSHOT
      |mavenRepositories:
      |  - https://repo.example/maven
      |baseProvided:
      |  - org.goldenport:goldenport-cncf_3
      |versions:
      |  - version: 0.4.9
      |    channel: stable
      |    status: active
      |    scalaBinaryVersion: "3"
      |    module: org.goldenport:goldenport-cncf_3:0.4.9
      |    publishedAt: 2026-05-24T01:00:00Z
      |  - version: 0.4.10-SNAPSHOT
      |    channel: snapshot
      |    status: active
      |    scalaBinaryVersion: "3"
      |    module: org.goldenport:goldenport-cncf_3:0.4.10-SNAPSHOT
      |    publishedAt: 2026-05-25T01:00:00Z
      |""".stripMargin

  private def _project_yaml(
    minimum: String,
    tested: Vector[String]
  ): String =
    s"""packaging:
       |  kind: car
       |  car:
       |    runtime:
       |      cncf:
       |        minimum: $minimum
       |        excluded: []
       |        tested:
       |${tested.map(v => s"          - $v").mkString("\n")}
       |""".stripMargin
}

final class FakeResolver(
  classpath: Option[Vector[Path]] = None
) extends CncfRuntimeResolver {
  var resolvedVersions: Vector[String] = Vector.empty
  var resolvedClasspaths: Vector[String] = Vector.empty
  override def resolveVersion(version: String, config: LauncherConfig, paths: LauncherPaths): String = {
    resolvedVersions :+= version
    if (version == LauncherConfig.DEFAULT_RUNTIME_VERSION) "0.9.0" else version
  }
  def resolve(version: String, config: LauncherConfig, paths: LauncherPaths): Vector[Path] = {
    val concreteversion = resolveVersion(version, config, paths)
    resolvedClasspaths :+= concreteversion
    classpath.getOrElse(Vector(paths.cwd.resolve(s"fake-cncf-$concreteversion.jar")))
  }
}

object FakeResolver {
  def apply(): FakeResolver = new FakeResolver()
  def apply(classpath: Option[Vector[Path]]): FakeResolver = new FakeResolver(classpath)
}

final class FakeClasspathExporter(
  response: Either[CncfException, String]
) extends RuntimeClasspathExporter {
  var projects: Vector[Path] = Vector.empty

  def exportRuntimeClasspath(project: Path): String = {
    projects :+= project
    response match {
      case Right(value) => value
      case Left(error) => throw error
    }
  }
}

object FakeClasspathExporter {
  def success(value: String): FakeClasspathExporter =
    new FakeClasspathExporter(Right(value))

  def failure(message: String): FakeClasspathExporter =
    new FakeClasspathExporter(Left(CncfException(message)))
}

final class FakeInvoker extends CncfInvoker {
  var lastClasspath: Vector[Path] = Vector.empty
  var lastArgs: Vector[String] = Vector.empty
  var onInvoke: () => Unit = () => ()

  override def invoke(classpath: Vector[Path], args: Vector[String]): Int = {
    lastClasspath = classpath
    lastArgs = args
    onInvoke()
    0
  }
}

object FakeInvoker {
  def apply(): FakeInvoker = new FakeInvoker()
}

final class FakeCncfTextusControlCenterRegistrationReporter extends CncfTextusControlCenterRegistrationReporter {
  var starts: Vector[(CncfTextusControlCenterRegistrationReport, Option[String])] = Vector.empty
  var configs: Vector[CncfTextusControlCenterRegistrationConfig] = Vector.empty
  var closes: Int = 0

  def start(
    config: CncfTextusControlCenterRegistrationConfig,
    report: CncfTextusControlCenterRegistrationReport,
    token: Option[String]
  ): CncfTextusControlCenterRegistrationSession = {
    starts :+= report -> token
    configs :+= config
    new CncfTextusControlCenterRegistrationSession {
      def close(): Unit = closes += 1
    }
  }
}

object FakeCncfTextusControlCenterRegistrationReporter {
  def apply(): FakeCncfTextusControlCenterRegistrationReporter = new FakeCncfTextusControlCenterRegistrationReporter()
}

final class CncfTextusControlCenterRegistrationOutageReporter extends CncfTextusControlCenterRegistrationReporter {
  def start(
    config: CncfTextusControlCenterRegistrationConfig,
    report: CncfTextusControlCenterRegistrationReport,
    token: Option[String]
  ): CncfTextusControlCenterRegistrationSession =
    throw CncfException("simulated Textus Control Center outage")
}

final class FakeLauncherDevInvoker extends LauncherDevInvoker {
  var devDir: Option[Path] = None
  var args: Vector[String] = Vector.empty
  var cwd: Option[Path] = None

  def invoke(devdir: Path, args: Vector[String], cwd: Path): Int = {
    this.devDir = Some(devdir)
    this.args = args
    this.cwd = Some(cwd)
    0
  }
}

object FakeLauncherDevInvoker {
  def apply(): FakeLauncherDevInvoker = new FakeLauncherDevInvoker()
}

final class FakeDevServerProcessManager(
  currentpid: Long
) extends DevServerProcessManager {
  var alive: Set[Long] = Set.empty
  var defaultProcessStartedAt: Option[java.time.Instant] =
    Some(java.time.Instant.parse("2026-05-24T00:00:00Z"))
  var processStarts: Map[Long, java.time.Instant] =
    Map(currentpid -> java.time.Instant.parse("2026-05-24T00:00:00Z"))
  var commandLines: Map[Long, String] =
    Map(currentpid -> "cncf dev server")
  var gracefulStopped: Vector[Long] = Vector.empty
  var forceStopped: Vector[Long] = Vector.empty
  var gracefulSucceeds: Boolean = true

  def currentPid: Long =
    currentpid

  def processStartedAt(pid: Long): Option[java.time.Instant] =
    processStarts.get(pid).orElse(defaultProcessStartedAt)

  def commandLine(pid: Long): Option[String] =
    commandLines.get(pid)

  def isAlive(pid: Long): Boolean =
    alive.contains(pid)

  def stopGracefully(pid: Long): Boolean = {
    gracefulStopped :+= pid
    if (gracefulSucceeds) {
      alive -= pid
      true
    } else {
      false
    }
  }

  def stopForcibly(pid: Long): Boolean = {
    forceStopped :+= pid
    alive -= pid
    true
  }
}

object FakeDevServerProcessManager {
  def apply(currentpid: Long): FakeDevServerProcessManager =
    new FakeDevServerProcessManager(currentpid)
}
