package cncf.launcher

import java.nio.charset.StandardCharsets
import java.nio.file.Files

/*
 * @since   May. 17, 2026
 * @version Jun.  8, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfLauncherMain {
  def main(args: Array[String]): Unit = {
    val effectiveargs = _args_from_file().getOrElse(args.toVector)
    val code =
      try CncfLauncher().run(effectiveargs)
      catch {
        case e: CncfException =>
          Console.err.println(e.getMessage)
          e.code
        case e: Throwable =>
          Console.err.println(e.getMessage)
          1
      }
    if (code != 0)
      sys.exit(code)
  }

  private def _args_from_file(): Option[Vector[String]] =
    sys.env.get("CNCF_LAUNCHER_ARGS_FILE").map { filename =>
      val bytes = Files.readAllBytes(java.nio.file.Paths.get(filename))
      if (bytes.isEmpty)
        Vector.empty
      else
        new String(bytes, StandardCharsets.UTF_8).split("\u0000", -1).toVector.dropRight(1)
    }
}
