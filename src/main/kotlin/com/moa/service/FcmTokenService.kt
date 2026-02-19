package com.moa.service

import com.moa.common.exception.ErrorCode
import com.moa.common.exception.ForbiddenException
import com.moa.common.exception.NotFoundException
import com.moa.entity.FcmToken
import com.moa.repository.FcmTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FcmTokenService(
    private val fcmTokenRepository: FcmTokenRepository,
) {

    @Transactional
    fun registerToken(memberId: Long, token: String) {
        fcmTokenRepository.findByToken(token)
            ?.let { it.memberId = memberId }
            ?: fcmTokenRepository.save(FcmToken(memberId = memberId, token = token))
    }

    @Transactional
    fun deleteToken(memberId: Long, token: String) {
        val fcmToken = fcmTokenRepository.findByToken(token)
            ?: throw NotFoundException()

        if (fcmToken.memberId != memberId) {
            throw ForbiddenException(ErrorCode.TOKEN_DELETE_FORBIDDEN)
        }

        fcmTokenRepository.delete(fcmToken)
    }
}
