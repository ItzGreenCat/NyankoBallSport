package dev.onyxcat;


import dev.onyxcat.network.NetworkHandler;
import dev.onyxcat.session.SessionManager;

public class Main {
    public static GuiMainMenu mainMenuInstance;
    public static NetworkHandler networkHandler;
    public static SessionManager sessionManager;
    public static void main(String[] args) {
        System.out.println("Starting UP...");
        networkHandler = new NetworkHandler();
        sessionManager = new SessionManager();
        mainMenuInstance = new GuiMainMenu();
        mainMenuInstance.startupMenu();
    }
}