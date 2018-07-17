package com.basicfu.sip.core.util

import com.basicfu.sip.core.common.Constant
import com.basicfu.sip.core.model.dto.UserDto
import java.util.*

object TokenUtil {

    /**
     * 生成token
     */
    fun generateToken(): String {
        return UUID.randomUUID().toString().replace("-", "")
    }

    /**
     * 获取当前token
     */
    fun getCurrentToken(): String? {
        return RequestUtil.getHeader(Constant.System.AUTHORIZATION) ?: return null
    }

    /**
     * 获取当前用户
     */
    fun getCurrentUser(): UserDto? {
        val token = RequestUtil.getHeader(Constant.System.AUTHORIZATION) ?: return null
        return RedisUtil.get<UserDto>(token)
    }

}
