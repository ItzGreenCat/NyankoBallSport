package dev.onyxcat.session;

import com.fasterxml.jackson.databind.JsonNode;
import dev.onyxcat.network.EnvelopeCrypto;
import dev.onyxcat.network.HeaderIdentity;

public class Session {
    public static int uid = -1;
    public static String token = "";
    public static String unid = "0";
    public static String name = "";
    public static double weight = 68.0;
    public static String username = "";
    public static JsonNode profile = null;

    public static HeaderIdentity headerInformation = new HeaderIdentity();
    public static EnvelopeCrypto.EnvelopeSession envelopeSession = new EnvelopeCrypto.EnvelopeSession();
    public static void resetSession() {
        uid = -1;
        token = "";
        unid = "0";
        name = "";
        weight = 68.0;
        username = "";
        profile = null;
        headerInformation = new HeaderIdentity();
        envelopeSession = new EnvelopeCrypto.EnvelopeSession();
    }
}
