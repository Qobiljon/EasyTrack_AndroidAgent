package kr.ac.inha.nsl;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

class DbMgr {
    private static SQLiteDatabase db;

    static void init(Context context) {
        db = context.openOrCreateDatabase(context.getPackageName(), Context.MODE_PRIVATE, null);
        db.execSQL("create table if not exists Data(id integer primary key autoincrement, dataSourceId int default(0), timestamp bigint default(0), accuracy float default(0.0), data varchar(512) default(null));");
    }

    static void saveNumericData(int sensorId, long timestamp, float accuracy, float[] data) {
        StringBuilder sb = new StringBuilder();
        for (float value : data)
            sb.append(value).append(',');
        if (sb.length() > 0)
            sb.replace(sb.length() - 1, sb.length(), "");
        saveStringData(sensorId, timestamp, accuracy, sb.toString());
    }

    static void saveMixedData(int sensorId, long timestamp, float accuracy, Object... params) {
        StringBuilder sb = new StringBuilder();
        for (Object value : params)
            sb.append(value).append(',');
        if (sb.length() > 0)
            sb.replace(sb.length() - 1, sb.length(), "");
        saveStringData(sensorId, timestamp, accuracy, sb.toString());
    }

    static void saveStringData(int dataSourceId, long timestamp, float accuracy, String data) {
        db.execSQL("insert into Data(dataSourceId, timestamp, accuracy, data) values(?, ?, ?, ?);", new Object[]{
                dataSourceId,
                timestamp,
                accuracy,
                data
        });
    }

    static synchronized void cleanDb() {
        db.execSQL("delete from Data;");
    }

    static int countSamples() {
        Cursor cursor = db.rawQuery("select count(*) from Data;", new String[0]);
        int result = 0;
        if (cursor.moveToFirst())
            result = cursor.getInt(0);
        cursor.close();
        return result;
    }

    static Cursor getSensorData() {
        return db.rawQuery("select * from Data;", new String[0]);
    }

    static void deleteRecord(int id) {
        db.execSQL("delete from Data where id=?;", new Object[]{id});
    }
}
