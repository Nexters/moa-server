package com.moa.entity

enum class NotificationType(
    val title: String,
    val body: String
) {
    CLOCK_IN("출근 시간이에요!", "지금부터 급여가 쌓일 예정이에요."),
    CLOCK_OUT("퇴근 시간이에요!", "오늘 ₩%s 벌었어요.");

    fun getBody(vararg args: Any): String {
        return String.format(body, *args)
    }
}
