package com.basicfu.sip.client.util

import com.basicfu.sip.client.feign.DictFeign
import com.basicfu.sip.core.model.dto.DictDto
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

/**
 * @author basicfu
 * @date 2018/6/28
 */
@Component
class DictUtil {
    @Autowired
    private lateinit var dictFeignTmp: DictFeign

    @PostConstruct
    fun init() {
        DictUtil.dictFeign = dictFeignTmp
    }

    companion object {
        private lateinit var dictFeign: DictFeign

        /**
         * 根据字典value获取字典
         */
        fun get(value:String): List<DictDto>? {
            return dictFeign.get(value).data
        }
    }
}