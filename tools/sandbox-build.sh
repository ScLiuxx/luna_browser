#!/usr/bin/env bash
# 沙箱构建入口。
#
# 沙箱会在会话之间清空 /root/.gradle 与 /root/android-sdk，
# 这份脚本负责把它们装回来，并给出一个能在这个环境里跑通的 gradle 调用。
#
# 用法：
#   bash tools/sandbox-build.sh :app:compileDebugKotlin
#   bash tools/sandbox-build.sh :app:assembleDebug :app:assembleRelease
#
# 注意：项目 gradle.properties 写死了 -Xmx4096m，而沙箱 cgroup 上限只有 4 GiB，
# daemon 会被 OOM killer 干掉（表现为 "daemon disappeared unexpectedly"）。
# 这里用命令行参数覆盖内存设置——命令行优先级高于 gradle.properties。
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
CACHE="${SANDBOX_TOOLCHAIN_CACHE:-/tmp/liuli-toolchain}"
GRADLE_VERSION="9.7.1"
DIST_NAME="gradle-${GRADLE_VERSION}-bin"
# 目录名是 wrapper 按 distributionUrl 算出的哈希，不能随意改。
DIST_DIR="/root/.gradle/wrapper/dists/${DIST_NAME}/1w1c7tv4s851m17nbqdsro2tv"
SDK_ROOT="${ANDROID_SDK_ROOT:-/root/android-sdk}"

# ------------------------------------------------------------ 工具链下载
mkdir -p "${CACHE}"
if [ ! -f "${CACHE}/gradle.zip" ]; then
  echo "==> 下载 Gradle ${GRADLE_VERSION}"
  curl -sSL --max-time 900 -o "${CACHE}/gradle.zip" \
    "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
fi
if [ ! -f "${CACHE}/cmdline-tools.zip" ]; then
  echo "==> 下载 Android 命令行工具"
  curl -sSL --max-time 900 -o "${CACHE}/cmdline-tools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
fi

# ---------------------------------------------------------------- Gradle
if [ ! -d "${DIST_DIR}/${GRADLE_VERSION}" ]; then
  echo "==> 安装 Gradle ${GRADLE_VERSION}"
  mkdir -p "${DIST_DIR}"
  rm -f "${DIST_DIR}"/*.part "${DIST_DIR}"/*.lck
  unzip -q -o "${CACHE}/gradle.zip" -d "${DIST_DIR}"
  touch "${DIST_DIR}/${DIST_NAME}.zip.ok"
fi

# ------------------------------------------------------------ Android SDK
if [ ! -d "${SDK_ROOT}/platforms/android-37.0" ]; then
  echo "==> 安装 Android SDK"
  mkdir -p "${SDK_ROOT}/cmdline-tools"
  rm -rf "${CACHE}/cmdline-extract"
  unzip -q -o "${CACHE}/cmdline-tools.zip" -d "${CACHE}/cmdline-extract"
  rm -rf "${SDK_ROOT}/cmdline-tools/latest"
  mv "${CACHE}/cmdline-extract/cmdline-tools" "${SDK_ROOT}/cmdline-tools/latest"

  export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
  SDKMANAGER="${SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager"
  yes | "${SDKMANAGER}" --sdk_root="${SDK_ROOT}" --licenses > /dev/null 2>&1 || true
  "${SDKMANAGER}" --sdk_root="${SDK_ROOT}" \
    "platform-tools" "platforms;android-37.0" "build-tools;37.0.0" > /dev/null
fi

# ------------------------------------------------------------ 构建配置
mkdir -p /root/.gradle/init.d

cat > /root/.gradle/gradle.properties <<'PROPS'
# 沙箱通过本地代理出网。Gradle 不读 http_proxy 环境变量，必须显式声明。
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=18080
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=18080
systemProp.http.nonProxyHosts=localhost|127.0.0.1
PROPS

cat > /root/.gradle/init.d/mirror.gradle <<'MIRROR'
// Aliyun 镜像版本滞后（缺 AGP 9.3.x），Google Maven 必须直连。
def mirrorUrl = 'https://maven.aliyun.com/repository/public'
def googleUrl = 'https://dl.google.com/dl/android/maven2'
def gradlePluginUrl = 'https://maven.aliyun.com/repository/gradle-plugin'

beforeSettings { settings ->
    settings.pluginManagement {
        repositories {
            maven { url = googleUrl }
            maven { url = gradlePluginUrl }
            maven { url = mirrorUrl }
            gradlePluginPortal()
        }
    }
    settings.dependencyResolutionManagement {
        repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
        repositories {
            maven { url = googleUrl }
            maven { url = mirrorUrl }
        }
    }
}

allprojects {
    buildscript {
        repositories {
            maven { url = googleUrl }
            maven { url = gradlePluginUrl }
            maven { url = mirrorUrl }
        }
    }
}
MIRROR

echo "sdk.dir=${SDK_ROOT}" > "${REPO}/local.properties"

# ------------------------------------------------------------------ 构建
cd "${REPO}"
exec ./gradlew \
  -Dorg.gradle.jvmargs="-Xmx1700m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8" \
  -Dkotlin.daemon.jvmargs="-Xmx1200m" \
  --max-workers=2 \
  --no-parallel \
  "$@"
