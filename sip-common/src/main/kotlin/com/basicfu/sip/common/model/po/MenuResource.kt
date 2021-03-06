package com.basicfu.sip.common.model.po

import javax.persistence.*

@Table(name = "menu_resource")
class MenuResource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    @Column(name = "app_id")
    var appId: Long? = null
    @Column(name = "menu_id")
    var menuId: Long? = null
    @Column(name = "resource_id")
    var resourceId: Long? = null
    var cdate: Int? = null
}
