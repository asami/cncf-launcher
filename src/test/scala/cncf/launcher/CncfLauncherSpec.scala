/*
 * @since   May. 17, 2026
 *  version Jun. 29, 2026
 *  version Jul. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
package cncf.launcher

import org.scalatest.Suites

object CncfLauncherSpec {
  def main(args: Array[String]): Unit =
    CncfLauncherSpecManualMain.main(args)
}

final class CncfLauncherSpec extends Suites(
  new CncfLauncherCommandSpec,
  new CncfLauncherLifecycleSpec,
  new CncfLauncherRuntimeSpec,
  new CncfLauncherDevelopmentSpec
)
