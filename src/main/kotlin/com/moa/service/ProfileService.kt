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
    fun upsertProfile(req: ProfileUpsertRequest): ProfileResponse {
        val nickname = req.nickname
        val workplaceName = req.workplace.name

        // TODO. Member 연동 후 member.profile = profile 방식으로 1:1 연결 예정
        val profile = profileRepository.findAll().firstOrNull()?.apply {
            this.nickname = nickname
            this.workplaceName = workplaceName
        } ?: profileRepository.save(
            Profile(
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
