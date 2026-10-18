package dev.onyxcat.network;

public class RequestResult {
    public final int httpCode;
    public final ResponseDecryptor.Decrypted data;
    public final int length;
    public RequestResult(int httpCode, ResponseDecryptor.Decrypted data, int length){
        this.httpCode = httpCode;
        this.data = data;
        this.length = length;
    }
}
