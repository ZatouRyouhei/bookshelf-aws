package com.example.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SHA256Encoder {
    public String encodePassword(String origPassword) {
        String returnValue = "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(origPassword.getBytes());
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < digest.length; i++) {
                String tmp = Integer.toHexString(digest[i] & 0xff);
                if (tmp.length() == 1) {
                    builder.append('0').append(tmp);
                } else {
                    builder.append(tmp);
                }
            }
            returnValue = builder.toString();
        } catch (NoSuchAlgorithmException e) {
            returnValue = "";
            System.err.println(e.getMessage());
        }
        return returnValue;
    }
}
