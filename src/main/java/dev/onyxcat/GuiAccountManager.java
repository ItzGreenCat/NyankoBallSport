package dev.onyxcat;

import javax.swing.*;
import java.awt.*;

public class GuiAccountManager {
    public final JFrame accountFrame;
    public final JButton addAccount;
    public GuiAccountManager(){
        accountFrame = new JFrame("Account Manager");
        accountFrame.setSize(600,400);
        addAccount = new JButton("添加账号");
        addAccount.addActionListener((e) -> {
            LoginWindow.login((entry) -> {
                System.out.println(String.format("%s %s",entry.getKey(),entry.getValue()));
            });
        });
        accountFrame.add(addAccount, BorderLayout.NORTH);
    }
    public void show(){
        accountFrame.setVisible(true);
    }
}
