package com.basicfu.sip.getway.common.datasource

class DataSourceContextHolder {
    enum class DataSourceType {
        BASE
    }

    companion object {
        private val contextHolder = ThreadLocal<DataSourceType>()
        fun get(): String {
            return contextHolder.get().name
        }

        fun base() {
            contextHolder.set(DataSourceType.BASE)
        }
    }

}
