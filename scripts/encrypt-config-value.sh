#!/usr/bin/env bash

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JASYPT_VERSION="3.0.5"

if ! command -v mvn >/dev/null 2>&1; then
  echo "错误：未找到 Maven。请先安装 Maven 3.9 或兼容版本。" >&2
  exit 1
fi

if [[ $# -gt 1 ]]; then
  echo "用法：$0 [待加密值]" >&2
  echo "省略参数时会以隐藏方式读取待加密值。" >&2
  exit 1
fi

if [[ $# -eq 1 ]]; then
  PLAIN_VALUE="$1"
else
  read -r -s -p "请输入待加密值：" PLAIN_VALUE
  echo
fi

read -r -s -p "请输入加密主密钥：" MASTER_PASSWORD
echo
read -r -s -p "请再次输入加密主密钥：" MASTER_PASSWORD_CONFIRM
echo

if [[ -z "${PLAIN_VALUE}" ]]; then
  echo "错误：待加密值不能为空。" >&2
  exit 1
fi

if [[ -z "${MASTER_PASSWORD}" ]]; then
  echo "错误：加密主密钥不能为空。" >&2
  exit 1
fi

if [[ "${MASTER_PASSWORD}" != "${MASTER_PASSWORD_CONFIRM}" ]]; then
  echo "错误：两次输入的加密主密钥不一致。" >&2
  exit 1
fi

echo "正在生成 ENC(...) 密文；首次运行可能需要下载 Maven 插件..."

PLUGIN_OUTPUT="$({
  cd "${PROJECT_DIR}"
  JASYPT_ENCRYPTOR_PASSWORD="${MASTER_PASSWORD}" mvn \
    "com.github.ulisesbocchio:jasypt-maven-plugin:${JASYPT_VERSION}:encrypt-value" \
    "-Djasypt.plugin.value=${PLAIN_VALUE}"
} 2>&1)" || {
  echo "加密失败。Maven 输出：" >&2
  echo "${PLUGIN_OUTPUT}" >&2
  exit 1
}

ENCRYPTED_VALUE="$(printf '%s\n' "${PLUGIN_OUTPUT}" | grep -Eo 'ENC\([^[:space:]]+\)' | tail -n 1 || true)"

unset PLAIN_VALUE MASTER_PASSWORD MASTER_PASSWORD_CONFIRM

if [[ -z "${ENCRYPTED_VALUE}" ]]; then
  echo "错误：插件执行完成，但没有识别到 ENC(...) 输出。" >&2
  echo "${PLUGIN_OUTPUT}" >&2
  exit 1
fi

echo
echo "生成成功："
echo "${ENCRYPTED_VALUE}"
echo
echo "启动应用前，请通过环境变量 JASYPT_ENCRYPTOR_PASSWORD 提供刚才输入的主密钥。"
