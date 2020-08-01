package com.ruiyu.jsontodart

import com.ruiyu.jsontodart.utils.*
import com.ruiyu.utils.Inflector
import com.ruiyu.utils.toLowerCaseFirstOne
import com.ruiyu.utils.toUpperCaseFirstOne


/**
 * User: zhangruiyu
 * Date: 2019/12/23
 * Time: 11:32
 */
class HelperFileGeneratorInfo(val imports: MutableList<String> = mutableListOf(), val classes: MutableList<HelperClassGeneratorInfo> = mutableListOf())

class HelperClassGeneratorInfo {
    //协助的类名
    lateinit var className: String
    val fields: MutableList<Filed> = mutableListOf()


    fun addFiled(type: String, name: String, annotationValue: List<AnnotationValue>?) {
        fields.add(Filed(type, name).apply {
            this.annotationValue = annotationValue
        })
    }


    override fun toString(): String {
        val sb = StringBuffer()
        sb.append(jsonParseFunc())
        sb.append("\n")
        sb.append("\n")
        sb.append(jsonGenFunc())
        return sb.toString()
    }

    //生成fromjson方法
    private fun jsonParseFunc(): String {
        val sb = StringBuffer();
        sb.append("\n")
        sb.append("${className.toLowerCaseFirstOne()}FromJson(${className} data, Map<String, dynamic> json) {\n")
        fields.forEach { k ->
            //如果deserialize不是false,那么就解析,否则不解析
            if (k.getValueByName<Boolean>("deserialize") != false) {
                sb.append("\t${jsonParseExpression(k)}\n")
            }
        }
        sb.append("\treturn data;\n")
        sb.append("}")
        return sb.toString()
    }

    private fun jsonParseExpression(filed: Filed): String {
        val type = filed.type
        val name = filed.name
        //从json里取值的名称
        val getJsonName = filed.getValueByName("name") ?: name
        //是否是基础数据类型
        val isPrimitive = PRIMITIVE_TYPES[type] ?: false
        //是否是list
        val isListType = isListType(type)
        return when {
            isPrimitive -> {
                when {
                    isListType -> {
                        "if (json['$getJsonName'] != null) {\n\t\tdata.$name = json['$getJsonName']?.map((v) => v${buildToType(getListSubType(type))})?.toList()?.cast<${getListSubType(type)}>();\n\t}"
                    }
                    type == "DateTime" -> {
                        if (filed.getValueByName<String>("format")?.isNotEmpty() == true) {
                            "if(json['$getJsonName'] != null){\n\t\tDateFormat format = new DateFormat(${filed.getValueByName<String>("format")});\n\t\tdata.$name = format.parse(json['$getJsonName'].toString());\n\t}"
                        } else {
                            "if(json['$getJsonName'] != null){\n\t\tdata.$name = DateTime.tryParse(json['$getJsonName']);\n\t}"
                        }

                    }
                    else -> {
                        "if (json['$getJsonName'] != null) {\n\t\tdata.$name = json['$getJsonName']${buildToType(type)};\n\t}"
                    }
                }
            }
            isListType -> { // list of class  //如果是list,就把名字修改成单数
                //类名
                val listSubType = getListSubType(type)
                val value = when (listSubType) {
                    "dynamic" -> "data.${name}.addAll(json['$getJsonName']);"
                    "DateTime" ->
                        if (filed.getValueByName<String>("format")?.isNotEmpty() == true) {
                            "\n\t\tDateFormat format = new DateFormat(${filed.getValueByName<String>("format")});\n\t\t\t\t(json['$getJsonName'] as List).forEach((v) {\n\t\t\t\t\tif (v != null)\n\t\t\t\t\t\tdata.$name.add(format.parse(v.toString()));\n\t\t\t\t});".trimIndent()
                        } else {
                            "(json['$getJsonName'] as List).forEach((v) {\n\t\t\tdata.$name.add(DateTime.parse(v));\n\t\t});".trimIndent()
                        }
                    else -> "(json['$getJsonName'] as List).forEach((v) {\n\t\t\tdata.$name.add(new ${listSubType}().fromJson(v));\n\t\t});".trimIndent()
                }
                "if (json['$getJsonName'] != null) {\n\t\tdata.$name = new List<${listSubType}>();\n\t\t$value\n\t}"
            }
            else -> // class
                "if (json['$getJsonName'] != null) {\n\t\tdata.$name = new $type().fromJson(json['$getJsonName']);\n\t}"
        }
    }

    //生成tojson方法
    private fun jsonGenFunc(): String {
        val sb = StringBuffer();
        sb.append("Map<String, dynamic> ${className.toLowerCaseFirstOne()}ToJson(${className} entity) {\n");
        sb.append("\tfinal Map<String, dynamic> data = new Map<String, dynamic>();\n");
        fields.forEach { k ->
            //如果serialize不是false,那么就解析,否则不解析
            if (k.getValueByName<Boolean>("serialize") != false) {
                sb.append("\t${toJsonExpression(k)}\n")
            }
        }
        sb.append("\treturn data;\n");
        sb.append("}");
        return sb.toString()

    }

    private fun toJsonExpression(filed: Filed): String {
        val type = filed.type
        val name = filed.name
        //从json里取值的名称
        val getJsonName = filed.getValueByName("name") ?: name
        //是否是基础数据类型
        val isPrimitive = PRIMITIVE_TYPES[type] ?: false
        //是否是list
        val isListType = isListType(type)
        val thisKey = "entity.$name"
        //是否包含format注解
        val formatString = filed.getValueByName<String>("format")
        val isContainsDateFormat = formatString?.isNotEmpty() == true
        when {
            isPrimitive -> {
                if (type == "DateTime") {
                    return if (isContainsDateFormat) {
                        "if (${thisKey} != null) {\n" +
                                "    DateFormat format = new DateFormat($formatString);\n" +
                                "    data['${getJsonName}'] = format.format(${thisKey});\n" +
                                "  }"
                    } else "data['${getJsonName}'] = ${thisKey}?.toString();"
                }
                return "data['$getJsonName'] = $thisKey;"
            }
            isListType -> {
                //类名
                val listSubType = getListSubType(type)
                //是否是list<DateTime>类型
                val isListDateTime = listSubType == "DateTime"
                val value = if (listSubType == "dynamic") "[]" else if (listSubType == "DateTime") {
                    if (isContainsDateFormat)
                        "${thisKey}\n" +
                                "        .map((v) => format.format(v))\n" +
                                "        .toList()\n" +
                                "        .cast<String>()"
                    else "${thisKey}\n" +
                            "        .map((v) => v?.toString())\n" +
                            "        .toList()\n" +
                            "        .cast<String>()"
                } else "$thisKey.map((v) => v.toJson()).toList()"
                // class list
                return "if ($thisKey != null) {${if (isListDateTime && isContainsDateFormat) "\n\t\tDateFormat format = new DateFormat(${formatString});" else ""}\n\t\tdata['$getJsonName'] =  $value;\n\t}"
            }
            else -> {
                // class
                return "if ($thisKey != null) {\n\t\tdata['$getJsonName'] = ${thisKey}.toJson();\n\t}"
            }
        }
    }

    private fun buildToJsonClass(expression: String): String {
        return "$expression().toJson()"
    }

    private fun buildToType(typeName: String): String {
        return when {
            typeName.equals("int", true) -> {
                "?.toInt()"
            }
            typeName.equals("double", true) -> {
                "?.toDouble()"
            }
            typeName.equals("string", true) -> {
                "?.toString()"
            }
            else -> ""
        }
    }


}

class Filed constructor(
        //字段类型
        var type: String,
        //字段名字
        var name: String) {

    //待定
    var isPrivate: Boolean? = null
    //注解的值
    var annotationValue: List<AnnotationValue>? = null

    fun <T> getValueByName(name: String): T? {
        return annotationValue?.firstOrNull { it.name == name }?.getValueByName()
    }
}

@Suppress("UNCHECKED_CAST")
class AnnotationValue(val name: String, private val value: Any) {
    fun <T> getValueByName(): T {
        return value as T
    }
}