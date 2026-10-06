package com.edges.mask;

/** Runs outside the app process. Paths are private app paths, not UI input. */
final class GuardScript {
    static String build(String dir, DisplayProfile original, String wm, String uptime, String dumpsys) {
        String scaling = DisplayScaling.argument(original.scalingMode);
        return "#!/system/bin/sh\n"
            + "DIR=" + RootShell.quote(dir) + "\nWM=" + RootShell.quote(wm) + "\n"
            + "DUMP=" + RootShell.quote(dumpsys) + "\n"
            + "CLOCK=" + RootShell.quote(uptime) + "\n"
            + "mode() { " + DisplayScaling.queryCommand("\"$DUMP\"") + "; }\n"
            + "verify() {\n"
            + "  out=$(\"$WM\" size 2>&1) || return 1\n"
            + "  printf '%s\\n' \"$out\" | grep -F -x "
            + RootShell.quote("Physical size: " + original.physicalWidth + "x" + original.physicalHeight)
            + " >/dev/null || return 1\n"
            + (original.overridden
                ? "  printf '%s\\n' \"$out\" | grep -F -x "
                    + RootShell.quote("Override size: " + original.width + "x" + original.height)
                    + " >/dev/null || return 1\n"
                : "  ! printf '%s\\n' \"$out\" | grep -q 'Override size:' || return 1\n")
            + "  current=$(mode) || return 1\n"
            + "  [ \"$current\" = " + RootShell.quote("EdgeMask scaling: " + scaling) + " ]\n"
            + "}\nrestore() {\n  printf 'restoring\\n' >\"$DIR/state\"\n"
            + "  for attempt in 1 2 3; do\n"
            + "    \"$WM\" size " + RootShell.quote(original.restoreArgument()) + " >\"$DIR/rollback.log\" 2>&1; sized=$?\n"
            + "    \"$WM\" scaling " + RootShell.quote(scaling) + " >>\"$DIR/rollback.log\" 2>&1; scaled=$?\n"
            + "    if [ \"$sized\" -eq 0 ] && [ \"$scaled\" -eq 0 ] && verify; then\n"
            + "      printf 'restored\\n' >\"$DIR/state\"\n"
            + "      exit 0\n    fi\n"
            + "    sleep 1\n  done\n"
            + "  printf 'failed\\n' >\"$DIR/state\"\n  exit 1\n}\n"
            + "trap restore HUP INT TERM\n"
            + "printf '%s\\n' \"$$\" >\"$DIR/pid\"\n"
            + "cat /proc/sys/kernel/random/boot_id >\"$DIR/bootid\" 2>/dev/null || exit 1\n"
            + "printf 'ready\\n' >\"$DIR/ready\"\n"
            + "last=''\n"
            + "while [ ! -f \"$DIR/stop\" ]; do\n"
            + "  expiry=$(cat \"$DIR/deadline\" 2>/dev/null)\n"
            + "  case \"$expiry\" in ''|*[!0-9]*) break;; esac\n"
            + "  read now rest <\"$CLOCK\" || break\n  now=${now%.*}\n"
            + "  case \"$now\" in ''|*[!0-9]*) break;; esac\n"
            + "  [ \"$now\" -lt \"$expiry\" ] || break\n"
            + "  target=$(cat \"$DIR/target\" 2>/dev/null)\n"
            + "  if [ -n \"$target\" ] && [ \"$target\" != \"$last\" ]; then\n"
            + "    printf '%s\\n' \"$target\" | grep -E '^[1-9][0-9]{2,4}x[1-9][0-9]{2,4}$' >/dev/null || break\n"
            + "    tw=${target%x*}; th=${target#*x}\n"
            + "    [ \"$tw\" -le " + original.width + " ] && [ \"$th\" -le " + original.height + " ] || break\n"
            + "    [ $((tw * 100)) -ge " + original.width * 70 + " ] && [ $((th * 100)) -ge " + original.height * 70 + " ] || break\n"
            + "    [ $((tw * 160)) -ge " + original.density * 320 + " ] && [ $((th * 160)) -ge " + original.density * 320 + " ] || break\n"
            + "    [ \"$tw\" -eq " + original.width + " ] || [ \"$th\" -eq " + original.height + " ] || break\n"
            + "    mode >/dev/null || break\n"
            + "    \"$WM\" size \"$target\" >\"$DIR/rollback.log\" 2>&1 || break\n"
            + "    \"$WM\" scaling off >>\"$DIR/rollback.log\" 2>&1 || break\n"
            + "    current=$(mode) || break\n    [ \"$current\" = 'EdgeMask scaling: off' ] || break\n"
            + "    out=$(\"$WM\" size 2>&1) || break\n"
            + "    printf '%s\\n' \"$out\" | grep -F -x \"Override size: $target\" >/dev/null || break\n"
            + "    last=$target; printf '%s\\n' \"$target\" >\"$DIR/applied\"\n"
            + "  fi\n  sleep 0.2 2>/dev/null || sleep 1\ndone\nrestore\n";
    }
}
