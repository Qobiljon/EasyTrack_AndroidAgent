package kr.ac.inha.nsl;

import android.database.Cursor;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;

public class DbViewActivity extends AppCompatActivity {

    private WebView wvDbTable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_db_view);

        wvDbTable = findViewById(R.id.wvDbTable);
        wvDbTable.setMinimumWidth(2000);
        findViewById(R.id.btnRefreshWindow).callOnClick();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(0, 0);
    }

    public void refreshWindowClick(View view) {
        StringBuilder sb = new StringBuilder("<!DOCTYPE html><html><style type=\"text/css\">table {border-collapse: collapse;} th,td{padding: 5px; white-space: nowrap;}</style><body><table border=\"1\"><thead><th>INDEX</th><th>SENSOR_ID</th><th>TIMESTAMP</th><th>ACCURACY</th><th>RAW DATA(HEX)</th></thead><tbody>");
        Cursor cursor = Tools.db.rawQuery("SELECT sensorId, timestamp, accuracy, data FROM SensorRecords;", new String[0]);

        int index = 0;
        if (cursor.moveToFirst())
            do {
                byte sensorId = (byte) cursor.getShort(0);
                long timestamp = cursor.getLong(1);
                int accuracy = cursor.getInt(2);
                byte[] data = cursor.getBlob(3);

                sb.append("<tr><td>");
                sb.append(index++);
                sb.append("</td><td>");
                sb.append(sensorId);
                sb.append("</td><td>");
                sb.append(timestamp);
                sb.append("</td><td>");
                sb.append(accuracy);
                sb.append("</td><td>");
                sb.append(Tools.bytes2hexString(data));
                sb.append("</td></tr>");
            } while (cursor.moveToNext());

        sb.append("</tbody></table></body><html>");
        cursor.close();
        wvDbTable.loadData(sb.toString(), "text/html", "UTF-8");
    }
}
