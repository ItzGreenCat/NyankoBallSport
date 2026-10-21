package dev.onyxcat;

import dev.onyxcat.network.EnvelopeCrypto;
import dev.onyxcat.network.HeaderIdentity;
import dev.onyxcat.processor.PointProcessor;
import dev.onyxcat.processor.PolicyProcessor;
import dev.onyxcat.processor.RunningArguments;
import dev.onyxcat.processor.RunningProcessor;
import dev.onyxcat.session.Session;
import dev.onyxcat.session.SessionCodec;

import javax.swing.*;
import java.awt.*;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
    public final JPanel distancePanel;
    public final JLabel distanceLabel1;
    public final JLabel distanceLabel2;
    public final JTextField distanceMin;
    public final JTextField distanceMax;
    public final JPanel speedPanel;
    public final JTextField speedFieldMin;
    public final JTextField speedFieldMax;
    public final JLabel speedLabel;
    public final JPanel faceCheckPanel;
    public final JLabel faceCheckLabel;
    public final JCheckBox faceCheckBox;
    public final JLabel currentRunningInfo;
    public final JButton refreshInfoButton;
    public JPanel mainMenuControlPanel;
    public JButton submit;

    public int currentMile = 0;
    public int currentSpeed = 0;
    public Date currentStartDate;
    public int faceCheck;

    public GuiAccountManager accountManager;
    public JTextArea informationLabel;
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
        homePanel.setLayout(new BoxLayout(homePanel,BoxLayout.Y_AXIS));
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
        saveSettings.addActionListener(e -> saveInformation());
        settingControlPanel.add(autoGenerate);
        settingControlPanel.add(saveSettings);
        settingControlPanel.setMaximumSize(settingControlPanel.getPreferredSize());
        settingsPanel.add(settingControlPanel);
        informationLabel = new JTextArea();
        informationLabel.setEditable(false);
        informationLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainFrame.add(informationLabel,BorderLayout.SOUTH);
        distancePanel = new JPanel();
        distancePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        distanceLabel1 = new JLabel("距离(km) 最小值:");
        distancePanel.add(distanceLabel1);
        distanceMin = new JTextField();
        distancePanel.add(distanceMin);
        distanceLabel2 = new JLabel("最大值:");
        distancePanel.add(distanceLabel2);
        distanceMax = new JTextField();
        distancePanel.add(distanceMax);
        distancePanel.setMaximumSize(distancePanel.getPreferredSize());
        homePanel.add(distancePanel);
        speedPanel = new JPanel();
        speedPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        speedLabel = new JLabel("速度范围(秒/KM)[最小/最大]");
        speedFieldMin = new JTextField("360");
        speedFieldMax = new JTextField("480");
        speedPanel.add(speedLabel);
        speedPanel.add(speedFieldMin);
        speedPanel.add(speedFieldMax);
        speedPanel.setMaximumSize(speedPanel.getPreferredSize());
        homePanel.add(speedPanel);
        faceCheckPanel = new JPanel();
        faceCheckPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        faceCheckLabel = new JLabel("人脸验证");
        faceCheckPanel.add(faceCheckLabel);
        faceCheckBox = new JCheckBox();
        faceCheckBox.setSelected(true);
        faceCheckPanel.add(faceCheckBox);
        faceCheckPanel.setMaximumSize(faceCheckPanel.getPreferredSize());
        homePanel.add(faceCheckPanel);
        currentRunningInfo = new JLabel();
        currentRunningInfo.setAlignmentX(Component.LEFT_ALIGNMENT);
        homePanel.add(currentRunningInfo);
        updateRunningInfo();
        refreshInfoButton = new JButton("刷新方案");
        refreshInfoButton.addActionListener((e) -> updateRunningInfo());
        mainMenuControlPanel = new JPanel();
        mainMenuControlPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainMenuControlPanel.add(refreshInfoButton);
        submit = new JButton("提交跑步数据");
        mainMenuControlPanel.add(submit);
        mainMenuControlPanel.setMaximumSize(mainMenuControlPanel.getPreferredSize());
        homePanel.add(mainMenuControlPanel);
        submit.addActionListener((e) -> {
            try {
                RunningProcessor.processRun(new RunningArguments(currentMile,(long)(currentSpeed * ((double)currentMile) / 1000d),currentStartDate.getTime(),faceCheck,BigInteger.valueOf(EnvelopeCrypto.nowMs() % 2147483647)));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
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
    public void updateRunningInfo(){
        try{
            int randomDistance = Integer.parseInt(distanceMin.getText()) + random.nextInt(Integer.parseInt(distanceMax.getText()) - Integer.parseInt(distanceMin.getText()));
            int randomSpeed = Integer.parseInt(speedFieldMin.getText()) + random.nextInt(Integer.parseInt(speedFieldMax.getText()) - Integer.parseInt(speedFieldMin.getText()));
            currentMile = randomDistance;
            currentSpeed = randomSpeed;
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        currentStartDate = randomDateToday();
        faceCheck = faceCheckBox.isSelected() ? 1 : 0;
        int time = (int)(currentSpeed * ((double)currentMile) / 1000f);
        String text = "本次方案 -> 距离:" + currentMile + "米 速度" + currentSpeed + " s/km 用时:[" + (time - (time % 60)) / 60 + ":" + time % 60 + "] 开始时间:" + currentStartDate.toString();
        currentRunningInfo.setText(text);
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
        String labelText = String.format("当前环境: 平台[%s] 设备ID[%s] 系统版本[%s] IDFA/IMEI[%s]\n设备型号[%s] MAC地址[%s] 经纬度[%f,%f] 城市[%s]",
                Session.headerInformation.getPlatform(),
                Session.headerInformation.getDeviceId(),
                Session.headerInformation.getOsVersion(),
                Session.headerInformation.getIdfa(),
                Session.headerInformation.getDeviceName(),
                Session.headerInformation.getMacAddress(),
                Session.headerInformation.getAnchorLon(),
                Session.headerInformation.getAnchorLat(),
                Session.headerInformation.getCity());
        informationLabel.setText(labelText);
    }
    public void generateInformation(){
        deviceIDField.setText(UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
        systemVersionField.setText(iosButton.isSelected() ? HeaderIdentity.iosVersions[random.nextInt(HeaderIdentity.iosVersions.length)] : HeaderIdentity.androidVersions[random.nextInt(HeaderIdentity.androidVersions.length)]);
        deviceNameField.setText(iosButton.isSelected() ? "iPhone" : HeaderIdentity.androidDevices[random.nextInt(HeaderIdentity.androidVersions.length)]);
    }
    public void saveInformation(){
        Session.headerInformation.setPlatform(iosButton.isSelected() ? "ios" : "android");
        Session.headerInformation.setDeviceId(deviceIDField.getText());
        Session.headerInformation.setIdfa(iosButton.isSelected() ? UUID.randomUUID().toString().toUpperCase(Locale.ROOT) : "");
        Session.headerInformation.setOsVersion(systemVersionField.getText());
        Session.headerInformation.setDeviceName(deviceNameField.getText());
        Session.headerInformation.setMacAddress(HeaderIdentity.randomMac());
        Session.headerInformation.setAnchorLat(Double.parseDouble(latField.getText()));
        Session.headerInformation.setAnchorLon(Double.parseDouble(lonField.getText()));
        Session.headerInformation.setCity(cityField.getText());
        try{
            SessionCodec.saveSession("Session-" + Session.profile.get("username").textValue());
        } catch (Exception e){
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,"无法保存配置","提示",JOptionPane.WARNING_MESSAGE);
        }
    }
    public static Date randomDateToday() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);

        ZonedDateTime start = today.atTime(7, 0).atZone(zone);
        ZonedDateTime end = today.atTime(20, 0).atZone(zone);

        long randomMillis = ThreadLocalRandom.current().nextLong(
                start.toInstant().toEpochMilli(),
                end.toInstant().toEpochMilli()
        );

        return new Date(randomMillis);
    }
}
