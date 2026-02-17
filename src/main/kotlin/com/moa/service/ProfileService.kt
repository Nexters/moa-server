package com.moa.service

import com.moa.common.exception.NotFoundException
import com.moa.entity.Profile
import com.moa.repository.ProfileRepository
import com.moa.service.dto.NicknameUpdateRequest
import com.moa.service.dto.ProfileResponse
import com.moa.service.dto.OnboardingProfileUpsertRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProfileService(
    private val profileRepository: ProfileRepository,
) {

    @Transactional(readOnly = true)
    fun getProfile(memberId: Long): ProfileResponse {
        val profile = profileRepository.findByMemberId(memberId)
            ?: throw NotFoundException()
        return ProfileResponse(
            nickname = profile.nickname,
            workplace = profile.workplace,
        )
    }

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

    @Transactional
    fun updateNickname(memberId: Long, req: NicknameUpdateRequest): ProfileResponse {
        val profile = profileRepository.findByMemberId(memberId)
            ?: throw NotFoundException()

        profile.nickname = req.nickname

        return ProfileResponse(
            nickname = profile.nickname,
            workplace = profile.workplace,
        )
    }
}
