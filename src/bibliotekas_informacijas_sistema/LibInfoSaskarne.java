package bibliotekas_informacijas_sistema;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */

/**
 *
 * @author user
 */

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
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
import bibliotekas_informacijas_sistema.model.Eksemplars;
import bibliotekas_informacijas_sistema.model.Gramata;
import bibliotekas_informacijas_sistema.model.Izsniegums;
import bibliotekas_informacijas_sistema.model.Lietotajs;
import bibliotekas_informacijas_sistema.model.Loma;
import bibliotekas_informacijas_sistema.model.Rezervacija;
import bibliotekas_informacijas_sistema.dao.RezervacijaDAO;
import bibliotekas_informacijas_sistema.dao.LietotajsDAO;
import bibliotekas_informacijas_sistema.dao.GramataDAO;
import bibliotekas_informacijas_sistema.dao.EksemplarsDAO;
import bibliotekas_informacijas_sistema.dao.IzsniegumsDAO;


public class LibInfoSaskarne extends javax.swing.JFrame {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final LietotajsDAO lietotajsDAO = new LietotajsDAO();
    private final GramataDAO gramataDAO = new GramataDAO();
    private final EksemplarsDAO eksemplarsDAO = new EksemplarsDAO();
    private final IzsniegumsDAO izsniegumsDAO = new IzsniegumsDAO();
    private final RezervacijaDAO rezervacijaDAO = new RezervacijaDAO();
    private Lietotajs currentUser;
    private int editingBookId = -1;
    private int editingUserId = -1;
    private final Set<String> readNotificationKeys = new HashSet<>();
    private final List<Lietotajs> loanReaders = new ArrayList<>();
    private final List<Eksemplars> loanCopies = new ArrayList<>();
    private final List<Gramata> reservationBooks = new ArrayList<>();
    private boolean filterComboInitialized = false;
    private boolean suppressFilterAction = false;
    private javax.swing.JMenuItem menuAddBookItem;
    private javax.swing.JMenuItem menuAddUserItem;

    // ==================== UZNĒMĒJDARBĪBAS LOĢIKA (paliek kā metodes) ====================
    private boolean isStaff() { return currentUser != null && (currentUser.getLoma() == Loma.ADMIN || currentUser.getLoma() == Loma.LIBRARIAN); }
    private boolean isAdmin() { return currentUser != null && currentUser.getLoma() == Loma.ADMIN; }
    private boolean isReader() { return currentUser != null && currentUser.getLoma() == Loma.READER; }

    private void updateMainRoleAccess() {
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
        MainDialog.setTitle("LibInfo - Galvenais logs" + (currentUser == null ? "" : " — " + currentUser.getLoma()));
    }

    private void showError(Throwable ex) {
        String message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
        JOptionPane.showMessageDialog(getMessageParent(), message, "LibInfo kļūda", JOptionPane.ERROR_MESSAGE);
    }

    private void login() {
        String username = UsernameTextField.getText().trim();
        String password = new String(PasswordTextField.getPassword());
        try {
            currentUser = lietotajsDAO.authenticate(username, password);
            readNotificationKeys.clear();
            readNotificationKeys.addAll(lietotajsDAO.findReadNotificationKeys(currentUser));
            showMain();
        } catch (Exception ex) { showError(ex); }
    }

    private void openRegister() {
        clearRegisterFields();
        LoginDialog.setVisible(false);
        showDialog(RegisterDialog);
        SurnameTextField.requestFocusInWindow();
    }

    private void registerUser() {
        String username = UsernameTextField1.getText().trim();
        try {
            lietotajsDAO.registerUser(NameTextField.getText(), SurnameTextField.getText(), username,
                    new String(PasswordTextField1.getPassword()), new String(PasswordProveTextField.getPassword()), EMailTextField.getText());
            JOptionPane.showMessageDialog(RegisterDialog, "Reģistrācija veiksmīga. Tagad varat pieteikties.", "LibInfo", JOptionPane.INFORMATION_MESSAGE);
            showLogin();
            UsernameTextField.setText(username);
        } catch (Exception ex) { showError(ex); }
    }

    private void openBookList() {
        if (currentUser == null) return;
        filterComboInitialized = false;
        BookListDialog.setVisible(false);
        showDialog(BookListDialog);
        FilterComboBoxActionPerformed(null);
        boolean staff = isStaff();
        AddButton.setEnabled(staff);
        EditButton.setEnabled(staff);
        DeleteButton.setEnabled(staff);
        loadBooksFromUi();
    }

    private void loadBooksFromUi() {
        try {
            List<Gramata> books = gramataDAO.searchBooks(SearchTextField.getText().trim(), String.valueOf(FilterComboBox.getSelectedItem()), currentUser);
            DefaultTableModel model = (DefaultTableModel) BookTable.getModel();
            model.setRowCount(0);
            for (Gramata book : books) {
                model.addRow(new Object[]{book.getGramatasId(), book.getNosaukums(), book.getAutors(),
                    book.getIsbn(), book.getKategorija(), book.getIzdosanasGads(), book.isPieejama() ? "Jā" : "Nē"});
            }
        } catch (Exception ex) { showError(ex); }
    }

    private void openNewBook() {
        if (!isStaff()) { showError(new SecurityException("Grāmatu pievienošana ir pieejama bibliotekāram vai administratoram.")); return; }
        editingBookId = -1;
        clearBookFields();
        BookAvailabilityCheckBox.setEnabled(true);
        BookEditDialog.setTitle("LibInfo - Pievienot grāmatu");
        showDialog(BookEditDialog);
    }

    private void editSelectedBook() {
        if (!isStaff()) { showError(new SecurityException("Grāmatu rediģēšana ir pieejama bibliotekāram vai administratoram.")); return; }
        int row = BookTable.getSelectedRow();
        if (row < 0) { showError(new IllegalArgumentException("Izvēlieties grāmatu tabulā.")); return; }
        int modelRow = BookTable.convertRowIndexToModel(row);
        int id = ((Number) BookTable.getModel().getValueAt(modelRow, 0)).intValue();
        try {
            Gramata book = gramataDAO.findBookById(id, currentUser);
            if (book == null) throw new IllegalArgumentException("Grāmata nav atrasta.");
            editingBookId = id;
            fillBookFields(book);
            BookAvailabilityCheckBox.setEnabled(false);
            BookEditDialog.setTitle("LibInfo - Rediģēt grāmatu");
            showDialog(BookEditDialog);
        } catch (Exception ex) { showError(ex); }
    }

    private void saveBook() {
        if (!isStaff()) return;
        try {
            Object yearValue = BookYearSpinner.getValue();
            Gramata book = new Gramata();
            book.setNosaukums(BookNameTextField.getText().trim());
            book.setAutors(BookAuthorTextField.getText().trim());
            book.setIsbn(BookISBNTextField.getText().trim());
            book.setKategorija(BookCategoryTextField.getText().trim());
            book.setApraksts(BookDescriptionTextArea.getText().trim());
            if (yearValue instanceof Number) book.setIzdosanasGads(((Number) yearValue).intValue());
            gramataDAO.saveBook(book, editingBookId, BookAvailabilityCheckBox.isSelected(), currentUser);
            JOptionPane.showMessageDialog(BookEditDialog, "Grāmatas dati saglabāti.", "LibInfo", JOptionPane.INFORMATION_MESSAGE);
            returnToBookList();
        } catch (Exception ex) { showError(ex); }
    }

    private void deleteSelectedBook() {
        if (!isStaff()) return;
        int row = BookTable.getSelectedRow();
        if (row < 0) { showError(new IllegalArgumentException("Izvēlieties grāmatu tabulā.")); return; }
        int modelRow = BookTable.convertRowIndexToModel(row);
        int id = ((Number) BookTable.getModel().getValueAt(modelRow, 0)).intValue();
        if (JOptionPane.showConfirmDialog(BookListDialog, "Dzēst izvēlēto grāmatu?", "Apstiprināt", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            if (!gramataDAO.deleteBook(id, currentUser)) throw new IllegalStateException("Grāmatu nevar dzēst, jo tai ir saistīti ieraksti.");
            showInfo("Grāmata veiksmīgi dzēsta.");
            loadBooksFromUi();
        } catch (Exception ex) { showError(ex); }
    }

    private void importBookByIsbn() {
        try {
            Gramata metadata = gramataDAO.fetchBookMetadataByIsbn(BookISBNTextField.getText().trim());
            BookNameTextField.setText(nvl(metadata.getNosaukums()));
            BookAuthorTextField.setText(nvl(metadata.getAutors()));
            BookDescriptionTextArea.setText(nvl(metadata.getApraksts()));
            BookCategoryTextField.setText(nvl(metadata.getKategorija()));
            if (metadata.getIzdosanasGads() != null) {
                int currentYear = java.time.LocalDate.now().getYear();
                int year = metadata.getIzdosanasGads();
                if (year >= 1800 && year <= currentYear + 1) BookYearSpinner.setValue(year);
            }
            showInfo("Dati aizpildīti no Google Books API.");
        } catch (Exception ex) { showError(ex); }
    }

    private void openUserList() {
        if (!isStaff()) { showError(new SecurityException("Lietotāju pārvaldība ir pieejama tikai darbiniekiem.")); return; }
        showDialog(UserListFrameDialog);
        DeleteUserButton.setEnabled(isAdmin());
        EditUserRoleButton.setEnabled(isAdmin());
        loadUsersFromUi();
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
        } catch (Exception ex) { showError(ex); }
    }

    private void openNewUser() {
        if (!isStaff()) { showError(new SecurityException("Lietotāja izveide ir pieejama tikai darbiniekiem.")); return; }
        editingUserId = -1;
        clearUserFields();
        configureUserRoleChoices();
        UserRoleComboBox.setSelectedItem("READER");
        UserRoleComboBox.setEnabled(isAdmin());
        UserEditDialog.setTitle("LibInfo - Pievienot lietotāju");
        showDialog(UserEditDialog);
    }

    private void configureUserRoleChoices() {
        List<Loma> roles = lietotajsDAO.getAvailableRoles(currentUser);
        String[] names = roles.stream().map(Enum::name).toArray(String[]::new);
        UserRoleComboBox.setModel(new DefaultComboBoxModel<>(names));
    }

    private void editSelectedUser() {
        if (!isStaff()) return;
        int row = UserTable.getSelectedRow();
        if (row < 0) { showError(new IllegalArgumentException("Izvēlieties lietotāju tabulā.")); return; }
        int modelRow = UserTable.convertRowIndexToModel(row);
        int id = ((Number) UserTable.getModel().getValueAt(modelRow, 0)).intValue();
        try {
            Lietotajs user = lietotajsDAO.findUserById(id, currentUser);
            if (user == null) throw new IllegalArgumentException("Lietotājs nav atrasts.");
            editingUserId = id;
            fillUserFields(user);
            configureUserRoleChoices();
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

    private void saveUser() {
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
            returnToUserList();
        } catch (Exception ex) { showError(ex); }
    }

    private void deleteSelectedUser() {
        if (!isAdmin()) { showError(new SecurityException("Lietotājus dzēst drīkst tikai administrators.")); return; }
        int row = UserTable.getSelectedRow();
        if (row < 0) { showError(new IllegalArgumentException("Izvēlieties lietotāju tabulā.")); return; }
        int modelRow = UserTable.convertRowIndexToModel(row);
        int id = ((Number) UserTable.getModel().getValueAt(modelRow, 0)).intValue();
        if (JOptionPane.showConfirmDialog(UserListFrameDialog, "Dzēst izvēlēto lietotāju?", "Apstiprināt", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            if (!lietotajsDAO.deleteUser(id, currentUser)) throw new IllegalStateException("Lietotāju nevar dzēst, jo tas ir piesaistīts izsniegumiem vai rezervācijām.");
            showInfo("Lietotājs veiksmīgi dzēsts.");
            loadUsersFromUi(false);
        } catch (Exception ex) { showError(ex); }
    }

    private void changeSelectedRole() {
        if (!isAdmin()) { showError(new SecurityException("Lomas mainīt drīkst tikai administrators.")); return; }
        int row = UserTable.getSelectedRow();
        if (row < 0) { showError(new IllegalArgumentException("Izvēlieties lietotāju tabulā.")); return; }
        int modelRow = UserTable.convertRowIndexToModel(row);
        int id = ((Number) UserTable.getModel().getValueAt(modelRow, 0)).intValue();
        List<Loma> roles = lietotajsDAO.getAvailableRoles(currentUser);
        javax.swing.JComboBox<Loma> combo = new javax.swing.JComboBox<>(roles.toArray(new Loma[0]));
        if (JOptionPane.showConfirmDialog(UserListFrameDialog, combo, "Izvēlieties jauno lomu", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try {
            lietotajsDAO.changeUserRole(id, (Loma) combo.getSelectedItem(), currentUser);
            showInfo("Lietotāja loma veiksmīgi mainīta.");
            loadUsersFromUi(false);
        } catch (Exception ex) { showError(ex); }
    }

    private void openLoan() {
        if (!isStaff()) { showError(new SecurityException("Izsniegšanu drīkst reģistrēt bibliotekārs vai administrators.")); return; }
        showDialog(LoanDialog);
        try {
            loanReaders.clear();
            loanReaders.addAll(lietotajsDAO.findReaderUsers(currentUser));
            loanCopies.clear();
            loanCopies.addAll(eksemplarsDAO.findAvailableCopies(currentUser));

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

    private void issueBook() {
        if (!isStaff()) return;
        int readerIndex = ReaderComboBox.getSelectedIndex();
        int copyIndex = BookComboBox.getSelectedIndex();
        if (readerIndex < 0 || copyIndex < 0) {
            showError(new IllegalArgumentException("Izvēlieties lasītāju un eksemplāru."));
            return;
        }
        try { LoanTimeFormattedTextField.commitEdit(); }
        catch (java.text.ParseException ex) {
            showError(new IllegalArgumentException("Nederīgs termiņa formāts. Izmantojiet GGGG-MM-DD."));
            return;
        }
        try {
            izsniegumsDAO.issueBook(loanReaders.get(readerIndex).getLietotajaId(),
                    loanCopies.get(copyIndex).getEksemplaraId(),
                    String.valueOf(LoanTimeFormattedTextField.getValue()), currentUser);
            JOptionPane.showMessageDialog(LoanDialog, "Grāmata izsniegta.", "LibInfo", JOptionPane.INFORMATION_MESSAGE);
            returnToMain(LoanDialog);
        } catch (Exception ex) { showError(ex); }
    }

    private void openReturn() { openReturn(false); }
    private void openReturn(boolean overdueOnly) {
        if (!isStaff()) { showError(new SecurityException("Grāmatu atgriešanu drīkst veikt darbinieks.")); return; }
        showDialog(ReturnDialog);
        try {
            List<Izsniegums> loans = izsniegumsDAO.findLoans(overdueOnly, currentUser);
            DefaultTableModel model = (DefaultTableModel) ReturnTable.getModel();
            model.setRowCount(0);
            for (Izsniegums loan : loans) {
                model.addRow(new Object[]{loan.getIzsniegumaId(), loan.getLietotajs(),
                    loan.getEksemplars() == null || loan.getEksemplars().getGramata() == null
                        ? "" : loan.getEksemplars().getGramata().getNosaukums(), loan.getIzsniegts(), loan.getTermins()});
            }
            ReturnLabel.setText(overdueOnly ? "Kavētie izsniegumi" : "Aktīvie izsniegumi");
        } catch (Exception ex) { showError(ex); }
    }

    private void returnSelectedBook() {
        if (!isStaff()) return;
        int row = ReturnTable.getSelectedRow();
        if (row < 0) { showError(new IllegalArgumentException("Izvēlieties izsniegumu.")); return; }
        int modelRow = ReturnTable.convertRowIndexToModel(row);
        int id = ((Number) ReturnTable.getModel().getValueAt(modelRow, 0)).intValue();
        try {
            izsniegumsDAO.returnLoan(id, currentUser);
            showInfo("Grāmata veiksmīgi atgriezta.");
            openReturn(false);
        } catch (Exception ex) { showError(ex); }
    }

    private void openReservations() {
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

    private void updateReservationActionButtons() {
        boolean activeSelection = false;
        int selectedViewRow = ReservationTable.getSelectedRow();
        if (selectedViewRow >= 0) {
            int selectedModelRow = ReservationTable.convertRowIndexToModel(selectedViewRow);
            Object statusValue = ReservationTable.getModel().getValueAt(selectedModelRow, 4);
            activeSelection = statusValue != null && RezervacijaDAO.isActiveStatus(statusValue.toString());
        }
        boolean staff = isStaff();
        boolean reader = isReader();
        boolean ownsSelection = false;
        if (selectedViewRow >= 0) {
            int selectedModelRow = ReservationTable.convertRowIndexToModel(selectedViewRow);
            Object readerValue = ReservationTable.getModel().getValueAt(selectedModelRow, 1);
            ownsSelection = readerValue instanceof Lietotajs && currentUser != null
                    && ((Lietotajs) readerValue).getLietotajaId() == currentUser.getLietotajaId();
        }
        NewReservationButton.setEnabled(reader);
        CancelReservationButton.setEnabled(reader && activeSelection && ownsSelection);
        RunButton.setEnabled(staff && activeSelection);
    }

    private void createReservation() {
        if (!isReader()) { showError(new SecurityException("Rezervāciju var izveidot tikai lasītājs.")); return; }
        try {
            reservationBooks.clear();
            reservationBooks.addAll(rezervacijaDAO.findReservationBooks(currentUser));
            if (reservationBooks.isEmpty()) {
                showError(new IllegalStateException("Nav grāmatu rezervācijai."));
                return;
            }
            String[] names = reservationBooks.stream().map(Gramata::getNosaukums).toArray(String[]::new);
            int selected = JOptionPane.showOptionDialog(ReservationDialog, "Izvēlieties grāmatu rezervācijai:",
                    "Jauna rezervācija", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, names, names[0]);
            if (selected < 0) return;
            rezervacijaDAO.createReservation(reservationBooks.get(selected).getGramatasId(), currentUser);
            showInfo("Rezervācija izveidota.");
            openReservations();
        } catch (Exception ex) { showError(ex); }
    }

    private void cancelSelectedReservation() {
        if (!isReader()) { showError(new SecurityException("Rezervāciju var atcelt tās īpašnieks — lasītājs.")); return; }
        int row = ReservationTable.getSelectedRow();
        if (row < 0) { showError(new IllegalArgumentException("Izvēlieties rezervāciju.")); return; }
        int modelRow = ReservationTable.convertRowIndexToModel(row);
        int id = ((Number) ReservationTable.getModel().getValueAt(modelRow, 0)).intValue();
        try {
            rezervacijaDAO.cancelReservation(id, currentUser);
            showInfo("Rezervācija veiksmīgi atcelta.");
            openReservations();
        } catch (Exception ex) { showError(ex); }
    }

    private void completeSelectedReservation() {
        if (!isStaff()) { showError(new SecurityException("Rezervāciju var izpildīt bibliotekārs vai administrators.")); return; }
        int row = ReservationTable.getSelectedRow();
        if (row < 0) { showError(new IllegalArgumentException("Izvēlieties rezervāciju.")); return; }
        int modelRow = ReservationTable.convertRowIndexToModel(row);
        int id = ((Number) ReservationTable.getModel().getValueAt(modelRow, 0)).intValue();
        try {
            rezervacijaDAO.completeReservation(id, currentUser);
            showInfo("Rezervācija veiksmīgi izpildīta.");
            openReservations();
        } catch (Exception ex) { showError(ex); }
    }

    private void openNotifications() {
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

    private void markNotificationRead() {
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
            openNotifications();
        } catch (Exception ex) { showError(ex); }
    }

    private void exitApplication() {
        if (JOptionPane.showConfirmDialog(MainDialog, "Vai tiešām iziet no LibInfo?", "Iziet", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        hideAllDialogs(); dispose(); System.exit(0);
    }

    private void clearRegisterFields() { SurnameTextField.setText(""); NameTextField.setText(""); PasswordTextField1.setText(""); UsernameTextField1.setText(""); EMailTextField.setText(""); PasswordProveTextField.setText(""); }
    private void clearBookFields() {
        BookNameTextField.setText(""); BookAuthorTextField.setText(""); BookISBNTextField.setText("");
        BookCategoryTextField.setText(""); BookYearSpinner.setValue(java.time.LocalDate.now().getYear());
        BookDescriptionTextArea.setText(""); BookAvailabilityCheckBox.setSelected(true);
    }
    private void fillBookFields(Gramata book) {
        BookNameTextField.setText(nvl(book.getNosaukums())); BookAuthorTextField.setText(nvl(book.getAutors()));
        BookISBNTextField.setText(nvl(book.getIsbn())); BookCategoryTextField.setText(nvl(book.getKategorija()));
        BookYearSpinner.setValue(book.getIzdosanasGads() == null ? java.time.LocalDate.now().getYear() : book.getIzdosanasGads());
        BookDescriptionTextArea.setText(nvl(book.getApraksts())); BookAvailabilityCheckBox.setSelected(book.isPieejama());
    }
    private void clearUserFields() {
        UserNameTextField.setText(""); UserSurnameTextField.setText(""); UsernickTextField.setText("");
        UserPasswordTextField.setText(""); UserEMailTextField.setText(""); UserRoleComboBox.setSelectedItem("READER");
    }
    private void fillUserFields(Lietotajs u) {
        UserNameTextField.setText(nvl(u.getVards())); UserSurnameTextField.setText(nvl(u.getUzvards()));
        UsernickTextField.setText(nvl(u.getLietotajvards())); UserPasswordTextField.setText("");
        UserEMailTextField.setText(nvl(u.getEpasts()));
        UserRoleComboBox.setSelectedItem(u.getLoma() == null ? "READER" : u.getLoma().name());
    }
    private void returnToBookList() { BookEditDialog.setVisible(false); clearDialogFields(BookEditDialog); showDialog(BookListDialog); loadBooksFromUi(); }
    private void returnToUserList() { UserEditDialog.setVisible(false); clearDialogFields(UserEditDialog); showDialog(UserListFrameDialog); loadUsersFromUi(false); }

    private void clearDialogFields(JDialog dialog) {
        if (dialog == BookListDialog) {
            SearchTextField.setText("");
            if (FilterComboBox.getItemCount() > 0) FilterComboBox.setSelectedIndex(0);
            clearTable(BookTable);
        } else if (dialog == BookEditDialog) {
            clearBookFields();
        } else if (dialog == UserListFrameDialog) {
            UserSearchTextField.setText("");
            clearTable(UserTable);
        } else if (dialog == UserEditDialog) {
            clearUserFields();
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

    // ==================== UTILĪTAS (paliek kā metodes) ====================
    private void setResponsiveTableContent(JDialog dialog, Component header, javax.swing.JScrollPane table, Component actions) {
        Container content = dialog.getContentPane();
        content.removeAll();
        content.setLayout(new java.awt.BorderLayout(10, 10));
        content.setBackground(new Color(246, 248, 251));
        if (content instanceof javax.swing.JComponent) {
            ((javax.swing.JComponent) content).setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
        }
        content.add(header, java.awt.BorderLayout.NORTH);
        content.add(table, java.awt.BorderLayout.CENTER);
        content.add(actions, java.awt.BorderLayout.SOUTH);
    }

    private List<JDialog> allDialogs() {
        List<JDialog> result = new ArrayList<>();
        result.add(LoginDialog); result.add(RegisterDialog); result.add(MainDialog);
        result.add(BookListDialog); result.add(BookEditDialog); result.add(UserListFrameDialog);
        result.add(UserEditDialog); result.add(LoanDialog); result.add(ReturnDialog);
        result.add(ReservationDialog); result.add(NotificationDialog);
        return result;
    }

    private void hideAllDialogs() { for (JDialog d : allDialogs()) d.setVisible(false); }

    private void showDialog(JDialog dialog) {
        hideAllDialogs();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
        dialog.toFront();
    }

    private void showLogin() {
        clearRegisterFields();
        UsernameTextField.setText("");
        PasswordTextField.setText("");
        showDialog(LoginDialog);
        PasswordTextField.requestFocusInWindow();
    }

    private void showMain() { updateMainRoleAccess(); showDialog(MainDialog); }

    private void returnToMain(JDialog source) {
        source.setVisible(false);
        clearDialogFields(source);
        showMain();
    }

    private Component getMessageParent() {
        for (JDialog dialog : allDialogs()) {
            if (dialog.isVisible()) return dialog;
        }
        return MainDialog != null ? MainDialog : this;
    }

    private void showInfo(String message) {
        JOptionPane.showMessageDialog(getMessageParent(), message, "LibInfo", JOptionPane.INFORMATION_MESSAGE);
    }

    // ==================== KONSTRUKTORS ====================
    public LibInfoSaskarne() {
        initComponents();
        try {
            lietotajsDAO.initializeDatabase();
        } catch (Exception ex) {
            showError(ex);
        }
        UsernameTextField.setText("");
        PasswordTextField.setText("");
        showLogin();
    }

    // ==================== INIT COMPONENTS (ar AUTOGENERĒTU konfigurāciju) ====================
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

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
        MainDialog = new javax.swing.JDialog();
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
        BookMenu = new javax.swing.JMenu();
        UserMenu = new javax.swing.JMenu();
        LoanMenu = new javax.swing.JMenu();
        ReservationMenu = new javax.swing.JMenu();
        NotificationMenu = new javax.swing.JMenu();
        HelpMenu = new javax.swing.JMenu();
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
        BookAuthorTextField = new javax.swing.JTextField();
        BookNameTextField = new javax.swing.JTextField();
        BookISBNLabel = new javax.swing.JLabel();
        BookCategoryTextField = new javax.swing.JTextField();
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

        LoginDialog.setResizable(false);

        AppLabel.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        AppLabel.setText("LibInfo - Bibliotēkas sistēma");

        UsernameLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        UsernameLabel.setText("Lietotājvārds:");

        UsernameTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UsernameTextFieldActionPerformed(evt);
            }
        });

        PasswordLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        PasswordLabel.setText("Parole:");

        LoginButton.setBackground(new java.awt.Color(204, 204, 204));
        LoginButton.setText("Pieteikties");
        LoginButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LoginButtonActionPerformed(evt);
            }
        });

        RegisterButton.setBackground(new java.awt.Color(204, 204, 204));
        RegisterButton.setText("Reģistrēties");
        RegisterButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                RegisterButtonActionPerformed(evt);
            }
        });

        CloseButton.setBackground(new java.awt.Color(204, 204, 204));
        CloseButton.setText("Aizvērt");
        CloseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                CloseButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout LoginDialogLayout = new javax.swing.GroupLayout(LoginDialog.getContentPane());
        LoginDialog.getContentPane().setLayout(LoginDialogLayout);
        LoginDialogLayout.setHorizontalGroup(
            LoginDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, LoginDialogLayout.createSequentialGroup()
                .addContainerGap(49, Short.MAX_VALUE)
                .addGroup(LoginDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, LoginDialogLayout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(LoginButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(RegisterButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(CloseButton)
                        .addGap(73, 73, 73))
                    .addGroup(LoginDialogLayout.createSequentialGroup()
                        .addGap(8, 8, 8)
                        .addGroup(LoginDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(AppLabel)
                            .addGroup(LoginDialogLayout.createSequentialGroup()
                                .addComponent(UsernameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(UsernameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(43, 43, 43))))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, LoginDialogLayout.createSequentialGroup()
                .addGap(110, 110, 110)
                .addComponent(PasswordLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 52, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(PasswordTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 192, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        LoginDialogLayout.setVerticalGroup(
            LoginDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(LoginDialogLayout.createSequentialGroup()
                .addGap(56, 56, 56)
                .addComponent(AppLabel)
                .addGap(18, 18, 18)
                .addGroup(LoginDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(UsernameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(UsernameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(LoginDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(PasswordTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(PasswordLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(58, 58, 58)
                .addGroup(LoginDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(RegisterButton, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(CloseButton, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(LoginButton, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(52, Short.MAX_VALUE))
        );

        RegisterDialog.setResizable(false);

        RegisterLabel.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        RegisterLabel.setText("Jauna lietotāja reģistrācija");

        NameLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        NameLabel.setText("Vārds:");

        SurnameLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        SurnameLabel.setText("Uzvārds:");

        UsernameLabel1.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        UsernameLabel1.setText("Lietotājvārds:");

        PasswordLabel1.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        PasswordLabel1.setText("Parole:");

        PasswordProveLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        PasswordProveLabel.setText("Apstiprināt paroli:");

        EMailLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        EMailLabel.setText("E-pasts:");

        RegisterButton1.setBackground(new java.awt.Color(204, 204, 204));
        RegisterButton1.setText("Reģistrēties");
        RegisterButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                RegisterButton1ActionPerformed(evt);
            }
        });

        CancelButton.setBackground(new java.awt.Color(204, 204, 204));
        CancelButton.setText("Atcelt");
        CancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                CancelButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout RegisterDialogLayout = new javax.swing.GroupLayout(RegisterDialog.getContentPane());
        RegisterDialog.getContentPane().setLayout(RegisterDialogLayout);
        RegisterDialogLayout.setHorizontalGroup(
            RegisterDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(RegisterDialogLayout.createSequentialGroup()
                .addContainerGap(70, Short.MAX_VALUE)
                .addGroup(RegisterDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, RegisterDialogLayout.createSequentialGroup()
                        .addGroup(RegisterDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(RegisterDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(RegisterLabel, javax.swing.GroupLayout.Alignment.TRAILING)
                                .addGroup(RegisterDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, RegisterDialogLayout.createSequentialGroup()
                                        .addComponent(SurnameLabel)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(SurnameTextField))
                                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, RegisterDialogLayout.createSequentialGroup()
                                        .addComponent(NameLabel)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(NameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 212, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                .addGroup(RegisterDialogLayout.createSequentialGroup()
                                    .addComponent(UsernameLabel1)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                    .addComponent(UsernameTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, 174, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGroup(RegisterDialogLayout.createSequentialGroup()
                                    .addComponent(PasswordLabel1)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                    .addComponent(PasswordTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, 212, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGroup(RegisterDialogLayout.createSequentialGroup()
                                    .addComponent(PasswordProveLabel)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                    .addComponent(PasswordProveTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 147, javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addGroup(RegisterDialogLayout.createSequentialGroup()
                                .addComponent(EMailLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(EMailTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 218, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(25, 25, 25)))
                        .addGap(51, 51, 51))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, RegisterDialogLayout.createSequentialGroup()
                        .addComponent(RegisterButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(CancelButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(117, 117, 117))))
        );
        RegisterDialogLayout.setVerticalGroup(
            RegisterDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(RegisterDialogLayout.createSequentialGroup()
                .addGap(41, 41, 41)
                .addComponent(RegisterLabel)
                .addGap(18, 18, 18)
                .addGroup(RegisterDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(NameLabel)
                    .addComponent(NameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(RegisterDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(SurnameLabel)
                    .addComponent(SurnameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(RegisterDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(UsernameLabel1)
                    .addComponent(UsernameTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(RegisterDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(PasswordLabel1)
                    .addComponent(PasswordTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(RegisterDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(PasswordProveLabel)
                    .addComponent(PasswordProveTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(RegisterDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(EMailTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(EMailLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 10, Short.MAX_VALUE)
                .addGroup(RegisterDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(CancelButton, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(RegisterButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        MainDialog.setResizable(false);
        MainDialog.setSize(new java.awt.Dimension(470, 220));

        BookListButton.setBackground(new java.awt.Color(204, 204, 204));
        BookListButton.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        BookListButton.setText("Grāmatu saraksts");
        BookListButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                BookListButtonActionPerformed(evt);
            }
        });

        BookAddButton.setBackground(new java.awt.Color(204, 204, 204));
        BookAddButton.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        BookAddButton.setText("Pievienot grāmatu");

        UserListButton.setBackground(new java.awt.Color(204, 204, 204));
        UserListButton.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        UserListButton.setText("Lietotāju saraksts");

        BookLoanButton.setBackground(new java.awt.Color(204, 204, 204));
        BookLoanButton.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        BookLoanButton.setText("Izsniegt grāmatu");

        ReservationButton.setBackground(new java.awt.Color(204, 204, 204));
        ReservationButton.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        ReservationButton.setText("Rezervācijas");

        BookReturnButton.setBackground(new java.awt.Color(204, 204, 204));
        BookReturnButton.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        BookReturnButton.setText("Atgriezt grāmatu");

        NotificationsButton.setBackground(new java.awt.Color(204, 204, 204));
        NotificationsButton.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        NotificationsButton.setText("Paziņojumi");
        NotificationsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                NotificationsButtonActionPerformed(evt);
            }
        });

        CloseButton1.setBackground(new java.awt.Color(204, 204, 204));
        CloseButton1.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        CloseButton1.setText("Iziet");

        FileMenu.setText("Fails");
        ActionsMenuBar.add(FileMenu);

        BookMenu.setText("Grāmatas");
        ActionsMenuBar.add(BookMenu);

        UserMenu.setText("Lietotāji");
        ActionsMenuBar.add(UserMenu);

        LoanMenu.setText("Izsniegums");
        ActionsMenuBar.add(LoanMenu);

        ReservationMenu.setText("Rezervācijas");
        ActionsMenuBar.add(ReservationMenu);

        NotificationMenu.setText("Paziņojumi");
        ActionsMenuBar.add(NotificationMenu);

        HelpMenu.setText("Palīdzība");
        ActionsMenuBar.add(HelpMenu);

        MainDialog.setJMenuBar(ActionsMenuBar);

        javax.swing.GroupLayout MainDialogLayout = new javax.swing.GroupLayout(MainDialog.getContentPane());
        MainDialog.getContentPane().setLayout(MainDialogLayout);
        MainDialogLayout.setHorizontalGroup(
            MainDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(MainDialogLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(MainDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(MainDialogLayout.createSequentialGroup()
                        .addComponent(BookListButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(BookAddButton))
                    .addGroup(MainDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addGroup(javax.swing.GroupLayout.Alignment.LEADING, MainDialogLayout.createSequentialGroup()
                            .addComponent(UserListButton)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                            .addComponent(BookLoanButton))
                        .addGroup(MainDialogLayout.createSequentialGroup()
                            .addGroup(MainDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(ReservationButton)
                                .addComponent(NotificationsButton))
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(MainDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(CloseButton1)
                                .addComponent(BookReturnButton)))))
                .addContainerGap(179, Short.MAX_VALUE))
        );
        MainDialogLayout.setVerticalGroup(
            MainDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(MainDialogLayout.createSequentialGroup()
                .addGap(18, 18, 18)
                .addGroup(MainDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(BookAddButton)
                    .addComponent(BookListButton))
                .addGap(18, 18, 18)
                .addGroup(MainDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(BookLoanButton)
                    .addComponent(UserListButton))
                .addGap(18, 18, 18)
                .addGroup(MainDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(BookReturnButton)
                    .addComponent(ReservationButton))
                .addGap(18, 18, 18)
                .addGroup(MainDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(NotificationsButton)
                    .addComponent(CloseButton1))
                .addContainerGap(17, Short.MAX_VALUE))
        );

        BookListDialog.setResizable(true);
        BookListDialog.setSize(new java.awt.Dimension(900, 600));

        BookTable.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        BookTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null}
            },
            new String [] {
                "ID", "Nosaukums", "Autors", "ISBN", "Kategorija", "Gads", "Pieejamība"
            }
        ));
        BookScrollPane.setViewportView(BookTable);
        if (BookTable.getColumnModel().getColumnCount() > 0) {
            BookTable.getColumnModel().getColumn(3).setResizable(false);
        }

        AddButton.setBackground(new java.awt.Color(204, 204, 204));
        AddButton.setText("Pievienot");
        AddButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                AddButtonActionPerformed(evt);
            }
        });

        EditButton.setBackground(new java.awt.Color(204, 204, 204));
        EditButton.setText("Rediģēt");
        EditButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                EditButtonActionPerformed(evt);
            }
        });

        SearchButton.setBackground(new java.awt.Color(204, 204, 204));
        SearchButton.setText("Meklēt");
        SearchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SearchButtonActionPerformed(evt);
            }
        });

        DeleteButton.setBackground(new java.awt.Color(204, 204, 204));
        DeleteButton.setText("Dzēst");

        FilterLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        FilterLabel.setText("Filtrs:");

        FilterComboBox.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        FilterComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        FilterComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                FilterComboBoxActionPerformed(evt);
            }
        });

        CloseButton2.setBackground(new java.awt.Color(204, 204, 204));
        CloseButton2.setText("Aizvērt");
        CloseButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                CloseButton2ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout BookListDialogLayout = new javax.swing.GroupLayout(BookListDialog.getContentPane());
        BookListDialog.getContentPane().setLayout(BookListDialogLayout);
        BookListDialogLayout.setHorizontalGroup(
            BookListDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(BookListDialogLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(BookScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 888, Short.MAX_VALUE)
                .addContainerGap())
            .addGroup(BookListDialogLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(SearchTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(SearchButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(FilterLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 43, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(FilterComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(71, 71, 71))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, BookListDialogLayout.createSequentialGroup()
                .addGap(9, 9, 9)
                .addComponent(AddButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(EditButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(DeleteButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(CloseButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(24, 24, 24))
        );
        BookListDialogLayout.setVerticalGroup(
            BookListDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(BookListDialogLayout.createSequentialGroup()
                .addGap(14, 14, 14)
                .addGroup(BookListDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(SearchTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SearchButton, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(FilterLabel)
                    .addComponent(FilterComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(30, 30, 30)
                .addComponent(BookScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 387, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 44, Short.MAX_VALUE)
                .addGroup(BookListDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(CloseButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(DeleteButton, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(EditButton, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(AddButton, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(35, 35, 35))
        );

        BookNameLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        BookNameLabel.setText("Vārds:");

        SaveBookButton.setText("Saglabāt");
        SaveBookButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SaveBookButtonActionPerformed(evt);
            }
        });

        BookAuthorLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        BookAuthorLabel.setText("Autors:");

        BookNameTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                BookNameTextFieldActionPerformed(evt);
            }
        });

        BookISBNLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        BookISBNLabel.setText("ISBN:");

        BookCategoryTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                BookCategoryTextFieldActionPerformed(evt);
            }
        });

        BookCategoryLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        BookCategoryLabel.setText("Kategorija:");

        BookISBNTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                BookISBNTextFieldActionPerformed(evt);
            }
        });

        BookYearLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        BookYearLabel.setText("Izdošanas gads:");

        ReturnButton1.setText("Atcelt");
        ReturnButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ReturnButton1ActionPerformed(evt);
            }
        });

        ISBNSearchButton.setBackground(new java.awt.Color(204, 204, 204));
        ISBNSearchButton.setText("Meklēt pēc ISBN");
        ISBNSearchButton.setMaximumSize(new java.awt.Dimension(100, 45));
        ISBNSearchButton.setMinimumSize(new java.awt.Dimension(100, 45));
        ISBNSearchButton.setPreferredSize(new java.awt.Dimension(100, 45));
        ISBNSearchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ISBNSearchButtonActionPerformed(evt);
            }
        });

        BookDescriptionLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        BookDescriptionLabel.setText("Apraksts:");

        BookDescriptionTextArea.setColumns(20);
        BookDescriptionTextArea.setRows(5);
        jScrollPane3.setViewportView(BookDescriptionTextArea);

        BookAvailabilityLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        BookAvailabilityLabel.setText("Pieejamība:");

        BookAvailabilityCheckBox.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        BookAvailabilityCheckBox.setText("jCheckBox1");
        BookAvailabilityCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                BookAvailabilityCheckBoxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout BookEditDialogLayout = new javax.swing.GroupLayout(BookEditDialog.getContentPane());
        BookEditDialog.getContentPane().setLayout(BookEditDialogLayout);
        BookEditDialogLayout.setHorizontalGroup(
            BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(BookEditDialogLayout.createSequentialGroup()
                .addGap(134, 134, 134)
                .addComponent(SaveBookButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ReturnButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, BookEditDialogLayout.createSequentialGroup()
                .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(BookEditDialogLayout.createSequentialGroup()
                        .addGap(102, 102, 102)
                        .addComponent(BookNameLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(BookNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 212, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(BookEditDialogLayout.createSequentialGroup()
                        .addContainerGap(96, Short.MAX_VALUE)
                        .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(BookEditDialogLayout.createSequentialGroup()
                                .addComponent(BookDescriptionLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                                .addGap(49, 49, 49))
                            .addGroup(BookEditDialogLayout.createSequentialGroup()
                                .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(BookEditDialogLayout.createSequentialGroup()
                                        .addComponent(BookAvailabilityLabel)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(BookAvailabilityCheckBox))
                                    .addGroup(BookEditDialogLayout.createSequentialGroup()
                                        .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                            .addGroup(BookEditDialogLayout.createSequentialGroup()
                                                .addComponent(BookYearLabel)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(BookYearSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE))
                                            .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                                .addGroup(BookEditDialogLayout.createSequentialGroup()
                                                    .addComponent(BookCategoryLabel)
                                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                    .addComponent(BookCategoryTextField))
                                                .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                    .addGroup(BookEditDialogLayout.createSequentialGroup()
                                                        .addComponent(BookAuthorLabel)
                                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                        .addComponent(BookAuthorTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 210, javax.swing.GroupLayout.PREFERRED_SIZE))
                                                    .addGroup(BookEditDialogLayout.createSequentialGroup()
                                                        .addComponent(BookISBNLabel)
                                                        .addGap(18, 18, 18)
                                                        .addComponent(BookISBNTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 212, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                                        .addGap(12, 12, 12)
                                        .addComponent(ISBNSearchButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 8, Short.MAX_VALUE)))))
                .addGap(20, 20, 20))
        );
        BookEditDialogLayout.setVerticalGroup(
            BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(BookEditDialogLayout.createSequentialGroup()
                .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(BookEditDialogLayout.createSequentialGroup()
                        .addGap(106, 106, 106)
                        .addComponent(BookNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, BookEditDialogLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(BookNameLabel)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(BookAuthorLabel)
                    .addComponent(BookAuthorTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(BookISBNLabel)
                    .addComponent(BookISBNTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ISBNSearchButton, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(BookCategoryLabel)
                    .addComponent(BookCategoryTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(21, 21, 21)
                .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(BookYearSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(BookYearLabel))
                .addGap(18, 18, 18)
                .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 95, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(BookDescriptionLabel))
                .addGap(18, 18, 18)
                .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(BookAvailabilityLabel)
                    .addComponent(BookAvailabilityCheckBox))
                .addGap(18, 18, 18)
                .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(SaveBookButton, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ReturnButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(92, Short.MAX_VALUE))
        );

        UserTable.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        UserTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null, null, null},
                {null, null, null, null, null, null},
                {null, null, null, null, null, null},
                {null, null, null, null, null, null}
            },
            new String [] {
                "ID", "Vārds", "Uzvārds", "Lietotājvārds", "E-pasts", "Loma"
            }
        ));
        UserScrollPane.setViewportView(UserTable);
        if (UserTable.getColumnModel().getColumnCount() > 0) {
            UserTable.getColumnModel().getColumn(3).setResizable(false);
        }

        AddUserButton.setBackground(new java.awt.Color(204, 204, 204));
        AddUserButton.setText("Pievienot");
        AddUserButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                AddUserButtonActionPerformed(evt);
            }
        });

        ReturnButton2.setBackground(new java.awt.Color(204, 204, 204));
        ReturnButton2.setText("Atcelt");
        ReturnButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ReturnButton2ActionPerformed(evt);
            }
        });

        EditUserButton.setBackground(new java.awt.Color(204, 204, 204));
        EditUserButton.setText("Rediģēt");
        EditUserButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                EditUserButtonActionPerformed(evt);
            }
        });

        UserSearchTextField.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N

        UserSearchButton.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        UserSearchButton.setText("Meklēt:");
        UserSearchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UserSearchButtonActionPerformed(evt);
            }
        });

        DeleteUserButton.setBackground(new java.awt.Color(204, 204, 204));
        DeleteUserButton.setText("Dzēst");
        DeleteUserButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                DeleteUserButtonActionPerformed(evt);
            }
        });

        EditUserRoleButton.setBackground(new java.awt.Color(204, 204, 204));
        EditUserRoleButton.setText("Mainīt lomu");
        EditUserRoleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                EditUserRoleButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout UserListFrameDialogLayout = new javax.swing.GroupLayout(UserListFrameDialog.getContentPane());
        UserListFrameDialog.getContentPane().setLayout(UserListFrameDialogLayout);
        UserListFrameDialogLayout.setHorizontalGroup(
            UserListFrameDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(UserListFrameDialogLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(UserListFrameDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(UserScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 888, Short.MAX_VALUE)
                    .addGroup(UserListFrameDialogLayout.createSequentialGroup()
                        .addComponent(UserSearchButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(UserSearchTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 103, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 698, Short.MAX_VALUE)))
                .addContainerGap())
            .addGroup(UserListFrameDialogLayout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addComponent(AddUserButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(EditUserButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(12, 12, 12)
                .addComponent(DeleteUserButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(EditUserRoleButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(ReturnButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(21, 21, 21))
        );
        UserListFrameDialogLayout.setVerticalGroup(
            UserListFrameDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(UserListFrameDialogLayout.createSequentialGroup()
                .addGap(14, 14, 14)
                .addGroup(UserListFrameDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(UserSearchTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(UserSearchButton))
                .addGap(30, 30, 30)
                .addComponent(UserScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 418, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(31, 31, 31)
                .addGroup(UserListFrameDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(DeleteUserButton, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(EditUserButton, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(AddUserButton, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(EditUserRoleButton, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ReturnButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(35, Short.MAX_VALUE))
        );

        UserEditDialog.setSize(new java.awt.Dimension(450, 450));

        UserNameLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        UserNameLabel.setText("Vārds:");

        ReturnButton3.setBackground(new java.awt.Color(204, 204, 204));
        ReturnButton3.setText("Atcelt");
        ReturnButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ReturnButton3ActionPerformed(evt);
            }
        });

        UserSurnameLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        UserSurnameLabel.setText("Uzvārds:");

        UsernickLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        UsernickLabel.setText("Lietotājvārds:");

        UserPasswordTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UserPasswordTextFieldActionPerformed(evt);
            }
        });

        UserPasswordLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        UserPasswordLabel.setText("Parole:");

        UserEMailLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        UserEMailLabel.setText("E-pasts:");

        UserRoleLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        UserRoleLabel.setText("Loma:");

        UserRoleComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        UserRoleComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UserRoleComboBoxActionPerformed(evt);
            }
        });

        SaveUserButton.setBackground(new java.awt.Color(204, 204, 204));
        SaveUserButton.setText("Saglabāt");
        SaveUserButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SaveUserButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout UserEditDialogLayout = new javax.swing.GroupLayout(UserEditDialog.getContentPane());
        UserEditDialog.getContentPane().setLayout(UserEditDialogLayout);
        UserEditDialogLayout.setHorizontalGroup(
            UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, UserEditDialogLayout.createSequentialGroup()
                .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(UserEditDialogLayout.createSequentialGroup()
                        .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(UserEditDialogLayout.createSequentialGroup()
                                .addGap(121, 121, 121)
                                .addComponent(UserPasswordLabel))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(UserRoleLabel)
                                .addComponent(UserEMailLabel)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(UserPasswordTextField)
                            .addComponent(UserEMailTextField)
                            .addComponent(UserRoleComboBox, 0, 218, Short.MAX_VALUE)))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, UserEditDialogLayout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, UserEditDialogLayout.createSequentialGroup()
                                .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(UserNameLabel)
                                    .addComponent(UserSurnameLabel))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED))
                            .addGroup(UserEditDialogLayout.createSequentialGroup()
                                .addComponent(UsernickLabel)
                                .addGap(13, 13, 13)))
                        .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(UsernickTextField)
                            .addComponent(UserSurnameTextField)
                            .addComponent(UserNameTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 212, Short.MAX_VALUE))))
                .addGap(63, 63, 63))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, UserEditDialogLayout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(ReturnButton3, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(114, 114, 114))
            .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(UserEditDialogLayout.createSequentialGroup()
                    .addGap(123, 123, 123)
                    .addComponent(SaveUserButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap(227, Short.MAX_VALUE)))
        );
        UserEditDialogLayout.setVerticalGroup(
            UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(UserEditDialogLayout.createSequentialGroup()
                .addGap(106, 106, 106)
                .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(UserNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(UserNameLabel, javax.swing.GroupLayout.Alignment.TRAILING))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(UserSurnameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(UserSurnameLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(UsernickLabel)
                    .addComponent(UsernickTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(UserPasswordLabel)
                    .addComponent(UserPasswordTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(UserEMailLabel)
                    .addComponent(UserEMailTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(UserRoleComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(UserRoleLabel))
                .addGap(40, 40, 40)
                .addComponent(ReturnButton3, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(85, Short.MAX_VALUE))
            .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, UserEditDialogLayout.createSequentialGroup()
                    .addContainerGap(319, Short.MAX_VALUE)
                    .addComponent(SaveUserButton, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGap(86, 86, 86)))
        );

        ReaderLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        ReaderLabel.setText("Lasītājs:");

        BookComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        BookLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        BookLabel.setText("Grāmata:");

        ReaderComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        LoanDateLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        LoanDateLabel.setText("Izsniegšanas datums:");

        LoanTimeLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        LoanTimeLabel.setText("Termiņš:");

        LoanButton.setBackground(new java.awt.Color(204, 204, 204));
        LoanButton.setText("Izsniegt");

        ReturnButton4.setBackground(new java.awt.Color(204, 204, 204));
        ReturnButton4.setText("Atcelt");

        javax.swing.GroupLayout LoanDialogLayout = new javax.swing.GroupLayout(LoanDialog.getContentPane());
        LoanDialog.getContentPane().setLayout(LoanDialogLayout);
        LoanDialogLayout.setHorizontalGroup(
            LoanDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(LoanDialogLayout.createSequentialGroup()
                .addGroup(LoanDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(LoanDialogLayout.createSequentialGroup()
                        .addGap(203, 203, 203)
                        .addComponent(LoanTimeLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(LoanTimeFormattedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(LoanDialogLayout.createSequentialGroup()
                        .addGap(167, 167, 167)
                        .addComponent(LoanButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(ReturnButton4, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(LoanDialogLayout.createSequentialGroup()
                        .addGap(166, 166, 166)
                        .addComponent(ReaderLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(ReaderComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(LoanDialogLayout.createSequentialGroup()
                        .addGap(113, 113, 113)
                        .addGroup(LoanDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(BookLabel)
                            .addComponent(LoanDateLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LoanDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(LoanDateTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(BookComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap(149, Short.MAX_VALUE))
        );
        LoanDialogLayout.setVerticalGroup(
            LoanDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(LoanDialogLayout.createSequentialGroup()
                .addGap(124, 124, 124)
                .addGroup(LoanDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ReaderLabel)
                    .addComponent(ReaderComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(LoanDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(BookLabel)
                    .addComponent(BookComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(LoanDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(LoanDateLabel)
                    .addComponent(LoanDateTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(LoanDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(LoanTimeLabel)
                    .addComponent(LoanTimeFormattedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(26, 26, 26)
                .addGroup(LoanDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(LoanButton, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ReturnButton4, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(71, Short.MAX_VALUE))
        );

        ReturnLabel.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        ReturnLabel.setText("Aktīvie izsniegumi");

        ReturnTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null}
            },
            new String [] {
                "ID", "Lasītājs", "Grāmata", "Izsniegts", "Termiņš"
            }
        ));
        jScrollPane1.setViewportView(ReturnTable);

        ReturnButton.setBackground(new java.awt.Color(204, 204, 204));
        ReturnButton.setText("Atgriezt");
        ReturnButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ReturnButtonActionPerformed(evt);
            }
        });

        jButton19.setBackground(new java.awt.Color(204, 204, 204));
        jButton19.setText("Atcelt");

        javax.swing.GroupLayout ReturnDialogLayout = new javax.swing.GroupLayout(ReturnDialog.getContentPane());
        ReturnDialog.getContentPane().setLayout(ReturnDialogLayout);
        ReturnDialogLayout.setHorizontalGroup(
            ReturnDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ReturnDialogLayout.createSequentialGroup()
                .addGroup(ReturnDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(ReturnDialogLayout.createSequentialGroup()
                        .addGap(25, 25, 25)
                        .addGroup(ReturnDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(ReturnLabel)
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 555, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(ReturnDialogLayout.createSequentialGroup()
                        .addGap(207, 207, 207)
                        .addComponent(ReturnButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jButton19, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(20, Short.MAX_VALUE))
        );
        ReturnDialogLayout.setVerticalGroup(
            ReturnDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ReturnDialogLayout.createSequentialGroup()
                .addGap(22, 22, 22)
                .addComponent(ReturnLabel)
                .addGap(27, 27, 27)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 223, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 32, Short.MAX_VALUE)
                .addGroup(ReturnDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton19, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ReturnButton, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(31, 31, 31))
        );

        ReservationLabel.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        ReservationLabel.setText("Rezervāciju saraksts");

        ReservationTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null}
            },
            new String [] {
                "ID", "Lasītājs", "Grāmata", "Datums", "Statuss"
            }
        ));
        ReservationScrollPane.setViewportView(ReservationTable);
        if (ReservationTable.getColumnModel().getColumnCount() > 0) {
            ReservationTable.getColumnModel().getColumn(1).setResizable(false);
            ReservationTable.getColumnModel().getColumn(2).setResizable(false);
            ReservationTable.getColumnModel().getColumn(3).setResizable(false);
        }

        NewReservationButton.setBackground(new java.awt.Color(204, 204, 204));
        NewReservationButton.setText("Jauna rezervācija");
        NewReservationButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                NewReservationButtonActionPerformed(evt);
            }
        });

        CancelReservationButton.setBackground(new java.awt.Color(204, 204, 204));
        CancelReservationButton.setText("Atcelt rezervāciju");
        CancelReservationButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                CancelReservationButtonActionPerformed(evt);
            }
        });

        CloseButton3.setBackground(new java.awt.Color(204, 204, 204));
        CloseButton3.setText("Aizvērt");

        RunButton.setBackground(new java.awt.Color(204, 204, 204));
        RunButton.setText("Izpildīt");
        RunButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                RunButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout ReservationDialogLayout = new javax.swing.GroupLayout(ReservationDialog.getContentPane());
        ReservationDialog.getContentPane().setLayout(ReservationDialogLayout);
        ReservationDialogLayout.setHorizontalGroup(
            ReservationDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ReservationDialogLayout.createSequentialGroup()
                .addGap(25, 25, 25)
                .addGroup(ReservationDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(ReservationDialogLayout.createSequentialGroup()
                        .addComponent(NewReservationButton, javax.swing.GroupLayout.PREFERRED_SIZE, 122, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(CancelReservationButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(RunButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(257, 257, 257)
                        .addComponent(CloseButton3, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(ReservationDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(ReservationLabel)
                        .addComponent(ReservationScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 742, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(33, Short.MAX_VALUE))
        );
        ReservationDialogLayout.setVerticalGroup(
            ReservationDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ReservationDialogLayout.createSequentialGroup()
                .addGap(22, 22, 22)
                .addComponent(ReservationLabel)
                .addGap(27, 27, 27)
                .addComponent(ReservationScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 365, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(ReservationDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(NewReservationButton, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(CancelReservationButton, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(RunButton, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(CloseButton3, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(15, Short.MAX_VALUE))
        );

        NotificationLabel.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        NotificationLabel.setText("Mani paziņojumi");

        NotificationTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null}
            },
            new String [] {
                "ID", "Veids", "Teksts", "Datums", "Izlasīts"
            }
        ));
        NotificationScrollPane.setViewportView(NotificationTable);
        if (NotificationTable.getColumnModel().getColumnCount() > 0) {
            NotificationTable.getColumnModel().getColumn(3).setResizable(false);
        }

        ReadButton.setBackground(new java.awt.Color(204, 204, 204));
        ReadButton.setText("Atzīmēt kā izlasītu");

        CloseButton4.setBackground(new java.awt.Color(204, 204, 204));
        CloseButton4.setText("Aizvērt");

        javax.swing.GroupLayout NotificationDialogLayout = new javax.swing.GroupLayout(NotificationDialog.getContentPane());
        NotificationDialog.getContentPane().setLayout(NotificationDialogLayout);
        NotificationDialogLayout.setHorizontalGroup(
            NotificationDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(NotificationDialogLayout.createSequentialGroup()
                .addGroup(NotificationDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(NotificationDialogLayout.createSequentialGroup()
                        .addGap(25, 25, 25)
                        .addGroup(NotificationDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(NotificationLabel)
                            .addComponent(NotificationScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 652, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(NotificationDialogLayout.createSequentialGroup()
                        .addGap(26, 26, 26)
                        .addComponent(ReadButton, javax.swing.GroupLayout.PREFERRED_SIZE, 142, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(CloseButton4, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(23, Short.MAX_VALUE))
        );
        NotificationDialogLayout.setVerticalGroup(
            NotificationDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(NotificationDialogLayout.createSequentialGroup()
                .addGap(22, 22, 22)
                .addComponent(NotificationLabel)
                .addGap(27, 27, 27)
                .addComponent(NotificationScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 287, Short.MAX_VALUE)
                .addGap(18, 18, 18)
                .addGroup(NotificationDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(CloseButton4, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ReadButton, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(31, 31, 31))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 429, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 316, Short.MAX_VALUE)
        );

        // ============================================================
        // AUTOGENERĒTĀ KONFIGURĀCIJA (dialogs, listeners, layouts, polish)
        // ============================================================
        setVisible(false);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // --- Dialogu virsraksti un aizvēršanas uzvedība ---
        LoginDialog.setTitle("LibInfo - Pieteikšanās");
        LoginDialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { dispose(); System.exit(0); } });
        RegisterDialog.setTitle("LibInfo - Reģistrācija");
        RegisterDialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { showLogin(); } });
        MainDialog.setTitle("LibInfo - Galvenā izvēlne");
        MainDialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { dispose(); System.exit(0); } });
        BookListDialog.setTitle("LibInfo - Grāmatu saraksts");
        BookListDialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { returnToMain(BookListDialog); } });
        BookEditDialog.setTitle("LibInfo - Grāmata");
        BookEditDialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { returnToBookList(); } });
        UserListFrameDialog.setTitle("LibInfo - Lietotāji");
        UserListFrameDialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { returnToMain(UserListFrameDialog); } });
        UserEditDialog.setTitle("LibInfo - Lietotājs");
        UserEditDialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { returnToUserList(); } });
        LoanDialog.setTitle("LibInfo - Izsniegt grāmatu");
        LoanDialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { returnToMain(LoanDialog); } });
        ReturnDialog.setTitle("LibInfo - Atgriezt grāmatu");
        ReturnDialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { returnToMain(ReturnDialog); } });
        ReservationDialog.setTitle("LibInfo - Rezervācijas");
        ReservationDialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { returnToMain(ReservationDialog); } });
        NotificationDialog.setTitle("LibInfo - Paziņojumi");
        NotificationDialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { returnToMain(NotificationDialog); } });
        for (JDialog d : allDialogs()) {
            d.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
            d.setModal(false);
        }

        // Sarakstu logi ir pārdimensionējami
        BookListDialog.setResizable(true);
        UserListFrameDialog.setResizable(true);
        ReturnDialog.setResizable(true);
        ReservationDialog.setResizable(true);
        NotificationDialog.setResizable(true);

        // --- Sākotnējie komponentu iestatījumi ---
        int currentYear = java.time.LocalDate.now().getYear();
        BookYearSpinner.setModel(new javax.swing.SpinnerNumberModel(currentYear, 1800, currentYear + 1, 1));
        javax.swing.JSpinner.NumberEditor yearEditor = new javax.swing.JSpinner.NumberEditor(BookYearSpinner, "0");
        yearEditor.getTextField().setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        BookYearSpinner.setEditor(yearEditor);
        BookAvailabilityLabel.setText("Sākotnējais eksemplārs:");
        BookAvailabilityCheckBox.setText("Izveidot pieejamu eksemplāru");
        BookAvailabilityCheckBox.setToolTipText("Šī izvēle attiecas uz jaunu grāmatu. Esošo eksemplāru pieejamību nosaka izsniegumi.");
        LoanDateTextField.setEditable(false);
        LoanDateTextField.setToolTipText("Sistēma automātiski reģistrē izsniegšanas datumu.");
        try {
            javax.swing.text.MaskFormatter loanMask = new javax.swing.text.MaskFormatter("####-##-##");
            loanMask.setPlaceholderCharacter('_');
            LoanTimeFormattedTextField.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(loanMask));
        } catch (java.text.ParseException ex) { /* nevar notikt ar fiksētu masku */ }

        // --- Pogas, kurām nav NetBeans actionPerformed ---
        BookAddButton.addActionListener(e -> openNewBook());
        BookLoanButton.addActionListener(e -> openLoan());
        BookReturnButton.addActionListener(e -> openReturn());
        UserListButton.addActionListener(e -> openUserList());
        ReservationButton.addActionListener(e -> openReservations());
        CloseButton1.addActionListener(e -> exitApplication());
        DeleteButton.addActionListener(e -> deleteSelectedBook());
        LoanButton.addActionListener(e -> issueBook());
        ReturnButton4.addActionListener(e -> returnToMain(LoanDialog));
        jButton19.addActionListener(e -> returnToMain(ReturnDialog));
        CloseButton3.addActionListener(e -> returnToMain(ReservationDialog));
        ReadButton.addActionListener(e -> markNotificationRead());
        CloseButton4.addActionListener(e -> returnToMain(NotificationDialog));

        // --- Rezervāciju izvēles izsekošana ---
        ReservationTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateReservationActionButtons();
        });
        ReservationTable.setToolTipText("Lai atceltu vai izpildītu rezervāciju, vispirms atlasiet aktīvo ierakstu.");
        CancelReservationButton.setToolTipText("Atcelt atlasīto aktīvo rezervāciju.");
        RunButton.setToolTipText("Atzīmēt atlasīto aktīvo rezervāciju kā izpildītu.");

        // --- Izvēlnes ---
        FileMenu.removeAll(); BookMenu.removeAll(); UserMenu.removeAll(); LoanMenu.removeAll();
        ReservationMenu.removeAll(); NotificationMenu.removeAll(); HelpMenu.removeAll();
        javax.swing.JMenuItem exit = new javax.swing.JMenuItem("Iziet");
        exit.addActionListener(e -> exitApplication());
        FileMenu.add(exit);
        javax.swing.JMenuItem books = new javax.swing.JMenuItem("Saraksts");
        books.addActionListener(e -> openBookList());
        menuAddBookItem = new javax.swing.JMenuItem("Pievienot");
        menuAddBookItem.addActionListener(e -> openNewBook());
        javax.swing.JMenuItem searchBook = new javax.swing.JMenuItem("Meklēt");
        searchBook.addActionListener(e -> openBookList());
        BookMenu.add(books); BookMenu.add(menuAddBookItem); BookMenu.add(searchBook);
        javax.swing.JMenuItem users = new javax.swing.JMenuItem("Saraksts");
        users.addActionListener(e -> openUserList());
        menuAddUserItem = new javax.swing.JMenuItem("Pievienot");
        menuAddUserItem.addActionListener(e -> openNewUser());
        UserMenu.add(users); UserMenu.add(menuAddUserItem);
        javax.swing.JMenuItem issue = new javax.swing.JMenuItem("Izsniegt");
        issue.addActionListener(e -> openLoan());
        javax.swing.JMenuItem ret = new javax.swing.JMenuItem("Atgriezt");
        ret.addActionListener(e -> openReturn());
        javax.swing.JMenuItem overdue = new javax.swing.JMenuItem("Kavētie");
        overdue.addActionListener(e -> openReturn(true));
        LoanMenu.add(issue); LoanMenu.add(ret); LoanMenu.add(overdue);
        javax.swing.JMenuItem reservations = new javax.swing.JMenuItem("Saraksts");
        reservations.addActionListener(e -> openReservations());
        ReservationMenu.add(reservations);
        javax.swing.JMenuItem notifications = new javax.swing.JMenuItem("Mani paziņojumi");
        notifications.addActionListener(e -> openNotifications());
        NotificationMenu.add(notifications);
        javax.swing.JMenuItem about = new javax.swing.JMenuItem("Par programmu");
        about.addActionListener(e -> JOptionPane.showMessageDialog(MainDialog,
                "LibInfo - bibliotēkas menedžmenta sistēma\nJava Swing + Apache Derby",
                "Par programmu", JOptionPane.INFORMATION_MESSAGE));
        HelpMenu.add(about);

        // --- Responsīvais izkārtojums sarakstu logiem ---
        {
            java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            int tableWidth = Math.max(360, Math.min(850, screen.width - 80));
            int tableHeight = Math.max(160, Math.min(410, screen.height - 320));

            javax.swing.JPanel bookToolbar = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            bookToolbar.setOpaque(false);
            SearchTextField.setPreferredSize(new Dimension(170, SearchTextField.getPreferredSize().height));
            FilterComboBox.setPreferredSize(new Dimension(Math.min(210, tableWidth / 3), FilterComboBox.getPreferredSize().height));
            bookToolbar.add(SearchTextField);
            bookToolbar.add(SearchButton);
            bookToolbar.add(FilterLabel);
            bookToolbar.add(FilterComboBox);
            BookScrollPane.setPreferredSize(new Dimension(tableWidth, tableHeight));
            BookScrollPane.setMinimumSize(new Dimension(0, 120));
            javax.swing.JPanel bookActions = new javax.swing.JPanel(new java.awt.BorderLayout(8, 0));
            bookActions.setOpaque(false);
            javax.swing.JPanel bookLeft = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            bookLeft.setOpaque(false);
            bookLeft.add(AddButton); bookLeft.add(EditButton); bookLeft.add(DeleteButton);
            javax.swing.JPanel bookClose = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
            bookClose.setOpaque(false); bookClose.add(CloseButton2);
            bookActions.add(bookLeft, java.awt.BorderLayout.CENTER);
            bookActions.add(bookClose, java.awt.BorderLayout.EAST);
            setResponsiveTableContent(BookListDialog, bookToolbar, BookScrollPane, bookActions);

            javax.swing.JPanel userToolbar = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            userToolbar.setOpaque(false);
            UserSearchTextField.setPreferredSize(new Dimension(250, UserSearchTextField.getPreferredSize().height));
            userToolbar.add(UserSearchTextField); userToolbar.add(UserSearchButton);
            UserScrollPane.setPreferredSize(new Dimension(tableWidth, tableHeight));
            UserScrollPane.setMinimumSize(new Dimension(0, 120));
            javax.swing.JPanel userActions = new javax.swing.JPanel(new java.awt.BorderLayout(8, 0));
            userActions.setOpaque(false);
            javax.swing.JPanel userLeft = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
            userLeft.setOpaque(false);
            userLeft.add(AddUserButton); userLeft.add(EditUserButton); userLeft.add(DeleteUserButton); userLeft.add(EditUserRoleButton);
            javax.swing.JPanel userClose = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
            userClose.setOpaque(false); userClose.add(ReturnButton2);
            userActions.add(userLeft, java.awt.BorderLayout.CENTER);
            userActions.add(userClose, java.awt.BorderLayout.EAST);
            setResponsiveTableContent(UserListFrameDialog, userToolbar, UserScrollPane, userActions);

            int returnWidth = Math.max(360, Math.min(555, screen.width - 80));
            jScrollPane1.setPreferredSize(new Dimension(returnWidth, Math.max(160, Math.min(260, screen.height - 320))));
            jScrollPane1.setMinimumSize(new Dimension(0, 120));
            javax.swing.JPanel returnActions = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 12, 0));
            returnActions.setOpaque(false); returnActions.add(ReturnButton); returnActions.add(jButton19);
            setResponsiveTableContent(ReturnDialog, ReturnLabel, jScrollPane1, returnActions);

            NotificationScrollPane.setPreferredSize(new Dimension(Math.max(360, Math.min(660, screen.width - 80)),
                    Math.max(160, Math.min(300, screen.height - 320))));
            NotificationScrollPane.setMinimumSize(new Dimension(0, 120));
            javax.swing.JPanel notificationActions = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
            notificationActions.setOpaque(false); notificationActions.add(ReadButton); notificationActions.add(CloseButton4);
            setResponsiveTableContent(NotificationDialog, NotificationLabel, NotificationScrollPane, notificationActions);
        }

        // --- Rezervāciju dialoga elastīgais izkārtojums ---
        {
            Container content = ReservationDialog.getContentPane();
            content.removeAll();
            content.setLayout(new java.awt.BorderLayout(12, 12));
            content.setBackground(new Color(246, 248, 251));
            if (content instanceof javax.swing.JComponent) {
                ((javax.swing.JComponent) content).setBorder(javax.swing.BorderFactory.createEmptyBorder(16, 18, 16, 18));
            }
            javax.swing.JPanel heading = new javax.swing.JPanel(new java.awt.BorderLayout());
            heading.setOpaque(false);
            heading.add(ReservationLabel, java.awt.BorderLayout.CENTER);
            content.add(heading, java.awt.BorderLayout.NORTH);
            java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            int tableWidth = Math.max(360, Math.min(742, screen.width - 80));
            int tableHeight = Math.max(160, Math.min(365, screen.height - 285));
            ReservationScrollPane.setPreferredSize(new java.awt.Dimension(tableWidth, tableHeight));
            ReservationScrollPane.setMinimumSize(new java.awt.Dimension(0, 120));
            content.add(ReservationScrollPane, java.awt.BorderLayout.CENTER);
            javax.swing.JPanel leftActions = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            leftActions.setOpaque(false);
            leftActions.add(NewReservationButton); leftActions.add(CancelReservationButton); leftActions.add(RunButton);
            javax.swing.JPanel actions = new javax.swing.JPanel(new java.awt.BorderLayout(8, 0));
            actions.setOpaque(false);
            actions.add(leftActions, java.awt.BorderLayout.CENTER);
            javax.swing.JPanel closePanel = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
            closePanel.setOpaque(false); closePanel.add(CloseButton3);
            actions.add(closePanel, java.awt.BorderLayout.EAST);
            content.add(actions, java.awt.BorderLayout.SOUTH);
            content.revalidate();
            content.repaint();
        }

        // --- Vizuālā sakārtošana (iteratīva, bez rekursijas) ---
        {
            Color background = new Color(246, 248, 251);
            Font regular = new Font("SansSerif", Font.PLAIN, 13);
            Font emphasis = new Font("SansSerif", Font.BOLD, 13);
            JDialog[] styleDialogs = {LoginDialog, RegisterDialog, MainDialog, BookListDialog,
                BookEditDialog, UserListFrameDialog, UserEditDialog, LoanDialog, ReturnDialog,
                ReservationDialog, NotificationDialog};
            java.util.Deque<Component> styleQueue = new java.util.ArrayDeque<>();
            for (JDialog dialog : styleDialogs) {
                dialog.getContentPane().setBackground(background);
                styleQueue.add(dialog.getContentPane());
            }
            while (!styleQueue.isEmpty()) {
                Component c = styleQueue.poll();
                if (c instanceof javax.swing.JButton) {
                    javax.swing.JButton button = (javax.swing.JButton) c;
                    button.setFont(emphasis);
                    button.setFocusPainted(false);
                    button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
                    button.setMargin(new java.awt.Insets(6, 6, 6, 6));
                } else if (c instanceof javax.swing.JTextField
                        || c instanceof javax.swing.JTextArea
                        || c instanceof javax.swing.JComboBox) {
                    c.setFont(regular);
                }
                if (c instanceof Container) {
                    for (Component child : ((Container) c).getComponents()) styleQueue.add(child);
                }
            }
            JTable[] styleTables = {BookTable, UserTable, ReturnTable, ReservationTable, NotificationTable};
            for (JTable table : styleTables) {
                table.setFont(regular);
                table.setRowHeight(26);
                table.setFillsViewportHeight(true);
                table.setShowVerticalLines(false);
                table.setGridColor(new Color(226, 231, 238));
                table.setSelectionBackground(new Color(218, 232, 249));
                table.setSelectionForeground(Color.BLACK);
                table.setAutoCreateRowSorter(true);
                table.getTableHeader().setFont(emphasis);
                table.getTableHeader().setBackground(new Color(232, 237, 244));
                table.getTableHeader().setReorderingAllowed(false);
            }
        }

        // --- Logu izmēru pielāgošana ekrānam ---
        {
            java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            int maxWidth = Math.max(360, screen.width - 24);
            int maxHeight = Math.max(280, screen.height - 48);
            JDialog[] fitDialogs = {LoginDialog, RegisterDialog, MainDialog, BookListDialog,
                BookEditDialog, UserListFrameDialog, UserEditDialog, LoanDialog, ReturnDialog,
                ReservationDialog, NotificationDialog};
            for (JDialog dialog : fitDialogs) {
                dialog.pack();
                Dimension packed = dialog.getSize();
                if (packed.width > maxWidth || packed.height > maxHeight) {
                    Container originalContent = dialog.getContentPane();
                    javax.swing.JScrollPane outerScroll = new javax.swing.JScrollPane(originalContent);
                    outerScroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
                    outerScroll.getVerticalScrollBar().setUnitIncrement(18);
                    outerScroll.getHorizontalScrollBar().setUnitIncrement(18);
                    outerScroll.setPreferredSize(new Dimension(Math.min(packed.width, maxWidth), Math.min(packed.height, maxHeight)));
                    dialog.setContentPane(outerScroll);
                    dialog.pack();
                }
                if (dialog.getWidth() > maxWidth || dialog.getHeight() > maxHeight) {
                    dialog.setSize(Math.min(dialog.getWidth(), maxWidth), Math.min(dialog.getHeight(), maxHeight));
                }
            }
        }

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void RegisterButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_RegisterButtonActionPerformed
        openRegister();
    }//GEN-LAST:event_RegisterButtonActionPerformed

    private void LoginButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_LoginButtonActionPerformed
        login();
    }//GEN-LAST:event_LoginButtonActionPerformed

    private void CloseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_CloseButtonActionPerformed
        exitApplication();
    }//GEN-LAST:event_CloseButtonActionPerformed

    private void CancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_CancelButtonActionPerformed
        showLogin();
    }//GEN-LAST:event_CancelButtonActionPerformed

    private void RegisterButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_RegisterButton1ActionPerformed
        registerUser();
    }//GEN-LAST:event_RegisterButton1ActionPerformed

    private void ReturnButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ReturnButton3ActionPerformed
        returnToUserList();
    }//GEN-LAST:event_ReturnButton3ActionPerformed

    private void NotificationsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_NotificationsButtonActionPerformed
        openNotifications();
    }//GEN-LAST:event_NotificationsButtonActionPerformed

    private void UserPasswordTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_UserPasswordTextFieldActionPerformed
        saveUser();
    }//GEN-LAST:event_UserPasswordTextFieldActionPerformed

    private void SaveBookButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SaveBookButtonActionPerformed
        saveBook();
    }//GEN-LAST:event_SaveBookButtonActionPerformed

    private void BookCategoryTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_BookCategoryTextFieldActionPerformed
        saveBook();
    }//GEN-LAST:event_BookCategoryTextFieldActionPerformed

    private void ReturnButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ReturnButton1ActionPerformed
        returnToBookList();
    }//GEN-LAST:event_ReturnButton1ActionPerformed

    private void BookNameTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_BookNameTextFieldActionPerformed
        saveBook();
    }//GEN-LAST:event_BookNameTextFieldActionPerformed

    private void NewReservationButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_NewReservationButtonActionPerformed
        createReservation();
    }//GEN-LAST:event_NewReservationButtonActionPerformed

    private void UserSearchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_UserSearchButtonActionPerformed
        loadUsersFromUi();
    }//GEN-LAST:event_UserSearchButtonActionPerformed

    private void ISBNSearchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ISBNSearchButtonActionPerformed
        importBookByIsbn();
    }//GEN-LAST:event_ISBNSearchButtonActionPerformed

    private void SearchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SearchButtonActionPerformed
        loadBooksFromUi();
    }//GEN-LAST:event_SearchButtonActionPerformed

    private void EditUserButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_EditUserButtonActionPerformed
        editSelectedUser();
    }//GEN-LAST:event_EditUserButtonActionPerformed

    private void EditButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_EditButtonActionPerformed
        editSelectedBook();
    }//GEN-LAST:event_EditButtonActionPerformed

    private void SaveUserButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SaveUserButtonActionPerformed
        saveUser();
    }//GEN-LAST:event_SaveUserButtonActionPerformed

    private void ReturnButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ReturnButtonActionPerformed
        returnSelectedBook();
    }//GEN-LAST:event_ReturnButtonActionPerformed

    private void AddButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_AddButtonActionPerformed
        openNewBook();
    }//GEN-LAST:event_AddButtonActionPerformed

    private void CloseButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_CloseButton2ActionPerformed
        returnToMain(BookListDialog);
    }//GEN-LAST:event_CloseButton2ActionPerformed

    private void BookAvailabilityCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_BookAvailabilityCheckBoxActionPerformed
        // Izvēle tiek nolasīta saglabāšanas laikā; datubāzes statusu nosaka eksemplāri.
    }//GEN-LAST:event_BookAvailabilityCheckBoxActionPerformed

    private void UserRoleComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_UserRoleComboBoxActionPerformed
        // Lomas tiek uzstādītas, atverot lietotāja dialogu; šeit nav DB darbību.
    }//GEN-LAST:event_UserRoleComboBoxActionPerformed

    private void BookISBNTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_BookISBNTextFieldActionPerformed
        importBookByIsbn();
    }//GEN-LAST:event_BookISBNTextFieldActionPerformed

    private void FilterComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_FilterComboBoxActionPerformed
        if (suppressFilterAction) return;
        if (!filterComboInitialized) {
            java.util.LinkedHashSet<String> items = new java.util.LinkedHashSet<>();
            items.add("Visas grāmatas");
            items.add("Pieejamās");
            items.add("Nepieejamās");
            try {
                items.addAll(gramataDAO.findBookCategories());
            } catch (Exception ex) {
                showError(ex);
            }
            filterComboInitialized = true;
            suppressFilterAction = true;
            FilterComboBox.setModel(new DefaultComboBoxModel<>(items.toArray(new String[0])));
            FilterComboBox.setSelectedIndex(0);
            suppressFilterAction = false;
            return;
        }
        if (BookListDialog.isVisible() && currentUser != null) loadBooksFromUi();
    }//GEN-LAST:event_FilterComboBoxActionPerformed

    private void EditUserRoleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_EditUserRoleButtonActionPerformed
        changeSelectedRole();
    }//GEN-LAST:event_EditUserRoleButtonActionPerformed

    private void AddUserButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_AddUserButtonActionPerformed
        openNewUser();
    }//GEN-LAST:event_AddUserButtonActionPerformed

    private void DeleteUserButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_DeleteUserButtonActionPerformed
        deleteSelectedUser();
    }//GEN-LAST:event_DeleteUserButtonActionPerformed

    private void ReturnButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ReturnButton2ActionPerformed
        returnToMain(UserListFrameDialog);
    }//GEN-LAST:event_ReturnButton2ActionPerformed

    private void UsernameTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_UsernameTextFieldActionPerformed
        login();
    }//GEN-LAST:event_UsernameTextFieldActionPerformed

    private void BookListButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_BookListButtonActionPerformed
        openBookList();
    }//GEN-LAST:event_BookListButtonActionPerformed

    private void CancelReservationButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_CancelReservationButtonActionPerformed
        cancelSelectedReservation();
    }//GEN-LAST:event_CancelReservationButtonActionPerformed

    private void RunButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_RunButtonActionPerformed
        completeSelectedReservation();
    }//GEN-LAST:event_RunButtonActionPerformed

    public static void main(String args[]) {
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(LibInfoSaskarne.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(LibInfoSaskarne.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(LibInfoSaskarne.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(LibInfoSaskarne.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new LibInfoSaskarne();
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuBar ActionsMenuBar;
    private javax.swing.JButton AddButton;
    private javax.swing.JButton AddUserButton;
    private javax.swing.JLabel AppLabel;
    private javax.swing.JButton BookAddButton;
    private javax.swing.JLabel BookAuthorLabel;
    private javax.swing.JTextField BookAuthorTextField;
    private javax.swing.JCheckBox BookAvailabilityCheckBox;
    private javax.swing.JLabel BookAvailabilityLabel;
    private javax.swing.JLabel BookCategoryLabel;
    private javax.swing.JTextField BookCategoryTextField;
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
    private javax.swing.JDialog MainDialog;
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
    // End of variables declaration//GEN-END:variables
}