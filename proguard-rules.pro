# R8 rules shared by every app's release build (each app's build.gradle.kts points here).
#
# Shrink and optimize, but don't obfuscate: crash stack traces in bug reports stay readable
# without a per-release mapping file. The apps use no reflection and have no dependencies beyond
# :core/:contacts, so the default rules plus aapt's generated keeps (manifest components, views
# in layouts) are all R8 needs.
-dontobfuscate
