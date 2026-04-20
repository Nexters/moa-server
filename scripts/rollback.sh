#!/bin/bash
set -euo pipefail

# ============================================================
# MOA 수동 롤백 스크립트
# 사용법:
#   ./rollback.sh <docker_tag>   → 지정한 버전으로 배포
#   ./rollback.sh                → 직전 성공 버전으로 배포
# ============================================================

DEPLOY_HISTORY="/home/ubuntu/app/deploy-history"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ -n "${1:-}" ]; then
    ROLLBACK_TAG="$1"
    echo "[Rollback] Target version specified: ${ROLLBACK_TAG}"
else
    if [ ! -f "${DEPLOY_HISTORY}" ]; then
        echo "[ERROR] Deploy history file not found: ${DEPLOY_HISTORY}"
        echo "Cannot determine previous version. Please specify a docker tag."
        echo "Usage: ./rollback.sh <docker_tag>"
        exit 1
    fi

    CURRENT_TAG=$(grep "|SUCCESS" "${DEPLOY_HISTORY}" | tail -1 | cut -d'|' -f3)
    ROLLBACK_TAG=$(grep "|SUCCESS" "${DEPLOY_HISTORY}" | grep -v "|${CURRENT_TAG}|" | tail -1 | cut -d'|' -f3)

    if [ -z "${ROLLBACK_TAG}" ]; then
        echo "[ERROR] No previous successful deployment found in history."
        echo "Deploy history contents:"
        cat "${DEPLOY_HISTORY}"
        exit 1
    fi

    echo "[Rollback] Previous successful version found: ${ROLLBACK_TAG}"
    echo "  (Current version: ${CURRENT_TAG})"
fi

echo "========================================"
echo " Rolling back to: ${ROLLBACK_TAG}"
echo "========================================"
echo ""

# deploy.sh를 재호출하여 롤백 실행
# → 동일한 health check, smoke test, graceful shutdown 안전장치 적용
exec bash "${SCRIPT_DIR}/deploy.sh" "${ROLLBACK_TAG}"
