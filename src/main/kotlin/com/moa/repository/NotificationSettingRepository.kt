package com.moa.repository

import com.moa.entity.NotificationSetting
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationSettingRepository : JpaRepository<NotificationSetting, Long> {
    fun findByMemberId(memberId: Long): NotificationSetting?
}
