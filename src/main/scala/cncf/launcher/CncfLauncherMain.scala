package cncf.launcher

/*
 * @since   May. 17, 2026
 * @version May. 17, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfLauncherMain {
  def main(args: Array[String]): Unit = {
    val code =
      try CncfLauncher().run(args.toVector)
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
}
