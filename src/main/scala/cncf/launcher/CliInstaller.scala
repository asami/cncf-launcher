package cncf.launcher

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

/*
 * @since   Jun. 29, 2026
 * @version Jun. 29, 2026
 * @author  ASAMI, Tomoharu
 */
object CliInstaller {
  def installCncf(
    paths: LauncherPaths,
    command: CncfCommand.InstallCli
  ): Path = {
    val bindir = command.binDir.map(p => paths.cwd.resolve(p).normalize).getOrElse(paths.home.resolve("bin"))
    val target = bindir.resolve(command.installedName)
    if (Files.exists(target) && !command.overwrite)
      throw CncfException(s"CLI command already exists: ${target}; use --overwrite")
    Files.createDirectories(bindir)
    Files.writeString(target, cncfScript(paths, command), StandardCharsets.UTF_8)
    target.toFile.setExecutable(true, false)
    target
  }

  def cncfScript(
    paths: LauncherPaths,
    command: CncfCommand.InstallCli
  ): String = {
    val fixedtarget = paths.cwd.resolve(command.projectDev.getOrElse(".")).normalize.toAbsolutePath.normalize.toString
    val fileparams = _file_param_aliases(command.fileParams)
    val fileparamcases = _file_param_cases(fileparams)
    val operationprefix = command.operationPrefix.getOrElse("")
    val runtimeversion = command.runtimeVersion.getOrElse("")
    val runtimedevdir = command.runtimeDevDir.getOrElse("")
    val launcherdevdir = command.launcherDevDir.getOrElse("")
    val componentdevdirs = command.componentDevDirs.map(_.trim).filter(_.nonEmpty)
    val componentdevdirlines =
      componentdevdirs.map(x => s"  '${_shell(x)}'").mkString("\n")
    val selectorblock =
      command.operationPrefix match {
        case Some(_) =>
          """operation="$1"
            |shift
            |selector="${operation_prefix}.${operation}"
            |""".stripMargin
        case None =>
          """selector=""
            |""".stripMargin
      }
    s"""#!/usr/bin/env bash
       |set -euo pipefail
       |
       |fixed_target='${_shell(fixedtarget)}'
       |operation_prefix='${_shell(operationprefix)}'
       |runtime_version='${_shell(runtimeversion)}'
       |runtime_dev_dir='${_shell(runtimedevdir)}'
       |launcher_dev_dir='${_shell(launcherdevdir)}'
       |if [[ -n "$$launcher_dev_dir" ]]; then
       |  export CNCF_LAUNCHER_DEV_DIR="$$launcher_dev_dir"
       |fi
       |component_dev_dirs=(
       |${componentdevdirlines}
       |)
       |
       |usage() {
       |  cat <<'EOF'
       |Usage:
       |  ${command.installedName} <operation-selector> [args...]
       |
       |Examples:
       |  ${command.installedName} validate-presentation --presentationDsl xxx.yaml
       |
       |This command delegates to:
       |  cncf ${_render_runtime_usage(command)} ${fixedtarget} command <operation-selector>
       |EOF
       |}
       |
       |is_file_param() {
       |  case "$$1" in
       |${fileparamcases}
       |      return 0
       |      ;;
       |    *)
       |      return 1
       |      ;;
       |  esac
       |}
       |
       |if [[ $$# -lt 1 ]]; then
       |  usage >&2
       |  exit 2
       |fi
       |
       |case "$$1" in
       |  -h|--help|help)
       |    usage
       |    exit 0
       |    ;;
       |esac
       |
       |${selectorblock}
       |declare -a cncf_args=()
       |if [[ -n "$$runtime_version" ]]; then
       |  cncf_args+=("--runtime" "$$runtime_version")
       |fi
       |if [[ -n "$$runtime_dev_dir" ]]; then
       |  cncf_args+=("--runtime-dev-dir" "$$runtime_dev_dir")
       |fi
       |
       |declare -a component_dev_args=()
       |for dir in "$${component_dev_dirs[@]}"; do
       |  if [[ -n "$$dir" ]]; then
       |    component_dev_args+=("--component-dev-dir" "$$dir")
       |  fi
       |done
       |
       |declare -a command_args=()
       |if [[ -n "$$selector" ]]; then
       |  command_args+=("$$selector")
       |fi
       |
       |while [[ $$# -gt 0 ]]; do
       |  case "$$1" in
       |    --*=*)
       |      option="$${1%%=*}"
       |      name="$${option#--}"
       |      value="$${1#*=}"
       |      shift
       |      if is_file_param "$$name" && [[ -f "$$value" ]]; then
       |        value="$$(<"$$value")"
       |      fi
       |      command_args+=("$$option" "$$value")
       |      ;;
       |    --*)
       |      option="$$1"
       |      name="$${option#--}"
       |      shift
       |      if is_file_param "$$name" && [[ $$# -gt 0 ]]; then
       |        value="$$1"
       |        shift
       |        if [[ -f "$$value" ]]; then
       |          value="$$(<"$$value")"
       |        fi
       |        command_args+=("$$option" "$$value")
       |      else
       |        command_args+=("$$option")
       |      fi
       |      ;;
       |    *)
       |      command_args+=("$$1")
       |      shift
       |      ;;
       |  esac
       |done
       |
       |exec cncf "$${cncf_args[@]}" "$$fixed_target" command "$${component_dev_args[@]}" "$${command_args[@]}"
       |""".stripMargin
  }


  private def _render_runtime_usage(command: CncfCommand.InstallCli): String = {
    val args = Vector.newBuilder[String]
    command.runtimeVersion.foreach(v => args += s"--runtime ${v}")
    command.runtimeDevDir.foreach(v => args += s"--runtime-dev-dir ${v}")
    command.componentDevDirs.foreach(v => args += s"--component-dev-dir ${v}")
    val rendered = args.result().mkString(" ")
    if (rendered.isEmpty) "" else s"${rendered} "
  }

  private def _file_param_aliases(values: Vector[String]): Vector[String] =
    values.flatMap(v => Vector(v, _camel_to_kebab(v))).distinct

  private def _file_param_cases(values: Vector[String]): String =
    if (values.isEmpty) "    __cncf_no_file_params__)"
    else s"    ${values.map(_shell_case).mkString("|")})"

  private def _camel_to_kebab(value: String): String =
    value.flatMap { c =>
      if (c.isUpper) "-" + c.toLower.toString else c.toString
    }

  private def _shell(value: String): String =
    value.replace("'", "'\"'\"'")

  private def _shell_case(value: String): String =
    value.replace("\\", "\\\\").replace(")", "\\)")
}
