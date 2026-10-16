package cncf.launcher

import java.nio.file.{Files, Path}
import java.time.Instant
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

import io.circe.Json
import io.circe.parser.decode
import io.circe.syntax.*
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 */
final class ServerEvidenceProcessIdentitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CncfLocalServerEvidenceSnapshot.given

  private val _suffix = Gen.nonEmptyListOf(Gen.oneOf("abcXYZ0129._-:+".toVector)).map(_.take(12).mkString)

  "Shared Launcher native process observations" should {
    "preserve actual invocation identity (EP1)" which {
      "records the current native tuple through heartbeat and normal termination in fifty generated cases" in {
        Given("a JVM with native process start metadata and valid varied opaque invocation identities")
        val nativeinstant = _native_start()
        When("fifty generated invocations write and read the production store")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(50).withWorkers(1),
          Prop.forAll(_suffix) { suffix =>
            _with_paths { paths =>
              Given("a fresh target-owned store and a report timestamp different from the native start")
              val store = new CncfLocalServerEvidenceStore(paths)
              val report = _report("instance-" + suffix, "target-" + suffix, nativeinstant.minusSeconds(60))
              When("the invocation starts through the shipped store")
              store.started(report, "cncf")
              val started = _read(paths).entries.head
              Then("the persisted tuple is the actual current JVM identity rather than the report timestamp")
              started.instanceId shouldBe report.instanceId
              started.target shouldBe report.target
              started.artifactId shouldBe report.artifactId
              started.developmentDirectory shouldBe report.developmentDirectory
              started.startedAt shouldBe report.startedAt
              started.processId shouldBe Some(ProcessHandle.current().pid())
              started.processStartedAt shouldBe Some(nativeinstant)
              started.processStartedAt should not be Some(report.startedAt)
              When("heartbeat touches the invocation")
              store.alive(report.instanceId)
              val alive = _read(paths).entries.head
              Then("heartbeat changes no native identity or invocation facts")
              alive shouldBe started.copy(lastSeenAt = alive.lastSeenAt)
              When("normal termination closes the invocation")
              store.stopped(report.instanceId)
              val stopped = _read(paths).entries.head
              Then("normal stop retains the native tuple and all other invocation facts")
              stopped.stoppedAt should not be empty
              stopped shouldBe started.copy(lastSeenAt = stopped.lastSeenAt, stoppedAt = stopped.stoppedAt)
              true
            }
          })
        Then("all fifty real-store cases succeeded without discarded inputs")
        info(s"EP1 cncf successful=${result.succeeded} discarded=${result.discarded}")
        result.passed shouldBe true
        result.succeeded should be >= 50
        result.discarded shouldBe 0
      }
    }

    "retain the v1 source and wire contract (EP2)" which {
      "reads omitted and null optionals without upgrading legacy ownership or quarantining valid rows" in {
        Given("legacy twelve-argument construction and both legacy v1 optional-field representations")
        _with_paths { paths =>
          val now = Instant.now()
          val legacy = CncfLocalServerEvidenceEntry("cncf", "legacy", "legacy-target", Some("legacy-target"),
            "development", Some(paths.cwd.toString), None, None, "runtime-spec", now, now, None)
          val omitted = legacy.asJson.mapObject(_.remove("processId").remove("processStartedAt"))
          val nullable = omitted.mapObject(_.add("instanceId", Json.fromString("legacy-null"))
            .add("processId", Json.Null).add("processStartedAt", Json.Null))
          _seed(paths, Vector(omitted, nullable))
          val store = new CncfLocalServerEvidenceStore(paths)
          When("the shipped codec reads legacy records and the real writer appends an invocation")
          val before = _read(paths)
          store.started(_report("fresh", "fresh-target", now), "cncf")
          val after = _read(paths)
          val files = Files.list(paths.serverEvidence.getParent)
          val recovery = try files.iterator().asScala.exists(_.getFileName.toString.startsWith("server-evidence.recovery-"))
            finally files.close()
          Then("valid legacy rows remain non-owning, the new row has native metadata and no malformed recovery is made")
          before.entries.map(entry => (entry.processId, entry.processStartedAt)) shouldBe Vector((None, None), (None, None))
          after.entries.filter(_.instanceId.startsWith("legacy")) shouldBe before.entries
          after.entries.find(_.instanceId == "fresh").get.processStartedAt shouldBe Some(_native_start())
          after.entries.find(_.instanceId == "fresh").get.processId shouldBe Some(ProcessHandle.current().pid())
          recovery shouldBe false
        }
      }
    }

    "preserve another writer's observations (EP3)" which {
      "keeps a declared other-kind tuple and a legacy row exact while mutating its own distinct record" in {
        Given("shared-schema compatibility input for the other writer and a legacy record")
        _with_paths { paths =>
          val now = Instant.now()
          val other = CncfLocalServerEvidenceEntry("textus", "other-instance", "other-target", Some("other-target"),
            "development", Some(paths.cwd.toString), None, None, "runtime-spec", now, now, None,
            Some(ProcessHandle.current().pid()), Some(_native_start()))
          val legacy = other.copy(instanceId = "legacy", processId = None, processStartedAt = None)
          _seed(paths, Vector(other.asJson, legacy.asJson))
          val store = new CncfLocalServerEvidenceStore(paths)
          When("this writer starts, touches and normally stops its own invocation")
          store.started(_report("own-instance", "own-target", now), "cncf")
          store.alive("own-instance")
          store.stopped("own-instance")
          val entries = _read(paths).entries
          Then("other-kind identity and legacy absence survive the real read-modify-write cycle")
          entries.find(_.instanceId == other.instanceId) shouldBe Some(other)
          entries.find(_.instanceId == legacy.instanceId) shouldBe Some(legacy)
          entries.map(_.instanceId).distinct should have size 3
          entries.find(_.instanceId == "own-instance").get.stoppedAt should not be empty
        }
      }
    }

    "keep process metadata private in safe lists (EP4)" which {
      "exposes the original list field set and the exact separately protected detail" in {
        Given("a real store entry containing the current native tuple and a target-owned private directory")
        _with_paths { paths =>
          val store = new CncfLocalServerEvidenceStore(paths)
          val report = _report("private-instance", "private-target", Instant.now()).copy(developmentDirectory = Some(paths.cwd.toString))
          When("the real store starts and its shipped projections are encoded")
          store.started(report, "cncf")
          val entry = _read(paths).entries.head
          val listing = store.listProjection().toOption.get.asJson
          val detail = store.detailProjection(report.instanceId).toOption.get.get.asJson
          Then("the list retains its public fields without native identity or directory while detail retains the exact selected record")
          listing.hcursor.get[String]("schema").toOption shouldBe Some(CncfLocalServerEvidenceStore.ProjectionSchema)
          listing.hcursor.downField("entries").focus.get.asArray.get.head.asObject.get.keys.toSet shouldBe
            Set("launcherKind", "instanceId", "target", "artifactId", "executionMode", "subsystemName", "subsystemVersion",
              "runtimeVersion", "startedAt", "lastSeenAt", "stoppedAt")
          listing.noSpaces should not include "processId"
          listing.noSpaces should not include "processStartedAt"
          listing.noSpaces should not include "developmentDirectory"
          listing.noSpaces should not include paths.cwd.toString
          detail.hcursor.downField("entry").as[CncfLocalServerEvidenceEntry].toOption shouldBe Some(entry)
          entry.processStartedAt shouldBe Some(_native_start())
          entry.processId shouldBe Some(ProcessHandle.current().pid())
          entry.developmentDirectory shouldBe Some(paths.cwd.toString)
        }
      }
    }

  }

  private def _native_start(): Instant =
    ProcessHandle.current().info().startInstant().toScala.getOrElse(
      throw new IllegalStateException("EP prerequisite: current JVM native process startInstant is unavailable"))

  private def _report(instanceid: String, target: String, startedat: Instant): CncfTextusControlCenterRegistrationReport =
    CncfTextusControlCenterRegistrationReport(instanceid, target, Some(target), "development", Some(Path.of("target").toAbsolutePath.toString),
      None, None, "runtime-spec", startedat)

  private def _read(paths: LauncherPaths): CncfLocalServerEvidenceSnapshot =
    decode[CncfLocalServerEvidenceSnapshot](Files.readString(paths.serverEvidence)).fold(error => throw error, identity)

  private def _seed(paths: LauncherPaths, entries: Vector[Json]): Unit = {
    Files.createDirectories(paths.serverEvidence.getParent)
    Files.writeString(paths.serverEvidence, Json.obj("schema" -> Json.fromString(CncfLocalServerEvidenceSnapshot.Schema),
      "entries" -> Json.fromValues(entries)).noSpaces)
    ()
  }

  private def _with_paths[A](run: LauncherPaths => A): A = {
    val base = Path.of("target", "evidence-identity", "work").toAbsolutePath.normalize
    Files.createDirectories(base)
    val directory = Files.createTempDirectory(base, "invocation-")
    try run(LauncherPaths(directory, directory))
    finally {
      val paths = Files.walk(directory)
      try paths.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.delete)
      finally paths.close()
    }
  }
}
