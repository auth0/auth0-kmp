#!/usr/bin/env bash
#
# new-module.sh — scaffold a new published Auth0 KMP feature module.
#
# Usage:  ./scripts/new-module.sh <name>
# Example: ./scripts/new-module.sh mfa   ->  module :auth0-mfa
#
# What it does (idempotent — safe to re-run):
#   1. creates auth0-<name>/build.gradle.kts  (minimal KMP + api(auth0-core) + auth0.publish)
#   2. creates auth0-<name>/src/commonMain/kotlin/com/auth0/kmp/<name>/
#   3. adds   include(":auth0-<name>")            to settings.gradle.kts
#   4. adds   api(project(":auth0-<name>"))       to auth0/build.gradle.kts   (umbrella)
#      and    export(project(":auth0-<name>"))    to auth0/build.gradle.kts   (iOS framework)
#
# Umbrella export is OPT-OUT: a new module joins the umbrella api+export by
# default. If this module should NOT ship in the flagship `Auth0` framework,
# delete those two lines from auth0/build.gradle.kts after running.
#
# Every write is verified; the script aborts loudly and never half-applies.

set -euo pipefail

# --- locate repo root (script lives in <root>/scripts) -----------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

SETTINGS="$ROOT/settings.gradle.kts"
UMBRELLA="$ROOT/auth0/build.gradle.kts"

# --- validate argument -------------------------------------------------------
if [ $# -ne 1 ]; then
    echo "usage: $0 <name>   (e.g. $0 mfa  ->  :auth0-mfa)" >&2
    exit 2
fi

NAME="$1"
if ! [[ "$NAME" =~ ^[a-z][a-z0-9]*$ ]]; then
    echo "ERROR: <name> must be a single lowercase word ([a-z][a-z0-9]*): got '$NAME'" >&2
    echo "       it becomes the package segment com.auth0.kmp.$NAME, which can't contain '-'." >&2
    exit 2
fi

MODULE="auth0-$NAME"                       # gradle path segment / dir name
MODULE_DIR="$ROOT/$MODULE"
NAMESPACE="com.auth0.kmp.$NAME"
PKG_DIR="$MODULE_DIR/src/commonMain/kotlin/com/auth0/kmp/$NAME"

# baseName = Auth0 + Capitalized name  (portable — no GNU sed \U)
FIRST="$(printf '%s' "${NAME:0:1}" | tr '[:lower:]' '[:upper:]')"
BASENAME="Auth0${FIRST}${NAME:1}"

# --- preflight guards (abort before writing anything) ------------------------
if [ -e "$MODULE_DIR" ]; then
    echo "ERROR: $MODULE/ already exists — refusing to overwrite." >&2
    exit 1
fi
[ -f "$SETTINGS" ] || { echo "ERROR: not found: $SETTINGS" >&2; exit 1; }
[ -f "$UMBRELLA" ] || { echo "ERROR: not found: $UMBRELLA" >&2; exit 1; }

echo "Scaffolding module :$MODULE  (namespace $NAMESPACE, framework $BASENAME)"

# --- 1. module build.gradle.kts ---------------------------------------------
mkdir -p "$MODULE_DIR"
cat > "$MODULE_DIR/build.gradle.kts" <<EOF
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    id("auth0.publish")
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "$BASENAME"
            isStatic = true
        }
    }

    android {
        namespace = "$NAMESPACE"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        withHostTest { }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.auth0Core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
EOF
echo "  ✓ created $MODULE/build.gradle.kts"

# --- 2. source package dir ---------------------------------------------------
mkdir -p "$PKG_DIR"
echo "  ✓ created $MODULE/src/commonMain/kotlin/com/auth0/kmp/$NAME/"

# --- helper: insert a line after the LAST line matching an anchor -----------
# idempotent (skips if the exact line is already present) and verified.
insert_after_last() {
    local file="$1" anchor="$2" newline="$3"
    if grep -qF "$newline" "$file"; then
        echo "  • $(basename "$file"): already has '$(echo "$newline" | sed 's/^ *//')' — skipping"
        return 0
    fi
    local ln
    ln="$(grep -n "$anchor" "$file" | tail -1 | cut -d: -f1)"
    if [ -z "$ln" ]; then
        echo "ERROR: anchor '$anchor' not found in $file — build files may have changed shape." >&2
        echo "       No further edits made. Add the line manually." >&2
        exit 1
    fi
    awk -v n="$ln" -v ins="$newline" 'NR==n{print; print ins; next} {print}' "$file" > "$file.tmp"
    mv "$file.tmp" "$file"
    if ! grep -qF "$newline" "$file"; then
        echo "ERROR: insertion into $file did not land — aborting." >&2
        exit 1
    fi
}

# --- 3. settings.gradle.kts: include(":auth0-<name>") ------------------------
# anchor = last existing auth0 module include (sample-app includes don't match).
insert_after_last "$SETTINGS" '^include(":auth0' "include(\":$MODULE\")"
echo "  ✓ settings.gradle.kts + include(\":$MODULE\")"

# --- 4. auth0/build.gradle.kts: umbrella api + export ------------------------
# 12-space indent matches the existing api(...) / export(...) entries.
insert_after_last "$UMBRELLA" 'export(project(":auth0' "            export(project(\":$MODULE\"))"
insert_after_last "$UMBRELLA" 'api(project(":auth0'    "            api(project(\":$MODULE\"))"
echo "  ✓ auth0/build.gradle.kts + api + export (umbrella, opt-out)"

echo
echo "Done. Next:"
echo "  • ./gradlew :$MODULE:tasks            # sync / sanity-check the module"
echo "  • if :$MODULE should NOT ship in the Auth0 iOS framework, remove its"
echo "    api(project(\":$MODULE\")) + export(...) lines from auth0/build.gradle.kts"
