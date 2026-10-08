package pkg3.bibliotekas.informacijas.sistema.model;

import java.util.Date;

/** Rezervacija datu modelis atbilstoši projekta dokumentam */
public class Rezervacija {
    private int rezervacijasId;
    private Lietotajs lietotajs;
    private Gramata gramata;
    private Date datums;
    private String statuss;

    public Rezervacija() { }

    public int getRezervacijasId() { return rezervacijasId; }
    public void setRezervacijasId(int rezervacijasId) { this.rezervacijasId = rezervacijasId; }

    public Lietotajs getLietotajs() { return lietotajs; }
    public void setLietotajs(Lietotajs lietotajs) { this.lietotajs = lietotajs; }

    public Gramata getGramata() { return gramata; }
    public void setGramata(Gramata gramata) { this.gramata = gramata; }

    public Date getDatums() { return datums; }
    public void setDatums(Date datums) { this.datums = datums; }

    public String getStatuss() { return statuss; }
    public void setStatuss(String statuss) { this.statuss = statuss; }

    public String getStatus() { return statuss; }
    public void setStatus(String status) { this.statuss = status; }
}
