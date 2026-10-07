install_module() {
  rm -rf $TMPDIR
  mkdir -p $TMPDIR
  chcon u:object_r:system_file:s0 $TMPDIR
  cd $TMPDIR

  setup_flashable
  mount_partitions
  api_level_arch_detect

  # Setup busybox and binaries
  if $BOOTMODE; then
    boot_actions
  else
    recovery_actions
  fi

  # Extract prop file
  unzip -o "$ZIPFILE" module.prop -d $TMPDIR >&2
  [ ! -f $TMPDIR/module.prop ] && abort "! This zip is not a Magisk module!"

  local MODDIRNAME=modules
  $BOOTMODE && MODDIRNAME=modules_update
  local MODULEROOT=/data/adb/$MODDIRNAME
  MODID=$(grep_prop id $TMPDIR/module.prop)
  MODNAME=$(grep_prop name $TMPDIR/module.prop)
  MODAUTH=$(grep_prop author $TMPDIR/module.prop)
  MODPATH=$MODULEROOT/$MODID

  # Create mod paths
  rm -rf $MODPATH
  mkdir -p $MODPATH
  chcon u:object_r:system_file:s0 $MODPATH

  if is_legacy_script; then
    unzip -oj "$ZIPFILE" module.prop install.sh uninstall.sh 'common/*' -d $TMPDIR >&2

    # Load install script
    . $TMPDIR/install.sh

    # Callbacks
    print_modname
    on_install

    [ -f $TMPDIR/uninstall.sh ] && cp -af $TMPDIR/uninstall.sh $MODPATH/uninstall.sh
    $SKIPMOUNT && touch $MODPATH/skip_mount
    $PROPFILE && cp -af $TMPDIR/system.prop $MODPATH/system.prop
    cp -af $TMPDIR/module.prop $MODPATH/module.prop
    $POSTFSDATA && cp -af $TMPDIR/post-fs-data.sh $MODPATH/post-fs-data.sh
    $LATESTARTSERVICE && cp -af $TMPDIR/service.sh $MODPATH/service.sh

    ui_print "- Setting permissions"
    set_permissions
  else
    print_title "$MODNAME" "by $MODAUTH"
    print_title "Powered by Magisk"

    unzip -o "$ZIPFILE" customize.sh -d $MODPATH >&2

    if ! grep -q '^SKIPUNZIP=1$' $MODPATH/customize.sh 2>/dev/null; then
      ui_print "- Extracting module files"
      unzip -o "$ZIPFILE" -x 'META-INF/*' -d $MODPATH >&2
      set_default_perm $MODPATH
    fi

    # Load customization script
    [ -f $MODPATH/customize.sh ] && . $MODPATH/customize.sh
  fi

  # Handle replace folders
  for TARGET in $REPLACE; do
    ui_print "- Replace target: $TARGET"
    mktouch $MODPATH$TARGET/.replace
  done

  for TARGET in $REMOVE; do
    ui_print "- Remove target: $TARGET"
    mkdir -p $(dirname $MODPATH$TARGET) 2>/dev/null
    mknod $MODPATH$TARGET c 0 0
  done

  if $BOOTMODE; then
    # Update info for Magisk app
    mktouch /data/adb/modules/$MODID/update
    rm -rf /data/adb/modules/$MODID/remove 2>/dev/null
    rm -rf /data/adb/modules/$MODID/disable 2>/dev/null
    cp -af $MODPATH/module.prop /data/adb/modules/$MODID/module.prop
  fi

  # Copy over custom sepolicy rules
  if [ -f $MODPATH/sepolicy.rule ]; then
    ui_print "- Installing custom sepolicy rules"
    copy_preinit_files
  fi

  # Remove stuff that doesn't belong to modules and clean up any empty directories
  rm -rf \
  $MODPATH/system/placeholder $MODPATH/customize.sh \
  $MODPATH/README.md $MODPATH/.git*
  rmdir -p $MODPATH 2>/dev/null

  cd /
  $BOOTMODE || recovery_cleanup
  rm -rf $TMPDIR

  ui_print "- Done"
}
