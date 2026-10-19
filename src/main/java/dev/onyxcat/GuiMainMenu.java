package dev.onyxcat;

import dev.onyxcat.session.Session;
import dev.onyxcat.session.SessionManager;

import javax.swing.*;
import java.awt.*;

public class GuiMainMenu {
    public final JFrame mainFrame;
    public final JPanel mainPanel;
    public final JLabel accountLabel;
    public final JButton accountManagerButton;
    public final JPanel switchPanel;
    public final JPanel homePanel;
    public final JPanel settingsPanel;
    public final JButton settingsButton;
    public final JButton backHomeButton;
    public GuiAccountManager accountManager;
    public GuiMainMenu(){
        mainFrame = new JFrame("NyankoBall");
        mainFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        mainFrame.setSize(800,600);
        mainPanel = new JPanel();
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        mainPanel.setLayout(new GridLayout(1,4));
        mainFrame.add(mainPanel,BorderLayout.NORTH);
        accountLabel = new JLabel("AccountInfo");
        mainPanel.add(accountLabel);
        accountManagerButton = new JButton("账号管理");
        accountManagerButton.addActionListener((e) -> {
            accountManager.show();
        });
        mainPanel.add(accountManagerButton);
        accountManager = new GuiAccountManager();
        switchPanel = new JPanel(new CardLayout());
        homePanel = new JPanel();
        homePanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        homePanel.setLayout(new BoxLayout(homePanel,BoxLayout.Y_AXIS));
        settingsPanel = new JPanel();
        settingsButton = new JButton("设置");
        settingsButton.addActionListener((e) -> {
            ((CardLayout)switchPanel.getLayout()).show(switchPanel,"settings");
        });
        settingsButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        backHomeButton = new JButton("返回");
        backHomeButton.addActionListener((e) -> {
            ((CardLayout)switchPanel.getLayout()).show(switchPanel,"home");
        });
        homePanel.add(settingsButton);
        settingsPanel.add(backHomeButton);
        switchPanel.add(homePanel,"home");
        switchPanel.add(settingsPanel,"settings");
        mainFrame.add(switchPanel,BorderLayout.CENTER);
    }
    public void startupMenu(){
        mainFrame.setVisible(true);
    }
    public void refreshLabel(){
        if(Session.uid != -1){
            accountLabel.setText("当前账户:" + Session.name);
        } else {
            accountLabel.setText("未登入");
        }
    }
}
