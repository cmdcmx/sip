package com.basicfu.sip.core.common.interceptor

import com.alibaba.druid.sql.SQLUtils
import com.alibaba.druid.sql.ast.SQLExpr
import com.alibaba.druid.sql.ast.expr.*
import com.alibaba.druid.sql.ast.statement.*
import com.basicfu.sip.core.common.autoconfig.Config
import com.basicfu.sip.core.common.constant.CoreConstant
import com.basicfu.sip.core.util.RequestUtil
import com.basicfu.sip.core.util.ThreadLocalUtil
import com.google.common.base.CaseFormat
import com.mysql.jdbc.DatabaseMetaData
import org.apache.commons.lang3.NotImplementedException
import org.apache.commons.lang3.StringUtils
import org.apache.ibatis.executor.Executor
import org.apache.ibatis.executor.statement.StatementHandler
import org.apache.ibatis.mapping.MappedStatement
import org.apache.ibatis.mapping.SqlCommandType
import org.apache.ibatis.plugin.*
import org.apache.ibatis.reflection.SystemMetaObject
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.util.ReflectionUtils
import java.sql.Connection
import java.util.*

//@Intercepts(
//    Signature(type = Executor::class, method = "update", args = arrayOf(MappedStatement::class, Any::class)),
//    Signature(
//        type = Executor::class,
//        method = "query",
//        args = arrayOf(MappedStatement::class, Any::class, RowBounds::class, ResultHandler::class)
//    ),
//    Signature(
//        type = Executor::class,
//        method = "query",
//        args = arrayOf(
//            MappedStatement::class,
//            Any::class,
//            RowBounds::class,
//            ResultHandler::class,
//            CacheKey::class,
//            BoundSql::class
//        )
//    )
//)
/**
 * @author basicfu
 * @date 2018/9/4
 * sql拦截器部分查询可能无法拦截(需加query会拦截两次，待具体测试)
 */
@Component
@Intercepts(
    Signature(type = StatementHandler::class, method = "prepare", args = arrayOf(Connection::class, Integer::class)),
    Signature(type = Executor::class, method = "update", args = arrayOf(MappedStatement::class, Any::class))
//    Signature(type = StatementHandler::class, method = "query", args = arrayOf(Statement::class, ResultHandler::class))
)
class SqlInterceptor : Interceptor {
    val dialect = "MYSQL"
    @Autowired
    lateinit var config: Config

    override fun intercept(invocation: Invocation): Any {
        val proceed: Any
        var throwError = false
        val count = ThreadLocalUtil.get<Int>(CoreConstant.NOT_CHECK_APP)
        try {
            if (count != null) {
                return invocation.proceed()
            }
            //过滤应用在实际修改的地方判断，在连库查询时数据库可能不一样
            if (invocation.method.name == "prepare") {
                val metaData = (invocation.args[0] as Connection).metaData
                val field = ReflectionUtils.findField(DatabaseMetaData::class.java, "database")!!
                field.isAccessible = true
                val databaseName = field.get(metaData).toString()
                val statementHandler = invocation.target as StatementHandler
                val boundSql = statementHandler.boundSql
                @Suppress("UNCHECKED_CAST")
                val metaObject = SystemMetaObject.forObject(statementHandler)
                metaObject.setValue("delegate.boundSql.sql", addCondition(databaseName, boundSql.sql, config.appField))
            } else if (invocation.method.name == "update") {
                //当为insert时设置bean的appId
                //当为手动sql时目前不支持自动添加appId
                //过滤应用
                /**
                 * 这里无法获取到数据库名,因此只判断了表明
                 */
                val mappedStatement = (invocation.args[0] as MappedStatement)
                if (mappedStatement.sqlCommandType == SqlCommandType.INSERT) {
                    val statementList =
                        SQLUtils.parseStatements(mappedStatement.getBoundSql(invocation.args[1]).sql, dialect)
                    val tableName = (statementList[0] as SQLInsertStatement).tableName.simpleName
                    val allTable = arrayListOf<String>()
                    config.appExecuteTable.values.forEach {
                        allTable.addAll(it)
                    }
                    if (!allTable.contains(tableName)) {
                        val bean = invocation.args[1]
                        val appField = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, config.appField)
                        val appId: Long = getAppId()
                            ?: //log
                            throw RuntimeException("not found app code")
                        if (bean is HashMap<*, *>) {
                            val list = bean["list"] as ArrayList<*>
                            list.forEach {
                                val field = it::class.java.getDeclaredField(appField)
                                field.isAccessible = true
                                field.set(it, appId)
                            }
                        } else {
                            val field = bean::class.java.getDeclaredField(appField)
                            field.isAccessible = true
                            field.set(bean, appId)
                        }
                    }
                }
            }
            proceed = invocation.proceed()
        } catch (e: Exception) {
            //出错一定释放
            throwError = true
            ThreadLocalUtil.remove(CoreConstant.NOT_CHECK_APP)
            throw e
        } finally {
            //如果出错就不在处理此处，因为上面已经处理删除标识
            if (!throwError && count != null) {
                //如果非>=1不处理，需要主动释放
                if (count == 1) {
                    ThreadLocalUtil.remove(CoreConstant.NOT_CHECK_APP)
                } else if (count > 1) {
                    ThreadLocalUtil[CoreConstant.NOT_CHECK_APP] = count - 1
                }
            }
        }
        return proceed
    }

    override fun plugin(target: Any): Any {
        return Plugin.wrap(target, this)
    }

    override fun setProperties(properties: Properties) {
    }

    fun addCondition(databaseName: String, sql: String, field: String): String {
        val statementList = SQLUtils.parseStatements(sql, dialect)
        if (statementList == null || statementList.size == 0) return sql
        val sqlStatement = statementList[0]
        when (sqlStatement) {
            is SQLSelectStatement -> {
                val queryObject = sqlStatement.select.query as SQLSelectQueryBlock
                addSelectStatementCondition(databaseName, queryObject, queryObject.from, field)
            }
            is SQLUpdateStatement -> addUpdateStatementCondition(databaseName, sqlStatement, field)
            is SQLDeleteStatement -> addDeleteStatementCondition(databaseName, sqlStatement, field)
            is SQLInsertStatement -> addInsertStatementCondition(databaseName, sqlStatement, field)
        }
        return SQLUtils.toSQLString(statementList, dialect)
    }

    private fun addSelectStatementCondition(
        databaseName: String,
        queryObject: SQLSelectQueryBlock?,
        from: SQLTableSource?,
        fieldName: String
    ) {
        if (StringUtils.isBlank(fieldName) || from == null || queryObject == null) return
        val originCondition = queryObject.where
        var dbName = databaseName
        when (from) {
            is SQLExprTableSource -> {
                var tableName = ""
                if (from.expr is SQLIdentifierExpr) {
                    tableName = (from.expr as SQLIdentifierExpr).name
                } else if (from.expr is SQLPropertyExpr) {
                    //此种情况目前只发现在`数据库名.表明`的情况，取owner中的库名
                    val sqlPropertyExpr = from.expr as SQLPropertyExpr
                    dbName = sqlPropertyExpr.ownernName.replace("`", "")
                    tableName = sqlPropertyExpr.name
                }
                val alias = from.alias
                val newCondition =
                    newEqualityCondition(dbName, tableName, alias, fieldName, originCondition)
                queryObject.where = newCondition
            }
            is SQLJoinTableSource -> {
                val joinObject = from as SQLJoinTableSource?
                val left = joinObject!!.left
                val right = joinObject.right

                addSelectStatementCondition(dbName, queryObject, left, fieldName)
                addSelectStatementCondition(dbName, queryObject, right, fieldName)

            }
            is SQLSubqueryTableSource -> {
                val subSelectObject = from.select
                val subQueryObject = subSelectObject.query as SQLSelectQueryBlock
                addSelectStatementCondition(dbName, subQueryObject, subQueryObject.from, fieldName)
            }
            else -> throw NotImplementedException("未处理的异常")
        }
    }

    private fun addInsertStatementCondition(
        databaseName: String,
        insertStatement: SQLInsertStatement?,
        fieldName: String
    ) {
        if (insertStatement != null) {
            val sqlSelect = insertStatement.query
            if (sqlSelect != null) {
                val selectQueryBlock = sqlSelect.query as SQLSelectQueryBlock
                addSelectStatementCondition(
                    databaseName,
                    selectQueryBlock,
                    selectQueryBlock.from,
                    fieldName
                )
            }
        }
    }

    private fun addUpdateStatementCondition(
        databaseName: String,
        updateStatement: SQLUpdateStatement,
        fieldName: String
    ) {
        val where = updateStatement.where
        //添加子查询中的where条件
        addSQLExprCondition(databaseName, where, fieldName)
        val newCondition = newEqualityCondition(
            databaseName, updateStatement.tableName.simpleName,
            updateStatement.tableSource.alias, fieldName, where
        )
        updateStatement.where = newCondition
    }

    private fun addDeleteStatementCondition(
        databaseName: String,
        deleteStatement: SQLDeleteStatement,
        fieldName: String
    ) {
        val where = deleteStatement.where
        addSQLExprCondition(databaseName, where, fieldName)
        val newCondition = newEqualityCondition(
            databaseName, deleteStatement.tableName.simpleName,
            deleteStatement.tableSource.alias, fieldName, where
        )
        deleteStatement.where = newCondition

    }

    /**
     * 拼接修改sql
     * 判断是否要过滤应用
     */
    private fun newEqualityCondition(
        databaseName: String,
        tableName: String,
        tableAlias: String?,
        fieldName: String,
        originCondition: SQLExpr?
    ): SQLExpr? {
        val executeTable = config.appExecuteTable[databaseName]
        return if (executeTable != null && executeTable.contains(tableName)) {
            originCondition
        } else {
            val appId: Long = getAppId()
                ?: //log
                throw RuntimeException("not found app code")
            val filedName = if (StringUtils.isBlank(tableAlias)) fieldName else "$tableAlias.$fieldName"
            val condition =
                SQLBinaryOpExpr(SQLIdentifierExpr(filedName), SQLCharExpr(appId.toString()), SQLBinaryOperator.Equality)
            return SQLUtils.buildCondition(SQLBinaryOperator.BooleanAnd, condition, false, originCondition)
        }
    }

    private fun addSQLExprCondition(databaseName: String, where: SQLExpr, fieldName: String) {
        when (where) {
            is SQLInSubQueryExpr -> {
                val subSelectObject = where.getSubQuery()
                val subQueryObject = subSelectObject.query as SQLSelectQueryBlock
                addSelectStatementCondition(databaseName, subQueryObject, subQueryObject.from, fieldName)
            }
            is SQLBinaryOpExpr -> {
                val left = where.left
                val right = where.right
                addSQLExprCondition(databaseName, left, fieldName)
                addSQLExprCondition(databaseName, right, fieldName)
            }
            is SQLQueryExpr -> {
                val selectQueryBlock = where.getSubQuery().query as SQLSelectQueryBlock
                addSelectStatementCondition(
                    databaseName,
                    selectQueryBlock,
                    selectQueryBlock.from,
                    fieldName
                )
            }
        }
    }

    private fun getAppId(): Long? {
        return RequestUtil.getParameter("_appId")?.toLong()
    }

}
