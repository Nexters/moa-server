package com.moa.entity.notification

import com.moa.entity.notification.NotificationStatus.Companion.INACTIVE_STATUSES


enum class NotificationStatus {
    PENDING,
    SENT,
    FAILED,
    CANCELLED,
    EXPIRED;

    companion object {
        /**
         * "활성" 상태 집합 — 멱등성 체크에서 이 상태의 행이 존재하면 새 알림을 만들지 않는다.
         *
         * - PENDING / SENT / FAILED 는 시스템이 발송을 시도했거나 시도 중인 상태로 본다.
         * - 반대 의미인 [INACTIVE_STATUSES] (CANCELLED / EXPIRED) 는 absent 로 간주되어 재생성이 허용된다.
         */
        val ACTIVE_STATUSES: List<NotificationStatus> = listOf(PENDING, SENT, FAILED)

        /**
         * "비활성" 상태 집합 — 알림이 더 이상 흐름에 남아있지 않으며 absent 로 간주된다.
         *
         * - CANCELLED: 사용자가 명시적으로 취소 (예: 휴가 등록)
         * - EXPIRED: 시스템이 TTL 초과로 만료 처리
         */
        val INACTIVE_STATUSES: List<NotificationStatus> = listOf(CANCELLED, EXPIRED)
    }
}
