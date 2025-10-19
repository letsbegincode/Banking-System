#!/usr/bin/env bash
set -euo pipefail

JAVA_BIN="${JAVA_BIN:-java}"

if [[ -z "${BANKING_JDBC_URL:-}" ]]; then
  echo "[migrate] BANKING_JDBC_URL must be set for JDBC migrations" >&2
  exit 1
fi

export BANKING_STORAGE_MODE="${BANKING_STORAGE_MODE:-jdbc}"
if [[ "${BANKING_STORAGE_MODE}" != "jdbc" ]]; then
  echo "[migrate] BANKING_STORAGE_MODE forced to 'jdbc' for database migrations" >&2
  export BANKING_STORAGE_MODE="jdbc"
fi

if [[ -n "${BANKING_MIGRATION_CLASSPATH:-}" ]]; then
  CLASSPATH="${BANKING_MIGRATION_CLASSPATH}"
else
  declare -a entries=()
  if [[ -f "build/banking-api.jar" ]]; then
    entries+=("build/banking-api.jar")
  fi
  if [[ -d "build/classes" ]]; then
    entries+=("build/classes")
  fi
  if [[ ${#entries[@]} -eq 0 ]]; then
    echo "[migrate] Unable to locate compiled classes. Set BANKING_MIGRATION_CLASSPATH." >&2
    exit 1
  fi
  CLASSPATH="$(IFS=:; echo "${entries[*]}")"
fi

echo "[migrate] Running JDBC migrations against ${BANKING_JDBC_URL}"
"${JAVA_BIN}" -cp "${CLASSPATH}" \
  banking.persistence.repository.DatabaseMigrationCli
