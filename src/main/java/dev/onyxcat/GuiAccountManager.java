package dev.onyxcat;

import dev.onyxcat.session.Session;
import dev.onyxcat.session.SessionCodec;
import dev.onyxcat.session.SessionManager;

import javax.swing.*;
import java.awt.*;

public class GuiAccountManager {
    public final JFrame accountFrame;
    public final JButton addAccount;
    public JScrollPane scrollPanel;
    JPanel viewport;
    public GuiAccountManager(){
        accountFrame = new JFrame("Account Manager");
        accountFrame.setSize(600,400);
        viewport = new JPanel();
        viewport.setLayout(new BoxLayout(viewport,BoxLayout.Y_AXIS));
        scrollPanel = new JScrollPane(viewport);
        addAccount = new JButton("添加账号");
        refreshAccounts();
        addAccount.addActionListener((e) -> {
            LoginWindow.login(Main.sessionManager::login);
            refreshAccounts();
        });
        accountFrame.add(addAccount, BorderLayout.NORTH);
        accountFrame.add(scrollPanel, BorderLayout.CENTER);
    }
    public void show(){
        accountFrame.setVisible(true);
    }
    public void refreshAccounts(){
        viewport.removeAll();
        for(AccountContainer container : AccountList.accounts.containers){
            JPanel accountPanel = new JPanel();
            JButton selectButton = new JButton("选中");
            selectButton.addActionListener((e) -> {
                Main.sessionManager.loadLocalSession(container.accountName);
            });
            JButton logout = new JButton("登出");
            logout.addActionListener((e) -> {
                Main.sessionManager.loadLocalSession(container.accountName);
                Main.sessionManager.logout();
                Main.sessionManager.removeLocalSession(container.accountName);
            });
            JLabel nameLabel = new JLabel(container.realName + ":" + container.accountName);
            accountPanel.add(nameLabel);
            accountPanel.add(selectButton);
            accountPanel.add(logout);
            accountPanel.setMaximumSize(accountPanel.getPreferredSize());
            viewport.add(accountPanel);
        }
        viewport.revalidate();
        viewport.repaint();
    }
}
