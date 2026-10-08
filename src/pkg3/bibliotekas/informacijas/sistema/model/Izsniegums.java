package pkg3.bibliotekas.informacijas.sistema.model;

import java.util.Date;

/** Izsniegums datu modelis atbilstoši projekta dokumentam */
public class Izsniegums {
    private int izsniegumaId;
    private Lietotajs lietotajs;
    private Eksemplars eksemplars;
    private Date izsniegts;
    private Date termins;
    private Date atgriezts;

    public Izsniegums() { }

    public int getIzsniegumaId() { return izsniegumaId; }
    public void setIzsniegumaId(int izsniegumaId) { this.izsniegumaId = izsniegumaId; }

    public Lietotajs getLietotajs() { return lietotajs; }
    public void setLietotajs(Lietotajs lietotajs) { this.lietotajs = lietotajs; }

    public Eksemplars getEksemplars() { return eksemplars; }
    public void setEksemplars(Eksemplars eksemplars) { this.eksemplars = eksemplars; }

    public Date getIzsniegts() { return izsniegts; }
    public void setIzsniegts(Date izsniegts) { this.izsniegts = izsniegts; }

    public Date getTermins() { return termins; }
    public void setTermins(Date termins) { this.termins = termins; }

    public Date getAtgriezts() { return atgriezts; }
    public void setAtgriezts(Date atgriezts) { this.atgriezts = atgriezts; }

    public boolean isKavejies() {
        return atgriezts == null && termins != null && termins.before(new Date());
    }

    /** Maina tikai modeļa objektu, saglabāšanai izmanto IzsniegumsDAO.atgriezt */
    public void atgriezt() { if (atgriezts == null) atgriezts = new Date(); }
}
