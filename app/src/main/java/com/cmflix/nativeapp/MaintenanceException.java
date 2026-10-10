package com.cmflix.nativeapp;

/*
 * Backend maintenance mode ကို ကိုယ်စားပြုသော
 * exception။
 *
 * Backend က maintenance ON ဖြစ်နေချိန်တွင်
 * content API များ 503 + {"error": "maintenance",
 * "message": "..."} ပြန်ပေးသည်။ ApiClient က
 * ဒီ exception ကို throw လုပ်မည်။
 *
 * MainActivity.onError() က ဒီ type ကို စစ်ပြီး
 * plain text error အစား MaintenanceActivity
 * ဖွင့်မည်။
 */
public class MaintenanceException
        extends RuntimeException {

    public MaintenanceException(
            String message
    ) {
        super(message);
    }
}
