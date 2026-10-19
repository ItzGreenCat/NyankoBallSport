package dev.onyxcat;


import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatLightLaf;
import dev.onyxcat.network.NetworkHandler;
import dev.onyxcat.session.SessionManager;

import java.io.IOException;

public class Main {
    public static GuiMainMenu mainMenuInstance;
    public static NetworkHandler networkHandler;
    public static SessionManager sessionManager;
    public static void main(String[] args) throws IOException {
        System.out.println("Starting UP...");
        FlatDarculaLaf.setup();
        AccountList.loadAccounts();
        System.out.println("Current account count:" + AccountList.accounts.containers.size());
        networkHandler = new NetworkHandler();
        sessionManager = new SessionManager();
        mainMenuInstance = new GuiMainMenu();
        if(!AccountList.accounts.containers.isEmpty()){
            sessionManager.loadLocalSession(AccountList.accounts.containers.get(0).accountName);
        }
        mainMenuInstance.startupMenu();
    }
}