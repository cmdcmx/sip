package com.basicfu.sip.base.service

import com.basicfu.sip.base.mapper.UserAuthMapper
import com.basicfu.sip.base.model.po.UserAuth
import com.basicfu.sip.core.service.BaseService
import org.springframework.stereotype.Service

/**
 * @author basicfu
 * @date 2018/7/4
 */
@Service
class UserAuthService : BaseService<UserAuthMapper, UserAuth>()
