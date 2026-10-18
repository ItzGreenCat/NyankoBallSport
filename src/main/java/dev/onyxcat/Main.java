package dev.onyxcat;



public class Main {
    public static GuiMainMenu mainMenuInstance;
    public static void main(String[] args) {
        System.out.println("Starting UP...");
        mainMenuInstance = new GuiMainMenu();
        mainMenuInstance.startupMenu();
    }
}