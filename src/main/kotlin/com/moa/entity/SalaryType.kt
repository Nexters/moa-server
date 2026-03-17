package com.moa.entity

/**
 * 급여 산정 방식을 정의하는 열거형입니다.
 */
enum class SalaryType {
    /** 연봉 기반 산정 방식 */
    YEARLY,

    /** 월급 기반 산정 방식 */
    MONTHLY;

    companion object {
        /**
         * 입력된 급여 유형([SalaryInputType])을 내부 처리용 급여 산정 방식([SalaryType])으로 변환합니다.
         *
         * @param inputType 외부에서 입력된 급여 유형 (예: ANNUAL, MONTHLY)
         * @return 매핑된 [SalaryType] 인스턴스
         */
        fun from(inputType: SalaryInputType): SalaryType = when (inputType) {
            SalaryInputType.ANNUAL -> YEARLY
            SalaryInputType.MONTHLY -> MONTHLY
        }
    }
}
