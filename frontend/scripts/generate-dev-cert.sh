#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
SSL_DIR="${FRONTEND_DIR}/.ssl"
KEY_FILE="${SSL_DIR}/key.pem"
CERT_FILE="${SSL_DIR}/cert.pem"

mkdir -p "${SSL_DIR}"
umask 077

if command -v mkcert >/dev/null 2>&1; then
  echo "使用 mkcert 生成受本机信任的开发证书..."
  mkcert -install
  mkcert \
    -key-file "${KEY_FILE}" \
    -cert-file "${CERT_FILE}" \
    localhost 127.0.0.1 ::1
elif command -v openssl >/dev/null 2>&1; then
  echo "未找到 mkcert，使用 OpenSSL 生成自签名开发证书..."
  openssl req \
    -x509 \
    -newkey rsa:2048 \
    -sha256 \
    -nodes \
    -days 365 \
    -keyout "${KEY_FILE}" \
    -out "${CERT_FILE}" \
    -subj "/CN=localhost" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1,IP:0:0:0:0:0:0:0:1" \
    -addext "keyUsage=digitalSignature,keyEncipherment" \
    -addext "extendedKeyUsage=serverAuth"

  echo "提示：OpenSSL 生成的是自签名证书，浏览器首次访问时可能显示安全警告。"
  echo "如需自动信任，请安装 mkcert 后重新运行本脚本。"
else
  echo "错误：未找到 mkcert 或 openssl，无法生成开发证书。" >&2
  exit 1
fi

chmod 600 "${KEY_FILE}"
chmod 644 "${CERT_FILE}"

echo "开发证书已生成："
echo "  私钥：${KEY_FILE}"
echo "  证书：${CERT_FILE}"
echo "这两个文件仅供本地开发使用，请勿提交到版本库。"
