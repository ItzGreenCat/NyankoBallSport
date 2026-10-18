package dev.onyxcat.session;


import dev.onyxcat.network.EnvelopeCrypto;
import dev.onyxcat.network.HeaderIdentity;

public class Session {
    public static int uid = -1;
    public static String token = "";
    public static HeaderIdentity headerInformation;
    public static EnvelopeCrypto.EnvelopeSession envelopeSession;
    public void resetSession(){
        uid = -1;
        token = "";
        headerInformation = null;
        envelopeSession = null;
    }
}
