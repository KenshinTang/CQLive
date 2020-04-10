package com.kapplication.cqlivesdk.model

import com.alibaba.fastjson.annotation.JSONField

class CategoryList {
    @JSONField(name = "ret")
    var ret: Int = -1

    @JSONField(name = "reason")
    var reason: String = "unknown"

    @JSONField(name = "data")
    var data = ArrayList<Category>()

    override fun toString(): String {
        return "ret=$ret, reason=$reason, data=$data"
    }

    class Category {
        @JSONField(name = "category_id")
        var id: String = ""

        @JSONField(name = "category_name")
        var name: String = ""

        @JSONField(name = "default_icon_img_url")
        var icon: String = ""

        @JSONField(name = "live_list")
        var channelList = ArrayList<Channel>()

        override fun toString(): String {
            return "{id=$id, name=$name, icon=$icon, channelList=$channelList}"
        }
    }

    class Channel {
        @JSONField(name = "live_id")
        var id: String = ""

        @JSONField(name = "live_name")
        var name: String = ""

        @JSONField(name = "live_number")
        var number: String = ""

        @JSONField(name = "icon_img_url")
        var icon: String = ""

        @JSONField(name = "play_url")
        var playUrl: String = ""

        override fun toString(): String {
            return "{id=$id, name=$name, number=$number, icon=$icon, playUrl=$playUrl}"
        }
    }
}