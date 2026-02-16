package com.moa.service

import com.moa.entity.Profile
import com.moa.repository.ProfileRepository
import com.moa.service.dto.ProfileResponse
import com.moa.service.dto.OnboardingProfileUpsertRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProfileService(
    private val profileRepository: ProfileRepository,
) {

    @Transactional
    fun upsertProfile(memberId: Long, req: OnboardingProfileUpsertRequest): ProfileResponse {
        val nickname = req.nickname

        val profile = profileRepository.findByMemberId(memberId)?.apply {
            this.nickname = nickname
        } ?: profileRepository.save(
            Profile(
                memberId = memberId,
                nickname = nickname,
            )
        )

        return ProfileResponse(
            nickname = profile.nickname,
            workplace = profile.workplace,
        )
    }
}
