#!/usr/bin/env bash
set +x
set -euo pipefail
export LC_ALL=C

fail() {
  printf '%s\n' "$1" >&2
  exit 1
}

normalize() {
  local value="${1,,}"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

required=(JWT_SECRET SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD
          MINIO_ROOT_USER MINIO_ROOT_PASSWORD MINIO_ACCESS_KEY MINIO_SECRET_KEY)
for name in "${required[@]}"; do
  value="${!name-}"
  [[ ! "$value" =~ ^[[:space:]]*$ ]] || fail "$name must be configured and non-blank"
done

[[ ${#JWT_SECRET} -ge 32 ]] || fail 'JWT_SECRET must contain at least 32 UTF-8 bytes'
case "$(normalize "$JWT_SECRET")" in
  change-me*|local-only-*|test-only-*) fail 'JWT_SECRET must not use a built-in, local or test key' ;;
esac

for name in SPRING_DATASOURCE_PASSWORD MINIO_SECRET_KEY MINIO_ROOT_PASSWORD; do
  case "$(normalize "${!name}")" in
    admin|adminadmin|minioadmin|password|changeme|change-me|local-only-*|test-only-*)
      fail "$name must not use a standard, local or test password" ;;
  esac
done

[[ ${#MINIO_SECRET_KEY} -ge 8 ]] || fail 'MINIO_SECRET_KEY must contain at least 8 UTF-8 bytes'
[[ ${#MINIO_ROOT_PASSWORD} -ge 8 ]] || fail 'MINIO_ROOT_PASSWORD must contain at least 8 UTF-8 bytes'
[[ ${#MINIO_ROOT_USER} -ge 3 ]] || fail 'MINIO_ROOT_USER must contain at least 3 UTF-8 bytes'
[[ "$MINIO_ACCESS_KEY" != "$MINIO_ROOT_USER" ]] || fail 'MINIO_ACCESS_KEY must differ from MINIO_ROOT_USER'

printf '%s\n' 'Required deployment secrets passed validation.'
