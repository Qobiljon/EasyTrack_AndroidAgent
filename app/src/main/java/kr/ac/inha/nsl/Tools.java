package kr.ac.inha.nsl;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static android.content.Context.MODE_PRIVATE;

@SuppressWarnings("all")
class SensorTypes {
    static final byte ALL = 0x00;
    static final byte ACCELEROMETER = 0x01;
    static final byte GRAVITY = 0x02;
    static final byte LINEAR_ACCELERATION = 0x03;
    static final byte MAGNETIC = 0x04;
    static final byte ROTATION_VECTOR = 0x05;
    static final byte ORIENTATION = 0x06;
    static final byte GYROSCOPE = 0x07;
    static final byte LIGHT = 0x08;
    static final byte PROXIMITY = 0x09;
    static final byte PRESSURE = 0x0A;
    static final byte ULTRAVIOLET = 0x0B;
    static final byte TEMPERATURE = 0x0C;
    static final byte HUMIDITY = 0x0D;
    static final byte HRM = 0x0E;
    static final byte HRM_LED_GREEN = 0x0F;
    static final byte HRM_LED_IR = 0x10;
    static final byte HRM_LED_RED = 0x11;
    static final byte GYROSCOPE_UNCALIBRATED = 0x12;
    static final byte GEOMAGNETIC_UNCALIBRATED = 0x13;
    static final byte GYROSCOPE_ROTATION_VECTOR = 0x14;
    static final byte GEOMAGNETIC_ROTATION_VECTOR = 0x15;
    static final byte SIGNIFICANT_MOTION = 0x16;
    static final byte HUMAN_PEDOMETER = 0x17;
    static final byte HUMAN_SLEEP_MONITOR = 0x18;
    static final byte HUMAN_SLEEP_DETECTOR = 0x19;
    static final byte HUMAN_STRESS_MONITOR = 0x1A;

    static String name4sensor(final byte sensorId) {
        switch (sensorId) {
            case ACCELEROMETER:
                return "ACCELEROMETER";
            case GRAVITY:
                return "GRAVITY";
            case LINEAR_ACCELERATION:
                return "LINEAR_ACCELERATION";
            case MAGNETIC:
                return "MAGNETIC";
            case ROTATION_VECTOR:
                return "ROTATION_VECTOR";
            case ORIENTATION:
                return "ORIENTATION";
            case GYROSCOPE:
                return "GYROSCOPE";
            case LIGHT:
                return "LIGHT";
            case PROXIMITY:
                return "PROXIMITY";
            case PRESSURE:
                return "PRESSURE";
            case ULTRAVIOLET:
                return "ULTRAVIOLET";
            case TEMPERATURE:
                return "TEMPERATURE";
            case HUMIDITY:
                return "HUMIDITY";
            case HRM:
                return "HRM";
            case HRM_LED_GREEN:
                return "HRM_LED_GREEN";
            case HRM_LED_IR:
                return "HRM_LED_IR";
            case HRM_LED_RED:
                return "HRM_LED_RED";
            case GYROSCOPE_UNCALIBRATED:
                return "GYROSCOPE_UNCALIBRATED";
            case GEOMAGNETIC_UNCALIBRATED:
                return "GEOMAGNETIC_UNCALIBRATED";
            case GYROSCOPE_ROTATION_VECTOR:
                return "GYROSCOPE_ROTATION_VECTOR";
            case GEOMAGNETIC_ROTATION_VECTOR:
                return "GEOMAGNETIC_ROTATION_VECTOR";
            case SIGNIFICANT_MOTION:
                return "SIGNIFICANT_MOTION";
            case HUMAN_PEDOMETER:
                return "HUMAN_PEDOMETER";
            case HUMAN_SLEEP_MONITOR:
                return "HUMAN_SLEEP_MONITOR";
            case HUMAN_SLEEP_DETECTOR:
                return "HUMAN_SLEEP_DETECTOR";
            case HUMAN_STRESS_MONITOR:
                return "HUMAN_STRESS_MONITOR";
            default:
                return null;
        }
    }

    static final byte[] ALL_SENSORS = {
            ACCELEROMETER,
            GRAVITY,
            LINEAR_ACCELERATION,
            MAGNETIC,
            ROTATION_VECTOR,
            ORIENTATION,
            GYROSCOPE,
            LIGHT,
            PROXIMITY,
            PRESSURE,
            ULTRAVIOLET,
            TEMPERATURE,
            HUMIDITY,
            HRM,
            HRM_LED_GREEN,
            HRM_LED_IR,
            HRM_LED_RED,
            GYROSCOPE_UNCALIBRATED,
            GEOMAGNETIC_UNCALIBRATED,
            GYROSCOPE_ROTATION_VECTOR,
            GEOMAGNETIC_ROTATION_VECTOR,
            SIGNIFICANT_MOTION,
            HUMAN_PEDOMETER,
            HUMAN_SLEEP_MONITOR,
            HUMAN_SLEEP_DETECTOR,
            HUMAN_STRESS_MONITOR
    };
}

@SuppressWarnings("all")
class SensorSampleDurations {
    static short _100ms(short ms_interval) throws IncorrectDurationException {
        if (ms_interval < 1 || ms_interval > 100 || 100 % ms_interval != 0)
            throw new IncorrectDurationException("Incorrect ms_interval value was passed: " + ms_interval);
        else
            return (short) (100 / ms_interval);
    }

    static short _250ms(short ms_interval) throws IncorrectDurationException {
        if (ms_interval < 1 || ms_interval > 250 || 250 % ms_interval != 0)
            throw new IncorrectDurationException("Incorrect ms_interval value was passed: " + ms_interval);
        else
            return (short) (250 / ms_interval);
    }

    static short _500ms(short ms_interval) throws IncorrectDurationException {
        if (ms_interval < 1 || ms_interval > 500 || 500 % ms_interval != 0)
            throw new IncorrectDurationException("Incorrect ms_interval value was passed: " + ms_interval);
        else
            return (short) (500 / ms_interval);
    }

    static short _1000ms(short ms_interval) throws IncorrectDurationException {
        if (ms_interval < 1 || ms_interval > 1000 || 1000 % ms_interval != 0)
            throw new IncorrectDurationException("Incorrect ms_interval value was passed: " + ms_interval);
        else
            return (short) (1000 / ms_interval);
    }

    static short _2500ms(short ms_interval) throws IncorrectDurationException {
        if (ms_interval < 1 || ms_interval > 2500 || 2500 % ms_interval != 0)
            throw new IncorrectDurationException("Incorrect ms_interval value was passed: " + ms_interval);
        else
            return (short) (2500 / ms_interval);
    }

    static short _5000ms(short ms_interval) throws IncorrectDurationException {
        if (ms_interval < 1 || ms_interval > 5000 || 5000 % ms_interval != 0)
            throw new IncorrectDurationException("Incorrect ms_interval value was passed: " + ms_interval);
        else
            return (short) (5000 / ms_interval);
    }

    static short _10000ms(short ms_interval) throws IncorrectDurationException {
        if (ms_interval < 1 || ms_interval > 10000 || 10000 % ms_interval != 0)
            throw new IncorrectDurationException("Incorrect ms_interval value was passed: " + ms_interval);
        else
            return (short) (10000 / ms_interval);
    }

    static short _15000ms(short ms_interval) throws IncorrectDurationException {
        if (ms_interval < 1 || ms_interval > 15000 || 15000 % ms_interval != 0)
            throw new IncorrectDurationException("Incorrect ms_interval value was passed: " + ms_interval);
        else
            return (short) (15000 / ms_interval);
    }

    static short _20000ms(short ms_interval) throws IncorrectDurationException {
        if (ms_interval < 1 || ms_interval > 20000 || 20000 % ms_interval != 0)
            throw new IncorrectDurationException("Incorrect ms_interval value was passed: " + ms_interval);
        else
            return (short) (20000 / ms_interval);
    }

    static short _30000ms(short ms_interval) throws IncorrectDurationException {
        if (ms_interval < 1 || ms_interval > 30000 || 30000 % ms_interval != 0)
            throw new IncorrectDurationException("Incorrect ms_interval value was passed: " + ms_interval);
        else
            return (short) (30000 / ms_interval);
    }

    static short _32000ms(short ms_interval) throws IncorrectDurationException {
        if (ms_interval < 1 || ms_interval > 32000 || 32000 % ms_interval != 0)
            throw new IncorrectDurationException("Incorrect ms_interval value was passed: " + ms_interval);
        else
            return (short) (32000 / ms_interval);
    }

    static class IncorrectDurationException extends Exception {
        IncorrectDurationException(String message) {
            super(message);
        }
    }
}

@SuppressWarnings("all")
class MessagingConstants {
    static final byte RES_OK = 0x01;
    static final byte RES_FAIL = 0x02;

    static String name4constant(final byte value) {
        switch (value) {
            case RES_OK:
                return "SUCCESSFUL";
            case RES_FAIL:
                return "FAILURE";
            default:
                return null;
        }
    }
}

@SuppressWarnings("all")
class Tools {
    static SQLiteDatabase db;


    static void init(Context context) {
        db = context.openOrCreateDatabase("EasyTrack_TizenAgent_LocalDB", MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS SensorRecords(sensorId TINYINT DEFAULT(0), timestamp BIGINT DEFAULT(0), accuracy INT DEFAULT(0), data BLOB DEFAULT(NULL));");
    }


    static synchronized void saveSensorRecord(byte[] data) {
        db.execSQL("INSERT INTO SensorRecords(sensorId, timestamp, accuracy, data) VALUES(?, ?, ?, ?)", new Object[]{
                data[1],
                bytes2long(data, 2),
                bytes2int(data, 10),
                Arrays.copyOfRange(data, 14, data.length)
        });
    }


    static synchronized void cleanDb() {
        db.execSQL("DELETE FROM SensorRecords WHERE 1;");
    }


    static float bytes2float(final byte[] data, final int startIndex) {
        byte[] floatBytes = new byte[4];
        System.arraycopy(data, startIndex, floatBytes, 0, 4);
        return ByteBuffer.wrap(floatBytes).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    static int bytes2int(final byte[] data, final int startIndex) {
        byte[] intBytes = new byte[4];
        System.arraycopy(data, startIndex, intBytes, 0, 4);
        return ByteBuffer.wrap(intBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    static long bytes2long(final byte[] data, final int startIndex) {
        byte[] longBytes = new byte[8];
        System.arraycopy(data, startIndex, longBytes, 0, 8);
        return ByteBuffer.wrap(longBytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    static byte[] short2bytes(final short value) {
        return new byte[]{(byte) value, (byte) (value >> 8)};
    }


    public static String[] bytes2hexStrings(final byte[] bytes, final int offset) {
        String[] res = new String[bytes.length - offset];
        for (int n = offset; n < bytes.length; n++) {
            int intVal = bytes[n] & 0xff;
            res[n - offset] = "";
            if (intVal < 0x10)
                res[n - offset] += "0";
            res[n - offset] += Integer.toHexString(intVal).toUpperCase();
        }
        return res;
    }

    public static String[] bytes2hexStrings(final byte[] bytes) {
        return bytes2hexStrings(bytes, 0);
    }

    public static String bytes2hexString(final byte[] bytes, final int offset) {
        return String.join(" ", bytes2hexStrings(bytes, offset));
    }

    public static String bytes2hexString(final byte[] bytes) {
        return String.join(" ", bytes2hexStrings(bytes));
    }
}
