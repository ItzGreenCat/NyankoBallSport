package dev.onyxcat.network;

public class AdditionalHeader {
    public static AdditionalHeader EMPTY_HEADER = new AdditionalHeader(null,null);
    public final String key;
    public final String value;
    public AdditionalHeader(String key,String value){
        this.key = key;
        this.value = value;
    }
    public boolean isEmpty(){
        return key == null && value == null;
    }
}
