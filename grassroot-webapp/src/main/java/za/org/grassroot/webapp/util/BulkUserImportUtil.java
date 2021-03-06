package za.org.grassroot.webapp.util;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.Phonenumber;
import za.org.grassroot.core.util.PhoneNumberUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created by paballo on 2016/02/09.
 */
public class BulkUserImportUtil {

    public static Map<String, List<String>> splitPhoneNumbers(String userResponse) {

        userResponse = userResponse.trim().replaceAll("\n", "");
        String separator = getSeparator(userResponse);
        Pattern nonNumericPattern = Pattern.compile(separator);
        Map<String, List<String>> returnMap = new HashMap<>();
        List<String> validNumbers = new ArrayList<>();
        List<String> errorNumbers = new ArrayList<>();

        com.google.i18n.phonenumbers.PhoneNumberUtil phoneNumberUtil = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();

        for(String number:nonNumericPattern.split(userResponse.trim())){
            try {
               number = number.trim();
                Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(number, "ZA");
                if (!phoneNumberUtil.isValidNumber(phoneNumber))
                    errorNumbers.add(number);
                else if (number.length() < 10)
                    errorNumbers.add(number);
                else
                    validNumbers.add(PhoneNumberUtil.convertPhoneNumber(number));
            } catch (NumberParseException e) {
                errorNumbers.add(number);
            }

        }
        returnMap.put("valid", validNumbers);
        returnMap.put("error", errorNumbers);
        return returnMap;
    }

    public static boolean isNumberValid(String enteredNumber) {
        try {
            com.google.i18n.phonenumbers.PhoneNumberUtil phoneNumberUtil = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
            final String trimmed = enteredNumber.trim();
            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(trimmed, "ZA");
            if (!phoneNumberUtil.isValidNumber(phoneNumber))
                return false;
            else if (trimmed.length() < 10)
                return false;
            else
                return true;
        } catch (NumberParseException e) {
            return false;
        }
    }

    private static String getSeparator(String input){

        Character separator = null;
        for(int i =0; i<input.length(); i++){
            if(!Character.isDigit(input.charAt(i)) ){
                if (input.charAt(i) =='+' || Character.isLetter(input.charAt(i))){
                    continue;
                }
                separator = input.charAt(i);
                break;
            }
        }
        return String.valueOf(separator);
    }

}
