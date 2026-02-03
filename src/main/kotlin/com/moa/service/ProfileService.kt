package com.moa.service

import com.moa.entity.Profile
import com.moa.repository.ProfileRepository
import com.moa.service.dto.ProfileResponse
import com.moa.service.dto.ProfileUpsertRequest
import com.moa.service.dto.WorkplaceDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProfileService(
    private val profileRepository: ProfileRepository,
) {

    @Transactional
    fun upsertProfile(memberId: Long, req: ProfileUpsertRequest): ProfileResponse {
        val nickname = req.nickname
        val workplaceName = req.workplace.name

        val profile = profileRepository.findByMemberId(memberId)?.apply {
            this.nickname = nickname
            this.workplaceName = workplaceName
        } ?: profileRepository.save(
            Profile(
                memberId = memberId,
                nickname = nickname,
                workplaceName = workplaceName,
            )
        )

        return ProfileResponse(
            nickname = profile.nickname,
            workplace = WorkplaceDto(profile.workplaceName),
        )
    }
}
