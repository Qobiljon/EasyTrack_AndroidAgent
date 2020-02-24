package kr.ac.inha.nsl;

public class Tools {
    public static boolean isNumber(String str) {
        if (str == null)
            return false;
        try {
            Long d = Long.parseLong(str);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }
}
