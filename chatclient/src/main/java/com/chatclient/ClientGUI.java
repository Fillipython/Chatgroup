package com.chatclient;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class ClientGUI extends JFrame {
    private static final String SERVER_URL = System.getenv().getOrDefault("SERVER_URL", "http://localhost:8000");
    private final Gson gson = new Gson();

    private JTextField nicknameField;
    private JButton setNickButton;

    private JTextField groupNameField;
    private JButton createGroupButton;
    private JComboBox<String> groupComboBox;
    private DefaultComboBoxModel<String> groupModel;

    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;

    private String nickname = "";
    private int currentGroupId = -1;
    private Map<String, Integer> groupNameToId = new HashMap<>();

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private AtomicLong lastTimestampServer = new AtomicLong(0);
    private final Set<String> displayedMessages = Collections.synchronizedSet(new HashSet<>());

    private final Random random = new Random();

    public ClientGUI() {
        setTitle("Chat Client");
        setSize(600, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        initComponents();
        loadGroups();
        scheduler.scheduleAtFixedRate(this::pollMessages, 1, 1, TimeUnit.SECONDS);
    }

    private void initComponents() {
        JPanel topPanel = new JPanel(new FlowLayout());
        nicknameField = new JTextField(10);
        setNickButton = new JButton("Set Nickname");
        topPanel.add(new JLabel("Nickname:"));
        topPanel.add(nicknameField);
        topPanel.add(setNickButton);

        JPanel groupPanel = new JPanel(new FlowLayout());
        groupNameField = new JTextField(10);
        createGroupButton = new JButton("Create Group");
        groupModel = new DefaultComboBoxModel<>();
        groupComboBox = new JComboBox<>(groupModel);
        groupPanel.add(new JLabel("Group Name:"));
        groupPanel.add(groupNameField);
        groupPanel.add(createGroupButton);
        groupPanel.add(new JLabel("Join Group:"));
        groupPanel.add(groupComboBox);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);

        JPanel messagePanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        sendButton = new JButton("Send");
        messagePanel.add(messageField, BorderLayout.CENTER);
        messagePanel.add(sendButton, BorderLayout.EAST);

        Container cp = getContentPane();
        cp.setLayout(new BorderLayout());

        JPanel northContainer = new JPanel(new GridLayout(2, 1));
        northContainer.add(topPanel);
        northContainer.add(groupPanel);

        cp.add(northContainer, BorderLayout.NORTH);
        cp.add(scrollPane, BorderLayout.CENTER);
        cp.add(messagePanel, BorderLayout.SOUTH);

        setNickButton.addActionListener(e -> setNickname());
        createGroupButton.addActionListener(e -> createGroup());
        groupComboBox.addActionListener(e -> selectGroup());
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());
    }

    private void setNickname() {
        String nick = nicknameField.getText().trim();
        if (nick.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a nickname!");
            return;
        }
        new Thread(() -> {
            try {
                HttpClientUtil.postJson(SERVER_URL + "/nick", Map.of("name", nick));
                nickname = nick;
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Nickname set to: " + nick));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Error setting nickname: " + ex.getMessage()));
            }
        }).start();
    }

    private void createGroup() {
        String name = groupNameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a group name!");
            return;
        }
        new Thread(() -> {
            try {
                HttpClientUtil.postJson(SERVER_URL + "/grupos", Map.of("name", name));
                SwingUtilities.invokeLater(() -> groupNameField.setText(""));
                loadGroups();
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Error creating group: " + ex.getMessage()));
            }
        }).start();
    }

    private void loadGroups() {
        new Thread(() -> {
            try {
                String json = HttpClientUtil.get(SERVER_URL + "/grupos");
                java.lang.reflect.Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
                List<Map<String, Object>> groups = gson.fromJson(json, listType);
                SwingUtilities.invokeLater(() -> {
                    String selected = (String) groupComboBox.getSelectedItem();
                    groupNameToId.clear();
                    groupModel.removeAllElements();
                    if (groups != null) {
                        for (Map<String, Object> g : groups) {
                            String name = (String) g.get("name");
                            int id = ((Number) g.get("id")).intValue();
                            groupNameToId.put(name, id);
                            groupModel.addElement(name);
                        }
                    }
                    if (selected != null && groupNameToId.containsKey(selected)) {
                        groupComboBox.setSelectedItem(selected);
                    }
                });
            } catch (Exception ex) {
                // Servidor pode ainda não estar rodando no momento da inicialização
                System.err.println("Could not load groups: " + ex.getMessage());
            }
        }).start();
    }

    private void selectGroup() {
        String selected = (String) groupComboBox.getSelectedItem();
        if (selected != null && groupNameToId.containsKey(selected)) {
            int newId = groupNameToId.get(selected);
            if (newId != currentGroupId) {
                currentGroupId = newId;
                lastTimestampServer.set(0);
                displayedMessages.clear();
                chatArea.setText("Joined group: " + selected + "\n");
                pollMessages();
            }
        }
    }

    private void sendMessage() {
        if (nickname.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Set a nickname first!");
            return;
        }
        if (currentGroupId == -1) {
            JOptionPane.showMessageDialog(this, "Join a group first!");
            return;
        }
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;

        messageField.setText("");
        String idemKey = UUID.randomUUID().toString();
        long tsClient = System.currentTimeMillis();

        Map<String, Object> data = new HashMap<>();
        data.put("idemKey", idemKey);
        data.put("nickname", nickname);
        data.put("text", text);
        data.put("timestamp_client", tsClient);

        new Thread(() -> {
            int attempts = 0;
            while (attempts < 3) {
                try {
                    HttpClientUtil.postJson(SERVER_URL + "/grupos/" + currentGroupId + "/messages", data);
                    pollMessages();
                    return;
                } catch (Exception ex) {
                    attempts++;
                    try {
                        Thread.sleep(500L * attempts + random.nextInt(200));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Failed to send message after retries."));
        }).start();
    }

    private synchronized void pollMessages() {
        int groupId = currentGroupId;
        if (groupId == -1) return;
        try {
            long since = lastTimestampServer.get();
            String json = HttpClientUtil.get(SERVER_URL + "/grupos/" + groupId + "/messages?since=" + since + "&limit=50");
            java.lang.reflect.Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
            List<Map<String, Object>> messages = gson.fromJson(json, listType);
            if (messages != null && !messages.isEmpty()) {
                for (Map<String, Object> m : messages) {
                    long tsServer = ((Number) m.get("timestamp_server")).longValue();
                    if (tsServer > lastTimestampServer.get()) {
                        lastTimestampServer.set(tsServer);
                    }
                }
                SwingUtilities.invokeLater(() -> {
                    for (Map<String, Object> m : messages) {
                        String nick = (String) m.get("nickname");
                        String text = (String) m.get("text");
                        long tsClient = ((Number) m.get("timestamp_client")).longValue();
                        long tsServer = ((Number) m.get("timestamp_server")).longValue();
                        String msgKey = nick + ":" + tsClient + ":" + tsServer + ":" + text;
                        if (displayedMessages.add(msgKey)) {
                            chatArea.append("[" + nick + "]: " + text + "\n");
                        }
                    }
                    chatArea.setCaretPosition(chatArea.getDocument().getLength());
                });
            }
        } catch (Exception ex) {
            // Ignora erros temporários de polling
        }
    }

    @Override
    public void dispose() {
        scheduler.shutdownNow();
        super.dispose();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientGUI().setVisible(true));
    }
}
