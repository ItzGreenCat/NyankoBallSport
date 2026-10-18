package dev.onyxcat;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.function.Consumer;

public class LoginWindow {
    public static void login(Consumer<Map.Entry<String,String>> consumer){
        JFrame frame = new JFrame("Login");
        frame.setSize(200,100);
        JLabel accountLabel = new JLabel("账号");
        JTextField account = new JTextField();
        JLabel passwdLabel = new JLabel("密码");
        JPasswordField passwd = new JPasswordField();
        JPanel accountPanel = new JPanel();
        accountPanel.setLayout(new GridLayout(1,2));
        JPanel passwdPanel = new JPanel();
        passwdPanel.setLayout(new GridLayout(1,2));
        accountPanel.add(accountLabel);
        accountPanel.add(account);
        passwdPanel.add(passwdLabel);
        passwdPanel.add(passwd);
        frame.add(accountPanel,BorderLayout.NORTH);
        frame.add(passwdPanel,BorderLayout.CENTER);
        JButton button = new JButton("登录");
        button.addActionListener((e) -> {
            Map.Entry<String,String> entry = new Map.Entry<String, String>() {
                @Override
                public String getKey() {
                    return account.getText();
                }

                @Override
                public String getValue() {
                    return passwd.getText();
                }

                @Override
                public String setValue(String value) {
                    throw new IllegalCallerException("Cannot call setValue method!");
                }
            };
            consumer.accept(entry);
            frame.setVisible(false);
        });
        frame.add(button,BorderLayout.SOUTH);
        frame.setVisible(true);
    }
}
