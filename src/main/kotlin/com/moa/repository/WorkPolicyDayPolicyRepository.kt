package com.moa.repository

import com.moa.entity.WorkPolicyDayPolicy
import org.springframework.data.jpa.repository.JpaRepository

interface WorkPolicyDayPolicyRepository : JpaRepository<WorkPolicyDayPolicy, Long> {
    fun existsByWorkPolicyVersionId(workPolicyVersionId: Long): Boolean

    fun findAllByWorkPolicyVersionId(workPolicyVersionId: Long): List<WorkPolicyDayPolicy>

    fun deleteAllByWorkPolicyVersionId(workPolicyVersionId: Long)
}
