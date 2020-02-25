package kr.ac.inha.nsl

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

internal object DbMgr {
    private lateinit var db: SQLiteDatabase

    fun init(context: Context) {
        db = context.openOrCreateDatabase(context.packageName, Context.MODE_PRIVATE, null)
        db.execSQL("create table if not exists Data(id integer primary key autoincrement, dataSourceId int default(0), timestamp bigint default(0), accuracy float default(0.0), data varchar(512) default(null));")
    }

    fun saveNumericData(sensorId: Int, timestamp: Long, accuracy: Float, data: FloatArray) {
        val sb = StringBuilder()
        for (value in data) sb.append(value).append(',')
        if (sb.isNotEmpty()) sb.replace(sb.length - 1, sb.length, "")
        saveStringData(sensorId, timestamp, accuracy, sb.toString())
    }

    fun saveMixedData(sensorId: Int, timestamp: Long, accuracy: Float, vararg params: Any) {
        val sb = StringBuilder()
        for (value in params) sb.append(value).append(',')
        if (sb.isNotEmpty()) sb.replace(sb.length - 1, sb.length, "")
        saveStringData(sensorId, timestamp, accuracy, sb.toString())
    }

    private fun saveStringData(dataSourceId: Int, timestamp: Long, accuracy: Float, data: String) {
        db.execSQL("insert into Data(dataSourceId, timestamp, accuracy, data) values(?, ?, ?, ?);", arrayOf(
                dataSourceId,
                timestamp,
                accuracy,
                data
        ))
    }

    @Synchronized
    fun cleanDb() {
        db.execSQL("delete from Data;")
    }

    fun countSamples(): Int {
        val cursor = db.rawQuery("select count(*) from Data;", arrayOfNulls(0))
        var result = 0
        if (cursor.moveToFirst()) result = cursor.getInt(0)
        cursor.close()
        return result
    }

    val sensorData: Cursor
        get() = db.rawQuery("select * from Data;", arrayOfNulls(0))

    fun deleteRecord(id: Int) {
        db.execSQL("delete from Data where id=?;", arrayOf<Any>(id))
    }
}

internal object AppUseDb {
    private lateinit var db: SQLiteDatabase

    fun init(context: Context) {
        db = context.openOrCreateDatabase(context.packageName, Context.MODE_PRIVATE, null)
        db.execSQL("create table if not exists AppUse(id integer primary key autoincrement, package_name varchar(256), start_timestamp bigint, end_timestamp bigint, total_time_in_foreground bigint);")
    }

    private fun getOverlappingRecords(packageName: String, startTimestamp: Long, endTimestamp: Long): List<AppUsageRecord> {
        val res = mutableListOf<AppUsageRecord>()
        val cursor = db.rawQuery("select * from AppUse where package_name=? and (start_timestamp=$startTimestamp or end_timestamp=$endTimestamp or (start_timestamp < $startTimestamp and $startTimestamp < end_timestamp) or (start_timestamp < $endTimestamp and $endTimestamp < end_timestamp) or (start_timestamp < $startTimestamp and $endTimestamp < end_timestamp))", arrayOf(
                packageName
        ))
        if (cursor.moveToFirst())
            do {
                res.add(AppUsageRecord(
                        id = cursor.getInt(0),
                        packageName = cursor.getString(1),
                        startTimestamp = cursor.getLong(2),
                        endTimestamp = cursor.getLong(3),
                        totalTimeInForeground = cursor.getLong(4)
                ))
            } while (cursor.moveToNext())
        cursor.close()
        return res
    }

    private fun getLastRecord(packageName: String): AppUsageRecord? {
        val cursor = db.rawQuery("select * from AppUse where package_name=? order by end_timestamp desc limit(1);", arrayOf(packageName))

        return if (cursor.moveToFirst()) {
            val res = AppUsageRecord(
                    id = cursor.getInt(0),
                    packageName = cursor.getString(1),
                    startTimestamp = cursor.getLong(2),
                    endTimestamp = cursor.getLong(3),
                    totalTimeInForeground = cursor.getLong(4)
            )
            cursor.close()
            res
        } else {
            cursor.close()
            null
        }
    }

    private fun isUniqueRecord(packageName: String, startTimestamp: Long): Boolean {
        val cursor = db.rawQuery("select exists(select 1 from AppUse where package_name=? and start_timestamp=$startTimestamp)", arrayOf(packageName))
        val res = cursor.moveToFirst() && cursor.getInt(0) <= 0
        cursor.close()
        return res
    }

    fun saveAppUsageStat(packageName: String, endTimestamp: Long, totalTimeInForeground: Long) {
        val lastRecord = getLastRecord(packageName)
        if (lastRecord == null)
            db.execSQL("insert into AppUse(package_name, start_timestamp, end_timestamp, total_time_in_foreground) values(?, ?, ?, ?);", arrayOf(
                    packageName,
                    endTimestamp - totalTimeInForeground,
                    endTimestamp,
                    totalTimeInForeground
            ))
        else {
            // TODO: the interesting part
            val startTimestamp = endTimestamp - (totalTimeInForeground - lastRecord.totalTimeInForeground)
            if (startTimestamp != endTimestamp) {
                if (startTimestamp == lastRecord.endTimestamp)
                    db.execSQL("update AppUse set end_timestamp = ? and total_time_in_foreground = ? where id=?;", arrayOf(
                            endTimestamp,
                            totalTimeInForeground,
                            lastRecord.id
                    ))
                else if (isUniqueRecord(packageName, startTimestamp)) {
                    val overlappingElements = getOverlappingRecords(packageName, startTimestamp, endTimestamp)
                    if (overlappingElements.isEmpty())
                        db.execSQL("insert into AppUse(package_name, start_timestamp, end_timestamp, total_time_in_foreground) values(?, ?, ?, ?);", arrayOf(
                                packageName,
                                startTimestamp,
                                endTimestamp,
                                totalTimeInForeground
                        ))
                    else {
                        var minStartTimestamp = startTimestamp
                        var maxEndTimestamp = endTimestamp
                        var maxTotalTimeInForeground = totalTimeInForeground
                        for (appUse in overlappingElements) {
                            if (appUse.startTimestamp < minStartTimestamp)
                                minStartTimestamp = appUse.startTimestamp
                            if (appUse.endTimestamp > maxEndTimestamp)
                                maxEndTimestamp = appUse.endTimestamp
                            if (appUse.totalTimeInForeground > maxTotalTimeInForeground)
                                maxTotalTimeInForeground = appUse.totalTimeInForeground
                            db.execSQL("delete from AppUse where id=${appUse.id};")
                        }
                        db.execSQL("insert into AppUse(package_name, start_timestamp, end_timestamp, total_time_in_foreground) values(?,$minStartTimestamp,$maxEndTimestamp,$maxTotalTimeInForeground);", arrayOf(packageName))
                    }
                }
            }
        }
    }

    @Synchronized
    fun cleanDb() {
        db.execSQL("delete from Data;")
    }

    fun countSamples(): Int {
        val cursor = db.rawQuery("select count(*) from Data;", arrayOfNulls(0))
        var result = 0
        if (cursor.moveToFirst()) result = cursor.getInt(0)
        cursor.close()
        return result
    }

    val sensorData: Cursor
        get() = db.rawQuery("select * from Data;", arrayOfNulls(0))

    fun deleteRecord(id: Int) {
        db.execSQL("delete from Data where id=?;", arrayOf<Any>(id))
    }

    class AppUsageRecord(var id: Int, var packageName: String, var startTimestamp: Long, var endTimestamp: Long, var totalTimeInForeground: Long)
}