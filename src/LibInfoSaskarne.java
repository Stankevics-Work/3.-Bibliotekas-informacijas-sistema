/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */

/**
 *
 * @author user
 */

import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JOptionPane;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import pkg3.bibliotekas.informacijas.sistema.dao.EksemplarsDAO;
import pkg3.bibliotekas.informacijas.sistema.dao.GramataDAO;
import pkg3.bibliotekas.informacijas.sistema.dao.IzsniegumsDAO;
import pkg3.bibliotekas.informacijas.sistema.dao.LietotajsDAO;
import pkg3.bibliotekas.informacijas.sistema.dao.RezervacijaDAO;
import pkg3.bibliotekas.informacijas.sistema.db.DatabaseManager;
import pkg3.bibliotekas.informacijas.sistema.model.Eksemplars;
import pkg3.bibliotekas.informacijas.sistema.model.Gramata;
import pkg3.bibliotekas.informacijas.sistema.model.Izsniegums;
import pkg3.bibliotekas.informacijas.sistema.model.Lietotajs;
import pkg3.bibliotekas.informacijas.sistema.model.Loma;
import pkg3.bibliotekas.informacijas.sistema.model.Rezervacija;
import pkg3.bibliotekas.informacijas.sistema.util.PasswordUtil;


public class LibInfoSaskarne extends javax.swing.JFrame {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String DEFAULT_LIBRARY = "DTTT bibliotēka";
    private Lietotajs currentUser;
    private int editingBookId = -1;
    private int editingUserId = -1;
    private String pendingEditPassword;
    private final Set<String> readNotificationKeys = new HashSet<>();
    private final List<Lietotajs> loanReaders = new ArrayList<>();
    private final List<Eksemplars> loanCopies = new ArrayList<>();
    private final List<Gramata> reservationBooks = new ArrayList<>();

    /** Initializes application state after the NetBeans-generated components have been created. */
    private void initializeApplication() {
        setVisible(false); // The single JFrame is only the NetBeans form owner; all visible windows are JDialogs.
        configureDialogs();
        configureMenus();
        wireNavigation();
        try (Connection c = DatabaseManager.getConnection()) {
            DatabaseManager.initialize(c);
            DatabaseManager.seedInitialData(c);
        } catch (SQLException ex) {
            showError(ex);
        }
        jTextField4.setText("");
        jTextField3.setText("");
        showLogin();
    }

    private void configureDialogs() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        LoginDialog.setTitle("LibInfo - Pieteikšanās");
        RegisterDialog.setTitle("LibInfo - Reģistrācija");
        MainDialog.setTitle("LibInfo - Galvenais logs");
        BookListDialog.setTitle("LibInfo - Grāmatu saraksts");
        BookEditDialog.setTitle("LibInfo - Grāmata");
        UserListFrameDialog.setTitle("LibInfo - Lietotāju saraksts");
        UserEditDialog.setTitle("LibInfo - Lietotājs");
        LoanDialog.setTitle("LibInfo - Izsniegšana");
        ReturnDialog.setTitle("LibInfo - Atgriešana");
        ReservationDialog.setTitle("LibInfo - Rezervācijas");
        NotificationDialog.setTitle("LibInfo - Paziņojumi");
        LoginDialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { dispose(); System.exit(0); } });
        RegisterDialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { showLogin(); } });
        MainDialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { dispose(); System.exit(0); } });
        BookListDialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { returnToMain(BookListDialog); } });
        BookEditDialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { returnToBookList(); } });
        UserListFrameDialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { returnToMain(UserListFrameDialog); } });
        UserEditDialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { returnToUserList(); } });
        LoanDialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { returnToMain(LoanDialog); } });
        ReturnDialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { returnToMain(ReturnDialog); } });
        ReservationDialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { returnToMain(ReservationDialog); } });
        NotificationDialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { returnToMain(NotificationDialog); } });
        for (JDialog d : allDialogs()) {
            d.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
            d.setModal(false);
        }
        // The generated layout defines the visual proportions; pack first, then apply the documented sizes.
        for (JDialog d : allDialogs()) d.pack();
        LoginDialog.setSize(420, 320);
        RegisterDialog.setSize(420, 320);
        MainDialog.setSize(470, 220);
        BookListDialog.setSize(900, 600);
        BookEditDialog.setSize(500, 600);
        UserListFrameDialog.setSize(900, 600);
        UserEditDialog.setSize(450, 450);
        LoanDialog.setSize(500, 350);
        ReturnDialog.setSize(600, 400);
        ReservationDialog.setSize(800, 500);
        NotificationDialog.setSize(700, 450);
    }

    private List<JDialog> allDialogs() {
        List<JDialog> result = new ArrayList<>();
        result.add(LoginDialog); result.add(RegisterDialog); result.add(MainDialog);
        result.add(BookListDialog); result.add(BookEditDialog); result.add(UserListFrameDialog);
        result.add(UserEditDialog); result.add(LoanDialog); result.add(ReturnDialog);
        result.add(ReservationDialog); result.add(NotificationDialog);
        return result;
    }

    private void hideAllDialogs() {
        for (JDialog d : allDialogs()) d.setVisible(false);
    }

    private void showDialog(JDialog dialog) {
        hideAllDialogs();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
        dialog.toFront();
    }

    private void showLogin() {
        clearRegisterFields();
        showDialog(LoginDialog);
        jTextField3.requestFocusInWindow();
    }

    private void showMain() {
        updateMainRoleAccess();
        showDialog(MainDialog);
    }

    private void returnToMain(JDialog source) {
        source.setVisible(false);
        clearDialogFields(source);
        showMain();
    }

    private void configureMenus() {
        jMenu1.removeAll(); jMenu2.removeAll(); jMenu3.removeAll(); jMenu4.removeAll();
        jMenu5.removeAll(); jMenu6.removeAll(); jMenu7.removeAll();

        javax.swing.JMenuItem exit = new javax.swing.JMenuItem("Iziet");
        exit.addActionListener(e -> exitApplication());
        jMenu1.add(exit);

        javax.swing.JMenuItem books = new javax.swing.JMenuItem("Saraksts");
        books.addActionListener(e -> openBookList());
        javax.swing.JMenuItem addBook = new javax.swing.JMenuItem("Pievienot");
        addBook.addActionListener(e -> openNewBook());
        javax.swing.JMenuItem searchBook = new javax.swing.JMenuItem("Meklēt");
        searchBook.addActionListener(e -> openBookList());
        jMenu2.add(books); jMenu2.add(addBook); jMenu2.add(searchBook);

        javax.swing.JMenuItem users = new javax.swing.JMenuItem("Saraksts");
        users.addActionListener(e -> openUserList());
        javax.swing.JMenuItem addUser = new javax.swing.JMenuItem("Pievienot");
        addUser.addActionListener(e -> openNewUser());
        jMenu3.add(users); jMenu3.add(addUser);

        javax.swing.JMenuItem issue = new javax.swing.JMenuItem("Izsniegt");
        issue.addActionListener(e -> openLoan());
        javax.swing.JMenuItem ret = new javax.swing.JMenuItem("Atgriezt");
        ret.addActionListener(e -> openReturn());
        javax.swing.JMenuItem overdue = new javax.swing.JMenuItem("Kavētie");
        overdue.addActionListener(e -> openReturn(true));
        jMenu4.add(issue); jMenu4.add(ret); jMenu4.add(overdue);

        javax.swing.JMenuItem reservations = new javax.swing.JMenuItem("Saraksts");
        reservations.addActionListener(e -> openReservations());
        jMenu5.add(reservations);

        javax.swing.JMenuItem notifications = new javax.swing.JMenuItem("Mani paziņojumi");
        notifications.addActionListener(e -> openNotifications());
        jMenu6.add(notifications);

        javax.swing.JMenuItem about = new javax.swing.JMenuItem("Par programmu");
        about.addActionListener(e -> JOptionPane.showMessageDialog(MainDialog,
                "LibInfo - bibliotēkas menedžmenta sistēma\nJava Swing + Apache Derby",
                "Par programmu", JOptionPane.INFORMATION_MESSAGE));
        jMenu7.add(about);
    }

    private void wireNavigation() {
        // Existing NetBeans listeners are retained for design compatibility; these listeners perform the actual transitions.
        jButton4.addActionListener(e -> openRegister());
        jButton5.addActionListener(e -> login());
        jButton6.addActionListener(e -> exitApplication());
        jButton1.addActionListener(e -> showLogin());
        jButton2.addActionListener(e -> registerUser());

        jButton3.addActionListener(e -> openNotifications());
        jButton7.addActionListener(e -> openNewBook());
        jButton8.addActionListener(e -> openBookList());
        jButton9.addActionListener(e -> openReturn());
        jButton10.addActionListener(e -> openLoan());
        jButton11.addActionListener(e -> openUserList());
        jButton12.addActionListener(e -> openReservations());
        jButton13.addActionListener(e -> exitApplication());

        jButton33.addActionListener(e -> openNewBook());
        jButton34.addActionListener(e -> editSelectedBook());
        jButton35.addActionListener(e -> loadBooksFromUi());
        jButton36.addActionListener(e -> deleteSelectedBook());
        jButton38.addActionListener(e -> returnToMain(BookListDialog));
        jButton22.addActionListener(e -> saveBook());
        jButton23.addActionListener(e -> returnToBookList());
        jButton24.addActionListener(e -> importBookByIsbn());

        jButton29.addActionListener(e -> openNewUser());
        jButton30.addActionListener(e -> editSelectedUser());
        jButton31.addActionListener(e -> loadUsersFromUi());
        jButton32.addActionListener(e -> deleteSelectedUser());
        jButton37.addActionListener(e -> changeSelectedRole());
        jButton15.addActionListener(e -> returnToUserList());
        jButton39.addActionListener(e -> saveUser());

        jButton16.addActionListener(e -> issueBook());
        jButton17.addActionListener(e -> returnToMain(LoanDialog));
        jButton18.addActionListener(e -> returnSelectedBook());
        jButton19.addActionListener(e -> returnToMain(ReturnDialog));

        jButton25.addActionListener(e -> createReservation());
        jButton26.addActionListener(e -> cancelSelectedReservation());
        jButton27.addActionListener(e -> returnToMain(ReservationDialog));
        jButton28.addActionListener(e -> completeSelectedReservation());
        jButton20.addActionListener(e -> markNotificationRead());
        jButton21.addActionListener(e -> returnToMain(NotificationDialog));
    }

    private boolean isStaff() { return currentUser != null && (currentUser.getLoma() == Loma.ADMIN || currentUser.getLoma() == Loma.LIBRARIAN); }
    private boolean isAdmin() { return currentUser != null && currentUser.getLoma() == Loma.ADMIN; }
    private boolean isReader() { return currentUser != null && currentUser.getLoma() == Loma.READER; }

    private void updateMainRoleAccess() {
        boolean staff = isStaff();
        boolean admin = isAdmin();
        boolean reader = isReader();
        jButton7.setEnabled(staff);
        jButton8.setEnabled(true);
        jButton9.setEnabled(staff);
        jButton10.setEnabled(staff);
        jButton11.setEnabled(staff);
        jButton12.setEnabled(staff || reader);
        jButton3.setEnabled(true);
        // Menus remain available visually, but action methods enforce the same permissions.
        MainDialog.setTitle("LibInfo - Galvenais logs" + (currentUser == null ? "" : " — " + currentUser.getLoma()));
    }

    private void showError(Throwable ex) {
        String message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
        JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(MainDialog), message,
                "LibInfo kļūda", JOptionPane.ERROR_MESSAGE);
    }

    private <T> T db(DbOperation<T> operation) throws SQLException {
        try (Connection c = DatabaseManager.getConnection()) {
            DatabaseManager.initialize(c);
            return operation.run(c);
        }
    }

    @FunctionalInterface private interface DbOperation<T> { T run(Connection c) throws SQLException; }

    private void login() {
        String username = jTextField4.getText().trim();
        String password = jTextField3.getText();
        if (username.isEmpty() || password.isEmpty()) {
            showError(new IllegalArgumentException("Ievadiet lietotājvārdu un paroli.")); return;
        }
        try {
            Lietotajs user = db(c -> new LietotajsDAO(c).findByUsername(username));
            if (user == null || !PasswordUtil.verify(password, user.getParole())) {
                showError(new SecurityException("Nepareizs lietotājvārds vai parole.")); return;
            }
            currentUser = user;
            readNotificationKeys.clear();
            showMain();
        } catch (Exception ex) { showError(ex); }
    }

    private void openRegister() {
        clearRegisterFields();
        LoginDialog.setVisible(false);
        showDialog(RegisterDialog);
        jTextField5.requestFocusInWindow();
    }

    private void registerUser() {
        String vards = jTextField5.getText().trim();
        String uzvards = jTextField6.getText().trim();
        String username = jTextField7.getText().trim();
        String password = jTextField8.getText();
        String confirm = jTextField10.getText();
        String email = jTextField9.getText().trim();
        if (vards.isEmpty() || uzvards.isEmpty() || username.isEmpty() || password.isEmpty() || confirm.isEmpty() || email.isEmpty()) {
            showError(new IllegalArgumentException("Visi reģistrācijas lauki ir obligāti.")); return;
        }
        if (!password.equals(confirm)) { showError(new IllegalArgumentException("Paroles nesakrīt.")); return; }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) { showError(new IllegalArgumentException("E-pasta adrese nav derīga.")); return; }
        try {
            db(c -> {
                LietotajsDAO dao = new LietotajsDAO(c);
                if (dao.findByUsername(username) != null) throw new SQLException("Lietotājvārds jau ir aizņemts.");
                try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM lietotajs WHERE epasts = ?")) {
                    ps.setString(1, email);
                    try (ResultSet rs = ps.executeQuery()) { if (rs.next()) throw new SQLException("E-pasts jau ir aizņemts."); }
                }
                Lietotajs user = new Lietotajs();
                user.setVards(vards); user.setUzvards(uzvards); user.setLietotajvards(username);
                user.setParole(PasswordUtil.hash(password)); user.setEpasts(email); user.setLoma(Loma.READER);
                dao.insert(user); return user;
            });
            JOptionPane.showMessageDialog(RegisterDialog, "Reģistrācija veiksmīga. Tagad varat pieteikties.", "LibInfo", JOptionPane.INFORMATION_MESSAGE);
            showLogin();
            jTextField4.setText(username); jTextField3.setText(password);
        } catch (Exception ex) { showError(ex); }
    }

    private void openBookList() {
        if (currentUser == null) return;
        if (isReader() || currentUser.getLoma() == Loma.AUTHOR || isStaff()) {
            BookListDialog.setVisible(false);
            showDialog(BookListDialog);
            loadBooksFromUi();
        } else return;
    }

    private void loadBooksFromUi() {
        try {
            final String text = jTextField20.getText().trim();
            final String filter = String.valueOf(jComboBox4.getSelectedItem());
            List<Gramata> books = db(c -> searchBooks(c, text, filter));
            if (currentUser != null && currentUser.getLoma() == Loma.AUTHOR) {
                String author = (currentUser.getVards() + " " + currentUser.getUzvards()).trim();
                if (!author.isEmpty()) books.removeIf(b -> b.getAutors() == null || !b.getAutors().toLowerCase().contains(author.toLowerCase()));
            }
            DefaultTableModel model = (DefaultTableModel) jTable5.getModel();
            model.setRowCount(0);
            for (Gramata b : books) model.addRow(new Object[]{b.getGramatasId(), b.getNosaukums(), b.getAutors(), b.getIsbn(), b.getKategorija(), b.getIzdosanasGads(), b.isPieejama() ? "Jā" : "Nē"});
        } catch (Exception ex) { showError(ex); }
    }

    private List<Gramata> searchBooks(Connection c, String text, String filter) throws SQLException {
        String pattern = "%" + text + "%";
        String sql = "SELECT * FROM gramata WHERE 1=1";
        List<Object> args = new ArrayList<>();
        if (!text.isEmpty()) {
            if ("Autors".equals(filter)) { sql += " AND LOWER(autors) LIKE LOWER(?)"; args.add(pattern); }
            else if ("ISBN".equals(filter)) { sql += " AND isbn LIKE ?"; args.add(pattern); }
            else if ("Kategorija".equals(filter)) { sql += " AND LOWER(kategorija) LIKE LOWER(?)"; args.add(pattern); }
            else { sql += " AND LOWER(nosaukums) LIKE LOWER(?)"; args.add(pattern); }
        }
        sql += " ORDER BY nosaukums";
        List<Gramata> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i=0;i<args.size();i++) ps.setObject(i+1,args.get(i));
            try (ResultSet rs=ps.executeQuery()) {
                while(rs.next()) {
                    Gramata b=new Gramata();
                    b.setGramatasId(rs.getInt("gramatas_id")); b.setNosaukums(rs.getString("nosaukums"));
                    b.setApraksts(rs.getString("apraksts")); b.setIzdosanasGads((Integer)rs.getObject("izdosanas_gads"));
                    b.setIsbn(rs.getString("isbn")); b.setKategorija(rs.getString("kategorija"));
                    b.setAutors(rs.getString("autors")); b.setPieejamiba(rs.getBoolean("pieejamiba")); result.add(b);
                }
            }
        }
        return result;
    }

    private void openNewBook() {
        if (!isStaff()) { showError(new SecurityException("Grāmatu pievienošana ir pieejama bibliotekāram vai administratoram.")); return; }
        editingBookId = -1; clearBookFields();
        BookEditDialog.setTitle("LibInfo - Pievienot grāmatu");
        BookListDialog.setVisible(false);
        showDialog(BookEditDialog);
    }

    private void editSelectedBook() {
        if (!isStaff()) { showError(new SecurityException("Grāmatu rediģēšana ir pieejama bibliotekāram vai administratoram.")); return; }
        int row = jTable5.getSelectedRow();
        if (row < 0) { showError(new IllegalArgumentException("Izvēlieties grāmatu tabulā.")); return; }
        int id = ((Number) jTable5.getValueAt(row, 0)).intValue();
        try {
            Gramata book = db(c -> new GramataDAO(c).findById(id));
            if (book == null) throw new SQLException("Grāmata nav atrasta.");
            editingBookId = id; fillBookFields(book);
            BookEditDialog.setTitle("LibInfo - Rediģēt grāmatu");
            BookListDialog.setVisible(false); showDialog(BookEditDialog);
        } catch (Exception ex) { showError(ex); }
    }

    private void saveBook() {
        if (!isStaff()) return;
        String title = jTextField17.getText().trim(); String author = jTextField16.getText().trim();
        String isbn = jTextField19.getText().trim(); String category = jTextField18.getText().trim();
        String description = jTextArea1.getText().trim();
        if (title.isEmpty()) { showError(new IllegalArgumentException("Grāmatas nosaukums ir obligāts.")); return; }
        Integer year = null; Object spin = jSpinner1.getValue();
        if (spin instanceof Number && ((Number)spin).intValue() > 0) year = ((Number)spin).intValue();
        try {
            Integer finalYear = year;
            db(c -> {
                GramataDAO books = new GramataDAO(c);
                if (editingBookId < 0) {
                    Gramata b = new Gramata(); b.setNosaukums(title); b.setAutors(author.isEmpty()?null:author);
                    b.setIsbn(isbn.isEmpty()?null:isbn); b.setKategorija(category.isEmpty()?null:category); b.setApraksts(description.isEmpty()?null:description); b.setIzdosanasGads(finalYear);
                    books.insert(b);
                    // A newly created book receives one available physical copy so availability is immediately usable.
                    Eksemplars copy = new Eksemplars(); copy.setGramata(b); copy.setBiblioteka(DEFAULT_LIBRARY); copy.setStatus("pieejams"); new EksemplarsDAO(c).insert(copy);
                } else {
                    Gramata b = books.findById(editingBookId); if (b == null) throw new SQLException("Grāmata nav atrasta.");
                    b.setNosaukums(title); b.setAutors(author.isEmpty()?null:author); b.setIsbn(isbn.isEmpty()?null:isbn); b.setKategorija(category.isEmpty()?null:category); b.setApraksts(description.isEmpty()?null:description); b.setIzdosanasGads(finalYear);
                    books.update(b);
                }
                return null;
            });
            JOptionPane.showMessageDialog(BookEditDialog, "Grāmatas dati saglabāti.", "LibInfo", JOptionPane.INFORMATION_MESSAGE);
            returnToBookList();
        } catch (Exception ex) { showError(ex); }
    }

    private void deleteSelectedBook() {
        if (!isStaff()) return;
        int row=jTable5.getSelectedRow(); if(row<0){showError(new IllegalArgumentException("Izvēlieties grāmatu tabulā."));return;}
        int id=((Number)jTable5.getValueAt(row,0)).intValue();
        int ok=JOptionPane.showConfirmDialog(BookListDialog,"Dzēst izvēlēto grāmatu?","Apstiprināt",JOptionPane.YES_NO_OPTION);
        if(ok!=JOptionPane.YES_OPTION)return;
        try {
            boolean deleted=db(c->new GramataDAO(c).delete(id));
            if(!deleted) throw new SQLException("Grāmatu nevar dzēst, jo tai ir saistīti ieraksti.");
            loadBooksFromUi();
        } catch(Exception ex){showError(ex);}
    }

    private void importBookByIsbn() {
        String isbn=jTextField19.getText().trim(); if(isbn.isEmpty()){showError(new IllegalArgumentException("Ievadiet ISBN."));return;}
        try {
            java.net.http.HttpClient client=java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request=java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://www.googleapis.com/books/v1/volumes?q=isbn:"+java.net.URLEncoder.encode(isbn, java.nio.charset.StandardCharsets.UTF_8))).build();
            java.net.http.HttpResponse<String> response=client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            String json=response.body();
            String title=jsonValue(json,"title"); String description=jsonValue(json,"description"); String published=jsonValue(json,"publishedDate"); String categories=jsonValue(json,"categories"); String author=jsonValue(json,"authors");
            if(title!=null) jTextField17.setText(title); if(description!=null) jTextArea1.setText(description); if(categories!=null) jTextField18.setText(categories); if(author!=null) jTextField16.setText(author);
            if(published!=null && published.length()>=4) try{jSpinner1.setValue(Integer.parseInt(published.substring(0,4)));}catch(NumberFormatException ignored){}
            if(title==null) throw new SQLException("Google Books neatrada grāmatu pēc šī ISBN.");
            JOptionPane.showMessageDialog(BookEditDialog,"Dati aizpildīti no Google Books API.","LibInfo",JOptionPane.INFORMATION_MESSAGE);
        } catch(Exception ex){JOptionPane.showMessageDialog(BookEditDialog,"ISBN imports neizdevās: "+ex.getMessage(),"LibInfo",JOptionPane.WARNING_MESSAGE);}
    }

    private String jsonValue(String json,String key){
        java.util.regex.Pattern p=java.util.regex.Pattern.compile("\\\""+java.util.regex.Pattern.quote(key)+"\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        java.util.regex.Matcher m=p.matcher(json); if(m.find()) return m.group(1).replace("\\n"," ");
        java.util.regex.Pattern arr=java.util.regex.Pattern.compile("\\\""+java.util.regex.Pattern.quote(key)+"\\\"\\s*:\\s*\\[(?:\\s*\\\"([^\\\"]+)\\\"[^]]*)?\\]");
        java.util.regex.Matcher a=arr.matcher(json); return a.find()?a.group(1):null;
    }

    private void openUserList() {
        if (!isStaff()) { showError(new SecurityException("Lietotāju pārvaldība ir pieejama tikai darbiniekiem.")); return; }
        showDialog(UserListFrameDialog); loadUsersFromUi();
    }
    private void loadUsersFromUi(){
        try{
            String q=jTextField2.getText().trim().toLowerCase(); List<Lietotajs> users=db(c->new LietotajsDAO(c).findAll());
            if(!q.isEmpty()) users.removeIf(u->!(u.getVards()+" "+u.getUzvards()+" "+u.getLietotajvards()+" "+u.getEpasts()).toLowerCase().contains(q));
            DefaultTableModel model=(DefaultTableModel)jTable4.getModel(); model.setRowCount(0);
            for(Lietotajs u:users)model.addRow(new Object[]{u.getLietotajaId(),u.getVards(),u.getUzvards(),u.getLietotajvards(),u.getEpasts(),u.getLoma()});
        }catch(Exception ex){showError(ex);}
    }
    private void openNewUser(){
        if(!isStaff()){showError(new SecurityException("Lietotāja izveide ir pieejama tikai darbiniekiem."));return;}
        editingUserId=-1; pendingEditPassword=null; clearUserFields(); UserEditDialog.setTitle("LibInfo - Pievienot lietotāju"); showDialog(UserEditDialog);
    }
    private void editSelectedUser(){
        if(!isStaff())return; int row=jTable4.getSelectedRow(); if(row<0){showError(new IllegalArgumentException("Izvēlieties lietotāju tabulā."));return;}
        int id=((Number)jTable4.getValueAt(row,0)).intValue(); try{Lietotajs u=db(c->new LietotajsDAO(c).findById(id)); if(u==null)throw new SQLException("Lietotājs nav atrasts."); editingUserId=id; pendingEditPassword=u.getParole(); fillUserFields(u); showDialog(UserEditDialog);}catch(Exception ex){showError(ex);}
    }
    private void saveUser(){
        if(!isStaff())return; String v=jTextField12.getText().trim(), uz=jTextField11.getText().trim(), un=jTextField14.getText().trim(), pw=jTextField13.getText(), em=jTextField15.getText().trim();
        if(v.isEmpty()||uz.isEmpty()||un.isEmpty()||em.isEmpty()){showError(new IllegalArgumentException("Aizpildiet obligātos laukus."));return;}
        if(!em.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")){showError(new IllegalArgumentException("E-pasta adrese nav derīga."));return;}
        try{ db(c->{LietotajsDAO dao=new LietotajsDAO(c); Lietotajs u=editingUserId<0?new Lietotajs():dao.findById(editingUserId); if(u==null)throw new SQLException("Lietotājs nav atrasts.");
            u.setVards(v);u.setUzvards(uz);u.setLietotajvards(un);u.setEpasts(em);u.setLoma(Loma.valueOf(String.valueOf(jComboBox1.getSelectedItem())));
            String hash=pw.isBlank()?pendingEditPassword:PasswordUtil.hash(pw); if(hash==null)throw new SQLException("Parole ir obligāta."); u.setParole(hash); if(editingUserId<0)dao.insert(u); else dao.update(u); return null; });
            JOptionPane.showMessageDialog(UserEditDialog,"Lietotāja dati saglabāti.","LibInfo",JOptionPane.INFORMATION_MESSAGE); returnToUserList();
        }catch(Exception ex){showError(ex);}
    }
    private void deleteSelectedUser(){
        if(!isAdmin()){showError(new SecurityException("Lietotājus dzēst drīkst tikai administrators."));return;} int row=jTable4.getSelectedRow(); if(row<0){showError(new IllegalArgumentException("Izvēlieties lietotāju tabulā."));return;} int id=((Number)jTable4.getValueAt(row,0)).intValue();
        if(currentUser!=null&&id==currentUser.getLietotajaId()){showError(new IllegalArgumentException("Pašreizējo lietotāju dzēst nevar."));return;} if(JOptionPane.showConfirmDialog(UserListFrameDialog,"Dzēst izvēlēto lietotāju?","Apstiprināt",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        try{if(!db(c->new LietotajsDAO(c).delete(id)))throw new SQLException("Lietotāju nevar dzēst.");loadUsersFromUi();}catch(Exception ex){showError(ex);}
    }
    private void changeSelectedRole(){
        if(!isAdmin()){showError(new SecurityException("Lomas mainīt drīkst tikai administrators."));return;} int row=jTable4.getSelectedRow(); if(row<0){showError(new IllegalArgumentException("Izvēlieties lietotāju tabulā."));return;} int id=((Number)jTable4.getValueAt(row,0)).intValue();
        javax.swing.JComboBox<Loma> combo=new javax.swing.JComboBox<>(Loma.values()); if(JOptionPane.showConfirmDialog(UserListFrameDialog,combo,"Izvēlieties jauno lomu",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){try{db(c->{Lietotajs u=new LietotajsDAO(c).findById(id); if(u==null)throw new SQLException("Lietotājs nav atrasts."); u.setLoma((Loma)combo.getSelectedItem()); new LietotajsDAO(c).update(u); return null;});loadUsersFromUi();}catch(Exception ex){showError(ex);}}
    }

    private void openLoan(){
        if(!isStaff()){showError(new SecurityException("Izsniegšanu drīkst reģistrēt bibliotekārs vai administrators."));return;} showDialog(LoanDialog);
        try{
            loanReaders.clear(); loanCopies.clear(); loanReaders.addAll(db(c->new LietotajsDAO(c).findAll())); loanReaders.removeIf(u->u.getLoma()!=Loma.READER);
            List<Gramata> books=db(c->new GramataDAO(c).findAll());
            List<String> readerNames=new ArrayList<>(), bookNames=new ArrayList<>(); for(Lietotajs u:loanReaders)readerNames.add(u.toString());
            for(Gramata b:books){List<Eksemplars> copies=db(c->new EksemplarsDAO(c).findByBook(b.getGramatasId())); for(Eksemplars copy:copies)if("pieejams".equals(copy.getStatus())){loanCopies.add(copy);bookNames.add(b.getNosaukums()+" (ID "+copy.getEksemplaraId()+")");}}
            jComboBox3.setModel(new DefaultComboBoxModel(readerNames.toArray(new String[0]))); jComboBox2.setModel(new DefaultComboBoxModel(bookNames.toArray(new String[0])));
            jTextField1.setText(LocalDate.now().format(DATE_FORMAT)); jFormattedTextField1.setValue(LocalDate.now().plusDays(14).format(DATE_FORMAT));
        }catch(Exception ex){showError(ex);}
    }
    private void issueBook(){
        if(!isStaff())return; int ri=jComboBox3.getSelectedIndex(), ci=jComboBox2.getSelectedIndex(); if(ri<0||ci<0){showError(new IllegalArgumentException("Izvēlieties lasītāju un eksemplāru."));return;}
        LocalDate due=parseDueDate(String.valueOf(jFormattedTextField1.getValue())); if(due==null)return;
        try{Lietotajs u=loanReaders.get(ri);Eksemplars copy=loanCopies.get(ci);Izsniegums loan=new Izsniegums();loan.setLietotajs(u);loan.setEksemplars(copy);loan.setTermins(Timestamp.valueOf(due.atTime(23,59,59)));db(c->{new IzsniegumsDAO(c).insert(loan);return null;});JOptionPane.showMessageDialog(LoanDialog,"Grāmata izsniegta.","LibInfo",JOptionPane.INFORMATION_MESSAGE);returnToMain(LoanDialog);}catch(Exception ex){showError(ex);}
    }
    private LocalDate parseDueDate(String value){try{return LocalDate.parse(value,DATE_FORMAT);}catch(DateTimeParseException ex){showError(new IllegalArgumentException("Termiņam jābūt formātā GGGG-MM-DD."));return null;}}

    private void openReturn(){openReturn(false);}
    private void openReturn(boolean overdueOnly){
        if(!isStaff()){showError(new SecurityException("Grāmatu atgriešanu drīkst veikt darbinieks."));return;} showDialog(ReturnDialog); try{
            List<Izsniegums> loans=db(c->overdueOnly?new IzsniegumsDAO(c).findOverdue():new IzsniegumsDAO(c).findActive()); DefaultTableModel model=(DefaultTableModel)jTable1.getModel();model.setRowCount(0);
            for(Izsniegums l:loans)model.addRow(new Object[]{l.getIzsniegumaId(),l.getLietotajs(),l.getEksemplars()==null?"":l.getEksemplars().getGramata().getNosaukums(),l.getIzsniegts(),l.getTermins()});
            jLabel19.setText(overdueOnly?"Kavētie izsniegumi":"Aktīvie izsniegumi");
        }catch(Exception ex){showError(ex);}
    }
    private void returnSelectedBook(){
        if(!isStaff())return;int row=jTable1.getSelectedRow();if(row<0){showError(new IllegalArgumentException("Izvēlieties izsniegumu."));return;}int id=((Number)jTable1.getValueAt(row,0)).intValue();try{if(!db(c->new IzsniegumsDAO(c).atgriezt(id)))throw new SQLException("Izsniegumu neizdevās atgriezt.");openReturn();}catch(Exception ex){showError(ex);}
    }

    private void openReservations(){
        if(!(isStaff()||isReader())){showError(new SecurityException("Rezervācijas nav pieejamas šai lomai."));return;} showDialog(ReservationDialog); try{
            List<Rezervacija> reservations=db(c->new RezervacijaDAO(c).findAll()); if(isReader())reservations.removeIf(r->r.getLietotajs()==null||r.getLietotajs().getLietotajaId()!=currentUser.getLietotajaId());
            DefaultTableModel model=(DefaultTableModel)jTable3.getModel();model.setRowCount(0);for(Rezervacija r:reservations)model.addRow(new Object[]{r.getRezervacijasId(),r.getLietotajs(),r.getGramata()==null?"":r.getGramata().getNosaukums(),r.getDatums(),r.getStatuss()});
            jButton25.setEnabled(isReader()); jButton26.setEnabled(isReader()); jButton28.setEnabled(isStaff());
        }catch(Exception ex){showError(ex);}
    }
    private void createReservation(){
        if(!isReader()){showError(new SecurityException("Rezervāciju var izveidot tikai lasītājs."));return;}
        try{reservationBooks.clear();reservationBooks.addAll(db(c->new GramataDAO(c).findAll())); if(reservationBooks.isEmpty()){showError(new SQLException("Datubāzē nav grāmatu."));return;}
            String[] names=reservationBooks.stream().map(Gramata::getNosaukums).toArray(String[]::new);int selected=JOptionPane.showOptionDialog(ReservationDialog,"Izvēlieties grāmatu rezervācijai:","Jauna rezervācija",JOptionPane.DEFAULT_OPTION,JOptionPane.QUESTION_MESSAGE,null,names,names[0]);if(selected<0)return;
            Gramata b=reservationBooks.get(selected);Rezervacija r=new Rezervacija();r.setLietotajs(currentUser);r.setGramata(b);db(c->{new RezervacijaDAO(c).insert(r);return null;});JOptionPane.showMessageDialog(ReservationDialog,"Rezervācija izveidota.","LibInfo",JOptionPane.INFORMATION_MESSAGE);openReservations();
        }catch(Exception ex){showError(ex);}
    }
    private void cancelSelectedReservation(){
        if(!isReader())return;
        int row=jTable3.getSelectedRow();
        if(row<0){showError(new IllegalArgumentException("Izvēlieties rezervāciju."));return;}
        int id=((Number)jTable3.getValueAt(row,0)).intValue();
        try{
            db(c->{
                Rezervacija r=new RezervacijaDAO(c).findById(id);
                if(r==null||r.getLietotajs()==null||r.getLietotajs().getLietotajaId()!=currentUser.getLietotajaId()) throw new SQLException("Rezervācija nav atrasta.");
                r.setStatuss("atcelta");
                new RezervacijaDAO(c).update(r);
                return null;
            });
            openReservations();
        }catch(Exception ex){showError(ex);}
    }
    private void completeSelectedReservation(){
        if(!isStaff())return;
        int row=jTable3.getSelectedRow();
        if(row<0){showError(new IllegalArgumentException("Izvēlieties rezervāciju."));return;}
        int id=((Number)jTable3.getValueAt(row,0)).intValue();
        try{
            db(c->{
                try(PreparedStatement ps=c.prepareStatement("UPDATE rezervacija SET statuss='izpildīta' WHERE rezervacijas_id=? AND statuss='aktīva'")){
                    ps.setInt(1,id);
                    if(ps.executeUpdate()!=1)throw new SQLException("Rezervāciju nevar izpildīt.");
                }
                return null;
            });
            openReservations();
        }catch(Exception ex){showError(ex);}
    }

    private void openNotifications(){
        if(currentUser==null)return; showDialog(NotificationDialog); try{
            List<Izsniegums> overdue=db(c->{List<Izsniegums> list=new IzsniegumsDAO(c).findOverdue(); if(isReader())list.removeIf(l->l.getLietotajs()==null||l.getLietotajs().getLietotajaId()!=currentUser.getLietotajaId()); return list;});
            List<Rezervacija> activeRes=db(c->{List<Rezervacija> list=new RezervacijaDAO(c).findActive(); if(isReader())list.removeIf(r->r.getLietotajs()==null||r.getLietotajs().getLietotajaId()!=currentUser.getLietotajaId()); return list;});
            DefaultTableModel model=(DefaultTableModel)jTable2.getModel();model.setRowCount(0);int n=1;
            for(Izsniegums l:overdue){String key="loan:"+l.getIzsniegumaId();model.addRow(new Object[]{l.getIzsniegumaId(),"Kavējums","Jāatgriež: "+l.getEksemplars().getGramata().getNosaukums(),l.getTermins(),readNotificationKeys.contains(key)?"Jā":"Nē"});n++;}
            for(Rezervacija r:activeRes){String key="reservation:"+r.getRezervacijasId();model.addRow(new Object[]{100000+r.getRezervacijasId(),"Rezervācija","Aktīva rezervācija: "+r.getGramata().getNosaukums(),r.getDatums(),readNotificationKeys.contains(key)?"Jā":"Nē"});}
        }catch(Exception ex){showError(ex);}
    }
    private void markNotificationRead(){int row=jTable2.getSelectedRow();if(row<0){showError(new IllegalArgumentException("Izvēlieties paziņojumu."));return;}int id=((Number)jTable2.getValueAt(row,0)).intValue();String type=String.valueOf(jTable2.getValueAt(row,1));String key=type.equals("Kavējums")?"loan:"+id:"reservation:"+(id-100000);readNotificationKeys.add(key);openNotifications();}

    private void exitApplication(){
        if(JOptionPane.showConfirmDialog(MainDialog,"Vai tiešām iziet no LibInfo?","Iziet",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        hideAllDialogs(); dispose(); System.exit(0);
    }

    private void clearRegisterFields(){jTextField5.setText("");jTextField6.setText("");jTextField7.setText("");jTextField8.setText("");jTextField9.setText("");jTextField10.setText("");}
    private void clearBookFields(){jTextField17.setText("");jTextField16.setText("");jTextField19.setText("");jTextField18.setText("");jSpinner1.setValue(2026);jTextArea1.setText("");jCheckBox1.setSelected(true);}
    private void fillBookFields(Gramata b){jTextField17.setText(nvl(b.getNosaukums()));jTextField16.setText(nvl(b.getAutors()));jTextField19.setText(nvl(b.getIsbn()));jTextField18.setText(nvl(b.getKategorija()));jSpinner1.setValue(b.getIzdosanasGads()==null?2026:b.getIzdosanasGads());jTextArea1.setText(nvl(b.getApraksts()));jCheckBox1.setSelected(b.isPieejama());}
    private void clearUserFields(){jTextField12.setText("");jTextField11.setText("");jTextField14.setText("");jTextField13.setText("");jTextField15.setText("");jComboBox1.setSelectedItem("READER");}
    private void fillUserFields(Lietotajs u){jTextField12.setText(nvl(u.getVards()));jTextField11.setText(nvl(u.getUzvards()));jTextField14.setText(nvl(u.getLietotajvards()));jTextField13.setText("");jTextField15.setText(nvl(u.getEpasts()));jComboBox1.setSelectedItem(u.getLoma()==null?"READER":u.getLoma().name());}
    private void returnToBookList(){BookEditDialog.setVisible(false);clearBookFields();showDialog(BookListDialog);loadBooksFromUi();}
    private void returnToUserList(){UserEditDialog.setVisible(false);clearUserFields();showDialog(UserListFrameDialog);loadUsersFromUi();}
    private void clearDialogFields(JDialog d){if(d==BookListDialog)jTextField20.setText("");if(d==UserListFrameDialog)jTextField2.setText("");}
    private static String nvl(String value){return value==null?"":value;}

    /**
     * Creates new form LibInfoSaskarne
     */
    public LibInfoSaskarne() {
        initComponents();
        initializeApplication();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        LoginDialog = new javax.swing.JDialog();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jTextField3 = new javax.swing.JTextField();
        jTextField4 = new javax.swing.JTextField();
        jButton4 = new javax.swing.JButton();
        jButton5 = new javax.swing.JButton();
        jButton6 = new javax.swing.JButton();
        RegisterDialog = new javax.swing.JDialog();
        jLabel7 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        jTextField5 = new javax.swing.JTextField();
        jTextField6 = new javax.swing.JTextField();
        jLabel10 = new javax.swing.JLabel();
        jTextField7 = new javax.swing.JTextField();
        jLabel11 = new javax.swing.JLabel();
        jTextField8 = new javax.swing.JTextField();
        jLabel12 = new javax.swing.JLabel();
        jTextField9 = new javax.swing.JTextField();
        jLabel13 = new javax.swing.JLabel();
        jTextField10 = new javax.swing.JTextField();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        MainDialog = new javax.swing.JDialog();
        jButton3 = new javax.swing.JButton();
        jButton7 = new javax.swing.JButton();
        jButton8 = new javax.swing.JButton();
        jButton9 = new javax.swing.JButton();
        jButton10 = new javax.swing.JButton();
        jButton11 = new javax.swing.JButton();
        jButton12 = new javax.swing.JButton();
        jButton13 = new javax.swing.JButton();
        jMenuBar1 = new javax.swing.JMenuBar();
        jMenu1 = new javax.swing.JMenu();
        jMenu2 = new javax.swing.JMenu();
        jMenu3 = new javax.swing.JMenu();
        jMenu4 = new javax.swing.JMenu();
        jMenu5 = new javax.swing.JMenu();
        jMenu6 = new javax.swing.JMenu();
        jMenu7 = new javax.swing.JMenu();
        BookListDialog = new javax.swing.JDialog();
        jScrollPane6 = new javax.swing.JScrollPane();
        jTable5 = new javax.swing.JTable();
        jButton33 = new javax.swing.JButton();
        jButton34 = new javax.swing.JButton();
        jTextField20 = new javax.swing.JTextField();
        jButton35 = new javax.swing.JButton();
        jButton36 = new javax.swing.JButton();
        jLabel31 = new javax.swing.JLabel();
        jComboBox4 = new javax.swing.JComboBox<>();
        jButton38 = new javax.swing.JButton();
        BookEditDialog = new javax.swing.JDialog();
        jLabel23 = new javax.swing.JLabel();
        jButton22 = new javax.swing.JButton();
        jLabel24 = new javax.swing.JLabel();
        jTextField16 = new javax.swing.JTextField();
        jTextField17 = new javax.swing.JTextField();
        jLabel25 = new javax.swing.JLabel();
        jTextField18 = new javax.swing.JTextField();
        jLabel26 = new javax.swing.JLabel();
        jTextField19 = new javax.swing.JTextField();
        jLabel27 = new javax.swing.JLabel();
        jButton23 = new javax.swing.JButton();
        jButton24 = new javax.swing.JButton();
        jSpinner1 = new javax.swing.JSpinner();
        jLabel29 = new javax.swing.JLabel();
        jScrollPane3 = new javax.swing.JScrollPane();
        jTextArea1 = new javax.swing.JTextArea();
        jLabel30 = new javax.swing.JLabel();
        jCheckBox1 = new javax.swing.JCheckBox();
        UserListFrameDialog = new javax.swing.JDialog();
        jScrollPane5 = new javax.swing.JScrollPane();
        jTable4 = new javax.swing.JTable();
        jButton29 = new javax.swing.JButton();
        jButton37 = new javax.swing.JButton();
        jButton30 = new javax.swing.JButton();
        jTextField2 = new javax.swing.JTextField();
        jButton31 = new javax.swing.JButton();
        jButton32 = new javax.swing.JButton();
        jButton40 = new javax.swing.JButton();
        UserEditDialog = new javax.swing.JDialog();
        jLabel14 = new javax.swing.JLabel();
        jButton15 = new javax.swing.JButton();
        jLabel16 = new javax.swing.JLabel();
        jTextField11 = new javax.swing.JTextField();
        jTextField12 = new javax.swing.JTextField();
        jLabel17 = new javax.swing.JLabel();
        jTextField13 = new javax.swing.JTextField();
        jLabel18 = new javax.swing.JLabel();
        jTextField14 = new javax.swing.JTextField();
        jTextField15 = new javax.swing.JTextField();
        jLabel20 = new javax.swing.JLabel();
        jLabel21 = new javax.swing.JLabel();
        jComboBox1 = new javax.swing.JComboBox<>();
        jButton39 = new javax.swing.JButton();
        LoanDialog = new javax.swing.JDialog();
        jLabel1 = new javax.swing.JLabel();
        jComboBox2 = new javax.swing.JComboBox<>();
        jLabel2 = new javax.swing.JLabel();
        jComboBox3 = new javax.swing.JComboBox<>();
        jLabel3 = new javax.swing.JLabel();
        jTextField1 = new javax.swing.JTextField();
        jLabel15 = new javax.swing.JLabel();
        jFormattedTextField1 = new javax.swing.JFormattedTextField();
        jButton16 = new javax.swing.JButton();
        jButton17 = new javax.swing.JButton();
        ReturnDialog = new javax.swing.JDialog();
        jLabel19 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();
        jButton18 = new javax.swing.JButton();
        jButton19 = new javax.swing.JButton();
        ReservationDialog = new javax.swing.JDialog();
        jLabel28 = new javax.swing.JLabel();
        jScrollPane4 = new javax.swing.JScrollPane();
        jTable3 = new javax.swing.JTable();
        jButton25 = new javax.swing.JButton();
        jButton26 = new javax.swing.JButton();
        jButton27 = new javax.swing.JButton();
        jButton28 = new javax.swing.JButton();
        NotificationDialog = new javax.swing.JDialog();
        jLabel22 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTable2 = new javax.swing.JTable();
        jButton20 = new javax.swing.JButton();
        jButton21 = new javax.swing.JButton();

        LoginDialog.setPreferredSize(new java.awt.Dimension(420, 320));
        LoginDialog.setResizable(false);

        jLabel4.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel4.setText("Lietotājvārds:");

        jLabel5.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jLabel5.setText("LibInfo - Bibliotēkas sistēma");

        jLabel6.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel6.setText("Parole:");

        jButton4.setBackground(new java.awt.Color(204, 204, 204));
        jButton4.setText("Reģistrēties");
        jButton4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton4ActionPerformed(evt);
            }
        });

        jButton5.setBackground(new java.awt.Color(204, 204, 204));
        jButton5.setText("Pieteikties");
        jButton5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton5ActionPerformed(evt);
            }
        });

        jButton6.setBackground(new java.awt.Color(204, 204, 204));
        jButton6.setText("Aizvērt");
        jButton6.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton6ActionPerformed(evt);
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
                        .addComponent(jButton5, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jButton4, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jButton6)
                        .addGap(73, 73, 73))
                    .addGroup(LoginDialogLayout.createSequentialGroup()
                        .addGap(8, 8, 8)
                        .addGroup(LoginDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel5)
                            .addGroup(LoginDialogLayout.createSequentialGroup()
                                .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(jTextField4, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(43, 43, 43))))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, LoginDialogLayout.createSequentialGroup()
                .addGap(110, 110, 110)
                .addComponent(jLabel6, javax.swing.GroupLayout.PREFERRED_SIZE, 52, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTextField3, javax.swing.GroupLayout.PREFERRED_SIZE, 192, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        LoginDialogLayout.setVerticalGroup(
            LoginDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(LoginDialogLayout.createSequentialGroup()
                .addGap(56, 56, 56)
                .addComponent(jLabel5)
                .addGap(18, 18, 18)
                .addGroup(LoginDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jTextField4, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(LoginDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jTextField3, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel6, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(58, 58, 58)
                .addGroup(LoginDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton4, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton6, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton5, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(52, Short.MAX_VALUE))
        );

        RegisterDialog.setPreferredSize(new java.awt.Dimension(420, 320));
        RegisterDialog.setResizable(false);

        jLabel7.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel7.setText("Vārds:");

        jLabel8.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jLabel8.setText("Jauna lietotāja reģistrācija");

        jLabel9.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel9.setText("Uzvārds:");

        jLabel10.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel10.setText("Lietotājvārds:");

        jLabel11.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel11.setText("Parole:");

        jLabel12.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel12.setText("Apstiprināt paroli:");

        jLabel13.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel13.setText("E-pasts:");

        jButton1.setBackground(new java.awt.Color(204, 204, 204));
        jButton1.setText("Atcelt");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        jButton2.setBackground(new java.awt.Color(204, 204, 204));
        jButton2.setText("Reģistrēties");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
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
                                .addComponent(jLabel8, javax.swing.GroupLayout.Alignment.TRAILING)
                                .addGroup(RegisterDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, RegisterDialogLayout.createSequentialGroup()
                                        .addComponent(jLabel9)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jTextField5))
                                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, RegisterDialogLayout.createSequentialGroup()
                                        .addComponent(jLabel7)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(jTextField6, javax.swing.GroupLayout.PREFERRED_SIZE, 212, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                .addGroup(RegisterDialogLayout.createSequentialGroup()
                                    .addComponent(jLabel10)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                    .addComponent(jTextField8, javax.swing.GroupLayout.PREFERRED_SIZE, 174, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGroup(RegisterDialogLayout.createSequentialGroup()
                                    .addComponent(jLabel11)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                    .addComponent(jTextField7, javax.swing.GroupLayout.PREFERRED_SIZE, 212, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGroup(RegisterDialogLayout.createSequentialGroup()
                                    .addComponent(jLabel12)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                    .addComponent(jTextField10, javax.swing.GroupLayout.PREFERRED_SIZE, 147, javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addGroup(RegisterDialogLayout.createSequentialGroup()
                                .addComponent(jLabel13)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jTextField9, javax.swing.GroupLayout.PREFERRED_SIZE, 218, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(25, 25, 25)))
                        .addGap(51, 51, 51))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, RegisterDialogLayout.createSequentialGroup()
                        .addComponent(jButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(117, 117, 117))))
        );
        RegisterDialogLayout.setVerticalGroup(
            RegisterDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(RegisterDialogLayout.createSequentialGroup()
                .addGap(41, 41, 41)
                .addComponent(jLabel8)
                .addGap(18, 18, 18)
                .addGroup(RegisterDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel7)
                    .addComponent(jTextField6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(RegisterDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel9)
                    .addComponent(jTextField5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(RegisterDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel10)
                    .addComponent(jTextField8, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(RegisterDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel11)
                    .addComponent(jTextField7, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(RegisterDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel12)
                    .addComponent(jTextField10, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(RegisterDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jTextField9, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel13))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 10, Short.MAX_VALUE)
                .addGroup(RegisterDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        MainDialog.setPreferredSize(new java.awt.Dimension(470, 220));
        MainDialog.setResizable(false);
        MainDialog.setSize(new java.awt.Dimension(470, 220));

        jButton3.setBackground(new java.awt.Color(204, 204, 204));
        jButton3.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jButton3.setText("Paziņojumi");
        jButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton3ActionPerformed(evt);
            }
        });

        jButton7.setBackground(new java.awt.Color(204, 204, 204));
        jButton7.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jButton7.setText("Pievienot grāmatu");

        jButton8.setBackground(new java.awt.Color(204, 204, 204));
        jButton8.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jButton8.setText("Grāmatu saraksts");

        jButton9.setBackground(new java.awt.Color(204, 204, 204));
        jButton9.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jButton9.setText("Atgriezt grāmatu");

        jButton10.setBackground(new java.awt.Color(204, 204, 204));
        jButton10.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jButton10.setText("Izsniegt grāmatu");

        jButton11.setBackground(new java.awt.Color(204, 204, 204));
        jButton11.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jButton11.setText("Lietotāju saraksts");

        jButton12.setBackground(new java.awt.Color(204, 204, 204));
        jButton12.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jButton12.setText("Rezervācijas");

        jButton13.setBackground(new java.awt.Color(204, 204, 204));
        jButton13.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jButton13.setText("Iziet");

        jMenu1.setText("Fails");
        jMenuBar1.add(jMenu1);

        jMenu2.setText("Grāmatas");
        jMenuBar1.add(jMenu2);

        jMenu3.setText("Lietotāji");
        jMenuBar1.add(jMenu3);

        jMenu4.setText("Izsniegums");
        jMenuBar1.add(jMenu4);

        jMenu5.setText("Rezervācijas");
        jMenuBar1.add(jMenu5);

        jMenu6.setText("Paziņojumi");
        jMenuBar1.add(jMenu6);

        jMenu7.setText("Palīdzība");
        jMenuBar1.add(jMenu7);

        MainDialog.setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout MainDialogLayout = new javax.swing.GroupLayout(MainDialog.getContentPane());
        MainDialog.getContentPane().setLayout(MainDialogLayout);
        MainDialogLayout.setHorizontalGroup(
            MainDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(MainDialogLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(MainDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(MainDialogLayout.createSequentialGroup()
                        .addComponent(jButton8)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton7))
                    .addGroup(MainDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addGroup(javax.swing.GroupLayout.Alignment.LEADING, MainDialogLayout.createSequentialGroup()
                            .addComponent(jButton11)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                            .addComponent(jButton10))
                        .addGroup(MainDialogLayout.createSequentialGroup()
                            .addGroup(MainDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(jButton12)
                                .addComponent(jButton3))
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(MainDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(jButton13)
                                .addComponent(jButton9)))))
                .addContainerGap(174, Short.MAX_VALUE))
        );
        MainDialogLayout.setVerticalGroup(
            MainDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(MainDialogLayout.createSequentialGroup()
                .addGap(18, 18, 18)
                .addGroup(MainDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton7)
                    .addComponent(jButton8))
                .addGap(18, 18, 18)
                .addGroup(MainDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton10)
                    .addComponent(jButton11))
                .addGap(18, 18, 18)
                .addGroup(MainDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton9)
                    .addComponent(jButton12))
                .addGap(18, 18, 18)
                .addGroup(MainDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton3)
                    .addComponent(jButton13))
                .addContainerGap(17, Short.MAX_VALUE))
        );

        BookListDialog.setResizable(false);
        BookListDialog.setSize(new java.awt.Dimension(900, 600));

        jTable5.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jTable5.setModel(new javax.swing.table.DefaultTableModel(
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
        jScrollPane6.setViewportView(jTable5);
        if (jTable5.getColumnModel().getColumnCount() > 0) {
            jTable5.getColumnModel().getColumn(3).setResizable(false);
        }

        jButton33.setBackground(new java.awt.Color(204, 204, 204));
        jButton33.setText("Pievienot");

        jButton34.setBackground(new java.awt.Color(204, 204, 204));
        jButton34.setText("Rēdiģēt");
        jButton34.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton34ActionPerformed(evt);
            }
        });

        jButton35.setBackground(new java.awt.Color(204, 204, 204));
        jButton35.setText("Meklēt");
        jButton35.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton35ActionPerformed(evt);
            }
        });

        jButton36.setBackground(new java.awt.Color(204, 204, 204));
        jButton36.setText("Dzēst");

        jLabel31.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel31.setText("Filtrs:");

        jComboBox4.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jComboBox4.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        jButton38.setBackground(new java.awt.Color(204, 204, 204));
        jButton38.setText("Aizvért");

        javax.swing.GroupLayout BookListDialogLayout = new javax.swing.GroupLayout(BookListDialog.getContentPane());
        BookListDialog.getContentPane().setLayout(BookListDialogLayout);
        BookListDialogLayout.setHorizontalGroup(
            BookListDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(BookListDialogLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane6, javax.swing.GroupLayout.DEFAULT_SIZE, 888, Short.MAX_VALUE)
                .addContainerGap())
            .addGroup(BookListDialogLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jTextField20, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButton35, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel31, javax.swing.GroupLayout.PREFERRED_SIZE, 43, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jComboBox4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(71, 71, 71))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, BookListDialogLayout.createSequentialGroup()
                .addGap(9, 9, 9)
                .addComponent(jButton33, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jButton34, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jButton36, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jButton38, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(24, 24, 24))
        );
        BookListDialogLayout.setVerticalGroup(
            BookListDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(BookListDialogLayout.createSequentialGroup()
                .addGap(14, 14, 14)
                .addGroup(BookListDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jTextField20, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton35, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel31)
                    .addComponent(jComboBox4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(30, 30, 30)
                .addComponent(jScrollPane6, javax.swing.GroupLayout.PREFERRED_SIZE, 387, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 44, Short.MAX_VALUE)
                .addGroup(BookListDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton38, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton36, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton34, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton33, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(35, 35, 35))
        );

        jLabel23.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel23.setText("Vārds:");

        jButton22.setText("Saglabāt");
        jButton22.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton22ActionPerformed(evt);
            }
        });

        jLabel24.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel24.setText("Autors:");

        jTextField17.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextField17ActionPerformed(evt);
            }
        });

        jLabel25.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel25.setText("ISBN:");

        jTextField18.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextField18ActionPerformed(evt);
            }
        });

        jLabel26.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel26.setText("Kategorija:");

        jLabel27.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel27.setText("Izdošanas gads:");

        jButton23.setText("Atcelt");
        jButton23.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton23ActionPerformed(evt);
            }
        });

        jButton24.setBackground(new java.awt.Color(204, 204, 204));
        jButton24.setText("Meklēt pēc ISBN");
        jButton24.setMaximumSize(new java.awt.Dimension(100, 45));
        jButton24.setMinimumSize(new java.awt.Dimension(100, 45));
        jButton24.setPreferredSize(new java.awt.Dimension(100, 45));
        jButton24.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton24ActionPerformed(evt);
            }
        });

        jLabel29.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel29.setText("Apraksts:");

        jTextArea1.setColumns(20);
        jTextArea1.setRows(5);
        jScrollPane3.setViewportView(jTextArea1);

        jLabel30.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel30.setText("Pieiejamība:");

        jCheckBox1.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jCheckBox1.setText("jCheckBox1");

        javax.swing.GroupLayout BookEditDialogLayout = new javax.swing.GroupLayout(BookEditDialog.getContentPane());
        BookEditDialog.getContentPane().setLayout(BookEditDialogLayout);
        BookEditDialogLayout.setHorizontalGroup(
            BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(BookEditDialogLayout.createSequentialGroup()
                .addGap(140, 140, 140)
                .addComponent(jButton22, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jButton23, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, BookEditDialogLayout.createSequentialGroup()
                .addContainerGap(104, Short.MAX_VALUE)
                .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(BookEditDialogLayout.createSequentialGroup()
                        .addComponent(jLabel30)
                        .addGap(18, 18, 18)
                        .addComponent(jCheckBox1))
                    .addGroup(BookEditDialogLayout.createSequentialGroup()
                        .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, BookEditDialogLayout.createSequentialGroup()
                                .addComponent(jLabel29)
                                .addGap(18, 18, 18)
                                .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, BookEditDialogLayout.createSequentialGroup()
                                .addComponent(jLabel27)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jSpinner1))
                            .addGroup(BookEditDialogLayout.createSequentialGroup()
                                .addComponent(jLabel23)
                                .addGap(18, 18, 18)
                                .addComponent(jTextField17, javax.swing.GroupLayout.PREFERRED_SIZE, 212, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                .addGroup(BookEditDialogLayout.createSequentialGroup()
                                    .addComponent(jLabel25)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(jTextField19, javax.swing.GroupLayout.PREFERRED_SIZE, 212, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, BookEditDialogLayout.createSequentialGroup()
                                    .addComponent(jLabel24)
                                    .addGap(18, 18, 18)
                                    .addComponent(jTextField16, javax.swing.GroupLayout.PREFERRED_SIZE, 212, javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addGroup(BookEditDialogLayout.createSequentialGroup()
                                .addComponent(jLabel26)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jTextField18, javax.swing.GroupLayout.PREFERRED_SIZE, 212, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton24, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        BookEditDialogLayout.setVerticalGroup(
            BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(BookEditDialogLayout.createSequentialGroup()
                .addGap(106, 106, 106)
                .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jTextField17, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel23))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel24)
                    .addComponent(jTextField16, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel25)
                    .addComponent(jTextField19, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton24, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(BookEditDialogLayout.createSequentialGroup()
                        .addGap(32, 32, 32)
                        .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel27)
                            .addComponent(jSpinner1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(BookEditDialogLayout.createSequentialGroup()
                        .addGap(7, 7, 7)
                        .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jTextField18, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel26))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 95, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel29))
                .addGap(28, 28, 28)
                .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel30)
                    .addComponent(jCheckBox1))
                .addGap(18, 18, 18)
                .addGroup(BookEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jButton22, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton23, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(105, Short.MAX_VALUE))
        );

        jTable4.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jTable4.setModel(new javax.swing.table.DefaultTableModel(
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
        jScrollPane5.setViewportView(jTable4);
        if (jTable4.getColumnModel().getColumnCount() > 0) {
            jTable4.getColumnModel().getColumn(3).setResizable(false);
        }

        jButton29.setBackground(new java.awt.Color(204, 204, 204));
        jButton29.setText("Pievienot");

        jButton37.setBackground(new java.awt.Color(204, 204, 204));
        jButton37.setText("Atcelt");

        jButton30.setBackground(new java.awt.Color(204, 204, 204));
        jButton30.setText("Rēdiģēt");
        jButton30.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton30ActionPerformed(evt);
            }
        });

        jTextField2.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N

        jButton31.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jButton31.setText("Meklēt:");
        jButton31.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton31ActionPerformed(evt);
            }
        });

        jButton32.setBackground(new java.awt.Color(204, 204, 204));
        jButton32.setText("Dzēst");

        jButton40.setBackground(new java.awt.Color(204, 204, 204));
        jButton40.setText("Mainīt lomu");

        javax.swing.GroupLayout UserListFrameDialogLayout = new javax.swing.GroupLayout(UserListFrameDialog.getContentPane());
        UserListFrameDialog.getContentPane().setLayout(UserListFrameDialogLayout);
        UserListFrameDialogLayout.setHorizontalGroup(
            UserListFrameDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(UserListFrameDialogLayout.createSequentialGroup()
                .addGroup(UserListFrameDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(UserListFrameDialogLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jScrollPane5, javax.swing.GroupLayout.DEFAULT_SIZE, 888, Short.MAX_VALUE))
                    .addGroup(UserListFrameDialogLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jButton31)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jTextField2, javax.swing.GroupLayout.PREFERRED_SIZE, 103, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 698, Short.MAX_VALUE)))
                .addContainerGap())
            .addGroup(UserListFrameDialogLayout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addComponent(jButton29, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jButton30, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(12, 12, 12)
                .addComponent(jButton32, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jButton40, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jButton37, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(21, 21, 21))
        );
        UserListFrameDialogLayout.setVerticalGroup(
            UserListFrameDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(UserListFrameDialogLayout.createSequentialGroup()
                .addGap(14, 14, 14)
                .addGroup(UserListFrameDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jTextField2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton31))
                .addGap(30, 30, 30)
                .addComponent(jScrollPane5, javax.swing.GroupLayout.PREFERRED_SIZE, 418, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(31, 31, 31)
                .addGroup(UserListFrameDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton32, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton30, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton29, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton40, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton37, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(35, Short.MAX_VALUE))
        );

        UserEditDialog.setPreferredSize(new java.awt.Dimension(450, 450));
        UserEditDialog.setSize(new java.awt.Dimension(450, 450));

        jLabel14.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel14.setText("Vārds:");

        jButton15.setBackground(new java.awt.Color(204, 204, 204));
        jButton15.setText("Atcelt");
        jButton15.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton15ActionPerformed(evt);
            }
        });

        jLabel16.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel16.setText("Uzvārds:");

        jLabel17.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel17.setText("Lietotājvārds:");

        jTextField13.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextField13ActionPerformed(evt);
            }
        });

        jLabel18.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel18.setText("Parole:");

        jLabel20.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel20.setText("E-pasts:");

        jLabel21.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel21.setText("Loma:");

        jComboBox1.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        jButton39.setBackground(new java.awt.Color(204, 204, 204));
        jButton39.setText("Saglabāt");
        jButton39.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton39ActionPerformed(evt);
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
                                .addComponent(jLabel18))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(jLabel21)
                                .addComponent(jLabel20)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jTextField13)
                            .addComponent(jTextField15)
                            .addComponent(jComboBox1, 0, 218, Short.MAX_VALUE)))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, UserEditDialogLayout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, UserEditDialogLayout.createSequentialGroup()
                                .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel14)
                                    .addComponent(jLabel16))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED))
                            .addGroup(UserEditDialogLayout.createSequentialGroup()
                                .addComponent(jLabel17)
                                .addGap(13, 13, 13)))
                        .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(jTextField14)
                            .addComponent(jTextField11)
                            .addComponent(jTextField12, javax.swing.GroupLayout.DEFAULT_SIZE, 212, Short.MAX_VALUE))))
                .addGap(63, 63, 63))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, UserEditDialogLayout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(jButton15, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(114, 114, 114))
            .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(UserEditDialogLayout.createSequentialGroup()
                    .addGap(123, 123, 123)
                    .addComponent(jButton39, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap(227, Short.MAX_VALUE)))
        );
        UserEditDialogLayout.setVerticalGroup(
            UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(UserEditDialogLayout.createSequentialGroup()
                .addGap(106, 106, 106)
                .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jTextField12, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel14, javax.swing.GroupLayout.Alignment.TRAILING))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jTextField11, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel16))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel17)
                    .addComponent(jTextField14, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel18)
                    .addComponent(jTextField13, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel20)
                    .addComponent(jTextField15, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jComboBox1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel21))
                .addGap(40, 40, 40)
                .addComponent(jButton15, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(85, Short.MAX_VALUE))
            .addGroup(UserEditDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, UserEditDialogLayout.createSequentialGroup()
                    .addContainerGap(319, Short.MAX_VALUE)
                    .addComponent(jButton39, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGap(86, 86, 86)))
        );

        jLabel1.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel1.setText("Lasītājs:");

        jComboBox2.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        jLabel2.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel2.setText("Grāmata:");

        jComboBox3.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        jLabel3.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel3.setText("Izsniegšanas datums:");

        jLabel15.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel15.setText("Termiņš:");

        jButton16.setBackground(new java.awt.Color(204, 204, 204));
        jButton16.setText("Izsneigt");

        jButton17.setBackground(new java.awt.Color(204, 204, 204));
        jButton17.setText("Atcelt");

        javax.swing.GroupLayout LoanDialogLayout = new javax.swing.GroupLayout(LoanDialog.getContentPane());
        LoanDialog.getContentPane().setLayout(LoanDialogLayout);
        LoanDialogLayout.setHorizontalGroup(
            LoanDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(LoanDialogLayout.createSequentialGroup()
                .addGroup(LoanDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(LoanDialogLayout.createSequentialGroup()
                        .addGap(203, 203, 203)
                        .addComponent(jLabel15)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jFormattedTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(LoanDialogLayout.createSequentialGroup()
                        .addGap(167, 167, 167)
                        .addComponent(jButton16, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jButton17, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(LoanDialogLayout.createSequentialGroup()
                        .addGap(166, 166, 166)
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jComboBox3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(LoanDialogLayout.createSequentialGroup()
                        .addGap(113, 113, 113)
                        .addGroup(LoanDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel2)
                            .addComponent(jLabel3))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LoanDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jComboBox2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap(149, Short.MAX_VALUE))
        );
        LoanDialogLayout.setVerticalGroup(
            LoanDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(LoanDialogLayout.createSequentialGroup()
                .addGap(124, 124, 124)
                .addGroup(LoanDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jComboBox3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(LoanDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(jComboBox2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(LoanDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(LoanDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel15)
                    .addComponent(jFormattedTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(26, 26, 26)
                .addGroup(LoanDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton16, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton17, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(71, Short.MAX_VALUE))
        );

        jLabel19.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel19.setText("Aktīvie izsniegumi");

        jTable1.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null}
            },
            new String [] {
                "ID", "Lasitājs", "Grāmata", "Izsniegts", "Termiņš"
            }
        ));
        jScrollPane1.setViewportView(jTable1);
        if (jTable1.getColumnModel().getColumnCount() > 0) {
            jTable1.getColumnModel().getColumn(3).setResizable(false);
        }

        jButton18.setBackground(new java.awt.Color(204, 204, 204));
        jButton18.setText("Atgriezt");
        jButton18.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton18ActionPerformed(evt);
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
                            .addComponent(jLabel19)
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 555, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(ReturnDialogLayout.createSequentialGroup()
                        .addGap(225, 225, 225)
                        .addComponent(jButton18, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(26, 26, 26)
                        .addComponent(jButton19, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(20, Short.MAX_VALUE))
        );
        ReturnDialogLayout.setVerticalGroup(
            ReturnDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ReturnDialogLayout.createSequentialGroup()
                .addGap(22, 22, 22)
                .addComponent(jLabel19)
                .addGap(27, 27, 27)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 223, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 32, Short.MAX_VALUE)
                .addGroup(ReturnDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton19, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton18, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(31, 31, 31))
        );

        jLabel28.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel28.setText("Rezervāciju saraksts");

        jTable3.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null}
            },
            new String [] {
                "ID", "Lasitājs", "Grāmata", "Datums", "Statuss"
            }
        ));
        jScrollPane4.setViewportView(jTable3);
        if (jTable3.getColumnModel().getColumnCount() > 0) {
            jTable3.getColumnModel().getColumn(3).setResizable(false);
        }

        jButton25.setBackground(new java.awt.Color(204, 204, 204));
        jButton25.setText("Jauna rezervācija");
        jButton25.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton25ActionPerformed(evt);
            }
        });

        jButton26.setBackground(new java.awt.Color(204, 204, 204));
        jButton26.setText("Atcelt rezervāciju");

        jButton27.setBackground(new java.awt.Color(204, 204, 204));
        jButton27.setText("Aizvērt");

        jButton28.setBackground(new java.awt.Color(204, 204, 204));
        jButton28.setText("Izpildīt");

        javax.swing.GroupLayout ReservationDialogLayout = new javax.swing.GroupLayout(ReservationDialog.getContentPane());
        ReservationDialog.getContentPane().setLayout(ReservationDialogLayout);
        ReservationDialogLayout.setHorizontalGroup(
            ReservationDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ReservationDialogLayout.createSequentialGroup()
                .addGap(25, 25, 25)
                .addGroup(ReservationDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(ReservationDialogLayout.createSequentialGroup()
                        .addGap(18, 18, 18)
                        .addComponent(jButton25, javax.swing.GroupLayout.PREFERRED_SIZE, 122, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jButton26)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jButton28, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton27, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jLabel28)
                    .addComponent(jScrollPane4, javax.swing.GroupLayout.PREFERRED_SIZE, 742, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(33, Short.MAX_VALUE))
        );
        ReservationDialogLayout.setVerticalGroup(
            ReservationDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ReservationDialogLayout.createSequentialGroup()
                .addGap(22, 22, 22)
                .addComponent(jLabel28)
                .addGap(27, 27, 27)
                .addComponent(jScrollPane4, javax.swing.GroupLayout.PREFERRED_SIZE, 365, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(ReservationDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton25, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton26, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton28, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton27, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(15, Short.MAX_VALUE))
        );

        jLabel22.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel22.setText("Mani paziņojumi");

        jTable2.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null}
            },
            new String [] {
                "ID", "Veids", "Teksts", "Datums", "Izlāsīts"
            }
        ));
        jScrollPane2.setViewportView(jTable2);
        if (jTable2.getColumnModel().getColumnCount() > 0) {
            jTable2.getColumnModel().getColumn(3).setResizable(false);
        }

        jButton20.setBackground(new java.awt.Color(204, 204, 204));
        jButton20.setText("Atzīmēt kā izlasītu");

        jButton21.setBackground(new java.awt.Color(204, 204, 204));
        jButton21.setText("Aizvērt");

        javax.swing.GroupLayout NotificationDialogLayout = new javax.swing.GroupLayout(NotificationDialog.getContentPane());
        NotificationDialog.getContentPane().setLayout(NotificationDialogLayout);
        NotificationDialogLayout.setHorizontalGroup(
            NotificationDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(NotificationDialogLayout.createSequentialGroup()
                .addGroup(NotificationDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(NotificationDialogLayout.createSequentialGroup()
                        .addGap(25, 25, 25)
                        .addGroup(NotificationDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel22)
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 652, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(NotificationDialogLayout.createSequentialGroup()
                        .addGap(26, 26, 26)
                        .addComponent(jButton20, javax.swing.GroupLayout.PREFERRED_SIZE, 142, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jButton21, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(23, Short.MAX_VALUE))
        );
        NotificationDialogLayout.setVerticalGroup(
            NotificationDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(NotificationDialogLayout.createSequentialGroup()
                .addGap(22, 22, 22)
                .addComponent(jLabel22)
                .addGap(27, 27, 27)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 287, Short.MAX_VALUE)
                .addGap(18, 18, 18)
                .addGroup(NotificationDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton21, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton20, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE))
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

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jButton4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton4ActionPerformed
        // Actual navigation/database logic is wired in initializeApplication().
    }//GEN-LAST:event_jButton4ActionPerformed

    private void jButton5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton5ActionPerformed
        // Actual navigation/database logic is wired in initializeApplication().
    }//GEN-LAST:event_jButton5ActionPerformed

    private void jButton6ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton6ActionPerformed
        // Actual navigation/database logic is wired in initializeApplication().
    }//GEN-LAST:event_jButton6ActionPerformed

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        // Actual navigation/database logic is wired in initializeApplication().
    }//GEN-LAST:event_jButton1ActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        // Actual navigation/database logic is wired in initializeApplication().
    }//GEN-LAST:event_jButton2ActionPerformed

    private void jButton15ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton15ActionPerformed
        // Actual navigation/database logic is wired in initializeApplication().
    }//GEN-LAST:event_jButton15ActionPerformed

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
        // Actual navigation/database logic is wired in initializeApplication().
    }//GEN-LAST:event_jButton3ActionPerformed

    private void jTextField13ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextField13ActionPerformed
        // Actual navigation/database logic is wired in initializeApplication().
    }//GEN-LAST:event_jTextField13ActionPerformed

    private void jButton22ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton22ActionPerformed
        // Actual navigation/database logic is wired in initializeApplication().
    }//GEN-LAST:event_jButton22ActionPerformed

    private void jTextField18ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextField18ActionPerformed
        // Actual navigation/database logic is wired in initializeApplication().
    }//GEN-LAST:event_jTextField18ActionPerformed

    private void jButton23ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton23ActionPerformed
        // Actual navigation/database logic is wired in initializeApplication().
    }//GEN-LAST:event_jButton23ActionPerformed

    private void jTextField17ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextField17ActionPerformed
        // Actual navigation/database logic is wired in initializeApplication().
    }//GEN-LAST:event_jTextField17ActionPerformed

    private void jButton25ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton25ActionPerformed
        // Actual navigation/database logic is wired in initializeApplication().
    }//GEN-LAST:event_jButton25ActionPerformed

    private void jButton31ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton31ActionPerformed
        // Actual navigation/database logic is wired in initializeApplication().
    }//GEN-LAST:event_jButton31ActionPerformed

    private void jButton24ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton24ActionPerformed
        // Actual navigation/database logic is wired in initializeApplication().
    }//GEN-LAST:event_jButton24ActionPerformed

    private void jButton35ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton35ActionPerformed
        // Actual navigation/database logic is wired in initializeApplication().
    }//GEN-LAST:event_jButton35ActionPerformed

    private void jButton30ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton30ActionPerformed
        // Actual navigation/database logic is wired in initializeApplication().
    }//GEN-LAST:event_jButton30ActionPerformed

    private void jButton34ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton34ActionPerformed
        // Actual navigation/database logic is wired in initializeApplication().
    }//GEN-LAST:event_jButton34ActionPerformed

    private void jButton39ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton39ActionPerformed
        // Actual navigation/database logic is wired in initializeApplication().
    }//GEN-LAST:event_jButton39ActionPerformed

    private void jButton18ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton18ActionPerformed
        // Actual navigation/database logic is wired in initializeApplication().
    }//GEN-LAST:event_jButton18ActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
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
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new LibInfoSaskarne();
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JDialog BookEditDialog;
    private javax.swing.JDialog BookListDialog;
    private javax.swing.JDialog LoanDialog;
    private javax.swing.JDialog LoginDialog;
    private javax.swing.JDialog MainDialog;
    private javax.swing.JDialog NotificationDialog;
    private javax.swing.JDialog RegisterDialog;
    private javax.swing.JDialog ReservationDialog;
    private javax.swing.JDialog ReturnDialog;
    private javax.swing.JDialog UserEditDialog;
    private javax.swing.JDialog UserListFrameDialog;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton10;
    private javax.swing.JButton jButton11;
    private javax.swing.JButton jButton12;
    private javax.swing.JButton jButton13;
    private javax.swing.JButton jButton15;
    private javax.swing.JButton jButton16;
    private javax.swing.JButton jButton17;
    private javax.swing.JButton jButton18;
    private javax.swing.JButton jButton19;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton20;
    private javax.swing.JButton jButton21;
    private javax.swing.JButton jButton22;
    private javax.swing.JButton jButton23;
    private javax.swing.JButton jButton24;
    private javax.swing.JButton jButton25;
    private javax.swing.JButton jButton26;
    private javax.swing.JButton jButton27;
    private javax.swing.JButton jButton28;
    private javax.swing.JButton jButton29;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButton30;
    private javax.swing.JButton jButton31;
    private javax.swing.JButton jButton32;
    private javax.swing.JButton jButton33;
    private javax.swing.JButton jButton34;
    private javax.swing.JButton jButton35;
    private javax.swing.JButton jButton36;
    private javax.swing.JButton jButton37;
    private javax.swing.JButton jButton38;
    private javax.swing.JButton jButton39;
    private javax.swing.JButton jButton4;
    private javax.swing.JButton jButton40;
    private javax.swing.JButton jButton5;
    private javax.swing.JButton jButton6;
    private javax.swing.JButton jButton7;
    private javax.swing.JButton jButton8;
    private javax.swing.JButton jButton9;
    private javax.swing.JCheckBox jCheckBox1;
    private javax.swing.JComboBox<String> jComboBox1;
    private javax.swing.JComboBox<String> jComboBox2;
    private javax.swing.JComboBox<String> jComboBox3;
    private javax.swing.JComboBox<String> jComboBox4;
    private javax.swing.JFormattedTextField jFormattedTextField1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel23;
    private javax.swing.JLabel jLabel24;
    private javax.swing.JLabel jLabel25;
    private javax.swing.JLabel jLabel26;
    private javax.swing.JLabel jLabel27;
    private javax.swing.JLabel jLabel28;
    private javax.swing.JLabel jLabel29;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel30;
    private javax.swing.JLabel jLabel31;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenu jMenu2;
    private javax.swing.JMenu jMenu3;
    private javax.swing.JMenu jMenu4;
    private javax.swing.JMenu jMenu5;
    private javax.swing.JMenu jMenu6;
    private javax.swing.JMenu jMenu7;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JScrollPane jScrollPane6;
    private javax.swing.JSpinner jSpinner1;
    private javax.swing.JTable jTable1;
    private javax.swing.JTable jTable2;
    private javax.swing.JTable jTable3;
    private javax.swing.JTable jTable4;
    private javax.swing.JTable jTable5;
    private javax.swing.JTextArea jTextArea1;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JTextField jTextField10;
    private javax.swing.JTextField jTextField11;
    private javax.swing.JTextField jTextField12;
    private javax.swing.JTextField jTextField13;
    private javax.swing.JTextField jTextField14;
    private javax.swing.JTextField jTextField15;
    private javax.swing.JTextField jTextField16;
    private javax.swing.JTextField jTextField17;
    private javax.swing.JTextField jTextField18;
    private javax.swing.JTextField jTextField19;
    private javax.swing.JTextField jTextField2;
    private javax.swing.JTextField jTextField20;
    private javax.swing.JTextField jTextField3;
    private javax.swing.JTextField jTextField4;
    private javax.swing.JTextField jTextField5;
    private javax.swing.JTextField jTextField6;
    private javax.swing.JTextField jTextField7;
    private javax.swing.JTextField jTextField8;
    private javax.swing.JTextField jTextField9;
    // End of variables declaration//GEN-END:variables
}
