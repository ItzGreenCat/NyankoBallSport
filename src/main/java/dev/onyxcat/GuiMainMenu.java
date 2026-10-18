package dev.onyxcat;

import dev.onyxcat.session.SessionManager;

import javax.swing.*;
import java.awt.*;

public class GuiMainMenu {
    public final JFrame mainFrame;
    public final JPanel mainPanel;
    public final JLabel accountLabel;
    public final JButton accountManagerButton;
    public GuiAccountManager accountManager;
    public GuiMainMenu(){
        mainFrame = new JFrame("NyankoBall");
        mainFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        mainFrame.setSize(800,600);
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridLayout(1,4));
        mainFrame.add(mainPanel,BorderLayout.NORTH);
        accountLabel = new JLabel("AccountInfo");
        mainPanel.add(accountLabel);
        accountManagerButton = new JButton("账号管理");
        accountManagerButton.addActionListener((e) -> {
            accountManager.show();
        });
        mainPanel.add(accountManagerButton);

        JButton testLogOut = new JButton("testlogout");
        testLogOut.addActionListener((e) -> {
            Main.sessionManager.logout();
        });
        mainPanel.add(testLogOut);
        accountManager = new GuiAccountManager();
    }
    public void startupMenu(){
        mainFrame.setVisible(true);
    }
}
