package com.moa.entity

enum class NotificationType(
    val title: String,
    val body: String
) {
    CLOCK_IN("출근 했어요!", "지금부터 급여가 쌓일 예정이에요."),
    CLOCK_OUT("퇴근 했어요!", "오늘 ₩%s 벌었어요."),
    PAYDAY("오늘은 월급날이에요!", "한 달 동안 고생 많으셨어요.");

    fun getBody(vararg args: Any): String {
        return String.format(body, *args)
    }
}
