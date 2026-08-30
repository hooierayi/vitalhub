#!/usr/bin/env bash

set -euo pipefail

usage() {
    printf '%s\n' \
        "Usage:" \
        "  ./scripts/build-apk.sh --name <app-name> --version <version-name> --version-code <code> --type <debug|release> [options]" \
        "" \
        "Required:" \
        "  --name          App display name written into the APK manifest." \
        "  --version       Version name written into the APK manifest." \
        "  --version-code  Positive integer version code written into the APK manifest." \
        "  --type          Build type: debug or release." \
        "" \
        "Optional:" \
        "  --output        Output directory. Defaults to <project>/artifacts/apk." \
        "  -h, --help      Show this help." \
        "" \
        "Example:" \
        "  ./scripts/build-apk.sh --name VitalHub --version 1.2.0 --version-code 12 --type debug"
}

fail() {
    printf 'Error: %s\n' "$1" >&2
    exit 1
}

sanitize_component() {
    printf '%s' "$1" | sed -E 's#[/\\:[:space:]]+#-#g; s/^-+//; s/-+$//'
}

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "$script_dir/.." && pwd)"
app_name=""
version_name=""
version_code=""
build_type=""
output_dir="$project_dir/artifacts/apk"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --name)
            [[ $# -ge 2 ]] || fail "--name requires a value."
            app_name="$2"
            shift 2
            ;;
        --version)
            [[ $# -ge 2 ]] || fail "--version requires a value."
            version_name="$2"
            shift 2
            ;;
        --version-code)
            [[ $# -ge 2 ]] || fail "--version-code requires a value."
            version_code="$2"
            shift 2
            ;;
        --type)
            [[ $# -ge 2 ]] || fail "--type requires a value."
            build_type="$2"
            shift 2
            ;;
        --output)
            [[ $# -ge 2 ]] || fail "--output requires a value."
            output_dir="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            fail "Unknown argument: $1"
            ;;
    esac
done

[[ -n "$app_name" ]] || fail "--name is required."
[[ -n "$version_name" ]] || fail "--version is required."
[[ -n "$version_code" ]] || fail "--version-code is required."
[[ "$build_type" == "debug" || "$build_type" == "release" ]] || {
    fail "--type must be debug or release."
}
if [[ ! "$version_code" =~ ^[1-9][0-9]*$ ]]; then
    fail "--version-code must be a positive integer."
fi

safe_app_name="$(sanitize_component "$app_name")"
safe_version_name="$(sanitize_component "$version_name")"
[[ -n "$safe_app_name" ]] || fail "--name does not contain a usable filename component."
[[ -n "$safe_version_name" ]] || fail "--version does not contain a usable filename component."

if [[ -n "${JAVA_HOME:-}" && ! -x "$JAVA_HOME/bin/java" ]]; then
    android_studio_jbr="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    if [[ -x "$android_studio_jbr/bin/java" ]]; then
        export JAVA_HOME="$android_studio_jbr"
    else
        unset JAVA_HOME
    fi
fi
if [[ -z "${JAVA_HOME:-}" ]] && ! java -version >/dev/null 2>&1; then
    android_studio_jbr="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    [[ -x "$android_studio_jbr/bin/java" ]] || fail "No working Java runtime was found."
    export JAVA_HOME="$android_studio_jbr"
fi

commit_full="$(git -C "$project_dir" rev-parse HEAD)"
commit_hash="$(git -C "$project_dir" rev-parse --short=8 HEAD)"
commit_subject="$(git -C "$project_dir" log -1 --format=%s)"
commit_time="$(git -C "$project_dir" log -1 --format=%aI)"
git_dirty="false"
commit_label="$commit_hash"
if ! git -C "$project_dir" diff --quiet || ! git -C "$project_dir" diff --cached --quiet; then
    git_dirty="true"
    commit_label="${commit_hash}-dirty"
fi

build_timestamp="$(date '+%Y%m%d%H%M%S')"
build_time="$(date '+%Y-%m-%d %H:%M:%S %z')"
case "$build_type" in
    debug) gradle_variant="Debug" ;;
    release) gradle_variant="Release" ;;
esac
gradle_args=(
    ":app:assemble${gradle_variant}"
    "-PappName=$app_name"
    "-PversionName=$version_name"
)
gradle_args+=("-PversionCode=$version_code")

printf 'Building %s %s (%s) from commit %s...\n' \
    "$app_name" "$version_name" "$build_type" "$commit_label"
"$project_dir/gradlew" "${gradle_args[@]}"

source_apk="$project_dir/app/build/outputs/apk/$build_type/app-$build_type.apk"
signing="signed"
if [[ "$build_type" == "release" && ! -f "$source_apk" ]]; then
    source_apk="$project_dir/app/build/outputs/apk/release/app-release-unsigned.apk"
    signing="unsigned"
fi
[[ -f "$source_apk" ]] || fail "Gradle succeeded but no APK was found for $build_type."

mkdir -p "$output_dir"
artifact_name="${safe_app_name}-${safe_version_name}-${version_code}-${commit_label}-${build_timestamp}-${build_type}.apk"
artifact_path="$output_dir/$artifact_name"
metadata_path="${artifact_path%.apk}.txt"
[[ ! -e "$artifact_path" && ! -e "$metadata_path" ]] || {
    fail "Output already exists: $artifact_path"
}
cp "$source_apk" "$artifact_path"

if command -v shasum >/dev/null 2>&1; then
    sha256="$(shasum -a 256 "$artifact_path" | awk '{print $1}')"
else
    sha256="$(sha256sum "$artifact_path" | awk '{print $1}')"
fi
size_bytes="$(wc -c < "$artifact_path" | tr -d '[:space:]')"

{
    printf 'app_name=%s\n' "$app_name"
    printf 'version_name=%s\n' "$version_name"
    printf 'version_code=%s\n' "$version_code"
    printf 'build_type=%s\n' "$build_type"
    printf 'signing=%s\n' "$signing"
    printf 'commit=%s\n' "$commit_hash"
    printf 'commit_full=%s\n' "$commit_full"
    printf 'commit_subject=%s\n' "$commit_subject"
    printf 'commit_time=%s\n' "$commit_time"
    printf 'git_dirty=%s\n' "$git_dirty"
    printf 'build_time=%s\n' "$build_time"
    printf 'size_bytes=%s\n' "$size_bytes"
    printf 'sha256=%s\n' "$sha256"
    printf 'apk=%s\n' "$artifact_path"
} > "$metadata_path"

printf '\nAPK created successfully.\n'
printf 'APK:      %s\n' "$artifact_path"
printf 'Metadata: %s\n' "$metadata_path"
printf 'SHA-256:  %s\n' "$sha256"
if [[ "$signing" == "unsigned" ]]; then
    printf 'Warning: release APK is unsigned because no release signing config is defined.\n'
fi
