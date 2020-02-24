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

    fun saveMixedData(sensorId: Int, timestamp: Long, accuracy: Float, vararg params: Any?) {
        val sb = StringBuilder()
        for (value in params) sb.append(value).append(',')
        if (sb.isNotEmpty()) sb.replace(sb.length - 1, sb.length, "")
        saveStringData(sensorId, timestamp, accuracy, sb.toString())
    }

    fun saveStringData(dataSourceId: Int, timestamp: Long, accuracy: Float, data: String) {
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