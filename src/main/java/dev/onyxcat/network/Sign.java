package dev.onyxcat.network;

public class Sign {
    public static final String SALT = "2slhe02lsfiwowlcixisla_sls-_slaor";
    public static String signUrlMD5(String url){
        String temp = url.replace("https://","http://");
        return EnvelopeCrypto.md5Hex((temp + SALT).getBytes());
    }
}
