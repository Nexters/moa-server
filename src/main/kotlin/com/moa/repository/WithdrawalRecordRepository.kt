package com.moa.repository

import com.moa.entity.WithdrawalRecord
import org.springframework.data.jpa.repository.JpaRepository

interface WithdrawalRecordRepository : JpaRepository<WithdrawalRecord, Long> {
}
