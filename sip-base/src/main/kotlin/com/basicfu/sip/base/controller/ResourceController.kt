package com.basicfu.sip.base.controller

import com.basicfu.sip.base.model.vo.ResourceVo
import com.basicfu.sip.base.service.ResourceService
import com.basicfu.sip.common.constant.Constant
import com.basicfu.sip.core.model.Result
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

/**
 * @author basicfu
 * @date 2018/7/9
 */
@RestController
@RequestMapping("/resource")
class ResourceController {
    @Autowired
    lateinit var resourceService: ResourceService

    @GetMapping("/list")
    fun list(vo: ResourceVo): Result<Any> {
        return Result.success(resourceService.list(vo))
    }

    @GetMapping("/all")
    fun all(): Result<Any> {
        return Result.success(resourceService.all())
    }

    @GetMapping("/suggest")
    fun suggest(@RequestParam q: String, @RequestParam(defaultValue = Constant.System.PAGE_SIZE_STR) limit: Int): Result<Any> {
        return Result.success(resourceService.suggest(q, limit))
    }

    @PostMapping("/sync")
    fun sync(@RequestBody vo: ResourceVo): Result<Any> {
        return resourceService.sync(vo)
    }

    @PostMapping("/insert")
    fun insert(@RequestBody vo: ResourceVo): Result<Any> {
        return Result.success(resourceService.insert(vo))
    }

    @PostMapping("/update")
    fun update(@RequestBody vo: ResourceVo): Result<Any> {
        return Result.success(resourceService.update(vo))
    }

    @DeleteMapping("/delete")
    fun delete(@RequestBody ids: List<Long>): Result<Any> {
        return Result.success(resourceService.delete(ids))
    }
}
