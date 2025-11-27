// LoginSystemAdvanced.java
// Single-file Swing application with:
// - File-based user storage (users.db)
// - Login/Register/Forgot password
// - Roles: ADMIN / USER
// - Session system (session.db)
// - Login history (history.db)
// - Admin panel: view/edit/delete users
// - User profile update
// - Multi-window GUI
// - Export login history to CSV

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class LoginSystemAdvanced {

    // Filenames
    private static final File USER_FILE = new File("users.db");
    private static final File HISTORY_FILE = new File("history.db");
    private static final File SESSION_FILE = new File("session.db");

    // In-memory user store
    private final Map<String, User> users = new LinkedHashMap<>(); // preserve insertion order

    // Main windows
    private JFrame loginFrame;
    private JTextField tfUser;
    private JPasswordField pfPass;

    // Session
    private User currentUser = null;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginSystemAdvanced().start());
    }

    private void start() {
        loadUsers();
        ensureAdminExists();
        showLoginWindow();
        autoLoginSession();
    }

    // -------------------- User POJO --------------------
    static class User {
        String username;
        String password;
        String role; // "ADMIN" or "USER"
        String question;
        String answer;

        User(String username, String password, String role, String question, String answer) {
            this.username = username;
            this.password = password;
            this.role = role;
            this.question = question;
            this.answer = answer;
        }
    }

    // -------------------- Persistence --------------------
    private void loadUsers() {
        users.clear();
        try {
            if (!USER_FILE.exists()) return;
            List<String> lines = Files.readAllLines(USER_FILE.toPath());
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                // format: username|password|role|question|answer
                String[] p = line.split("\\|", -1);
                if (p.length >= 5) {
                    users.put(p[0], new User(p[0], p[1], p[2], p[3], p[4]));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed to load users: " + e.getMessage());
        }
    }

    private void saveUsers() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(USER_FILE))) {
            for (User u : users.values()) {
                pw.println(escape(u.username) + "|" + escape(u.password) + "|" + escape(u.role) + "|" + escape(u.question) + "|" + escape(u.answer));
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed to save users: " + e.getMessage());
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("|", "\\|");
    }

    private String unescape(String s) {
        if (s == null) return "";
        return s.replace("\\|", "|");
    }

    // -------------------- History --------------------
    private void logHistory(String username, String status) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(HISTORY_FILE, true))) {
            String dt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            pw.println(escape(username) + "|" + dt + "|" + escape(status));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Export history to CSV
    private void exportHistoryCsv(Component parent) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("login_history.csv"));
        int ok = fc.showSaveDialog(parent);
        if (ok != JFileChooser.APPROVE_OPTION) return;
        File out = fc.getSelectedFile();
        try (BufferedReader br = new BufferedReader(new FileReader(HISTORY_FILE));
             PrintWriter pw = new PrintWriter(new FileWriter(out))) {
            pw.println("username,datetime,status");
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split("\\|", -1);
                String user = p.length>0?unescape(p[0]):"";
                String dt = p.length>1?p[1]:"";
                String st = p.length>2?unescape(p[2]):"";
                // escape quotes
                user = user.replace("\"","\"\"");
                st = st.replace("\"","\"\"");
                pw.println("\""+user+"\",\""+dt+"\",\""+st+"\"");
            }
            JOptionPane.showMessageDialog(parent, "Exported to: " + out.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(parent, "Export failed: " + e.getMessage());
        }
    }

    // -------------------- Session --------------------
    private void saveSession(String username) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(SESSION_FILE))) {
            pw.println(username == null ? "" : username);
        } catch (Exception e) { /* ignore */ }
    }

    private void autoLoginSession() {
        if (!SESSION_FILE.exists()) return;
        try {
            List<String> lines = Files.readAllLines(SESSION_FILE.toPath());
            if (lines.isEmpty()) return;
            String last = lines.get(0).trim();
            if (!last.isEmpty() && users.containsKey(last)) {
                // make small delay and open dashboard for last user
                SwingUtilities.invokeLater(() -> openDashboard(users.get(last)));
            }
        } catch (Exception e) { /* ignore */ }
    }

    // -------------------- UI: Login Window --------------------
    private void showLoginWindow() {
        loginFrame = new JFrame("Login System - Advanced");
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setSize(420, 320);
        loginFrame.setLayout(null);
        loginFrame.setLocationRelativeTo(null);

        JLabel title = new JLabel("Login System", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setBounds(40, 10, 340, 40);
        loginFrame.add(title);

        JLabel lblUser = new JLabel("Username:");
        lblUser.setBounds(50, 70, 80, 25);
        loginFrame.add(lblUser);

        tfUser = new JTextField();
        tfUser.setBounds(140, 70, 220, 25);
        loginFrame.add(tfUser);

        JLabel lblPass = new JLabel("Password:");
        lblPass.setBounds(50, 110, 80, 25);
        loginFrame.add(lblPass);

        pfPass = new JPasswordField();
        pfPass.setBounds(140, 110, 220, 25);
        loginFrame.add(pfPass);

        JButton btnLogin = new JButton("Login");
        btnLogin.setBounds(50, 160, 100, 30);
        btnLogin.addActionListener(e -> doLogin());
        loginFrame.add(btnLogin);

        JButton btnRegister = new JButton("Register");
        btnRegister.setBounds(170, 160, 100, 30);
        btnRegister.addActionListener(e -> showRegisterDialog());
        loginFrame.add(btnRegister);

        JButton btnForgot = new JButton("Forgot Password");
        btnForgot.setBounds(290, 160, 120, 30);
        btnForgot.addActionListener(e -> doForgotPassword());
        loginFrame.add(btnForgot);

        JButton btnExit = new JButton("Exit");
        btnExit.setBounds(170, 210, 100, 30);
        btnExit.addActionListener(e -> System.exit(0));
        loginFrame.add(btnExit);

        loginFrame.setVisible(true);
    }

    private void doLogin() {
        String u = tfUser.getText().trim();
        String p = new String(pfPass.getPassword());

        if (!users.containsKey(u) || !users.get(u).password.equals(p)) {
            logHistory(u, "Failed");
            JOptionPane.showMessageDialog(loginFrame, "Invalid username or password.");
            return;
        }

        currentUser = users.get(u);
        logHistory(u, "Success");
        saveSession(u);
        openDashboard(currentUser);
    }

    // -------------------- Register --------------------
    private void showRegisterDialog() {
        JDialog dlg = new JDialog(loginFrame, "Register", true);
        dlg.setSize(420, 320);
        dlg.setLayout(null);
        dlg.setLocationRelativeTo(loginFrame);

        JLabel l1 = new JLabel("Username:"); l1.setBounds(30, 30, 100, 25); dlg.add(l1);
        JTextField tu = new JTextField(); tu.setBounds(140, 30, 220, 25); dlg.add(tu);

        JLabel l2 = new JLabel("Password:"); l2.setBounds(30, 70, 100, 25); dlg.add(l2);
        JPasswordField tp = new JPasswordField(); tp.setBounds(140, 70, 220, 25); dlg.add(tp);

        JLabel l3 = new JLabel("Role:"); l3.setBounds(30, 110, 100, 25); dlg.add(l3);
        JComboBox<String> cbRole = new JComboBox<>(new String[]{"USER", "ADMIN"}); cbRole.setBounds(140,110,220,25); dlg.add(cbRole);

        JLabel l4 = new JLabel("Security Q:"); l4.setBounds(30,150,100,25); dlg.add(l4);
        JTextField tq = new JTextField(); tq.setBounds(140,150,220,25); dlg.add(tq);

        JLabel l5 = new JLabel("Answer:"); l5.setBounds(30,190,100,25); dlg.add(l5);
        JTextField ta = new JTextField(); ta.setBounds(140,190,220,25); dlg.add(ta);

        JButton bCreate = new JButton("Create"); bCreate.setBounds(140,230,100,30);
        bCreate.addActionListener(ev -> {
            String user = tu.getText().trim();
            String pass = new String(tp.getPassword());
            String role = (String) cbRole.getSelectedItem();
            String q = tq.getText().trim();
            String a = ta.getText().trim();

            if (user.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "Username & Password required.");
                return;
            }
            if (users.containsKey(user)) {
                JOptionPane.showMessageDialog(dlg, "Username exists.");
                return;
            }
            User nu = new User(user, pass, role, q, a);
            users.put(user, nu);
            saveUsers();
            JOptionPane.showMessageDialog(dlg, "Created user: " + user);
            dlg.dispose();
        });
        dlg.add(bCreate);

        dlg.setVisible(true);
    }

    // -------------------- Forgot Password --------------------
    private void doForgotPassword() {
        String user = JOptionPane.showInputDialog(loginFrame, "Enter username:");
        if (user == null || user.isBlank()) return;
        if (!users.containsKey(user)) {
            JOptionPane.showMessageDialog(loginFrame, "User not found.");
            return;
        }
        User u = users.get(user);
        String ans = JOptionPane.showInputDialog(loginFrame, u.question == null || u.question.isBlank() ? "Security question?" : u.question);
        if (ans == null) return;
        if (ans.equals(u.answer)) {
            String newPass = JOptionPane.showInputDialog(loginFrame, "Enter new password:");
            if (newPass != null && !newPass.isBlank()) {
                u.password = newPass;
                saveUsers();
                JOptionPane.showMessageDialog(loginFrame, "Password changed.");
            }
        } else {
            JOptionPane.showMessageDialog(loginFrame, "Incorrect answer.");
        }
    }

    // -------------------- Dashboard / Multi-window --------------------
    private void openDashboard(User u) {
        // close login window (but keep session file)
        if (loginFrame != null) loginFrame.dispose();

        if (u.role.equals("ADMIN")) showAdminDashboard(u);
        else showUserDashboard(u);
    }

    // ---------- User Dashboard ----------
    private void showUserDashboard(User u) {
        JFrame f = new JFrame("User Dashboard - " + u.username);
        f.setSize(500, 320);
        f.setLocationRelativeTo(null);
        f.setLayout(new BorderLayout());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Logged in as: " + u.username + " (USER)"));
        JButton btnLogout = new JButton("Logout"); top.add(btnLogout);
        JButton btnProfile = new JButton("Edit Profile"); top.add(btnProfile);
        f.add(top, BorderLayout.NORTH);

        JTextArea info = new JTextArea();
        info.setEditable(false);
        info.setText("Welcome, " + u.username + "!\n\nUse Edit Profile to change your password or security question.\nLogin history is available to admin.");
        f.add(new JScrollPane(info), BorderLayout.CENTER);

        btnLogout.addActionListener(e -> {
            saveSession("");
            f.dispose();
            // reopen login window
            SwingUtilities.invokeLater(() -> {
                start(); // restart app UI
            });
        });

        btnProfile.addActionListener(e -> showProfileEditor(u, f));

        f.setVisible(true);
    }

    // ---------- Admin Dashboard ----------
    private void showAdminDashboard(User admin) {
        JFrame f = new JFrame("Admin Dashboard - " + admin.username);
        f.setSize(800, 500);
        f.setLocationRelativeTo(null);
        f.setLayout(new BorderLayout());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Admin: " + admin.username));
        JButton btnLogout = new JButton("Logout"); top.add(btnLogout);
        JButton btnRefresh = new JButton("Refresh Users"); top.add(btnRefresh);
        JButton btnExport = new JButton("Export History CSV"); top.add(btnExport);
        f.add(top, BorderLayout.NORTH);

        // user table
        String[] cols = {"Username","Role","Security Question","Security Answer","Actions"};
        DefaultTableModel model = new DefaultTableModel(cols,0) {
            public boolean isCellEditable(int r,int c){ return false; }
        };
        JTable table = new JTable(model);
        table.setRowHeight(22);
        refreshUserTable(model);
        f.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnViewHistory = new JButton("View Login History");
        JButton btnCreateUser = new JButton("Create New User");
        JButton btnEdit = new JButton("Edit Selected");
        JButton btnDelete = new JButton("Delete Selected");
        bottom.add(btnViewHistory); bottom.add(btnCreateUser); bottom.add(btnEdit); bottom.add(btnDelete);
        f.add(bottom, BorderLayout.SOUTH);

        btnLogout.addActionListener(e -> {
            saveSession("");
            f.dispose();
            SwingUtilities.invokeLater(() -> start());
        });

        btnRefresh.addActionListener(e -> refreshUserTable(model));

        btnExport.addActionListener(e -> exportHistoryCsv(f));

        btnViewHistory.addActionListener(e -> showHistoryWindow(f));

        btnCreateUser.addActionListener(e -> {
            f.setEnabled(false);
            showRegisterDialog();
            f.setEnabled(true);
            refreshUserTable(model);
        });

        btnEdit.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(f, "Select a user"); return; }
            String username = (String) model.getValueAt(row,0);
            showAdminEditDialog(username, f);
            refreshUserTable(model);
        });

        btnDelete.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(f, "Select a user"); return; }
            String username = (String) model.getValueAt(row,0);
            if (username.equals("admin")) { JOptionPane.showMessageDialog(f, "Cannot delete admin"); return; }
            int yn = JOptionPane.showConfirmDialog(f, "Delete user: " + username + " ?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (yn == JOptionPane.YES_OPTION) {
                users.remove(username);
                saveUsers();
                refreshUserTable(model);
                JOptionPane.showMessageDialog(f, "Deleted " + username);
            }
        });

        f.setVisible(true);
    }

    private void refreshUserTable(DefaultTableModel model) {
        model.setRowCount(0);
        for (User u : users.values()) {
            model.addRow(new Object[]{u.username, u.role, u.question, u.answer, "Edit/Delete"});
        }
    }

    // -------------------- User Profile Editor --------------------
    private void showProfileEditor(User u, Window parent) {
        JDialog dlg = new JDialog((Frame) null, "Edit Profile - " + u.username, true);
        dlg.setSize(420, 300);
        dlg.setLocationRelativeTo(parent);
        dlg.setLayout(null);

        JLabel l1 = new JLabel("Username:"); l1.setBounds(20,20,100,25); dlg.add(l1);
        JLabel lu = new JLabel(u.username); lu.setBounds(140,20,220,25); dlg.add(lu);

        JLabel l2 = new JLabel("Current Password:"); l2.setBounds(20,60,120,25); dlg.add(l2);
        JPasswordField cur = new JPasswordField(); cur.setBounds(140,60,220,25); dlg.add(cur);

        JLabel l3 = new JLabel("New Password:"); l3.setBounds(20,100,120,25); dlg.add(l3);
        JPasswordField np = new JPasswordField(); np.setBounds(140,100,220,25); dlg.add(np);

        JLabel l4 = new JLabel("Security Q:"); l4.setBounds(20,140,120,25); dlg.add(l4);
        JTextField tq = new JTextField(u.question); tq.setBounds(140,140,220,25); dlg.add(tq);

        JLabel l5 = new JLabel("Answer:"); l5.setBounds(20,180,120,25); dlg.add(l5);
        JTextField ta = new JTextField(u.answer); ta.setBounds(140,180,220,25); dlg.add(ta);

        JButton bSave = new JButton("Save"); bSave.setBounds(140,220,100,30);
        bSave.addActionListener(ev -> {
            String curPass = new String(cur.getPassword());
            String newPass = new String(np.getPassword());
            if (!curPass.equals(u.password)) {
                JOptionPane.showMessageDialog(dlg, "Current password incorrect.");
                return;
            }
            if (!newPass.isBlank()) u.password = newPass;
            u.question = tq.getText();
            u.answer = ta.getText();
            users.put(u.username, u);
            saveUsers();
            JOptionPane.showMessageDialog(dlg, "Profile updated.");
            dlg.dispose();
        });
        dlg.add(bSave);

        dlg.setVisible(true);
    }

    // -------------------- Admin Edit Dialog --------------------
    private void showAdminEditDialog(String username, Window parent) {
        User u = users.get(username);
        if (u == null) { JOptionPane.showMessageDialog(parent, "User not found"); return; }

        JDialog dlg = new JDialog((Frame)null, "Edit User - " + username, true);
        dlg.setSize(480, 360);
        dlg.setLocationRelativeTo(parent);
        dlg.setLayout(null);

        JLabel l1 = new JLabel("Username:"); l1.setBounds(20,20,120,25); dlg.add(l1);
        JLabel lu = new JLabel(u.username); lu.setBounds(160,20,260,25); dlg.add(lu);

        JLabel l2 = new JLabel("Password:"); l2.setBounds(20,60,120,25); dlg.add(l2);
        JTextField tp = new JTextField(u.password); tp.setBounds(160,60,260,25); dlg.add(tp);

        JLabel l3 = new JLabel("Role:"); l3.setBounds(20,100,120,25); dlg.add(l3);
        JComboBox<String> cbRole = new JComboBox<>(new String[]{"USER","ADMIN"}); cbRole.setBounds(160,100,260,25); cbRole.setSelectedItem(u.role); dlg.add(cbRole);

        JLabel l4 = new JLabel("Security Q:"); l4.setBounds(20,140,120,25); dlg.add(l4);
        JTextField tq = new JTextField(u.question); tq.setBounds(160,140,260,25); dlg.add(tq);

        JLabel l5 = new JLabel("Answer:"); l5.setBounds(20,180,120,25); dlg.add(l5);
        JTextField ta = new JTextField(u.answer); ta.setBounds(160,180,260,25); dlg.add(ta);

        JButton bSave = new JButton("Save Changes"); bSave.setBounds(160,230,140,30);
        bSave.addActionListener(ev -> {
            String newPass = tp.getText();
            String newRole = (String) cbRole.getSelectedItem();
            String newQ = tq.getText();
            String newA = ta.getText();

            if (u.username.equals("admin") && !newRole.equals("ADMIN")) {
                JOptionPane.showMessageDialog(dlg, "Admin must keep ADMIN role.");
                cbRole.setSelectedItem("ADMIN");
                return;
            }

            u.password = newPass;
            u.role = newRole;
            u.question = newQ;
            u.answer = newA;
            users.put(u.username, u);
            saveUsers();
            JOptionPane.showMessageDialog(dlg, "User updated.");
            dlg.dispose();
        });
        dlg.add(bSave);

        dlg.setVisible(true);
    }

    // -------------------- History Viewer --------------------
    private void showHistoryWindow(Component parent) {
        JDialog dlg = new JDialog((Frame)null, "Login History", true);
        dlg.setSize(700, 500);
        dlg.setLocationRelativeTo(parent);
        dlg.setLayout(new BorderLayout());

        String[] cols = {"Username","DateTime","Status"};
        DefaultTableModel tm = new DefaultTableModel(cols,0) {
            public boolean isCellEditable(int r,int c){return false;}
        };
        JTable tbl = new JTable(tm);
        dlg.add(new JScrollPane(tbl), BorderLayout.CENTER);

        // load history
        try {
            if (HISTORY_FILE.exists()) {
                List<String> lines = Files.readAllLines(HISTORY_FILE.toPath());
                for (String line : lines) {
                    String[] p = line.split("\\|", -1);
                    String user = p.length>0?unescape(p[0]) : "";
                    String dt = p.length>1?p[1] : "";
                    String st = p.length>2?unescape(p[2]) : "";
                    tm.addRow(new Object[]{user, dt, st});
                }
            }
        } catch (Exception e) { e.printStackTrace(); }

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnExport = new JButton("Export CSV");
        btnExport.addActionListener(e -> exportHistoryCsv(dlg));
        bottom.add(btnExport);

        dlg.add(bottom, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    // -------------------- Admin helpers --------------------
    private void refreshUserTableModel(DefaultTableModel model) {
        model.setRowCount(0);
        for (User u : users.values()) {
            model.addRow(new Object[]{u.username, u.role, u.question, u.answer});
        }
    }

    // -------------------- Utilities --------------------
    private void ensureAdminExists() {
        if (!users.containsKey("admin")) {
            users.put("admin", new User("admin", "admin123", "ADMIN", "Default admin question", "admin"));
            saveUsers();
        }
    }
}
