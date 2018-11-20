package com.basicfu.sip.permission.service

import com.basicfu.sip.common.constant.Constant
import com.basicfu.sip.common.enum.Enum
import com.basicfu.sip.common.model.dto.ResourceDto
import com.basicfu.sip.core.common.exception.CustomException
import com.basicfu.sip.core.common.mapper.example
import com.basicfu.sip.core.common.mapper.generate
import com.basicfu.sip.core.service.BaseService
import com.basicfu.sip.core.util.SqlUtil
import com.basicfu.sip.permission.mapper.PermissionMapper
import com.basicfu.sip.permission.mapper.PermissionResourceMapper
import com.basicfu.sip.permission.mapper.ResourceMapper
import com.basicfu.sip.common.model.dto.PermissionDto
import com.basicfu.sip.permission.model.po.Permission
import com.basicfu.sip.common.model.po.PermissionResource
import com.basicfu.sip.common.util.AppUtil
import com.basicfu.sip.permission.model.po.Resource
import com.basicfu.sip.permission.model.vo.PermissionVo
import com.github.pagehelper.PageInfo
import org.apache.commons.lang3.StringUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * @author basicfu
 * @date 2018/7/9
 */
@Service
class PermissionService : BaseService<PermissionMapper, Permission>() {
    @Autowired
    lateinit var permissionResourceMapper: PermissionResourceMapper
    @Autowired
    lateinit var resourceMapper: ResourceMapper
    @Autowired
    lateinit var roleService: RoleService

    fun list(vo: PermissionVo): PageInfo<PermissionDto> {
        val pageInfo = selectPage<PermissionDto>(example<Permission> {
            andLike {
                name = vo.q
                code = vo.q
            }
            orderByDesc(Permission::cdate)
        })
        val ids = pageInfo.list.map { it.id!! }
        if (ids.isNotEmpty()) {
            val permissionResourceMap = permissionResourceMapper.selectByExample(example<PermissionResource> {
                select(PermissionResource::permissionId, PermissionResource::resourceId)
                andIn(PermissionResource::permissionId, ids)
            }).groupBy { it.permissionId }
            pageInfo.list.forEach {
                it.resourceCount = permissionResourceMap[it.id!!]?.size?.toLong() ?: 0
            }
        }
        return pageInfo
    }

    /**
     * 查询permissionResource,resource自己的appId和sip的，appNotCheck目前无法满足，分页时有count语句
     */
    fun listResourceById(id: Long, q: String?): PageInfo<ResourceDto> {
        val likeValue = SqlUtil.dealLikeValue(q)
        startPage()
        val appIds= arrayListOf(AppUtil.getAppId())
        val appCode = AppUtil.getAppCode()
        if(Constant.System.APP_SYSTEM_CODE!=appCode){
            appIds.add(AppUtil.getAppIdByAppCode(Constant.System.APP_SYSTEM_CODE))
        }
        var sql =
            "select r.id as id,service_id as serviceId,url,method,name,pr.cdate as cdate from permission_resource pr " +
                    "LEFT JOIN resource r on pr.resource_id=r.id WHERE pr.permission_id=$id " +
                    "and pr.app_id in (${StringUtils.join(appIds,",")}) and r.app_id in (${StringUtils.join(appIds,",")})"
        likeValue?.let { sql += " and (r.url like $likeValue or r.name like $likeValue)" }
        sql += " ORDER BY pr.cdate DESC"
        AppUtil.notCheckApp(2)
        val result = resourceMapper.selectBySql(sql)
        return getPageInfo(result)
    }

    fun all(): List<PermissionDto> = to(mapper.selectAll())
    fun insert(vo: PermissionVo): Int {
        if (mapper.selectCount(generate {
                name = vo.name
            }) != 0) throw CustomException(Enum.EXIST_PERMISSION_NAME)
        if (mapper.selectCount(generate {
                code = vo.code
            }) != 0) throw CustomException(Enum.EXIST_PERMISSION_CODE)
        val po = dealInsert(to<Permission>(vo))
        return mapper.insertSelective(po)
    }

    /**
     * 允许添加自己和sip的资源
     */
    fun insertResource(vo: PermissionVo): Int {
        var ids = vo.resourceIds!!
        AppUtil.notCheckApp()
        val appIds= arrayListOf(AppUtil.getAppId())
        val appCode = AppUtil.getAppCode()
        if(Constant.System.APP_SYSTEM_CODE!=appCode){
            appIds.add(AppUtil.getAppIdByAppCode(Constant.System.APP_SYSTEM_CODE))
        }
        if (resourceMapper.selectCountByExample(example<Resource> {
                andIn(Resource::appId,appIds)
                andIn(Resource::id, ids)
            }) != ids.size) throw CustomException(Enum.NOT_FOUND_RESOURCE)
        val existsResourceIds = permissionResourceMapper.selectByExample(example<PermissionResource> {
            andEqualTo(PermissionResource::permissionId, vo.id)
            andIn(PermissionResource::resourceId, ids)
        }).map { it.resourceId }
        ids = ids.filter { !existsResourceIds.contains(it) }
        if (ids.isEmpty()) {
            throw CustomException(Enum.EXIST_ADD_DATA)
        }
        val permissionResources = arrayListOf<PermissionResource>()
        ids.forEach { it ->
            val pr = PermissionResource()
            pr.permissionId = vo.id
            pr.resourceId = it
            permissionResources.add(dealInsert(pr))
        }
        val count=permissionResourceMapper.insertList(permissionResources)
        roleService.refreshRolePermission()
        return count
    }

    fun update(vo: PermissionVo): Int {
        val checkPermissionName = mapper.selectOne(generate {
            name = vo.name
        })
        if (checkPermissionName != null && checkPermissionName.id != vo.id) throw CustomException(
            Enum.EXIST_PERMISSION_NAME
        )
        val checkPermissionCode = mapper.selectOne(generate {
            code = vo.code
        })
        if (checkPermissionCode != null && checkPermissionCode.id != vo.id) throw CustomException(
            Enum.EXIST_PERMISSION_CODE
        )
        val po = dealUpdate(to<Permission>(vo))
        return mapper.updateByPrimaryKeySelective(po)
    }

    fun delete(ids: List<Long>): Int {
        if (ids.isNotEmpty()) {
            permissionResourceMapper.deleteByExample(example<PermissionResource> {
                andIn(PermissionResource::permissionId, ids)
            })
        }
        val count= deleteByIds(ids)
        roleService.refreshRolePermission()
        return count
    }

    fun deleteResource(vo: PermissionVo): Int {
        val count= permissionResourceMapper.deleteByExample(example<PermissionResource> {
            andEqualTo(PermissionResource::permissionId, vo.id)
            andIn(PermissionResource::resourceId, vo.resourceIds!!)
        })
        roleService.refreshRolePermission()
        return count
    }
}
