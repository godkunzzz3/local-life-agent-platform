package com.hmdp.utils;

public class SystemConstants {
    private static final String DEFAULT_IMAGE_UPLOAD_DIR = "uploads/images/";
    private static final String IMAGE_UPLOAD_DIR_ENV = System.getenv("HMDP_IMAGE_UPLOAD_DIR");

    public static final String IMAGE_UPLOAD_DIR = System.getProperty(
            "hmdp.image-upload-dir",
            IMAGE_UPLOAD_DIR_ENV == null ? DEFAULT_IMAGE_UPLOAD_DIR : IMAGE_UPLOAD_DIR_ENV
    );
    public static final String USER_NICK_NAME_PREFIX = "user_";
    public static final int DEFAULT_PAGE_SIZE = 5;
    public static final int MAX_PAGE_SIZE = 10;
}
