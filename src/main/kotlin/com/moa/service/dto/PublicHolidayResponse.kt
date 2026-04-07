package com.moa.service.dto

import com.moa.entity.PublicHoliday
import java.time.LocalDate

data class PublicHolidayResponse(
    val id: Long,
    val date: LocalDate,
    val name: String,
) {
    companion object {
        fun from(entity: PublicHoliday) = PublicHolidayResponse(
            id = entity.id,
            date = entity.date,
            name = entity.name,
        )
    }
}
