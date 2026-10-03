package com.example.demo.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * 身份证号解析工具。
 * 支持 18 位身份证：从身份证号中提取性别、生日、年龄。
 * 18 位身份证结构：前 6 位地区码 + 8 位出生日期(YYYYMMDD) + 3 位顺序码 + 1 位校验码
 * 第 17 位（顺序码末位）奇数为男，偶数为女。
 */
public final class IdCardUtil {

    private IdCardUtil() {
    }

    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^\\d{17}[\\dXx]$");

    private static final DateTimeFormatter BIRTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 性别：男 */
    public static final String GENDER_MALE = "男";
    /** 性别：女 */
    public static final String GENDER_FEMALE = "女";

    /**
     * 校验身份证号格式是否合法（18 位 + 校验码）。
     */
    public static boolean isValid(String idCard) {
        if (idCard == null || !ID_CARD_PATTERN.matcher(idCard).matches()) {
            return false;
        }
        // 校验出生日期合法
        try {
            parseBirthDate(idCard);
        } catch (Exception e) {
            return false;
        }
        // 校验校验码
        return checkChecksum(idCard);
    }

    /**
     * 从身份证号提取性别。
     *
     * @return "男" 或 "女"，身份证非法时返回 null
     */
    public static String parseGender(String idCard) {
        if (!isValid(idCard)) {
            return null;
        }
        int genderDigit = Character.getNumericValue(idCard.charAt(16));
        return genderDigit % 2 == 1 ? GENDER_MALE : GENDER_FEMALE;
    }

    /**
     * 从身份证号提取出生日期。
     *
     * @return 出生日期 yyyy-MM-dd，身份证非法时返回 null
     */
    public static String parseBirthDate(String idCard) {
        if (idCard == null || idCard.length() < 14) {
            return null;
        }
        try {
            LocalDate birth = LocalDate.parse(idCard.substring(6, 14), BIRTH_FORMATTER);
            return birth.toString();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 从身份证号计算年龄。
     *
     * @return 年龄（周岁），身份证非法时返回 -1
     */
    public static int parseAge(String idCard) {
        String birthStr = parseBirthDate(idCard);
        if (birthStr == null) {
            return -1;
        }
        LocalDate birth = LocalDate.parse(birthStr);
        LocalDate now = LocalDate.now();
        int age = now.getYear() - birth.getYear();
        if (now.isBefore(birth.plusYears(age))) {
            age--;
        }
        return age;
    }

    /**
     * 校验身份证校验码（GB 11643-1999）。
     */
    private static boolean checkChecksum(String idCard) {
        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] checkCodes = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += Character.getNumericValue(idCard.charAt(i)) * weights[i];
        }
        char expected = checkCodes[sum % 11];
        char actual = Character.toUpperCase(idCard.charAt(17));
        return expected == actual;
    }
}
