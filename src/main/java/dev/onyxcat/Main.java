package dev.onyxcat;


import dev.onyxcat.network.NetworkHandler;
import dev.onyxcat.session.SessionCodec;
import dev.onyxcat.session.SessionManager;

import java.io.IOException;

public class Main {
    public static GuiMainMenu mainMenuInstance;
    public static NetworkHandler networkHandler;
    public static SessionManager sessionManager;
    public static void main(String[] args) throws IOException {
        System.out.println("Starting UP...");
        AccountList.loadAccounts();
        networkHandler = new NetworkHandler();
        sessionManager = new SessionManager();
        mainMenuInstance = new GuiMainMenu();
        SessionCodec.loadSession("Session-18731169770");
        mainMenuInstance.startupMenu();
    }
}