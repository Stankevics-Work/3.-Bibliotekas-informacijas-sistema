package pkg3.bibliotekas.informacijas.sistema.model;

/** Eksemplars datu modelis atbilstoši projekta dokumentam */
public class Eksemplars {
    private int eksemplaraId;
    private Gramata gramata;
    private String biblioteka;
    private String status;

    public Eksemplars() { }

    public int getEksemplaraId() { return eksemplaraId; }
    public void setEksemplaraId(int eksemplaraId) { this.eksemplaraId = eksemplaraId; }

    public Gramata getGramata() { return gramata; }
    public void setGramata(Gramata gramata) { this.gramata = gramata; }

    public String getBiblioteka() { return biblioteka; }
    public void setBiblioteka(String biblioteka) { this.biblioteka = biblioteka; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
