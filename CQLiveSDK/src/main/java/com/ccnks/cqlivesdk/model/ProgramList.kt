package com.ccnks.cqlivesdk.model

import com.alibaba.fastjson.annotation.JSONField

class ProgramList {
    @JSONField(name = "ret")
    var ret: Int = -1

    @JSONField(name = "reason")
    var reason: String = "unknown"

    @JSONField(name = "data")
    var data = ArrayList<Program>()

    override fun toString(): String {
        return "ret=$ret, reason=$reason, data=$data"
    }

    class Program {
        @JSONField(name = "date")
        var date: String = ""

        @JSONField(name = "week")
        var week: String = ""

        @JSONField(name = "playbill_list")
        var playbillList = ArrayList<Playbill>()

        override fun toString(): String {
            return "{date=$date, week=$week, playbillList=$playbillList}"
        }
    }

    class Playbill {
        @JSONField(name = "id")
        var id: String = ""

        @JSONField(name = "name")
        var name: String = ""

        @JSONField(name = "begin_time")
        var beginTime: String = ""

        @JSONField(name = "end_time")
        var endTime: String = ""

        @JSONField(name = "play_status")
        var status: Int = 0

        override fun toString(): String {
            return "{id=$id, name=$name, beginTime=$beginTime, endTime=$endTime, status=$status}"
        }
    }
}