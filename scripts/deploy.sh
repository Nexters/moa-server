#!/bin/bash
set -euo pipefail

# ============================================================
# MOA Blue-Green 무중단 배포 스크립트
# 사용법: ./deploy.sh <docker_tag>
# ============================================================

DOCKER_TAG=${1:?"Usage: ./deploy.sh <docker_tag>"}
DOCKER_IMAGE=${DOCKER_IMAGE:-"godqhr721/moa_server"}
IMAGE="${DOCKER_IMAGE}:${DOCKER_TAG}"
APP_DIR="/home/ubuntu/app"
STATE_FILE="${APP_DIR}/active-color"
DEPLOY_HISTORY="${APP_DIR}/deploy-history"
NGINX_UPSTREAM_CONF="/etc/nginx/conf.d/moa-upstream.conf"
LOCK_FILE="${APP_DIR}/deploy.lock"
SMOKE_TEST_HOST="moa-official.kr"
HEALTH_CHECK_MAX_RETRY=30
HEALTH_CHECK_INTERVAL=2

# ============================================================
# flock: 동시 배포 방지
# ============================================================
exec 200>"${LOCK_FILE}"
if ! flock -n 200; then
    echo "[ERROR] Another deployment is already running. Exiting."
    exit 1
fi

# ============================================================
# Step 1: 현재 활성 컬러 확인
# ============================================================
if [ -f "$STATE_FILE" ] && [ "$(cat "$STATE_FILE")" = "blue" ]; then
    CURRENT="blue"
    NEXT="green"
    CURRENT_PORT=8080
    NEXT_PORT=8081
else
    CURRENT="green"
    NEXT="blue"
    CURRENT_PORT=8081
    NEXT_PORT=8080
fi

echo "========================================"
echo " Current: ${CURRENT} (:${CURRENT_PORT})"
echo " Next:    ${NEXT} (:${NEXT_PORT})"
echo " Image:   ${IMAGE}"
echo "========================================"

# ============================================================
# Step 2: 새 이미지 Pull
# ============================================================
echo "[Step 2] Pulling image: ${IMAGE}"
sudo docker pull "${IMAGE}"

# ============================================================
# Step 3: 기존 대기 컨테이너 정리 후 새 컨테이너 시작
# ============================================================
NEXT_CONTAINER="moa-${NEXT}"

if sudo docker inspect "${NEXT_CONTAINER}" &>/dev/null; then
    echo "[Step 3] Removing existing standby container: ${NEXT_CONTAINER}"
    sudo docker stop "${NEXT_CONTAINER}" 2>/dev/null || true
    sudo docker rm "${NEXT_CONTAINER}" 2>/dev/null || true
fi

echo "[Step 3] Starting new container: ${NEXT_CONTAINER} on port ${NEXT_PORT}"
sudo docker run \
    -v ${APP_DIR}/logs:/app/logs \
    --name "${NEXT_CONTAINER}" \
    --add-host host.docker.internal:host-gateway \
    --restart unless-stopped \
    -p "${NEXT_PORT}:8080" \
    -e SPRING_PROFILES_ACTIVE=prod \
    -e DEPLOY_COLOR="${NEXT}" \
    -e APP_VERSION="${DOCKER_TAG}" \
    -d "${IMAGE}"

# ============================================================
# Step 4: Health Check (최대 60초 대기)
# ============================================================
echo "[Step 4] Waiting for health check on port ${NEXT_PORT}..."

for i in $(seq 1 ${HEALTH_CHECK_MAX_RETRY}); do
    HEALTH=$(curl -sf "http://localhost:${NEXT_PORT}/actuator/health" 2>/dev/null || true)

    if echo "${HEALTH}" | grep -q '"status":"UP"'; then
        echo "[Step 4] Health check PASSED (attempt ${i}/${HEALTH_CHECK_MAX_RETRY})"
        break
    fi

    if [ "${i}" -eq "${HEALTH_CHECK_MAX_RETRY}" ]; then
        echo "[Step 4] Health check FAILED after ${HEALTH_CHECK_MAX_RETRY} attempts"
        echo "[Rollback] Stopping failed container: ${NEXT_CONTAINER}"
        sudo docker stop "${NEXT_CONTAINER}" 2>/dev/null || true
        sudo docker rm "${NEXT_CONTAINER}" 2>/dev/null || true
        echo "[Rollback] Current container (${CURRENT}) is still active. No downtime occurred."

        echo "$(date '+%Y-%m-%d %H:%M:%S')|${NEXT}|${DOCKER_TAG}|FAILED_HEALTH_CHECK" >> "${DEPLOY_HISTORY}"
        exit 1
    fi

    echo "  ... attempt ${i}/${HEALTH_CHECK_MAX_RETRY} - waiting ${HEALTH_CHECK_INTERVAL}s"
    sleep ${HEALTH_CHECK_INTERVAL}
done

# ============================================================
# Step 5: Nginx upstream 전환
# ============================================================
echo "[Step 5] Switching Nginx upstream to ${NEXT} (:${NEXT_PORT})"

sudo tee "${NGINX_UPSTREAM_CONF}" > /dev/null <<EOF
upstream moa_backend {
    server 127.0.0.1:${NEXT_PORT};
}
EOF

sudo nginx -t
sudo nginx -s reload
echo "[Step 5] Nginx reloaded successfully"

# ============================================================
# Step 6: Smoke Test (nginx 경유 확인)
# ============================================================
echo "[Step 6] Running smoke test via Nginx (HTTPS)..."
sleep 2

SMOKE_RESULT=$(curl -sf \
    --resolve "${SMOKE_TEST_HOST}:443:127.0.0.1" \
    "https://${SMOKE_TEST_HOST}/api/v1/deploy-info" 2>/dev/null || true)

if echo "${SMOKE_RESULT}" | grep -q "\"version\":\"${DOCKER_TAG}\""; then
    echo "[Step 6] Smoke test PASSED - new version confirmed via Nginx"
else
    echo "[Step 6] Smoke test FAILED - reverting Nginx to ${CURRENT} (:${CURRENT_PORT})"

    sudo tee "${NGINX_UPSTREAM_CONF}" > /dev/null <<EOF
upstream moa_backend {
    server 127.0.0.1:${CURRENT_PORT};
}
EOF
    sudo nginx -t
    sudo nginx -s reload

    sudo docker stop "${NEXT_CONTAINER}" 2>/dev/null || true
    sudo docker rm "${NEXT_CONTAINER}" 2>/dev/null || true

    echo "[Rollback] Reverted to ${CURRENT}. Smoke test response: ${SMOKE_RESULT}"
    echo "$(date '+%Y-%m-%d %H:%M:%S')|${NEXT}|${DOCKER_TAG}|FAILED_SMOKE_TEST" >> "${DEPLOY_HISTORY}"
    exit 2
fi

# ============================================================
# Step 7: 이전 컨테이너 종료 (Graceful Shutdown)
# ============================================================
CURRENT_CONTAINER="moa-${CURRENT}"

if sudo docker inspect "${CURRENT_CONTAINER}" &>/dev/null; then
    echo "[Step 7] Stopping previous container: ${CURRENT_CONTAINER} (graceful, 70s timeout)"
    sudo docker stop --time 70 "${CURRENT_CONTAINER}" 2>/dev/null || true
    sudo docker rm "${CURRENT_CONTAINER}" 2>/dev/null || true
fi

# ============================================================
# Step 8: 상태 파일 + 배포 이력 업데이트
# ============================================================
echo "${NEXT}" | sudo tee "${STATE_FILE}" > /dev/null
echo "$(date '+%Y-%m-%d %H:%M:%S')|${NEXT}|${DOCKER_TAG}|SUCCESS" >> "${DEPLOY_HISTORY}"

# ============================================================
# Step 9: Docker 이미지 정리
# ============================================================
echo "[Step 9] Cleaning up unused Docker images..."
sudo docker image prune -f

echo "========================================"
echo " Deploy complete!"
echo " Active: ${NEXT} (:${NEXT_PORT})"
echo " Version: ${DOCKER_TAG}"
echo "========================================"
