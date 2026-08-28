// The presence of `module-info.java` puts hegel on the module path. `requires dev.hegel;`
// names the automatic module by its committed `Automatic-Module-Name`; if that name ever
// changes, this no longer resolves and the build fails — which is the regression we guard.
module dev.hegel.consumer {
    requires dev.hegel;
}
