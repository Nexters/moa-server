package com.moa.service

import com.moa.entity.Profile
import com.moa.repository.ProfileRepository
import com.moa.service.dto.ProfileResponse
import com.moa.service.dto.ProfileUpsertRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProfileService(
    private val profileRepository: ProfileRepository,
) {

    @Transactional
    fun upsertProfile(memberId: Long, req: ProfileUpsertRequest): ProfileResponse {
        val nickname = req.nickname
        val workplace = req.workplace

        val profile = profileRepository.findByMemberId(memberId)?.apply {
            this.nickname = nickname
            this.workplace = workplace
        } ?: profileRepository.save(
            Profile(
                memberId = memberId,
                nickname = nickname,
                workplace = workplace,
            )
        )

        return ProfileResponse(
            nickname = profile.nickname,
            workplace = profile.workplace,
        )
    }
}
