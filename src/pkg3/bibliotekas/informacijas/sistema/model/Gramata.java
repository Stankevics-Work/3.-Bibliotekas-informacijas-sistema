package pkg3.bibliotekas.informacijas.sistema.model;

/** Gramata datu modelis atbilstoši projekta dokumentam */
public class Gramata {
    private int gramatasId;
    private String nosaukums;
    private String apraksts;
    private Integer izdosanasGads;
    private String isbn;
    private String kategorija;
    private boolean pieejamiba;
    private String autors;

    public Gramata() { }

    public int getGramatasId() { return gramatasId; }
    public void setGramatasId(int gramatasId) { this.gramatasId = gramatasId; }

    public String getNosaukums() { return nosaukums; }
    public void setNosaukums(String nosaukums) { this.nosaukums = nosaukums; }

    public String getApraksts() { return apraksts; }
    public void setApraksts(String apraksts) { this.apraksts = apraksts; }

    public Integer getIzdosanasGads() { return izdosanasGads; }
    public void setIzdosanasGads(Integer izdosanasGads) { this.izdosanasGads = izdosanasGads; }

    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }

    public String getKategorija() { return kategorija; }
    public void setKategorija(String kategorija) { this.kategorija = kategorija; }

    public boolean getPieejamiba() { return pieejamiba; }
    public void setPieejamiba(boolean pieejamiba) { this.pieejamiba = pieejamiba; }

    public String getAutors() { return autors; }
    public void setAutors(String autors) { this.autors = autors; }

    public boolean isPieejama() { return pieejamiba; }
}
