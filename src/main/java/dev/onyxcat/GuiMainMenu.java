package dev.onyxcat;

import dev.onyxcat.network.HeaderIdentity;
import dev.onyxcat.session.Session;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

public class GuiMainMenu {
    public final Random random = new Random();
    public final JFrame mainFrame;
    public final JPanel mainPanel;
    public final JLabel accountLabel;
    public final JButton accountManagerButton;
    public final JPanel switchPanel;
    public final JPanel homePanel;
    public final JPanel settingsPanel;
    public final JButton settingsButton;
    public final JButton backHomeButton;
    public final JPanel systemSelectPanel;
    public final JRadioButton iosButton;
    public final JRadioButton androidButton;
    public final ButtonGroup systemButtonGroup;
    public final JLabel systemLabel;
    public final JPanel deviceIDPanel;
    public final JLabel deviceIDLabel;
    public final JTextField deviceIDField;
    public final JPanel systemVersionPanel;
    public final JLabel systemVersionLabel;
    public final JTextField systemVersionField;
    public final JPanel deviceNamePanel;
    public final JLabel deviceNameLabel;
    public final JTextField deviceNameField;
    public final JPanel cityPanel;
    public final JLabel cityLabel;
    public final JTextField cityField;
    public final JPanel positionPanel;
    public final JLabel latLabel;
    public final JTextField latField;
    public final JLabel lonLabel;
    public final JTextField lonField;
    public final JPanel settingControlPanel;
    public final JButton autoGenerate;
    public final JButton saveSettings;
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
        settingsPanel.setLayout(new BoxLayout(settingsPanel,BoxLayout.Y_AXIS));
        settingsPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        settingsButton = new JButton("设置");
        settingsButton.addActionListener((e) -> {
            ((CardLayout)switchPanel.getLayout()).show(switchPanel,"settings");
        });
        settingsButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        backHomeButton = new JButton("返回");
        backHomeButton.addActionListener((e) -> {
            ((CardLayout)switchPanel.getLayout()).show(switchPanel,"home");
        });
        backHomeButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        homePanel.add(settingsButton);
        settingsPanel.add(backHomeButton);
        switchPanel.add(homePanel,"home");
        switchPanel.add(settingsPanel,"settings");
        mainFrame.add(switchPanel,BorderLayout.CENTER);
        systemSelectPanel = new JPanel();
        systemSelectPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        systemLabel = new JLabel("系统");
        iosButton = new JRadioButton("IOS");
        androidButton = new JRadioButton("Android");
        systemButtonGroup = new ButtonGroup();
        systemButtonGroup.add(iosButton);
        systemButtonGroup.add(androidButton);
        systemSelectPanel.add(systemLabel);
        systemSelectPanel.add(iosButton);
        systemSelectPanel.add(androidButton);
        systemSelectPanel.setMaximumSize(systemSelectPanel.getPreferredSize());
        settingsPanel.add(systemSelectPanel);
        deviceIDPanel = new JPanel();
        deviceIDField = new JTextField();
        deviceIDField.setPreferredSize(new Dimension(deviceIDField.getPreferredSize().width + 250,deviceIDField.getPreferredSize().height));
        deviceIDLabel = new JLabel("设备ID");
        deviceIDPanel.add(deviceIDLabel);
        deviceIDPanel.add(deviceIDField);
        deviceIDPanel.setMaximumSize(deviceIDPanel.getPreferredSize());
        deviceIDPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        settingsPanel.add(deviceIDPanel);
        systemVersionPanel = new JPanel();
        systemVersionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        systemVersionLabel = new JLabel("系统版本");
        systemVersionField = new JTextField();
        systemVersionField.setPreferredSize(new Dimension(systemVersionField.getPreferredSize().width + 100,systemVersionField.getPreferredSize().height));
        systemVersionPanel.add(systemVersionLabel);
        systemVersionPanel.add(systemVersionField);
        systemVersionPanel.setMaximumSize(systemVersionPanel.getPreferredSize());
        settingsPanel.add(systemVersionPanel);
        deviceNamePanel = new JPanel();
        deviceNamePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        deviceNameLabel = new JLabel("机型");
        deviceNameField = new JTextField();
        deviceNameField.setPreferredSize(new Dimension(deviceNameField.getPreferredSize().width + 100,deviceNameField.getPreferredSize().height));
        deviceNamePanel.add(deviceNameLabel);
        deviceNamePanel.add(deviceNameField);
        deviceNamePanel.setMaximumSize(deviceNamePanel.getPreferredSize());
        settingsPanel.add(deviceNamePanel);
        cityPanel = new JPanel();
        cityPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        cityLabel = new JLabel("城市");
        cityField = new JTextField();
        cityField.setPreferredSize(new Dimension(cityField.getPreferredSize().width + 100,cityField.getPreferredSize().height));
        cityPanel.add(cityLabel);
        cityPanel.add(cityField);
        cityPanel.setMaximumSize(cityPanel.getPreferredSize());
        settingsPanel.add(cityPanel);
        positionPanel = new JPanel();
        positionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        latLabel = new JLabel("纬度");
        latField = new JTextField();
        lonLabel = new JLabel("经度");
        lonField = new JTextField();
        positionPanel.add(lonLabel);
        positionPanel.add(lonField);
        positionPanel.add(latLabel);
        positionPanel.add(latField);
        positionPanel.setMaximumSize(positionPanel.getPreferredSize());
        settingsPanel.add(positionPanel);
        settingControlPanel = new JPanel();
        settingControlPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoGenerate = new JButton("自动生成");
        autoGenerate.addActionListener(e -> generateInformation());
        saveSettings = new JButton("保存");
        settingControlPanel.add(autoGenerate);
        settingControlPanel.add(saveSettings);
        settingControlPanel.setMaximumSize(settingControlPanel.getPreferredSize());
        settingsPanel.add(settingControlPanel);
        refreshInformation();
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
    public void refreshInformation(){
        iosButton.setSelected(false);
        androidButton.setSelected(false);
        String system = Session.headerInformation.getPlatform();
        if(system.equals("ios")){
            iosButton.setSelected(true);
        } else {
            androidButton.setSelected(true);
        }
        deviceIDField.setText(Session.headerInformation.getDeviceId());
        systemVersionField.setText(Session.headerInformation.getOsVersion());
        deviceNameField.setText(Session.headerInformation.getDeviceName());
        latField.setText(String.valueOf(Session.headerInformation.getAnchorLat()));
        lonField.setText(String.valueOf(Session.headerInformation.getAnchorLon()));
        cityField.setText(String.valueOf(Session.headerInformation.getCity()));
    }
    public void generateInformation(){
        deviceIDField.setText(UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
        systemVersionField.setText(iosButton.isSelected() ? HeaderIdentity.iosVersions[random.nextInt(HeaderIdentity.iosVersions.length)] : HeaderIdentity.androidVersions[random.nextInt(HeaderIdentity.androidVersions.length)]);
        deviceNameField.setText(iosButton.isSelected() ? "iPhone" : HeaderIdentity.androidDevices[random.nextInt(HeaderIdentity.androidVersions.length)]);
    }
}
