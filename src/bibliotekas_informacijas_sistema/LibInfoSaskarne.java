package bibliotekas_informacijas_sistema;

import java.awt.Component;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JOptionPane;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import model.Eksemplars;
import model.Gramata;
import model.Izsniegums;
import model.Lietotajs;
import model.Loma;
import model.Rezervacija;
import dao.RezervacijaDAO;
import dao.LietotajsDAO;
import dao.GramataDAO;
import dao.EksemplarsDAO;
import dao.IzsniegumsDAO;


public class LibInfoSaskarne extends javax.swing.JFrame {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final LietotajsDAO lietotajsDAO = new LietotajsDAO();
    private final GramataDAO gramataDAO = new GramataDAO();
    private final EksemplarsDAO eksemplarsDAO = new EksemplarsDAO();
    private final IzsniegumsDAO izsniegumsDAO = new IzsniegumsDAO();
    private final RezervacijaDAO rezervacijaDAO = new RezervacijaDAO();
    private Lietotajs currentUser;
    private int editingBookId = -1;
    private Integer originalBookYear;
    private boolean bookYearEdited;
    private int editingUserId = -1;
    private final Set<String> readNotificationKeys = new HashSet<>();
    private final List<Lietotajs> loanReaders = new ArrayList<>();
    private final List<Eksemplars> loanCopies = new ArrayList<>();
    private final List<Gramata> reservationBooks = new ArrayList<>();
    private boolean filterComboInitialized = false;
    private boolean suppressFilterAction = false;

    /* ==================== Utilītmetodes ==================== */

    private List<JDialog> allDialogs() {
        List<JDialog> result = new ArrayList<>();
        result.add(LoginDialog); result.add(RegisterDialog);
        result.add(BookListDialog); result.add(BookEditDialog); result.add(UserListFrameDialog);
        result.add(UserEditDialog); result.add(LoanDialog); result.add(ReturnDialog);
        result.add(ReservationDialog); result.add(NotificationDialog);
        return result;
    }

    private void hideAllDialogs() {
        for (JDialog d : allDialogs()) d.setVisible(false);
    }

    private void showDialog(JDialog dialog) {
        setVisible(false);
        hideAllDialogs();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
        dialog.toFront();
    }

    private Component getMessageParent() {
        for (JDialog dialog : allDialogs()) {
            if (dialog.isVisible()) return dialog;
        }
        return this;
    }

    private void showInfo(String message) {
        JOptionPane.showMessageDialog(getMessageParent(), message,
                "LibInfo", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(Throwable ex) {
        String message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
        JOptionPane.showMessageDialog(getMessageParent(), message,
                "LibInfo kļūda", JOptionPane.ERROR_MESSAGE);
    }

    private boolean isStaff() { return currentUser != null && (currentUser.getLoma() == Loma.ADMIN || currentUser.getLoma() == Loma.LIBRARIAN); }
    private boolean isAdmin() { return currentUser != null && currentUser.getLoma() == Loma.ADMIN; }
    private boolean isReader() { return currentUser != null && currentUser.getLoma() == Loma.READER; }

    private void updateMainRoleAccess() {
        MainFrameStatusLabel.setText("Pieteicies: " + (currentUser == null ? "—" : currentUser.getLietotajvards()) + " | Loma: " + (currentUser == null ? "—" : currentUser.getLoma()));
        boolean staff = isStaff();
        boolean reader = isReader();

        BookAddButton.setEnabled(staff);
        BookListButton.setEnabled(true);
        BookReturnButton.setEnabled(staff);
        BookLoanButton.setEnabled(staff);
        UserListButton.setEnabled(staff);
        ReservationButton.setEnabled(staff || reader);
        ReservationMenu.setEnabled(staff || reader);

        UserMenu.setEnabled(staff);
        LoanMenu.setEnabled(staff);
        NotificationMenu.setEnabled(staff || reader);
        NotificationsButton.setEnabled(staff || reader);
        if (menuAddBookItem != null) menuAddBookItem.setEnabled(staff);
        if (menuAddUserItem != null) menuAddUserItem.setEnabled(staff);

        setTitle("LibInfo - Galvenais logs" + (currentUser == null ? "" : " — " + currentUser.getLoma()));
    }

    private void showMain() {
        updateMainRoleAccess();
        hideAllDialogs();
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
        toFront();
    }

    private void returnToMain(JDialog source) {
        source.setVisible(false);
        clearDialogFields(source);
        showMain();
    }

    private void clearDialogFields(JDialog dialog) {
        if (dialog == BookListDialog) {
            SearchTextField.setText("");
            if (FilterComboBox.getItemCount() > 0) FilterComboBox.setSelectedIndex(0);
            clearTable(BookTable);
        } else if (dialog == BookEditDialog) {
            BookNameTextField.setText(""); BookAuthorTextField.setSelectedItem(""); BookISBNTextField.setText("");
            BookCategoryTextField.setSelectedItem(""); BookYearSpinner.setValue(java.time.LocalDate.now().getYear());
            BookDescriptionTextArea.setText(""); BookAvailabilityCheckBox.setSelected(true);
            originalBookYear = null; bookYearEdited = false;
        } else if (dialog == UserListFrameDialog) {
            UserSearchTextField.setText("");
            clearTable(UserTable);
        } else if (dialog == UserEditDialog) {
            UserNameTextField.setText(""); UserSurnameTextField.setText(""); UsernickTextField.setText("");
            UserPasswordTextField.setText(""); UserEMailTextField.setText(""); UserRoleComboBox.setSelectedItem("READER");
        } else if (dialog == LoanDialog) {
            ReaderComboBox.setModel(new DefaultComboBoxModel<>());
            BookComboBox.setModel(new DefaultComboBoxModel<>());
            LoanDateTextField.setText("");
            LoanTimeFormattedTextField.setValue(null);
            LoanTimeFormattedTextField.setText("");
            loanReaders.clear();
            loanCopies.clear();
        } else if (dialog == ReturnDialog) {
            clearTable(ReturnTable);
            ReturnLabel.setText("Aktīvie izsniegumi");
        } else if (dialog == ReservationDialog) {
            clearTable(ReservationTable);
        } else if (dialog == NotificationDialog) {
            clearTable(NotificationTable);
        }
    }

    private void clearTable(JTable table) {
        ((DefaultTableModel) table.getModel()).setRowCount(0);
        table.clearSelection();
    }

    private static String nvl(String value) { return value == null ? "" : value; }

    private void loadBooksFromUi() { loadBooksFromUi(false); }

    private void loadBooksFromUi(boolean notifyResult) {
        try {
            List<Gramata> books = gramataDAO.searchBooks(
                    SearchTextField.getText().trim(), String.valueOf(FilterComboBox.getSelectedItem()), currentUser);
            DefaultTableModel model = (DefaultTableModel) BookTable.getModel();
            model.setRowCount(0);
            for (Gramata book : books) {
                model.addRow(new Object[]{book.getGramatasId(), book.getNosaukums(), book.getAutors(),
                    book.getIsbn(), book.getKategorija(), book.getIzdosanasGads(), book.isPieejama() ? "Jā" : "Nē"});
            }
            if (notifyResult) showInfo("Grāmatu dati veiksmīgi nolasīti. Ierakstu skaits: " + books.size() + ".");
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void loadUsersFromUi() { loadUsersFromUi(true); }

    private void loadUsersFromUi(boolean showReadResult) {
        try {
            List<Lietotajs> users = lietotajsDAO.searchUsers(UserSearchTextField.getText(), currentUser);
            DefaultTableModel model = (DefaultTableModel) UserTable.getModel();
            model.setRowCount(0);
            for (Lietotajs user : users) {
                model.addRow(new Object[]{user.getLietotajaId(), user.getVards(), user.getUzvards(),
                    user.getLietotajvards(), user.getEpasts(), user.getLoma()});
            }
            if (showReadResult) showInfo("Lietotāju dati veiksmīgi nolasīti. Ierakstu skaits: " + users.size() + ".");
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void updateReservationActionButtons() {
        boolean activeSelection = false;
        int selectedViewRow = ReservationTable.getSelectedRow();
        if (selectedViewRow >= 0) {
            int selectedModelRow = ReservationTable.convertRowIndexToModel(selectedViewRow);
            Object statusValue = ReservationTable.getModel().getValueAt(selectedModelRow, 4);
            activeSelection = statusValue != null && RezervacijaDAO.isActiveStatus(statusValue.toString());
        }
        boolean ownsSelection = false;
        if (selectedViewRow >= 0) {
            int selectedModelRow = ReservationTable.convertRowIndexToModel(selectedViewRow);
            Object readerValue = ReservationTable.getModel().getValueAt(selectedModelRow, 1);
            ownsSelection = readerValue instanceof Lietotajs
                    && currentUser != null
                    && ((Lietotajs) readerValue).getLietotajaId() == currentUser.getLietotajaId();
        }
        NewReservationButton.setEnabled(isReader());
        CancelReservationButton.setEnabled(isReader() && activeSelection && ownsSelection);
        RunButton.setEnabled(isStaff() && activeSelection);
    }

    private void initializeApplication() {
        BookTable.getModel().addTableModelListener(e -> BookListDialogStatusLabel.setText("Kopā: " + BookTable.getRowCount() + " grāmatas"));
        UserTable.getModel().addTableModelListener(e -> UserListFrameDialogStatusLabel.setText("Kopā: " + UserTable.getRowCount() + " lietotāji"));
        ReservationTable.getModel().addTableModelListener(e -> ReservationDialogStatusLabel.setText("Kopā: " + ReservationTable.getRowCount() + " rezervācijas"));
        NotificationTable.getModel().addTableModelListener(e -> NotificationDialogStatusLabel.setText("Kopā: " + NotificationTable.getRowCount() + " paziņojumi"));
        for (JDialog dialog : allDialogs()) dialog.pack();
        try {
            lietotajsDAO.initializeDatabase();
        } catch (Exception ex) {
            showError(ex);
        }
        // Iekļauts showLogin() saturs
        SurnameTextField.setText(""); NameTextField.setText("");
        PasswordTextField1.setText(""); UsernameTextField1.setText("");
        EMailTextField.setText(""); PasswordProveTextField.setText("");
        UsernameTextField.setText("");
        PasswordTextField.setText("");
        showDialog(LoginDialog);
        UsernameTextField.requestFocusInWindow();
    }

    public LibInfoSaskarne() {
        initComponents();
        initializeApplication();
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        MainFrameStatusLabel = new javax.swing.JLabel();
        BookListDialogStatusLabel = new javax.swing.JLabel();
        UserSearchLabel = new javax.swing.JLabel();
        UserListFrameDialogStatusLabel = new javax.swing.JLabel();
        UserEditDialogStatusLabel = new javax.swing.JLabel();
        LoanDialogStatusLabel = new javax.swing.JLabel();
        ReturnDialogStatusLabel = new javax.swing.JLabel();
        ReservationDialogStatusLabel = new javax.swing.JLabel();
        NotificationDialogStatusLabel = new javax.swing.JLabel();
        LoginDialogSurface = new javax.swing.JPanel();
        RegisterDialogSurface = new javax.swing.JPanel();
        BookListDialogSurface = new javax.swing.JPanel();
        BookEditDialogSurface = new javax.swing.JPanel();
        UserListFrameDialogSurface = new javax.swing.JPanel();
        UserEditDialogSurface = new javax.swing.JPanel();
        LoanDialogSurface = new javax.swing.JPanel();
        ReturnDialogSurface = new javax.swing.JPanel();
        ReservationDialogSurface = new javax.swing.JPanel();
        NotificationDialogSurface = new javax.swing.JPanel();
        MainFrameSurface = new javax.swing.JPanel();


        LoginDialog = new javax.swing.JDialog();
        AppLabel = new javax.swing.JLabel();
        UsernameLabel = new javax.swing.JLabel();
        UsernameTextField = new javax.swing.JTextField();
        PasswordLabel = new javax.swing.JLabel();
        PasswordTextField = new javax.swing.JPasswordField();
        LoginButton = new javax.swing.JButton();
        RegisterButton = new javax.swing.JButton();
        CloseButton = new javax.swing.JButton();
        RegisterDialog = new javax.swing.JDialog();
        RegisterLabel = new javax.swing.JLabel();
        NameLabel = new javax.swing.JLabel();
        NameTextField = new javax.swing.JTextField();
        SurnameLabel = new javax.swing.JLabel();
        SurnameTextField = new javax.swing.JTextField();
        UsernameLabel1 = new javax.swing.JLabel();
        PasswordLabel1 = new javax.swing.JLabel();
        PasswordTextField1 = new javax.swing.JPasswordField();
        UsernameTextField1 = new javax.swing.JTextField();
        PasswordProveLabel = new javax.swing.JLabel();
        PasswordProveTextField = new javax.swing.JPasswordField();
        EMailLabel = new javax.swing.JLabel();
        EMailTextField = new javax.swing.JTextField();
        RegisterButton1 = new javax.swing.JButton();
        CancelButton = new javax.swing.JButton();
        BookListDialog = new javax.swing.JDialog();
        BookScrollPane = new javax.swing.JScrollPane();
        BookTable = new javax.swing.JTable();
        AddButton = new javax.swing.JButton();
        EditButton = new javax.swing.JButton();
        SearchTextField = new javax.swing.JTextField();
        SearchButton = new javax.swing.JButton();
        DeleteButton = new javax.swing.JButton();
        FilterLabel = new javax.swing.JLabel();
        FilterComboBox = new javax.swing.JComboBox<>();
        CloseButton2 = new javax.swing.JButton();
        BookEditDialog = new javax.swing.JDialog();
        BookNameLabel = new javax.swing.JLabel();
        SaveBookButton = new javax.swing.JButton();
        BookAuthorLabel = new javax.swing.JLabel();
        BookAuthorTextField = new javax.swing.JComboBox();
        BookNameTextField = new javax.swing.JTextField();
        BookISBNLabel = new javax.swing.JLabel();
        BookCategoryTextField = new javax.swing.JComboBox();
        BookCategoryLabel = new javax.swing.JLabel();
        BookISBNTextField = new javax.swing.JTextField();
        BookYearLabel = new javax.swing.JLabel();
        ReturnButton1 = new javax.swing.JButton();
        ISBNSearchButton = new javax.swing.JButton();
        BookYearSpinner = new javax.swing.JSpinner();
        BookDescriptionLabel = new javax.swing.JLabel();
        jScrollPane3 = new javax.swing.JScrollPane();
        BookDescriptionTextArea = new javax.swing.JTextArea();
        BookAvailabilityLabel = new javax.swing.JLabel();
        BookAvailabilityCheckBox = new javax.swing.JCheckBox();
        UserListFrameDialog = new javax.swing.JDialog();
        UserScrollPane = new javax.swing.JScrollPane();
        UserTable = new javax.swing.JTable();
        AddUserButton = new javax.swing.JButton();
        ReturnButton2 = new javax.swing.JButton();
        EditUserButton = new javax.swing.JButton();
        UserSearchTextField = new javax.swing.JTextField();
        UserSearchButton = new javax.swing.JButton();
        DeleteUserButton = new javax.swing.JButton();
        EditUserRoleButton = new javax.swing.JButton();
        UserEditDialog = new javax.swing.JDialog();
        UserNameLabel = new javax.swing.JLabel();
        ReturnButton3 = new javax.swing.JButton();
        UserSurnameLabel = new javax.swing.JLabel();
        UserSurnameTextField = new javax.swing.JTextField();
        UserNameTextField = new javax.swing.JTextField();
        UsernickLabel = new javax.swing.JLabel();
        UserPasswordTextField = new javax.swing.JPasswordField();
        UserPasswordLabel = new javax.swing.JLabel();
        UsernickTextField = new javax.swing.JTextField();
        UserEMailTextField = new javax.swing.JTextField();
        UserEMailLabel = new javax.swing.JLabel();
        UserRoleLabel = new javax.swing.JLabel();
        UserRoleComboBox = new javax.swing.JComboBox<>();
        SaveUserButton = new javax.swing.JButton();
        LoanDialog = new javax.swing.JDialog();
        ReaderLabel = new javax.swing.JLabel();
        BookComboBox = new javax.swing.JComboBox<>();
        BookLabel = new javax.swing.JLabel();
        ReaderComboBox = new javax.swing.JComboBox<>();
        LoanDateLabel = new javax.swing.JLabel();
        LoanDateTextField = new javax.swing.JTextField();
        LoanTimeLabel = new javax.swing.JLabel();
        LoanTimeFormattedTextField = new javax.swing.JFormattedTextField();
        LoanButton = new javax.swing.JButton();
        ReturnButton4 = new javax.swing.JButton();
        ReturnDialog = new javax.swing.JDialog();
        ReturnLabel = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        ReturnTable = new javax.swing.JTable();
        ReturnButton = new javax.swing.JButton();
        jButton19 = new javax.swing.JButton();
        ReservationDialog = new javax.swing.JDialog();
        ReservationLabel = new javax.swing.JLabel();
        ReservationScrollPane = new javax.swing.JScrollPane();
        ReservationTable = new javax.swing.JTable();
        NewReservationButton = new javax.swing.JButton();
        CancelReservationButton = new javax.swing.JButton();
        CloseButton3 = new javax.swing.JButton();
        RunButton = new javax.swing.JButton();
        NotificationDialog = new javax.swing.JDialog();
        NotificationLabel = new javax.swing.JLabel();
        NotificationScrollPane = new javax.swing.JScrollPane();
        NotificationTable = new javax.swing.JTable();
        ReadButton = new javax.swing.JButton();
        CloseButton4 = new javax.swing.JButton();
        BookListButton = new javax.swing.JButton();
        BookAddButton = new javax.swing.JButton();
        UserListButton = new javax.swing.JButton();
        BookLoanButton = new javax.swing.JButton();
        ReservationButton = new javax.swing.JButton();
        BookReturnButton = new javax.swing.JButton();
        NotificationsButton = new javax.swing.JButton();
        CloseButton1 = new javax.swing.JButton();
        ActionsMenuBar = new javax.swing.JMenuBar();
        FileMenu = new javax.swing.JMenu();
        menuExitItem = new javax.swing.JMenuItem();
        BookMenu = new javax.swing.JMenu();
        menuBooksItem = new javax.swing.JMenuItem();
        menuAddBookItem = new javax.swing.JMenuItem();
        menuSearchBookItem = new javax.swing.JMenuItem();
        UserMenu = new javax.swing.JMenu();
        menuUsersItem = new javax.swing.JMenuItem();
        menuAddUserItem = new javax.swing.JMenuItem();
        LoanMenu = new javax.swing.JMenu();
        menuIssueItem = new javax.swing.JMenuItem();
        menuReturnItem = new javax.swing.JMenuItem();
        menuOverdueItem = new javax.swing.JMenuItem();
        ReservationMenu = new javax.swing.JMenu();
        menuReservationsItem = new javax.swing.JMenuItem();
        NotificationMenu = new javax.swing.JMenu();
        menuNotificationsItem = new javax.swing.JMenuItem();
        HelpMenu = new javax.swing.JMenu();
        menuAboutItem = new javax.swing.JMenuItem();

        LoginDialog.setResizable(false);

        UsernameTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UsernameTextFieldActionPerformed(evt);
            }
        });

        LoginButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LoginButtonActionPerformed(evt);
            }
        });

        RegisterButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                RegisterButtonActionPerformed(evt);
            }
        });

        CloseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                CloseButtonActionPerformed(evt);
            }
        });

        AppLabel.setText("LibInfo - Bibliotēkas sistēma");
        UsernameLabel.setText("Lietotājvārds:");
        PasswordLabel.setText("Parole:");
        LoginButton.setText("Pieteikties");
        RegisterButton.setText("Reģistrēties");
        CloseButton.setText("Aizvērt");

        RegisterDialog.setResizable(false);

        RegisterLabel.setText("Jauna lietotāja reģistrācija");

        NameLabel.setText("Vārds:");

        SurnameLabel.setText("Uzvārds:");

        UsernameLabel1.setText("Lietotājvārds:");

        PasswordLabel1.setText("Parole:");

        PasswordProveLabel.setText("Apstiprināt paroli:");

        EMailLabel.setText("E-pasts:");

        RegisterButton1.setText("Reģistrēties");
        RegisterButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                RegisterButton1ActionPerformed(evt);
            }
        });

        CancelButton.setText("Atcelt");
        CancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                CancelButtonActionPerformed(evt);
            }
        });

        BookListDialog.setResizable(true);
        BookListDialog.setSize(new java.awt.Dimension(900, 600));

        BookTable.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        BookTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "ID", "Nosaukums", "Autors", "ISBN", "Kategorija", "Gads", "Pieejamība"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        BookTable.setAutoCreateRowSorter(true);
        BookTable.setRowHeight(26);
        BookScrollPane.setViewportView(BookTable);
        if (BookTable.getColumnModel().getColumnCount() > 0) {
            BookTable.getColumnModel().getColumn(3).setResizable(false);
        }

        AddButton.setText("Pievienot");
        AddButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                AddButtonActionPerformed(evt);
            }
        });

        EditButton.setText("Rediģēt");
        EditButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                EditButtonActionPerformed(evt);
            }
        });

        SearchButton.setText("Meklēt");
        SearchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SearchButtonActionPerformed(evt);
            }
        });

        DeleteButton.setText("Dzēst");

        FilterLabel.setText("Filtrs:");

        FilterComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Visas", "Nosaukums", "Autors", "ISBN", "Kategorija" }));
        FilterComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                FilterComboBoxActionPerformed(evt);
            }
        });

        CloseButton2.setText("Aizvērt");
        CloseButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                CloseButton2ActionPerformed(evt);
            }
        });

        BookNameLabel.setText("Vārds:");

        SaveBookButton.setText("Saglabāt");
        SaveBookButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SaveBookButtonActionPerformed(evt);
            }
        });

        BookAuthorLabel.setText("Autors:");

        BookAuthorTextField.setEditable(true);

        BookNameTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                BookNameTextFieldActionPerformed(evt);
            }
        });

        BookISBNLabel.setText("ISBN:");

        BookCategoryTextField.setEditable(true);

        BookCategoryLabel.setText("Kategorija:");

        BookISBNTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                BookISBNTextFieldActionPerformed(evt);
            }
        });

        BookYearLabel.setText("Izdošanas gads:");

        ReturnButton1.setText("Atcelt");
        ReturnButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ReturnButton1ActionPerformed(evt);
            }
        });

        ISBNSearchButton.setText("Meklēt pēc ISBN");
        ISBNSearchButton.setMaximumSize(new java.awt.Dimension(100, 45));
        ISBNSearchButton.setMinimumSize(new java.awt.Dimension(100, 45));
        ISBNSearchButton.setPreferredSize(new java.awt.Dimension(100, 45));
        ISBNSearchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ISBNSearchButtonActionPerformed(evt);
            }
        });

        BookDescriptionLabel.setText("Apraksts:");

        BookDescriptionTextArea.setColumns(20);
        BookDescriptionTextArea.setRows(5);
        jScrollPane3.setViewportView(BookDescriptionTextArea);

        BookAvailabilityLabel.setText("Sākotnējais eksemplārs:");

        BookAvailabilityCheckBox.setText("Izveidot pieejamu eksemplāru");
        BookAvailabilityCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                BookAvailabilityCheckBoxActionPerformed(evt);
            }
        });

        UserTable.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        UserTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "ID", "Vārds", "Uzvārds", "Lietotājvārds", "E-pasts", "Loma"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        UserTable.setAutoCreateRowSorter(true);
        UserTable.setRowHeight(26);
        UserScrollPane.setViewportView(UserTable);
        if (UserTable.getColumnModel().getColumnCount() > 0) {
            UserTable.getColumnModel().getColumn(3).setResizable(false);
        }

        AddUserButton.setText("Pievienot");
        AddUserButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                AddUserButtonActionPerformed(evt);
            }
        });

        ReturnButton2.setText("Atcelt");
        ReturnButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ReturnButton2ActionPerformed(evt);
            }
        });

        EditUserButton.setText("Rediģēt");
        EditUserButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                EditUserButtonActionPerformed(evt);
            }
        });

        UserSearchButton.setText("Meklēt:");
        UserSearchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UserSearchButtonActionPerformed(evt);
            }
        });

        DeleteUserButton.setText("Dzēst");
        DeleteUserButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                DeleteUserButtonActionPerformed(evt);
            }
        });

        EditUserRoleButton.setText("Mainīt lomu");
        EditUserRoleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                EditUserRoleButtonActionPerformed(evt);
            }
        });

        UserEditDialog.setSize(new java.awt.Dimension(450, 450));

        UserNameLabel.setText("Vārds:");

        ReturnButton3.setText("Atcelt");
        ReturnButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ReturnButton3ActionPerformed(evt);
            }
        });

        UserSurnameLabel.setText("Uzvārds:");

        UsernickLabel.setText("Lietotājvārds:");

        UserPasswordTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UserPasswordTextFieldActionPerformed(evt);
            }
        });

        UserPasswordLabel.setText("Parole:");

        UserEMailLabel.setText("E-pasts:");

        UserRoleLabel.setText("Loma:");

        UserRoleComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "READER", "AUTHOR", "LIBRARIAN", "ADMIN" }));
        UserRoleComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UserRoleComboBoxActionPerformed(evt);
            }
        });

        SaveUserButton.setText("Saglabāt");
        SaveUserButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SaveUserButtonActionPerformed(evt);
            }
        });

        ReaderLabel.setText("Lasītājs:");

        BookComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        BookLabel.setText("Grāmata:");

        ReaderComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        LoanDateLabel.setText("Izsniegšanas datums:");

        LoanTimeLabel.setText("Termiņš:");

        LoanButton.setText("Izsniegt");

        ReturnButton4.setText("Atcelt");

        ReturnLabel.setText("Aktīvie izsniegumi");

        ReturnTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "ID", "Lasītājs", "Grāmata", "Izsniegts", "Termiņš"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        ReturnTable.setAutoCreateRowSorter(true);
        ReturnTable.setRowHeight(26);
        jScrollPane1.setViewportView(ReturnTable);

        ReturnButton.setText("Atgriezt");
        ReturnButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ReturnButtonActionPerformed(evt);
            }
        });

        jButton19.setText("Atcelt");

        ReservationLabel.setText("Rezervāciju saraksts");

        ReservationTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "ID", "Lasītājs", "Grāmata", "Datums", "Statuss"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        ReservationTable.setAutoCreateRowSorter(true);
        ReservationTable.setRowHeight(26);
        ReservationScrollPane.setViewportView(ReservationTable);
        if (ReservationTable.getColumnModel().getColumnCount() > 0) {
            ReservationTable.getColumnModel().getColumn(1).setResizable(false);
            ReservationTable.getColumnModel().getColumn(2).setResizable(false);
            ReservationTable.getColumnModel().getColumn(3).setResizable(false);
        }

        NewReservationButton.setText("Jauna rezervācija");
        NewReservationButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                NewReservationButtonActionPerformed(evt);
            }
        });

        CancelReservationButton.setText("Atcelt rezervāciju");
        CancelReservationButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                CancelReservationButtonActionPerformed(evt);
            }
        });

        CloseButton3.setText("Aizvērt");

        RunButton.setText("Izpildīt");
        RunButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                RunButtonActionPerformed(evt);
            }
        });

        NotificationLabel.setText("Mani paziņojumi");

        NotificationTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "ID", "Veids", "Teksts", "Datums", "Izlasīts"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        NotificationTable.setAutoCreateRowSorter(true);
        NotificationTable.setRowHeight(26);
        NotificationScrollPane.setViewportView(NotificationTable);
        if (NotificationTable.getColumnModel().getColumnCount() > 0) {
            NotificationTable.getColumnModel().getColumn(3).setResizable(false);
        }

        ReadButton.setText("Atzīmēt kā izlasītu");

        CloseButton4.setText("Aizvērt");

        setTitle("LibInfo - Galvenais logs");

        BookListButton.setText("Grāmatu saraksts");
        BookListButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                BookListButtonActionPerformed(evt);
            }
        });

        BookAddButton.setText("Pievienot grāmatu");

        UserListButton.setText("Lietotāju saraksts");

        BookLoanButton.setText("Izsniegt grāmatu");

        ReservationButton.setText("Rezervācijas");

        BookReturnButton.setText("Atgriezt grāmatu");

        NotificationsButton.setText("Paziņojumi");
        NotificationsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                NotificationsButtonActionPerformed(evt);
            }
        });

        CloseButton1.setText("Iziet");

        FileMenu.setText("Fails");

        menuExitItem.setText("Iziet");
        FileMenu.add(menuExitItem);

        ActionsMenuBar.add(FileMenu);

        BookMenu.setText("Grāmatas");

        menuBooksItem.setText("Saraksts");
        BookMenu.add(menuBooksItem);

        menuAddBookItem.setText("Pievienot");
        BookMenu.add(menuAddBookItem);

        menuSearchBookItem.setText("Meklēt");
        BookMenu.add(menuSearchBookItem);

        ActionsMenuBar.add(BookMenu);

        UserMenu.setText("Lietotāji");

        menuUsersItem.setText("Saraksts");
        UserMenu.add(menuUsersItem);

        menuAddUserItem.setText("Pievienot");
        UserMenu.add(menuAddUserItem);

        ActionsMenuBar.add(UserMenu);

        LoanMenu.setText("Izsniegums");

        menuIssueItem.setText("Izsniegt");
        LoanMenu.add(menuIssueItem);

        menuReturnItem.setText("Atgriezt");
        LoanMenu.add(menuReturnItem);

        menuOverdueItem.setText("Kavētie");
        LoanMenu.add(menuOverdueItem);

        ActionsMenuBar.add(LoanMenu);

        ReservationMenu.setText("Rezervācijas");

        menuReservationsItem.setText("Saraksts");
        ReservationMenu.add(menuReservationsItem);

        ActionsMenuBar.add(ReservationMenu);

        NotificationMenu.setText("Paziņojumi");

        menuNotificationsItem.setText("Mani paziņojumi");
        NotificationMenu.add(menuNotificationsItem);

        ActionsMenuBar.add(NotificationMenu);

        HelpMenu.setText("Palīdzība");

        menuAboutItem.setText("Par programmu");
        HelpMenu.add(menuAboutItem);

        ActionsMenuBar.add(HelpMenu);

        setJMenuBar(ActionsMenuBar);

        LoginDialogSurface.setBackground(new java.awt.Color(240, 240, 240));
        AppLabel.setText("LibInfo - Bibliotēkas sistēma");
        AppLabel.setFont(new java.awt.Font("Arial", 1, 16));
        AppLabel.setHorizontalAlignment(0);
        UsernameLabel.setText("Lietotājvārds:");
        UsernameLabel.setFont(new java.awt.Font("Arial", 0, 12));
        UsernameLabel.setHorizontalAlignment(4);
        UsernameTextField.setFont(new java.awt.Font("Arial", 0, 12));
        PasswordLabel.setText("Parole:");
        PasswordLabel.setFont(new java.awt.Font("Arial", 0, 12));
        PasswordLabel.setHorizontalAlignment(4);
        PasswordTextField.setFont(new java.awt.Font("Arial", 0, 12));
        LoginButton.setText("Pieteikties");
        LoginButton.setFont(new java.awt.Font("Arial", 0, 12));
        LoginButton.setBackground(new java.awt.Color(225, 225, 225));
        RegisterButton.setText("Reģistrēties");
        RegisterButton.setFont(new java.awt.Font("Arial", 0, 12));
        RegisterButton.setBackground(new java.awt.Color(225, 225, 225));
        CloseButton.setText("Aizvērt");
        CloseButton.setFont(new java.awt.Font("Arial", 0, 12));
        CloseButton.setBackground(new java.awt.Color(225, 225, 225));
        RegisterDialogSurface.setBackground(new java.awt.Color(240, 240, 240));
        RegisterLabel.setText("Jauna lietotāja reģistrācija");
        RegisterLabel.setFont(new java.awt.Font("Arial", 1, 16));
        RegisterLabel.setHorizontalAlignment(0);
        NameLabel.setText("Vārds:");
        NameLabel.setFont(new java.awt.Font("Arial", 0, 12));
        NameLabel.setHorizontalAlignment(4);
        NameTextField.setFont(new java.awt.Font("Arial", 0, 12));
        SurnameLabel.setText("Uzvārds:");
        SurnameLabel.setFont(new java.awt.Font("Arial", 0, 12));
        SurnameLabel.setHorizontalAlignment(4);
        SurnameTextField.setFont(new java.awt.Font("Arial", 0, 12));
        UsernameLabel1.setText("Lietotājvārds:");
        UsernameLabel1.setFont(new java.awt.Font("Arial", 0, 12));
        UsernameLabel1.setHorizontalAlignment(4);
        PasswordLabel1.setText("Parole:");
        PasswordLabel1.setFont(new java.awt.Font("Arial", 0, 12));
        PasswordLabel1.setHorizontalAlignment(4);
        PasswordTextField1.setFont(new java.awt.Font("Arial", 0, 12));
        UsernameTextField1.setFont(new java.awt.Font("Arial", 0, 12));
        PasswordProveLabel.setText("Apstiprināt paroli:");
        PasswordProveLabel.setFont(new java.awt.Font("Arial", 0, 12));
        PasswordProveLabel.setHorizontalAlignment(4);
        PasswordProveTextField.setFont(new java.awt.Font("Arial", 0, 12));
        EMailLabel.setText("E-pasts:");
        EMailLabel.setFont(new java.awt.Font("Arial", 0, 12));
        EMailLabel.setHorizontalAlignment(4);
        EMailTextField.setFont(new java.awt.Font("Arial", 0, 12));
        RegisterButton1.setText("Reģistrēties");
        RegisterButton1.setFont(new java.awt.Font("Arial", 0, 12));
        RegisterButton1.setBackground(new java.awt.Color(225, 225, 225));
        CancelButton.setText("Atcelt");
        CancelButton.setFont(new java.awt.Font("Arial", 0, 12));
        CancelButton.setBackground(new java.awt.Color(225, 225, 225));
        BookListDialogSurface.setBackground(new java.awt.Color(240, 240, 240));
        BookScrollPane.setFont(new java.awt.Font("Arial", 0, 12));
        AddButton.setText("Pievienot");
        AddButton.setFont(new java.awt.Font("Arial", 0, 12));
        AddButton.setBackground(new java.awt.Color(225, 225, 225));
        EditButton.setText("Rediģēt");
        EditButton.setFont(new java.awt.Font("Arial", 0, 12));
        EditButton.setBackground(new java.awt.Color(225, 225, 225));
        SearchTextField.setFont(new java.awt.Font("Arial", 0, 12));
        SearchButton.setText("Meklēt");
        SearchButton.setFont(new java.awt.Font("Arial", 0, 12));
        SearchButton.setBackground(new java.awt.Color(225, 225, 225));
        DeleteButton.setText("Dzēst");
        DeleteButton.setFont(new java.awt.Font("Arial", 0, 12));
        DeleteButton.setBackground(new java.awt.Color(225, 225, 225));
        FilterLabel.setText("Filtrs:");
        FilterLabel.setFont(new java.awt.Font("Arial", 0, 12));
        FilterLabel.setHorizontalAlignment(4);
        FilterComboBox.setFont(new java.awt.Font("Arial", 0, 12));
        CloseButton2.setText("Aizvērt");
        CloseButton2.setFont(new java.awt.Font("Arial", 0, 12));
        CloseButton2.setBackground(new java.awt.Color(225, 225, 225));
        BookListDialogStatusLabel.setText("Kopā: 0 grāmatas");
        BookListDialogStatusLabel.setFont(new java.awt.Font("Arial", 0, 12));
        BookListDialogStatusLabel.setHorizontalAlignment(2);
        BookEditDialogSurface.setBackground(new java.awt.Color(240, 240, 240));
        BookNameLabel.setText("Nosaukums:");
        BookNameLabel.setFont(new java.awt.Font("Arial", 0, 12));
        BookNameLabel.setHorizontalAlignment(4);
        SaveBookButton.setText("Saglabāt");
        SaveBookButton.setFont(new java.awt.Font("Arial", 0, 12));
        SaveBookButton.setBackground(new java.awt.Color(213, 229, 243));
        BookAuthorLabel.setText("Autors:");
        BookAuthorLabel.setFont(new java.awt.Font("Arial", 0, 12));
        BookAuthorLabel.setHorizontalAlignment(4);
        BookAuthorTextField.setFont(new java.awt.Font("Arial", 0, 12));
        BookNameTextField.setFont(new java.awt.Font("Arial", 0, 12));
        BookISBNLabel.setText("ISBN:");
        BookISBNLabel.setFont(new java.awt.Font("Arial", 0, 12));
        BookISBNLabel.setHorizontalAlignment(4);
        BookCategoryTextField.setFont(new java.awt.Font("Arial", 0, 12));
        BookCategoryLabel.setText("Kategorija:");
        BookCategoryLabel.setFont(new java.awt.Font("Arial", 0, 12));
        BookCategoryLabel.setHorizontalAlignment(4);
        BookISBNTextField.setFont(new java.awt.Font("Arial", 0, 12));
        BookYearLabel.setText("Izdošanas gads:");
        BookYearLabel.setFont(new java.awt.Font("Arial", 0, 12));
        BookYearLabel.setHorizontalAlignment(4);
        ReturnButton1.setText("Atcelt");
        ReturnButton1.setFont(new java.awt.Font("Arial", 0, 12));
        ReturnButton1.setBackground(new java.awt.Color(225, 225, 225));
        ISBNSearchButton.setText("Meklēt pēc ISBN");
        ISBNSearchButton.setFont(new java.awt.Font("Arial", 0, 12));
        ISBNSearchButton.setBackground(new java.awt.Color(213, 229, 243));
        BookYearSpinner.setFont(new java.awt.Font("Arial", 0, 12));
        BookDescriptionLabel.setText("Apraksts:");
        BookDescriptionLabel.setFont(new java.awt.Font("Arial", 0, 12));
        BookDescriptionLabel.setHorizontalAlignment(4);
        jScrollPane3.setFont(new java.awt.Font("Arial", 0, 12));
        BookAvailabilityLabel.setText("Pieejamība:");
        BookAvailabilityLabel.setFont(new java.awt.Font("Arial", 0, 12));
        BookAvailabilityLabel.setHorizontalAlignment(4);
        BookAvailabilityCheckBox.setText("Pieejama");
        BookAvailabilityCheckBox.setFont(new java.awt.Font("Arial", 0, 12));
        UserListFrameDialogSurface.setBackground(new java.awt.Color(240, 240, 240));
        UserScrollPane.setFont(new java.awt.Font("Arial", 0, 12));
        AddUserButton.setText("Pievienot");
        AddUserButton.setFont(new java.awt.Font("Arial", 0, 12));
        AddUserButton.setBackground(new java.awt.Color(225, 225, 225));
        ReturnButton2.setText("Atcelt");
        ReturnButton2.setFont(new java.awt.Font("Arial", 0, 12));
        ReturnButton2.setBackground(new java.awt.Color(225, 225, 225));
        EditUserButton.setText("Rediģēt");
        EditUserButton.setFont(new java.awt.Font("Arial", 0, 12));
        EditUserButton.setBackground(new java.awt.Color(225, 225, 225));
        UserSearchTextField.setFont(new java.awt.Font("Arial", 0, 12));
        UserSearchButton.setText("Meklēt:");
        UserSearchButton.setFont(new java.awt.Font("Arial", 0, 12));
        UserSearchButton.setBackground(new java.awt.Color(225, 225, 225));
        DeleteUserButton.setText("Dzēst");
        DeleteUserButton.setFont(new java.awt.Font("Arial", 0, 12));
        DeleteUserButton.setBackground(new java.awt.Color(225, 225, 225));
        EditUserRoleButton.setText("Mainīt lomu");
        EditUserRoleButton.setFont(new java.awt.Font("Arial", 0, 12));
        EditUserRoleButton.setBackground(new java.awt.Color(213, 229, 243));
        UserSearchLabel.setText("Meklēt:");
        UserSearchLabel.setFont(new java.awt.Font("Arial", 0, 12));
        UserSearchLabel.setHorizontalAlignment(4);
        UserListFrameDialogStatusLabel.setText("Kopā: 0 lietotāji");
        UserListFrameDialogStatusLabel.setFont(new java.awt.Font("Arial", 0, 12));
        UserListFrameDialogStatusLabel.setHorizontalAlignment(2);
        UserEditDialogSurface.setBackground(new java.awt.Color(240, 240, 240));
        UserNameLabel.setText("Vārds:");
        UserNameLabel.setFont(new java.awt.Font("Arial", 0, 12));
        UserNameLabel.setHorizontalAlignment(4);
        ReturnButton3.setText("Atcelt");
        ReturnButton3.setFont(new java.awt.Font("Arial", 0, 12));
        ReturnButton3.setBackground(new java.awt.Color(225, 225, 225));
        UserSurnameLabel.setText("Uzvārds:");
        UserSurnameLabel.setFont(new java.awt.Font("Arial", 0, 12));
        UserSurnameLabel.setHorizontalAlignment(4);
        UserSurnameTextField.setFont(new java.awt.Font("Arial", 0, 12));
        UserNameTextField.setFont(new java.awt.Font("Arial", 0, 12));
        UsernickLabel.setText("Lietotājvārds:");
        UsernickLabel.setFont(new java.awt.Font("Arial", 0, 12));
        UsernickLabel.setHorizontalAlignment(4);
        UserPasswordTextField.setFont(new java.awt.Font("Arial", 0, 12));
        UserPasswordLabel.setText("Parole:");
        UserPasswordLabel.setFont(new java.awt.Font("Arial", 0, 12));
        UserPasswordLabel.setHorizontalAlignment(4);
        UsernickTextField.setFont(new java.awt.Font("Arial", 0, 12));
        UserEMailTextField.setFont(new java.awt.Font("Arial", 0, 12));
        UserEMailLabel.setText("E-pasts:");
        UserEMailLabel.setFont(new java.awt.Font("Arial", 0, 12));
        UserEMailLabel.setHorizontalAlignment(4);
        UserRoleLabel.setText("Loma:");
        UserRoleLabel.setFont(new java.awt.Font("Arial", 0, 12));
        UserRoleLabel.setHorizontalAlignment(4);
        UserRoleComboBox.setFont(new java.awt.Font("Arial", 0, 12));
        SaveUserButton.setText("Saglabāt");
        SaveUserButton.setFont(new java.awt.Font("Arial", 0, 12));
        SaveUserButton.setBackground(new java.awt.Color(213, 229, 243));
        UserEditDialogStatusLabel.setText("Lomas: ADMIN / LIBRARIAN / READER / AUTHOR");
        UserEditDialogStatusLabel.setFont(new java.awt.Font("Arial", 0, 12));
        UserEditDialogStatusLabel.setHorizontalAlignment(2);
        LoanDialogSurface.setBackground(new java.awt.Color(240, 240, 240));
        ReaderLabel.setText("Lasītājs:");
        ReaderLabel.setFont(new java.awt.Font("Arial", 0, 12));
        ReaderLabel.setHorizontalAlignment(4);
        BookComboBox.setFont(new java.awt.Font("Arial", 0, 12));
        BookLabel.setText("Grāmata:");
        BookLabel.setFont(new java.awt.Font("Arial", 0, 12));
        BookLabel.setHorizontalAlignment(4);
        ReaderComboBox.setFont(new java.awt.Font("Arial", 0, 12));
        LoanDateLabel.setText("Izsniegšanas datums:");
        LoanDateLabel.setFont(new java.awt.Font("Arial", 0, 11));
        LoanDateLabel.setHorizontalAlignment(4);
        LoanDateTextField.setFont(new java.awt.Font("Arial", 0, 12));
        LoanTimeLabel.setText("Termiņš:");
        LoanTimeLabel.setFont(new java.awt.Font("Arial", 0, 12));
        LoanTimeLabel.setHorizontalAlignment(4);
        LoanTimeFormattedTextField.setFont(new java.awt.Font("Arial", 0, 12));
        LoanButton.setText("Izsniegt");
        LoanButton.setFont(new java.awt.Font("Arial", 0, 12));
        LoanButton.setBackground(new java.awt.Color(213, 229, 243));
        ReturnButton4.setText("Atcelt");
        ReturnButton4.setFont(new java.awt.Font("Arial", 0, 12));
        ReturnButton4.setBackground(new java.awt.Color(225, 225, 225));
        LoanDialogStatusLabel.setText("Statuss: grāmata tiks atzīmēta kā izsniegta");
        LoanDialogStatusLabel.setFont(new java.awt.Font("Arial", 0, 12));
        LoanDialogStatusLabel.setHorizontalAlignment(2);
        ReturnDialogSurface.setBackground(new java.awt.Color(240, 240, 240));
        ReturnLabel.setText("Aktīvie izsniegumi");
        ReturnLabel.setFont(new java.awt.Font("Arial", 1, 12));
        ReturnLabel.setHorizontalAlignment(2);
        jScrollPane1.setFont(new java.awt.Font("Arial", 0, 12));
        ReturnButton.setText("Atgriezt");
        ReturnButton.setFont(new java.awt.Font("Arial", 0, 12));
        ReturnButton.setBackground(new java.awt.Color(213, 229, 243));
        jButton19.setText("Atcelt");
        jButton19.setFont(new java.awt.Font("Arial", 0, 12));
        jButton19.setBackground(new java.awt.Color(225, 225, 225));
        ReturnDialogStatusLabel.setText("Statuss: grāmata tiks atzīmēta kā pieejama");
        ReturnDialogStatusLabel.setFont(new java.awt.Font("Arial", 0, 12));
        ReturnDialogStatusLabel.setHorizontalAlignment(2);
        ReservationDialogSurface.setBackground(new java.awt.Color(240, 240, 240));
        ReservationLabel.setText("Rezervāciju saraksts");
        ReservationLabel.setFont(new java.awt.Font("Arial", 1, 12));
        ReservationLabel.setHorizontalAlignment(2);
        ReservationScrollPane.setFont(new java.awt.Font("Arial", 0, 12));
        NewReservationButton.setText("Jauna rezervācija");
        NewReservationButton.setFont(new java.awt.Font("Arial", 0, 12));
        NewReservationButton.setBackground(new java.awt.Color(213, 229, 243));
        CancelReservationButton.setText("Atcelt rezervāciju");
        CancelReservationButton.setFont(new java.awt.Font("Arial", 0, 12));
        CancelReservationButton.setBackground(new java.awt.Color(225, 225, 225));
        CloseButton3.setText("Aizvērt");
        CloseButton3.setFont(new java.awt.Font("Arial", 0, 12));
        CloseButton3.setBackground(new java.awt.Color(225, 225, 225));
        RunButton.setText("Izpildīt");
        RunButton.setFont(new java.awt.Font("Arial", 0, 12));
        RunButton.setBackground(new java.awt.Color(225, 225, 225));
        ReservationDialogStatusLabel.setText("Kopā: 0 rezervācijas");
        ReservationDialogStatusLabel.setFont(new java.awt.Font("Arial", 0, 12));
        ReservationDialogStatusLabel.setHorizontalAlignment(2);
        NotificationDialogSurface.setBackground(new java.awt.Color(240, 240, 240));
        NotificationLabel.setText("Mani paziņojumi");
        NotificationLabel.setFont(new java.awt.Font("Arial", 1, 12));
        NotificationLabel.setHorizontalAlignment(2);
        NotificationScrollPane.setFont(new java.awt.Font("Arial", 0, 12));
        ReadButton.setText("Atzīmēt kā izlasītu");
        ReadButton.setFont(new java.awt.Font("Arial", 0, 12));
        ReadButton.setBackground(new java.awt.Color(213, 229, 243));
        CloseButton4.setText("Aizvērt");
        CloseButton4.setFont(new java.awt.Font("Arial", 0, 12));
        CloseButton4.setBackground(new java.awt.Color(225, 225, 225));
        NotificationDialogStatusLabel.setText("Kopā: 0 paziņojumi");
        NotificationDialogStatusLabel.setFont(new java.awt.Font("Arial", 0, 12));
        NotificationDialogStatusLabel.setHorizontalAlignment(2);
        MainFrameSurface.setBackground(new java.awt.Color(240, 240, 240));
        BookListButton.setText("Grāmatu saraksts");
        BookListButton.setFont(new java.awt.Font("Arial", 0, 10));
        BookListButton.setBackground(new java.awt.Color(225, 225, 225));
        BookAddButton.setText("Pievienot grāmatu");
        BookAddButton.setFont(new java.awt.Font("Arial", 0, 10));
        BookAddButton.setBackground(new java.awt.Color(225, 225, 225));
        UserListButton.setText("Lietotāju saraksts");
        UserListButton.setFont(new java.awt.Font("Arial", 0, 10));
        UserListButton.setBackground(new java.awt.Color(225, 225, 225));
        BookLoanButton.setText("Izsniegt grāmatu");
        BookLoanButton.setFont(new java.awt.Font("Arial", 0, 10));
        BookLoanButton.setBackground(new java.awt.Color(225, 225, 225));
        ReservationButton.setText("Rezervācijas");
        ReservationButton.setFont(new java.awt.Font("Arial", 0, 10));
        ReservationButton.setBackground(new java.awt.Color(225, 225, 225));
        BookReturnButton.setText("Atgriezt grāmatu");
        BookReturnButton.setFont(new java.awt.Font("Arial", 0, 10));
        BookReturnButton.setBackground(new java.awt.Color(225, 225, 225));
        NotificationsButton.setText("Paziņojumi");
        NotificationsButton.setFont(new java.awt.Font("Arial", 0, 10));
        NotificationsButton.setBackground(new java.awt.Color(225, 225, 225));
        CloseButton1.setText("Iziet");
        CloseButton1.setFont(new java.awt.Font("Arial", 0, 10));
        CloseButton1.setBackground(new java.awt.Color(225, 225, 225));
        MainFrameStatusLabel.setText("Pieteicies: — | Loma: —");
        MainFrameStatusLabel.setFont(new java.awt.Font("Arial", 0, 10));
        MainFrameStatusLabel.setHorizontalAlignment(2);
        BookTable.setFillsViewportHeight(true);
        UserTable.setFillsViewportHeight(true);
        ReturnTable.setFillsViewportHeight(true);
        ReservationTable.setFillsViewportHeight(true);
        NotificationTable.setFillsViewportHeight(true);

        // ============================================================
        // Custom UI (logu īpašības, klausītāji, spinneris, tooltips, maska)
        // ============================================================

        setTitle("LibInfo - Galvenā izvēlne");
        setDefaultCloseOperation(javax.swing.JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { CloseButtonActionPerformed(null); }
        });

        LoginDialog.setTitle("LibInfo - Pieteikšanās");
        LoginDialog.setModal(false);
        LoginDialog.setDefaultCloseOperation(javax.swing.JDialog.DO_NOTHING_ON_CLOSE);
        LoginDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { CloseButtonActionPerformed(null); }
        });

        RegisterDialog.setTitle("LibInfo - Reģistrācija");
        RegisterDialog.setModal(false);
        RegisterDialog.setDefaultCloseOperation(javax.swing.JDialog.DO_NOTHING_ON_CLOSE);
        RegisterDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { CancelButtonActionPerformed(null); }
        });

        BookListDialog.setTitle("LibInfo - Grāmatu saraksts");
        BookListDialog.setResizable(true);
        BookListDialog.setModal(false);
        BookListDialog.setDefaultCloseOperation(javax.swing.JDialog.DO_NOTHING_ON_CLOSE);
        BookListDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { returnToMain(BookListDialog); }
        });

        BookEditDialog.setTitle("LibInfo - Grāmata");
        BookEditDialog.setModal(false);
        BookEditDialog.setDefaultCloseOperation(javax.swing.JDialog.DO_NOTHING_ON_CLOSE);
        BookEditDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { returnToMain(BookEditDialog); }
        });

        UserListFrameDialog.setTitle("LibInfo - Lietotāji");
        UserListFrameDialog.setResizable(true);
        UserListFrameDialog.setModal(false);
        UserListFrameDialog.setDefaultCloseOperation(javax.swing.JDialog.DO_NOTHING_ON_CLOSE);
        UserListFrameDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { returnToMain(UserListFrameDialog); }
        });

        UserEditDialog.setTitle("LibInfo - Lietotājs");
        UserEditDialog.setModal(false);
        UserEditDialog.setDefaultCloseOperation(javax.swing.JDialog.DO_NOTHING_ON_CLOSE);
        UserEditDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { returnToMain(UserEditDialog); }
        });

        LoanDialog.setTitle("LibInfo - Izsniegt grāmatu");
        LoanDialog.setModal(false);
        LoanDialog.setDefaultCloseOperation(javax.swing.JDialog.DO_NOTHING_ON_CLOSE);
        LoanDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { returnToMain(LoanDialog); }
        });

        ReturnDialog.setTitle("LibInfo - Atgriezt grāmatu");
        ReturnDialog.setResizable(true);
        ReturnDialog.setModal(false);
        ReturnDialog.setDefaultCloseOperation(javax.swing.JDialog.DO_NOTHING_ON_CLOSE);
        ReturnDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { returnToMain(ReturnDialog); }
        });

        ReservationDialog.setTitle("LibInfo - Rezervācijas");
        ReservationDialog.setResizable(true);
        ReservationDialog.setModal(false);
        ReservationDialog.setDefaultCloseOperation(javax.swing.JDialog.DO_NOTHING_ON_CLOSE);
        ReservationDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { returnToMain(ReservationDialog); }
        });

        NotificationDialog.setTitle("LibInfo - Paziņojumi");
        NotificationDialog.setResizable(true);
        NotificationDialog.setModal(false);
        NotificationDialog.setDefaultCloseOperation(javax.swing.JDialog.DO_NOTHING_ON_CLOSE);
        NotificationDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { returnToMain(NotificationDialog); }
        });

        int currentYear = java.time.LocalDate.now().getYear();
        BookYearSpinner.setModel(new javax.swing.SpinnerNumberModel(currentYear, 1800, 2100, 1));
        javax.swing.JSpinner.NumberEditor yearEditor =
                new javax.swing.JSpinner.NumberEditor(BookYearSpinner, "0");
        BookYearSpinner.setEditor(yearEditor);
        BookYearSpinner.addChangeListener(e -> bookYearEdited = true);
        yearEditor.getTextField().getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { bookYearEdited = true; }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { bookYearEdited = true; }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { bookYearEdited = true; }
        });

        BookAvailabilityCheckBox.setToolTipText("Šī izvēle attiecas uz jaunu grāmatu. Esošo eksemplāru pieejamību nosaka izsniegumi.");

        LoanDateTextField.setEditable(false);
        LoanDateTextField.setToolTipText("Sistēma automātiski reģistrē izsniegšanas datumu.");
        try {
            javax.swing.text.MaskFormatter loanMask = new javax.swing.text.MaskFormatter("####-##-##");
            loanMask.setPlaceholderCharacter('_');
            LoanTimeFormattedTextField.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(loanMask));
        } catch (java.text.ParseException ex) {
            // Nevar notikt ar fiksētu masku
        }

        // Pogas klausītāji
        BookAddButton.addActionListener(e -> AddButtonActionPerformed(e));
        BookLoanButton.addActionListener(e -> BookLoanButtonActionPerformed(e));
        BookReturnButton.addActionListener(e -> BookReturnButtonActionPerformed(e));
        UserListButton.addActionListener(e -> UserListButtonActionPerformed(e));
        ReservationButton.addActionListener(e -> ReservationButtonActionPerformed(e));
        CloseButton1.addActionListener(e -> CloseButtonActionPerformed(e));

        DeleteButton.addActionListener(e -> DeleteButtonActionPerformed(e));

        LoanButton.addActionListener(e -> LoanButtonActionPerformed(e));
        ReturnButton4.addActionListener(e -> returnToMain(LoanDialog));

        jButton19.addActionListener(e -> returnToMain(ReturnDialog));

        CloseButton3.addActionListener(e -> returnToMain(ReservationDialog));

        ReadButton.addActionListener(e -> ReadButtonActionPerformed(e));
        CloseButton4.addActionListener(e -> returnToMain(NotificationDialog));

        ReservationTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateReservationActionButtons();
        });
        ReservationTable.setToolTipText("Lai atceltu vai izpildītu rezervāciju, vispirms atlasiet aktīvo ierakstu.");
        CancelReservationButton.setToolTipText("Atcelt atlasīto aktīvo rezervāciju.");
        RunButton.setToolTipText("Atzīmēt atlasīto aktīvo rezervāciju kā izpildītu.");

        // Izvēļņu klausītāji
        menuExitItem.addActionListener(e -> CloseButtonActionPerformed(e));
        menuBooksItem.addActionListener(e -> BookListButtonActionPerformed(e));
        menuAddBookItem.addActionListener(e -> AddButtonActionPerformed(e));
        menuSearchBookItem.addActionListener(e -> BookListButtonActionPerformed(e));
        menuUsersItem.addActionListener(e -> UserListButtonActionPerformed(e));
        menuAddUserItem.addActionListener(e -> AddUserButtonActionPerformed(e));
        menuIssueItem.addActionListener(e -> BookLoanButtonActionPerformed(e));
        menuReturnItem.addActionListener(e -> BookReturnButtonActionPerformed(e));
        menuOverdueItem.addActionListener(e -> {
            if (!isStaff()) { showError(new SecurityException("Kavētos izsniegumus drīkst skatīt tikai darbinieks.")); return; }
            showDialog(ReturnDialog);
            try {
                List<Izsniegums> loans = izsniegumsDAO.findLoans(true, currentUser);
                DefaultTableModel model = (DefaultTableModel) ReturnTable.getModel();
                model.setRowCount(0);
                for (Izsniegums loan : loans) {
                    model.addRow(new Object[]{loan.getIzsniegumaId(), loan.getLietotajs(),
                        loan.getEksemplars() == null || loan.getEksemplars().getGramata() == null ? "" : loan.getEksemplars().getGramata().getNosaukums(),
                        loan.getIzsniegts(), loan.getTermins()});
                }
                ReturnLabel.setText("Kavētie izsniegumi");
            } catch (Exception ex) { showError(ex); }
        });
        menuReservationsItem.addActionListener(e -> ReservationButtonActionPerformed(e));
        menuNotificationsItem.addActionListener(e -> NotificationsButtonActionPerformed(e));
        menuAboutItem.addActionListener(e -> showInfo("LibInfo - bibliotēkas informācijas sistēma. Java Swing + Apache Derby."));

        javax.swing.GroupLayout LoginDialogSurfaceLayout = new javax.swing.GroupLayout(LoginDialogSurface);
        LoginDialogSurface.setLayout(LoginDialogSurfaceLayout);
        LoginDialogSurfaceLayout.setHorizontalGroup(
        LoginDialogSurfaceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(LoginDialogSurfaceLayout.createSequentialGroup()
        .addGap(20, 20, 20)
        .addComponent(AppLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 380, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 20, Short.MAX_VALUE))
        .addGroup(LoginDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(UsernameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 148, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 262, Short.MAX_VALUE))
        .addGroup(LoginDialogSurfaceLayout.createSequentialGroup()
        .addGap(160, 160, 160)
        .addComponent(UsernameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 180, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 80, Short.MAX_VALUE))
        .addGroup(LoginDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(PasswordLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 148, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 262, Short.MAX_VALUE))
        .addGroup(LoginDialogSurfaceLayout.createSequentialGroup()
        .addGap(160, 160, 160)
        .addComponent(PasswordTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 180, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 80, Short.MAX_VALUE))
        .addGroup(LoginDialogSurfaceLayout.createSequentialGroup()
        .addGap(30, 30, 30)
        .addComponent(LoginButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 290, Short.MAX_VALUE))
        .addGroup(LoginDialogSurfaceLayout.createSequentialGroup()
        .addGap(140, 140, 140)
        .addComponent(RegisterButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 180, Short.MAX_VALUE))
        .addGroup(LoginDialogSurfaceLayout.createSequentialGroup()
        .addGap(250, 250, 250)
        .addComponent(CloseButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 70, Short.MAX_VALUE))
        );
        LoginDialogSurfaceLayout.setVerticalGroup(
        LoginDialogSurfaceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(LoginDialogSurfaceLayout.createSequentialGroup()
        .addGap(20, 20, 20)
        .addComponent(AppLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 244, Short.MAX_VALUE))
        .addGroup(LoginDialogSurfaceLayout.createSequentialGroup()
        .addGap(74, 74, 74)
        .addComponent(UsernameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 191, Short.MAX_VALUE))
        .addGroup(LoginDialogSurfaceLayout.createSequentialGroup()
        .addGap(74, 74, 74)
        .addComponent(UsernameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 191, Short.MAX_VALUE))
        .addGroup(LoginDialogSurfaceLayout.createSequentialGroup()
        .addGap(114, 114, 114)
        .addComponent(PasswordLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 151, Short.MAX_VALUE))
        .addGroup(LoginDialogSurfaceLayout.createSequentialGroup()
        .addGap(114, 114, 114)
        .addComponent(PasswordTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 151, Short.MAX_VALUE))
        .addGroup(LoginDialogSurfaceLayout.createSequentialGroup()
        .addGap(194, 194, 194)
        .addComponent(LoginButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 63, Short.MAX_VALUE))
        .addGroup(LoginDialogSurfaceLayout.createSequentialGroup()
        .addGap(194, 194, 194)
        .addComponent(RegisterButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 63, Short.MAX_VALUE))
        .addGroup(LoginDialogSurfaceLayout.createSequentialGroup()
        .addGap(194, 194, 194)
        .addComponent(CloseButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 63, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout RegisterDialogSurfaceLayout = new javax.swing.GroupLayout(RegisterDialogSurface);
        RegisterDialogSurface.setLayout(RegisterDialogSurfaceLayout);
        RegisterDialogSurfaceLayout.setHorizontalGroup(
        RegisterDialogSurfaceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(20, 20, 20)
        .addComponent(RegisterLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 380, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 20, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(NameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 138, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 272, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(150, 150, 150)
        .addComponent(NameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 70, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(SurnameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 138, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 272, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(150, 150, 150)
        .addComponent(SurnameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 70, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(UsernameLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 138, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 272, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(150, 150, 150)
        .addComponent(UsernameTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 70, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(PasswordLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 138, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 272, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(150, 150, 150)
        .addComponent(PasswordTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 70, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(PasswordProveLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 138, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 272, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(150, 150, 150)
        .addComponent(PasswordProveTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 70, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(EMailLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 138, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 272, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(150, 150, 150)
        .addComponent(EMailTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 70, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(50, 50, 50)
        .addComponent(RegisterButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 260, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(180, 180, 180)
        .addComponent(CancelButton, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 130, Short.MAX_VALUE))
        );
        RegisterDialogSurfaceLayout.setVerticalGroup(
        RegisterDialogSurfaceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(16, 16, 16)
        .addComponent(RegisterLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 348, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(62, 62, 62)
        .addComponent(NameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 303, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(62, 62, 62)
        .addComponent(NameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 303, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(97, 97, 97)
        .addComponent(SurnameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 268, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(97, 97, 97)
        .addComponent(SurnameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 268, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(132, 132, 132)
        .addComponent(UsernameLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 233, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(132, 132, 132)
        .addComponent(UsernameTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 233, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(167, 167, 167)
        .addComponent(PasswordLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 198, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(167, 167, 167)
        .addComponent(PasswordTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 198, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(202, 202, 202)
        .addComponent(PasswordProveLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 163, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(202, 202, 202)
        .addComponent(PasswordProveTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 163, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(237, 237, 237)
        .addComponent(EMailLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 128, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(237, 237, 237)
        .addComponent(EMailTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 128, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(307, 307, 307)
        .addComponent(RegisterButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 50, Short.MAX_VALUE))
        .addGroup(RegisterDialogSurfaceLayout.createSequentialGroup()
        .addGap(307, 307, 307)
        .addComponent(CancelButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 50, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout BookListDialogSurfaceLayout = new javax.swing.GroupLayout(BookListDialogSurface);
        BookListDialogSurface.setLayout(BookListDialogSurfaceLayout);
        BookListDialogSurfaceLayout.setHorizontalGroup(
        BookListDialogSurfaceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(BookListDialogSurfaceLayout.createSequentialGroup()
        .addGap(20, 20, 20)
        .addComponent(SearchTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 300, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 580, Short.MAX_VALUE))
        .addGroup(BookListDialogSurfaceLayout.createSequentialGroup()
        .addGap(330, 330, 330)
        .addComponent(SearchButton, javax.swing.GroupLayout.PREFERRED_SIZE, 95, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 475, Short.MAX_VALUE))
        .addGroup(BookListDialogSurfaceLayout.createSequentialGroup()
        .addGap(602, 602, 602)
        .addComponent(FilterLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 65, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 233, Short.MAX_VALUE))
        .addGroup(BookListDialogSurfaceLayout.createSequentialGroup()
        .addGap(677, 677, 677)
        .addComponent(FilterComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 23, Short.MAX_VALUE))
        .addGroup(BookListDialogSurfaceLayout.createSequentialGroup()
        .addGap(15, 15, 15)
        .addComponent(BookScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 870, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 15, Short.MAX_VALUE))
        .addGroup(BookListDialogSurfaceLayout.createSequentialGroup()
        .addGap(15, 15, 15)
        .addComponent(AddButton, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 775, Short.MAX_VALUE))
        .addGroup(BookListDialogSurfaceLayout.createSequentialGroup()
        .addGap(139, 139, 139)
        .addComponent(EditButton, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 651, Short.MAX_VALUE))
        .addGroup(BookListDialogSurfaceLayout.createSequentialGroup()
        .addGap(263, 263, 263)
        .addComponent(DeleteButton, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 527, Short.MAX_VALUE))
        .addGroup(BookListDialogSurfaceLayout.createSequentialGroup()
        .addGap(775, 775, 775)
        .addComponent(CloseButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 15, Short.MAX_VALUE))
        .addGroup(BookListDialogSurfaceLayout.createSequentialGroup()
        .addGap(12, 12, 12)
        .addComponent(BookListDialogStatusLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 876, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 12, Short.MAX_VALUE))
        );
        BookListDialogSurfaceLayout.setVerticalGroup(
        BookListDialogSurfaceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(BookListDialogSurfaceLayout.createSequentialGroup()
        .addGap(20, 20, 20)
        .addComponent(SearchTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 524, Short.MAX_VALUE))
        .addGroup(BookListDialogSurfaceLayout.createSequentialGroup()
        .addGap(20, 20, 20)
        .addComponent(SearchButton, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 524, Short.MAX_VALUE))
        .addGroup(BookListDialogSurfaceLayout.createSequentialGroup()
        .addGap(20, 20, 20)
        .addComponent(FilterLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 524, Short.MAX_VALUE))
        .addGroup(BookListDialogSurfaceLayout.createSequentialGroup()
        .addGap(20, 20, 20)
        .addComponent(FilterComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 524, Short.MAX_VALUE))
        .addGroup(BookListDialogSurfaceLayout.createSequentialGroup()
        .addGap(65, 65, 65)
        .addComponent(BookScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 380, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 127, Short.MAX_VALUE))
        .addGroup(BookListDialogSurfaceLayout.createSequentialGroup()
        .addGap(467, 467, 467)
        .addComponent(AddButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 70, Short.MAX_VALUE))
        .addGroup(BookListDialogSurfaceLayout.createSequentialGroup()
        .addGap(467, 467, 467)
        .addComponent(EditButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 70, Short.MAX_VALUE))
        .addGroup(BookListDialogSurfaceLayout.createSequentialGroup()
        .addGap(467, 467, 467)
        .addComponent(DeleteButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 70, Short.MAX_VALUE))
        .addGroup(BookListDialogSurfaceLayout.createSequentialGroup()
        .addGap(467, 467, 467)
        .addComponent(CloseButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 70, Short.MAX_VALUE))
        .addGroup(BookListDialogSurfaceLayout.createSequentialGroup()
        .addGap(549, 549, 549)
        .addComponent(BookListDialogStatusLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 3, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout BookEditDialogSurfaceLayout = new javax.swing.GroupLayout(BookEditDialogSurface);
        BookEditDialogSurface.setLayout(BookEditDialogSurfaceLayout);
        BookEditDialogSurfaceLayout.setHorizontalGroup(
        BookEditDialogSurfaceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(BookNameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 392, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(110, 110, 110)
        .addComponent(BookNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 360, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 30, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(BookAuthorLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 392, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(110, 110, 110)
        .addComponent(BookAuthorTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 360, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 30, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(BookISBNLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 392, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(110, 110, 110)
        .addComponent(BookISBNTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 190, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(BookCategoryLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 392, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(110, 110, 110)
        .addComponent(BookCategoryTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 360, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 30, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(BookYearLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 392, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(110, 110, 110)
        .addComponent(BookYearSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 290, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(320, 320, 320)
        .addComponent(ISBNSearchButton, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 30, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(BookDescriptionLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 392, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(110, 110, 110)
        .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 360, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 30, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(BookAvailabilityLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 392, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(110, 110, 110)
        .addComponent(BookAvailabilityCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 170, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(110, 110, 110)
        .addComponent(SaveBookButton, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 250, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(330, 330, 330)
        .addComponent(ReturnButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 30, Short.MAX_VALUE))
        );
        BookEditDialogSurfaceLayout.setVerticalGroup(
        BookEditDialogSurfaceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(20, 20, 20)
        .addComponent(BookNameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 525, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(20, 20, 20)
        .addComponent(BookNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 525, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(60, 60, 60)
        .addComponent(BookAuthorLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 485, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(60, 60, 60)
        .addComponent(BookAuthorTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 485, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(100, 100, 100)
        .addComponent(BookISBNLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 445, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(100, 100, 100)
        .addComponent(BookISBNTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 444, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(140, 140, 140)
        .addComponent(BookCategoryLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 405, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(140, 140, 140)
        .addComponent(BookCategoryTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 405, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(180, 180, 180)
        .addComponent(BookYearLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 365, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(180, 180, 180)
        .addComponent(BookYearSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 364, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(100, 100, 100)
        .addComponent(ISBNSearchButton, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 444, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(225, 225, 225)
        .addComponent(BookDescriptionLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 320, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(225, 225, 225)
        .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 187, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(405, 405, 405)
        .addComponent(BookAvailabilityLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 140, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(405, 405, 405)
        .addComponent(BookAvailabilityCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 140, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(490, 490, 490)
        .addComponent(SaveBookButton, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 46, Short.MAX_VALUE))
        .addGroup(BookEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(490, 490, 490)
        .addComponent(ReturnButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 46, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout UserListFrameDialogSurfaceLayout = new javax.swing.GroupLayout(UserListFrameDialogSurface);
        UserListFrameDialogSurface.setLayout(UserListFrameDialogSurfaceLayout);
        UserListFrameDialogSurfaceLayout.setHorizontalGroup(
        UserListFrameDialogSurfaceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(UserListFrameDialogSurfaceLayout.createSequentialGroup()
        .addGap(85, 85, 85)
        .addComponent(UserSearchTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 305, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 510, Short.MAX_VALUE))
        .addGroup(UserListFrameDialogSurfaceLayout.createSequentialGroup()
        .addGap(405, 405, 405)
        .addComponent(UserSearchButton, javax.swing.GroupLayout.PREFERRED_SIZE, 95, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 400, Short.MAX_VALUE))
        .addGroup(UserListFrameDialogSurfaceLayout.createSequentialGroup()
        .addGap(15, 15, 15)
        .addComponent(UserSearchLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 825, Short.MAX_VALUE))
        .addGroup(UserListFrameDialogSurfaceLayout.createSequentialGroup()
        .addGap(15, 15, 15)
        .addComponent(UserScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 870, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 15, Short.MAX_VALUE))
        .addGroup(UserListFrameDialogSurfaceLayout.createSequentialGroup()
        .addGap(15, 15, 15)
        .addComponent(AddUserButton, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 775, Short.MAX_VALUE))
        .addGroup(UserListFrameDialogSurfaceLayout.createSequentialGroup()
        .addGap(139, 139, 139)
        .addComponent(EditUserButton, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 651, Short.MAX_VALUE))
        .addGroup(UserListFrameDialogSurfaceLayout.createSequentialGroup()
        .addGap(263, 263, 263)
        .addComponent(DeleteUserButton, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 527, Short.MAX_VALUE))
        .addGroup(UserListFrameDialogSurfaceLayout.createSequentialGroup()
        .addGap(387, 387, 387)
        .addComponent(EditUserRoleButton, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 403, Short.MAX_VALUE))
        .addGroup(UserListFrameDialogSurfaceLayout.createSequentialGroup()
        .addGap(775, 775, 775)
        .addComponent(ReturnButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 15, Short.MAX_VALUE))
        .addGroup(UserListFrameDialogSurfaceLayout.createSequentialGroup()
        .addGap(12, 12, 12)
        .addComponent(UserListFrameDialogStatusLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 876, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 12, Short.MAX_VALUE))
        );
        UserListFrameDialogSurfaceLayout.setVerticalGroup(
        UserListFrameDialogSurfaceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(UserListFrameDialogSurfaceLayout.createSequentialGroup()
        .addGap(18, 18, 18)
        .addComponent(UserSearchTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 526, Short.MAX_VALUE))
        .addGroup(UserListFrameDialogSurfaceLayout.createSequentialGroup()
        .addGap(18, 18, 18)
        .addComponent(UserSearchButton, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 526, Short.MAX_VALUE))
        .addGroup(UserListFrameDialogSurfaceLayout.createSequentialGroup()
        .addGap(18, 18, 18)
        .addComponent(UserSearchLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 526, Short.MAX_VALUE))
        .addGroup(UserListFrameDialogSurfaceLayout.createSequentialGroup()
        .addGap(65, 65, 65)
        .addComponent(UserScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 380, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 127, Short.MAX_VALUE))
        .addGroup(UserListFrameDialogSurfaceLayout.createSequentialGroup()
        .addGap(464, 464, 464)
        .addComponent(AddUserButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 73, Short.MAX_VALUE))
        .addGroup(UserListFrameDialogSurfaceLayout.createSequentialGroup()
        .addGap(464, 464, 464)
        .addComponent(EditUserButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 73, Short.MAX_VALUE))
        .addGroup(UserListFrameDialogSurfaceLayout.createSequentialGroup()
        .addGap(464, 464, 464)
        .addComponent(DeleteUserButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 73, Short.MAX_VALUE))
        .addGroup(UserListFrameDialogSurfaceLayout.createSequentialGroup()
        .addGap(464, 464, 464)
        .addComponent(EditUserRoleButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 73, Short.MAX_VALUE))
        .addGroup(UserListFrameDialogSurfaceLayout.createSequentialGroup()
        .addGap(464, 464, 464)
        .addComponent(ReturnButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 73, Short.MAX_VALUE))
        .addGroup(UserListFrameDialogSurfaceLayout.createSequentialGroup()
        .addGap(549, 549, 549)
        .addComponent(UserListFrameDialogStatusLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 3, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout UserEditDialogSurfaceLayout = new javax.swing.GroupLayout(UserEditDialogSurface);
        UserEditDialogSurface.setLayout(UserEditDialogSurfaceLayout);
        UserEditDialogSurfaceLayout.setHorizontalGroup(
        UserEditDialogSurfaceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(UserNameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 103, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 337, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(125, 125, 125)
        .addComponent(UserNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 280, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 45, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(UserSurnameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 103, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 337, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(125, 125, 125)
        .addComponent(UserSurnameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 280, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 45, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(UsernickLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 103, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 337, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(125, 125, 125)
        .addComponent(UsernickTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 280, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 45, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(UserPasswordLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 103, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 337, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(125, 125, 125)
        .addComponent(UserPasswordTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 280, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 45, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(UserEMailLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 103, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 337, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(125, 125, 125)
        .addComponent(UserEMailTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 280, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 45, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(UserRoleLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 103, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 337, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(125, 125, 125)
        .addComponent(UserRoleComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 280, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 45, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(80, 80, 80)
        .addComponent(SaveUserButton, javax.swing.GroupLayout.PREFERRED_SIZE, 130, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 240, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(240, 240, 240)
        .addComponent(ReturnButton3, javax.swing.GroupLayout.PREFERRED_SIZE, 130, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 80, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(12, 12, 12)
        .addComponent(UserEditDialogStatusLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 426, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 12, Short.MAX_VALUE))
        );
        UserEditDialogSurfaceLayout.setVerticalGroup(
        UserEditDialogSurfaceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(25, 25, 25)
        .addComponent(UserNameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 370, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(25, 25, 25)
        .addComponent(UserNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 370, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(65, 65, 65)
        .addComponent(UserSurnameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 330, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(65, 65, 65)
        .addComponent(UserSurnameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 330, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(105, 105, 105)
        .addComponent(UsernickLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 290, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(105, 105, 105)
        .addComponent(UsernickTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 290, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(145, 145, 145)
        .addComponent(UserPasswordLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 250, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(145, 145, 145)
        .addComponent(UserPasswordTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 250, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(185, 185, 185)
        .addComponent(UserEMailLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 210, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(185, 185, 185)
        .addComponent(UserEMailTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 210, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(225, 225, 225)
        .addComponent(UserRoleLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 170, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(225, 225, 225)
        .addComponent(UserRoleComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 170, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(335, 335, 335)
        .addComponent(SaveUserButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 52, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(335, 335, 335)
        .addComponent(ReturnButton3, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 52, Short.MAX_VALUE))
        .addGroup(UserEditDialogSurfaceLayout.createSequentialGroup()
        .addGap(399, 399, 399)
        .addComponent(UserEditDialogStatusLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 3, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout LoanDialogSurfaceLayout = new javax.swing.GroupLayout(LoanDialogSurface);
        LoanDialogSurface.setLayout(LoanDialogSurfaceLayout);
        LoanDialogSurfaceLayout.setHorizontalGroup(
        LoanDialogSurfaceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(LoanDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(ReaderLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 115, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 375, Short.MAX_VALUE))
        .addGroup(LoanDialogSurfaceLayout.createSequentialGroup()
        .addGap(130, 130, 130)
        .addComponent(ReaderComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 330, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 40, Short.MAX_VALUE))
        .addGroup(LoanDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(BookLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 115, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 375, Short.MAX_VALUE))
        .addGroup(LoanDialogSurfaceLayout.createSequentialGroup()
        .addGap(130, 130, 130)
        .addComponent(BookComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 330, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 40, Short.MAX_VALUE))
        .addGroup(LoanDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(LoanDateLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 115, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 375, Short.MAX_VALUE))
        .addGroup(LoanDialogSurfaceLayout.createSequentialGroup()
        .addGap(130, 130, 130)
        .addComponent(LoanDateTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 165, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 205, Short.MAX_VALUE))
        .addGroup(LoanDialogSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(LoanTimeLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 115, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 375, Short.MAX_VALUE))
        .addGroup(LoanDialogSurfaceLayout.createSequentialGroup()
        .addGap(130, 130, 130)
        .addComponent(LoanTimeFormattedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 165, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 205, Short.MAX_VALUE))
        .addGroup(LoanDialogSurfaceLayout.createSequentialGroup()
        .addGap(80, 80, 80)
        .addComponent(LoanButton, javax.swing.GroupLayout.PREFERRED_SIZE, 135, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 285, Short.MAX_VALUE))
        .addGroup(LoanDialogSurfaceLayout.createSequentialGroup()
        .addGap(250, 250, 250)
        .addComponent(ReturnButton4, javax.swing.GroupLayout.PREFERRED_SIZE, 135, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 115, Short.MAX_VALUE))
        .addGroup(LoanDialogSurfaceLayout.createSequentialGroup()
        .addGap(12, 12, 12)
        .addComponent(LoanDialogStatusLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 476, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 12, Short.MAX_VALUE))
        );
        LoanDialogSurfaceLayout.setVerticalGroup(
        LoanDialogSurfaceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(LoanDialogSurfaceLayout.createSequentialGroup()
        .addGap(25, 25, 25)
        .addComponent(ReaderLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 270, Short.MAX_VALUE))
        .addGroup(LoanDialogSurfaceLayout.createSequentialGroup()
        .addGap(25, 25, 25)
        .addComponent(ReaderComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 270, Short.MAX_VALUE))
        .addGroup(LoanDialogSurfaceLayout.createSequentialGroup()
        .addGap(65, 65, 65)
        .addComponent(BookLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 230, Short.MAX_VALUE))
        .addGroup(LoanDialogSurfaceLayout.createSequentialGroup()
        .addGap(65, 65, 65)
        .addComponent(BookComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 230, Short.MAX_VALUE))
        .addGroup(LoanDialogSurfaceLayout.createSequentialGroup()
        .addGap(105, 105, 105)
        .addComponent(LoanDateLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 190, Short.MAX_VALUE))
        .addGroup(LoanDialogSurfaceLayout.createSequentialGroup()
        .addGap(105, 105, 105)
        .addComponent(LoanDateTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 189, Short.MAX_VALUE))
        .addGroup(LoanDialogSurfaceLayout.createSequentialGroup()
        .addGap(145, 145, 145)
        .addComponent(LoanTimeLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 150, Short.MAX_VALUE))
        .addGroup(LoanDialogSurfaceLayout.createSequentialGroup()
        .addGap(145, 145, 145)
        .addComponent(LoanTimeFormattedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 149, Short.MAX_VALUE))
        .addGroup(LoanDialogSurfaceLayout.createSequentialGroup()
        .addGap(235, 235, 235)
        .addComponent(LoanButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 52, Short.MAX_VALUE))
        .addGroup(LoanDialogSurfaceLayout.createSequentialGroup()
        .addGap(235, 235, 235)
        .addComponent(ReturnButton4, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 52, Short.MAX_VALUE))
        .addGroup(LoanDialogSurfaceLayout.createSequentialGroup()
        .addGap(299, 299, 299)
        .addComponent(LoanDialogStatusLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 3, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout ReturnDialogSurfaceLayout = new javax.swing.GroupLayout(ReturnDialogSurface);
        ReturnDialogSurface.setLayout(ReturnDialogSurfaceLayout);
        ReturnDialogSurfaceLayout.setHorizontalGroup(
        ReturnDialogSurfaceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(ReturnDialogSurfaceLayout.createSequentialGroup()
        .addGap(15, 15, 15)
        .addComponent(ReturnLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 570, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 15, Short.MAX_VALUE))
        .addGroup(ReturnDialogSurfaceLayout.createSequentialGroup()
        .addGap(15, 15, 15)
        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 570, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 15, Short.MAX_VALUE))
        .addGroup(ReturnDialogSurfaceLayout.createSequentialGroup()
        .addGap(140, 140, 140)
        .addComponent(ReturnButton, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 320, Short.MAX_VALUE))
        .addGroup(ReturnDialogSurfaceLayout.createSequentialGroup()
        .addGap(315, 315, 315)
        .addComponent(jButton19, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 145, Short.MAX_VALUE))
        .addGroup(ReturnDialogSurfaceLayout.createSequentialGroup()
        .addGap(12, 12, 12)
        .addComponent(ReturnDialogStatusLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 576, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 12, Short.MAX_VALUE))
        );
        ReturnDialogSurfaceLayout.setVerticalGroup(
        ReturnDialogSurfaceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(ReturnDialogSurfaceLayout.createSequentialGroup()
        .addGap(12, 12, 12)
        .addComponent(ReturnLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 338, Short.MAX_VALUE))
        .addGroup(ReturnDialogSurfaceLayout.createSequentialGroup()
        .addGap(40, 40, 40)
        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 232, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 100, Short.MAX_VALUE))
        .addGroup(ReturnDialogSurfaceLayout.createSequentialGroup()
        .addGap(292, 292, 292)
        .addComponent(ReturnButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 45, Short.MAX_VALUE))
        .addGroup(ReturnDialogSurfaceLayout.createSequentialGroup()
        .addGap(292, 292, 292)
        .addComponent(jButton19, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 45, Short.MAX_VALUE))
        .addGroup(ReturnDialogSurfaceLayout.createSequentialGroup()
        .addGap(349, 349, 349)
        .addComponent(ReturnDialogStatusLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 3, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout ReservationDialogSurfaceLayout = new javax.swing.GroupLayout(ReservationDialogSurface);
        ReservationDialogSurface.setLayout(ReservationDialogSurfaceLayout);
        ReservationDialogSurfaceLayout.setHorizontalGroup(
        ReservationDialogSurfaceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(ReservationDialogSurfaceLayout.createSequentialGroup()
        .addGap(15, 15, 15)
        .addComponent(ReservationLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 770, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 15, Short.MAX_VALUE))
        .addGroup(ReservationDialogSurfaceLayout.createSequentialGroup()
        .addGap(15, 15, 15)
        .addComponent(ReservationScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 770, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 15, Short.MAX_VALUE))
        .addGroup(ReservationDialogSurfaceLayout.createSequentialGroup()
        .addGap(15, 15, 15)
        .addComponent(NewReservationButton, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 635, Short.MAX_VALUE))
        .addGroup(ReservationDialogSurfaceLayout.createSequentialGroup()
        .addGap(179, 179, 179)
        .addComponent(CancelReservationButton, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 471, Short.MAX_VALUE))
        .addGroup(ReservationDialogSurfaceLayout.createSequentialGroup()
        .addGap(343, 343, 343)
        .addComponent(RunButton, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 307, Short.MAX_VALUE))
        .addGroup(ReservationDialogSurfaceLayout.createSequentialGroup()
        .addGap(660, 660, 660)
        .addComponent(CloseButton3, javax.swing.GroupLayout.PREFERRED_SIZE, 125, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 15, Short.MAX_VALUE))
        .addGroup(ReservationDialogSurfaceLayout.createSequentialGroup()
        .addGap(12, 12, 12)
        .addComponent(ReservationDialogStatusLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 776, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 12, Short.MAX_VALUE))
        );
        ReservationDialogSurfaceLayout.setVerticalGroup(
        ReservationDialogSurfaceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(ReservationDialogSurfaceLayout.createSequentialGroup()
        .addGap(12, 12, 12)
        .addComponent(ReservationLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 438, Short.MAX_VALUE))
        .addGroup(ReservationDialogSurfaceLayout.createSequentialGroup()
        .addGap(40, 40, 40)
        .addComponent(ReservationScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 332, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 100, Short.MAX_VALUE))
        .addGroup(ReservationDialogSurfaceLayout.createSequentialGroup()
        .addGap(392, 392, 392)
        .addComponent(NewReservationButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 45, Short.MAX_VALUE))
        .addGroup(ReservationDialogSurfaceLayout.createSequentialGroup()
        .addGap(392, 392, 392)
        .addComponent(CancelReservationButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 45, Short.MAX_VALUE))
        .addGroup(ReservationDialogSurfaceLayout.createSequentialGroup()
        .addGap(392, 392, 392)
        .addComponent(RunButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 45, Short.MAX_VALUE))
        .addGroup(ReservationDialogSurfaceLayout.createSequentialGroup()
        .addGap(392, 392, 392)
        .addComponent(CloseButton3, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 45, Short.MAX_VALUE))
        .addGroup(ReservationDialogSurfaceLayout.createSequentialGroup()
        .addGap(449, 449, 449)
        .addComponent(ReservationDialogStatusLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 3, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout NotificationDialogSurfaceLayout = new javax.swing.GroupLayout(NotificationDialogSurface);
        NotificationDialogSurface.setLayout(NotificationDialogSurfaceLayout);
        NotificationDialogSurfaceLayout.setHorizontalGroup(
        NotificationDialogSurfaceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(NotificationDialogSurfaceLayout.createSequentialGroup()
        .addGap(15, 15, 15)
        .addComponent(NotificationLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 670, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 15, Short.MAX_VALUE))
        .addGroup(NotificationDialogSurfaceLayout.createSequentialGroup()
        .addGap(15, 15, 15)
        .addComponent(NotificationScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 670, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 15, Short.MAX_VALUE))
        .addGroup(NotificationDialogSurfaceLayout.createSequentialGroup()
        .addGap(15, 15, 15)
        .addComponent(ReadButton, javax.swing.GroupLayout.PREFERRED_SIZE, 180, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 505, Short.MAX_VALUE))
        .addGroup(NotificationDialogSurfaceLayout.createSequentialGroup()
        .addGap(560, 560, 560)
        .addComponent(CloseButton4, javax.swing.GroupLayout.PREFERRED_SIZE, 125, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 15, Short.MAX_VALUE))
        .addGroup(NotificationDialogSurfaceLayout.createSequentialGroup()
        .addGap(12, 12, 12)
        .addComponent(NotificationDialogStatusLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 676, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 12, Short.MAX_VALUE))
        );
        NotificationDialogSurfaceLayout.setVerticalGroup(
        NotificationDialogSurfaceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(NotificationDialogSurfaceLayout.createSequentialGroup()
        .addGap(12, 12, 12)
        .addComponent(NotificationLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 388, Short.MAX_VALUE))
        .addGroup(NotificationDialogSurfaceLayout.createSequentialGroup()
        .addGap(40, 40, 40)
        .addComponent(NotificationScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 282, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 100, Short.MAX_VALUE))
        .addGroup(NotificationDialogSurfaceLayout.createSequentialGroup()
        .addGap(342, 342, 342)
        .addComponent(ReadButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 45, Short.MAX_VALUE))
        .addGroup(NotificationDialogSurfaceLayout.createSequentialGroup()
        .addGap(342, 342, 342)
        .addComponent(CloseButton4, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 45, Short.MAX_VALUE))
        .addGroup(NotificationDialogSurfaceLayout.createSequentialGroup()
        .addGap(399, 399, 399)
        .addComponent(NotificationDialogStatusLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 3, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout MainFrameSurfaceLayout = new javax.swing.GroupLayout(MainFrameSurface);
        MainFrameSurface.setLayout(MainFrameSurfaceLayout);
        MainFrameSurfaceLayout.setHorizontalGroup(
        MainFrameSurfaceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(MainFrameSurfaceLayout.createSequentialGroup()
        .addGap(10, 10, 10)
        .addComponent(BookListButton, javax.swing.GroupLayout.PREFERRED_SIZE, 105, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 785, Short.MAX_VALUE))
        .addGroup(MainFrameSurfaceLayout.createSequentialGroup()
        .addGap(120, 120, 120)
        .addComponent(BookAddButton, javax.swing.GroupLayout.PREFERRED_SIZE, 105, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 675, Short.MAX_VALUE))
        .addGroup(MainFrameSurfaceLayout.createSequentialGroup()
        .addGap(230, 230, 230)
        .addComponent(UserListButton, javax.swing.GroupLayout.PREFERRED_SIZE, 105, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 565, Short.MAX_VALUE))
        .addGroup(MainFrameSurfaceLayout.createSequentialGroup()
        .addGap(340, 340, 340)
        .addComponent(BookLoanButton, javax.swing.GroupLayout.PREFERRED_SIZE, 105, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 455, Short.MAX_VALUE))
        .addGroup(MainFrameSurfaceLayout.createSequentialGroup()
        .addGap(450, 450, 450)
        .addComponent(BookReturnButton, javax.swing.GroupLayout.PREFERRED_SIZE, 105, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 345, Short.MAX_VALUE))
        .addGroup(MainFrameSurfaceLayout.createSequentialGroup()
        .addGap(560, 560, 560)
        .addComponent(ReservationButton, javax.swing.GroupLayout.PREFERRED_SIZE, 105, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 235, Short.MAX_VALUE))
        .addGroup(MainFrameSurfaceLayout.createSequentialGroup()
        .addGap(670, 670, 670)
        .addComponent(NotificationsButton, javax.swing.GroupLayout.PREFERRED_SIZE, 105, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 125, Short.MAX_VALUE))
        .addGroup(MainFrameSurfaceLayout.createSequentialGroup()
        .addGap(780, 780, 780)
        .addComponent(CloseButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 105, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 15, Short.MAX_VALUE))
        .addGroup(MainFrameSurfaceLayout.createSequentialGroup()
        .addGap(12, 12, 12)
        .addComponent(MainFrameStatusLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 876, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 12, Short.MAX_VALUE))
        );
        MainFrameSurfaceLayout.setVerticalGroup(
        MainFrameSurfaceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(MainFrameSurfaceLayout.createSequentialGroup()
        .addGap(8, 8, 8)
        .addComponent(BookListButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 497, Short.MAX_VALUE))
        .addGroup(MainFrameSurfaceLayout.createSequentialGroup()
        .addGap(8, 8, 8)
        .addComponent(BookAddButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 497, Short.MAX_VALUE))
        .addGroup(MainFrameSurfaceLayout.createSequentialGroup()
        .addGap(8, 8, 8)
        .addComponent(UserListButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 497, Short.MAX_VALUE))
        .addGroup(MainFrameSurfaceLayout.createSequentialGroup()
        .addGap(8, 8, 8)
        .addComponent(BookLoanButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 497, Short.MAX_VALUE))
        .addGroup(MainFrameSurfaceLayout.createSequentialGroup()
        .addGap(8, 8, 8)
        .addComponent(BookReturnButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 497, Short.MAX_VALUE))
        .addGroup(MainFrameSurfaceLayout.createSequentialGroup()
        .addGap(8, 8, 8)
        .addComponent(ReservationButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 497, Short.MAX_VALUE))
        .addGroup(MainFrameSurfaceLayout.createSequentialGroup()
        .addGap(8, 8, 8)
        .addComponent(NotificationsButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 497, Short.MAX_VALUE))
        .addGroup(MainFrameSurfaceLayout.createSequentialGroup()
        .addGap(8, 8, 8)
        .addComponent(CloseButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 497, Short.MAX_VALUE))
        .addGroup(MainFrameSurfaceLayout.createSequentialGroup()
        .addGap(517, 517, 517)
        .addComponent(MainFrameStatusLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 3, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout LoginDialogLayout = new javax.swing.GroupLayout(LoginDialog.getContentPane());
        LoginDialog.getContentPane().setLayout(LoginDialogLayout);
        LoginDialogLayout.setHorizontalGroup(
        LoginDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(LoginDialogLayout.createSequentialGroup()
        .addGap(0, 0, 0)
        .addComponent(LoginDialogSurface, javax.swing.GroupLayout.PREFERRED_SIZE, 420, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, Short.MAX_VALUE))
        );
        LoginDialogLayout.setVerticalGroup(
        LoginDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(LoginDialogLayout.createSequentialGroup()
        .addGap(0, 0, 0)
        .addComponent(LoginDialogSurface, javax.swing.GroupLayout.PREFERRED_SIZE, 292, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout RegisterDialogLayout = new javax.swing.GroupLayout(RegisterDialog.getContentPane());
        RegisterDialog.getContentPane().setLayout(RegisterDialogLayout);
        RegisterDialogLayout.setHorizontalGroup(
        RegisterDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(RegisterDialogLayout.createSequentialGroup()
        .addGap(0, 0, 0)
        .addComponent(RegisterDialogSurface, javax.swing.GroupLayout.PREFERRED_SIZE, 420, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, Short.MAX_VALUE))
        );
        RegisterDialogLayout.setVerticalGroup(
        RegisterDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(RegisterDialogLayout.createSequentialGroup()
        .addGap(0, 0, 0)
        .addComponent(RegisterDialogSurface, javax.swing.GroupLayout.PREFERRED_SIZE, 392, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout BookListDialogLayout = new javax.swing.GroupLayout(BookListDialog.getContentPane());
        BookListDialog.getContentPane().setLayout(BookListDialogLayout);
        BookListDialogLayout.setHorizontalGroup(
        BookListDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(BookListDialogLayout.createSequentialGroup()
        .addGap(0, 0, 0)
        .addComponent(BookListDialogSurface, javax.swing.GroupLayout.PREFERRED_SIZE, 900, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, Short.MAX_VALUE))
        );
        BookListDialogLayout.setVerticalGroup(
        BookListDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(BookListDialogLayout.createSequentialGroup()
        .addGap(0, 0, 0)
        .addComponent(BookListDialogSurface, javax.swing.GroupLayout.PREFERRED_SIZE, 572, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout BookEditDialogLayout = new javax.swing.GroupLayout(BookEditDialog.getContentPane());
        BookEditDialog.getContentPane().setLayout(BookEditDialogLayout);
        BookEditDialogLayout.setHorizontalGroup(
        BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(BookEditDialogLayout.createSequentialGroup()
        .addGap(0, 0, 0)
        .addComponent(BookEditDialogSurface, javax.swing.GroupLayout.PREFERRED_SIZE, 500, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, Short.MAX_VALUE))
        );
        BookEditDialogLayout.setVerticalGroup(
        BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(BookEditDialogLayout.createSequentialGroup()
        .addGap(0, 0, 0)
        .addComponent(BookEditDialogSurface, javax.swing.GroupLayout.PREFERRED_SIZE, 572, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout UserListFrameDialogLayout = new javax.swing.GroupLayout(UserListFrameDialog.getContentPane());
        UserListFrameDialog.getContentPane().setLayout(UserListFrameDialogLayout);
        UserListFrameDialogLayout.setHorizontalGroup(
        UserListFrameDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(UserListFrameDialogLayout.createSequentialGroup()
        .addGap(0, 0, 0)
        .addComponent(UserListFrameDialogSurface, javax.swing.GroupLayout.PREFERRED_SIZE, 900, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, Short.MAX_VALUE))
        );
        UserListFrameDialogLayout.setVerticalGroup(
        UserListFrameDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(UserListFrameDialogLayout.createSequentialGroup()
        .addGap(0, 0, 0)
        .addComponent(UserListFrameDialogSurface, javax.swing.GroupLayout.PREFERRED_SIZE, 572, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout UserEditDialogLayout = new javax.swing.GroupLayout(UserEditDialog.getContentPane());
        UserEditDialog.getContentPane().setLayout(UserEditDialogLayout);
        UserEditDialogLayout.setHorizontalGroup(
        UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(UserEditDialogLayout.createSequentialGroup()
        .addGap(0, 0, 0)
        .addComponent(UserEditDialogSurface, javax.swing.GroupLayout.PREFERRED_SIZE, 450, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, Short.MAX_VALUE))
        );
        UserEditDialogLayout.setVerticalGroup(
        UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(UserEditDialogLayout.createSequentialGroup()
        .addGap(0, 0, 0)
        .addComponent(UserEditDialogSurface, javax.swing.GroupLayout.PREFERRED_SIZE, 422, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout LoanDialogLayout = new javax.swing.GroupLayout(LoanDialog.getContentPane());
        LoanDialog.getContentPane().setLayout(LoanDialogLayout);
        LoanDialogLayout.setHorizontalGroup(
        LoanDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(LoanDialogLayout.createSequentialGroup()
        .addGap(0, 0, 0)
        .addComponent(LoanDialogSurface, javax.swing.GroupLayout.PREFERRED_SIZE, 500, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, Short.MAX_VALUE))
        );
        LoanDialogLayout.setVerticalGroup(
        LoanDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(LoanDialogLayout.createSequentialGroup()
        .addGap(0, 0, 0)
        .addComponent(LoanDialogSurface, javax.swing.GroupLayout.PREFERRED_SIZE, 322, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout ReturnDialogLayout = new javax.swing.GroupLayout(ReturnDialog.getContentPane());
        ReturnDialog.getContentPane().setLayout(ReturnDialogLayout);
        ReturnDialogLayout.setHorizontalGroup(
        ReturnDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(ReturnDialogLayout.createSequentialGroup()
        .addGap(0, 0, 0)
        .addComponent(ReturnDialogSurface, javax.swing.GroupLayout.PREFERRED_SIZE, 600, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, Short.MAX_VALUE))
        );
        ReturnDialogLayout.setVerticalGroup(
        ReturnDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(ReturnDialogLayout.createSequentialGroup()
        .addGap(0, 0, 0)
        .addComponent(ReturnDialogSurface, javax.swing.GroupLayout.PREFERRED_SIZE, 372, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout ReservationDialogLayout = new javax.swing.GroupLayout(ReservationDialog.getContentPane());
        ReservationDialog.getContentPane().setLayout(ReservationDialogLayout);
        ReservationDialogLayout.setHorizontalGroup(
        ReservationDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(ReservationDialogLayout.createSequentialGroup()
        .addGap(0, 0, 0)
        .addComponent(ReservationDialogSurface, javax.swing.GroupLayout.PREFERRED_SIZE, 800, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, Short.MAX_VALUE))
        );
        ReservationDialogLayout.setVerticalGroup(
        ReservationDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(ReservationDialogLayout.createSequentialGroup()
        .addGap(0, 0, 0)
        .addComponent(ReservationDialogSurface, javax.swing.GroupLayout.PREFERRED_SIZE, 472, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout NotificationDialogLayout = new javax.swing.GroupLayout(NotificationDialog.getContentPane());
        NotificationDialog.getContentPane().setLayout(NotificationDialogLayout);
        NotificationDialogLayout.setHorizontalGroup(
        NotificationDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(NotificationDialogLayout.createSequentialGroup()
        .addGap(0, 0, 0)
        .addComponent(NotificationDialogSurface, javax.swing.GroupLayout.PREFERRED_SIZE, 700, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, Short.MAX_VALUE))
        );
        NotificationDialogLayout.setVerticalGroup(
        NotificationDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(NotificationDialogLayout.createSequentialGroup()
        .addGap(0, 0, 0)
        .addComponent(NotificationDialogSurface, javax.swing.GroupLayout.PREFERRED_SIZE, 422, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
        layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(layout.createSequentialGroup()
        .addGap(0, 0, 0)
        .addComponent(MainFrameSurface, javax.swing.GroupLayout.PREFERRED_SIZE, 900, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
        layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(layout.createSequentialGroup()
        .addGap(0, 0, 0)
        .addComponent(MainFrameSurface, javax.swing.GroupLayout.PREFERRED_SIZE, 540, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, Short.MAX_VALUE))
        );

        pack();
        setVisible(false);
    }// </editor-fold>//GEN-END:initComponents

    /* ==================== Pogu apstrādātāji (ar inlinētu loģiku) ==================== */

    private void LoginButtonActionPerformed(java.awt.event.ActionEvent evt) {
        String username = UsernameTextField.getText().trim();
        String password = new String(PasswordTextField.getPassword());
        try {
            currentUser = lietotajsDAO.authenticate(username, password);
            readNotificationKeys.clear();
            readNotificationKeys.addAll(lietotajsDAO.findReadNotificationKeys(currentUser));
            UsernameTextField.setText("");
            PasswordTextField.setText("");
            showMain();
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void UsernameTextFieldActionPerformed(java.awt.event.ActionEvent evt) {
        LoginButtonActionPerformed(evt);
    }

    private void RegisterButtonActionPerformed(java.awt.event.ActionEvent evt) {
        // clearRegisterFields
        SurnameTextField.setText(""); NameTextField.setText("");
        PasswordTextField1.setText(""); UsernameTextField1.setText("");
        EMailTextField.setText(""); PasswordProveTextField.setText("");
        LoginDialog.setVisible(false);
        showDialog(RegisterDialog);
        SurnameTextField.requestFocusInWindow();
    }

    private void RegisterButton1ActionPerformed(java.awt.event.ActionEvent evt) {
        String username = UsernameTextField1.getText().trim();
        try {
            lietotajsDAO.registerUser(
                    NameTextField.getText(), SurnameTextField.getText(), username,
                    new String(PasswordTextField1.getPassword()), new String(PasswordProveTextField.getPassword()), EMailTextField.getText());
            JOptionPane.showMessageDialog(RegisterDialog,
                    "Reģistrācija veiksmīga. Tagad varat pieteikties.", "LibInfo", JOptionPane.INFORMATION_MESSAGE);
            CancelButtonActionPerformed(evt);
            UsernameTextField.setText(username);
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void CancelButtonActionPerformed(java.awt.event.ActionEvent evt) {
        // clearRegisterFields + showLogin
        SurnameTextField.setText(""); NameTextField.setText("");
        PasswordTextField1.setText(""); UsernameTextField1.setText("");
        EMailTextField.setText(""); PasswordProveTextField.setText("");
        UsernameTextField.setText("");
        PasswordTextField.setText("");
        showDialog(LoginDialog);
        UsernameTextField.requestFocusInWindow();
    }

    private void CloseButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (JOptionPane.showConfirmDialog(getMessageParent(), "Vai tiešām iziet no LibInfo?", "Iziet", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        hideAllDialogs(); dispose(); System.exit(0);
    }

    private void AddButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (!isStaff()) { showError(new SecurityException("Grāmatu pievienošana ir pieejama bibliotekāram vai administratoram.")); return; }
        editingBookId = -1;
        // loadBookChoices
        try {
            java.util.SortedSet<String> authors = new java.util.TreeSet<>();
            java.util.SortedSet<String> categories = new java.util.TreeSet<>();
            authors.add(""); categories.add("");
            for (Gramata book : gramataDAO.searchBooks("", "Visas grāmatas", currentUser)) {
                if (book.getAutors() != null) authors.add(book.getAutors());
                if (book.getKategorija() != null) categories.add(book.getKategorija());
            }
            BookAuthorTextField.setModel(new DefaultComboBoxModel<>(authors.toArray(new String[0])));
            BookCategoryTextField.setModel(new DefaultComboBoxModel<>(categories.toArray(new String[0])));
        } catch (Exception ex) { showError(ex); return; }
        // clearBookFields
        BookNameTextField.setText(""); BookAuthorTextField.setSelectedItem(""); BookISBNTextField.setText("");
        BookCategoryTextField.setSelectedItem(""); BookYearSpinner.setValue(java.time.LocalDate.now().getYear());
        BookDescriptionTextArea.setText(""); BookAvailabilityCheckBox.setSelected(true);
        originalBookYear = null; bookYearEdited = false;
        BookAvailabilityCheckBox.setEnabled(true);
        BookEditDialog.setTitle("LibInfo - Pievienot grāmatu");
        showDialog(BookEditDialog);
    }

    private void BookListButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (currentUser == null) return;
        filterComboInitialized = false;
        BookListDialog.setVisible(false);
        showDialog(BookListDialog);
        FilterComboBoxActionPerformed(null);
        boolean staff = isStaff();
        AddButton.setEnabled(staff);
        EditButton.setEnabled(staff);
        DeleteButton.setEnabled(staff);
        loadBooksFromUi(true);
    }

    private void EditButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (!isStaff()) { showError(new SecurityException("Grāmatu rediģēšana ir pieejama bibliotekāram vai administratoram.")); return; }
        int row = BookTable.getSelectedRow();
        if (row < 0) { showError(new IllegalArgumentException("Izvēlieties grāmatu tabulā.")); return; }
        int modelRow = BookTable.convertRowIndexToModel(row);
        int id = ((Number) BookTable.getModel().getValueAt(modelRow, 0)).intValue();
        try {
            Gramata book = gramataDAO.findBookById(id, currentUser);
            if (book == null) throw new IllegalArgumentException("Grāmata nav atrasta.");
            java.util.SortedSet<String> authors = new java.util.TreeSet<>();
            java.util.SortedSet<String> categories = new java.util.TreeSet<>();
            authors.add(""); categories.add("");
            for (Gramata b : gramataDAO.searchBooks("", "Visas grāmatas", currentUser)) {
                if (b.getAutors() != null) authors.add(b.getAutors());
                if (b.getKategorija() != null) categories.add(b.getKategorija());
            }
            BookAuthorTextField.setModel(new DefaultComboBoxModel<>(authors.toArray(new String[0])));
            BookCategoryTextField.setModel(new DefaultComboBoxModel<>(categories.toArray(new String[0])));
            editingBookId = id;
            BookNameTextField.setText(nvl(book.getNosaukums()));
            BookAuthorTextField.setSelectedItem(nvl(book.getAutors()));
            BookISBNTextField.setText(nvl(book.getIsbn()));
            BookCategoryTextField.setSelectedItem(nvl(book.getKategorija()));
            BookYearSpinner.setValue(book.getIzdosanasGads() == null ? java.time.LocalDate.now().getYear() : book.getIzdosanasGads());
            BookDescriptionTextArea.setText(nvl(book.getApraksts()));
            BookAvailabilityCheckBox.setSelected(book.isPieejama());
            originalBookYear = book.getIzdosanasGads();
            bookYearEdited = false;
            BookAvailabilityCheckBox.setEnabled(true);
            BookEditDialog.setTitle("LibInfo - Rediģēt grāmatu");
            showDialog(BookEditDialog);
        } catch (Exception ex) { showError(ex); }
    }

    private void SaveBookButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (!isStaff()) return;
        try {
            boolean yearWasEdited = bookYearEdited;
            BookYearSpinner.commitEdit();
            Object yearValue = BookYearSpinner.getValue();
            Gramata book = new Gramata();
            book.setNosaukums(BookNameTextField.getText().trim());
            book.setAutors(String.valueOf(BookAuthorTextField.getEditor().getItem()).trim());
            book.setIsbn(BookISBNTextField.getText().trim());
            book.setKategorija(String.valueOf(BookCategoryTextField.getEditor().getItem()).trim());
            book.setApraksts(BookDescriptionTextArea.getText().trim());
            if (yearValue instanceof Number && (editingBookId < 0 || originalBookYear != null || yearWasEdited)) {
                book.setIzdosanasGads(((Number) yearValue).intValue());
            }
            gramataDAO.saveBook(book, editingBookId, BookAvailabilityCheckBox.isSelected(), currentUser);
            JOptionPane.showMessageDialog(BookEditDialog, "Grāmatas dati saglabāti.", "LibInfo", JOptionPane.INFORMATION_MESSAGE);
            BookEditDialog.setVisible(false);
            clearDialogFields(BookEditDialog);
            showDialog(BookListDialog);
            loadBooksFromUi();
        } catch (Exception ex) { showError(ex); }
    }

    private void DeleteButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (!isStaff()) return;
        int row = BookTable.getSelectedRow();
        if (row < 0) { showError(new IllegalArgumentException("Izvēlieties grāmatu tabulā.")); return; }
        int modelRow = BookTable.convertRowIndexToModel(row);
        int id = ((Number) BookTable.getModel().getValueAt(modelRow, 0)).intValue();
        if (JOptionPane.showConfirmDialog(BookListDialog, "Dzēst izvēlēto grāmatu?", "Apstiprināt",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            if (!gramataDAO.deleteBook(id, currentUser)) throw new IllegalStateException("Grāmatu nevar dzēst, jo tai ir saistīti ieraksti.");
            showInfo("Grāmata veiksmīgi dzēsta.");
            loadBooksFromUi();
        } catch (Exception ex) { showError(ex); }
    }

    private void ISBNSearchButtonActionPerformed(java.awt.event.ActionEvent evt) {
        try {
            Gramata metadata = gramataDAO.fetchBookMetadataByIsbn(BookISBNTextField.getText().trim());
            BookNameTextField.setText(nvl(metadata.getNosaukums()));
            BookAuthorTextField.setSelectedItem(nvl(metadata.getAutors()));
            BookDescriptionTextArea.setText(nvl(metadata.getApraksts()));
            BookCategoryTextField.setSelectedItem(nvl(metadata.getKategorija()));
            if (metadata.getIzdosanasGads() != null) {
                int currentYear = java.time.LocalDate.now().getYear();
                int year = metadata.getIzdosanasGads();
                if (year >= 1800 && year <= currentYear + 1) BookYearSpinner.setValue(year);
            }
            showInfo("Dati aizpildīti no Google Books API.");
        } catch (Exception ex) { showError(ex); }
    }

    private void SearchButtonActionPerformed(java.awt.event.ActionEvent evt) { loadBooksFromUi(true); }

    private void FilterComboBoxActionPerformed(java.awt.event.ActionEvent evt) {
        if (suppressFilterAction) return;
        if (!filterComboInitialized) {
            java.util.LinkedHashSet<String> items = new java.util.LinkedHashSet<>();
            items.add("Visas grāmatas"); items.add("Pieejamās"); items.add("Nepieejamās");
            try { items.addAll(gramataDAO.findBookCategories()); } catch (Exception ex) { showError(ex); }
            filterComboInitialized = true;
            suppressFilterAction = true;
            FilterComboBox.setModel(new DefaultComboBoxModel<>(items.toArray(new String[0])));
            FilterComboBox.setSelectedIndex(0);
            suppressFilterAction = false;
            return;
        }
        if (BookListDialog.isVisible() && currentUser != null) loadBooksFromUi(true);
    }

    private void CloseButton2ActionPerformed(java.awt.event.ActionEvent evt) { returnToMain(BookListDialog); }

    private void BookNameTextFieldActionPerformed(java.awt.event.ActionEvent evt) { SaveBookButtonActionPerformed(evt); }
    private void BookISBNTextFieldActionPerformed(java.awt.event.ActionEvent evt) { ISBNSearchButtonActionPerformed(evt); }
    private void BookCategoryTextFieldActionPerformed(java.awt.event.ActionEvent evt) { SaveBookButtonActionPerformed(evt); }

    private void BookAvailabilityCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {
        if (editingBookId > 0) {
            boolean now = BookAvailabilityCheckBox.isSelected();
            BookAvailabilityCheckBox.setToolTipText(now ? "Grāmata tiks atzīmēta kā pieejama."
                    : "Grāmata tiks atzīmēta kā nepieejama. Esošie izsniegumi netiek mainīti.");
        }
    }

    private void ReturnButton1ActionPerformed(java.awt.event.ActionEvent evt) {
        BookEditDialog.setVisible(false);
        clearDialogFields(BookEditDialog);
        showDialog(BookListDialog);
        loadBooksFromUi();
    }

    private void UserListButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (!isStaff()) { showError(new SecurityException("Lietotāju pārvaldība ir pieejama tikai darbiniekiem.")); return; }
        showDialog(UserListFrameDialog);
        DeleteUserButton.setEnabled(isAdmin());
        EditUserRoleButton.setEnabled(isAdmin());
        loadUsersFromUi();
    }

    private void AddUserButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (!isStaff()) { showError(new SecurityException("Lietotāja izveide ir pieejama tikai darbiniekiem.")); return; }
        editingUserId = -1;
        UserNameTextField.setText(""); UserSurnameTextField.setText(""); UsernickTextField.setText("");
        UserPasswordTextField.setText(""); UserEMailTextField.setText(""); UserRoleComboBox.setSelectedItem("READER");
        List<Loma> roles = lietotajsDAO.getAvailableRoles(currentUser);
        String[] names = roles.stream().map(Enum::name).toArray(String[]::new);
        UserRoleComboBox.setModel(new DefaultComboBoxModel<>(names));
        UserRoleComboBox.setSelectedItem("READER");
        UserRoleComboBox.setEnabled(isAdmin());
        UserEditDialog.setTitle("LibInfo - Pievienot lietotāju");
        showDialog(UserEditDialog);
    }

    private void EditUserButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (!isStaff()) return;
        int row = UserTable.getSelectedRow();
        if (row < 0) { showError(new IllegalArgumentException("Izvēlieties lietotāju tabulā.")); return; }
        int modelRow = UserTable.convertRowIndexToModel(row);
        int id = ((Number) UserTable.getModel().getValueAt(modelRow, 0)).intValue();
        try {
            Lietotajs user = lietotajsDAO.findUserById(id, currentUser);
            if (user == null) throw new IllegalArgumentException("Lietotājs nav atrasts.");
            editingUserId = id;
            UserNameTextField.setText(nvl(user.getVards()));
            UserSurnameTextField.setText(nvl(user.getUzvards()));
            UsernickTextField.setText(nvl(user.getLietotajvards()));
            UserPasswordTextField.setText("");
            UserEMailTextField.setText(nvl(user.getEpasts()));
            List<Loma> roles = lietotajsDAO.getAvailableRoles(currentUser);
            String[] names = roles.stream().map(Enum::name).toArray(String[]::new);
            UserRoleComboBox.setModel(new DefaultComboBoxModel<>(names));
            String roleName = user.getLoma().name();
            boolean rolePresent = false;
            for (int i = 0; i < UserRoleComboBox.getItemCount(); i++) {
                if (roleName.equals(UserRoleComboBox.getItemAt(i))) { rolePresent = true; break; }
            }
            if (!rolePresent) UserRoleComboBox.addItem(roleName);
            UserRoleComboBox.setSelectedItem(roleName);
            UserRoleComboBox.setEnabled(isAdmin() && id != currentUser.getLietotajaId());
            UserEditDialog.setTitle("LibInfo - Rediģēt lietotāju");
            showDialog(UserEditDialog);
        } catch (Exception ex) { showError(ex); }
    }

    private void SaveUserButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (!isStaff()) return;
        try {
            Lietotajs form = new Lietotajs();
            form.setVards(UserNameTextField.getText());
            form.setUzvards(UserSurnameTextField.getText());
            form.setLietotajvards(UsernickTextField.getText());
            form.setEpasts(UserEMailTextField.getText());
            try { form.setLoma(Loma.valueOf(String.valueOf(UserRoleComboBox.getSelectedItem()))); }
            catch (IllegalArgumentException ex) { form.setLoma(Loma.READER); }
            lietotajsDAO.saveManagedUser(form, editingUserId, new String(UserPasswordTextField.getPassword()), currentUser);
            JOptionPane.showMessageDialog(UserEditDialog, "Lietotāja dati saglabāti.", "LibInfo", JOptionPane.INFORMATION_MESSAGE);
            UserEditDialog.setVisible(false);
            clearDialogFields(UserEditDialog);
            showDialog(UserListFrameDialog);
            loadUsersFromUi(false);
        } catch (Exception ex) { showError(ex); }
    }

    private void DeleteUserButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (!isAdmin()) { showError(new SecurityException("Lietotājus dzēst drīkst tikai administrators.")); return; }
        int row = UserTable.getSelectedRow();
        if (row < 0) { showError(new IllegalArgumentException("Izvēlieties lietotāju tabulā.")); return; }
        int modelRow = UserTable.convertRowIndexToModel(row);
        int id = ((Number) UserTable.getModel().getValueAt(modelRow, 0)).intValue();
        if (JOptionPane.showConfirmDialog(UserListFrameDialog, "Dzēst izvēlēto lietotāju?", "Apstiprināt",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            if (!lietotajsDAO.deleteUser(id, currentUser))
                throw new IllegalStateException("Lietotāju nevar dzēst, jo tas ir piesaistīts izsniegumiem vai rezervācijām.");
            showInfo("Lietotājs veiksmīgi dzēsts.");
            loadUsersFromUi(false);
        } catch (Exception ex) { showError(ex); }
    }

    private void UserSearchButtonActionPerformed(java.awt.event.ActionEvent evt) { loadUsersFromUi(); }
    private void UserPasswordTextFieldActionPerformed(java.awt.event.ActionEvent evt) { SaveUserButtonActionPerformed(evt); }
    private void UserRoleComboBoxActionPerformed(java.awt.event.ActionEvent evt) { /* lomas tiek iestatītas dialogā */ }

    private void EditUserRoleButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (!isAdmin()) { showError(new SecurityException("Lomas mainīt drīkst tikai administrators.")); return; }
        EditUserButtonActionPerformed(evt);
        if (UserEditDialog.isVisible()) UserRoleComboBox.requestFocusInWindow();
    }

    private void ReturnButton2ActionPerformed(java.awt.event.ActionEvent evt) { returnToMain(UserListFrameDialog); }

    private void ReturnButton3ActionPerformed(java.awt.event.ActionEvent evt) {
        UserEditDialog.setVisible(false);
        clearDialogFields(UserEditDialog);
        showDialog(UserListFrameDialog);
        loadUsersFromUi(false);
    }

    private void BookLoanButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (!isStaff()) { showError(new SecurityException("Izsniegšanu drīkst reģistrēt bibliotekārs vai administrators.")); return; }
        showDialog(LoanDialog);
        try {
            loanReaders.clear(); loanReaders.addAll(lietotajsDAO.findReaderUsers(currentUser));
            loanCopies.clear();  loanCopies.addAll(eksemplarsDAO.findAvailableCopies(currentUser));
            if (loanReaders.isEmpty()) {
                LoanButton.setEnabled(false);
                ReaderComboBox.setModel(new DefaultComboBoxModel<>());
                BookComboBox.setModel(new DefaultComboBoxModel<>());
                showInfo("Nav reģistrētu lasītāju. Vispirms pievienojiet lasītāju.");
                return;
            }
            if (loanCopies.isEmpty()) {
                LoanButton.setEnabled(false);
                String[] readerNames = loanReaders.stream().map(Lietotajs::toString).toArray(String[]::new);
                ReaderComboBox.setModel(new DefaultComboBoxModel<>(readerNames));
                BookComboBox.setModel(new DefaultComboBoxModel<>());
                showInfo("Nav pieejamu eksemplāru.");
                return;
            }
            LoanButton.setEnabled(true);
            String[] readerNames = loanReaders.stream().map(Lietotajs::toString).toArray(String[]::new);
            String[] copyNames = loanCopies.stream().map(copy -> copy.getGramata().getNosaukums()
                    + " (eksemplārs ID " + copy.getEksemplaraId() + ")").toArray(String[]::new);
            ReaderComboBox.setModel(new DefaultComboBoxModel<>(readerNames));
            BookComboBox.setModel(new DefaultComboBoxModel<>(copyNames));
            LoanDateTextField.setText(LocalDate.now().format(DATE_FORMAT));
            LoanTimeFormattedTextField.setValue(LocalDate.now().plusDays(14).format(DATE_FORMAT));
        } catch (Exception ex) { showError(ex); }
    }

    private void LoanButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (!isStaff()) return;
        int readerIndex = ReaderComboBox.getSelectedIndex();
        int copyIndex = BookComboBox.getSelectedIndex();
        if (readerIndex < 0 || copyIndex < 0) { showError(new IllegalArgumentException("Izvēlieties lasītāju un eksemplāru.")); return; }
        try { LoanTimeFormattedTextField.commitEdit(); }
        catch (java.text.ParseException ex) { showError(new IllegalArgumentException("Nederīgs termiņa formāts. Izmantojiet GGGG-MM-DD.")); return; }
        try {
            izsniegumsDAO.issueBook(loanReaders.get(readerIndex).getLietotajaId(),
                    loanCopies.get(copyIndex).getEksemplaraId(),
                    String.valueOf(LoanTimeFormattedTextField.getValue()), currentUser);
            JOptionPane.showMessageDialog(LoanDialog, "Grāmata izsniegta.", "LibInfo", JOptionPane.INFORMATION_MESSAGE);
            returnToMain(LoanDialog);
        } catch (Exception ex) { showError(ex); }
    }

    private void BookReturnButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (!isStaff()) { showError(new SecurityException("Grāmatu atgriešanu drīkst veikt darbinieks.")); return; }
        showDialog(ReturnDialog);
        try {
            List<Izsniegums> loans = izsniegumsDAO.findLoans(false, currentUser);
            DefaultTableModel model = (DefaultTableModel) ReturnTable.getModel();
            model.setRowCount(0);
            for (Izsniegums loan : loans) {
                model.addRow(new Object[]{loan.getIzsniegumaId(), loan.getLietotajs(),
                        loan.getEksemplars() == null || loan.getEksemplars().getGramata() == null ? "" : loan.getEksemplars().getGramata().getNosaukums(),
                        loan.getIzsniegts(), loan.getTermins()});
            }
            ReturnLabel.setText("Aktīvie izsniegumi");
        } catch (Exception ex) { showError(ex); }
    }

    private void ReturnButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (!isStaff()) return;
        int row = ReturnTable.getSelectedRow();
        if (row < 0) { showError(new IllegalArgumentException("Izvēlieties izsniegumu.")); return; }
        int modelRow = ReturnTable.convertRowIndexToModel(row);
        int id = ((Number) ReturnTable.getModel().getValueAt(modelRow, 0)).intValue();
        try {
            izsniegumsDAO.returnLoan(id, currentUser);
            showInfo("Grāmata veiksmīgi atgriezta.");
            BookReturnButtonActionPerformed(evt);
        } catch (Exception ex) { showError(ex); }
    }

    private void ReservationButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (currentUser == null) return;
        showDialog(ReservationDialog);
        try {
            List<Rezervacija> reservations = rezervacijaDAO.findReservations(currentUser);
            DefaultTableModel model = (DefaultTableModel) ReservationTable.getModel();
            model.setRowCount(0);
            for (Rezervacija reservation : reservations) {
                model.addRow(new Object[]{reservation.getRezervacijasId(), reservation.getLietotajs(),
                        reservation.getGramata() == null ? "" : reservation.getGramata().getNosaukums(),
                        reservation.getDatums(), reservation.getStatuss()});
            }
            updateReservationActionButtons();
        } catch (Exception ex) { showError(ex); }
    }

    private void NewReservationButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (!isReader()) { showError(new SecurityException("Rezervāciju var izveidot tikai lasītājs.")); return; }
        try {
            reservationBooks.clear();
            reservationBooks.addAll(rezervacijaDAO.findReservationBooks(currentUser));
            if (reservationBooks.isEmpty()) { showError(new IllegalStateException("Nav grāmatu rezervācijai.")); return; }
            String[] names = reservationBooks.stream().map(Gramata::getNosaukums).toArray(String[]::new);
            int selected = JOptionPane.showOptionDialog(ReservationDialog, "Izvēlieties grāmatu rezervācijai:",
                    "Jauna rezervācija", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, names, names[0]);
            if (selected < 0) return;
            rezervacijaDAO.createReservation(reservationBooks.get(selected).getGramatasId(), currentUser);
            showInfo("Rezervācija izveidota.");
            ReservationButtonActionPerformed(evt);
        } catch (Exception ex) { showError(ex); }
    }

    private void CancelReservationButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (!isReader()) { showError(new SecurityException("Rezervāciju var atcelt tās īpašnieks — lasītājs.")); return; }
        int row = ReservationTable.getSelectedRow();
        if (row < 0) { showError(new IllegalArgumentException("Izvēlieties rezervāciju.")); return; }
        int modelRow = ReservationTable.convertRowIndexToModel(row);
        int id = ((Number) ReservationTable.getModel().getValueAt(modelRow, 0)).intValue();
        try {
            rezervacijaDAO.cancelReservation(id, currentUser);
            showInfo("Rezervācija veiksmīgi atcelta.");
            ReservationButtonActionPerformed(evt);
        } catch (Exception ex) { showError(ex); }
    }

    private void RunButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (!isStaff()) { showError(new SecurityException("Rezervāciju var izpildīt bibliotekārs vai administrators.")); return; }
        int row = ReservationTable.getSelectedRow();
        if (row < 0) { showError(new IllegalArgumentException("Izvēlieties rezervāciju.")); return; }
        int modelRow = ReservationTable.convertRowIndexToModel(row);
        int id = ((Number) ReservationTable.getModel().getValueAt(modelRow, 0)).intValue();
        try {
            rezervacijaDAO.completeReservation(id, currentUser);
            showInfo("Rezervācija veiksmīgi izpildīta.");
            ReservationButtonActionPerformed(evt);
        } catch (Exception ex) { showError(ex); }
    }

    private void NotificationsButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (currentUser == null) return;
        showDialog(NotificationDialog);
        try {
            List<Izsniegums> overdueNotifications = izsniegumsDAO.findNotificationLoans(currentUser);
            List<Rezervacija> activeReservationNotifications = rezervacijaDAO.findNotificationReservations(currentUser);
            DefaultTableModel model = (DefaultTableModel) NotificationTable.getModel();
            model.setRowCount(0);
            for (Izsniegums loan : overdueNotifications) {
                String key = "loan:" + loan.getIzsniegumaId();
                String title = loan.getEksemplars() == null || loan.getEksemplars().getGramata() == null
                        ? "Grāmata" : loan.getEksemplars().getGramata().getNosaukums();
                model.addRow(new Object[]{loan.getIzsniegumaId(), "Kavējums", "Jāatgriež: " + title,
                        loan.getTermins(), readNotificationKeys.contains(key) ? "Jā" : "Nē"});
            }
            for (Rezervacija reservation : activeReservationNotifications) {
                String key = "reservation:" + reservation.getRezervacijasId();
                String title = reservation.getGramata() == null ? "Grāmata" : reservation.getGramata().getNosaukums();
                model.addRow(new Object[]{100000 + reservation.getRezervacijasId(), "Rezervācija",
                        "Aktīva rezervācija: " + title, reservation.getDatums(), readNotificationKeys.contains(key) ? "Jā" : "Nē"});
            }
        } catch (Exception ex) { showError(ex); }
    }

    private void ReadButtonActionPerformed(java.awt.event.ActionEvent evt) {
        int viewRow = NotificationTable.getSelectedRow();
        if (viewRow < 0) { showError(new IllegalArgumentException("Izvēlieties paziņojumu.")); return; }
        int row = NotificationTable.convertRowIndexToModel(viewRow);
        int displayedId = ((Number) NotificationTable.getModel().getValueAt(row, 0)).intValue();
        String label = String.valueOf(NotificationTable.getModel().getValueAt(row, 1));
        String type = "Kavējums".equals(label) ? "loan" : "reservation";
        int id = "reservation".equals(type) ? displayedId - 100000 : displayedId;
        String key = type + ":" + id;
        try {
            lietotajsDAO.markNotificationRead(type, id, currentUser);
            readNotificationKeys.add(key);
            showInfo("Paziņojums atzīmēts kā izlasīts un saglabāts datubāzē.");
            NotificationsButtonActionPerformed(evt);
        } catch (Exception ex) { showError(ex); }
    }

    public static void main(String args[]) {
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(LibInfoSaskarne.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        java.awt.EventQueue.invokeLater(() -> new LibInfoSaskarne());
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel MainFrameStatusLabel;
    private javax.swing.JLabel BookListDialogStatusLabel;
    private javax.swing.JLabel UserSearchLabel;
    private javax.swing.JLabel UserListFrameDialogStatusLabel;
    private javax.swing.JLabel UserEditDialogStatusLabel;
    private javax.swing.JLabel LoanDialogStatusLabel;
    private javax.swing.JLabel ReturnDialogStatusLabel;
    private javax.swing.JLabel ReservationDialogStatusLabel;
    private javax.swing.JLabel NotificationDialogStatusLabel;
    private javax.swing.JPanel LoginDialogSurface;
    private javax.swing.JPanel RegisterDialogSurface;
    private javax.swing.JPanel BookListDialogSurface;
    private javax.swing.JPanel BookEditDialogSurface;
    private javax.swing.JPanel UserListFrameDialogSurface;
    private javax.swing.JPanel UserEditDialogSurface;
    private javax.swing.JPanel LoanDialogSurface;
    private javax.swing.JPanel ReturnDialogSurface;
    private javax.swing.JPanel ReservationDialogSurface;
    private javax.swing.JPanel NotificationDialogSurface;
    private javax.swing.JPanel MainFrameSurface;

    private javax.swing.JMenuBar ActionsMenuBar;
    private javax.swing.JButton AddButton;
    private javax.swing.JButton AddUserButton;
    private javax.swing.JLabel AppLabel;
    private javax.swing.JButton BookAddButton;
    private javax.swing.JLabel BookAuthorLabel;
    private javax.swing.JComboBox BookAuthorTextField;
    private javax.swing.JCheckBox BookAvailabilityCheckBox;
    private javax.swing.JLabel BookAvailabilityLabel;
    private javax.swing.JLabel BookCategoryLabel;
    private javax.swing.JComboBox BookCategoryTextField;
    private javax.swing.JComboBox<String> BookComboBox;
    private javax.swing.JLabel BookDescriptionLabel;
    private javax.swing.JTextArea BookDescriptionTextArea;
    private javax.swing.JDialog BookEditDialog;
    private javax.swing.JLabel BookISBNLabel;
    private javax.swing.JTextField BookISBNTextField;
    private javax.swing.JLabel BookLabel;
    private javax.swing.JButton BookListButton;
    private javax.swing.JDialog BookListDialog;
    private javax.swing.JButton BookLoanButton;
    private javax.swing.JMenu BookMenu;
    private javax.swing.JLabel BookNameLabel;
    private javax.swing.JTextField BookNameTextField;
    private javax.swing.JButton BookReturnButton;
    private javax.swing.JScrollPane BookScrollPane;
    private javax.swing.JTable BookTable;
    private javax.swing.JLabel BookYearLabel;
    private javax.swing.JSpinner BookYearSpinner;
    private javax.swing.JButton CancelButton;
    private javax.swing.JButton CancelReservationButton;
    private javax.swing.JButton CloseButton;
    private javax.swing.JButton CloseButton1;
    private javax.swing.JButton CloseButton2;
    private javax.swing.JButton CloseButton3;
    private javax.swing.JButton CloseButton4;
    private javax.swing.JButton DeleteButton;
    private javax.swing.JButton DeleteUserButton;
    private javax.swing.JLabel EMailLabel;
    private javax.swing.JTextField EMailTextField;
    private javax.swing.JButton EditButton;
    private javax.swing.JButton EditUserButton;
    private javax.swing.JButton EditUserRoleButton;
    private javax.swing.JMenu FileMenu;
    private javax.swing.JComboBox<String> FilterComboBox;
    private javax.swing.JLabel FilterLabel;
    private javax.swing.JMenu HelpMenu;
    private javax.swing.JButton ISBNSearchButton;
    private javax.swing.JButton LoanButton;
    private javax.swing.JLabel LoanDateLabel;
    private javax.swing.JTextField LoanDateTextField;
    private javax.swing.JDialog LoanDialog;
    private javax.swing.JMenu LoanMenu;
    private javax.swing.JFormattedTextField LoanTimeFormattedTextField;
    private javax.swing.JLabel LoanTimeLabel;
    private javax.swing.JButton LoginButton;
    private javax.swing.JDialog LoginDialog;
    private javax.swing.JLabel NameLabel;
    private javax.swing.JTextField NameTextField;
    private javax.swing.JButton NewReservationButton;
    private javax.swing.JDialog NotificationDialog;
    private javax.swing.JLabel NotificationLabel;
    private javax.swing.JMenu NotificationMenu;
    private javax.swing.JScrollPane NotificationScrollPane;
    private javax.swing.JTable NotificationTable;
    private javax.swing.JButton NotificationsButton;
    private javax.swing.JLabel PasswordLabel;
    private javax.swing.JLabel PasswordLabel1;
    private javax.swing.JLabel PasswordProveLabel;
    private javax.swing.JPasswordField PasswordProveTextField;
    private javax.swing.JPasswordField PasswordTextField;
    private javax.swing.JPasswordField PasswordTextField1;
    private javax.swing.JButton ReadButton;
    private javax.swing.JComboBox<String> ReaderComboBox;
    private javax.swing.JLabel ReaderLabel;
    private javax.swing.JButton RegisterButton;
    private javax.swing.JButton RegisterButton1;
    private javax.swing.JDialog RegisterDialog;
    private javax.swing.JLabel RegisterLabel;
    private javax.swing.JButton ReservationButton;
    private javax.swing.JDialog ReservationDialog;
    private javax.swing.JLabel ReservationLabel;
    private javax.swing.JMenu ReservationMenu;
    private javax.swing.JScrollPane ReservationScrollPane;
    private javax.swing.JTable ReservationTable;
    private javax.swing.JButton ReturnButton;
    private javax.swing.JButton ReturnButton1;
    private javax.swing.JButton ReturnButton2;
    private javax.swing.JButton ReturnButton3;
    private javax.swing.JButton ReturnButton4;
    private javax.swing.JDialog ReturnDialog;
    private javax.swing.JLabel ReturnLabel;
    private javax.swing.JTable ReturnTable;
    private javax.swing.JButton RunButton;
    private javax.swing.JButton SaveBookButton;
    private javax.swing.JButton SaveUserButton;
    private javax.swing.JButton SearchButton;
    private javax.swing.JTextField SearchTextField;
    private javax.swing.JLabel SurnameLabel;
    private javax.swing.JTextField SurnameTextField;
    private javax.swing.JLabel UserEMailLabel;
    private javax.swing.JTextField UserEMailTextField;
    private javax.swing.JDialog UserEditDialog;
    private javax.swing.JButton UserListButton;
    private javax.swing.JDialog UserListFrameDialog;
    private javax.swing.JMenu UserMenu;
    private javax.swing.JLabel UserNameLabel;
    private javax.swing.JTextField UserNameTextField;
    private javax.swing.JLabel UserPasswordLabel;
    private javax.swing.JPasswordField UserPasswordTextField;
    private javax.swing.JComboBox<String> UserRoleComboBox;
    private javax.swing.JLabel UserRoleLabel;
    private javax.swing.JScrollPane UserScrollPane;
    private javax.swing.JButton UserSearchButton;
    private javax.swing.JTextField UserSearchTextField;
    private javax.swing.JLabel UserSurnameLabel;
    private javax.swing.JTextField UserSurnameTextField;
    private javax.swing.JTable UserTable;
    private javax.swing.JLabel UsernameLabel;
    private javax.swing.JLabel UsernameLabel1;
    private javax.swing.JTextField UsernameTextField;
    private javax.swing.JTextField UsernameTextField1;
    private javax.swing.JLabel UsernickLabel;
    private javax.swing.JTextField UsernickTextField;
    private javax.swing.JButton jButton19;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JMenuItem menuAboutItem;
    private javax.swing.JMenuItem menuAddBookItem;
    private javax.swing.JMenuItem menuAddUserItem;
    private javax.swing.JMenuItem menuBooksItem;
    private javax.swing.JMenuItem menuExitItem;
    private javax.swing.JMenuItem menuIssueItem;
    private javax.swing.JMenuItem menuNotificationsItem;
    private javax.swing.JMenuItem menuOverdueItem;
    private javax.swing.JMenuItem menuReservationsItem;
    private javax.swing.JMenuItem menuReturnItem;
    private javax.swing.JMenuItem menuSearchBookItem;
    private javax.swing.JMenuItem menuUsersItem;
    // End of variables declaration//GEN-END:variables
}