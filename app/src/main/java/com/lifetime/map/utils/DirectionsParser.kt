package com.lifetime.map.utils

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import java.util.ArrayList
import java.util.HashMap

class DirectionsParser {

    fun parse(jObject: JSONObject): List<List<HashMap<String, String>>> {
        val routes = ArrayList<List<HashMap<String, String>>>()
        val jRoutes: JSONArray
        var jLeg: JSONArray
        var jManeuver: JSONArray

        try {
            jRoutes = jObject.getJSONObject("response").getJSONArray("route")

            //Loop for all route
            for (i in 0 until jRoutes.length()) {
                val path = ArrayList<HashMap<String, String>>()
                jLeg = (jRoutes.get(i) as JSONObject).getJSONArray("leg")

                //Loop for all waypoint
                for (j in 0 until jLeg.length()) {
                    jManeuver = (jLeg.get(j) as JSONObject).getJSONArray("maneuver")

                    //Loop for all maneuver
                    for (k in 0 until jManeuver.length()) {
                        val jPosition = (jManeuver.get(k) as JSONObject).getJSONObject("position")
                        val hm = HashMap<String, String>()
                        hm["lat"] = jPosition.getString("latitude")
                        hm["long"] = jPosition.getString("longitude")
                        path.add(hm)
                    }
                    routes.add(path)
                }
            }

        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return routes
    }
}
