package com.moa.entity

enum class NotificationSettingType(
    val category: String,
    val title: String,
) {
    WORK("서비스 알림", "출퇴근 알림"),
    PAYDAY("서비스 알림", "월급날 알림"),
    MARKETING("광고성 정보 알림", "혜택 및 이벤트 알림"),
}
