package cn.nolaurene.cms.utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtils {

    private static String normalFormat = "yyyy-MM-dd hh:mm:ss";

    public static String transferToNormalFormat(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat(normalFormat);
        return formatter.format(date);
    }
}
